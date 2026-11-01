package com.hemodialyse.backend.contexts.organisation.publishedlanguage;

import java.util.UUID;

public record ProtocoleReference(UUID id, UUID centreId, String code, String libelle) {
}
