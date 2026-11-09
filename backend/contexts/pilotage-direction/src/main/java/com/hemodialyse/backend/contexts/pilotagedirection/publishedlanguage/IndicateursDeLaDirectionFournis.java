package com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Port FOURNI au Pilotage direction par la plateforme ({@code pilotagedirection.v1}, ADR 0102, PD4) : les indicateurs médicaux, de
 * stock et les alertes d'une société sur une période, DÉJÀ MASQUÉS (seuil d'anonymat), tels que la route {@code /indicators} les sert.
 * Ils restent calculés par la plateforme depuis DM9 (ADR 0101) ; le Pilotage ne les lit que pour constituer le rapport de synthèse.
 * <p>
 * Les valeurs absentes ({@code null}) sont des valeurs masquées ou inconnues : le rapport les imprime « &lt; seuil » ou « — ».
 */
public interface IndicateursDeLaDirectionFournis {

    IndicateursDeLaPeriode pour(UUID societeId, LocalDate from, LocalDate to);

    /** {@code pctDansCible} de chaque cible KDIGO ; les effectifs sont masqués (nuls sous le seuil). */
    record Clinique(BigDecimal ktV, BigDecimal hemoglobine, BigDecimal phosphore, BigDecimal pth, BigDecimal albumine,
                    Long vhbPositifs, Long vhcPositifs, Long vihPositifs, Long patientsObservanceEnRetard,
                    Long greffeListeAttente, Long greffeBilanEnCours, Long greffesPeriode) {
    }

    record Stock(Long articlesActifs, Long articlesSousSeuil, Long lotsPerimes, Long lotsPeremptionProche, BigDecimal valeurStock) {
    }

    record IndicateursDuCentre(String nom, Clinique clinique, Stock stock) {
    }

    /** {@code severity} : {@code CRITICAL} ou {@code WARNING} ; {@code valeur} nulle quand l'alerte n'en porte pas. */
    record AlerteDeLaDirection(String centre, String code, String severity, BigDecimal valeur) {
    }

    record IndicateursDeLaPeriode(List<IndicateursDuCentre> centres, IndicateursDuCentre totaux, List<AlerteDeLaDirection> alertes) {
    }
}
