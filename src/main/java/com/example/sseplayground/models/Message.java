package com.example.sseplayground.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.codec.ServerSentEvent;

import java.util.UUID;

@Data
@AllArgsConstructor
public class Message {
    private String message;

    public ServerSentEvent<String> toSSE() {
        return ServerSentEvent.<String>builder()
                .id(UUID.randomUUID().toString())
                .event("message")
                .data(this.message)
                .build();
    }
}
