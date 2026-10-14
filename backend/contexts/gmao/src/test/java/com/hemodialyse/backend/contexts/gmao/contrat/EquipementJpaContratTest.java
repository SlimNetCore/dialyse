package com.hemodialyse.backend.contexts.gmao.contrat;

import com.hemodialyse.backend.contexts.gmao.application.equipement.LectureEquipements;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.CodesEquipementDuCentre;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementRepository;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.EquipementRepositoryJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.EquipementSpringData;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.EquipementStatutHistoriqueSpringData;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.LectureEquipementsJpa;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Contrat des équipements exécuté contre l'adapter JPA sur H2 (mode PostgreSQL). Chaque appel de port s'exécute dans sa
 * propre transaction.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:gmao-contrat-equipement;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EquipementJpaContratTest extends EquipementContrat {

    @Autowired
    private EquipementSpringData equipements;
    @Autowired
    private EquipementStatutHistoriqueSpringData historique;

    private EquipementRepositoryJpa repository;
    private LectureEquipementsJpa lecture;

    @BeforeEach
    void creerLesAdaptersEtViderLesTables() {
        repository = new EquipementRepositoryJpa(equipements, historique);
        lecture = new LectureEquipementsJpa(equipements);
        historique.deleteAll();
        equipements.deleteAll();
    }

    @Override
    protected EquipementRepository repository() {
        return repository;
    }

    @Override
    protected CodesEquipementDuCentre codes() {
        return repository;
    }

    @Override
    protected LectureEquipements lecture() {
        return lecture;
    }

    @Override
    protected void supprimerLogiquement(EquipementId id) {
        equipements.findById(id.valeur()).ifPresent(e -> {
            e.setDeletedAt(Instant.parse("2026-02-01T00:00:00Z"));
            equipements.saveAndFlush(e);
        });
    }
}
