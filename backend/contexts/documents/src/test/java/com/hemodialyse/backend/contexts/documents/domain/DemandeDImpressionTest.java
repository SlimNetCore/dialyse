package com.hemodialyse.backend.contexts.documents.domain;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Les regles de la demande d'impression, reprises de DocumentRestController.validatePrintRequest ; les identifiants illisibles sont refuses (KB-DOC1-5 c). */
class DemandeDImpressionTest {

    private static final UUID CENTRE = new UUID(0L, 1L);
    private static final UUID ID = new UUID(0L, 2L);

    private static Map<String, String> params(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    private static void refuse(UUID centre, String type, Map<String, String> params, String message) {
        assertThatThrownBy(() -> DemandeDImpression.parType(centre, type, null, params)).isInstanceOf(DemandeInvalide.class).hasMessage(message);
    }

    @Test
    void should_require_a_centre_and_a_type() {
        refuse(null, "LISTE_PATIENTS", null, "centerId est obligatoire");
        refuse(CENTRE, null, null, "typeDocument est obligatoire");
        refuse(CENTRE, "   ", null, "typeDocument est obligatoire");
    }

    @Test
    void should_normalise_the_type_by_trimming_and_upper_casing_it() {
        var demande = DemandeDImpression.parType(CENTRE, " liste_patients ", null, null);

        assertThat(demande.type()).isEqualTo("LISTE_PATIENTS");
        assertThat(demande.parametres()).isEmpty();
    }

    @Test
    void should_trim_the_parameter_values_and_keep_null_values() {
        Map<String, String> brut = params("patientId", "  " + ID + " ");
        brut.put("vide", null);

        var demande = DemandeDImpression.parType(CENTRE, "FICHE_PATIENT", "pdf", brut);

        assertThat(demande.parametres()).containsEntry("patientId", ID.toString()).containsEntry("vide", null);
        assertThat(demande.formatDemande()).isEqualTo("pdf");
    }

    @Test
    void should_require_the_identifier_of_each_document_that_prints_one() {
        refuse(CENTRE, "FICHE_PATIENT", null, "patientId est obligatoire pour FICHE_PATIENT");
        refuse(CENTRE, "FICHE_PATIENT", params("patientId", "  "), "patientId est obligatoire pour FICHE_PATIENT");
        refuse(CENTRE, "ATTESTATION", null, "patientId ou attestationId est obligatoire pour ATTESTATION");
        refuse(CENTRE, "PEC", null, "patientId ou pecId est obligatoire pour PEC");
        refuse(CENTRE, "ORDONNANCE", null, "ordonnanceId est obligatoire pour ORDONNANCE");
        refuse(CENTRE, "SYNTHESE_FACTURATION_MENSUELLE", params("PERIOD_START", "2026-01-01"),
                "PERIOD_START et PERIOD_END sont obligatoires pour SYNTHESE_FACTURATION_MENSUELLE");
    }

    @Test
    void should_accept_either_identifier_where_two_are_allowed() {
        assertThat(DemandeDImpression.parType(CENTRE, "ATTESTATION", null, params("attestationId", ID.toString()))).isNotNull();
        assertThat(DemandeDImpression.parType(CENTRE, "PEC", null, params("pecId", ID.toString()))).isNotNull();
        assertThat(DemandeDImpression.parType(CENTRE, "ORDONNANCE", null, params("ordonnanceId", ID.toString()))).isNotNull();
    }

    @Test
    void should_refuse_an_identifier_that_is_not_a_uuid_instead_of_failing_in_the_engine() {
        refuse(CENTRE, "FICHE_PATIENT", params("patientId", "pas-un-uuid"), "patientId illisible : un identifiant est attendu");
        refuse(CENTRE, "ATTESTATION", params("attestationId", "x"), "attestationId illisible : un identifiant est attendu");
        refuse(CENTRE, "PEC", params("pecId", "x"), "pecId illisible : un identifiant est attendu");
        refuse(CENTRE, "ORDONNANCE", params("ordonnanceId", "x"), "ordonnanceId illisible : un identifiant est attendu");
    }

    @Test
    void should_not_check_the_identifiers_of_documents_that_do_not_use_them() {
        assertThat(DemandeDImpression.parType(CENTRE, "LISTE_PATIENTS", null, params("patientId", "pas-un-uuid"))).isNotNull();
    }

    @Test
    void should_only_require_the_centre_when_printing_by_model_and_ignore_the_type() {
        assertThatThrownBy(() -> DemandeDImpression.parModele(null, null, null)).isInstanceOf(DemandeInvalide.class)
                .hasMessage("centerId est obligatoire");

        var demande = DemandeDImpression.parModele(CENTRE, "EXCEL", params("patientId", "n'importe quoi"));

        assertThat(demande.type()).isNull();
        assertThat(demande.formatDemande()).isEqualTo("EXCEL");
    }
}
