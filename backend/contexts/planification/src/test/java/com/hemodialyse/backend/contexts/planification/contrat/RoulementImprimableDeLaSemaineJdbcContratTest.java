package com.hemodialyse.backend.contexts.planification.contrat;

import com.hemodialyse.backend.contexts.planification.application.RoulementImprimableDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.infrastructure.persistence.RoulementImprimableDeLaSemaineJdbc;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;

/** Contrat du roulement imprimé d'une semaine contre l'adapter JDBC sur H2 (mode PostgreSQL), DOC5e (ADR 0197). */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:planification-contrat-doc5e;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RoulementImprimableDeLaSemaineJdbcContratTest extends RoulementImprimableDeLaSemaineContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void creerLesTablesEtViderLesLignes() {
        SchemaDuParametrageH2.creer(jdbc);
        SchemaDuParametrageH2.creerLesInfirmiers(jdbc);
        SchemaDuParametrageH2.viderLesInfirmiers(jdbc, CENTRE.valeur(), AUTRE.valeur());
    }

    @Override
    protected RoulementImprimableDeLaSemaine roulement() {
        return new RoulementImprimableDeLaSemaineJdbc(jdbc);
    }

    @Override
    protected void infirmier(CentreId centre, UUID id, String nom, String prenom, boolean actif) {
        jdbc.update("INSERT INTO infirmier (id, center_id, matricule, nom, prenom, qualification, habilite_isolement, actif) "
                + "VALUES (?, ?, ?, ?, ?, 'INFIRMIER', FALSE, ?)", id, centre.valeur(), "M" + id.toString().substring(0, 8), nom, prenom, actif);
    }

    @Override
    protected void affectation(CentreId centre, UUID infirmier, UUID salle, UUID creneau, String jours) {
        jdbc.update("INSERT INTO infirmier_affectation (id, center_id, infirmier_id, salle_id, creneau_id, jours) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), centre.valeur(), infirmier, salle, creneau, jours);
    }

    @Override
    protected void absence(CentreId centre, UUID infirmier, LocalDate debut, LocalDate fin) {
        jdbc.update("INSERT INTO infirmier_absence (id, center_id, infirmier_id, date_debut, date_fin, type) VALUES (?, ?, ?, ?, ?, 'CONGE')",
                UUID.randomUUID(), centre.valeur(), infirmier, Date.valueOf(debut), Date.valueOf(fin));
    }

    @Override
    protected void remplacement(CentreId centre, UUID infirmier, LocalDate jour, UUID salle, UUID creneau) {
        jdbc.update("INSERT INTO infirmier_remplacement (id, center_id, date_jour, salle_id, creneau_id, infirmier_id) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), centre.valeur(), Date.valueOf(jour), salle, creneau, infirmier);
    }
}
