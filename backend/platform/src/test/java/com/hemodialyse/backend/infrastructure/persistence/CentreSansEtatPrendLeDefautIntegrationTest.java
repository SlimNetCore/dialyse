package com.hemodialyse.backend.infrastructure.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KB-ORG-09 corrigé (ADR 0115, migration V56) sur la base de test H2 bâtie par Hibernate : un centre créé sans état est actif par
 * défaut, et l'état ne peut plus être NULL. Même contrat que la migration PostgreSQL (DEFAULT TRUE, NOT NULL).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class CentreSansEtatPrendLeDefautIntegrationTest {

    private static final UUID SOCIETE = UUID.fromString("99999600-0000-0000-0000-000000000001");
    private static final UUID CENTRE = UUID.fromString("99999600-0000-0000-0000-0000000000c1");
    private static final UUID AUTRE = UUID.fromString("99999600-0000-0000-0000-0000000000c2");

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void preparer() {
        nettoyer();
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                SOCIETE, "ZT-O09", "Société O09");
    }

    @AfterEach
    void nettoyer() {
        jdbc.update("DELETE FROM centers WHERE id IN (?, ?)", CENTRE, AUTRE);
        jdbc.update("DELETE FROM societes WHERE id = ?", SOCIETE);
    }

    @Test
    void un_centre_insere_sans_etat_est_actif_par_defaut() {
        jdbc.update("INSERT INTO centers (id, code, name, societe_id) VALUES (?,?,?,?)", CENTRE, "ZT-O09-A", "Sans etat", SOCIETE);

        assertThat(jdbc.queryForObject("SELECT actif FROM centers WHERE id = ?", Boolean.class, CENTRE)).isTrue();
    }

    @Test
    void l_etat_d_un_centre_ne_peut_pas_etre_nul() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,NULL)",
                AUTRE, "ZT-O09-B", "Nul", SOCIETE)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
