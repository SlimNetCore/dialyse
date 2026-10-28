package com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacypatient;

import com.hemodialyse.backend.contexts.planification.application.ContraintesDesPatients;
import com.hemodialyse.backend.contexts.planification.domain.Competence;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ACL vers Patient et couverture et le Dossier médical (tables {@code patients} et {@code abords_vasculaires}, lecture seule) : les
 * transporteurs de chaque patient et les compétences qu'il exige des infirmiers, {@code PEDIATRIE} sous 18 ans au jour donné et
 * {@code CATHETER} avec un abord vasculaire actif de type cathéter (tunnélisé ou aigu) non terminé à ce jour (RG-PLN-097). Borné au centre.
 */
public class ContraintesDesPatientsJdbc implements ContraintesDesPatients {

    private final JdbcTemplate jdbc;

    public ContraintesDesPatientsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<PatientId, Contraintes> duCentre(CentreId centre, LocalDate aPartirDu) {
        Set<UUID> catheters = new HashSet<>(jdbc.query("SELECT DISTINCT patient_id FROM abords_vasculaires "
                        + "WHERE center_id = ? AND actif = TRUE AND type_abord IN ('KT_TUNNELISE', 'KT_AIGU') "
                        + "AND (date_fin IS NULL OR date_fin >= ?)",
                (rs, i) -> rs.getObject("patient_id", UUID.class), centre.valeur(), Date.valueOf(aPartirDu)));
        Map<PatientId, Contraintes> contraintes = new HashMap<>();
        jdbc.query("SELECT id, date_naissance, transporteur_aller_id, transporteur_retour_id FROM patients WHERE center_id = ?",
                rs -> {
                    UUID id = rs.getObject("id", UUID.class);
                    Set<UUID> transporteurs = new HashSet<>();
                    UUID aller = rs.getObject("transporteur_aller_id", UUID.class);
                    UUID retour = rs.getObject("transporteur_retour_id", UUID.class);
                    if (aller != null) transporteurs.add(aller);
                    if (retour != null) transporteurs.add(retour);
                    Date naissance = rs.getDate("date_naissance");
                    Set<Competence> competences = EnumSet.noneOf(Competence.class);
                    if (naissance != null && naissance.toLocalDate().plusYears(AGE_ADULTE).isAfter(aPartirDu)) {
                        competences.add(Competence.PEDIATRIE);
                    }
                    if (catheters.contains(id)) competences.add(Competence.CATHETER);
                    if (!transporteurs.isEmpty() || !competences.isEmpty()) {
                        contraintes.put(new PatientId(id), new Contraintes(transporteurs, competences));
                    }
                }, centre.valeur());
        return contraintes;
    }
}
