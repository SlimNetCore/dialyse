package com.hemodialyse.backend.contexts.documents.domain;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * La demande d'impression d'un document d'un centre : par type ({@code POST /print}) ou par modèle ({@code POST /print/{modeleId}}).
 * Règles reprises de {@code DocumentRestController.validatePrintRequest} ; en plus, un identifiant de document qui n'est pas un UUID est
 * refusé (400) au lieu de faire échouer le moteur (500, KB-DOC1-5 c, ADR 0142).
 *
 * @param type          le type normalisé (sans blancs de bord, en majuscules) ; {@code null} pour une impression par modèle
 * @param formatDemande le format imposé par le demandeur, {@code null} pour celui du modèle
 * @param parametres    les paramètres du demandeur, valeurs sans blancs de bord (une valeur peut être nulle)
 */
public record DemandeDImpression(UUID centre, String type, String formatDemande, Map<String, String> parametres) {

    public static DemandeDImpression parType(UUID centre, String type, String formatDemande, Map<String, String> parametres) {
        if (centre == null) throw new DemandeInvalide("centerId est obligatoire");
        if (type == null || type.isBlank()) throw new DemandeInvalide("typeDocument est obligatoire");
        String normalise = type.trim().toUpperCase(Locale.ROOT);
        Map<String, String> params = normaliser(parametres);
        verifierLesIdentifiants(normalise, params);
        return new DemandeDImpression(centre, normalise, formatDemande, params);
    }

    /** Par modèle : le type du modèle décide, aucune règle par type n'est appliquée (comportement historique). */
    public static DemandeDImpression parModele(UUID centre, String formatDemande, Map<String, String> parametres) {
        if (centre == null) throw new DemandeInvalide("centerId est obligatoire");
        return new DemandeDImpression(centre, null, formatDemande, normaliser(parametres));
    }

    private static Map<String, String> normaliser(Map<String, String> brut) {
        if (brut == null || brut.isEmpty()) return Map.of();
        Map<String, String> normalise = new HashMap<>();
        brut.forEach((k, v) -> normalise.put(k, v == null ? null : v.trim()));
        return Collections.unmodifiableMap(normalise);
    }

    private static void verifierLesIdentifiants(String type, Map<String, String> params) {
        switch (type) {
            case "FICHE_PATIENT" -> {
                exiger(absent(params, "patientId"), "patientId est obligatoire pour " + type);
                lisible(params, "patientId");
            }
            case "ATTESTATION" -> {
                exiger(absent(params, "patientId") && absent(params, "attestationId"), "patientId ou attestationId est obligatoire pour ATTESTATION");
                lisible(params, "patientId");
                lisible(params, "attestationId");
            }
            case "PEC" -> {
                exiger(absent(params, "patientId") && absent(params, "pecId"), "patientId ou pecId est obligatoire pour PEC");
                lisible(params, "patientId");
                lisible(params, "pecId");
            }
            case "ORDONNANCE" -> {
                exiger(absent(params, "ordonnanceId"), "ordonnanceId est obligatoire pour ORDONNANCE");
                lisible(params, "ordonnanceId");
            }
            case "SYNTHESE_FACTURATION_MENSUELLE" -> exiger(absent(params, "PERIOD_START") || absent(params, "PERIOD_END"),
                    "PERIOD_START et PERIOD_END sont obligatoires pour SYNTHESE_FACTURATION_MENSUELLE");
            default -> {
            }
        }
    }

    private static boolean absent(Map<String, String> params, String nom) {
        String v = params.get(nom);
        return v == null || v.isBlank();
    }

    private static void exiger(boolean refus, String message) {
        if (refus) throw new DemandeInvalide(message);
    }

    private static void lisible(Map<String, String> params, String nom) {
        if (absent(params, nom)) return;
        try {
            UUID.fromString(params.get(nom));
        } catch (IllegalArgumentException e) {
            throw new DemandeInvalide(nom + " illisible : un identifiant est attendu");
        }
    }
}
