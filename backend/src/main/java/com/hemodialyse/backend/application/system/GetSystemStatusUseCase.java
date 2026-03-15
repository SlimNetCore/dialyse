package com.hemodialyse.backend.application.system;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;

@Service
public class GetSystemStatusUseCase {

    public SystemStatus execute() {
        return new SystemStatus("ok", "hemodialyse-backend", OffsetDateTime.now());
    }

    public record SystemStatus(String status, String service, OffsetDateTime timestamp) {
    }
}

