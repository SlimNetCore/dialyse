package com.hemodialyse.backend.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.FichesDesPatients;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.FichesFournies;
import com.hemodialyse.backend.domain.patient.model.Patient;
import com.hemodialyse.backend.domain.patient.port.PatientRepositoryPort;
import com.hemodialyse.backend.domain.patient.vo.NumeroAssurance;
import com.hemodialyse.backend.domain.patient.vo.PatientId;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ACL de la Séance (ADR 0133, S5a) : les recherches et références sont lues dans le langage publié du contexte
 * patient-couverture ; seule la fiche (S5b) reste lue dans les patients historiques.
 */
class FichesFournieLegacyTest {

    private static final UUID CENTRE = UUID.fromString("c1000000-0000-0000-0000-0000000000a1");
    private static final UUID PATIENT = UUID.fromString("a1000000-0000-0000-0000-000000000001");

    private final PatientRepositoryPort historiques = mock(PatientRepositoryPort.class);
    private final FichesDesPatients publiees = mock(FichesDesPatients.class);
    private final FichesFournies acl = new FichesFournieLegacy(historiques, publiees);

    @Test
    void should_retrouver_un_patient_par_identifiant_dans_le_langage_publie() {
        when(publiees.existe(CENTRE, PATIENT)).thenReturn(true);

        assertThat(acl.parIdentifiant(CENTRE, PATIENT)).contains(PATIENT);

        verifyNoInteractions(historiques);
    }

    @Test
    void should_ne_rien_rendre_pour_un_identifiant_inconnu_du_centre() {
        when(publiees.existe(CENTRE, PATIENT)).thenReturn(false);

        assertThat(acl.parIdentifiant(CENTRE, PATIENT)).isEmpty();
        verifyNoInteractions(historiques);
    }

    @Test
    void should_retrouver_un_patient_par_code_dans_le_langage_publie() {
        when(publiees.parCode(CENTRE, "PAT-0001")).thenReturn(Optional.of(PATIENT));

        assertThat(acl.parCode(CENTRE, "PAT-0001")).contains(PATIENT);
        assertThat(acl.parCode(CENTRE, "PAT-0002")).isEmpty();

        verifyNoInteractions(historiques);
    }

    @Test
    void should_retrouver_un_patient_par_numero_d_assurance_dans_le_langage_publie() {
        when(publiees.parNumeroDAssurance(CENTRE, "AS-1")).thenReturn(Optional.of(PATIENT));

        assertThat(acl.parNumeroDAssurance(CENTRE, "AS-1")).contains(PATIENT);
        assertThat(acl.parNumeroDAssurance(CENTRE, "AS-2")).isEmpty();

        verifyNoInteractions(historiques);
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

        verifyNoInteractions(historiques);
    }

    @Test
    void should_garder_la_fiche_dans_les_patients_historiques_en_attendant_S5b() {
        Patient p = new Patient();
        p.setId(PatientId.of(PATIENT));
        p.setCenterId(CenterId.of(CENTRE));
        p.setCodePatient("PAT-1");
        p.setNumeroAssurance(new NumeroAssurance("N-1"));
        when(historiques.findById(any(), any())).thenReturn(Optional.of(p));

        var fiche = acl.fiche(CENTRE, PATIENT).orElseThrow();

        assertThat(fiche.id()).isEqualTo(PATIENT);
        assertThat(fiche.code()).isEqualTo("PAT-1");
        assertThat(fiche.numeroDAssurance()).isEqualTo("N-1");
        verify(historiques).findById(PatientId.of(PATIENT), CenterId.of(CENTRE));
        verifyNoInteractions(publiees);
    }
}
