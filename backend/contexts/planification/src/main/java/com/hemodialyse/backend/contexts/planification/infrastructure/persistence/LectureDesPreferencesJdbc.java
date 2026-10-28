package com.hemodialyse.backend.contexts.planification.infrastructure.persistence;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.application.LectureDesPreferences;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.PageDeResultats;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;
import com.hemodialyse.backend.contexts.planification.domain.PreferenceDePlanification;
import com.hemodialyse.backend.contexts.planification.domain.ProfilDePlanification;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Read model des écrans de préférences et de profils : le paramétrage du contexte rapproché, en lecture seule, des patients
 * suivis (colonnes d'état, de jours et de créneau de {@code patients}) et des infirmiers actifs (table {@code infirmier}), tant
 * que ces contextes ne sont pas extraits. Tout est borné au centre.
 */
public class LectureDesPreferencesJdbc implements LectureDesPreferences {

    private static final Set<String> ETATS_DE_SORTIE = Set.of("TRANSFERE", "DECEDE", "GREFFE", "GUERRI");
    private static final String JOURS = "p.jour_dimanche, p.jour_lundi, p.jour_mardi, p.jour_mercredi, p.jour_jeudi, "
            + "p.jour_vendredi, p.jour_samedi";
    private static final String SUIVIS = "p.center_id = ? AND (p.etat_patient IS NULL OR p.etat_patient NOT IN ("
            + ETATS_DE_SORTIE.stream().sorted().map(e -> "'" + e + "'").collect(Collectors.joining(",")) + "))";

    private final JdbcTemplate jdbc;

    public LectureDesPreferencesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageDeResultats<LigneDePatient> patientsSuivis(CentreId centre, Pagination pagination) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM patients p WHERE " + SUIVIS, Long.class, centre.valeur());
        List<LigneDePatient> lignes = jdbc.query("SELECT p.id, p.nom, p.prenom, p.position_id, " + JOURS
                        + ", pp.creneau_prefere_id, pp.seances_par_semaine, COALESCE(pp.jours_a_choisir, FALSE) AS jours_a_choisir "
                        + "FROM patients p LEFT JOIN planning_preference_patient pp "
                        + "ON pp.center_id = p.center_id AND pp.patient_id = p.id WHERE " + SUIVIS
                        + " ORDER BY p.nom, p.prenom, p.id LIMIT ? OFFSET ?",
                (rs, i) -> {
                    PatientId patient = new PatientId(rs.getObject("id", UUID.class));
                    Set<JourDeLaSemaine> jours = EnumSet.noneOf(JourDeLaSemaine.class);
                    for (JourDeLaSemaine jour : JourDeLaSemaine.values()) {
                        if (rs.getBoolean("jour_" + jour.name().toLowerCase(Locale.ROOT))) jours.add(jour);
                    }
                    String prenom = rs.getString("prenom") == null ? "" : rs.getString("prenom");
                    String nom = rs.getString("nom") == null ? "" : rs.getString("nom");
                    UUID creneau = rs.getObject("position_id", UUID.class);
                    PreferenceDePlanification preference = PreferencesJdbc.preference(rs, patient);
                    return new LigneDePatient(patient, (prenom + " " + nom).trim(), jours,
                            creneau == null ? null : new CreneauId(creneau), preference);
                }, centre.valeur(), pagination.taille(), pagination.decalage());
        return new PageDeResultats<>(lignes, total == null ? 0 : total, pagination.page(), pagination.taille());
    }

    @Override
    public PageDeResultats<LigneDInfirmier> infirmiersActifs(CentreId centre, Pagination pagination) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM infirmier WHERE center_id = ? AND actif = TRUE", Long.class,
                centre.valeur());
        List<LigneDInfirmier> lignes = jdbc.query("SELECT i.id, i.nom, i.prenom, i.qualification, "
                        + "COALESCE(p.taux_activite, 100) AS taux_activite, p.competences FROM infirmier i "
                        + "LEFT JOIN infirmier_profil_planning p ON p.center_id = i.center_id AND p.infirmier_id = i.id "
                        + "WHERE i.center_id = ? AND i.actif = TRUE ORDER BY i.nom, i.prenom, i.id LIMIT ? OFFSET ?",
                (rs, i) -> {
                    InfirmierId infirmier = new InfirmierId(rs.getObject("id", UUID.class));
                    String prenom = rs.getString("prenom");
                    return new LigneDInfirmier(infirmier, prenom == null ? rs.getString("nom") : prenom + " " + rs.getString("nom"),
                            rs.getString("qualification"), new ProfilDePlanification(infirmier, rs.getInt("taux_activite"),
                            ProfilsJdbc.decoder(rs.getString("competences"))));
                }, centre.valeur(), pagination.taille(), pagination.decalage());
        return new PageDeResultats<>(lignes, total == null ? 0 : total, pagination.page(), pagination.taille());
    }
}
