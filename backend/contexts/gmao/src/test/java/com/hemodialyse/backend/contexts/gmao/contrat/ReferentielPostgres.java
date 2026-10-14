package com.hemodialyse.backend.contexts.gmao.contrat;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/**
 * Lignes que le schéma Flyway réel exige (clés étrangères vers {@code centers} et {@code app_user}, plan vers équipement) et
 * que H2 ne vérifie pas. Réservé aux contrats PostgreSQL 16 sur base JETABLE.
 */
final class ReferentielPostgres {

    private ReferentielPostgres() {
    }

    static void centre(JdbcTemplate jdbc, UUID id) {
        jdbc.update("INSERT INTO centers (id, societe_id) VALUES (?, ?) ON CONFLICT DO NOTHING", id, id);
    }

    static void utilisateur(JdbcTemplate jdbc, UUID id) {
        jdbc.update("INSERT INTO app_user (id, active, password_hash, username, must_change_password) "
                + "VALUES (?, TRUE, 'x', ?, FALSE) ON CONFLICT DO NOTHING", id, "pg16-contrat-" + id);
    }

    static void equipement(JdbcTemplate jdbc, UUID id, UUID centre, UUID par) {
        jdbc.update("INSERT INTO gmao_equipements (id, code, designation, type, date_installation, centre_id, statut, cree_par, "
                + "date_creation) VALUES (?, ?, 'Equipement de test', 'OSMOSEUR', CURRENT_TIMESTAMP, ?, 'EN_SERVICE', ?, "
                + "CURRENT_TIMESTAMP) ON CONFLICT DO NOTHING", id, "PG16-" + id, centre, par);
    }
}
