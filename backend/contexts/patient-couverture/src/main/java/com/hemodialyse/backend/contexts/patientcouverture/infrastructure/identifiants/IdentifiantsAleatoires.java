package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.identifiants;

import com.hemodialyse.backend.contexts.patientcouverture.application.Identifiants;

import java.util.Locale;
import java.util.UUID;

/** Identifiants aléatoires ; le code patient reste {@code PAT-} et 8 caractères hexadécimaux en majuscules (format actuel). */
public class IdentifiantsAleatoires implements Identifiants {

    @Override
    public UUID nouveau() {
        return UUID.randomUUID();
    }

    @Override
    public String nouveauCodePatient() {
        return "PAT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }
}
