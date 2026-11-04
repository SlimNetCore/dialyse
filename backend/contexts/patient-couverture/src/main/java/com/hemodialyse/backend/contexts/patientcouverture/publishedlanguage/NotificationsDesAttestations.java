package com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage;

import java.util.UUID;

/**
 * Port publié (implémenté par la plateforme) : prévient les écrans connectés du centre qu'une attestation de droits est créée ou
 * supprimée (diffusions {@code ATTESTATION_CREATED} et {@code ATTESTATION_DELETED}, sans notification durable). Une suppression
 * d'un identifiant inconnu ne diffuse plus rien (décision du 2026-11-04, KB-PAT-40).
 */
public interface NotificationsDesAttestations {

    void attestationCreee(UUID centre, UUID attestation, UUID patient);

    void attestationSupprimee(UUID centre, UUID attestation, UUID patient);
}
