package com.hemodialyse.backend.contexts.gmao.infrastructure.acl.documents;

import com.hemodialyse.backend.contexts.gmao.application.intervention.EvenementVu;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.EvenementDeChronologie.Type;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** ACL vers Documents (PL6) : traduction du type MIME rendu en format du bon et texte de la ligne de temps (KB-PL6-6). */
class BonRenduParDocumentsTest {

    private static final UUID AUTEUR = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    private static EvenementVu evenement(Type type, String heure, String detail) {
        return new EvenementVu(type, Instant.parse("2026-03-10T" + heure + ":00Z"), AUTEUR, "Karim", detail);
    }

    @ParameterizedTest
    @CsvSource({
            "application/pdf, PDF",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet, EXCEL",
            "text/html, HTML",
            "application/octet-stream, PDF"})
    void le_type_mime_rendu_devient_le_format_du_bon(String typeMime, String attendu) {
        assertThat(BonRenduParDocuments.format(typeMime)).isEqualTo(attendu);
    }

    @Test
    void une_cloture_rejouee_par_une_rectification_nest_imprimee_qu_une_fois() {
        String texte = BonRenduParDocuments.chronologieTexte(List.of(
                evenement(Type.TERMINEE, "10:00", null),
                evenement(Type.TERMINEE, "10:00", null),
                evenement(Type.RECTIFIEE, "11:00", "Erreur")), ZoneOffset.UTC);

        assertThat(texte.lines().filter(l -> l.contains("Terminée")).count()).isEqualTo(1);
        assertThat(texte).contains("Rectifiée par Karim — motif : Erreur");
    }

    @Test
    void des_clotures_d_heures_differentes_restent_toutes_imprimees() {
        String texte = BonRenduParDocuments.chronologieTexte(List.of(
                evenement(Type.TERMINEE, "10:00", null),
                evenement(Type.TERMINEE, "12:00", null)), ZoneOffset.UTC);

        assertThat(texte.lines().filter(l -> l.contains("Terminée")).count()).isEqualTo(2);
    }

    @Test
    void un_motif_blanc_nest_pas_imprime() {
        String texte = BonRenduParDocuments.chronologieTexte(List.of(
                evenement(Type.RECTIFIEE, "11:00", "   "), evenement(Type.RECTIFIEE, "12:00", null)), ZoneOffset.UTC);

        assertThat(texte).doesNotContain("motif");
    }

    @Test
    void la_ligne_du_fuseau_termine_la_chronologie() {
        String texte = BonRenduParDocuments.chronologieTexte(List.of(), ZoneOffset.UTC);

        assertThat(texte).isEqualTo("Heures exprimées dans le fuseau Z.");
    }
}
