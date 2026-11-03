package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.HorlogeFixe;
import com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.IdentifiantsSequentiels;
import com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.PatientsEnMemoire;
import com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.PrisesEnChargeEnMemoire;
import com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.SessionFixe;
import com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.UniteImmediate;
import com.hemodialyse.backend.contexts.patientcouverture.domain.EtatDuPatient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Fiches;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Patient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PecIntrouvable;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PriseEnCharge;
import com.hemodialyse.backend.contexts.patientcouverture.domain.RegleDePatientViolee;
import com.hemodialyse.backend.contexts.patientcouverture.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.patientcouverture.domain.StatutPec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.AUJOURDHUI;
import static com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.AUTRE_CENTRE;
import static com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.CENTRE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cycle de la PEC, demande de la fiche et recherche : cas d'usage avec ports en mémoire. */
class PecTest {

    private static final LocalDate D = LocalDate.of(2026, 3, 1);
    private static final LocalDate F = LocalDate.of(2026, 9, 1);

    private SessionFixe session;
    private PatientsEnMemoire patients;
    private PrisesEnChargeEnMemoire pecs;
    private final Set<PatientId> avecAttestationValide = new HashSet<>();
    private final List<LocalDate> joursDemandes = new ArrayList<>();
    private final List<String> verrous = new ArrayList<>();
    private GererLesPec gerer;
    private PatientId vacancier;
    private PatientId nonVacancier;

    @BeforeEach
    void preparer() {
        session = new SessionFixe();
        patients = new PatientsEnMemoire();
        pecs = new PrisesEnChargeEnMemoire();
        avecAttestationValide.clear();
        joursDemandes.clear();
        verrous.clear();
        AttestationsDuPatient attestations = (centre, patient, jour) -> {
            joursDemandes.add(jour);
            return avecAttestationValide.contains(patient);
        };
        gerer = new GererLesPec(session, new HorlogeFixe(), new IdentifiantsSequentiels(), pecs, patients, attestations,
                new UniteImmediate(), centre -> verrous.add("verrou"));
        nonVacancier = patient(Fiches.nominale("PEC-1", null, null));
        vacancier = patient(Fiches.nominale("PEC-2", EtatDuPatient.VACANCIER_LOCAL, LocalDate.of(2026, 12, 1)));
    }

    private PatientId patient(com.hemodialyse.backend.contexts.patientcouverture.domain.Fiche fiche) {
        Patient p = Patient.admettre(new PatientId(UUID.randomUUID()), "PAT-X", CENTRE, fiche, AUJOURDHUI, Doubles.MAINTENANT,
                UUID::randomUUID);
        patients.enregistrer(p);
        return p.id();
    }

    // ---- création

    @Test
    void should_creer_une_pec_pour_un_non_vacancier_avec_attestation_valide_au_jour_du_centre() {
        avecAttestationValide.add(nonVacancier);

        PriseEnCharge pec = gerer.creer(CENTRE.valeur(), nonVacancier.valeur(), D, F, null);

        assertThat(pec.statut()).isEqualTo(StatutPec.CREE);
        assertThat(pecs.lignes).containsKey(pec.id());
        assertThat(joursDemandes).containsExactly(AUJOURDHUI);
        assertThat(verrous).containsExactly("verrou");
    }

    @Test
    void should_refuser_en_422_une_pec_sans_attestation_valide_pour_un_non_vacancier_et_l_accepter_pour_un_vacancier() {
        assertThatThrownBy(() -> gerer.creer(CENTRE.valeur(), nonVacancier.valeur(), D, F, null))
                .isInstanceOf(RegleDePatientViolee.class).extracting("code").isEqualTo("BUSINESS_RULE_VIOLATION");
        assertThat(pecs.lignes).isEmpty();

        assertThat(gerer.creer(CENTRE.valeur(), vacancier.valeur(), null, null, null).statut()).isEqualTo(StatutPec.CREE);
    }

    @Test
    void should_refuser_en_400_une_pec_pour_un_patient_inconnu_et_en_403_dans_un_autre_centre() {
        assertThatThrownBy(() -> gerer.creer(CENTRE.valeur(), UUID.randomUUID(), D, F, null)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> gerer.creer(AUTRE_CENTRE.valeur(), vacancier.valeur(), D, F, null)).isInstanceOf(AccesRefuse.class);
        assertThat(verrous).as("seule la première tentative a pris le verrou (la seconde est refusée avant)").hasSize(1);
    }

