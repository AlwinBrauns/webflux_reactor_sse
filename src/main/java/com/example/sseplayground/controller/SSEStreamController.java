package com.example.sseplayground.controller;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.UUID;

@RestController
@CrossOrigin("*")
@RequestMapping("api/v1/")
public class SSEStreamController {
    private final Sinks.Many<ServerSentEvent<String>> mainSinks = Sinks.many().multicast().onBackpressureBuffer();

    public SSEStreamController() {
        Flux.interval(Duration.ofSeconds(60)).map(sq -> {
            return mainSinks.tryEmitNext(ServerSentEvent.<String>builder()
                            .id(UUID.randomUUID().toString())
                            .event("keep-alive")
                            .data("ping")
                    .build());
        }).subscribe();
    }

    @GetMapping(path = "main-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> listenToMainStream() {
        return mainSinks.asFlux();
    }

    public Sinks.Many<ServerSentEvent<String>> getMainSinks() {
        return mainSinks;
    }
}
