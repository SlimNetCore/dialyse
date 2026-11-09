package com.hemodialyse.backend.characterization.patientcouverture.pc4;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Prédicat SQL d'effectif de RÉFÉRENCE (RG-PAT-032), copie figée de l'ancien {@code EffectifSql.presentSur} de la plateforme, retiré avec
 * PD1 (ADR 0102) : les tests PC4 et PC5 gardent ainsi une règle indépendante des modules pour comparer le compteur publié du
 * Patient et couverture ; le module Pilotage direction porte sa propre copie ({@code PredicatDEffectif}). Réservé aux tests.
 */
public final class EffectifDeReference {

    private static final String SORTIES = "'DECEDE','GREFFE','GUERRI','TRANSFERE'";
    private static final String SEJOURS = "'OCCASIONNEL','VACANCIER_ETRANGER','VACANCIER_LOCAL'";

    private EffectifDeReference() {
    }

    public static Fragment presentSur(String alias, LocalDate debut, LocalDate fin) {
        String a = alias + ".";
        String sql = "(" + a + "date_admission IS NULL OR " + a + "date_admission <= ?) AND ("
                + a + "etat_patient IS NULL OR " + a + "etat_patient NOT IN (" + SORTIES + "," + SEJOURS + ") "
                + "OR (" + a + "etat_patient IN (" + SEJOURS + ") AND (" + a + "date_evenement_etat IS NULL OR "
                + a + "date_evenement_etat >= ?)) "
                + "OR (" + a + "etat_patient IN ('TRANSFERE','GUERRI') AND " + a + "date_evenement_etat IS NOT NULL AND "
                + a + "date_evenement_etat >= ?) "
                + "OR (" + a + "etat_patient IN ('DECEDE','GREFFE') AND " + a + "date_evenement_etat IS NOT NULL AND "
                + a + "date_evenement_etat > ?))";
        List<Object> args = new ArrayList<>();
        args.add(Date.valueOf(fin));
        args.add(Date.valueOf(debut));
        args.add(Date.valueOf(debut));
        args.add(Date.valueOf(debut));
        return new Fragment(sql, args);
    }

    public record Fragment(String sql, List<Object> args) {
    }
}
