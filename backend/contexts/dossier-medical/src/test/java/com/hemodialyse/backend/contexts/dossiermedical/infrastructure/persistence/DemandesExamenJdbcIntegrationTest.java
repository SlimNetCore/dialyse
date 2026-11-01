package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.DossierMedicalTestApplication;
import com.hemodialyse.backend.contexts.dossiermedical.domain.CategorieExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandeExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandeExamenId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandeExamenRepository;
import com.hemodialyse.backend.contexts.dossiermedical.domain.LigneDemandeExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutDemandeExamen;
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
 * Test d'int\u00e9gration de l'adaptateur JDBC {@link DemandesExamenJdbc}.
 * V\u00e9rifie l'isolation multi-centre (centerId) et la pagination.
 */
@SpringBootTest(classes = DossierMedicalTestApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:dossier-medical-examens;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:db/schema.sql"
        })
class DemandesExamenJdbcIntegrationTest {

    private static final CentreId CENTRE_A = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000a"));
    private static final CentreId CENTRE_B = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000b"));
    private static final UUID PATIENT_A = UUID.fromString("99997300-0000-0000-0000-000000000101");
    private static final UUID PATIENT_B = UUID.fromString("99997300-0000-0000-0000-000000000102");

    @Autowired
    private DemandeExamenRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM lignes_demande_examen");
        jdbc.update("DELETE FROM demandes_examen");
    }

    private DemandeExamen creerDemande(UUID id, CentreId centre, UUID patient, StatutDemandeExamen statut) {
        DemandeExamen d = DemandeExamen.reconstituer(
                new DemandeExamenId(id), patient, centre, "MED-001",
                LocalDate.of(2026, 3, 10), CategorieExamen.BIOLOGIE, false, "Bilan",
                statut, null,
                List.of(LigneDemandeExamen.creer(UUID.randomUUID(), "LOINC", "1234-5", "Sodium", "Sodium s\u00e9rum", null)),
                OffsetDateTime.parse("2026-03-10T08:00:00Z"),
                OffsetDateTime.parse("2026-03-10T08:00:00Z"));
        repository.save(d);
        return d;
    }

    @Test
    void save_et_trouver_centre_a() {
        UUID id = UUID.randomUUID();
        DemandeExamen d = creerDemande(id, CENTRE_A, PATIENT_A, StatutDemandeExamen.DEMANDE);
        DemandeExamen trouve = repository.trouver(d.id(), CENTRE_A).orElseThrow();
        assertEquals(id, trouve.id().value());
        assertEquals(StatutDemandeExamen.DEMANDE, trouve.statut());
    }

    @Test
    void trouver_centre_b_ne_retourne_pas_donnees_centre_a() {
        UUID idA = UUID.randomUUID();
        creerDemande(idA, CENTRE_A, PATIENT_A, StatutDemandeExamen.DEMANDE);
        assertFalse(repository.trouver(new DemandeExamenId(idA), CENTRE_B).isPresent());
    }

    @Test
    void lister_par_patient_est_page_et_filtre_centre() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        creerDemande(id1, CENTRE_A, PATIENT_A, StatutDemandeExamen.DEMANDE);
        creerDemande(id2, CENTRE_A, PATIENT_A, StatutDemandeExamen.PRELEVE);
        creerDemande(id3, CENTRE_A, PATIENT_B, StatutDemandeExamen.DEMANDE);
        // Donn\u00e9es d'un autre centre : ne doivent pas apparatre
        creerDemande(UUID.randomUUID(), CENTRE_B, PATIENT_A, StatutDemandeExamen.DEMANDE);

        PagedResult<DemandeExamen> page = repository.listerParPatient(CENTRE_A, PATIENT_A, 0, 10);
        assertEquals(2, page.items().size());
        assertEquals(2, page.total());
        assertEquals(0, page.page());
        assertEquals(10, page.size());

        PagedResult<DemandeExamen> pageB = repository.listerParPatient(CENTRE_A, PATIENT_B, 0, 10);
        assertEquals(1, pageB.items().size());
        assertEquals(1, pageB.total());
    }

    @Test
    void pagination_respecte_page_et_size() {
        for (int i = 0; i < 5; i++) {
            creerDemande(UUID.randomUUID(), CENTRE_A, PATIENT_A, StatutDemandeExamen.DEMANDE);
        }
        PagedResult<DemandeExamen> p1 = repository.listerParPatient(CENTRE_A, PATIENT_A, 0, 2);
        assertEquals(2, p1.items().size());
        assertEquals(5, p1.total());
        PagedResult<DemandeExamen> p2 = repository.listerParPatient(CENTRE_A, PATIENT_A, 1, 2);
        assertEquals(2, p2.items().size());
        PagedResult<DemandeExamen> p3 = repository.listerParPatient(CENTRE_A, PATIENT_A, 2, 2);
        assertEquals(1, p3.items().size());
    }
}
