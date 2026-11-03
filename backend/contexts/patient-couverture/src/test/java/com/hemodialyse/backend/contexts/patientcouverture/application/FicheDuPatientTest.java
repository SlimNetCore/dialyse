package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.CouvertureEnMemoire;
import com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.HorlogeFixe;
import com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.IdentifiantsSequentiels;
import com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.MouvementsEnMemoire;
import com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.PatientsEnMemoire;
import com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.PlaceEnregistree;
import com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.SessionFixe;
import com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.UniteImmediate;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Couverture;
import com.hemodialyse.backend.contexts.patientcouverture.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.patientcouverture.domain.EtatDuPatient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Fiches;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.patientcouverture.domain.RegleDePatientViolee;
import com.hemodialyse.backend.contexts.patientcouverture.domain.TypeDeMouvement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.AUTRE_CENTRE;
import static com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.CENTRE;
import static com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.saisie;
import static com.hemodialyse.backend.contexts.patientcouverture.domain.Fiches.ADMISSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cas d'usage de la fiche : admission, modification, consultation, avec les ports en mémoire. */
class FicheDuPatientTest {

    private SessionFixe session;
    private PatientsEnMemoire patients;
    private MouvementsEnMemoire mouvements;
    private PlaceEnregistree place;
    private CouvertureEnMemoire couverture;
    private UniteImmediate unite;
    private AdmettreUnPatient admettre;
    private ModifierLaFiche modifier;
    private ConsulterLaFiche consulter;

    @BeforeEach
    void preparer() {
        session = new SessionFixe();
        patients = new PatientsEnMemoire();
        mouvements = new MouvementsEnMemoire();
        place = new PlaceEnregistree();
        couverture = new CouvertureEnMemoire();
        unite = new UniteImmediate();
        var horloge = new HorlogeFixe();
        var ids = new IdentifiantsSequentiels();
        admettre = new AdmettreUnPatient(session, horloge, ids, place, patients, mouvements, couverture, unite);
        modifier = new ModifierLaFiche(session, horloge, ids, place, patients, mouvements, couverture, unite);
        consulter = new ConsulterLaFiche(session, patients, couverture, (centre, id) -> java.util.Optional.empty());
    }

    private PatientEnregistre admis(String numero) {
        return admettre.executer(saisie(Fiches.nominale(numero, null, null), CENTRE.valeur()));
    }

    // ---- admission

    @Test
    void should_admettre_un_patient_avec_son_mouvement_sa_place_et_sa_couverture_dans_une_unite_de_travail() {
        PatientEnregistre r = admis("PC1-001");

        assertThat(r.code()).startsWith("PAT-");
        assertThat(r.centre()).isEqualTo(CENTRE);
        assertThat(r.vacancier()).isFalse();
        assertThat(patients.lignes).containsKey(r.id());
        assertThat(mouvements.ecrits).singleElement().satisfies(m -> {
            assertThat(m.type()).isEqualTo(TypeDeMouvement.ADMISSION);
            assertThat(m.patient()).isEqualTo(r.id());
            assertThat(m.dateEffet()).isEqualTo(ADMISSION);
        });
        assertThat(place.appels).containsExactly("verifier", "placer");
        assertThat(couverture.appels).containsExactly("enregistrer");
        assertThat(unite.appels).isEqualTo(1);
    }

    @Test
    void should_refuser_l_admission_dans_un_autre_centre_que_celui_de_la_session() {
        assertThatThrownBy(() -> admettre.executer(saisie(Fiches.nominale(), AUTRE_CENTRE.valeur())))
                .isInstanceOf(AccesRefuse.class);
        assertThat(patients.lignes).isEmpty();
        assertThat(place.appels).isEmpty();
    }

    @Test
    void should_refuser_l_admission_d_une_session_sans_centre() {
        session.centreDeLaSession = null;

        assertThatThrownBy(() -> admettre.executer(saisie(Fiches.nominale(), null))).isInstanceOf(AccesRefuse.class);
    }

