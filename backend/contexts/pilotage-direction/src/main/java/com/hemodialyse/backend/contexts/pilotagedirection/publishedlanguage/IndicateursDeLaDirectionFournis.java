package com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Les indicateurs d'une société sur une période SOUS LA FORME QUE LIT LE RAPPORT DE SYNTHÈSE ({@code pilotagedirection.v1}, ADR 0102, PD4) :
 * indicateurs médicaux, de stock et alertes, DÉJÀ MASQUÉS (seuil d'anonymat). Depuis PD6 (ADR 0106) la plateforme ne les fournit plus : le
 * Pilotage les compose ({@link IndicateursDeLaDirection}) et un adaptateur du module les traduit vers ce port. Les enregistrements restent le
 * contrat du rapport ({@code SourceDuRapport}), qui sert aussi à relire un instantané figé (résiduel PD6-R1 : nom du port à renommer avec
 * le prochain changement de version du langage publié).
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
