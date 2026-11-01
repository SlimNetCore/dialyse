package com.hemodialyse.backend.contexts.organisation.publishedlanguage;

import java.time.Instant;
import java.util.UUID;

public record SalleFermee(UUID salleId, UUID centreId, String code, Instant survenuLe) {
}
