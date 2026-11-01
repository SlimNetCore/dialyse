package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.DossierMedicalTestApplication;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Allergie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AllergieId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutClinique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeAllergie;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Test d'intégration de l'adaptateur JDBC {@link AllergiesJdbc}.
 * Vérifie l'isolation multi-centre (centerId) et la pagination.
 */
@SpringBootTest(classes = DossierMedicalTestApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:dossier-medical-allergies;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:db/schema.sql"
        })
class AllergiesJdbcIntegrationTest {

    private static final CentreId CENTRE_A = new CentreId(UUID.fromString("99997500-0000-0000-0000-00000000000a"));
    private static final CentreId CENTRE_B = new CentreId(UUID.fromString("99997500-0000-0000-0000-00000000000b"));
    private static final UUID PATIENT_A = UUID.fromString("99997500-0000-0000-0000-000000000101");
    private static final UUID PATIENT_B = UUID.fromString("99997500-0000-0000-0000-000000000102");

    @Autowired
    private com.hemodialyse.backend.contexts.dossiermedical.domain.AllergieRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM allergies");
    }

    private Allergie creerAllergie(UUID id, CentreId centre, UUID patient, TypeAllergie type,
                                   String allergene, String reaction) {
        Allergie a = Allergie.reconstituer(
                new AllergieId(id), patient, centre, allergene, type, reaction,
                LocalDate.of(2020, 1, 1), null, StatutClinique.ACTIF, null, null,
                OffsetDateTime.parse("2026-03-10T08:00:00Z"),
                OffsetDateTime.parse("2026-03-10T08:00:00Z"));
        repository.save(a);
        return a;
    }

    @Test
    void save_et_trouver_centre_a() {
        UUID id = UUID.randomUUID();
        Allergie a = creerAllergie(id, CENTRE_A, PATIENT_A, TypeAllergie.MEDICAMENTOSE,
                "Pénicilline", "Éruption cutanée");
        Allergie trouve = repository.trouver(a.getId(), CENTRE_A).orElseThrow();
        assertEquals(id, trouve.getId().value());
        assertEquals(TypeAllergie.MEDICAMENTOSE, trouve.getType());
        assertEquals(StatutClinique.ACTIF, trouve.getStatutClinique());
    }

    @Test
    void trouver_centre_b_ne_retourne_pas_donnees_centre_a() {
        UUID idA = UUID.randomUUID();
        creerAllergie(idA, CENTRE_A, PATIENT_A, TypeAllergie.MEDICAMENTOSE,
                "Pénicilline", "Éruption cutanée");
        assertFalse(repository.trouver(new AllergieId(idA), CENTRE_B).isPresent());
    }

    @Test
    void lister_par_patient_est_page_et_filtre_centre() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        creerAllergie(id1, CENTRE_A, PATIENT_A, TypeAllergie.MEDICAMENTOSE,
                "Pénicilline", "Éruption cutanée");
        creerAllergie(id2, CENTRE_A, PATIENT_A, TypeAllergie.ALIMENTAIRE, "Arachide", "Œdème");
        creerAllergie(id3, CENTRE_A, PATIENT_B, TypeAllergie.MEDICAMENTOSE,
                "Pénicilline", "Éruption cutanée");
        // Données d'un autre centre : ne doivent pas apparaître
        creerAllergie(UUID.randomUUID(), CENTRE_B, PATIENT_A, TypeAllergie.MEDICAMENTOSE,
                "Pénicilline", "Éruption cutanée");

        PagedResult<Allergie> page = repository.findPagedByPatientId(PATIENT_A, CENTRE_A, 0, 10);
        assertEquals(2, page.items().size());
        assertEquals(2, page.total());
        assertEquals(0, page.page());
        assertEquals(10, page.size());

        PagedResult<Allergie> pageB = repository.findPagedByPatientId(PATIENT_B, CENTRE_A, 0, 10);
        assertEquals(1, pageB.items().size());
        assertEquals(1, pageB.total());
    }

    @Test
    void pagination_respecte_page_et_size() {
        for (int i = 0; i < 5; i++) {
            creerAllergie(UUID.randomUUID(), CENTRE_A, PATIENT_A, TypeAllergie.MEDICAMENTOSE,
                    "Pénicilline", "Éruption cutanée");
        }
        PagedResult<Allergie> p1 = repository.findPagedByPatientId(PATIENT_A, CENTRE_A, 0, 2);
        assertEquals(2, p1.items().size());
        assertEquals(5, p1.total());
        PagedResult<Allergie> p2 = repository.findPagedByPatientId(PATIENT_A, CENTRE_A, 1, 2);
        assertEquals(2, p2.items().size());
        PagedResult<Allergie> p3 = repository.findPagedByPatientId(PATIENT_A, CENTRE_A, 2, 2);
        assertEquals(1, p3.items().size());
    }

    @Test
    void deleteById_supprime_uniquement_centre_propre() {
        UUID idA = UUID.randomUUID();
        creerAllergie(idA, CENTRE_A, PATIENT_A, TypeAllergie.MEDICAMENTOSE,
                "Pénicilline", "Éruption cutanée");
        repository.deleteById(new AllergieId(idA), CENTRE_A);
        assertFalse(repository.trouver(new AllergieId(idA), CENTRE_A).isPresent());
        assertEquals(0, repository.findByPatientId(PATIENT_A, CENTRE_A).size());
    }
}
