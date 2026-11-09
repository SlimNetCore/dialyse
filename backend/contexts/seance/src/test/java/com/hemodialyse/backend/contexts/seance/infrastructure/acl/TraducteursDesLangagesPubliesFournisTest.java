package com.hemodialyse.backend.contexts.seance.infrastructure.acl;

import com.hemodialyse.backend.contexts.seance.application.FicheDuPatient;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyseance.DiffusionsParLeLegacy;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyseance.PatientsParLeLegacy;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.DiffusionsFournies;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.FichesFournies;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Traducteurs des langages publiés encore fournis par la plateforme vers les ports de la Séance (ADR 0088 ; depuis SE7f, ADR 0100 : fiches
 * des patients et diffusions seulement, le planning, l'historique, les forfaits, les générateurs et les volets étant lus par la Séance).
 */
class TraducteursDesLangagesPubliesFournisTest {

    private static final UUID CENTRE = new UUID(0, 0xA);
    private static final UUID PATIENT = new UUID(1, 1);
    private static final UUID SEANCE = new UUID(2, 2);
    private static final LocalDate JOUR = LocalDate.of(2026, 6, 15);

    @Test
    void la_fiche_du_patient_et_ses_references_sont_traduites() {
        UUID generateur = new UUID(3, 3);
        var fiche = new FichesFournies.Fiche(PATIENT, "P-1", "Nom", "Prenom", "F", JOUR, "123", "A+", "0550", new UUID(4, 4), new UUID(5, 5),
                new UUID(6, 6), generateur, "G1", "Fresenius", "ACTIF");
        FichesFournies fourni = Mockito.mock(FichesFournies.class);
        Mockito.when(fourni.fiche(CENTRE, PATIENT)).thenReturn(Optional.of(fiche));
        Mockito.when(fourni.parCode(CENTRE, "P-1")).thenReturn(Optional.of(PATIENT));
        Mockito.when(fourni.tous(CENTRE)).thenReturn(List.of(new FichesFournies.References(PATIENT, "P-1", "123", "999")));
        var patients = new PatientsParLeLegacy(fourni);

        var traduite = patients.fiche(new CentreId(CENTRE), new PatientId(PATIENT)).orElseThrow();
        assertThat(traduite.id()).isEqualTo(new PatientId(PATIENT));
        assertThat(traduite.numeroDAssurance()).isEqualTo("123");
        assertThat(traduite.telephoneMobile()).isEqualTo("0550");
        assertThat(traduite.generateur()).isEqualTo(generateur);
        assertThat(traduite.generateurMarque()).isEqualTo("Fresenius");
        assertThat(patients.parCode(new CentreId(CENTRE), "P-1")).contains(new PatientId(PATIENT));
        assertThat(patients.parNumeroDAssurance(new CentreId(CENTRE), "000")).isEmpty();
        var references = patients.tous(new CentreId(CENTRE));
        assertThat(references).singleElement().satisfies(r -> {
            assertThat(r.patient()).isEqualTo(new PatientId(PATIENT));
            assertThat(r.numeroDAssuranceDeLAssure()).isEqualTo("999");
        });
    }

    @Test
    void les_diffusions_portent_l_identite_du_patient_et_le_jour_en_texte_iso() {
        DiffusionsFournies fourni = Mockito.mock(DiffusionsFournies.class);
        var diffusions = new DiffusionsParLeLegacy(fourni);
        var seance = Seance.creer(new SeanceId(SEANCE), new CentreId(CENTRE), new PatientId(PATIENT), JOUR, Instant.parse("2026-06-15T08:00:00Z"));
        var patient = new FicheDuPatient(new PatientId(PATIENT), "P-1", "Nom", "Prenom", null, null, null, null, null, null, null, null,
                null, null, null, null);

        diffusions.seanceCreee(new CentreId(CENTRE), seance, patient);
        diffusions.seanceValidee(new CentreId(CENTRE), seance, patient);
        diffusions.saisieDeLInfirmier(new CentreId(CENTRE), "SEANCE_CREEE", seance, patient, "marie");
        diffusions.voletParamedicalEnregistre(new CentreId(CENTRE), seance, patient);

        Mockito.verify(fourni).seanceCreee(CENTRE, SEANCE, PATIENT, "Nom", "Prenom", "2026-06-15");
        Mockito.verify(fourni).seanceValidee(CENTRE, SEANCE, PATIENT, "Nom", "Prenom", "2026-06-15");
        Mockito.verify(fourni).saisieDeLInfirmier(CENTRE, "SEANCE_CREEE", PATIENT, "Nom", "Prenom", "marie", "2026-06-15");
        Mockito.verify(fourni).voletParamedicalEnregistre(CENTRE, SEANCE, PATIENT, "Nom", "Prenom", "2026-06-15");
    }

    @Test
    void la_diffusion_des_consommables_ne_porte_que_le_centre_et_la_seance() {
        DiffusionsFournies fourni = Mockito.mock(DiffusionsFournies.class);
        var seance = Seance.creer(new SeanceId(SEANCE), new CentreId(CENTRE), new PatientId(PATIENT), JOUR, Instant.parse("2026-06-15T08:00:00Z"));

        new DiffusionsParLeLegacy(fourni).consommablesModifies(new CentreId(CENTRE), seance);

        Mockito.verify(fourni).consommablesModifies(CENTRE, SEANCE);
    }
}