    @Test
    void should_refuser_un_numero_d_assurance_deja_utilise_quelle_que_soit_la_casse() {
        admis("PC1-DOUBLON");

        assertThatThrownBy(() -> admis(" pc1-doublon "))
                .isInstanceOf(RegleDePatientViolee.class).extracting("code").isEqualTo("BUSINESS_RULE_VIOLATION");
        assertThat(patients.lignes).hasSize(1);
        assertThat(mouvements.ecrits).hasSize(1);
    }

    @Test
    void should_accepter_le_meme_numero_dans_un_autre_centre() {
        admis("PC1-001");
        session.centreDeLaSession = AUTRE_CENTRE;

        assertThat(admettre.executer(saisie(Fiches.nominale("PC1-001", null, null), AUTRE_CENTRE.valeur())).centre())
                .isEqualTo(AUTRE_CENTRE);
    }

    @Test
    void should_exiger_l_attestation_d_un_non_vacancier_sans_rien_ecrire() {
        var sansAttestation = new SaisieDeLaFiche(CENTRE.valeur(), Fiches.nominale(), null, null);

        assertThatThrownBy(() -> admettre.executer(sansAttestation))
                .isInstanceOf(com.hemodialyse.backend.contexts.patientcouverture.domain.SaisieInvalide.class);
        assertThat(patients.lignes).isEmpty();
        assertThat(mouvements.ecrits).isEmpty();
        assertThat(couverture.appels).isEmpty();
    }

    @Test
    void should_admettre_un_vacancier_sans_attestation() {
        var vacancier = new SaisieDeLaFiche(CENTRE.valeur(),
                Fiches.nominale("V-1", EtatDuPatient.VACANCIER_ETRANGER, LocalDate.of(2026, 12, 1)), null, null);

        assertThat(admettre.executer(vacancier).vacancier()).isTrue();
    }

    @Test
    void should_ne_rien_ecrire_quand_la_planification_refuse_la_place() {
        place.refus = new IllegalStateException("PLACEMENT_GENERATEUR_OCCUPE");

        assertThatThrownBy(() -> admis("PC1-001")).hasMessage("PLACEMENT_GENERATEUR_OCCUPE");
        assertThat(patients.lignes).isEmpty();
        assertThat(mouvements.ecrits).isEmpty();
    }

    @Test
    void should_propager_l_ecriture_concurrente_du_verrou_du_centre() {
        place.refus = new EcritureConcurrente();

        assertThatThrownBy(() -> admis("PC1-001")).isInstanceOf(EcritureConcurrente.class);
        assertThat(patients.lignes).isEmpty();
    }

    // ---- modification

    @Test
    void should_modifier_la_fiche_et_tracer_le_changement_d_etat_puis_liberer_la_place_echue() {
        PatientEnregistre r = admis("PC1-001");
        place.appels.clear();
        place.libere = true;
        LocalDate fin = LocalDate.of(2026, 10, 1);

        modifier.executer(r.id().valeur(), saisie(Fiches.nominale("PC1-001", EtatDuPatient.GUERRI, fin), CENTRE.valeur()));

        assertThat(patients.lignes.get(r.id()).etat()).isEqualTo(EtatDuPatient.GUERRI);
        assertThat(mouvements.ecrits).hasSize(2);
        assertThat(mouvements.ecrits.get(1).type()).isEqualTo(TypeDeMouvement.GUERISON);
        assertThat(place.appels).containsExactly("verifier", "placer", "libererSiEchue");
    }

    @Test
    void should_repondre_introuvable_pour_un_patient_inconnu_ou_d_un_autre_centre() {
        PatientEnregistre r = admis("PC1-001");
        session.centreDeLaSession = AUTRE_CENTRE;

        assertThatThrownBy(() -> modifier.executer(r.id().valeur(),
                saisie(Fiches.nominale("PC1-001", null, null), AUTRE_CENTRE.valeur()))).isInstanceOf(PatientIntrouvable.class);
        session.centreDeLaSession = CENTRE;
        assertThatThrownBy(() -> modifier.executer(UUID.randomUUID(), saisie(Fiches.nominale(), CENTRE.valeur())))
                .isInstanceOf(PatientIntrouvable.class);
    }

