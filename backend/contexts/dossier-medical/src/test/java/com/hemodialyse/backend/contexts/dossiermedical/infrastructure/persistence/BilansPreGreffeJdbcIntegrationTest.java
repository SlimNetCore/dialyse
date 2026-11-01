package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.DossierMedicalTestApplication;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffeId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffeRepository;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EtapeBilan;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test d'intégration de l'adaptateur JDBC {@link BilansPreGreffeJdbc}.
 * Vérifie l'isolation multi-centre (centerId) et la pagination.
 */
@SpringBootTest(classes = DossierMedicalTestApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:dossier-medical-bilans;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:db/schema.sql"
        })
class BilansPreGreffeJdbcIntegrationTest {

    private static final CentreId CENTRE_A = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000a"));
    private static final CentreId CENTRE_B = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000b"));
    private static final UUID PATIENT_A = UUID.fromString("99997300-0000-0000-0000-000000000101");
    private static final UUID PATIENT_B = UUID.fromString("99997300-0000-0000-0000-000000000102");

    @Autowired
    private BilanPreGreffeRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM etapes_bilan_pregreffe");
        jdbc.update("DELETE FROM bilans_pregreffe");
    }

    private BilanPreGreffe creerBilan(UUID id, CentreId centre, UUID patient) {
        OffsetDateTime maintenant = OffsetDateTime.ofInstant(
                java.time.Instant.parse("2026-03-10T08:00:00Z"), ZoneOffset.UTC);
        BilanPreGreffe b = BilanPreGreffe.demarrer(
                new BilanPreGreffeId(id), patient, centre,
                List.of(EtapeBilan.aFaire("CARDIO", "Bilan cardiaque"),
                        EtapeBilan.aFaire("NEPHRO", "Avis néphrologique")),
                LocalDate.of(2026, 3, 10), null, maintenant);
        repository.save(b);
        return b;
    }

    @Test
    void save_et_trouver_centre_a() {
        UUID id = UUID.randomUUID();
        BilanPreGreffe b = creerBilan(id, CENTRE_A, PATIENT_A);
        BilanPreGreffe trouve = repository.trouver(b.id(), CENTRE_A).orElseThrow();
        assertEquals(id, trouve.id().value());
        assertEquals(2, trouve.etapes().size());
    }

    @Test
    void trouver_centre_b_ne_retourne_pas_donnees_centre_a() {
        UUID idA = UUID.randomUUID();
        creerBilan(idA, CENTRE_A, PATIENT_A);
        assertFalse(repository.trouver(new BilanPreGreffeId(idA), CENTRE_B).isPresent());
    }

    @Test
    void lister_par_patient_est_page_et_filtre_centre() {
        creerBilan(UUID.randomUUID(), CENTRE_A, PATIENT_A);
        creerBilan(UUID.randomUUID(), CENTRE_A, PATIENT_B);
        // Données d'un autre centre : ne doivent pas apparaître
        creerBilan(UUID.randomUUID(), CENTRE_B, PATIENT_A);

        PagedResult<BilanPreGreffe> page = repository.listerParPatient(CENTRE_A, PATIENT_A, 0, 10);
        assertEquals(1, page.items().size());
        assertEquals(1, page.total());
    }

    @Test
    void valider_etape_persiste() {
        UUID id = UUID.randomUUID();
        BilanPreGreffe b = creerBilan(id, CENTRE_A, PATIENT_A);
        BilanPreGreffe modifie = b.validerEtape("CARDIO", "MED-001",
                OffsetDateTime.ofInstant(java.time.Instant.parse("2026-03-11T08:00:00Z"), ZoneOffset.UTC));
        repository.save(modifie);
        BilanPreGreffe relu = repository.trouver(new BilanPreGreffeId(id), CENTRE_A).orElseThrow();
        assertTrue(relu.etapes().stream().filter(e -> e.code().equals("CARDIO")).findFirst()
                .orElseThrow().validee());
    }
}
