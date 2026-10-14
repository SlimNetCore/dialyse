package com.hemodialyse.backend.contexts.gmao.contrat;

import com.hemodialyse.backend.contexts.gmao.application.equipement.LectureEquipements;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.CodesEquipementDuCentre;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementRepository;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.EquipementRepositoryJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.EquipementSpringData;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.EquipementStatutHistoriqueSpringData;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.LectureEquipementsJpa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat PostgreSQL 16 réel de la slice G3 : la MÊME suite {@link EquipementContrat} que le double en mémoire et
 * l'adapter sur H2, sur la table historique {@code gmao_equipements} (V5 puis Hibernate). Base JETABLE uniquement (voir
 * backend/scripts/gmao-pg16-rejeu.sh) : le test vide les équipements et leurs dépendants.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=${gmao.pg16.url}",
        "spring.datasource.username=${gmao.pg16.user:gmao_test}",
        "spring.datasource.password=${gmao.pg16.password:}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.sql.init.mode=never"
})
@EnabledIfSystemProperty(named = "gmao.pg16.url", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EquipementPostgresContractIT extends EquipementContrat {

    @Autowired
    private EquipementSpringData equipements;
    @Autowired
    private EquipementStatutHistoriqueSpringData historique;
    @Autowired
    private JdbcTemplate jdbc;

    private EquipementRepositoryJpa repository;
    private LectureEquipementsJpa lecture;

    @BeforeEach
    void creerLesAdaptersEtViderLesTables() {
        repository = new EquipementRepositoryJpa(equipements, historique);
        lecture = new LectureEquipementsJpa(equipements);
        jdbc.execute("TRUNCATE gmao_equipements, gmao_equipement_statut_historique CASCADE");
        // le schéma Flyway réel porte les clés étrangères des équipements vers centers et app_user
        ReferentielPostgres.centre(jdbc, CENTRE_A.valeur());
        ReferentielPostgres.centre(jdbc, CENTRE_B.valeur());
        ReferentielPostgres.utilisateur(jdbc, ADMIN.valeur());
    }

    @Override
    protected EquipementRepository repository() {
        return repository;
    }

    @Override
    protected CodesEquipementDuCentre codes() {
        return repository;
    }

    @Override
    protected LectureEquipements lecture() {
        return lecture;
    }

    @Override
    protected void supprimerLogiquement(EquipementId id) {
        equipements.findById(id.valeur()).ifPresent(e -> {
            e.setDeletedAt(Instant.parse("2026-02-01T00:00:00Z"));
            equipements.saveAndFlush(e);
        });
    }

    /** La base réelle peut garder l'unicité globale du code de V5 (KB-GMAO-33) : voir {@link #uniciteGlobaleDuCode()}. */
    @Override
    @Test
    void should_refuse_the_same_code_twice_in_a_centre_and_accept_it_in_another() {
        enregistrer(CENTRE_A, "G-01", T0);

        assertThatThrownBy(() -> enregistrer(CENTRE_A, "G-01", T1)).isInstanceOf(RuntimeException.class);
        if (uniciteGlobaleDuCode()) {
            assertThatThrownBy(() -> enregistrer(CENTRE_B, "G-01", T1)).isInstanceOf(RuntimeException.class);
        } else {
            assertThat(enregistrer(CENTRE_B, "G-01", T1).centreId()).isEqualTo(CENTRE_B);
        }
    }

    @Test
    void should_run_on_a_real_postgresql_16() {
        assertThat(jdbc.queryForObject("SHOW server_version", String.class)).startsWith("16.");
    }

    @Test
    void should_have_the_centre_and_code_unique_constraint_of_the_persistence_model() {
        assertThat(contraintesDUnicite()).contains("centre_id,code");
    }

    /** Vrai si la base porte aussi une contrainte d'unicité sur {@code code} seul (V5 : {@code code VARCHAR(50) NOT NULL UNIQUE}). */
    private boolean uniciteGlobaleDuCode() {
        return contraintesDUnicite().contains("code");
    }

    /** Colonnes de chaque contrainte d'unicité de la table, séparées par des virgules. */
    private List<String> contraintesDUnicite() {
        return jdbc.queryForList("SELECT string_agg(a.attname::text, ',' ORDER BY a.attname) FROM pg_constraint c "
                + "JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey) "
                + "WHERE c.conrelid = 'gmao_equipements'::regclass AND c.contype = 'u' GROUP BY c.oid", String.class);
    }
}
