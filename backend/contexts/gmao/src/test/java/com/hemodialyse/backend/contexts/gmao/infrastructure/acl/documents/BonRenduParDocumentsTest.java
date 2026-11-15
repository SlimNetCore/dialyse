package com.hemodialyse.backend.contexts.gmao.infrastructure.acl.documents;

import com.hemodialyse.backend.contexts.gmao.application.intervention.EvenementVu;
import com.hemodialyse.backend.contexts.gmao.application.intervention.InterventionVue;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.EvenementDeChronologie.Type;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.PrioriteIntervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.StatutIntervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.TypeIntervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.TypeLigneCout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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

    private static InterventionVue.LigneVue ligne(TypeLigneCout type, String libelle, String quantite, String prix) {
        BigDecimal q = new BigDecimal(quantite);
        BigDecimal p = new BigDecimal(prix);
        return new InterventionVue.LigneVue(UUID.randomUUID(), type, libelle, q, p, q.multiply(p), null, false);
    }

    private static InterventionVue bon(List<InterventionVue.LigneVue> lignes) {
        Instant debut = Instant.parse("2026-03-10T08:30:00Z");
        return new InterventionVue(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), TypeIntervention.CURATIVE,
                StatutIntervention.TERMINEE, debut, debut.plusSeconds(3600), null, StatutEquipement.HORS_SERVICE,
                StatutEquipement.EN_SERVICE, PrioriteIntervention.NORMALE, null, null, null, false, false, 0, "Panne", null, null,
                lignes, BigDecimal.ZERO, null, debut, null, null, null, List.of());
    }

    @Test
    void les_lignes_du_bon_sont_traduites_champ_par_champ_dans_l_ordre_croissant_des_libelles() {
        List<Map<String, Object>> lignes = BonRenduParDocuments.lignesDuBon(bon(List.of(
                ligne(TypeLigneCout.INTERVENANT, "Zéro", "1", "5000.00"),
                ligne(TypeLigneCout.PIECE, "alpha", "2", "10.50"),
                ligne(TypeLigneCout.MAIN_OEUVRE, "Alpha", "2.5", "1000.00"),
                ligne(TypeLigneCout.AUTRE, "Joint 10", "0.0001", "10.00"))));

        assertThat(lignes).extracting(l -> l.get("LIGNE_LIBELLE")).containsExactly("Alpha", "Joint 10", "Zéro", "alpha");
        assertThat(lignes).extracting(l -> l.get("LIGNE_TYPE"))
                .containsExactly("Main d'œuvre", "Autre", "Honoraires intervenant", "Pièce");
        assertThat(lignes.get(0).get("LIGNE_QUANTITE")).isEqualTo(new BigDecimal("2.5"));
        assertThat(lignes.get(0).get("LIGNE_PRIX")).isEqualTo(new BigDecimal("1000.00"));
        assertThat(lignes.get(0).get("LIGNE_MONTANT")).isEqualTo(new BigDecimal("2500.000"));
    }

    @Test
    void a_libelle_egal_les_lignes_gardent_l_ordre_de_l_intervention() {
        List<Map<String, Object>> lignes = BonRenduParDocuments.lignesDuBon(bon(List.of(
                ligne(TypeLigneCout.PIECE, "Joint", "1", "3.00"),
                ligne(TypeLigneCout.PIECE, "Joint", "1", "1.00"))));

        assertThat(lignes).extracting(l -> l.get("LIGNE_PRIX")).containsExactly(new BigDecimal("3.00"), new BigDecimal("1.00"));
    }

    @Test
    void un_bon_sans_ligne_fournit_un_enregistrement_vide_pour_que_sa_fiche_s_imprime() {
        List<Map<String, Object>> lignes = BonRenduParDocuments.lignesDuBon(bon(List.of()));

        assertThat(lignes).hasSize(1);
        assertThat(lignes.get(0)).containsOnlyKeys("LIGNE_TYPE", "LIGNE_LIBELLE", "LIGNE_QUANTITE", "LIGNE_PRIX", "LIGNE_MONTANT");
        assertThat(lignes.get(0).values()).containsOnlyNulls();
    }
}
