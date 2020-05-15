package com.ge.hc.fhir.client.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.r4.model.HumanName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ge.hc.fhir.client.domain.Appointment;
import com.ge.hc.fhir.client.domain.Patient;

@Component
public class AppointmentRetrievalService {
    @Autowired
    private FhirClient fhirClient;

    public List<Appointment> getAppointments(LocalDateTime startDateTime, LocalDateTime endDateTime, String modality) {
        List<org.hl7.fhir.r4.model.Appointment> fhirAppointments = fhirClient.getAppointments(startDateTime, endDateTime, modality);
        List<Appointment> appointments = new ArrayList<>();
        for (org.hl7.fhir.r4.model.Appointment fhirAppointment: fhirAppointments) {
            appointments.add(fhirAppointmentToDto(fhirAppointment));
        }
        return appointments;
    }

    private Appointment fhirAppointmentToDto(org.hl7.fhir.r4.model.Appointment fhirAppointment) {
        Appointment appointment = new Appointment();
        appointment.setId(fhirAppointment.getIdentifier().get(0).getValue());
        appointment.setStartDate(LocalDateTime.ofInstant(fhirAppointment.getStart().toInstant(), ZoneId.systemDefault()));
        appointment.setEndDate(LocalDateTime.ofInstant(fhirAppointment.getEnd().toInstant(), ZoneId.systemDefault()));
        appointment.setCreationDate(LocalDateTime.ofInstant(fhirAppointment.getCreated().toInstant(), ZoneId.systemDefault()));
        appointment.setModality(fhirAppointment.getExtensionByUrl("http://ge.com/dwetl/fhir#app_modality").getValue().primitiveValue());
        appointment.setStatus(fhirAppointment.getStatus().toCode());
        appointment.setPriority(fhirAppointment.getPriority());
        Patient patient = new Patient();
        fhirAppointment.getParticipant().stream()
                                        .filter(participant -> participant.getActor().getResource() instanceof org.hl7.fhir.r4.model.Patient)
                                        .findFirst()
                                        .map(participant -> (org.hl7.fhir.r4.model.Patient)(participant.getActor().getResource()))
                                        .ifPresent(fhirPatient -> {
                                            List<HumanName> names = fhirPatient.getName();
                                            if (!names.isEmpty()) {
                                                patient.setFirstName(names.get(0).getGivenAsSingleString());
                                                patient.setLastName(names.get(0).getFamily());
                                            }
                                            
                                            patient.setSex(fhirPatient.getGender().toCode());
                                            patient.setBirthDate(LocalDateTime.ofInstant(fhirPatient.getBirthDate().toInstant(), ZoneId.systemDefault()));
                                        });
        appointment.setPatient(patient);
        return appointment;
    }
    
}