    @Test
    void should_refuser_a_la_modification_le_numero_d_un_autre_patient() {
        admis("PC1-A");
        PatientEnregistre b = admis("PC1-B");

        assertThatThrownBy(() -> modifier.executer(b.id().valeur(),
                saisie(Fiches.nominale("pc1-a", null, null), CENTRE.valeur())))
                .isInstanceOf(RegleDePatientViolee.class).extracting("code").isEqualTo("BUSINESS_RULE_VIOLATION");
        assertThat(patients.lignes.get(b.id()).fiche().couverture().numeroAssurance().valeur()).isEqualTo("PC1-B");
    }

    @Test
    void should_changer_de_numero_d_assurance_a_la_modification_quand_il_est_libre() {
        PatientEnregistre r = admis("PC1-A");

        modifier.executer(r.id().valeur(), saisie(Fiches.nominale("PC1-NEUF", null, null), CENTRE.valeur()));

        assertThat(patients.lignes.get(r.id()).fiche().couverture().numeroAssurance().valeur()).isEqualTo("PC1-NEUF");
    }

    @Test
    void should_accepter_sans_attestation_saisie_quand_une_attestation_est_deja_enregistree() {
        PatientEnregistre r = admis("PC1-001");

        modifier.executer(r.id().valeur(), new SaisieDeLaFiche(CENTRE.valeur(), Fiches.nominale(), null, null));

        assertThat(patients.lignes.get(r.id()).etat()).isEqualTo(EtatDuPatient.PERMANENT);
    }

    @Test
    void should_refuser_a_la_modification_l_etat_de_sortie_sans_date() {
        PatientEnregistre r = admis("PC1-001");

        assertThatThrownBy(() -> modifier.executer(r.id().valeur(),
                saisie(Fiches.nominale("PC1-001", EtatDuPatient.DECEDE, null), CENTRE.valeur())))
                .isInstanceOf(RegleDePatientViolee.class).extracting("code").isEqualTo("PATIENT_DATE_EVENEMENT_REQUISE");
        assertThat(mouvements.ecrits).hasSize(1);
    }

    // ---- consultation

    @Test
    void should_consulter_la_fiche_du_centre_de_la_session() {
        PatientEnregistre r = admis("PC1-001");

        FicheDuPatient f = consulter.executer(CENTRE.valeur(), r.id().valeur());

        assertThat(f.code()).isEqualTo(r.code());
        assertThat(f.fiche().identite().nom()).isEqualTo("Benali");
    }

    @Test
    void should_refuser_toute_lecture_d_un_autre_centre() {
        PatientEnregistre r = admis("PC1-001");

        assertThatThrownBy(() -> consulter.executer(AUTRE_CENTRE.valeur(), r.id().valeur())).isInstanceOf(AccesRefuse.class);
    }

    @Test
    void should_rendre_l_assure_principal_actuel_pour_un_patient_assure_par_un_tiers() {
        var tiers = new SaisieDeLaFiche(CENTRE.valeur(), Fiches.tiers("PC1-T", "AS-1"),
                new CouvertureDeLaFiche.AttestationSaisie(null, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1)), null);
        PatientEnregistre r = admettre.executer(tiers);
        couverture.principal = java.util.Optional.of(new Couverture.AssureSurLaFiche("M", "Dupont", "Jean", "1950-02-02",
                null, null, null, null, null));

        FicheDuPatient f = consulter.executer(null, r.id().valeur());

        assertThat(f.fiche().couverture().assure().nom()).isEqualTo("Dupont");
    }

    @Test
    void should_repondre_introuvable_a_la_consultation_d_un_patient_inconnu() {
        assertThatThrownBy(() -> consulter.executer(CENTRE.valeur(), UUID.randomUUID())).isInstanceOf(PatientIntrouvable.class);
    }
}
