package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.EffectifDesPatients;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adapter du port publié {@link EffectifDesPatients} sur la table {@code patients} : les requêtes d'effectif que le Pilotage direction
 * faisait en SQL direct (ADR 0102), reprises MOT POUR MOT avec le prédicat de la règle de présence (RG-PAT-032, mêmes états, mêmes
 * paramètres que {@code CompteursDeCouvertureJdbc.patientsPresentsLe}, pour une période). Compatible H2 / PostgreSQL.
 */
public class EffectifDesPatientsJdbc implements EffectifDesPatients {

    private static final String SORTIES = "'DECEDE','GREFFE','GUERRI','TRANSFERE'";
    private static final String SEJOURS = "'OCCASIONNEL','VACANCIER_ETRANGER','VACANCIER_LOCAL'";

    private static final String PREDICAT = "(p.date_admission IS NULL OR p.date_admission <= ?) AND ("
            + "p.etat_patient IS NULL OR p.etat_patient NOT IN (" + SORTIES + "," + SEJOURS + ") "
            // séjour limité : jusqu'à la fin de séjour incluse (sans date : non borné)
            + "OR (p.etat_patient IN (" + SEJOURS + ") AND (p.date_evenement_etat IS NULL OR p.date_evenement_etat >= ?)) "
            // transfert, guérison : dernière présence = la date incluse
            + "OR (p.etat_patient IN ('TRANSFERE','GUERRI') AND p.date_evenement_etat IS NOT NULL AND p.date_evenement_etat >= ?) "
            // décès, greffe : place libérée à la date (dernière présence = la veille)
            + "OR (p.etat_patient IN ('DECEDE','GREFFE') AND p.date_evenement_etat IS NOT NULL AND p.date_evenement_etat > ?))";

    private final JdbcTemplate jdbc;

    public EffectifDesPatientsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static String condition(Collection<UUID> centres) {
        return "p.center_id IN (" + String.join(",", Collections.nCopies(centres.size(), "?")) + ") AND " + PREDICAT;
    }

    /** Les centres puis les quatre dates du prédicat (fin, début, début, début), dans l'ordre des {@code ?}. */
    private static Object[] arguments(Collection<UUID> centres, LocalDate debut, LocalDate fin) {
        List<Object> tous = new ArrayList<>(centres);
        tous.add(Date.valueOf(fin));
        tous.add(Date.valueOf(debut));
        tous.add(Date.valueOf(debut));
        tous.add(Date.valueOf(debut));
        return tous.toArray();
    }

    @Override
    public Map<UUID, EffectifDuCentre> parCentre(Collection<UUID> centres, LocalDate debut, LocalDate fin) {
        Map<UUID, EffectifDuCentre> effectifs = new HashMap<>();
        jdbc.query("SELECT p.center_id, COUNT(*) AS total, SUM(CASE WHEN p.sous_kt = TRUE THEN 1 ELSE 0 END) AS kt FROM patients p WHERE "
                        + condition(centres) + " GROUP BY p.center_id",
                rs -> {
                    effectifs.put(rs.getObject("center_id", UUID.class), new EffectifDuCentre(rs.getLong("total"), rs.getLong("kt")));
                }, arguments(centres, debut, fin));
        return effectifs;
    }

    @Override
    public List<ProfilDuPatient> profils(Collection<UUID> centres, LocalDate debut, LocalDate fin) {
        return jdbc.query("SELECT p.center_id, p.sexe, p.date_naissance FROM patients p WHERE " + condition(centres),
                (rs, i) -> {
                    Date naissance = rs.getDate("date_naissance");
                    return new ProfilDuPatient(rs.getObject("center_id", UUID.class), rs.getString("sexe"),
                            naissance == null ? null : naissance.toLocalDate());
                }, arguments(centres, debut, fin));
    }

    @Override
    public List<PatientsDuCentrePayeur> parCentrePayeur(Collection<UUID> centres, LocalDate debut, LocalDate fin) {
        return jdbc.query("SELECT p.center_id, p.centre_payeur_id, COUNT(*) AS n FROM patients p WHERE " + condition(centres)
                        + " GROUP BY p.center_id, p.centre_payeur_id",
                (rs, i) -> new PatientsDuCentrePayeur(rs.getObject("center_id", UUID.class),
                        rs.getObject("centre_payeur_id", UUID.class), rs.getLong("n")),
                arguments(centres, debut, fin));
    }

    @Override
    public Map<UUID, PrescriptionsDeLAnemie> prescriptionsDeLAnemie(Collection<UUID> centres, LocalDate debut, LocalDate fin) {
        Map<UUID, PrescriptionsDeLAnemie> prescriptions = new HashMap<>();
        jdbc.query("SELECT p.center_id, SUM(CASE WHEN p.epo_enabled = TRUE THEN 1 ELSE 0 END) AS sous_epo, "
                        + "SUM(CASE WHEN p.fer_enabled = TRUE THEN 1 ELSE 0 END) AS sous_fer FROM patients p WHERE "
                        + condition(centres) + " GROUP BY p.center_id",
                rs -> {
                    prescriptions.put(rs.getObject("center_id", UUID.class),
                            new PrescriptionsDeLAnemie(rs.getLong("sous_epo"), rs.getLong("sous_fer")));
                }, arguments(centres, debut, fin));
        return prescriptions;
    }
}
