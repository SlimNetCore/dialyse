package com.hemodialyse.backend.contexts.gmao.contrat;

import com.hemodialyse.backend.contexts.gmao.application.equipement.HistoriqueDesStatuts;
import com.hemodialyse.backend.contexts.gmao.application.equipement.LectureEquipements;
import com.hemodialyse.backend.contexts.gmao.application.intervention.LectureInterventions;
import com.hemodialyse.backend.contexts.gmao.application.pilotage.LectureDesComptages;
import com.hemodialyse.backend.contexts.gmao.application.pilotage.LectureDesCouts;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementRepository;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionRepository;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.EquipementRepositoryJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.EquipementSpringData;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.EquipementStatutHistoriqueSpringData;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.HistoriqueDesStatutsJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.InterventionRepositoryJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.InterventionSpringData;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.LectureDesComptagesJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.LectureDesCoutsJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.LectureEquipementsJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.LectureInterventionsJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.LigneDeCoutSpringData;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.PlanMaintenanceSpringData;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.RectificationSpringData;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.TacheSpringData;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.GenerateursDuCentreJpa;
import com.hemodialyse.backend.contexts.gmao.publishedlanguage.GenerateursDuCentre;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Contrat des lectures de consultation et de pilotage exécuté contre les adapters JPA sur H2 (mode PostgreSQL). Chaque appel
 * de port s'exécute dans sa propre transaction. La variante PostgreSQL 16 réelle est {@code PilotagePostgresContractIT}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:gmao-contrat-pilotage;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PilotageJpaContratTest extends PilotageContrat {

    @Autowired
    private EquipementSpringData equipements;
    @Autowired
    private EquipementStatutHistoriqueSpringData historique;
    @Autowired
    private InterventionSpringData interventions;
    @Autowired
    private LigneDeCoutSpringData lignes;
    @Autowired
    private RectificationSpringData rectifications;
    @Autowired
    private TacheSpringData taches;
    @Autowired
    private PlanMaintenanceSpringData plans;
    @Autowired
    private JdbcTemplate jdbc;

    private EquipementRepositoryJpa equipementRepository;
    private InterventionRepositoryJpa interventionRepository;
    private LectureEquipementsJpa lectureEquipements;
    private LectureInterventionsJpa lectureInterventions;
    private HistoriqueDesStatutsJpa historiques;
    private LectureDesComptagesJpa comptages;
    private LectureDesCoutsJpa couts;

    @BeforeEach
    void creerLesAdaptersEtViderLesTables() {
        equipementRepository = new EquipementRepositoryJpa(equipements, historique);
        interventionRepository = new InterventionRepositoryJpa(interventions, lignes, rectifications, taches);
        lectureEquipements = new LectureEquipementsJpa(equipements);
        lectureInterventions = new LectureInterventionsJpa(interventions, interventionRepository);
        historiques = new HistoriqueDesStatutsJpa(historique);
        comptages = new LectureDesComptagesJpa(equipements, interventions, plans);
        couts = new LectureDesCoutsJpa(lignes);
        taches.deleteAll();
        rectifications.deleteAll();
        lignes.deleteAll();
        interventions.deleteAll();
        historique.deleteAll();
        equipements.deleteAll();
    }

    @Override
    protected EquipementRepository equipements() {
        return equipementRepository;
    }

    @Override
    protected LectureEquipements lectureEquipements() {
        return lectureEquipements;
    }

    @Override
    protected InterventionRepository interventions() {
        return interventionRepository;
    }

    @Override
    protected LectureInterventions lectureInterventions() {
        return lectureInterventions;
    }

    @Override
    protected HistoriqueDesStatuts historique() {
        return historiques;
    }

    @Override
    protected LectureDesComptages comptages() {
        return comptages;
    }

    @Override
    protected LectureDesCouts couts() {
        return couts;
    }

    @Override
    protected GenerateursDuCentre generateursDuCentre() {
        return new GenerateursDuCentreJpa(equipements);
    }

    @Override
    protected void supprimerLogiquementEquipement(EquipementId id) {
        jdbc.update("UPDATE gmao_equipements SET deleted_at = ? WHERE id = ?",
                Timestamp.from(Instant.parse("2026-02-01T00:00:00Z")), id.valeur());
    }

    @Override
    protected void supprimerLogiquementIntervention(InterventionId id) {
        jdbc.update("UPDATE gmao_interventions SET deleted_at = ? WHERE id = ?",
                Timestamp.from(Instant.parse("2026-02-01T00:00:00Z")), id.valeur());
    }
}
