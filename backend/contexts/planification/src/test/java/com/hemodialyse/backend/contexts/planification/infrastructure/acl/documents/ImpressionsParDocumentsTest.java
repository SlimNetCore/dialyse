package com.hemodialyse.backend.contexts.planification.infrastructure.acl.documents;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.DocumentRendu;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.RenduDeDocument;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.RenduRefuse;
import com.hemodialyse.backend.contexts.planification.application.CalendriersDesPropositions;
import com.hemodialyse.backend.contexts.planification.application.CaseImprimableDuCalendrier;
import com.hemodialyse.backend.contexts.planification.application.LigneDuPlanningDePresence;
import com.hemodialyse.backend.contexts.planification.application.PlanningDePresenceImprimable;
import com.hemodialyse.backend.contexts.planification.domain.CalendrierDeLaProposition.CaseDuCalendrier;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.ExecutionId;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.ImpressionDeLaPresence;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
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
        Map<String, Object> donneesRendues;

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
            donneesRendues = donnees;
            if (refus != null) throw refus;
            return new DocumentRendu(new byte[]{1}, type.equals("X") ? "text/html" : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "x");
        }
    }

    @Test
    void la_presence_est_rendue_en_lecture_sans_ecriture_avec_les_sept_dates_et_le_libelle_de_semaine() {
        DocumentsFactice documents = new DocumentsFactice();
        documents.format = "EXCEL";
        ImpressionDeLaPresence.Document document = new PresenceImprimeeParDocuments(documents, PLANNING_VIDE).imprimer(CENTRE, DIMANCHE);
        assertThat(document.format()).isEqualTo("EXCEL");
        assertThat(documents.appels).containsExactly("format PLANNING_PRESENCE_INFIRMIERS", "sans provisionner PLANNING_PRESENCE_INFIRMIERS EXCEL");
        assertThat(documents.donnees).containsKeys("DATE_0", "DATE_6", "SEMAINE_LABEL", "LIGNES");
        assertThat(documents.donnees.get("SEMAINE_LABEL")).isEqualTo("Semaine du 08/11/2026 au 14/11/2026");
    }

    private static final PlanningDePresenceImprimable PLANNING_VIDE = (centre, debut) -> List.of();

    /** DOC5e (ADR 0197) : chaque ligne du planning devient un enregistrement du gabarit, dans les types qu'il déclare. */
    @Test
    void les_lignes_du_planning_sont_traduites_champ_par_champ_dans_les_types_du_gabarit() {
        DocumentsFactice documents = new DocumentsFactice();
        List<LigneDuPlanningDePresence> lignes = List.of(
                new LigneDuPlanningDePresence(DIMANCHE, "Salle 1", "MATIN", "Matin", "Sara Amrani", LigneDuPlanningDePresence.Situation.ABSENT),
                new LigneDuPlanningDePresence(DIMANCHE.plusDays(1), "Salle 2", "SOIR", "Soir", "Zahi", LigneDuPlanningDePresence.Situation.REMPLACANT),
                new LigneDuPlanningDePresence(DIMANCHE.plusDays(2), "Salle 3", "SOIR", "Soir", "Luc Roux", LigneDuPlanningDePresence.Situation.PREVU));
        List<LocalDate> demandes = new ArrayList<>();
        new PresenceImprimeeParDocuments(documents, (centre, debut) -> {
            demandes.add(debut);
            assertThat(centre.valeur()).isEqualTo(CENTRE);
            return lignes;
        }).imprimer(CENTRE, DIMANCHE);
        assertThat(demandes).containsExactly(DIMANCHE);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> champs = (List<Map<String, Object>>) documents.donnees.get("LIGNES");
        assertThat(champs).hasSize(3);
        assertThat(champs.get(0)).containsEntry("DATE_JOUR", java.sql.Date.valueOf(DIMANCHE)).containsEntry("SALLE", "Salle 1")
                .containsEntry("CRENEAU_CODE", "MATIN").containsEntry("CRENEAU", "Matin").containsEntry("INFIRMIER", "Sara Amrani")
                .containsEntry("STATUT", "Absent").hasSize(6);
        assertThat(champs.get(1)).containsEntry("STATUT", "Remplaçant").containsEntry("DATE_JOUR", java.sql.Date.valueOf(DIMANCHE.plusDays(1)));
        assertThat(champs.get(2)).containsEntry("STATUT", "Prévu");
    }

    @Test
    void un_format_non_servi_ne_lit_pas_le_planning() {
        DocumentsFactice documents = new DocumentsFactice();
        documents.format = "CSV";
        new PresenceImprimeeParDocuments(documents, (centre, debut) -> {
            throw new AssertionError("le planning ne doit pas être lu");
        }).imprimer(CENTRE, DIMANCHE);
    }

    @Test
    void un_format_que_le_contexte_ne_sert_pas_n_est_pas_genere() {
        DocumentsFactice documents = new DocumentsFactice();
        documents.format = "CSV";
        ImpressionDeLaPresence.Document document = new PresenceImprimeeParDocuments(documents, PLANNING_VIDE).imprimer(CENTRE, DIMANCHE);
        assertThat(document.contenu()).isEmpty();
        assertThat(document.format()).isEqualTo("CSV");
        assertThat(documents.appels).containsExactly("format PLANNING_PRESENCE_INFIRMIERS");
    }

    @Test
    void un_modele_desactive_refuse_avec_le_code_de_documents() {
        DocumentsFactice documents = new DocumentsFactice();
        documents.refus = new RenduRefuse("DOCUMENT_MODEL_INACTIVE", "désactivé");
        assertThatThrownBy(() -> new PresenceImprimeeParDocuments(documents, PLANNING_VIDE).imprimer(CENTRE, DIMANCHE))
                .isInstanceOfSatisfying(ImpressionDeLaPresence.ImpressionRefusee.class, e -> {
                    assertThat(e.code()).isEqualTo("DOCUMENT_MODEL_INACTIVE");
                    assertThat(e.getMessage()).isEqualTo("désactivé");
                });
    }

    @Test
    void une_generation_impossible_ne_divulgue_pas_la_cause() {
        DocumentsFactice documents = new DocumentsFactice();
        documents.refus = new RenduRefuse("DOCUMENT_GENERATION_FAILED", "Impossible de générer : secret technique");
        assertThatThrownBy(() -> new PresenceImprimeeParDocuments(documents, PLANNING_VIDE).imprimer(CENTRE, DIMANCHE))
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

    /** Calendriers factice : ne sert que les lignes imprimables. */
    static CalendriersDesPropositions calendriers(List<CaseImprimableDuCalendrier> cases) {
        return new CalendriersDesPropositions() {
            @Override
            public void enregistrer(CentreId centre, ExecutionId execution, List<CaseDuCalendrier> cases) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<CaseDuCalendrier> lire(CentreId centre, ExecutionId execution, LocalDate semaineDebut) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<LocalDate> semaines(CentreId centre, ExecutionId execution) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<CaseImprimableDuCalendrier> imprimables(CentreId centre, ExecutionId execution) {
                return cases;
            }

            @Override
            public void purgerOrphelins(CentreId centre) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static List<String> sept(String prefixe, int renseignes) {
        List<String> l = new ArrayList<>();
        for (int i = 0; i < 7; i++) l.add(i < renseignes ? prefixe + i : null);
        return l;
    }

    @SuppressWarnings("unchecked")
    @Test
    void les_lignes_du_calendrier_sont_fournies_au_gabarit_champ_par_champ_dans_les_types_declares() {
        DocumentsFactice documents = new DocumentsFactice();
        var cases = List.of(
                new CaseImprimableDuCalendrier(DIMANCHE, "Salle 1", "Matin", 1, sept("E", 7), sept("C", 7)),
                new CaseImprimableDuCalendrier(DIMANCHE.plusWeeks(1), "Salle é", "Soir", 2, sept("E", 2), sept("C", 2)));
        UUID execution = UUID.randomUUID();
        new CalendrierDeLaPropositionImprimeParDocuments(documents, calendriers(cases)).imprimer(CENTRE, execution, "t", "r");
        assertThat(documents.donneesRendues).containsEntry("RUN_ID", execution.toString()).containsEntry("TITRE_PROPOSITION", "t")
                .containsEntry("RESUME_INDICATEURS", "r");
        List<Map<String, Object>> lignes = (List<Map<String, Object>>) documents.donneesRendues.get("LIGNES");
        assertThat(lignes).hasSize(2);
        assertThat(lignes.get(0)).hasSize(18).containsEntry("SEMAINE_DEBUT", java.sql.Date.valueOf(DIMANCHE))
                .containsEntry("SALLE_NOM", "Salle 1").containsEntry("CRENEAU_LIBELLE", "Matin").containsEntry("CRENEAU_ORDRE", 1)
                .containsEntry("ENTETE_0", "E0").containsEntry("ENTETE_6", "E6").containsEntry("CELL_0", "C0").containsEntry("CELL_6", "C6");
        assertThat(lignes.get(1)).containsEntry("SEMAINE_DEBUT", java.sql.Date.valueOf(DIMANCHE.plusWeeks(1)))
                .containsEntry("SALLE_NOM", "Salle é").containsEntry("CRENEAU_ORDRE", 2).containsEntry("ENTETE_1", "E1")
                .containsEntry("ENTETE_2", null).containsEntry("CELL_2", null).containsEntry("CELL_6", null);
    }

    @Test
    void le_calendrier_est_rendu_par_type_avec_le_titre_et_le_resume_et_un_refus_devient_un_refus_du_contexte() {
        DocumentsFactice documents = new DocumentsFactice();
        var impression = new CalendrierDeLaPropositionImprimeParDocuments(documents, calendriers(List.of()));
        assertThat(impression.imprimer(CENTRE, UUID.randomUUID(), "t", "r").format()).isEqualTo("EXCEL");
        assertThat(documents.appels).containsExactly("rendre PLANNING_OPTIMISE null");
        documents.refus = new RenduRefuse("DOCUMENT_MODEL_INACTIVE", "désactivé");
        assertThatThrownBy(() -> impression.imprimer(CENTRE, UUID.randomUUID(), "t", "r"))
                .isInstanceOf(com.hemodialyse.backend.contexts.planification.publishedlanguage.ImpressionDuCalendrierDeLaProposition.ImpressionRefusee.class);
    }
}
