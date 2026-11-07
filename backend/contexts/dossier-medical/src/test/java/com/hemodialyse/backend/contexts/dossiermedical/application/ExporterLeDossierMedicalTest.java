package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.contrat.AbordsVasculairesEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.contrat.AllergiesEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.contrat.AnalysesEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.contrat.AntecedentsEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.contrat.ObservationsBiologiquesEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.contrat.OrdonnancesEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.BundleFhir;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Export du dossier médical : garde de patient, bundle d'un patient sans donnée, traduction du sexe, plafond reproduit. */
class ExporterLeDossierMedicalTest {
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID INCONNU = UUID.fromString("00000000-0000-0000-0000-0000000000ff");

    private ExporterLeDossierMedical exporteur(String sexe) {
        IdentitesDesPatients identites = (centre, patient) -> patient.valeur().equals(PATIENT)
                ? Optional.of(new IdentitesDesPatients.Identite("Nom", "Prenom", sexe, LocalDate.of(1970, 5, 17), "ASS-1")) : Optional.empty();
        return new ExporterLeDossierMedical(demande -> new com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId(UUID.randomUUID()), identites,
                new AntecedentsEnMemoire(), new AllergiesEnMemoire(), new AnalysesEnMemoire(), new ObservationsBiologiquesEnMemoire(),
                new OrdonnancesEnMemoire(), new AbordsVasculairesEnMemoire(),
                HorlogeDuCentre.enUtc(Clock.fixed(Instant.parse("2026-11-07T10:00:00Z"), ZoneOffset.UTC)));
    }

    @Test
    void should_refuser_un_patient_inconnu_ou_d_un_autre_centre() {
        assertThatThrownBy(() -> exporteur("M").executer(null, INCONNU)).isInstanceOf(PatientIntrouvable.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_exporter_un_bundle_d_une_seule_ressource_pour_un_patient_sans_donnee() {
        Map<String, Object> bundle = BundleFhir.de(exporteur("F").executer(null, PATIENT));
        assertThat(bundle).containsEntry("resourceType", "Bundle").containsEntry("type", "collection").containsEntry("total", 1)
                .containsEntry("timestamp", "2026-11-07T10:00Z");
        var ressource = (Map<String, Object>) ((Map<String, Object>) ((List<Object>) bundle.get("entry")).getFirst()).get("resource");
        assertThat(ressource).containsEntry("resourceType", "Patient").containsEntry("gender", "female").containsEntry("birthDate", "1970-05-17");
        assertThat(ressource.get("identifier")).isEqualTo(List.of(Map.of("system", "urn:hemodialyse:numero-assurance", "value", "ASS-1")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_traduire_le_sexe_comme_le_legacy() {
        Map<String, String> attendus = new java.util.LinkedHashMap<>();
        attendus.put("M", "male");
        attendus.put("MASCULIN", "male");
        attendus.put("m", "male");
        attendus.put(" f ", "female");
        attendus.put("FEMININ", "female");
        attendus.put("X", "unknown");
        attendus.put("", "unknown");
        attendus.put(null, null);
        attendus.forEach((sexe, genre) -> {
            Map<String, Object> bundle = BundleFhir.de(exporteur(sexe).executer(null, PATIENT));
            var ressource = (Map<String, Object>) ((Map<String, Object>) ((List<Object>) bundle.get("entry")).getFirst()).get("resource");
            assertThat(ressource.get("gender")).as("sexe '%s'", sexe).isEqualTo(genre);
        });
    }

    @Test
    void should_plafonner_a_cinq_cents_les_observations_et_les_ordonnances_comme_le_legacy() {
        assertThat(ExporterLeDossierMedical.PLAFOND_DES_OBSERVATIONS_ET_DES_ORDONNANCES).isEqualTo(500);
    }
}
