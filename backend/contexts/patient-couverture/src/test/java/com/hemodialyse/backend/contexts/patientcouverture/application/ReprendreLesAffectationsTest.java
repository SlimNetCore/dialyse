package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.application.AssuresDuCentre.Affectation;
import com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.AssuresEnMemoire;
import com.hemodialyse.backend.contexts.patientcouverture.application.PatientsDeLaReprise.AssureSurLaFiche;
import com.hemodialyse.backend.contexts.patientcouverture.application.PatientsDeLaReprise.Fiche;
import com.hemodialyse.backend.contexts.patientcouverture.application.ReprendreLesPatientsTest.PatientsEnMemoireDeLaReprise;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDesAffectations.AffectationConnue;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDesAffectations.ReportDeLAffectation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.AUTRE_CENTRE;
import static com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.CENTRE;
import static com.hemodialyse.backend.contexts.patientcouverture.application.Doubles.MAINTENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reprise de l'historique des affectations (S6b-3, ADR 0134) : une affectation principale est l'unique principale du patient et
 * l'assuré principal de sa fiche, et la rejouer ne la rétrograde pas (KB-S6a-1).
 */
class ReprendreLesAffectationsTest {

    private static final LocalDate DEBUT = LocalDate.of(2019, 1, 2);

    private PatientsEnMemoireDeLaReprise patients;
    private AssuresEnMemoire assures;
    private ReprendreLesAffectations reprise;
    private UUID patient;

    @BeforeEach
    void preparer() {
        patients = new PatientsEnMemoireDeLaReprise();
        assures = new AssuresEnMemoire();
        reprise = new ReprendreLesAffectations(assures, patients);
        patient = UUID.randomUUID();
        patients.creer(CENTRE, fiche(patient, "ZP111", "ZA900"), MAINTENANT);
    }

    private static Fiche fiche(UUID id, String numero, String assure) {
        return new Fiche(id, "C-1", numero, "BENALI", "Karim", "M", LocalDate.of(1962, 3, 15), DEBUT, false, "PERMANENT", "ENFANT",
                null, null, null, 0, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                Set.of(), assure, AssureSurLaFiche.vide());
    }

    private List<Affectation> historique() {
        return assures.historique(CENTRE, new PatientId(patient));
    }

    @Test
    void une_nouvelle_affectation_principale_retire_le_principal_des_autres_et_devient_l_assure_de_la_fiche() {
        UUID ancienne = assures.ajouterUneAffectationPrincipale(CENTRE, new PatientId(patient), "ZA900", DEBUT);

        UUID nouvelle = reprise.reprendre(CENTRE.valeur(), patient, null, new ReportDeLAffectation("ZA901", true,
                LocalDate.of(2022, 1, 1), null));

        assertThat(historique()).hasSize(2);
        assertThat(assures.trouverAffectation(ancienne).orElseThrow().principal()).isFalse();
        assertThat(assures.trouverAffectation(nouvelle).orElseThrow()).satisfies(a -> {
            assertThat(a.principal()).isTrue();
            assertThat(a.numeroAssurance()).isEqualTo("ZA901");
            assertThat(a.dateDebut()).isEqualTo(LocalDate.of(2022, 1, 1));
            assertThat(a.dateFin()).isNull();
        });
        assertThat(patients.trouver(CENTRE, patient).orElseThrow().assureNumeroAssurance()).isEqualTo("ZA901");
    }

    @Test
    void rejouer_une_affectation_principale_la_laisse_principale_et_ne_change_pas_l_assure_de_la_fiche() {
        UUID id = reprise.reprendre(CENTRE.valeur(), patient, null, new ReportDeLAffectation("ZA901", true, DEBUT, null));

        UUID rejouee = reprise.reprendre(CENTRE.valeur(), patient, id, new ReportDeLAffectation("ZA901", true, DEBUT, null));

        assertThat(rejouee).isEqualTo(id);
        assertThat(historique()).singleElement().satisfies(a -> assertThat(a.principal()).isTrue());
        assertThat(patients.trouver(CENTRE, patient).orElseThrow().assureNumeroAssurance()).isEqualTo("ZA901");
    }

    @Test
    void une_affectation_non_principale_ne_change_ni_le_principal_ni_l_assure_de_la_fiche() {
        UUID principale = assures.ajouterUneAffectationPrincipale(CENTRE, new PatientId(patient), "ZA900", DEBUT);

        UUID close = reprise.reprendre(CENTRE.valeur(), patient, null, new ReportDeLAffectation("ZA902", false,
                LocalDate.of(2021, 1, 1), LocalDate.of(2021, 12, 31)));

        assertThat(assures.trouverAffectation(principale).orElseThrow().principal()).isTrue();
        assertThat(assures.trouverAffectation(close).orElseThrow()).satisfies(a -> {
            assertThat(a.principal()).isFalse();
            assertThat(a.dateFin()).isEqualTo(LocalDate.of(2021, 12, 31));
        });
        assertThat(patients.trouver(CENTRE, patient).orElseThrow().assureNumeroAssurance()).isEqualTo("ZA900");
    }

    @Test
    void une_ligne_existante_est_remplacee_et_garde_sa_date_d_affectation() {
        UUID id = assures.ajouterUneAffectation(CENTRE, new PatientId(patient), "ZA900", false, DEBUT, null);
        var dateAffectation = assures.trouverAffectation(id).orElseThrow().dateAffectation();

        reprise.reprendre(CENTRE.valeur(), patient, id, new ReportDeLAffectation("ZA900", false, DEBUT, LocalDate.of(2020, 1, 1)));

        assertThat(historique()).singleElement().satisfies(a -> {
            assertThat(a.dateFin()).isEqualTo(LocalDate.of(2020, 1, 1));
            assertThat(a.dateAffectation()).isEqualTo(dateAffectation);
        });
    }

    @Test
    void une_ligne_d_un_autre_centre_n_est_jamais_remplacee_une_ligne_est_ajoutee() {
        UUID autre = UUID.randomUUID();
        UUID ligneAutreCentre = assures.ajouterUneAffectation(AUTRE_CENTRE, new PatientId(autre), "ZA900", false, DEBUT, null);

        UUID id = reprise.reprendre(CENTRE.valeur(), patient, ligneAutreCentre, new ReportDeLAffectation("ZA901", false, DEBUT, null));

        assertThat(id).isNotEqualTo(ligneAutreCentre);
        assertThat(assures.trouverAffectation(ligneAutreCentre).orElseThrow().numeroAssurance()).isEqualTo("ZA900");
        assertThat(historique()).singleElement().satisfies(a -> assertThat(a.numeroAssurance()).isEqualTo("ZA901"));
    }

    @Test
    void l_historique_du_patient_est_rendu_pour_le_rapprochement() {
        UUID id = assures.ajouterUneAffectation(CENTRE, new PatientId(patient), "ZA900", false, DEBUT, null);

        assertThat(reprise.historique(CENTRE.valeur(), patient)).containsExactly(new AffectationConnue(id, "ZA900", DEBUT));
        assertThat(reprise.historique(AUTRE_CENTRE.valeur(), patient)).isEmpty();
    }

    @Test
    void une_affectation_principale_d_un_patient_supprime_depuis_est_refusee() {
        patients.lignes.clear();

        assertThatThrownBy(() -> reprise.reprendre(CENTRE.valeur(), patient, null, new ReportDeLAffectation("ZA901", true, DEBUT, null)))
                .isInstanceOf(PatientIntrouvable.class);
    }
}
