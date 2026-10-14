package com.hemodialyse.backend.contexts.gmao.domain.equipement;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Temps d'indisponibilité d'un équipement sur une période (R29, RG-GMA-060) : durée cumulée passée en maintenance, en attente
 * de pièce ou hors service, rejouée sur l'historique des statuts. {@code DESACTIF} et {@code REFORME} sont des fins de vie,
 * pas une indisponibilité de maintenance : ils ne comptent pas. Service de domaine pur, sans état.
 */
public final class CalculDIndisponibilite {

    private static final Set<StatutEquipement> STATUTS_INDISPONIBLES = EnumSet.of(
            StatutEquipement.EN_MAINTENANCE, StatutEquipement.EN_ATTENTE_PIECE, StatutEquipement.HORS_SERVICE);

    private CalculDIndisponibilite() {
    }

    /**
     * @param historique    changements de statut, du plus ancien au plus récent
     * @param statutCourant statut actuel : il décide de l'intervalle sans historique et ferme l'intervalle ouvert jusqu'à {@code au}
     * @param du            début de la période (inclus)
     * @param au            fin de la période (exclue)
     * @return la durée cumulée en statut indisponible sur la période, nulle si la période est vide
     */
    public static Duration calculer(List<ChangementDeStatut> historique, StatutEquipement statutCourant, Instant du,
                                    Instant au) {
        if (!du.isBefore(au)) return Duration.ZERO;

        Duration total = Duration.ZERO;
        Instant debutDeIntervalle = du;
        StatutEquipement statut = historique.isEmpty() ? statutCourant : historique.get(0).precedent();
        for (ChangementDeStatut changement : historique) {
            if (changement.le().isAfter(au)) break;
            total = total.plus(tempsIndisponible(statut, debutDeIntervalle.isBefore(du) ? du : debutDeIntervalle,
                    borner(changement.le(), du, au)));
            debutDeIntervalle = changement.le();
            statut = changement.nouveau();
        }
        return total.plus(tempsIndisponible(statut, debutDeIntervalle.isBefore(du) ? du : debutDeIntervalle, au));
    }

    private static Duration tempsIndisponible(StatutEquipement statut, Instant debut, Instant fin) {
        if (!STATUTS_INDISPONIBLES.contains(statut) || !debut.isBefore(fin)) return Duration.ZERO;
        return Duration.between(debut, fin);
    }

    private static Instant borner(Instant instant, Instant du, Instant au) {
        if (instant.isBefore(du)) return du;
        return instant.isAfter(au) ? au : instant;
    }
}
