package com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage;

import java.util.UUID;

/**
 * Langage publié (besoin du Dossier médical, fourni par la plateforme : centre de notifications, hors contexte) : prévient le centre
 * qu'une prescription du patient a changé (diffusion temps réel {@code PRESCRIPTION_CHANGED} vers les infirmiers, médecins et
 * administrateurs, sans notification durable). L'adaptateur attend la validation de la transaction en cours pour diffuser. Appelé
 * aussi pour une suppression sans effet (reproduit du legacy, KB-MED-25 : minimisation à décider).
 */
public interface SignalementDesPrescriptions {

    void prescriptionChangee(UUID centre, UUID patient);
}
