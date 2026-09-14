package com.hemodialyse.backend.infrastructure.web.dto.request;

import java.util.UUID;

public record UpsertNotesBilanGreffeRequest(
        UUID centerId,
        String contreIndications,
        String conclusionNephrologue
) {
}
