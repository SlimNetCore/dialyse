package com.hemodialyse.backend.contexts.planification.contrat;

import com.hemodialyse.backend.contexts.planification.application.CentresActifs;
import com.hemodialyse.backend.contexts.planification.application.PersonnelDeLaPresence;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacycentres.CentresActifsLegacy;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.PersonnelDeLaPresenceJdbc;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.RoulementEtAbsencesLusParLeLegacyJdbc;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Contrat du personnel de la présence contre les adaptateurs JDBC : la base (H2 ou PostgreSQL 16) est fournie par la sous-classe. */
abstract class PersonnelDeLaPresenceJdbcContrat extends PersonnelDeLaPresenceContrat {

    protected abstract JdbcTemplate jdbc();

    @Override
    protected PersonnelDeLaPresence personnel() {
        return new PersonnelDeLaPresenceJdbc(jdbc(), new RoulementEtAbsencesLusParLeLegacyJdbc(jdbc()));
    }

    @Override
    protected CentresActifs centresActifs() {
        return new CentresActifsLegacy(jdbc());
    }

    @Override
    protected void infirmier(CentreId centre, UUID id, String nom, String prenom, boolean actif, boolean habilite) {
        jdbc().update("INSERT INTO infirmier (id, center_id, matricule, nom, prenom, qualification, habilite_isolement, actif) "
                        + "VALUES (?, ?, ?, ?, ?, 'INFIRMIER', ?, ?)", id, centre.valeur(), "M" + id.toString().substring(0, 8), nom, prenom,
                habilite, actif);
    }

    @Override
    protected void affectation(CentreId centre, UUID infirmier, Set<DayOfWeek> jours) {
        String noms = jours.stream().map(j -> j == DayOfWeek.SUNDAY ? "DIMANCHE" : switch (j) {
            case MONDAY -> "LUNDI";
            case TUESDAY -> "MARDI";
            case WEDNESDAY -> "MERCREDI";
            case THURSDAY -> "JEUDI";
            case FRIDAY -> "VENDREDI";
            default -> "SAMEDI";
        }).collect(Collectors.joining(","));
        jdbc().update("INSERT INTO infirmier_affectation (id, center_id, infirmier_id, salle_id, creneau_id, jours) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), centre.valeur(), infirmier, SALLE, CRENEAU, noms);
    }

    @Override
    protected void absence(CentreId centre, UUID infirmier, LocalDate debut, LocalDate fin) {
        jdbc().update("INSERT INTO infirmier_absence (id, center_id, infirmier_id, date_debut, date_fin, type) VALUES (?, ?, ?, ?, ?, 'CONGE')",
                UUID.randomUUID(), centre.valeur(), infirmier, Date.valueOf(debut), Date.valueOf(fin));
    }

    @Override
    protected void remplacement(CentreId centre, UUID infirmier, LocalDate date) {
        jdbc().update("INSERT INTO infirmier_remplacement (id, center_id, date_jour, salle_id, creneau_id, infirmier_id) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), centre.valeur(), Date.valueOf(date), SALLE, CRENEAU, infirmier);
    }

    @Override
    protected void ratio(CentreId centre, Integer valeur) {
        jdbc().update("DELETE FROM planning_parametres WHERE center_id = ?", centre.valeur());
        jdbc().update("INSERT INTO planning_parametres (center_id, jours_ouverts, patients_par_infirmier, updated_at) VALUES (?, '', ?, CURRENT_TIMESTAMP)",
                centre.valeur(), valeur);
    }

    @Override
    protected void centre(CentreId centre, boolean actif) {
        jdbc().update("UPDATE centers SET actif = ? WHERE id = ?", actif, centre.valeur());
    }

    protected void viderPlanningParametres() {
        jdbc().update("DELETE FROM planning_parametres WHERE center_id IN (?, ?)", CENTRE.valeur(), AUTRE.valeur());
    }
}
