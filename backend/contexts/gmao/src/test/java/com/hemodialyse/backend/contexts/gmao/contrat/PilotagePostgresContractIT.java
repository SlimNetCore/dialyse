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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat PostgreSQL 16 réel de la slice G6 : la MÊME suite {@link PilotageContrat} que les doubles en mémoire et les adapters
 * sur H2, sur les tables historiques {@code gmao_equipements}, {@code gmao_equipement_statut_historique},
 * {@code gmao_interventions} et {@code gmao_lignes_cout_intervention}. Les sommes de coûts (quantité fois prix) et les comptages
 * sont calculés par la base. Base JETABLE uniquement (voir backend/scripts/gmao-pg16-rejeu.sh) : le test vide ces tables.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=${gmao.pg16.url}",
        "spring.datasource.username=${gmao.pg16.user:gmao_test}",
        "spring.datasource.password=${gmao.pg16.password:}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.sql.init.mode=never"
})
@EnabledIfSystemProperty(named = "gmao.pg16.url", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PilotagePostgresContractIT extends PilotageContrat {

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
        jdbc.execute("TRUNCATE gmao_interventions, gmao_lignes_cout_intervention, gmao_rectifications_intervention, "
                + "gmao_taches_intervention, gmao_equipements, gmao_equipement_statut_historique CASCADE");
        // le schéma Flyway réel porte les clés étrangères vers centers et app_user
        ReferentielPostgres.centre(jdbc, CENTRE_A.valeur());
        ReferentielPostgres.centre(jdbc, CENTRE_B.valeur());
        ReferentielPostgres.utilisateur(jdbc, ADMIN.valeur());
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
        jdbc.update("UPDATE gmao_equipements SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", id.valeur());
    }

    @Override
    protected void supprimerLogiquementIntervention(InterventionId id) {
        jdbc.update("UPDATE gmao_interventions SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", id.valeur());
    }

    @Test
    void should_run_on_a_real_postgresql_16() {
        assertThat(jdbc.queryForObject("SHOW server_version", String.class)).startsWith("16.");
    }
}
