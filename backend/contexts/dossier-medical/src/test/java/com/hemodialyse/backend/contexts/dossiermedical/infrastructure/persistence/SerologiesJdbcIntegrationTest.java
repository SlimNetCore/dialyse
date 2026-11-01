package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.DossierMedicalTestApplication;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ResultatSerologie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Serologie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SerologieId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SerologieRepository;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeSerologie;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test d'intégration de l'adaptateur JDBC {@link SerologiesJdbc}.
 * Vérifie l'isolation multi-centre (centerId) et la pagination.
 */
@SpringBootTest(classes = DossierMedicalTestApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:dossier-medical-serologies;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:db/schema.sql"
        })
class SerologiesJdbcIntegrationTest {

    private static final CentreId CENTRE_A = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000a"));
    private static final CentreId CENTRE_B = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000b"));
    private static final UUID PATIENT_A = UUID.fromString("99997300-0000-0000-0000-000000000101");
    private static final UUID PATIENT_B = UUID.fromString("99997300-0000-0000-0000-000000000102");

    @Autowired
    private SerologieRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM serologies");
    }

    private Serologie creerSerologie(UUID id, CentreId centre, UUID patient, TypeSerologie type,
                                     ResultatSerologie resultat) {
        Serologie s = Serologie.reconstituer(
                new SerologieId(id), patient, centre, "MED-001",
                LocalDate.of(2026, 3, 10), type, resultat,
                "45.0", "UI/mL", Instant.parse("2026-03-11T10:00:00Z"),
                "Labo Central", "Bilan",
                OffsetDateTime.parse("2026-03-10T08:00:00Z"),
                OffsetDateTime.parse("2026-03-10T08:00:00Z"));
        repository.save(s);
        return s;
    }

    @Test
    void save_et_trouver_centre_a() {
        UUID id = UUID.randomUUID();
        Serologie s = creerSerologie(id, CENTRE_A, PATIENT_A, TypeSerologie.VHB, ResultatSerologie.NEGATIF);
        Serologie trouve = repository.trouver(s.id(), CENTRE_A).orElseThrow();
        assertEquals(id, trouve.id().value());
        assertEquals(ResultatSerologie.NEGATIF, trouve.resultat());
        assertEquals(TypeSerologie.VHB, trouve.typeSerologie());
    }

    @Test
    void trouver_centre_b_ne_retourne_pas_donnees_centre_a() {
        UUID idA = UUID.randomUUID();
        creerSerologie(idA, CENTRE_A, PATIENT_A, TypeSerologie.VHB, ResultatSerologie.POSITIF);
        assertFalse(repository.trouver(new SerologieId(idA), CENTRE_B).isPresent());
    }

    @Test
    void trouver_retourne_aucun_pour_autre_centre() {
        creerSerologie(UUID.randomUUID(), CENTRE_A, PATIENT_A, TypeSerologie.VHC, ResultatSerologie.NEGATIF);
        assertFalse(repository.trouver(new SerologieId(UUID.randomUUID()), CENTRE_B).isPresent());
    }

    @Test
    void lister_par_patient_est_page_et_filtre_centre() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();
        creerSerologie(id1, CENTRE_A, PATIENT_A, TypeSerologie.VHB, ResultatSerologie.NEGATIF);
        creerSerologie(id2, CENTRE_A, PATIENT_A, TypeSerologie.VHC, ResultatSerologie.POSITIF);
        creerSerologie(id3, CENTRE_A, PATIENT_B, TypeSerologie.VHB, ResultatSerologie.NEGATIF);
        // Données d'un autre centre : ne doivent pas apparaître
        creerSerologie(UUID.randomUUID(), CENTRE_B, PATIENT_A, TypeSerologie.VHB, ResultatSerologie.NEGATIF);

        PagedResult<Serologie> page = repository.listerParPatient(CENTRE_A, PATIENT_A, 0, 10);
        assertEquals(2, page.items().size());
        assertEquals(2, page.total());
        assertEquals(0, page.page());
        assertEquals(10, page.size());

        PagedResult<Serologie> pageB = repository.listerParPatient(CENTRE_A, PATIENT_B, 0, 10);
        assertEquals(1, pageB.items().size());
        assertEquals(1, pageB.total());
    }

    @Test
    void pagination_respecte_page_et_size() {
        for (int i = 0; i < 5; i++) {
            creerSerologie(UUID.randomUUID(), CENTRE_A, PATIENT_A, TypeSerologie.VHB, ResultatSerologie.NEGATIF);
        }
        PagedResult<Serologie> p1 = repository.listerParPatient(CENTRE_A, PATIENT_A, 0, 2);
        assertEquals(2, p1.items().size());
        assertEquals(5, p1.total());
        PagedResult<Serologie> p2 = repository.listerParPatient(CENTRE_A, PATIENT_A, 1, 2);
        assertEquals(2, p2.items().size());
        PagedResult<Serologie> p3 = repository.listerParPatient(CENTRE_A, PATIENT_A, 2, 2);
        assertEquals(1, p3.items().size());
    }

    @Test
    void lister_filtre_par_resultat_positif() {
        creerSerologie(UUID.randomUUID(), CENTRE_A, PATIENT_A, TypeSerologie.VHB, ResultatSerologie.POSITIF);
        creerSerologie(UUID.randomUUID(), CENTRE_A, PATIENT_A, TypeSerologie.VHC, ResultatSerologie.NEGATIF);
        creerSerologie(UUID.randomUUID(), CENTRE_A, PATIENT_A, TypeSerologie.VIH, ResultatSerologie.POSITIF);

        PagedResult<Serologie> page = repository.listerParPatient(CENTRE_A, PATIENT_A, 0, 10);
        long positifs = page.items().stream()
                .filter(s -> s.resultat() == ResultatSerologie.POSITIF)
                .count();
        assertEquals(2, positifs);
    }
}
