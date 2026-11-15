package com.hemodialyse.backend.contexts.seance.infrastructure.acl.patientcouverture;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.FichesDesPatients;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.PlacesDesPatients;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.FichesFournies;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ACL de la Séance (ADR 0133, S5) : tout est lu dans des langages publiés. Recherches et références : Patient et couverture ; fiche :
 * la carte et le médecin traitant de Patient et couverture, la place (salle, créneau, générateur) de la Planification.
 */
class FichesFourniesParPatientCouvertureTest {

    private static final UUID CENTRE = UUID.fromString("c1000000-0000-0000-0000-0000000000a1");
    private static final UUID PATIENT = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final UUID MEDECIN = UUID.fromString("e1000000-0000-0000-0000-000000000001");
    private static final UUID SALLE = UUID.fromString("51000000-0000-0000-0000-000000000001");
    private static final UUID CRENEAU = UUID.fromString("b1000000-0000-0000-0000-000000000001");
    private static final UUID GENERATEUR = UUID.fromString("91000000-0000-0000-0000-000000000001");

    private static final FichesDesPatients.CarteDuPatient CARTE = new FichesDesPatients.CarteDuPatient("PAT-1", "Benali", "Karim", "M",
            LocalDate.of(1970, 5, 17), "O+", "N-1", "0555");

    private final FichesDesPatients publiees = mock(FichesDesPatients.class);
    private final PlacesDesPatients places = mock(PlacesDesPatients.class);
    private final FichesFournies acl = new FichesFourniesParPatientCouverture(publiees, places);

    @Test
    void should_composer_la_fiche_de_la_carte_du_medecin_traitant_et_de_la_place_publiee() {
        when(publiees.carte(CENTRE, PATIENT)).thenReturn(Optional.of(CARTE));
        when(publiees.medecinTraitant(CENTRE, PATIENT)).thenReturn(Optional.of(MEDECIN));
        when(places.deLaFiche(CENTRE, PATIENT)).thenReturn(Optional.of(
                new PlacesDesPatients.PlaceDuPatient(PATIENT, SALLE, CRENEAU, GENERATEUR, Set.of())));

        var fiche = acl.fiche(CENTRE, PATIENT).orElseThrow();

        assertThat(fiche).isEqualTo(new FichesFournies.Fiche(PATIENT, "PAT-1", "Benali", "Karim", "M", LocalDate.of(1970, 5, 17), "N-1",
                "O+", "0555", MEDECIN, SALLE, CRENEAU, GENERATEUR, null, null, null));
    }

    @Test
    void should_laisser_le_nom_la_marque_et_l_etat_du_generateur_a_la_seance_qui_les_precise_par_la_gmao() {
        when(publiees.carte(CENTRE, PATIENT)).thenReturn(Optional.of(CARTE));
        when(places.deLaFiche(CENTRE, PATIENT)).thenReturn(Optional.of(
                new PlacesDesPatients.PlaceDuPatient(PATIENT, SALLE, CRENEAU, GENERATEUR, Set.of())));

        var fiche = acl.fiche(CENTRE, PATIENT).orElseThrow();

        assertThat(fiche.generateur()).isEqualTo(GENERATEUR);
        assertThat(fiche.generateurNom()).isNull();
        assertThat(fiche.generateurMarque()).isNull();
        assertThat(fiche.generateurEtat()).isNull();
    }

    @Test
    void should_rendre_une_fiche_sans_medecin_ni_place_quand_le_patient_n_en_a_pas() {
        when(publiees.carte(CENTRE, PATIENT)).thenReturn(Optional.of(CARTE));
        when(publiees.medecinTraitant(CENTRE, PATIENT)).thenReturn(Optional.empty());
        when(places.deLaFiche(CENTRE, PATIENT)).thenReturn(Optional.empty());

        var fiche = acl.fiche(CENTRE, PATIENT).orElseThrow();

        assertThat(fiche.medecinTraitant()).isNull();
        assertThat(fiche.salle()).isNull();
        assertThat(fiche.position()).isNull();
        assertThat(fiche.generateur()).isNull();
    }

    @Test
    void should_rendre_une_place_partielle_telle_que_lue() {
        when(publiees.carte(CENTRE, PATIENT)).thenReturn(Optional.of(CARTE));
        when(places.deLaFiche(CENTRE, PATIENT)).thenReturn(Optional.of(
                new PlacesDesPatients.PlaceDuPatient(PATIENT, SALLE, null, null, Set.of())));

        var fiche = acl.fiche(CENTRE, PATIENT).orElseThrow();

        assertThat(fiche.salle()).isEqualTo(SALLE);
        assertThat(fiche.position()).isNull();
        assertThat(fiche.generateur()).isNull();
    }

    @Test
    void should_ne_rien_rendre_ni_lire_la_place_pour_un_patient_inconnu_du_centre() {
        when(publiees.carte(CENTRE, PATIENT)).thenReturn(Optional.empty());

        assertThat(acl.fiche(CENTRE, PATIENT)).isEmpty();

        verifyNoInteractions(places);
    }

    @Test
    void should_retrouver_un_patient_par_identifiant_dans_le_langage_publie() {
        when(publiees.existe(CENTRE, PATIENT)).thenReturn(true);

        assertThat(acl.parIdentifiant(CENTRE, PATIENT)).contains(PATIENT);
        when(publiees.existe(CENTRE, PATIENT)).thenReturn(false);
        assertThat(acl.parIdentifiant(CENTRE, PATIENT)).isEmpty();
        verifyNoInteractions(places);
    }

    @Test
    void should_retrouver_un_patient_par_code_dans_le_langage_publie() {
        when(publiees.parCode(CENTRE, "PAT-0001")).thenReturn(Optional.of(PATIENT));

        assertThat(acl.parCode(CENTRE, "PAT-0001")).contains(PATIENT);
        assertThat(acl.parCode(CENTRE, "PAT-0002")).isEmpty();
    }

    @Test
    void should_retrouver_un_patient_par_numero_d_assurance_dans_le_langage_publie() {
        when(publiees.parNumeroDAssurance(CENTRE, "AS-1")).thenReturn(Optional.of(PATIENT));

        assertThat(acl.parNumeroDAssurance(CENTRE, "AS-1")).contains(PATIENT);
        assertThat(acl.parNumeroDAssurance(CENTRE, "AS-2")).isEmpty();
    }

    @Test
    void should_traduire_les_references_de_tous_les_patients_du_centre_dans_l_ordre_publie() {
        UUID autre = UUID.fromString("a2000000-0000-0000-0000-000000000002");
        when(publiees.references(CENTRE)).thenReturn(List.of(
                new FichesDesPatients.ReferencesDuPatient(PATIENT, "PAT-1", "N-1", "AS-1"),
                new FichesDesPatients.ReferencesDuPatient(autre, null, "N-2", null)));

        assertThat(acl.tous(CENTRE)).containsExactly(
                new FichesFournies.References(PATIENT, "PAT-1", "N-1", "AS-1"),
                new FichesFournies.References(autre, null, "N-2", null));
    }
}
