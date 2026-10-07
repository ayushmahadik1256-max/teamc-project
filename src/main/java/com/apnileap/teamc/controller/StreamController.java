package com.apnileap.teamc.controller;

import com.apnileap.teamc.service.EventStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
public class StreamController {
    private final EventStreamService eventStreamService;

    @GetMapping(value = {"/api/v1/stream/events", "/api/v1/ui/stream", "/api/v1/stream"}, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents() {
        return eventStreamService.createEmitter();
    }
}
