package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.documents;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.DocumentRendu;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.RenduDeDocument;
import com.hemodialyse.backend.contexts.pilotagedirection.application.PageDuRapport;
import com.hemodialyse.backend.contexts.pilotagedirection.application.PageDuRapport.Genre;
import com.hemodialyse.backend.contexts.pilotagedirection.application.PageDuRapport.Ligne;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RapportRenduParDocumentsTest {

    private static final PageDuRapport PAGE = new PageDuRapport("Titre", "Société", "contexte", "12/11/2026 10:00", "note",
            List.of(new Ligne("01", "Chiffres", Genre.ENTETE, List.of("A", "B")), new Ligne("01", "", Genre.LIGNE, List.of("1", "2"))));

    private static final class Espion implements RenduDeDocument {
        UUID centre;
        UUID societe;
        String type;
        Map<String, Object> donnees;

        @Override
        public DocumentRendu rendre(UUID centre, String typeDeDocument, Map<String, Object> donnees, String format) {
            this.centre = centre;
            this.type = typeDeDocument;
            this.donnees = donnees;
            return new DocumentRendu(new byte[]{1}, "application/pdf", "rapport.pdf");
        }

        @Override
        public DocumentRendu rendrePourLaSociete(UUID societe, String typeDeDocument, Map<String, Object> donnees, String format) {
            this.societe = societe;
            this.type = typeDeDocument;
            this.donnees = donnees;
            return new DocumentRendu(new byte[]{2}, "application/pdf", "rapport.pdf");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void le_rapport_est_rendu_par_le_type_RAPPORT_DIRECTION_avec_ses_lignes_de_treize_champs() {
        Espion documents = new Espion();
        UUID centre = UUID.randomUUID();

        var impression = new RapportRenduParDocuments(documents).imprimer(UUID.randomUUID(), centre, PAGE);

        assertThat(documents.type).isEqualTo("RAPPORT_DIRECTION");
        assertThat(documents.centre).isEqualTo(centre);
        assertThat(impression.octets()).containsExactly(1);
        assertThat(documents.donnees).containsEntry("RAPPORT_TITRE", "Titre").containsEntry("RAPPORT_NOTE", "note");
        List<Map<String, Object>> lignes = (List<Map<String, Object>>) documents.donnees.get("LIGNES");
        assertThat(lignes).hasSize(2);
        assertThat(lignes.get(0)).containsEntry("GENRE", "ENTETE").containsEntry("TITRE", "Chiffres").containsEntry("C1", "A")
                .containsEntry("C2", "B").containsEntry("C3", "").containsEntry("C13", "");
    }

    @Test
    void une_societe_sans_centre_est_rendue_par_le_gabarit_standard() {
        Espion documents = new Espion();
        UUID societe = UUID.randomUUID();

        var impression = new RapportRenduParDocuments(documents).imprimer(societe, null, PAGE);

        assertThat(documents.societe).isEqualTo(societe);
        assertThat(documents.centre).isNull();
        assertThat(impression.octets()).containsExactly(2);
    }
}
