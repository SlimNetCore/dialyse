package com.hemodialyse.backend.contexts.gmao.contrat;

import com.hemodialyse.backend.contexts.gmao.application.EquipementExiste;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.EquipementRepositoryJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.EquipementSpringData;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.EquipementStatutHistoriqueSpringData;
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

/** Contrat de {@link EquipementExiste} contre le repository JPA des équipements (G3 propriétaire de la table). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:gmao-equipements-contrat;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EquipementExisteJpaContratTest extends EquipementExisteContrat {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EquipementSpringData equipements;
    @Autowired
    private EquipementStatutHistoriqueSpringData historique;

    private EquipementExiste adapter;

    @BeforeEach
    void creerLAdapter() {
        adapter = new EquipementRepositoryJpa(equipements, historique);
        jdbc.update("DELETE FROM public.gmao_equipements");
    }

    @Override
    protected EquipementExiste equipements() {
        return adapter;
    }

    @Override
    protected void preparerLeReferentiel() {
        String colonnes = "(id, code, designation, type, centre_id, statut, date_installation, date_creation, cree_par, deleted_at)";
        jdbc.update("INSERT INTO public.gmao_equipements " + colonnes + " VALUES (?, 'C-1', 'Osmoseur', 'AUTRE', ?, 'EN_SERVICE', "
                + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, NULL)", CONNU.valeur(), CONNU.valeur(), CONNU.valeur());
        jdbc.update("INSERT INTO public.gmao_equipements " + colonnes + " VALUES (?, 'C-2', 'Ancien', 'AUTRE', ?, 'EN_SERVICE', "
                + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?)", SUPPRIME_LOGIQUEMENT.valeur(), CONNU.valeur(),
                CONNU.valeur(), Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
    }
}
