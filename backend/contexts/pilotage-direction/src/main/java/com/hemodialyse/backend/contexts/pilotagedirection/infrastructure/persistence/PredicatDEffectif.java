package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.persistence;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.Periode;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

/**
 * Prédicat SQL de l'EFFECTIF d'une période (RG-PAT-032) : un patient est compté s'il a été admis au plus tard le dernier jour de la période
 * et s'il n'était pas déjà sorti avant son premier jour. Repris MOT POUR MOT de {@code EffectifSql.presentSur} (plateforme, retiré avec
 * PD1 : son seul usage restant était la Direction), lui-même fidèle à la règle {@code FinOccupation.dernierJourOccupe} de l'ancien module
 * patient ; les listes d'états sont celles de cette règle, triées.
 * <ul>
 *   <li>sortie sans date : le patient n'est plus compté ; avec une date, TRANSFERE et GUERRI jusqu'à la date incluse, DECEDE et GREFFE
 *   jusqu'à la veille (la place est libérée le jour même) ;</li>
 *   <li>séjour limité (OCCASIONNEL, VACANCIER_LOCAL, VACANCIER_ETRANGER) : jusqu'à la fin de séjour incluse, non borné sans date.</li>
 * </ul>
 * Compatible H2 / PostgreSQL (aucune arithmétique de dates). Paramètres à lier dans l'ordre : fin, début, début, début.
 */
final class PredicatDEffectif {

    private static final String SORTIES = "'DECEDE','GREFFE','GUERRI','TRANSFERE'";
    private static final String SEJOURS = "'OCCASIONNEL','VACANCIER_ETRANGER','VACANCIER_LOCAL'";

    private final String sql;
    private final List<Object> arguments;

    private PredicatDEffectif(String sql, List<Object> arguments) {
        this.sql = sql;
        this.arguments = arguments;
    }

    /** @param alias alias de la table {@code patients} dans la requête */
    static PredicatDEffectif presentSur(String alias, Periode periode) {
        String a = alias + ".";
        String sql = "(" + a + "date_admission IS NULL OR " + a + "date_admission <= ?) AND ("
                + a + "etat_patient IS NULL OR " + a + "etat_patient NOT IN (" + SORTIES + "," + SEJOURS + ") "
                // séjour limité : jusqu'à la fin de séjour incluse (sans date : non borné)
                + "OR (" + a + "etat_patient IN (" + SEJOURS + ") AND (" + a + "date_evenement_etat IS NULL OR "
                + a + "date_evenement_etat >= ?)) "
                // transfert, guérison : dernière présence = la date incluse
                + "OR (" + a + "etat_patient IN ('TRANSFERE','GUERRI') AND " + a + "date_evenement_etat IS NOT NULL AND "
                + a + "date_evenement_etat >= ?) "
                // décès, greffe : place libérée à la date (dernière présence = la veille)
                + "OR (" + a + "etat_patient IN ('DECEDE','GREFFE') AND " + a + "date_evenement_etat IS NOT NULL AND "
                + a + "date_evenement_etat > ?))";
        List<Object> args = new ArrayList<>();
        args.add(Date.valueOf(periode.fin()));
        args.add(Date.valueOf(periode.debut()));
        args.add(Date.valueOf(periode.debut()));
        args.add(Date.valueOf(periode.debut()));
        return new PredicatDEffectif(sql, args);
    }

    String sql() {
        return sql;
    }

    /** Les identifiants de centre puis les paramètres du prédicat, dans l'ordre des {@code ?} de la requête. */
    Object[] arguments(List<?> centres) {
        List<Object> tous = new ArrayList<>(centres);
        tous.addAll(arguments);
        return tous.toArray();
    }
}
