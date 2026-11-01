package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.DossierMedicalTestApplication;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationBiologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationBiologiqueId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationBiologiqueRepository;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Test d'intégration de l'adaptateur JDBC {@link ObservationsBiologiquesJdbc}.
 * Vérifie l'isolation multi-centre (centerId) et la pagination.
 */
@SpringBootTest(classes = DossierMedicalTestApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:dossier-medical-observations;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:db/schema.sql"
        })
class ObservationsBiologiquesJdbcIntegrationTest {

    private static final CentreId CENTRE_A = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000a"));
    private static final CentreId CENTRE_B = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000b"));
    private static final UUID PATIENT_A = UUID.fromString("99997300-0000-0000-0000-000000000101");
    private static final UUID PATIENT_B = UUID.fromString("99997300-0000-0000-0000-000000000102");

    @Autowired
    private ObservationBiologiqueRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM observations_biologiques");
    }

    private ObservationBiologique creerObservation(UUID id, CentreId centre, UUID patient, String code) {
        OffsetDateTime maintenant = OffsetDateTime.ofInstant(
                java.time.Instant.parse("2026-03-10T08:00:00Z"), ZoneOffset.UTC);
        ObservationBiologique o = ObservationBiologique.reconstituer(
                new ObservationBiologiqueId(id), patient, centre, code, "Libellé " + code,
                "11.5", "g/dL", 12.0, 16.0, LocalDate.of(2026, 3, 10),
                "MED-001", "Labo Central", null, maintenant, maintenant);
        repository.save(o);
        return o;
    }

    @Test
    void save_et_trouver_centre_a() {
        UUID id = UUID.randomUUID();
        ObservationBiologique o = creerObservation(id, CENTRE_A, PATIENT_A, "HGB");
        ObservationBiologique trouve = repository.trouver(o.id(), CENTRE_A).orElseThrow();
        assertEquals(id, trouve.id().value());
        assertEquals("HGB", trouve.code());
    }

    @Test
    void trouver_centre_b_ne_retourne_pas_donnees_centre_a() {
        UUID idA = UUID.randomUUID();
        creerObservation(idA, CENTRE_A, PATIENT_A, "HGB");
        assertFalse(repository.trouver(new ObservationBiologiqueId(idA), CENTRE_B).isPresent());
    }

    @Test
    void lister_par_patient_est_page_et_filtre_centre() {
        creerObservation(UUID.randomUUID(), CENTRE_A, PATIENT_A, "HGB");
        creerObservation(UUID.randomUUID(), CENTRE_A, PATIENT_A, "CRP");
        creerObservation(UUID.randomUUID(), CENTRE_A, PATIENT_B, "HGB");
        // Données d'un autre centre : ne doivent pas apparaître
        creerObservation(UUID.randomUUID(), CENTRE_B, PATIENT_A, "HGB");

        PagedResult<ObservationBiologique> page = repository.listerParPatient(CENTRE_A, PATIENT_A, 0, 10);
        assertEquals(2, page.items().size());
        assertEquals(2, page.total());

        PagedResult<ObservationBiologique> pageB = repository.listerParPatient(CENTRE_A, PATIENT_B, 0, 10);
        assertEquals(1, pageB.items().size());
        assertEquals(1, pageB.total());
    }

    @Test
    void pagination_respecte_page_et_size() {
        for (int i = 0; i < 5; i++) {
            creerObservation(UUID.randomUUID(), CENTRE_A, PATIENT_A, "CODE" + i);
        }
        PagedResult<ObservationBiologique> p1 = repository.listerParPatient(CENTRE_A, PATIENT_A, 0, 2);
        assertEquals(2, p1.items().size());
        assertEquals(5, p1.total());
        PagedResult<ObservationBiologique> p3 = repository.listerParPatient(CENTRE_A, PATIENT_A, 2, 2);
        assertEquals(1, p3.items().size());
    }
}
