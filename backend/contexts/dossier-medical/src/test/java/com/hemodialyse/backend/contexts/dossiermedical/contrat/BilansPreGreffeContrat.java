package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.AvisRcp;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilansPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DecisionRcp;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutDuBilanPreGreffe;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat du port {@link BilansPreGreffe} : le double en mémoire, H2 et PostgreSQL 16 passent le MÊME jeu de tests. */
abstract class BilansPreGreffeContrat {
    static final CentreId CENTRE_A = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d7a1"));
    static final CentreId CENTRE_B = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000d7b2"));
    static final PatientId PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000d7001"));
    static final PatientId AUTRE_PATIENT = new PatientId(UUID.fromString("00000000-0000-0000-0000-0000000d7002"));
    static final Instant T0 = Instant.parse("2026-11-07T10:00:00Z");
    static final LocalDate JOUR = LocalDate.of(2026, 11, 7);

    protected abstract BilansPreGreffe bilans();

    static BilanPreGreffe bilan(CentreId centre, PatientId patient, Instant creeLe) {
        return BilanPreGreffe.ouvrir(UUID.randomUUID(), patient, centre, creeLe);
    }

    @Test
    void un_bilan_ouvert_est_relu_non_debute_et_vierge() {
        var b = bilan(CENTRE_A, PATIENT, T0);
        bilans().ouvrir(b);
        var relu = bilans().trouver(CENTRE_A, PATIENT).orElseThrow();
        assertThat(relu.id()).isEqualTo(b.id());
        assertThat(relu.statut()).isEqualTo(StatutDuBilanPreGreffe.NON_DEBUTE);
        assertThat(relu.groupeSanguinConfirme()).isNull();
        assertThat(relu.praClasseI()).isNull();
        assertThat(relu.decisions()).isEmpty();
        assertThat(relu.creeLe()).isEqualTo(T0);
    }

    @Test
    void le_bilan_est_cherche_par_patient_et_centre() {
        bilans().ouvrir(bilan(CENTRE_A, PATIENT, T0));
        assertThat(bilans().trouver(CENTRE_B, PATIENT)).isEmpty();
        assertThat(bilans().trouver(CENTRE_A, AUTRE_PATIENT)).isEmpty();
        assertThat(bilans().trouverEnVerrouillant(CENTRE_B, PATIENT)).isEmpty();
        assertThat(bilans().trouverEnVerrouillant(CENTRE_A, PATIENT)).isPresent();
    }

    @Test
    void l_enregistrement_relit_le_statut_les_dates_le_bilan_immunologique_et_les_notes() {
        bilans().ouvrir(bilan(CENTRE_A, PATIENT, T0));
        var b = bilans().trouverEnVerrouillant(CENTRE_A, PATIENT).orElseThrow();
        b.changerLeStatut(StatutDuBilanPreGreffe.BILAN_EN_COURS, JOUR, T0.plusSeconds(1));
        b.mettreAJourLeBilanImmunologique("A+", "A1 B8", new BigDecimal("12.345"), new BigDecimal("80"), T0.plusSeconds(2));
        b.mettreAJourLesNotes("aucune", "favorable", T0.plusSeconds(3));
        bilans().enregistrer(b);
        var relu = bilans().trouver(CENTRE_A, PATIENT).orElseThrow();
        assertThat(relu.statut()).isEqualTo(StatutDuBilanPreGreffe.BILAN_EN_COURS);
        assertThat(relu.dateDebutBilan()).isEqualTo(JOUR);
        assertThat(relu.groupeSanguinConfirme()).isEqualTo("A+");
        assertThat(relu.typageHla()).isEqualTo("A1 B8");
        assertThat(relu.praClasseI()).isEqualByComparingTo("12.35");
        assertThat(relu.praClasseII()).isEqualByComparingTo("80");
        assertThat(relu.contreIndications()).isEqualTo("aucune");
        assertThat(relu.conclusionNephrologue()).isEqualTo("favorable");
        assertThat(relu.modifieLe()).isEqualTo(T0.plusSeconds(3));
        assertThat(relu.creeLe()).isEqualTo(T0);
    }

    @Test
    void les_decisions_sont_conservees_une_seule_fois_et_relues_de_la_plus_recente_reunion_a_la_plus_ancienne() {
        bilans().ouvrir(bilan(CENTRE_A, PATIENT, T0));
        var b = bilans().trouverEnVerrouillant(CENTRE_A, PATIENT).orElseThrow();
        b.ajouterUneDecision(UUID.randomUUID(), JOUR.minusDays(10), AvisRcp.AJOURNE, "premier", null, JOUR, T0);
        b.ajouterUneDecision(UUID.randomUUID(), JOUR, AvisRcp.FAVORABLE, null, JOUR.plusMonths(6), JOUR, T0);
        bilans().enregistrer(b);
        var deuxieme = bilans().trouverEnVerrouillant(CENTRE_A, PATIENT).orElseThrow();
        deuxieme.mettreAJourLesNotes("x", "y", T0.plusSeconds(1));
        bilans().enregistrer(deuxieme);
        var relu = bilans().trouver(CENTRE_A, PATIENT).orElseThrow();
        assertThat(relu.decisions()).extracting(DecisionRcp::avis).containsExactly(AvisRcp.FAVORABLE, AvisRcp.AJOURNE);
        assertThat(relu.decisions().getFirst().prochaineDateRevue()).isEqualTo(JOUR.plusMonths(6));
        assertThat(relu.decisions().get(1).compteRendu()).isEqualTo("premier");
    }

    @Test
    void un_doublon_existant_est_lu_sans_erreur_et_le_plus_ancien_l_emporte() {
        var ancien = bilan(CENTRE_A, PATIENT, T0);
        var recent = bilan(CENTRE_A, PATIENT, T0.plusSeconds(60));
        bilans().ouvrir(recent);
        bilans().ouvrir(ancien);
        assertThat(bilans().trouver(CENTRE_A, PATIENT).orElseThrow().id()).isEqualTo(ancien.id());
        assertThat(bilans().trouverEnVerrouillant(CENTRE_A, PATIENT).orElseThrow().id()).isEqualTo(ancien.id());
    }

    @Test
    void le_verrou_d_ouverture_peut_etre_pris_plusieurs_fois_dans_la_meme_transaction() {
        bilans().verrouillerLOuverture(CENTRE_A, PATIENT);
        bilans().verrouillerLOuverture(CENTRE_A, PATIENT);
    }
}
