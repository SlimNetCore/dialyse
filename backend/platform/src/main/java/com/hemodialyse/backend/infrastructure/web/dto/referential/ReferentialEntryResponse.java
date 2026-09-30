package com.hemodialyse.backend.infrastructure.web.dto.referential;

import com.hemodialyse.backend.domain.referential.admin.model.ReferentialEntry;

import java.util.Map;
import java.util.UUID;

public record ReferentialEntryResponse(UUID id, Map<String, String> values, Map<String, String> references) {

    public static ReferentialEntryResponse from(ReferentialEntry entry) {
        return new ReferentialEntryResponse(entry.id(), entry.values(), entry.references());
    }
}