    // ---- cycle

    @Test
    void should_valider_puis_cloturer_et_rendre_le_nom_reel_du_patient() {
        PriseEnCharge pec = gerer.creer(CENTRE.valeur(), vacancier.valeur(), D, F, null);

        var validee = gerer.valider(CENTRE.valeur(), pec.id(), D, F, new UUID(7, 7));
        StatutPec statutApresValidation = validee.pec().statut();
        var cloturee = gerer.cloturer(CENTRE.valeur(), pec.id());

        assertThat(statutApresValidation).isEqualTo(StatutPec.VALIDEE);
        assertThat(validee.nomDuPatient()).isEqualTo("Karim Benali");
        assertThat(cloturee.pec().statut()).isEqualTo(StatutPec.CLOTUREE);
        assertThat(pecs.lignes.get(pec.id()).accordForfait()).isEqualTo(new UUID(7, 7));
    }

    @Test
    void should_refuser_les_transitions_interdites_et_rien_changer() {
        PriseEnCharge pec = gerer.creer(CENTRE.valeur(), vacancier.valeur(), D, F, null);

        assertThatThrownBy(() -> gerer.cloturer(CENTRE.valeur(), pec.id())).isInstanceOf(RegleDePatientViolee.class);
        gerer.valider(CENTRE.valeur(), pec.id(), D, F, null);
        assertThatThrownBy(() -> gerer.valider(CENTRE.valeur(), pec.id(), D, F, null)).isInstanceOf(RegleDePatientViolee.class);
        assertThat(pecs.lignes.get(pec.id()).statut()).isEqualTo(StatutPec.VALIDEE);
    }

    @Test
    void should_repondre_introuvable_pour_une_pec_inconnue_ou_d_un_autre_centre() {
        PriseEnCharge pec = gerer.creer(CENTRE.valeur(), vacancier.valeur(), D, F, null);
        session.centreDeLaSession = AUTRE_CENTRE;
        UUID inconnue = UUID.randomUUID();

        assertThatThrownBy(() -> gerer.valider(AUTRE_CENTRE.valeur(), pec.id(), D, F, null)).isInstanceOf(PecIntrouvable.class);
        assertThatThrownBy(() -> gerer.cloturer(AUTRE_CENTRE.valeur(), inconnue)).isInstanceOf(PecIntrouvable.class);
        assertThatThrownBy(() -> gerer.supprimer(AUTRE_CENTRE.valeur(), pec.id())).isInstanceOf(PecIntrouvable.class);
        assertThatThrownBy(() -> gerer.autoriseUneSeance(AUTRE_CENTRE.valeur(), pec.id())).isInstanceOf(PecIntrouvable.class);
        assertThat(pecs.lignes).containsKey(pec.id());
    }

    @Test
    void should_supprimer_une_pec_quel_que_soit_son_statut_reproduit_a_decider_avec_les_metiers() {
        PriseEnCharge pec = gerer.creer(CENTRE.valeur(), vacancier.valeur(), D, F, null);
        gerer.valider(CENTRE.valeur(), pec.id(), D, F, null);

        gerer.supprimer(CENTRE.valeur(), pec.id());

        assertThat(pecs.lignes).isEmpty();
    }

    @Test
    void should_autoriser_une_seance_selon_le_statut_seul_et_l_attestation_du_non_vacancier_reproduit() {
        PriseEnCharge duVacancier = gerer.creer(CENTRE.valeur(), vacancier.valeur(), D, F, null);
        assertThat(gerer.autoriseUneSeance(CENTRE.valeur(), duVacancier.id())).as("créée").isFalse();
        gerer.valider(CENTRE.valeur(), duVacancier.id(), null, null, null);
        assertThat(gerer.autoriseUneSeance(CENTRE.valeur(), duVacancier.id())).as("validée sans période : vrai").isTrue();

        avecAttestationValide.add(nonVacancier);
        PriseEnCharge dun = gerer.creer(CENTRE.valeur(), nonVacancier.valeur(), D, F, null);
        gerer.valider(CENTRE.valeur(), dun.id(), D, F, null);
        assertThat(gerer.autoriseUneSeance(CENTRE.valeur(), dun.id())).isTrue();
        avecAttestationValide.clear();
        assertThat(gerer.autoriseUneSeance(CENTRE.valeur(), dun.id())).as("attestation échue : faux").isFalse();
    }

