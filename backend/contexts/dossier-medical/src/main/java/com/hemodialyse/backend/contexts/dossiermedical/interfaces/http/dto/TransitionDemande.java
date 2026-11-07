package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import java.util.UUID;

/** Corps d'une transition (signer, imprimer, annuler) : le centre, facultatif (par défaut celui de la session). */
public record TransitionDemande(UUID centerId) {
}
