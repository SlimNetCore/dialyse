package com.hemodialyse.backend.contexts.organisation.publishedlanguage;

import java.util.UUID;

public record MachineReference(UUID id, UUID centreId, String code, String libelle, boolean actif) {
}
