package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.AttestationsEnMemoire;
import com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.HorlogeFixe;
import com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.IdentifiantsSequentiels;
import com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.PatientsEnMemoire;
import com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.SessionFixe;
import com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.UniteImmediate;
import com.hemodialyse.backend.contexts.patientcouverture.domain.AttestationDroit;
import com.hemodialyse.backend.contexts.patientcouverture.domain.AttestationIntrouvable;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Fiches;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Patient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.RegleDePatientViolee;
import com.hemodialyse.backend.contexts.patientcouverture.domain.SaisieInvalide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.AUJOURDHUI;
import static com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.AUTRE_CENTRE;
import static com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.CENTRE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Attestations de droits (PC4) : déclaration, suppression, liste, recherche et saisie de la fiche, avec ports en mémoire. */
class AttestationsTest {

    private static final LocalDate D = LocalDate.of(2026, 1, 1);
    private static final LocalDate F = LocalDate.of(2026, 12, 31);

    private SessionFixe session;
    private PatientsEnMemoire patients;
    private AttestationsEnMemoire attestations;
    private final List<String> verrous = new ArrayList<>();
    private GererLesAttestations gerer;
    private PatientId patient;

    @BeforeEach
    void preparer() {
        session = new SessionFixe();
        patients = new PatientsEnMemoire();
        attestations = new AttestationsEnMemoire();
        verrous.clear();
        gerer = new GererLesAttestations(session, new HorlogeFixe(), new IdentifiantsSequentiels(), attestations, patients,
                new UniteImmediate(), centre -> verrous.add("verrou"));
        Patient p = Patient.admettre(new PatientId(UUID.randomUUID()), "PAT-X", CENTRE, Fiches.nominale("ATT-1", null, null),
                AUJOURDHUI, Doubles.MAINTENANT, UUID::randomUUID);
        patients.enregistrer(p);
        patient = p.id();
    }

    // ---- déclaration

    @Test
    void should_declarer_une_attestation_sous_le_verrou_du_centre() {
        AttestationDroit a = gerer.declarer(CENTRE.valeur(), patient.valeur(), D, F);

        assertThat(attestations.lignes).containsKey(a.id());
        assertThat(a.patient()).isEqualTo(patient);
        assertThat(a.centre()).isEqualTo(CENTRE);
        assertThat(a.creeLe()).isEqualTo(Doubles.MAINTENANT);
        assertThat(verrous).containsExactly("verrou");
    }

    @Test
    void should_accepter_sans_aucun_controle_de_periode_les_cas_reproduits_du_legacy() {
        LocalDate hier = AUJOURDHUI.minusDays(1);
        gerer.declarer(CENTRE.valeur(), patient.valeur(), D, D);
        gerer.declarer(CENTRE.valeur(), patient.valeur(), D, D);
        gerer.declarer(CENTRE.valeur(), patient.valeur(), hier.minusDays(50), hier);
        gerer.declarer(CENTRE.valeur(), patient.valeur(), AUJOURDHUI.plusDays(100), AUJOURDHUI.plusDays(200));
        gerer.declarer(CENTRE.valeur(), patient.valeur(), D, F);
        gerer.declarer(CENTRE.valeur(), patient.valeur(), F, F.plusDays(10));

        assertThat(attestations.lignes).hasSize(6);
    }

    @Test
    void should_refuser_en_400_des_dates_absentes_ou_une_fin_avant_le_debut() {
        assertThatThrownBy(() -> gerer.declarer(CENTRE.valeur(), patient.valeur(), null, F))
                .isInstanceOf(SaisieInvalide.class).hasMessage("Dates attestation obligatoires");
        assertThatThrownBy(() -> gerer.declarer(CENTRE.valeur(), patient.valeur(), D, null))
                .isInstanceOf(SaisieInvalide.class).hasMessage("Dates attestation obligatoires");
        assertThatThrownBy(() -> gerer.declarer(CENTRE.valeur(), patient.valeur(), F, D))
                .isInstanceOf(SaisieInvalide.class).hasMessage("La date de début doit être antérieure à la date de fin");
        assertThatThrownBy(() -> gerer.declarer(CENTRE.valeur(), null, D, F))
                .isInstanceOf(SaisieInvalide.class).hasMessage("PatientId cannot be null");
        assertThat(attestations.lignes).isEmpty();
        assertThat(verrous).as("la forme est vérifiée avant de prendre le verrou").isEmpty();
    }

