package com.hemodialyse.backend.contexts.seance.infrastructure.acl.documents;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.DocumentRendu;
import com.hemodialyse.backend.contexts.seance.application.CahierImprime;
import com.hemodialyse.backend.contexts.seance.application.DemandeDeCahier;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.PeriodeDuCahier;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** ACL vers Documents (PL5) : traduction de la demande en paramètres du modèle et du type MIME rendu en format du cahier. */
class CahierRenduParDocumentsTest {

    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    @ParameterizedTest
    @CsvSource({
            "application/pdf, PDF",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet, EXCEL",
            "text/html, HTML",
            "application/octet-stream, PDF"})
    void le_type_mime_rendu_devient_le_format_du_cahier(String typeMime, CahierImprime.Format attendu) {
        assertThat(CahierRenduParDocuments.format(typeMime)).isEqualTo(attendu);
    }

    @Test
    void le_cahier_est_demande_a_documents_sous_le_type_cahier_dialyse_avec_les_parametres_du_modele() {
        Map<String, Object> recu = new HashMap<>();
        String[] type = new String[1];
        var acl = new CahierRenduParDocuments((centre, typeDeDocument, donnees, format) -> {
            recu.putAll(donnees);
            type[0] = typeDeDocument;
            assertThat(centre).isEqualTo(CENTRE);
            assertThat(format).isNull();
            return new DocumentRendu(new byte[]{7}, "application/pdf", "cahier-dialyse.pdf");
        });

        CahierImprime cahier = acl.rendre(new CentreId(CENTRE), new DemandeDeCahier(new PatientId(PATIENT),
                new PeriodeDuCahier(LocalDate.of(2026, 1, 1), null), "Patient : X", "12/11/2026 10:30", "admin", false));

        assertThat(type[0]).isEqualTo("CAHIER_DIALYSE");
        assertThat(cahier.format()).isEqualTo(CahierImprime.Format.PDF);
        assertThat(cahier.octets()).containsExactly(7);
        assertThat(recu).containsEntry("PATIENT_ID", PATIENT.toString()).containsEntry("PATIENT_LIGNE", "Patient : X")
                .containsEntry("EDITE_LE", "12/11/2026 10:30").containsEntry("EDITE_PAR", "admin")
                .containsEntry("VOLET_MEDICAL_VISIBLE", false)
                .containsEntry("DATE_DEBUT", java.sql.Date.valueOf("2026-01-01")).containsEntry("DATE_FIN", java.sql.Date.valueOf("2999-12-31"));
    }
}
