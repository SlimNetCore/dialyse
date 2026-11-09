package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.persistence;

import com.hemodialyse.backend.contexts.pilotagedirection.application.EffectifsDesPatients;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.EffectifDuCentre;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.Periode;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete.PatientsDeLaCaisse;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete.PrescriptionsDeLAnemie;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete.ProfilDUnPatient;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lecteur TRANSITOIRE de la table {@code patients} et de la chaîne payeur / agence / caisse : agrégats de la Direction avec le prédicat
 * d'effectif. Jalon inscrit dans {@code TableDuPatientCouvertureTest} (2026-11-09, ADR 0102 ; jointure de la caisse dans un GROUP BY, la
 * découper changerait le résultat) et dans {@code TableDeLOrganisationTest} n'est PAS nécessaire (aucune table de l'Organisation) ;
 * échéance : langage publié du Patient (effectif d'une période, profil et caisse), revue au plus tard le 2026-12-15, butoir 2027-03-31.
 * Lecture seule, AUCUNE ligne n'identifie un patient (sexe, naissance et centre seulement).
 * <p>
 * SQL repris MOT POUR MOT des deux services retirés (commit {@code 9c21d51a}) : effectif et patients sous KT
 * {@code DirectionDashboardQueryService.java:112-116}, profils {@code DirectionBreakdownQueryService.java:171-181}, patients par caisse
 * {@code :186-192} (jointure {@code CAISSE_JOIN :42-45}), patients sous EPO / fer {@code :227-235}.
 */
public final class PatientsDesCentresJdbc implements EffectifsDesPatients {

    private static final String CAISSE_JOIN =
            "LEFT JOIN centre_payeur cp ON cp.id = p.centre_payeur_id "
                    + "LEFT JOIN agence ag ON ag.id = cp.agence_id "
                    + "LEFT JOIN caisse_assurance ca ON ca.id = ag.caisse_id ";

    private final JdbcTemplate jdbc;

    public PatientsDesCentresJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static String marques(List<UUID> centres) {
        return String.join(",", Collections.nCopies(centres.size(), "?"));
    }

    @Override
    public Map<UUID, EffectifDuCentre> parCentre(List<UUID> centres, Periode periode) {
        Map<UUID, EffectifDuCentre> effectifs = new HashMap<>();
        PredicatDEffectif effectif = PredicatDEffectif.presentSur("p", periode);
        jdbc.query("SELECT p.center_id, COUNT(*) AS total, SUM(CASE WHEN p.sous_kt = TRUE THEN 1 ELSE 0 END) AS kt "
                        + "FROM patients p WHERE p.center_id IN (" + marques(centres) + ") AND " + effectif.sql() + " GROUP BY p.center_id",
                rs -> {
                    effectifs.put(rs.getObject("center_id", UUID.class), new EffectifDuCentre(rs.getLong("total"), rs.getLong("kt")));
                }, effectif.arguments(centres));
        return effectifs;
    }

    @Override
    public List<ProfilDUnPatient> profils(List<UUID> centres, Periode periode) {
        PredicatDEffectif effectif = PredicatDEffectif.presentSur("p", periode);
        return jdbc.query("SELECT p.center_id, p.sexe, p.date_naissance FROM patients p WHERE p.center_id IN (" + marques(centres)
                        + ") AND " + effectif.sql(),
                (rs, i) -> {
                    java.sql.Date naissance = rs.getDate("date_naissance");
                    return new ProfilDUnPatient(rs.getObject("center_id", UUID.class), rs.getString("sexe"),
                            naissance == null ? null : naissance.toLocalDate());
                }, effectif.arguments(centres));
    }

    @Override
    public List<PatientsDeLaCaisse> parCaisse(List<UUID> centres, Periode periode) {
        PredicatDEffectif effectif = PredicatDEffectif.presentSur("p", periode);
        return jdbc.query("SELECT p.center_id, COALESCE(ca.code, '') AS code, MAX(ca.nom) AS nom, COUNT(*) AS n "
                        + "FROM patients p " + CAISSE_JOIN + "WHERE p.center_id IN (" + marques(centres) + ") AND " + effectif.sql() + " "
                        + "GROUP BY p.center_id, COALESCE(ca.code, '')",
                (rs, i) -> new PatientsDeLaCaisse(rs.getObject("center_id", UUID.class), rs.getString("code"), rs.getString("nom"),
                        rs.getLong("n")),
                effectif.arguments(centres));
    }

    @Override
    public Map<UUID, PrescriptionsDeLAnemie> prescriptionsDeLAnemie(List<UUID> centres, Periode periode) {
        Map<UUID, PrescriptionsDeLAnemie> prescriptions = new HashMap<>();
        PredicatDEffectif effectif = PredicatDEffectif.presentSur("p", periode);
        jdbc.query("SELECT p.center_id, "
                        + "SUM(CASE WHEN p.epo_enabled = TRUE THEN 1 ELSE 0 END) AS sous_epo, "
                        + "SUM(CASE WHEN p.fer_enabled = TRUE THEN 1 ELSE 0 END) AS sous_fer "
                        + "FROM patients p WHERE p.center_id IN (" + marques(centres) + ") AND " + effectif.sql() + " GROUP BY p.center_id",
                rs -> {
                    prescriptions.put(rs.getObject("center_id", UUID.class),
                            new PrescriptionsDeLAnemie(rs.getLong("sous_epo"), rs.getLong("sous_fer")));
                }, effectif.arguments(centres));
        return prescriptions;
    }
}
