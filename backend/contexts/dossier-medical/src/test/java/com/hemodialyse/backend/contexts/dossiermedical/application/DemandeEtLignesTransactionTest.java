package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.application.DemanderUnExamen.LigneDemandee;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.DemandesDExamenJdbc;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PREUVE TRANSACTIONNELLE RÉELLE (H2, vraie transaction Spring, adapter JDBC) de la demande d'un examen : l'en-tête et les lignes sont
 * deux écritures d'une même transaction. Un échec sur la seconde ligne (identifiant déjà pris) ne laisse NI en-tête NI première ligne ;
 * une demande valide enregistre tout.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:dossier-medical-demande-lignes;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DemandeEtLignesTransactionTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d3a1"));
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000d3001");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactions;

    @BeforeEach
    void avant() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS demandes_examen (id UUID PRIMARY KEY, patient_id UUID NOT NULL, center_id UUID NOT NULL, "
                + "prescripteur_id VARCHAR(100), date_demande DATE NOT NULL, categorie VARCHAR(15) NOT NULL, urgent BOOLEAN NOT NULL, "
                + "motif VARCHAR(500), statut VARCHAR(20) NOT NULL, conclusion TEXT, created_at TIMESTAMP WITH TIME ZONE NOT NULL, "
                + "updated_at TIMESTAMP WITH TIME ZONE NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS lignes_demande_examen (id UUID PRIMARY KEY, demande_id UUID NOT NULL, "
                + "analyte_code_system VARCHAR(10), analyte_code VARCHAR(20), analyte_code_display VARCHAR(255), libelle VARCHAR(255), "
                + "commentaire VARCHAR(500))");
        vider();
    }

    @AfterEach
    void apres() {
        vider();
    }

    private void vider() {
        jdbc.update("DELETE FROM lignes_demande_examen");
        jdbc.update("DELETE FROM demandes_examen WHERE center_id = ?", CENTRE.valeur());
    }

    private DemanderUnExamen demander(UUID identifiantUnique) {
        var unite = new UniteDeTravailSpring(new TransactionTemplate(transactions), jdbc, null);
        // Quand `identifiantUnique` est fourni, TOUS les identifiants (demande et lignes) sont identiques : la seconde ligne viole la clé primaire.
        GenerateurDIdentifiants identifiants = identifiantUnique != null ? () -> identifiantUnique : UUID::randomUUID;
        return new DemanderUnExamen(demande -> CENTRE, (centre, patient) -> true, new DemandesDExamenJdbc(jdbc), unite, identifiants,
                HorlogeDuCentre.enUtc(Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC)));
    }

    private static List<LigneDemandee> deuxLignes() {
        return List.of(new LigneDemandee("LOINC", "718-7", null, "Hemoglobine", null), new LigneDemandee(null, null, null, "Ferritine", null));
    }

    private int compter(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    @Test
    void un_echec_sur_une_ligne_annule_aussi_l_en_tete_et_les_lignes_deja_ecrites() {
        assertThatThrownBy(() -> demander(UUID.fromString("00000000-0000-0000-0000-0000000000f1"))
                .executer(null, PATIENT, "dr", null, "BIOLOGIE", false, null, deuxLignes())).isInstanceOf(RuntimeException.class);
        assertThat(compter("demandes_examen")).isZero();
        assertThat(compter("lignes_demande_examen")).isZero();
    }

    @Test
    void une_demande_valide_enregistre_l_en_tete_et_toutes_les_lignes() {
        var demande = demander(null).executer(null, PATIENT, "dr", null, "BIOLOGIE", false, null, deuxLignes());
        assertThat(compter("demandes_examen")).isEqualTo(1);
        assertThat(compter("lignes_demande_examen")).isEqualTo(2);
        assertThat(demande.lignes()).hasSize(2);
    }
}
