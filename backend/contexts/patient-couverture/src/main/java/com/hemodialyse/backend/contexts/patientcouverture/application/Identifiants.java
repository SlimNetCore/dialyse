package com.hemodialyse.backend.contexts.patientcouverture.application;

import java.util.UUID;

/** Port sortant : génération des identifiants (patient, mouvement) et du code patient {@code PAT-XXXXXXXX}. */
public interface Identifiants {

    UUID nouveau();

    /** Code patient lisible, unique : {@code PAT-} suivi de 8 caractères hexadécimaux en majuscules. */
    String nouveauCodePatient();
}
