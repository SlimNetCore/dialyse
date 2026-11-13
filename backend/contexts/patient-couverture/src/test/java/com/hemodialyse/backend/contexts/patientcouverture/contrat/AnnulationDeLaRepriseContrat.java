package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.AnnulationDeLaReprise;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat de l'annulation d'un lot de reprise (RP1, ADR 0169) : le même jeu de tests passe sur le double en mémoire et sur les
 * tables réelles ({@code patients}, {@code assure}, {@code assure_patient}) : suppressions bornées au centre, assuré retenu hors du lot.
 */
abstract class AnnulationDeLaRepriseContrat {

    protected static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    protected static final UUID AUTRE = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    protected static final UUID P1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    protected static final UUID P2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    protected static final UUID A1 = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    protected static final UUID A2 = UUID.fromString("00000000-0000-0000-0000-0000000000f2");

    protected abstract AnnulationDeLaReprise annulation();

    protected abstract void patient(UUID centre, UUID id, String numeroAssuranceDeLAssure);

    protected abstract void assure(UUID centre, String numero);

    protected abstract void affectation(UUID centre, UUID id, UUID patient, String numero);

    protected abstract void priseEnCharge(UUID id, UUID patient);

    protected abstract void attestation(UUID id, UUID patient);

    protected abstract boolean patientExiste(UUID id);

    protected abstract boolean assureExiste(String numero);

    protected abstract boolean affectationExiste(UUID id);

    protected abstract boolean attestationExiste(UUID id);

    protected abstract boolean priseEnChargeExiste(UUID id);

    /** RP3a (ADR 0171) : l'annulation supprime les attestations et prises en charge du lot, bornées au centre. */
    @Test
    void supprimer_une_attestation_ou_une_prise_en_charge_est_borne_au_centre_et_laisse_les_autres_lignes() {
        UUID pec = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
        UUID autrePec = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
        UUID attestation = UUID.fromString("00000000-0000-0000-0000-0000000000d2");
        UUID autreAttestation = UUID.fromString("00000000-0000-0000-0000-0000000000d3");
        patient(CENTRE, P1, null);
        priseEnCharge(pec, P1);
        priseEnCharge(autrePec, P1);
        attestation(attestation, P1);
        attestation(autreAttestation, P1);

        annulation().supprimerUneAttestation(AUTRE, attestation);
        annulation().supprimerUnePriseEnCharge(AUTRE, pec);
        assertThat(attestationExiste(attestation)).isTrue();
        assertThat(priseEnChargeExiste(pec)).isTrue();

        annulation().supprimerUneAttestation(CENTRE, attestation);
        annulation().supprimerUnePriseEnCharge(CENTRE, pec);
        assertThat(attestationExiste(attestation)).isFalse();
        assertThat(priseEnChargeExiste(pec)).isFalse();
        assertThat(attestationExiste(autreAttestation)).isTrue();
        assertThat(priseEnChargeExiste(autrePec)).isTrue();
        assertThat(patientExiste(P1)).isTrue();
    }

    @Test
    void un_assure_rattache_a_un_patient_qui_n_est_pas_du_lot_est_retenu() {
        assure(CENTRE, "ZA1");
        patient(CENTRE, P1, "ZA1");
        patient(CENTRE, P2, "ZA1");

        assertThat(annulation().assureRetenuHorsDuLot(CENTRE, "ZA1", Set.of(), Set.of(P1.toString()))).isTrue();
        assertThat(annulation().assureRetenuHorsDuLot(CENTRE, "ZA1", Set.of(), Set.of(P1.toString(), P2.toString()))).isFalse();
    }

    @Test
    void un_assure_n_est_pas_retenu_par_un_patient_d_un_autre_centre_ni_par_un_autre_assure() {
        assure(CENTRE, "ZA2");
        patient(AUTRE, P1, "ZA2");
        patient(CENTRE, P2, "ZA-AUTRE");

        assertThat(annulation().assureRetenuHorsDuLot(CENTRE, "ZA2", Set.of(), Set.of())).isFalse();
    }

    @Test
    void un_assure_rattache_par_une_affectation_est_retenu_sauf_si_l_affectation_ou_son_patient_est_du_lot() {
        assure(CENTRE, "ZA3");
        patient(CENTRE, P1, null);
        affectation(CENTRE, A1, P1, "ZA3");

        assertThat(annulation().assureRetenuHorsDuLot(CENTRE, "ZA3", Set.of(), Set.of())).isTrue();
        assertThat(annulation().assureRetenuHorsDuLot(CENTRE, "ZA3", Set.of(A1.toString()), Set.of())).isFalse();
        assertThat(annulation().assureRetenuHorsDuLot(CENTRE, "ZA3", Set.of(), Set.of(P1.toString()))).isFalse();
        assertThat(annulation().assureRetenuHorsDuLot(AUTRE, "ZA3", Set.of(), Set.of())).isFalse();
    }

    @Test
    void supprimer_un_patient_supprime_ses_affectations_et_jamais_celles_ou_la_fiche_d_un_autre_centre() {
        patient(CENTRE, P1, null);
        patient(CENTRE, P2, null);
        affectation(CENTRE, A1, P1, "ZA4");
        affectation(CENTRE, A2, P2, "ZA4");

        annulation().supprimerLePatient(AUTRE, P1);
        assertThat(patientExiste(P1)).isTrue();
        assertThat(affectationExiste(A1)).isTrue();

        annulation().supprimerLePatient(CENTRE, P1);
        assertThat(patientExiste(P1)).isFalse();
        assertThat(affectationExiste(A1)).isFalse();
        assertThat(patientExiste(P2)).isTrue();
        assertThat(affectationExiste(A2)).isTrue();
    }

    @Test
    void supprimer_une_affectation_ou_un_assure_est_borne_au_centre() {
        assure(CENTRE, "ZA5");
        patient(CENTRE, P1, null);
        affectation(CENTRE, A1, P1, "ZA5");

        annulation().supprimerLAffectation(AUTRE, A1);
        annulation().supprimerLAssure(AUTRE, "ZA5");
        assertThat(affectationExiste(A1)).isTrue();
        assertThat(assureExiste("ZA5")).isTrue();

        annulation().supprimerLAffectation(CENTRE, A1);
        annulation().supprimerLAssure(CENTRE, "ZA5");
        assertThat(affectationExiste(A1)).isFalse();
        assertThat(assureExiste("ZA5")).isFalse();
        assertThat(patientExiste(P1)).isTrue();
    }

    @Test
    void les_prises_en_charge_et_les_attestations_d_un_patient_sont_listees() {
        UUID pec = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
        UUID attestation = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
        patient(CENTRE, P1, null);
        priseEnCharge(pec, P1);
        attestation(attestation, P1);

        assertThat(annulation().prisesEnChargeDuPatient(P1)).containsExactly(pec);
        assertThat(annulation().attestationsDuPatient(P1)).containsExactly(attestation);
        assertThat(annulation().prisesEnChargeDuPatient(P2)).isEmpty();
        assertThat(annulation().attestationsDuPatient(P2)).isEmpty();
    }
}
