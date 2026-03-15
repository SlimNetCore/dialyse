package com.hemodialyse.backend.infrastructure.web;

import com.hemodialyse.backend.application.system.GetSystemStatusUseCase;
import com.hemodialyse.backend.application.system.GetSystemStatusUseCase.SystemStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    private final GetSystemStatusUseCase getSystemStatusUseCase;

    public SystemController(GetSystemStatusUseCase getSystemStatusUseCase) {
        this.getSystemStatusUseCase = getSystemStatusUseCase;
    }

    @GetMapping("/ping")
    public SystemStatus ping() {
        return getSystemStatusUseCase.execute();
    }
}

