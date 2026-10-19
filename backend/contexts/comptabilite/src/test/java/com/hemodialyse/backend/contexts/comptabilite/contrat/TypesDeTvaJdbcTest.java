package com.hemodialyse.backend.contexts.comptabilite.contrat;

import com.hemodialyse.backend.contexts.comptabilite.application.tva.TypeDeTvaVue;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTva;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTvaId;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.RegleTvaRepositoryJdbc;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.TypesDeTvaJdbc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lecture JDBC de l'écran « Types de TVA » sur H2 (mode PostgreSQL) : page, tri, audit et isolement par centre.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:comptabilite-types-tva;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TypesDeTvaJdbcTest {

    private static final CentreId CENTRE_A = new CentreId(UUID.fromString("99996600-0000-0000-0000-00000000000a"));
    private static final CentreId CENTRE_B = new CentreId(UUID.fromString("99996600-0000-0000-0000-00000000000b"));

    @Autowired
    private JdbcTemplate jdbc;

    private RegleTvaRepositoryJdbc regles;
    private TypesDeTvaJdbc lecture;

    private static RegleTva regle(CentreId centre, String libelle, String debut) {
        return RegleTva.definir(new RegleTvaId(UUID.randomUUID()), centre, libelle, "HEMODIALYSE", new BigDecimal("19"), false,
                LocalDate.parse(debut), null, null);
    }

    @BeforeEach
    void creerLaTable() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS tva_types (id UUID NOT NULL PRIMARY KEY, center_id UUID NOT NULL, "
                + "libelle VARCHAR(255) NOT NULL, taux NUMERIC(5, 2) NOT NULL, "
                + "type_prestation VARCHAR(120) DEFAULT 'HEMODIALYSE' NOT NULL, exonere BOOLEAN DEFAULT FALSE NOT NULL, "
                + "date_debut_validite DATE NOT NULL, date_fin_validite DATE, texte_reference VARCHAR(500), "
                + "actif BOOLEAN DEFAULT TRUE NOT NULL, created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL, "
                + "created_by VARCHAR(120))");
        jdbc.update("DELETE FROM tva_types");
        regles = new RegleTvaRepositoryJdbc(jdbc);
        lecture = new TypesDeTvaJdbc(jdbc);
    }

    @Test
    void la_page_est_triee_par_debut_decroissant_puis_libelle_et_compte_tout_le_centre() {
        regles.ajouter(regle(CENTRE_A, "B", "2026-01-01"), "admin");
        regles.ajouter(regle(CENTRE_A, "A", "2026-01-01"), "admin");
        regles.ajouter(regle(CENTRE_A, "C", "2024-01-01"), "admin");
        regles.ajouter(regle(CENTRE_B, "Autre", "2026-01-01"), "admin");

        var premiere = lecture.page(CENTRE_A, 0, 2);
        var seconde = lecture.page(CENTRE_A, 1, 2);

        assertThat(premiere.items()).extracting(TypeDeTvaVue::libelle).containsExactly("A", "B");
        assertThat(seconde.items()).extracting(TypeDeTvaVue::libelle).containsExactly("C");
        assertThat(premiere.total()).isEqualTo(3);
        assertThat(seconde.page()).isEqualTo(1);
        assertThat(seconde.size()).isEqualTo(2);
    }

    @Test
    void un_type_se_lit_avec_son_auteur_sa_date_de_creation_et_seulement_dans_son_centre() {
        RegleTva r = regle(CENTRE_A, "TVA", "2026-01-01");
        regles.ajouter(r, "admin");

        TypeDeTvaVue vue = lecture.type(CENTRE_A, r.id()).orElseThrow();

        assertThat(vue.id()).isEqualTo(r.id().valeur());
        assertThat(vue.centre()).isEqualTo(CENTRE_A.valeur());
        assertThat(vue.creePar()).isEqualTo("admin");
        assertThat(vue.creeLe()).isNotNull();
        assertThat(vue.actif()).isTrue();
        assertThat(lecture.type(CENTRE_B, r.id())).isEmpty();
    }
}

