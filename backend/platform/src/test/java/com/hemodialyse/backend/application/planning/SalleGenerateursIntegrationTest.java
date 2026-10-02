package com.hemodialyse.backend.application.planning;

import com.hemodialyse.backend.domain.planning.model.SalleVue;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Vue d'ensemble des salles et capacité : générateurs affectés (hors réformés), places restantes, refus d'un générateur
 * au-delà de la capacité, isolation entre centres.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class SalleGenerateursIntegrationTest {

    private static final UUID CENTRE = UUID.fromString("99997200-0000-0000-0000-00000000000a");
    private static final UUID AUTRE_CENTRE = UUID.fromString("99997200-0000-0000-0000-00000000000b");
    private static final UUID SALLE_LIMITEE = UUID.fromString("99997200-0000-0000-0000-0000000000a1");
    private static final UUID SALLE_LIBRE = UUID.fromString("99997200-0000-0000-0000-0000000000a2");
    private static final UUID SALLE_AUTRE = UUID.fromString("99997200-0000-0000-0000-0000000000a3");
    private static final UUID G1 = UUID.fromString("99997200-0000-0000-0000-0000000000d1");
    private static final UUID G2 = UUID.fromString("99997200-0000-0000-0000-0000000000d2");
    private static final UUID G_REFORME = UUID.fromString("99997200-0000-0000-0000-0000000000d3");
    private static final UUID G_NEUF = UUID.fromString("99997200-0000-0000-0000-0000000000d4");

    @Autowired
    private SalleGenerateursService service;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        cleanup();
        jdbc.update("INSERT INTO salle (id, center_id, code, nom, capacite) VALUES (?, ?, 'SG-1', 'Salle limitée', 2)",
                SALLE_LIMITEE, CENTRE);
        jdbc.update("INSERT INTO salle (id, center_id, code, nom, isolement) VALUES (?, ?, 'SG-2', 'Salle libre', 'OUI')",
                SALLE_LIBRE, CENTRE);
        jdbc.update("INSERT INTO salle (id, center_id, code, nom, capacite) VALUES (?, ?, 'SG-3', 'Autre centre', 1)",
                SALLE_AUTRE, AUTRE_CENTRE);
        insertGenerateur(G1, CENTRE, SALLE_LIMITEE, "SG-G1", "EN_SERVICE");
        insertGenerateur(G2, CENTRE, SALLE_LIMITEE, "SG-G2", "HORS_SERVICE");
        insertGenerateur(G_REFORME, CENTRE, SALLE_LIMITEE, "SG-G3", "REFORME");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM gmao_equipements WHERE id IN (?, ?, ?, ?)", G1, G2, G_REFORME, G_NEUF);
        jdbc.update("DELETE FROM salle WHERE center_id IN (?, ?)", CENTRE, AUTRE_CENTRE);
    }

    @Test
    void the_overview_lists_each_room_with_its_assigned_generators_but_not_the_retired_ones() {
        PagedResult<SalleVue> page = service.lister(CENTRE, 0, 20);

        assertEquals(2, page.total());
        SalleVue limitee = page.items().get(0);
        assertEquals("SG-1", limitee.code());
        assertEquals(2, limitee.capacite());
        assertEquals(2, limitee.nbGenerateurs(), "le générateur réformé n'est plus affecté");
        assertEquals(0, limitee.placesRestantes());
        assertFalse(limitee.depassement());
        SalleVue libre = page.items().get(1);
        assertTrue(libre.isolement());
        assertEquals(0, libre.nbGenerateurs());
        assertNull(libre.capacite());
    }

    @Test
    void the_overview_is_paginated_and_scoped_to_the_center() {
        assertEquals(1, service.lister(CENTRE, 0, 1).items().size());
        assertEquals(2, service.lister(CENTRE, 0, 1).total());
        assertEquals(1, service.lister(CENTRE, 1, 1).items().size());
        assertEquals(1, service.lister(AUTRE_CENTRE, 0, 20).total());
        assertTrue(service.lister(AUTRE_CENTRE, 0, 20).items().stream()
                .flatMap(s -> s.generateurs().stream()).findAny().isEmpty());
    }

    @Test
    void an_assignment_beyond_the_capacity_is_refused() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.verifierAffectation(CENTRE, SALLE_LIMITEE, G_NEUF));
        assertEquals("SALLE_CAPACITE_DEPASSEE", e.getCode());
    }

    @Test
    void a_generator_already_in_a_full_room_can_still_be_edited() {
        assertDoesNotThrow(() -> service.verifierAffectation(CENTRE, SALLE_LIMITEE, G1));
    }

    @Test
    void a_room_without_capacity_or_no_room_never_refuses() {
        assertDoesNotThrow(() -> service.verifierAffectation(CENTRE, SALLE_LIBRE, G_NEUF));
        assertDoesNotThrow(() -> service.verifierAffectation(CENTRE, null, G_NEUF));
    }

    @Test
    void a_room_of_another_center_is_not_limited_by_this_center() {
        assertDoesNotThrow(() -> service.verifierAffectation(CENTRE, SALLE_AUTRE, G_NEUF));
    }

    @Test
    void freeing_a_place_allows_a_new_assignment() {
        jdbc.update("UPDATE gmao_equipements SET statut = 'REFORME' WHERE id = ?", G2);

        assertDoesNotThrow(() -> service.verifierAffectation(CENTRE, SALLE_LIMITEE, G_NEUF));
    }

    private void insertGenerateur(UUID id, UUID centre, UUID salle, String code, String statut) {
        jdbc.update("INSERT INTO gmao_equipements (id, code, designation, type, centre_id, statut, date_installation, "
                        + "salle_id, date_creation, cree_par) VALUES (?, ?, ?, 'GENERATEUR_DIALYSE', ?, ?, ?, ?, ?, ?)",
                id, code, "Générateur " + code, centre, statut, OffsetDateTime.now(ZoneOffset.UTC), salle,
                OffsetDateTime.now(ZoneOffset.UTC), new UUID(0, 0));
    }
}
