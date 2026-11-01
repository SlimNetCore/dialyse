package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.DossierMedicalTestApplication;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedent;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AntecedentId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AntecedentRepository;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ConceptCode;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutClinique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeAntecedent;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test d'intégration de l'adaptateur JDBC {@link AntecedentsJdbc}.
 * Vérifie l'isolation multi-centre (centerId) et la pagination.
 */
@SpringBootTest(classes = DossierMedicalTestApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:dossier-medical-antecedents;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:db/schema.sql"
        })
class AntecedentsJdbcIntegrationTest {

    private static final CentreId CENTRE_A = new CentreId(UUID.fromString("99997400-0000-0000-0000-00000000000a"));
    private static final CentreId CENTRE_B = new CentreId(UUID.fromString("99997400-0000-0000-0000-00000000000b"));
    private static final UUID PATIENT_A = UUID.fromString("99997400-0000-0000-0000-000000000101");
    private static final UUID PATIENT_B = UUID.fromString("99997400-0000-0000-0000-000000000102");

    @Autowired
    private AntecedentRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM antecedents");
    }

    private Antecedent creerAntecedent(UUID id, CentreId centre, UUID patient, TypeAntecedent type,
                                       ConceptCode diagnostic, String libelle) {
        Antecedent a = Antecedent.reconstituer(
                new AntecedentId(id), patient, centre, type, diagnostic, libelle,
                LocalDate.of(2020, 1, 1), null, StatutClinique.ACTIF, null, null,
                OffsetDateTime.parse("2026-03-10T08:00:00Z"),
                OffsetDateTime.parse("2026-03-10T08:00:00Z"));
        repository.save(a);
        return a;
    }

    @Test
    void save_et_trouver_centre_a() {
        UUID id = UUID.randomUUID();
        Antecedent a = creerAntecedent(id, CENTRE_A, PATIENT_A, TypeAntecedent.MEDICAL,
                new ConceptCode("CIM10", "N18.5", "IRC stade 5"), null);
        Antecedent trouve = repository.trouver(a.getId(), CENTRE_A).orElseThrow();
        assertEquals(id, trouve.getId().value());
        assertEquals(TypeAntecedent.MEDICAL, trouve.getType());
        assertEquals(StatutClinique.ACTIF, trouve.getStatutClinique());
    }

    @Test
    void trouver_centre_b_ne_retourne_pas_donnees_centre_a() {
        UUID idA = UUID.randomUUID();
        creerAntecedent(idA, CENTRE_A, PATIENT_A, TypeAntecedent.MEDICAL,
                new ConceptCode("CIM10", "N18.5", "IRC stade 5"), null);
        assertFalse(repository.trouver(new AntecedentId(idA), CENTRE_B).isPresent());
    }

    @Test
    void lister_par_patient_est_page_et_filtre_centre() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        creerAntecedent(id1, CENTRE_A, PATIENT_A, TypeAntecedent.MEDICAL,
                new ConceptCode("CIM10", "N18.5", "IRC stade 5"), null);
        creerAntecedent(id2, CENTRE_A, PATIENT_A, TypeAntecedent.CHIRURGICAL, null, "Appendicectomie");
        creerAntecedent(id3, CENTRE_A, PATIENT_B, TypeAntecedent.MEDICAL,
                new ConceptCode("CIM10", "E11.9", "Diabète"), null);
        // Données d'un autre centre : ne doivent pas apparaître
        creerAntecedent(UUID.randomUUID(), CENTRE_B, PATIENT_A, TypeAntecedent.MEDICAL,
                new ConceptCode("CIM10", "N18.5", "IRC stade 5"), null);

        PagedResult<Antecedent> page = repository.findPagedByPatientId(PATIENT_A, CENTRE_A, 0, 10);
        assertEquals(2, page.items().size());
        assertEquals(2, page.total());
        assertEquals(0, page.page());
        assertEquals(10, page.size());

        PagedResult<Antecedent> pageB = repository.findPagedByPatientId(PATIENT_B, CENTRE_A, 0, 10);
        assertEquals(1, pageB.items().size());
        assertEquals(1, pageB.total());
    }

    @Test
    void pagination_respecte_page_et_size() {
        for (int i = 0; i < 5; i++) {
            creerAntecedent(UUID.randomUUID(), CENTRE_A, PATIENT_A, TypeAntecedent.MEDICAL,
                    new ConceptCode("CIM10", "N18.5", "IRC stade 5"), null);
        }
        PagedResult<Antecedent> p1 = repository.findPagedByPatientId(PATIENT_A, CENTRE_A, 0, 2);
        assertEquals(2, p1.items().size());
        assertEquals(5, p1.total());
        PagedResult<Antecedent> p2 = repository.findPagedByPatientId(PATIENT_A, CENTRE_A, 1, 2);
        assertEquals(2, p2.items().size());
        PagedResult<Antecedent> p3 = repository.findPagedByPatientId(PATIENT_A, CENTRE_A, 2, 2);
        assertEquals(1, p3.items().size());
    }

    @Test
    void save_met_a_jour_statut_clinique() {
        UUID id = UUID.randomUUID();
        Antecedent a = creerAntecedent(id, CENTRE_A, PATIENT_A, TypeAntecedent.MEDICAL,
                null, "Diabète");
        a.resoudre(LocalDate.of(2023, 5, 1), OffsetDateTime.parse("2026-03-10T08:00:00Z"));
        repository.save(a);
        Antecedent trouve = repository.trouver(new AntecedentId(id), CENTRE_A).orElseThrow();
        assertEquals(StatutClinique.RESOLU, trouve.getStatutClinique());
    }

    @Test
    void deleteById_supprime_uniquement_centre_propre() {
        UUID idA = UUID.randomUUID();
        creerAntecedent(idA, CENTRE_A, PATIENT_A, TypeAntecedent.MEDICAL,
                new ConceptCode("CIM10", "N18.5", "IRC stade 5"), null);
        repository.deleteById(new AntecedentId(idA), CENTRE_A);
        assertFalse(repository.trouver(new AntecedentId(idA), CENTRE_A).isPresent());
        assertEquals(0, repository.findByPatientId(PATIENT_A, CENTRE_A).size());
    }
}
