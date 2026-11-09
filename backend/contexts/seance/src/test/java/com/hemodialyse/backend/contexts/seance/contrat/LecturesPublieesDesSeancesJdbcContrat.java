package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.organisation.infrastructure.persistence.LibellesDeLOrganisationJdbc;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.LecturesPublieesDesSeancesJdbc;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.FacturationDesSeancesPubliee;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.PlacesDesSeancesPubliees;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;

/** Partie JDBC du contrat des lectures publiées (H2 et PostgreSQL 16 : seule la connexion change). */
abstract class LecturesPublieesDesSeancesJdbcContrat extends LecturesPublieesDesSeancesContrat {

    protected abstract JdbcTemplate jdbc();

    /** Tables de l'Organisation lues par l'adapter (colonnes lues seulement) ; la table réelle des séances vient de {@link TableDesSeances}. */
    protected void preparer() {
        TableDesSeances.creer(jdbc());
        jdbc().execute("CREATE TABLE IF NOT EXISTS salle (id UUID PRIMARY KEY, center_id UUID NOT NULL, code VARCHAR(50) NOT NULL, "
                + "nom VARCHAR(255) NOT NULL)");
        jdbc().execute("CREATE TABLE IF NOT EXISTS position_creneau (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "code VARCHAR(50) NOT NULL, libelle VARCHAR(255) NOT NULL)");
        vider();
    }

    protected void vider() {
        jdbc().update("DELETE FROM seances");
        jdbc().update("DELETE FROM salle");
        jdbc().update("DELETE FROM position_creneau");
    }

    @Override
    protected void poserSalle(UUID centre, UUID id, String code) {
        jdbc().update("INSERT INTO salle (id, center_id, code, nom) VALUES (?, ?, ?, ?)", id, centre, code, "Salle " + code);
    }

    @Override
    protected void poserCreneau(UUID centre, UUID id, String code, String libelle) {
        jdbc().update("INSERT INTO position_creneau (id, center_id, code, libelle) VALUES (?, ?, ?, ?)", id, centre, code, libelle);
    }

    @Override
    protected void poserSeance(UUID centre, UUID id, String statut, UUID salle, UUID creneau) {
        jdbc().update("INSERT INTO seances (id, center_id, date_seance, patient_id, statut, salle_id, creneau_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, centre, Date.valueOf(LocalDate.of(2026, 2, 10)), new UUID(9, 9), statut, salle, creneau);
    }

    @Override
    protected FacturationDesSeancesPubliee facturation() {
        return new LecturesPublieesDesSeancesJdbc(jdbc(), new LibellesDeLOrganisationJdbc(jdbc()));
    }

    @Override
    protected PlacesDesSeancesPubliees places() {
        return new LecturesPublieesDesSeancesJdbc(jdbc(), new LibellesDeLOrganisationJdbc(jdbc()));
    }
}
