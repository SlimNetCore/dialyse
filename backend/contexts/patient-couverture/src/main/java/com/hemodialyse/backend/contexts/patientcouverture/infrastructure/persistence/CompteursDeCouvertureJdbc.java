package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.CompteursDeCouverture;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Adapter du port publié {@link CompteursDeCouverture} : les comptes du tableau de bord administratif, reprises À L'IDENTIQUE
 * (PC5, comportement inchangé). La période est comparée à {@code created_at} avec des dates (comme avant). L'effectif d'un jour
 * reprend le prédicat de l'effectif de l'ancien {@code EffectifSql} (mêmes états, mêmes paramètres), pour un jour seul.
 */
public class CompteursDeCouvertureJdbc implements CompteursDeCouverture {

    private static final String SORTIES = "'DECEDE','GREFFE','GUERRI','TRANSFERE'";
    private static final String SEJOURS = "'OCCASIONNEL','VACANCIER_ETRANGER','VACANCIER_LOCAL'";

    private final JdbcTemplate jdbc;

    public CompteursDeCouvertureJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static long compte(Long n) {
        return n != null ? n : 0;
    }

    @Override
    public long prisesEnCharge(UUID centre, String statut, LocalDate du, LocalDate au) {
        if (du == null || au == null) {
            return compte(jdbc.queryForObject("SELECT COUNT(1) FROM prise_en_charge WHERE center_id = ? AND statut = ?", Long.class,
                    centre, statut));
        }
        return compte(jdbc.queryForObject(
                "SELECT COUNT(1) FROM prise_en_charge WHERE center_id = ? AND statut = ? AND created_at >= ? AND created_at <= ?",
                Long.class, centre, statut, du, au));
    }

    @Override
    public long prisesEnChargeValideesAEcheance(UUID centre, LocalDate seuil) {
        return compte(jdbc.queryForObject(
                "SELECT COUNT(1) FROM prise_en_charge WHERE center_id = ? AND statut = 'VALIDEE' AND date_fin_demande <= ?",
                Long.class, centre, seuil));
    }

    @Override
    public long attestations(UUID centre, LocalDate du, LocalDate au) {
        if (du == null || au == null) {
            return compte(jdbc.queryForObject("SELECT COUNT(1) FROM attestation_droit WHERE center_id = ?", Long.class, centre));
        }
        return compte(jdbc.queryForObject(
                "SELECT COUNT(1) FROM attestation_droit WHERE center_id = ? AND created_at >= ? AND created_at <= ?",
                Long.class, centre, du, au));
    }

    @Override
    public long attestationsAEcheance(UUID centre, LocalDate seuil) {
        return compte(jdbc.queryForObject("SELECT COUNT(1) FROM attestation_droit WHERE center_id = ? AND date_fin <= ?",
                Long.class, centre, seuil));
    }

    @Override
    public long patientsCrees(UUID centre, LocalDate du, LocalDate au) {
        return compte(jdbc.queryForObject(
                "SELECT COUNT(1) FROM patients WHERE center_id = ? AND created_at >= ? AND created_at <= ?",
                Long.class, centre, du, au));
    }

    @Override
    public long patientsPresentsLe(UUID centre, LocalDate jour) {
        String a = "p.";
        String effectif = "(" + a + "date_admission IS NULL OR " + a + "date_admission <= ?) AND ("
                + a + "etat_patient IS NULL OR " + a + "etat_patient NOT IN (" + SORTIES + "," + SEJOURS + ") "
                + "OR (" + a + "etat_patient IN (" + SEJOURS + ") AND (" + a + "date_evenement_etat IS NULL OR "
                + a + "date_evenement_etat >= ?)) "
                + "OR (" + a + "etat_patient IN ('TRANSFERE','GUERRI') AND " + a + "date_evenement_etat IS NOT NULL AND "
                + a + "date_evenement_etat >= ?) "
                + "OR (" + a + "etat_patient IN ('DECEDE','GREFFE') AND " + a + "date_evenement_etat IS NOT NULL AND "
                + a + "date_evenement_etat > ?))";
        Date j = Date.valueOf(jour);
        return compte(jdbc.queryForObject("SELECT COUNT(1) FROM patients p WHERE p.center_id = ? AND " + effectif, Long.class,
                centre, j, j, j, j));
    }
}
