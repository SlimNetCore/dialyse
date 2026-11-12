package com.hemodialyse.backend.contexts.documents.publishedlanguage;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Papier en-tête publié par Documents (documents.v1, ADR 0140 D2) : l'identité imprimée du centre et de sa société (logo, en-tête, pied de
 * page). Promu du port que le Dossier médical définissait pour la plateforme (ADR 0142) ; chaque consommateur le lit par son propre port
 * (ACL côté consommateur).
 */
public interface PapierEnTeteDuCentre {

    /** Le document HTML avec l'en-tête et le pied de page d'identité du centre ; rendu tel quel si le document n'a pas de corps. */
    String habiller(String html, UUID centre);

    /**
     * Les paramètres de gabarit d'identité du centre ({@code SOCIETE_*}, {@code CENTRE_*}) ; textes vides si le centre est inconnu, logo
     * ({@code SOCIETE_LOGO}, un flux neuf à chaque appel) nul s'il est absent. Jamais nul.
     */
    Map<String, Object> parametresDeLEnTete(UUID centre);

    /** Retire des paramètres d'un appelant tout nom d'identité ({@code SOCIETE_*}, {@code CENTRE_*}) : seul le papier en-tête les pose. */
    Map<String, Object> ecarterLIdentiteDeLAppelant(Map<String, Object> parametres);

    /** L'en-tête d'une société seule (documents de direction, sans centre) ; vide si la société est inconnue. */
    Optional<EnTeteDeSociete> enTeteDeLaSociete(UUID societe);

    /**
     * @param nom        raison sociale
     * @param contact    adresse et coordonnées sur une ligne
     * @param legal      NIF, NIS, RC sur une ligne
     * @param piedDePage texte de pied de page défini par le propriétaire
     * @param logo       image (PNG ou JPEG), {@code null} si absente ; ne pas modifier le tableau
     */
    record EnTeteDeSociete(String nom, String contact, String legal, String piedDePage, byte[] logo) {
    }
}
