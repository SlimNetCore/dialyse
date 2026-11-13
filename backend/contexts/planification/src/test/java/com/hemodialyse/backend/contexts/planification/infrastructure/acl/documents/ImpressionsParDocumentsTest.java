package com.hemodialyse.backend.contexts.planification.infrastructure.acl.documents;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.DocumentRendu;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.RenduDeDocument;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.RenduRefuse;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.ImpressionDeLaPresence;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImpressionsParDocumentsTest {

    private static final UUID CENTRE = UUID.randomUUID();
    private static final LocalDate DIMANCHE = LocalDate.of(2026, 11, 8);

    /** Documents factice : enregistre les appels ; refuse avec le code donné si demandé. */
    static class DocumentsFactice implements RenduDeDocument {
        String format = "PDF";
        RenduRefuse refus;
        final List<String> appels = new ArrayList<>();
        Map<String, Object> donnees;

        @Override
        public String formatDImpression(UUID centre, String type) {
            appels.add("format " + type);
            if (refus != null && "DOCUMENT_MODEL_INACTIVE".equals(refus.code())) throw refus;
            return format;
        }

        @Override
        public DocumentRendu rendreSansProvisionner(UUID centre, String type, Map<String, Object> donnees, String format) {
            appels.add("sans provisionner " + type + " " + format);
            this.donnees = donnees;
            if (refus != null) throw refus;
            return new DocumentRendu(new byte[]{1}, "application/pdf", "x.pdf");
        }

        @Override
        public DocumentRendu rendre(UUID centre, String type, Map<String, Object> donnees, String format) {
            appels.add("rendre " + type + " " + format);
            if (refus != null) throw refus;
            return new DocumentRendu(new byte[]{1}, type.equals("X") ? "text/html" : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "x");
        }
    }

    @Test
    void la_presence_est_rendue_en_lecture_sans_ecriture_avec_les_sept_dates_et_le_libelle_de_semaine() {
        DocumentsFactice documents = new DocumentsFactice();
        documents.format = "EXCEL";
        ImpressionDeLaPresence.Document document = new PresenceImprimeeParDocuments(documents).imprimer(CENTRE, DIMANCHE);
        assertThat(document.format()).isEqualTo("EXCEL");
        assertThat(documents.appels).containsExactly("format PLANNING_PRESENCE_INFIRMIERS", "sans provisionner PLANNING_PRESENCE_INFIRMIERS EXCEL");
        assertThat(documents.donnees).containsKeys("DATE_0", "DATE_6", "SEMAINE_LABEL");
        assertThat(documents.donnees.get("SEMAINE_LABEL")).isEqualTo("Semaine du 08/11/2026 au 14/11/2026");
    }

    @Test
    void un_format_que_le_contexte_ne_sert_pas_n_est_pas_genere() {
        DocumentsFactice documents = new DocumentsFactice();
        documents.format = "CSV";
        ImpressionDeLaPresence.Document document = new PresenceImprimeeParDocuments(documents).imprimer(CENTRE, DIMANCHE);
        assertThat(document.contenu()).isEmpty();
        assertThat(document.format()).isEqualTo("CSV");
        assertThat(documents.appels).containsExactly("format PLANNING_PRESENCE_INFIRMIERS");
    }

    @Test
    void un_modele_desactive_refuse_avec_le_code_de_documents() {
        DocumentsFactice documents = new DocumentsFactice();
        documents.refus = new RenduRefuse("DOCUMENT_MODEL_INACTIVE", "désactivé");
        assertThatThrownBy(() -> new PresenceImprimeeParDocuments(documents).imprimer(CENTRE, DIMANCHE))
                .isInstanceOfSatisfying(ImpressionDeLaPresence.ImpressionRefusee.class, e -> {
                    assertThat(e.code()).isEqualTo("DOCUMENT_MODEL_INACTIVE");
                    assertThat(e.getMessage()).isEqualTo("désactivé");
                });
    }

    @Test
    void une_generation_impossible_ne_divulgue_pas_la_cause() {
        DocumentsFactice documents = new DocumentsFactice();
        documents.refus = new RenduRefuse("DOCUMENT_GENERATION_FAILED", "Impossible de générer : secret technique");
        assertThatThrownBy(() -> new PresenceImprimeeParDocuments(documents).imprimer(CENTRE, DIMANCHE))
                .isInstanceOfSatisfying(ImpressionDeLaPresence.ImpressionRefusee.class, e -> {
                    assertThat(e.code()).isEqualTo("DOCUMENT_GENERATION_FAILED");
                    assertThat(e.getMessage()).doesNotContain("secret").endsWith("voir le journal du serveur");
                });
    }

    @Test
    void le_type_mime_rendu_est_traduit_en_format_du_contexte() {
        assertThat(CalendrierDeLaPropositionImprimeParDocuments.format("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .isEqualTo("EXCEL");
        assertThat(CalendrierDeLaPropositionImprimeParDocuments.format("text/html")).isEqualTo("HTML");
        assertThat(CalendrierDeLaPropositionImprimeParDocuments.format("application/pdf")).isEqualTo("PDF");
        assertThat(CalendrierDeLaPropositionImprimeParDocuments.format(null)).isEqualTo("PDF");
    }

    @Test
    void le_calendrier_est_rendu_par_type_avec_le_titre_et_le_resume_et_un_refus_devient_un_refus_du_contexte() {
        DocumentsFactice documents = new DocumentsFactice();
        var impression = new CalendrierDeLaPropositionImprimeParDocuments(documents);
        assertThat(impression.imprimer(CENTRE, UUID.randomUUID(), "t", "r").format()).isEqualTo("EXCEL");
        assertThat(documents.appels).containsExactly("rendre PLANNING_OPTIMISE null");
        documents.refus = new RenduRefuse("DOCUMENT_MODEL_INACTIVE", "désactivé");
        assertThatThrownBy(() -> impression.imprimer(CENTRE, UUID.randomUUID(), "t", "r"))
                .isInstanceOf(com.hemodialyse.backend.contexts.planification.publishedlanguage.ImpressionDuCalendrierDeLaProposition.ImpressionRefusee.class);
    }
}
