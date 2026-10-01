package com.hemodialyse.backend.domain.gmao.service;

import com.hemodialyse.backend.domain.gmao.model.EquipementStatutHistorique;
import com.hemodialyse.backend.domain.gmao.model.StatutEquipement;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Calcule le temps d'indisponibilité (durée cumulée passée dans un statut "indisponible") d'un équipement
 * sur une période, à partir de son historique de changements de statut.
 * <p>
 * Service de domaine pur, sans effet de bord ni dépendance Spring/JPA (AGENTS.md §3), au même patron que
 * {@code PmpCalculator} (bc-stock) — pleinement testable isolément.
 * <p>
 * Choix assumé : {@link StatutEquipement#DESACTIF} et {@link StatutEquipement#REFORME} sont des statuts
 * de fin de vie, pas une "indisponibilité temporaire" due à la maintenance — ils ne comptent donc pas dans
 * ce calcul (voir le module GMAO v2, section Risques et compromis assumés).
 */
public final class IndisponibiliteCalculator {

    private static final Set<StatutEquipement> STATUTS_INDISPONIBLES = EnumSet.of(
            StatutEquipement.EN_MAINTENANCE,
            StatutEquipement.EN_ATTENTE_PIECE,
            StatutEquipement.HORS_SERVICE
    );

    private IndisponibiliteCalculator() {
    }

    /**
     * @param historique    historique de l'équipement, trié du plus ancien au plus récent
     * @param statutCourant statut actuel de l'équipement (pour calculer l'intervalle ouvert jusqu'à {@code to})
     * @param from          début de la période (inclus)
     * @param to            fin de la période (exclus) — généralement {@code LocalDateTime.now()}
     * @return la durée cumulée passée dans un statut indisponible sur la période, jamais négative
     */
    public static Duration calculer(
            List<EquipementStatutHistorique> historique,
            StatutEquipement statutCourant,
            LocalDateTime from,
            LocalDateTime to) {

        if (from == null || to == null || !from.isBefore(to)) {
            return Duration.ZERO;
        }

        Duration total = Duration.ZERO;
        LocalDateTime intervalStart = from;
        StatutEquipement intervalStatut = historique.isEmpty() ? statutCourant : historique.get(0).getStatutPrecedent();

        for (EquipementStatutHistorique entree : historique) {
            LocalDateTime changedAt = entree.getChangedAt();
            if (changedAt.isAfter(to)) {
                break;
            }
            total = total.plus(downDuration(intervalStatut, clampStart(intervalStart, from), clampEnd(changedAt, from, to)));
            intervalStart = changedAt;
            intervalStatut = entree.getStatutNouveau();
        }

        total = total.plus(downDuration(intervalStatut, clampStart(intervalStart, from), to));

        return total.isNegative() ? Duration.ZERO : total;
    }

    private static Duration downDuration(StatutEquipement statut, LocalDateTime start, LocalDateTime end) {
        if (statut == null || !STATUTS_INDISPONIBLES.contains(statut) || !start.isBefore(end)) {
            return Duration.ZERO;
        }
        return Duration.between(start, end);
    }

    private static LocalDateTime clampStart(LocalDateTime value, LocalDateTime from) {
        return value.isBefore(from) ? from : value;
    }

    private static LocalDateTime clampEnd(LocalDateTime value, LocalDateTime from, LocalDateTime to) {
        if (value.isBefore(from)) return from;
        if (value.isAfter(to)) return to;
        return value;
    }
}
