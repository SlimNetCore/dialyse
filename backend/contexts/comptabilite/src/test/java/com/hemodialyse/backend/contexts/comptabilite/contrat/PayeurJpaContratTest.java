package com.hemodialyse.backend.contexts.comptabilite.contrat;

import com.hemodialyse.backend.contexts.comptabilite.application.payeur.LectureDesPayeurs;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.payeur.CompteClientDuPayeurRepository;
import com.hemodialyse.backend.contexts.comptabilite.domain.payeur.PayeurId;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.CompteClientDuPayeurRepositoryJpa;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.ComptePayeurSpringData;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.acl.patientcouverture.PayeursParLAnnuaire;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Contrat des payeurs exécuté contre les adaptateurs JPA ({@code comptes_payeurs}) et JDBC ({@code centre_payeur}) sur H2
 * (mode PostgreSQL). Variante PostgreSQL 16 : {@code PayeurPostgresContractIT}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:comptabilite-contrat-payeur;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PayeurJpaContratTest extends PayeurContrat {

    @Autowired
    private ComptePayeurSpringData donnees;
    @Autowired
    private JdbcTemplate jdbc;

    private CompteClientDuPayeurRepository comptesClients;
    private LectureDesPayeurs lecture;

    @BeforeEach
    void creerLesAdaptateursEtViderLesTables() {
        // les tables des payeurs sont tenues par Patient et couverture : créées ici (en production elles viennent des migrations)
        jdbc.execute("CREATE TABLE IF NOT EXISTS caisse_assurance (id UUID PRIMARY KEY, center_id UUID NOT NULL, code VARCHAR(255), "
                + "nom VARCHAR(255), type_caisse VARCHAR(50))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS agence (id UUID PRIMARY KEY, center_id UUID NOT NULL, caisse_id UUID, "
                + "code VARCHAR(255), nom VARCHAR(255))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS centre_payeur (id UUID PRIMARY KEY, center_id UUID NOT NULL, agence_id UUID, "
                + "code VARCHAR(255), nom VARCHAR(255), adresse VARCHAR(255))");
        jdbc.update("DELETE FROM centre_payeur");
        donnees.deleteAll();
        comptesClients = new CompteClientDuPayeurRepositoryJpa(donnees);
        lecture = new PayeursParLAnnuaire(new com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence
                .PayeursDuCentreJdbc(jdbc), jdbc);
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