    @Test
    void should_lister_les_pec_du_centre_et_du_patient_de_la_session_seulement() {
        gerer.creer(CENTRE.valeur(), vacancier.valeur(), D, F, null);
        avecAttestationValide.add(nonVacancier);
        gerer.creer(CENTRE.valeur(), nonVacancier.valeur(), D, F, null);

        assertThat(gerer.duCentre(CENTRE.valeur())).hasSize(2);
        assertThat(gerer.duPatient(CENTRE.valeur(), vacancier.valeur())).hasSize(1);
        assertThatThrownBy(() -> gerer.duCentre(AUTRE_CENTRE.valeur())).isInstanceOf(AccesRefuse.class);
        assertThatThrownBy(() -> gerer.duPatient(AUTRE_CENTRE.valeur(), vacancier.valeur())).isInstanceOf(AccesRefuse.class);
    }

    // ---- demande de la fiche

    private PecDeLaFiche pecDeLaFiche() {
        return new PecDeLaFiche(pecs, new IdentifiantsSequentiels(), new HorlogeFixe());
    }

    private static CouvertureDeLaFiche.DemandeDePec demande(UUID id, LocalDate debut, LocalDate fin, UUID forfait) {
        return new CouvertureDeLaFiche.DemandeDePec(id, debut, fin, forfait);
    }

    @Test
    void should_creer_la_pec_de_la_fiche_ignorer_une_demande_incomplete_et_ne_pas_dupliquer() {
        var fiche = pecDeLaFiche();

        assertThat(fiche.enregistrer(CENTRE, vacancier, demande(null, D, null, null))).isEmpty();
        assertThat(fiche.enregistrer(CENTRE, vacancier, null)).isEmpty();
        assertThat(pecs.lignes).as("demande incomplète ignorée").isEmpty();

        assertThat(fiche.enregistrer(CENTRE, vacancier, demande(null, D, F, null))).isEmpty();
        assertThat(fiche.enregistrer(CENTRE, vacancier, demande(null, D, F, null))).isEmpty();

        assertThat(pecs.lignes).as("même demande : pas de doublon").hasSize(1);
        assertThat(pecs.lignes.values().iterator().next().statut()).isEqualTo(StatutPec.CREE);
    }

    @Test
    void should_ne_jamais_ramener_une_pec_validee_a_creee_et_reviser_seulement_la_demande() {
        var fiche = pecDeLaFiche();
        UUID id = new UUID(8, 8);
        fiche.enregistrer(CENTRE, vacancier, demande(id, D, F, null));
        gerer.valider(CENTRE.valeur(), id, D, F, new UUID(7, 7));

        fiche.enregistrer(CENTRE, vacancier, demande(id, D.plusDays(2), F, null));

        PriseEnCharge pec = pecs.lignes.get(id);
        assertThat(pec.statut()).isEqualTo(StatutPec.VALIDEE);
        assertThat(pec.demandeDebut()).isEqualTo(D.plusDays(2));
        assertThat(pec.accordForfait()).isEqualTo(new UUID(7, 7));
        assertThat(pecs.lignes).hasSize(1);
    }

    @Test
    void should_ajouter_une_pec_quand_les_dates_different_sans_identifiant() {
        var fiche = pecDeLaFiche();
        fiche.enregistrer(CENTRE, vacancier, demande(null, D, F, null));

        fiche.enregistrer(CENTRE, vacancier, demande(null, D.plusDays(5), F, null));

        assertThat(pecs.lignes).hasSize(2);
    }

    @Test
    void should_refuser_avec_un_code_propre_un_identifiant_deja_pris_par_une_autre_pec() {
        var fiche = pecDeLaFiche();
        UUID pris = new UUID(8, 9);
        fiche.enregistrer(CENTRE, vacancier, demande(pris, D, F, null));
        avecAttestationValide.add(nonVacancier);

        Optional<String> refus = fiche.enregistrer(CENTRE, nonVacancier, demande(pris, D, F, null));

        assertThat(refus).contains(PecDeLaFiche.IDENTIFIANT_DEJA_PRIS);
        assertThat(pecs.lignes.get(pris).patient()).as("la PEC existante est intacte").isEqualTo(vacancier);
        assertThat(pecs.lignes).hasSize(1);
    }
}
