package com.hemodialyse.backend.contexts.documents.application;

import com.hemodialyse.backend.contexts.documents.domain.IdentiteImprimable;

import java.util.Optional;
import java.util.UUID;

/** Port sortant : l'identité imprimée d'un centre ou d'une société, donnée d'Organisation (ACL, ADR 0140 D4). Lecture seule. */
public interface IdentitesImprimables {

    /** L'identité imprimée d'un centre et de sa société ; vide si le centre est inconnu (ou {@code null}). */
    Optional<IdentiteImprimable> duCentre(UUID centre);

    /** L'identité imprimée de la seule société ; vide si elle est inconnue (ou {@code null}). */
    Optional<IdentiteImprimable.Societe> deLaSociete(UUID societe);
}
