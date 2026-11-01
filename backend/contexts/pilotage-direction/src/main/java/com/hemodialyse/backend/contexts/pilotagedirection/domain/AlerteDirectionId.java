package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import java.util.UUID;

public record AlerteDirectionId(UUID value) {
    public AlerteDirectionId {
        if (value == null) throw new IllegalArgumentException("AlerteDirectionId requis");
    }
}
