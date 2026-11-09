package com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Langage publié du Pilotage direction pour les alertes de la direction ({@code pilotagedirection.v1}, PD3, ADR 0104). Les enregistrements
 * portent les NOMS DU JSON servi par la plateforme ({@code centerId}, {@code severity}, {@code firstSeenAt}, {@code resolvedAt}) : ce sont des
 * valeurs de contrat de {@code /indicators}, {@code /gmao} et {@code /alerts/history}, pas des choix de nommage.
 */
public interface AlertesDeLaDirection {

    /**
     * Les alertes que lèvent les indicateurs d'un centre (politique du Pilotage : seuils Kt/V 80 %, hémoglobine 50 %, observance, stock,
     * lots périmés, lots à péremption proche). Les taux masqués par l'anonymat sont {@code null} et ne lèvent jamais d'alerte de taux.
     */
    List<Alerte> evaluer(UUID centreId, String centre, BigDecimal ktVDansCiblePct, BigDecimal hbDansCiblePct,
                         boolean patientsEnRetardObservance, long articlesSousSeuil, long lotsPerimes, long lotsPeremptionProche);

    /** Compare les alertes levées à l'historique ouvert de la société : ouvre les nouvelles, referme celles qui ont disparu. */
    void enregistrer(UUID societeId, List<Alerte> alertes);

    /** L'historique récent (en cours puis résolues), du plus récemment actif au plus ancien ; {@code limite} est bornée à [1, 200]. */
    List<EntreeDHistorique> historique(UUID societeId, int limite);

    enum Gravite {WARNING, CRITICAL}

    /** Une alerte levée ; {@code valeur} est nulle quand l'alerte n'en porte pas. Aussi utilisée par les alertes GMAO de la plateforme. */
    record Alerte(UUID centerId, String centre, String code, Gravite severity, BigDecimal valeur) {
    }

    record EntreeDHistorique(UUID centerId, String centre, String code, String severity, BigDecimal valeur,
                             OffsetDateTime firstSeenAt, OffsetDateTime resolvedAt) {
    }
}
