package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.application.OrdonnancesAImprimer;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.OrdonnancesAImprimerJdbc;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Contrat des ordonnances à imprimer sur les tables réelles (H2, mode PostgreSQL) : le même jeu que le double en mémoire. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:dossier-medical-ordonnances-a-imprimer;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrdonnancesAImprimerJdbcContratTest extends OrdonnancesAImprimerContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void avant() {
        OrdonnancesJdbcContrat.creerLesTables(jdbc);
        vider();
    }

    @AfterEach
    void apres() {
        vider();
    }

    private void vider() {
        for (CentreId centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM lignes_ordonnance WHERE ordonnance_id IN (SELECT id FROM ordonnances WHERE center_id = ?)", centre.valeur());
            jdbc.update("DELETE FROM ordonnances WHERE center_id = ?", centre.valeur());
        }
    }

    @Override
    protected OrdonnancesAImprimer ordonnances() {
        return new OrdonnancesAImprimerJdbc(jdbc);
    }

    @Override
    protected void poser(CentreId centre, UUID id, UUID patient, String numero, LocalDate date, String medecin,
                         List<OrdonnancesAImprimer.LigneImprimable> lignes) {
        Timestamp t0 = Timestamp.from(Instant.parse("2026-01-01T10:00:00Z"));
        jdbc.update("INSERT INTO ordonnances (id, center_id, created_at, date_prescription, medecin_id, numero, patient_id, statut, "
                + "updated_at) VALUES (?,?,?,?,?,?,?,?,?)", id, centre.valeur(), t0, java.sql.Date.valueOf(date), medecin, numero, patient,
                "STATUT_DISPARU", t0);
        for (OrdonnancesAImprimer.LigneImprimable l : lignes) {
            jdbc.update("INSERT INTO lignes_ordonnance (id, ordonnance_id, medicament_code_display, libelle, posologie, voie, duree_jours, "
                            + "quantite, instructions) VALUES (?,?,?,?,?,?,?,?,?)", UUID.randomUUID(), id, l.libelleDuCode(), l.libelle(),
                    l.posologie(), l.voie(), l.dureeJours(), l.quantite(), l.instructions());
        }
    }
}
