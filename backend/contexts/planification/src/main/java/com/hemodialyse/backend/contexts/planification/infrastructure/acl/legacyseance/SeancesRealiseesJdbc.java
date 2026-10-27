package com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacyseance;

import com.hemodialyse.backend.contexts.planification.application.SeancesRealisees;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.SemaineDuPlanning.SeanceRealisee;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacydossiermedical.RisquesInfectieuxJdbc;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * ACL vers Séance ({@code seances}) : les séances réalisées (statuts {@code VALIDEE}, {@code SIGNEE}, {@code FACTUREE}) d'une période,
 * avec la place figée à la validation et le nom « prénom nom » du patient (un patient sorti depuis garde son nom sur ses séances
 * passées). Triées par date puis par identifiant de séance. Bornée au centre.
 */
public class SeancesRealiseesJdbc implements SeancesRealisees {

    private final JdbcTemplate jdbc;

    public SeancesRealiseesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<SeanceRealisee> duCentre(CentreId centre, LocalDate du, LocalDate au) {
        Set<UUID> aRisque = RisquesInfectieuxJdbc.patientsARisque(jdbc, centre.valeur());
        return jdbc.query("SELECT s.patient_id, s.date_seance, s.salle_id, s.creneau_id, s.generateur_id, p.nom, p.prenom "
                        + "FROM seances s JOIN patients p ON p.id = s.patient_id AND p.center_id = s.center_id "
                        + "WHERE s.center_id = ? AND s.date_seance BETWEEN ? AND ? AND s.statut IN ('VALIDEE', 'SIGNEE', 'FACTUREE') "
                        + "ORDER BY s.date_seance",
                (rs, i) -> {
                    UUID patient = rs.getObject("patient_id", UUID.class);
                    UUID salle = rs.getObject("salle_id", UUID.class);
                    UUID creneau = rs.getObject("creneau_id", UUID.class);
                    UUID generateur = rs.getObject("generateur_id", UUID.class);
                    String prenom = rs.getString("prenom") == null ? "" : rs.getString("prenom");
                    String nom = rs.getString("nom") == null ? "" : rs.getString("nom");
                    return new SeanceRealisee(new PatientId(patient), (prenom + " " + nom).trim(), rs.getDate("date_seance").toLocalDate(),
                            salle == null ? null : new SalleId(salle), creneau == null ? null : new CreneauId(creneau),
                            generateur == null ? null : new GenerateurId(generateur), aRisque.contains(patient));
                }, centre.valeur(), Date.valueOf(du), Date.valueOf(au));
    }
}
