package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.facturation.application.consultation.FactureListee;
import com.hemodialyse.backend.contexts.facturation.application.consultation.ListeDesFactures;
import com.hemodialyse.backend.contexts.facturation.infrastructure.persistence.ListeDesFacturesJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.LotsDePatientsJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.PayeursDuCentreJdbc;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat de la lecture des factures émises exécuté contre {@link ListeDesFacturesJdbc} sur H2 (mode PostgreSQL), plus la résolution propre à
 * l'adapter : fiche du patient du centre (sinon instantané de la facture) et caisse de la chaîne des payeurs (figée, sinon courante).
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:facturation-contrat-liste-factures;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ListeDesFacturesJdbcContratTest extends ListeDesFacturesContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void creerLesTables() {
        SchemaDesFacturesH2.creer(jdbc);
        jdbc.execute("CREATE TABLE IF NOT EXISTS patients (id UUID PRIMARY KEY, center_id UUID NOT NULL, numero_assurance VARCHAR(255), "
                + "nom VARCHAR(255), prenom VARCHAR(255), centre_payeur_id UUID, code_patient VARCHAR(50), etat_patient VARCHAR(30), "
                + "sexe VARCHAR(5), date_naissance DATE, date_admission DATE, type_patient VARCHAR(30), created_at TIMESTAMP WITH TIME ZONE)");
        vider();
    }

    @AfterEach
    void vider() {
        SchemaDesFacturesH2.vider(jdbc, CENTRE_A.valeur(), CENTRE_B.valeur());
        for (String table : new String[]{"patients", "centre_payeur", "agence", "caisse_assurance"}) {
            jdbc.update("DELETE FROM " + table + " WHERE center_id IN (?, ?)", CENTRE_A.valeur(), CENTRE_B.valeur());
        }
    }

    @Override
    protected ListeDesFactures liste() {
        return new ListeDesFacturesJdbc(jdbc, new PayeursDuCentreJdbc(jdbc), new LotsDePatientsJdbc(jdbc));
    }

    @Override
    protected void poserLaFacture(CentreId centre, UUID id, String numero, LocalDate date, String ttc) {
        BigDecimal total = new BigDecimal(ttc);
        jdbc.update("INSERT INTO factures (id, center_id, patient_id, numero_facture, period_start, period_end, date_facturation, "
                        + "tva_rate, total_ht, total_tva, total_ttc) VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, 0, ?)",
                id, centre.valeur(), UUID.randomUUID(), numero, Date.valueOf(date.withDayOfMonth(1)), Date.valueOf(date), Date.valueOf(date),
                total, total);
    }

    @Override
    protected void poserLaLigne(CentreId centre, UUID facture, UUID forfait, String libelle, String prix, int nombre) {
        BigDecimal pu = new BigDecimal(prix);
        jdbc.update("INSERT INTO facture_lignes (id, facture_id, center_id, forfait_id, forfait_label, unit_price_ht, seance_count, line_ht) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), facture, centre.valeur(), forfait, libelle, pu, nombre,
                pu.multiply(BigDecimal.valueOf(nombre)));
    }

    @Override
    protected void poserLeReglement(CentreId centre, UUID facture, String montant) {
        jdbc.update("INSERT INTO facture_reglements (id, facture_id, center_id, montant, date_reglement) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), facture, centre.valeur(), new BigDecimal(montant), Date.valueOf(LocalDate.of(2026, 11, 2)));
    }

    private UUID caisse(CentreId centre, String nom) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO caisse_assurance (id, center_id, code, nom) VALUES (?, ?, ?, ?)", id, centre.valeur(), nom, nom);
        return id;
    }

    private UUID agence(CentreId centre, UUID caisse) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO agence (id, center_id, caisse_id, code, nom) VALUES (?, ?, ?, 'AG', 'Agence')", id, centre.valeur(), caisse);
        return id;
    }

    private UUID centrePayeur(CentreId centre, UUID agence) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO centre_payeur (id, center_id, agence_id, code, nom) VALUES (?, ?, ?, 'CP', 'Centre payeur')", id,
                centre.valeur(), agence);
        return id;
    }

    @Test
    void should_read_the_patient_record_of_the_centre_or_the_snapshot_and_the_caisse_of_the_frozen_then_current_payer_chain() {
        poserLesFactures();
        UUID cnas = caisse(CENTRE_A, "CNAS");
        UUID casnos = caisse(CENTRE_A, "CASNOS");
        UUID cpCnas = centrePayeur(CENTRE_A, agence(CENTRE_A, cnas));
        UUID agCasnos = agence(CENTRE_A, casnos);
        UUID patient = UUID.randomUUID();
        jdbc.update("INSERT INTO patients (id, center_id, numero_assurance, nom, prenom, centre_payeur_id, code_patient) "
                + "VALUES (?, ?, '123456789012', 'BENALI', 'Karim', ?, 'P1')", patient, CENTRE_A.valeur(), cpCnas);
        UUID etranger = UUID.randomUUID();
        jdbc.update("INSERT INTO patients (id, center_id, numero_assurance, nom, prenom, code_patient) VALUES (?, ?, 'B-1', 'Etranger', "
                + "'Zed', 'PB')", etranger, CENTRE_B.valeur());
        jdbc.update("UPDATE factures SET patient_id = ? WHERE id IN (?, ?)", patient, F1, F2);
        jdbc.update("UPDATE factures SET agence_id_snapshot = ? WHERE id = ?", agCasnos, F2);
        jdbc.update("UPDATE factures SET patient_id = ?, patient_full_name = 'Instantane Nom', numero_immatriculation_snapshot = 'SNAP-1' "
                + "WHERE id = ?", etranger, F3);
        jdbc.update("UPDATE factures SET tva_rate = 9, total_ht = 917.43, total_tva = 82.57 WHERE id = ?", F1);

        FactureListee f1 = liste().deLaFacture(CENTRE_A, F1).orElseThrow();
        FactureListee f2 = liste().deLaFacture(CENTRE_A, F2).orElseThrow();
        FactureListee f3 = liste().deLaFacture(CENTRE_A, F3).orElseThrow();

        assertThat(f1.patientId()).isEqualTo(patient);
        assertThat(f1.patientNom()).isEqualTo("BENALI");
        assertThat(f1.patientPrenom()).isEqualTo("Karim");
        assertThat(f1.numeroAssurance()).isEqualTo("123456789012");
        assertThat(f1.caisseId()).isEqualTo(cnas);
        assertThat(f1.caisse()).isEqualTo("CNAS");
        assertThat(f1.tauxTva()).isEqualTo(new BigDecimal("9.00"));
        assertThat(f1.montantHt()).isEqualTo(new BigDecimal("917.43"));
        assertThat(f1.montantTva()).isEqualTo(new BigDecimal("82.57"));
        assertThat(f1.periodeDebut()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(f1.periodeFin()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(f2.caisseId()).isEqualTo(casnos);
        assertThat(f3.patientNom()).isEqualTo("Instantane Nom");
        assertThat(f3.patientPrenom()).isEmpty();
        assertThat(f3.numeroAssurance()).isEqualTo("SNAP-1");
        assertThat(f3.caisseId()).isNull();
        assertThat(f3.caisse()).isEqualTo("Non renseignee");
        assertThat(liste().entre(CENTRE_A, DU, AU)).extracting(FactureListee::caisse).containsExactly("Non renseignee", "CASNOS", "CNAS");
    }
}
