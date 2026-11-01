package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import java.util.UUID;

public record InstantaneMensuelId(UUID value) {
    public InstantaneMensuelId {
        if (value == null) throw new IllegalArgumentException("InstantaneMensuelId requis");
    }
}
