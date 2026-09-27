package com.hemodialyse.backend.application.direction;

import java.util.Optional;
import java.util.UUID;

/**
 * Port de sortie : identité d'une société pour l'en-tête (logo, raison sociale, coordonnées, mentions légales) et le
 * pied de page de ses documents de direction. Implémenté par l'infrastructure.
 */
public interface SocieteLetterheadPort {

    Optional<Letterhead> forSociete(UUID societeId);

    /**
     * @param nom      raison sociale
     * @param contact  adresse et coordonnées sur une ligne
     * @param legal    NIF, NIS, RC sur une ligne
     * @param piedPage texte de pied de page défini par le propriétaire
     * @param logo     image (PNG ou JPEG), ou {@code null}
     */
    record Letterhead(String nom, String contact, String legal, String piedPage, byte[] logo) {
    }
}
