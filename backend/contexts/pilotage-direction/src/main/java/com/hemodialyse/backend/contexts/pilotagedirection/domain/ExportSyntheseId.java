package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import java.util.UUID;

public record ExportSyntheseId(UUID value) {
    public ExportSyntheseId {
        if (value == null) throw new IllegalArgumentException("ExportSyntheseId requis");
    }
}
