package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.facturation.infrastructure.acl.patientcouverture.AgenceDuPayeurParLAnnuaire;
import com.hemodialyse.backend.contexts.facturation.infrastructure.persistence.RepriseDesSoldesJdbc;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.RepriseDesSoldes;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.RepriseDesSoldes.SoldeDOuverture;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le contrat de la reprise des soldes exécuté contre l'adaptateur JDBC ; les variantes H2 et PostgreSQL 16 ne diffèrent que par
 * leur base. Les lignes de test sont supprimées avant chaque test.
 */
public abstract class RepriseDesSoldesJdbcContrat extends RepriseDesSoldesContrat {

    protected abstract JdbcTemplate jdbc();

    protected void viderLesLignesDeTest() {
        SchemaDesFacturesH2.vider(jdbc(), CENTRE, AUTRE_CENTRE);
        for (String table : new String[]{"centre_payeur", "agence", "caisse_assurance"}) {
            jdbc().update("DELETE FROM " + table + " WHERE center_id IN (?, ?)", CENTRE, AUTRE_CENTRE);
        }
    }

    @Override
    protected RepriseDesSoldes reprise() {
        return new RepriseDesSoldesJdbc(jdbc(), new AgenceDuPayeurParLAnnuaire(
                new com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.PayeursDuCentreJdbc(jdbc())));
    }

    private UUID payeurDuCentre(UUID centre, UUID agence) {
        UUID caisse = UUID.randomUUID();
        UUID payeur = UUID.randomUUID();
        jdbc().update("INSERT INTO caisse_assurance (id, center_id, code, nom, type_caisse) VALUES (?, ?, ?, 'Caisse', 'STANDARD')",
                caisse, centre, "C" + caisse.toString().substring(0, 8));
        jdbc().update("INSERT INTO agence (id, center_id, caisse_id, code, nom) VALUES (?, ?, ?, ?, 'Agence')",
                agence, centre, caisse, "A" + agence.toString().substring(0, 8));
        jdbc().update("INSERT INTO centre_payeur (id, center_id, agence_id, code, nom) VALUES (?, ?, ?, ?, 'Payeur')",
                payeur, centre, agence, "P" + payeur.toString().substring(0, 8));
        return payeur;
    }

    private UUID agenceFigee(UUID facture) {
        return jdbc().queryForObject("SELECT agence_id_snapshot FROM factures WHERE id = ?", UUID.class, facture);
    }

    @Test
    void should_freeze_the_agence_of_the_payer_of_the_centre_on_the_opening_invoice() {
        UUID agence = UUID.randomUUID();
        UUID payeur = payeurDuCentre(CENTRE, agence);

        UUID facture = reprise().ouvrir(CENTRE, soldeAvecPayeur("REPRISE-AG1", payeur));

        assertThat(agenceFigee(facture)).isEqualTo(agence);
    }

    @Test
    void should_freeze_no_agence_for_an_unknown_payer_a_payer_of_another_centre_or_no_payer() {
        UUID payeurAilleurs = payeurDuCentre(AUTRE_CENTRE, UUID.randomUUID());

        assertThat(agenceFigee(reprise().ouvrir(CENTRE, soldeAvecPayeur("REPRISE-AG2", payeurAilleurs)))).isNull();
        assertThat(agenceFigee(reprise().ouvrir(CENTRE, soldeAvecPayeur("REPRISE-AG3", UUID.randomUUID())))).isNull();
        assertThat(agenceFigee(reprise().ouvrir(CENTRE, soldeAvecPayeur("REPRISE-AG4", null)))).isNull();
    }

    private static SoldeDOuverture soldeAvecPayeur(String numero, UUID payeur) {
        SoldeDOuverture base = solde(numero, "10.00", null);
        return new SoldeDOuverture(base.patient(), base.numero(), base.dateDeFacture(), base.debutDePeriode(), base.finDePeriode(),
                base.montantTtc(), base.montantRegle(), base.dateDuReglement(), base.libelle(), base.codePatient(), base.nomComplet(),
                base.statutDuPatient(), base.numeroImmatriculation(), payeur);
    }

    @Override
    protected void poserUnReglementDeLaPlateforme(UUID centre, UUID facture, String montant) {
        jdbc().update("INSERT INTO facture_reglements (id, facture_id, center_id, montant, date_reglement, saisi_par) "
                + "VALUES (?, ?, ?, ?, ?, 'caissier')", UUID.randomUUID(), facture, centre, new BigDecimal(montant),
                Date.valueOf(LocalDate.of(2026, 4, 1)));
    }

    @Override
    protected int lignes(UUID facture) {
        return jdbc().queryForObject("SELECT COUNT(*) FROM facture_lignes WHERE facture_id = ?", Integer.class, facture);
    }

    @Override
    protected int reglementsDeReprise(UUID facture) {
        return jdbc().queryForObject("SELECT COUNT(*) FROM facture_reglements WHERE facture_id = ? AND saisi_par = ?",
                Integer.class, facture, RepriseDesSoldes.AUTEUR_DE_LA_REPRISE);
    }

    @Override
    protected BigDecimal totalTtc(UUID facture) {
        return jdbc().queryForObject("SELECT total_ttc FROM factures WHERE id = ?", BigDecimal.class, facture);
    }
}
