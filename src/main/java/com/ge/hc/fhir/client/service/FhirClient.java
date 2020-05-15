package com.ge.hc.fhir.client.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Appointment;
import org.hl7.fhir.r4.model.Appointment.AppointmentParticipantComponent;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.HealthcareService;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.StrictErrorHandler;
import ca.uhn.fhir.rest.client.api.IGenericClient;

@Component
public class FhirClient {
    public static class MyStrictErrorHandler extends StrictErrorHandler {
        private static final Logger LOGGER = LoggerFactory.getLogger(MyStrictErrorHandler.class);

        @Override
        public void invalidValue(IParseLocation location, String value, String error) {
            LOGGER.error("Invalid attribute value: {}, error: {}", value, error);
        }
    }
    private static final Logger LOGGER = LoggerFactory.getLogger(FhirClient.class);

    private IGenericClient client;

    @Autowired
    public FhirClient(@Value("${fhir.server-url}") String serverBase,
                      @Value("${proxy.url:#{null}}") String proxyUrl,
                      @Value("${proxy.port:0}") int proxyPort,
                      ApiTokenInterceptor apiTokenInterceptor) {
        FhirContext context = FhirContext.forR4();
        context.setParserErrorHandler(new MyStrictErrorHandler());

        if (!StringUtils.isEmpty(proxyUrl)) {
            context.getRestfulClientFactory().setProxy(proxyUrl, proxyPort);
        }
        client = context.newRestfulGenericClient(serverBase);
        client.registerInterceptor(apiTokenInterceptor);
    }

    public List<Appointment> getAppointments(LocalDateTime startDateTime, LocalDateTime endDateTime, String modality) {
        Map<String, Map<String, List<String>>> filters = new HashMap<>();
        Map<String, List<String>> appointmentFilters = filters.getOrDefault("Appointment", new HashMap<>());
        List<String> dateFilters = new ArrayList<>();
        dateFilters.add("gt" + startDateTime);
        dateFilters.add("lt" + endDateTime);
        appointmentFilters.put("date", dateFilters);
        if (!StringUtils.isEmpty(modality)) {
            appointmentFilters.put("modality", Arrays.asList(modality));
        }

        Map<String, Resource> resources = new HashMap<>();
        Map<String, Appointment> appointments = new HashMap<>();
        Map<String, Patient> patients = new HashMap<>();
        Bundle response = getAppointmentsFromServer(appointmentFilters);
        collectResources(response, appointments, patients, resources);
        while (response.getLink(Bundle.LINK_NEXT) != null) {
            response = client.loadPage().next(response).execute();
            collectResources(response, appointments, patients, resources);
        }

        Map<ResourceType, Set<String>> missingResources = new EnumMap<>(ResourceType.class);
        Set<Appointment> appointmentWithMissingReferences = new HashSet<>();
        dereferenceResources(appointments, resources, missingResources, appointmentWithMissingReferences);
        if (!appointmentWithMissingReferences.isEmpty()) {
            for (Entry<ResourceType, Set<String>> missingResourceEntry: missingResources.entrySet()) {
                response = getResourcesForTypeById(missingResourceEntry.getKey(), missingResourceEntry.getValue());
                collectResources(response, appointments, patients, resources);
                while (response.getLink(Bundle.LINK_NEXT) != null) {
                    response = client.loadPage().next(response).execute();
                    collectResources(response, appointments, patients, resources);
                }
            }
            dereferenceResources(appointments, resources, missingResources, appointmentWithMissingReferences);
        }
        if (missingResources.containsKey(ResourceType.Patient)) {
            LOGGER.error("Error: patients were missing from the bundle !!!");
        }
        LOGGER.info("Received {} appointments", appointments.size());

        return new ArrayList<>(appointments.values());
    }

    private Bundle getAppointmentsFromServer(Map<String, List<String>> filters) {
        return client.search()
                     .forResource(Appointment.class)
                     .whereMap(filters)
                     .include(Appointment.INCLUDE_ACTOR)
                     .include(Appointment.INCLUDE_BASED_ON)
                     .returnBundle(Bundle.class)
                     .execute();
    }

