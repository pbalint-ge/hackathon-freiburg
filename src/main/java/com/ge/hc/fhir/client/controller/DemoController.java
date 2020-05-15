package com.ge.hc.fhir.client.controller;


import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ge.hc.fhir.client.domain.Appointment;
import com.ge.hc.fhir.client.service.AppointmentRetrievalService;

@Controller
@RequestMapping(path = "/test")
public class DemoController {
    @Autowired
    private AppointmentRetrievalService appointmentRetrievalService;

    @GetMapping(path = "/page")
    public String exampleWebpageResponse(@RequestParam(name = "startDateTime") @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime startDateTime,
                                         @RequestParam(name = "endDateTime") @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime endDateTime,
                                         @RequestParam(name = "modality", required = false) String modality,
                                         Model model) {
        List<Appointment> appointments = appointmentRetrievalService.getAppointments(startDateTime, endDateTime, modality);
        model.addAttribute("appointments", appointments);
        return "webpage";
    }
    
    @ResponseBody
    @GetMapping(path = "/response")
    public ResponseEntity<Object> exampleJsonResponse(@RequestParam(name = "startDateTime") @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime startDateTime,
                                                      @RequestParam(name = "endDateTime") @DateTimeFormat(iso = ISO.DATE_TIME) LocalDateTime endDateTime,
                                                      @RequestParam(name = "modality", required = false) String modality) {
        List<Appointment> appointments = appointmentRetrievalService.getAppointments(startDateTime, endDateTime, modality);
        return ResponseEntity.ok().body(appointments);
    }
}