    @Test
    void should_refuser_en_422_une_attestation_pour_un_patient_inexistant_ou_d_un_autre_centre() {
        assertThatThrownBy(() -> gerer.declarer(CENTRE.valeur(), UUID.randomUUID(), D, F))
                .isInstanceOf(RegleDePatientViolee.class).extracting("code").isEqualTo("ATTESTATION_PATIENT_INTROUVABLE");

        session.centreDeLaSession = AUTRE_CENTRE;
        assertThatThrownBy(() -> gerer.declarer(AUTRE_CENTRE.valeur(), patient.valeur(), D, F))
                .isInstanceOf(RegleDePatientViolee.class).extracting("code").isEqualTo("ATTESTATION_PATIENT_INTROUVABLE");
        assertThat(attestations.lignes).isEmpty();
    }

    @Test
    void should_refuser_en_403_un_centre_qui_n_est_pas_celui_de_la_session() {
        assertThatThrownBy(() -> gerer.declarer(AUTRE_CENTRE.valeur(), patient.valeur(), D, F)).isInstanceOf(AccesRefuse.class);
        assertThatThrownBy(() -> gerer.duPatient(AUTRE_CENTRE.valeur(), patient.valeur())).isInstanceOf(AccesRefuse.class);
        assertThatThrownBy(() -> gerer.supprimer(AUTRE_CENTRE.valeur(), UUID.randomUUID(), null)).isInstanceOf(AccesRefuse.class);
        assertThat(verrous).isEmpty();
    }

    // ---- suppression

    @Test
    void should_supprimer_et_rendre_l_attestation_avec_son_vrai_patient() {
        AttestationDroit a = gerer.declarer(CENTRE.valeur(), patient.valeur(), D, F);

        AttestationDroit supprimee = gerer.supprimer(CENTRE.valeur(), a.id(), null);

        assertThat(supprimee.patient()).isEqualTo(patient);
        assertThat(attestations.lignes).isEmpty();
    }

    @Test
    void should_refuser_en_404_une_suppression_inconnue_ou_d_un_autre_patient_et_ne_rien_supprimer() {
        AttestationDroit a = gerer.declarer(CENTRE.valeur(), patient.valeur(), D, F);

        assertThatThrownBy(() -> gerer.supprimer(CENTRE.valeur(), UUID.randomUUID(), null)).isInstanceOf(AttestationIntrouvable.class);
        assertThatThrownBy(() -> gerer.supprimer(CENTRE.valeur(), a.id(), UUID.randomUUID())).isInstanceOf(AttestationIntrouvable.class);
        gerer.supprimer(CENTRE.valeur(), a.id(), patient.valeur());
        assertThatThrownBy(() -> gerer.supprimer(CENTRE.valeur(), a.id(), null)).isInstanceOf(AttestationIntrouvable.class);
    }

    @Test
    void should_ne_pas_supprimer_l_attestation_d_un_autre_centre() {
        AttestationDroit autre = AttestationDroit.declarer(UUID.randomUUID(), patient, AUTRE_CENTRE, D, F, Doubles.MAINTENANT);
        attestations.enregistrer(autre);

        assertThatThrownBy(() -> gerer.supprimer(CENTRE.valeur(), autre.id(), null)).isInstanceOf(AttestationIntrouvable.class);
        assertThat(attestations.lignes).containsKey(autre.id());
    }

    // ---- liste

    @Test
    void should_lister_les_attestations_du_patient_dans_le_centre_et_une_liste_vide_pour_un_patient_inconnu() {
        AttestationDroit a = gerer.declarer(CENTRE.valeur(), patient.valeur(), D, F);

        assertThat(gerer.duPatient(CENTRE.valeur(), patient.valeur())).extracting(AttestationDroit::id).containsExactly(a.id());
        assertThat(gerer.duPatient(CENTRE.valeur(), UUID.randomUUID())).isEmpty();
    }

