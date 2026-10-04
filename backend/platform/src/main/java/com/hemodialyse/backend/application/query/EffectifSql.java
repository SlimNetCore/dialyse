package com.hemodialyse.backend.application.query;

import com.hemodialyse.backend.domain.patient.service.FinOccupation;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Prédicat SQL de l'<b>effectif</b> d'une période : un patient est compté s'il a été admis au plus tard le dernier jour
 * de la période et s'il n'était pas déjà sorti avant son premier jour. La sortie (transfert, décès, greffe, guérison)
 * ou la fin de séjour est lue à la date d'évènement : un patient dont l'évènement tombe <b>pendant ou après</b> la
 * période est compté, un patient dont l'évènement précède la période ne l'est pas (règle unique de
 * {@link FinOccupation#dernierJourOccupe}, RG-PAT-032). Utilisé partout où l'on compte des patients : liste, synthèse
 * mensuelle, tableau de bord de la direction, capacité.
 * <p>Compatible H2 / PostgreSQL (aucune arithmétique de dates).
 */
public final class EffectifSql {

    private static final String SORTIES = quoted(FinOccupation.ETATS_DE_SORTIE.stream().sorted().toList());
    private static final String SEJOURS = quoted(FinOccupation.ETATS_SEJOUR_LIMITE.stream().sorted().toList());

    private EffectifSql() {
    }

    private static String quoted(List<String> values) {
        return values.stream().map(v -> "'" + v + "'").collect(Collectors.joining(","));
    }

    /**
     * Fragment {@code AND}-able, avec les paramètres à lier dans l'ordre ({@code ?} : fin, début, début, début).
     *
     * @param alias alias de la table {@code patients} dans la requête
     */
    public static Fragment presentSur(String alias, java.time.LocalDate debut, java.time.LocalDate fin) {
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
        args.add(Date.valueOf(fin));
        args.add(Date.valueOf(debut));
        args.add(Date.valueOf(debut));
        args.add(Date.valueOf(debut));
        return new Fragment(sql, args);
    }

    /**
     * Même règle en Java (synthèse mensuelle lue ligne à ligne).
     */
    public static boolean present(String etat, java.time.LocalDate dateEvenement, java.time.LocalDate dateAdmission,
                                  java.time.LocalDate debut, java.time.LocalDate fin) {
        if (dateAdmission != null && dateAdmission.isAfter(fin)) return false;
        return FinOccupation.dernierJourOccupe(etat, dateEvenement).map(dernier -> !dernier.isBefore(debut)).orElse(true);
    }

    /**
     * Condition SQL et ses paramètres.
     */
    public record Fragment(String sql, List<Object> args) {
    }
}
