package com.hemodialyse.backend.infrastructure.persistence.adapter.gmao;

import com.hemodialyse.backend.domain.gmao.model.*;
import com.hemodialyse.backend.domain.gmao.port.DocumentInterventionRepositoryPort;
import com.hemodialyse.backend.domain.gmao.port.InterventionRepositoryPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Requêtes de coût de maintenance : seules les interventions en cours/terminées comptent, et tout est
 * cloisonné par centre (AGENTS.md §2).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class InterventionCostQueriesIntegrationTest {

    private static final UUID CENTRE_A = UUID.fromString("99994000-0000-0000-0000-00000000000a");
    private static final UUID CENTRE_B = UUID.fromString("99994000-0000-0000-0000-00000000000b");
    private static final UUID EQUIPEMENT_A = UUID.fromString("99994000-0000-0000-0000-0000000000a1");
    private static final UUID EQUIPEMENT_B = UUID.fromString("99994000-0000-0000-0000-0000000000b1");

    private static final OffsetDateTime DEBUT = OffsetDateTime.of(2026, 3, 10, 8, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime FROM = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime TO = OffsetDateTime.of(2027, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    private InterventionRepositoryPort interventions;

    @Autowired
    private DocumentInterventionRepositoryPort documents;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        cleanup();
        insertEquipement(EQUIPEMENT_A, CENTRE_A, "IC-A");
        insertEquipement(EQUIPEMENT_B, CENTRE_B, "IC-B");

        save(EQUIPEMENT_A, CENTRE_A, StatutIntervention.TERMINEE, "1000");
        save(EQUIPEMENT_A, CENTRE_A, StatutIntervention.EN_COURS, "500");
        save(EQUIPEMENT_A, CENTRE_A, StatutIntervention.ANNULEE, "9000");
        save(EQUIPEMENT_A, CENTRE_A, StatutIntervention.PLANIFIEE, "7000");
        save(EQUIPEMENT_B, CENTRE_B, StatutIntervention.TERMINEE, "2500");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM gmao_rectifications_intervention WHERE intervention_id IN " +
                "(SELECT id FROM gmao_interventions WHERE centre_id IN (?, ?))", CENTRE_A, CENTRE_B);
        jdbc.update("DELETE FROM gmao_documents_intervention WHERE centre_id IN (?, ?)", CENTRE_A, CENTRE_B);
        jdbc.update("DELETE FROM gmao_lignes_cout_intervention WHERE intervention_id IN " +
                "(SELECT id FROM gmao_interventions WHERE centre_id IN (?, ?))", CENTRE_A, CENTRE_B);
        jdbc.update("DELETE FROM gmao_interventions WHERE centre_id IN (?, ?)", CENTRE_A, CENTRE_B);
        jdbc.update("DELETE FROM gmao_equipements WHERE id IN (?, ?)", EQUIPEMENT_A, EQUIPEMENT_B);
    }

    @Test
    void sum_should_count_only_in_progress_and_finished_interventions() {
        assertEquals(0, new BigDecimal("1500").compareTo(
                interventions.sumCoutByEquipementIdAndDateRange(EQUIPEMENT_A, FROM, TO)));
        assertEquals(0, new BigDecimal("1500").compareTo(
                interventions.sumCoutByCentreIdAndDateRange(CENTRE_A, FROM, TO)));
        assertEquals(0, new BigDecimal("1500").compareTo(interventions.sumCoutCumuleByEquipementId(EQUIPEMENT_A)));
    }

    @Test
    void grouped_sum_should_be_scoped_to_the_centre() {
        Map<UUID, BigDecimal> parEquipement = interventions.sumCoutParEquipement(CENTRE_A, FROM, TO);

        assertEquals(1, parEquipement.size());
        assertEquals(0, new BigDecimal("1500").compareTo(parEquipement.get(EQUIPEMENT_A)));
        assertNull(parEquipement.get(EQUIPEMENT_B));
        assertEquals(0, new BigDecimal("2500").compareTo(
                interventions.sumCoutCumuleParEquipement(CENTRE_B).get(EQUIPEMENT_B)));
    }

    @Test
    void period_bounds_should_exclude_interventions_started_outside() {
        assertEquals(0, BigDecimal.ZERO.compareTo(
                interventions.sumCoutByEquipementIdAndDateRange(EQUIPEMENT_A, TO, TO.plusYears(1))));
    }

    @Test
    void en_retard_count_should_flag_late_planned_interventions_and_exceeded_deadlines_per_centre() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // seed : une intervention planifiée dont le début (mars 2026) est passé, dans le centre A seulement
        assertEquals(1, interventions.countEnRetardByCentreId(CENTRE_A, now));
        assertEquals(0, interventions.countEnRetardByCentreId(CENTRE_B, now));
        assertEquals(0, interventions.countEnRetardByCentreId(CENTRE_A, DEBUT.minusDays(1)));
    }

    @Test
    void suivi_fields_should_survive_a_persistence_round_trip() {
        UUID createur = UUID.randomUUID();
        UUID technicien = UUID.randomUUID();
        Intervention i = Intervention.creer(EQUIPEMENT_A, CENTRE_A, TypeIntervention.URGENTE, DEBUT, "Alarme",
                null, StatutEquipement.HORS_SERVICE, "Fuite dialysat", PrioriteIntervention.URGENTE,
                DEBUT.plusHours(6), createur);
        i.demarrer(technicien);
        i.terminer("Joint changé", StatutEquipement.EN_SERVICE, DEBUT.plusHours(3), "Joint usé", technicien);
        interventions.save(i);

        Intervention relu = interventions.findById(i.getId()).orElseThrow();

        assertEquals("Fuite dialysat", relu.getSymptome());
        assertEquals("Joint usé", relu.getCause());
        assertEquals(PrioriteIntervention.URGENTE, relu.getPriorite());
        assertEquals(DEBUT.plusHours(6).toInstant(), relu.getEcheance().toInstant());
        assertEquals(technicien, relu.getDemarrePar());
        assertEquals(technicien, relu.getCloturePar());
        assertEquals(List.of(EvenementIntervention.Type.CREEE, EvenementIntervention.Type.DEMARREE,
                EvenementIntervention.Type.TERMINEE), relu.chronologie().stream().map(EvenementIntervention::type).toList());
    }

    @Test
    void rectification_and_automatic_lines_should_survive_a_persistence_round_trip() {
        UUID auteur = UUID.randomUUID();
        Intervention i = Intervention.creer(EQUIPEMENT_A, CENTRE_A, TypeIntervention.CURATIVE, DEBUT, "Panne",
                null, StatutEquipement.EN_MAINTENANCE, UUID.randomUUID());
        i.demarrer(auteur);
        i.terminer("Fait", StatutEquipement.EN_SERVICE, DEBUT.plusHours(2), auteur);
        i.appliquerTarifIntervenant(new BigDecimal("1000"), TypeIntervenant.INTERNE, auteur);
        i.ajouterLigneCout(LigneCoutIntervention.creer(TypeLigneCout.PIECE, "Joint", BigDecimal.ONE, new BigDecimal("500"), null), auteur);
        interventions.save(i);

        Intervention relu = interventions.findById(i.getId()).orElseThrow();
        assertTrue(relu.getLignesCout().stream().anyMatch(l -> l.isAutomatique() && l.getType() == TypeLigneCout.MAIN_OEUVRE));
        assertTrue(relu.getLignesCout().stream().anyMatch(l -> !l.isAutomatique() && l.getType() == TypeLigneCout.PIECE));

        relu.rectifier("Durée erronée", DEBUT.minusHours(1), auteur);
        interventions.save(relu);

        Intervention rectifiee = interventions.findById(i.getId()).orElseThrow();
        assertEquals(StatutIntervention.EN_COURS, rectifiee.getStatut());
        assertEquals(1, rectifiee.getRectifications().size());
        assertEquals("Durée erronée", rectifiee.getRectifications().get(0).motif());
        assertEquals(auteur, rectifiee.getRectifications().get(0).par());
        assertEquals(1, rectifiee.getLignesCout().size());
        assertEquals(TypeLigneCout.PIECE, rectifiee.getLignesCout().get(0).getType());
        assertEquals(EvenementIntervention.Type.RECTIFIEE, rectifiee.chronologie().get(rectifiee.chronologie().size() - 1).type());
    }

    @Test
    void documents_should_be_stored_listed_without_content_and_downloaded_with_content() {
        UUID interventionId = UUID.randomUUID();
        byte[] pdf = "%PDF-1.7 facture".getBytes();
        DocumentIntervention doc = DocumentIntervention.creer(
                interventionId, CENTRE_A, TypeDocumentIntervention.FACTURE, "facture.pdf", pdf, UUID.randomUUID());
        documents.save(doc);

        var page = documents.findPagedByInterventionId(interventionId, 0, 20);

        assertEquals(1, page.total());
        assertNull(page.items().get(0).contenu());
        assertEquals("facture.pdf", page.items().get(0).nom());
        assertArrayEquals(pdf, documents.findWithContenuById(doc.id()).orElseThrow().contenu());
        assertEquals(0, documents.findPagedByInterventionId(UUID.randomUUID(), 0, 20).total());
    }

    private void save(UUID equipementId, UUID centreId, StatutIntervention statut, String montant) {
        LigneCoutIntervention ligne = LigneCoutIntervention.creer(
                TypeLigneCout.PIECE, "Pièce", BigDecimal.ONE, new BigDecimal(montant), null);
        interventions.save(Intervention.reconstruct(
                UUID.randomUUID(), equipementId, centreId, TypeIntervention.CURATIVE, statut, DEBUT, null, null,
                "Test coût", null, null, null, DEBUT, DEBUT, UUID.randomUUID(), UUID.randomUUID(), List.of(ligne),
                StatutEquipement.EN_SERVICE, null));
    }

    private void insertEquipement(UUID id, UUID centreId, String code) {
        jdbc.update(
                "INSERT INTO gmao_equipements (id, code, designation, type, centre_id, statut, date_installation, " +
                        "date_creation, cree_par) VALUES (?, ?, ?, 'GENERATEUR_DIALYSE', ?, 'EN_SERVICE', ?, ?, ?)",
                id, code, "Générateur " + code, centreId, OffsetDateTime.now(ZoneOffset.UTC), OffsetDateTime.now(ZoneOffset.UTC), new UUID(0, 0));
    }
}