    // ---- recherche : critères

    @Test
    void should_lire_les_filtres_de_dates_exactes_en_intervalle_et_redresser_un_intervalle_inverse() {
        var c = CriteresDAttestation.de(0, 10, null, null, null, null, "2026-03-05", "2026-06-01..2026-01-01");

        assertThat(c.debut()).isEqualTo(new CriteresDAttestation.Intervalle(LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 5)));
        assertThat(c.fin()).isEqualTo(new CriteresDAttestation.Intervalle(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1)));
        assertThat(CriteresDAttestation.de(0, 10, null, null, null, null, "05/03/2026T10:00", null).debut().de())
                .isEqualTo(LocalDate.of(2026, 3, 5));
        assertThat(CriteresDAttestation.de(0, 10, null, null, null, null, "..2026-01-01", "").debut())
                .isEqualTo(new CriteresDAttestation.Intervalle(null, LocalDate.of(2026, 1, 1)));
        assertThat(CriteresDAttestation.de(0, 10, null, null, null, null, " ", null).debut()).isNull();
    }

    @Test
    void should_refuser_en_400_une_date_illisible_une_page_negative_et_une_taille_hors_bornes() {
        assertThatThrownBy(() -> CriteresDAttestation.de(0, 10, null, null, null, null, "abc", null))
                .isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> CriteresDAttestation.de(0, 10, null, null, null, null, null, "2026-01-01..zz"))
                .isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> CriteresDAttestation.de(-1, 10, null, null, null, null, null, null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> CriteresDAttestation.de(0, 0, null, null, null, null, null, null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> CriteresDAttestation.de(0, 201, null, null, null, null, null, null)).isInstanceOf(SaisieInvalide.class);
        CriteresDAttestation.de(0, 1, null, null, null, null, null, null);
        CriteresDAttestation.de(0, 200, null, null, null, null, null, null);
    }

    // ---- saisie de la fiche

    @Test
    void should_enregistrer_l_attestation_de_la_fiche_sans_jamais_dupliquer_la_meme_periode() {
        var couverture = new CouvertureDeLaFiche(attestations, new IdentifiantsSequentiels(), new HorlogeFixe());
        var saisie = new CouvertureDeLaFiche.AttestationSaisie(null, D, F);

        couverture.enregistrer(CENTRE, patient, saisie);
        couverture.enregistrer(CENTRE, patient, saisie);
        couverture.enregistrer(CENTRE, patient, new CouvertureDeLaFiche.AttestationSaisie(null, D, F.plusDays(1)));

        assertThat(attestations.lignes).hasSize(2);
        assertThat(couverture.attestationEnregistree(CENTRE, patient)).isTrue();
        assertThat(couverture.attestationEnregistree(AUTRE_CENTRE, patient)).isFalse();
    }

    @Test
    void should_ignorer_une_attestation_incomplete_de_la_fiche_et_refuser_une_fin_avant_le_debut_avec_le_message_de_la_fiche() {
        var couverture = new CouvertureDeLaFiche(attestations, new IdentifiantsSequentiels(), new HorlogeFixe());

        couverture.enregistrer(CENTRE, patient, null);
        couverture.enregistrer(CENTRE, patient, new CouvertureDeLaFiche.AttestationSaisie(null, D, null));
        assertThat(attestations.lignes).isEmpty();

        assertThatThrownBy(() -> couverture.enregistrer(CENTRE, patient, new CouvertureDeLaFiche.AttestationSaisie(null, F, D)))
                .isInstanceOf(SaisieInvalide.class).hasMessage("Date fin avant date debut");
    }

    @Test
    void should_imposer_l_identifiant_donne_par_la_fiche() {
        var couverture = new CouvertureDeLaFiche(attestations, new IdentifiantsSequentiels(), new HorlogeFixe());
        UUID impose = UUID.randomUUID();

        couverture.enregistrer(CENTRE, patient, new CouvertureDeLaFiche.AttestationSaisie(impose, D, F));

        assertThat(attestations.lignes).containsKey(impose);
    }
}
