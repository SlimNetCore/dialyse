package com.hemodialyse.backend.contexts.organisation.publishedlanguage;

import java.time.Instant;
import java.util.UUID;

public record CreneauCree(UUID creneauId, UUID centreId, String code, String libelle, Instant survenuLe) {
}
