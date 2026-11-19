package com.hemodialyse.backend.contexts.stock.infrastructure.persistence.pages;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.application.consultation.fournisseurs.ConsulterLesFournisseursParPage;
import com.hemodialyse.backend.contexts.stock.application.pagination.PageDeResultats;
import com.hemodialyse.backend.contexts.stock.application.pagination.ParametresDePage;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Fournisseur;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Page des fournisseurs actifs en SQL sur H2 (mode PostgreSQL, schéma réel du contexte). */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:stock-pages-fournisseurs;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Sql("classpath:db/h2/stock-schema.sql")
class FournisseursParPageJdbcTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000a001"));
    private static final CentreId AUTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000b002"));

    @Autowired
    private JdbcTemplate jdbc;
    private ConsulterLesFournisseursParPage consulter;

    @BeforeEach
    void avant() {
        consulter = new ConsulterLesFournisseursParPage(new FournisseursParPageJdbc(jdbc));
        fournisseur(CENTRE, "F3", "Cedre Medical", "Dupont", true);
        fournisseur(CENTRE, "F1", "Acme Pharma", "Martin", true);
        fournisseur(CENTRE, "F2", "Biolab", "Durand", true);
        fournisseur(CENTRE, "F4", "Ancien Acme", "Dupont", false);
        fournisseur(AUTRE, "F9", "Acme Ailleurs", "Dupont", true);
    }

    private void fournisseur(CentreId centre, String code, String raisonSociale, String contact, boolean actif) {
        jdbc.update("INSERT INTO fournisseurs (id, center_id, code, raison_sociale, contact, actif) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), centre.valeur(), code, raisonSociale, contact, actif);
    }

    private static List<String> codes(PageDeResultats<Fournisseur> page) {
        return page.items().stream().map(Fournisseur::code).toList();
    }

    @Test
    void fournisseurs_actifs_du_centre_par_raison_sociale_pagines() {
        PageDeResultats<Fournisseur> page = consulter.page(CENTRE, new ParametresDePage(0, 2, null), null, null, null, null);
        assertEquals(3, page.total());
        assertEquals(List.of("F1", "F2"), codes(page));
        assertEquals(List.of("F3"), codes(consulter.page(CENTRE, new ParametresDePage(1, 2, null), null, null, null, null)));
    }

    @Test
    void recherche_globale_et_filtres_de_colonne_combines() {
        assertEquals(List.of("F3", "F2"), codes(consulter.page(CENTRE, new ParametresDePage(0, 20, "code,desc"), "du", null,
                null, null)));
        assertEquals(List.of("F1"), codes(consulter.page(CENTRE, new ParametresDePage(0, 20, null), "acme", null, null, null)));
        assertEquals(List.of("F3"), codes(consulter.page(CENTRE, new ParametresDePage(0, 20, null), null, "f", "cedre",
                "dup")));
        assertEquals(List.of("F3", "F2", "F1"), codes(consulter.page(CENTRE, new ParametresDePage(0, 20, "contact"), null, null,
                null, null)));
    }
}
