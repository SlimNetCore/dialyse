package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;
import java.util.UUID;

/** Port sortant : le générateur affecté à un patient, tel que le GMAO le décrit (lecture seule, bornée au centre). */
public interface GenerateursDuCentre {

    record Generateur(UUID id, String code, String fabricant, String statut) {
    }

    /** Vide si le générateur n'existe pas dans le centre ou a été supprimé. */
    Optional<Generateur> trouver(CentreId centre, UUID id);
}