    private void dereferenceResources(Map<String, Appointment> appointments,
                                      Map<String, Resource> resources,
                                      Map<ResourceType, Set<String>> missingResources,
                                      Set<Appointment> appointmentWithMissingReferences) {
        for (Appointment appointment: appointments.values()) {
            for (AppointmentParticipantComponent participant: appointment.getParticipant()) {
                Reference actorReference = participant.getActor();
                if (!lookupReference(appointment, actorReference, resources, missingResources)) {
                    appointmentWithMissingReferences.add(appointment);
                }
                else {
                    if (actorReference.getResource() instanceof HealthcareService) {
                        HealthcareService healthcareService = (HealthcareService)actorReference.getResource();
                        for (Reference location: healthcareService.getLocation()) {
                            if (!lookupReference(appointment, location, resources, missingResources)) {
                                appointmentWithMissingReferences.add(appointment);
                            }
                        }
                    }
                }
            }

            for (Reference serviceRequestReference: appointment.getBasedOn()) {
                if (!lookupReference(appointment, serviceRequestReference, resources, missingResources)) {
                    appointmentWithMissingReferences.add(appointment);
                }

                ServiceRequest serviceRequest = ((ServiceRequest)serviceRequestReference.getResource());
                if (!lookupReference(serviceRequest, serviceRequest.getRequester(), resources, missingResources)) {
                    appointmentWithMissingReferences.add(appointment);
                }

                for (Reference performer: serviceRequest.getPerformer()) {
                    if (!lookupReference(serviceRequest, performer, resources, missingResources)) {
                        appointmentWithMissingReferences.add(appointment);
                    }
                }

                if (!lookupReference(serviceRequest, serviceRequest.getSubject(), resources, missingResources)) {
                    appointmentWithMissingReferences.add(appointment);
                }
            }
        }
    }

    private boolean lookupReference(DomainResource parent,
                                    Reference reference,
                                    Map<String, Resource> resources,
                                    Map<ResourceType, Set<String>> missingResources) {
        if (reference == null) return true;

        boolean missingData = false;
        if (reference.getResource() == null) {
            Resource referencedResource = resources.get(reference.getReference());
            if (referencedResource != null) {
                reference.setResource(referencedResource);
                LOGGER.debug("Found {} for {}/{}", reference.getReference(), parent.getResourceType(), parent.getIdElement().getIdPart());
            }
            else {
                missingData = true;
                addMissingReference(missingResources, reference.getReference());
                LOGGER.error("Couldn't find {} for {}/{}", reference.getReference(), parent.getResourceType(), parent.getIdElement().getIdPart());
            }
        }
        else {
            LOGGER.debug("{} already present in {}/{}", reference.getReference(), parent.getResourceType(), parent.getIdElement().getIdPart());
        }
        return !missingData;
    }

    private void addMissingReference(Map<ResourceType, Set<String>> missingResources, String reference) {
        String[] referenceParts = reference.split("/");
        if (referenceParts.length != 2) {
            LOGGER.error("Error: reference: {} is of unknown format! (Expected: <ResourceType>/<Resource ID>)", reference);
            return;
        }
        try {
            ResourceType resourceType = ResourceType.fromCode(referenceParts[0]);
            Set<String> missingIdsForResourceType = missingResources.get(resourceType);
            if (missingIdsForResourceType == null) {
                missingIdsForResourceType = new HashSet<>();
                missingResources.put(resourceType, missingIdsForResourceType);
            }
            missingIdsForResourceType.add(referenceParts[1]);
        }
        catch (Exception e) {
            LOGGER.error("Error adding missing resource reference: {}", reference, e);
        }
    }

    private void collectResources(Bundle response, Map<String, Appointment> appointments, Map<String, Patient> patients, Map<String, Resource> resources) {
        for (BundleEntryComponent entry: response.getEntry()) {
            Resource resource = entry.getResource();
            String id = resource.getIdElement().getResourceType() + '/' + resource.getIdElement().getIdPart();
            resources.put(id, resource);
            ResourceType resourceType = resource.getResourceType();
            if (resourceType.equals(ResourceType.Appointment)) {
                appointments.put(id, (Appointment)resource);
            }
            else if (resourceType.equals(ResourceType.Patient)) {
                patients.put(id, (Patient)resource);
            }
        }
    }

    private Bundle getResourcesForTypeById(ResourceType resourceType, Set<String> ids) {
        Map<String, List<String>> idMap = new HashMap<>();
        idMap.put("_id:in", Collections.singletonList(ids.stream().collect(Collectors.joining(","))));
        return client.search()
                     .forResource(resourceType.name())
                     .whereMap(idMap)
                     .returnBundle(Bundle.class)
                     .execute();
    }
}
