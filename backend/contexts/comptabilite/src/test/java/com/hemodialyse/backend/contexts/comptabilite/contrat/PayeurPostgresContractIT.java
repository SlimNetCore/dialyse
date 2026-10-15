package com.hemodialyse.backend.contexts.comptabilite.contrat;

import com.hemodialyse.backend.contexts.comptabilite.application.payeur.LectureDesPayeurs;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.payeur.CompteClientDuPayeurRepository;
import com.hemodialyse.backend.contexts.comptabilite.domain.payeur.PayeurId;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.CompteClientDuPayeurRepositoryJpa;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.ComptePayeurSpringData;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.PayeursDuCentreJdbc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Contrat des payeurs exécuté contre les adaptateurs JPA et JDBC sur un PostgreSQL 16 RÉEL et JETABLE
 * (voir backend/scripts/comptabilite-pg16-rejeu.sh).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=${comptabilite.pg16.url}",
        "spring.datasource.username=${comptabilite.pg16.user:comptabilite_test}",
        "spring.datasource.password=${comptabilite.pg16.password:}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.sql.init.mode=never"
})
@EnabledIfSystemProperty(named = "comptabilite.pg16.url", matches = ".+")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PayeurPostgresContractIT extends PayeurContrat {

    @Autowired
    private ComptePayeurSpringData donnees;
    @Autowired
    private JdbcTemplate jdbc;

    private CompteClientDuPayeurRepository comptesClients;
    private LectureDesPayeurs lecture;

    @BeforeEach
    void creerLesAdaptateursEtViderLesTables() {
        // centre_payeur est tenu hors de la comptabilité : la table est créée ici (en production elle vient des migrations)
        jdbc.execute("CREATE TABLE IF NOT EXISTS centre_payeur (id UUID PRIMARY KEY, center_id UUID NOT NULL, agence_id UUID, "
                + "code VARCHAR(255), nom VARCHAR(255), adresse VARCHAR(255))");
        jdbc.update("DELETE FROM centre_payeur");
        donnees.deleteAll();
        comptesClients = new CompteClientDuPayeurRepositoryJpa(donnees);
        lecture = new PayeursDuCentreJdbc(jdbc);
    }

    @Override
    protected CompteClientDuPayeurRepository comptesClients() {
        return comptesClients;
    }

    @Override
    protected LectureDesPayeurs lecture() {
        return lecture;
    }

    @Override
    protected PayeurId payeur(CentreId centre, String code, String nom) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO centre_payeur (id, center_id, agence_id, code, nom) VALUES (?, ?, ?, ?, ?)", id,
                centre.valeur(), UUID.randomUUID(), code, nom);
        return new PayeurId(id);
    }

    @Override
    protected void poserLeCompteBrut(CentreId centre, PayeurId payeur, String compte) {
        jdbc.update("INSERT INTO comptes_payeurs (center_id, payeur_id, compte) VALUES (?, ?, ?)", centre.valeur(),
                payeur.valeur(), compte);
    }
}
