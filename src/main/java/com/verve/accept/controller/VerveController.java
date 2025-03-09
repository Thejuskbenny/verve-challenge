package com.verve.accept.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VerveController {

    private static final Logger logger = LoggerFactory.getLogger(VerveController.class);
    private final RequestTracker requestTracker;
    private final EndpointNotifier endpointNotifier;

    @Autowired
    public VerveController(RequestTracker requestTracker, EndpointNotifier endpointNotifier) {
        this.requestTracker = requestTracker;
        this.endpointNotifier = endpointNotifier;
    }

    @GetMapping("/api/verve/accept")
    public String acceptRequest(@RequestParam("id") int id,
                                @RequestParam(value = "endpoint", required = false) String endpoint) {
        try {
            requestTracker.trackRequest(id);

            if (endpoint != null && !endpoint.isEmpty()) {
                endpointNotifier.scheduleEndpointCall(endpoint);
            }

            return "ok";
        } catch (Exception e) {
            logger.error("Error processing request with id: {}", id, e);
            return "failed";
        }
    }
}
