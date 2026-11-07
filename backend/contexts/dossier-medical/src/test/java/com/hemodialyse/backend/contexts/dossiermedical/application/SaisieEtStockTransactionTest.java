package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.application.SaisirUneAdministration.Saisie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.RegleDeGestionViolee;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.AdministrationsJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.transaction.UniteDeTravailSpring;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PREUVE TRANSACTIONNELLE RÉELLE (H2, vraie transaction Spring, adapters JDBC) de la saisie d'une administration avec sortie de stock :
 * un refus du Stock ne laisse ni administration ni mouvement ; un échec APRÈS la sortie de stock (saisie refusée par la validation)
 * annule aussi la sortie ; une saisie valide enregistre les deux. Le Stock est simulé par une table de mouvements écrite avec la même
 * connexion (comme l'adapter réel qui rejoint la transaction en cours).
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:dossier-medical-saisie-stock;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SaisieEtStockTransactionTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d2a1"));
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000d2001");
    private static final UUID ARTICLE = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID SEANCE = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactions;

    private boolean stockRefuse;

    @BeforeEach
    void avant() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS mouvements_fictifs (id UUID PRIMARY KEY, center_id UUID NOT NULL, quantite NUMERIC(10, 3) NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS administrations_anemie (id UUID PRIMARY KEY, patient_id UUID NOT NULL, center_id UUID NOT NULL, "
                + "prescription_medicale_id UUID, type_traitement VARCHAR(20) NOT NULL, molecule VARCHAR(100), dose NUMERIC(10, 3), "
                + "unite_dose VARCHAR(20), voie VARCHAR(10), date_administration DATE NOT NULL, seance_id UUID, administre_par VARCHAR(100), "
                + "administree BOOLEAN NOT NULL, motif_non_administration VARCHAR(500), article_id UUID, quantite_article NUMERIC(10, 3), "
                + "created_at TIMESTAMP WITH TIME ZONE NOT NULL)");
        vider();
        stockRefuse = false;
    }

    @AfterEach
    void apres() {
        vider();
    }

    private void vider() {
        jdbc.update("DELETE FROM mouvements_fictifs WHERE center_id = ?", CENTRE.valeur());
        jdbc.update("DELETE FROM administrations_anemie WHERE center_id = ?", CENTRE.valeur());
    }

    private SaisirUneAdministration saisir() {
        var unite = new UniteDeTravailSpring(new TransactionTemplate(transactions), jdbc, null);
        CatalogueDesArticles catalogue = new CatalogueDesArticles() {
            @Override
            public java.util.Optional<ArticleDeTraitement> article(CentreId centre, UUID article) {
                return java.util.Optional.empty();
            }

            @Override
            public void verifierLaConversionDeLaDose(CentreId centre, UUID article, int dose, String unite) {
            }

            @Override
            public BigDecimal quantitePourLaDose(CentreId centre, UUID article, BigDecimal dose, String unite) {
                return dose.divide(new BigDecimal("4000"));
            }
        };
        SortiesDeStockDesAdministrations stock = (centre, seance, patient, jour, article, quantite, auteur) -> {
            if (stockRefuse) throw new RegleDeGestionViolee("SEANCE_FACTUREE_NON_MODIFIABLE", "La seance facturee ne peut plus etre modifiee");
            jdbc.update("INSERT INTO mouvements_fictifs (id, center_id, quantite) VALUES (?,?,?)", UUID.randomUUID(), centre.valeur(), quantite);
        };
        return new SaisirUneAdministration(demande -> CENTRE, (centre, patient) -> true, new AdministrationsJdbc(jdbc), catalogue, stock, unite,
                UUID::randomUUID, HorlogeDuCentre.enUtc(Clock.fixed(Instant.parse("2026-01-09T10:00:00Z"), ZoneOffset.UTC)),
                new com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.NotificationsDeLAnemie() {
                    @Override
                    public void administrationSaisie(UUID centre, UUID patient, LocalDate jour) {
                    }

                    @Override
                    public void observanceNonRespectee(UUID centre, UUID patient, String message, java.util.Map<String, String> details) {
                    }
                });
    }

    private int administrations() {
        return jdbc.queryForObject("SELECT count(*) FROM administrations_anemie WHERE center_id = ?", Integer.class, CENTRE.valeur());
    }

    private int mouvements() {
        return jdbc.queryForObject("SELECT count(*) FROM mouvements_fictifs WHERE center_id = ?", Integer.class, CENTRE.valeur());
    }

    @Test
    void une_saisie_valide_enregistre_l_administration_et_le_mouvement_de_stock() {
        saisir().executer(null, PATIENT, new Saisie(null, "EPO", "Epoetine", new BigDecimal("8000"), "UI", "SC", LocalDate.of(2026, 1, 5), SEANCE, "inf",
                true, null, ARTICLE, null));
        assertThat(administrations()).isEqualTo(1);
        assertThat(mouvements()).isEqualTo(1);
    }

    @Test
    void un_refus_du_stock_ne_laisse_ni_administration_ni_mouvement() {
        stockRefuse = true;
        assertThatThrownBy(() -> saisir().executer(null, PATIENT, new Saisie(null, "EPO", "Epoetine", new BigDecimal("8000"), "UI", "SC",
                LocalDate.of(2026, 1, 5), SEANCE, "inf", true, null, ARTICLE, null))).isInstanceOf(RegleDeGestionViolee.class);
        assertThat(administrations()).isZero();
        assertThat(mouvements()).isZero();
    }

    @Test
    void un_echec_apres_la_sortie_de_stock_annule_aussi_la_sortie() {
        // Pas de dose mais une quantité fournie : la sortie de stock a lieu (comme le legacy), puis la validation refuse la saisie.
        assertThatThrownBy(() -> saisir().executer(null, PATIENT, new Saisie(null, "EPO", "Epoetine", null, null, "SC", LocalDate.of(2026, 1, 5), SEANCE,
                "inf", true, null, ARTICLE, new BigDecimal("1")))).isInstanceOf(RegleDeGestionViolee.class).extracting("code")
                .isEqualTo("ADMINISTRATION_DOSE_REQUISE");
        assertThat(administrations()).isZero();
        assertThat(mouvements()).as("la sortie de stock est annulée avec la saisie").isZero();
    }
}
