package com.hemodialyse.backend.contexts.stock.infrastructure.acl.documents;

import com.hemodialyse.backend.contexts.stock.domain.inventaire.LigneDInventaire;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** DOC5j (ADR 0202) : les lignes du procès-verbal reprennent l'ordre, les champs et les états de l'ancienne requête du gabarit. */
class LignesDuProcesVerbalTest {

    private static final LocalDate JOUR = LocalDate.of(2026, 11, 15);
    private static final OffsetDateTime LE = OffsetDateTime.of(2026, 11, 15, 8, 0, 0, 0, ZoneOffset.UTC);

    private static LigneDInventaire ligne(String article, String libelle, String lot, LocalDate peremption, String theorique, String comptee,
                                          String motif) {
        LigneDInventaire l = LigneDInventaire.theorique(UUID.randomUUID(), UUID.fromString(article), "C-" + article.substring(0, 2),
                libelle, "boîte", null, lot, peremption, new BigDecimal(theorique), new BigDecimal("2.5"));
        return comptee == null ? l : l.compter(new BigDecimal(comptee), motif, "Alice", LE);
    }

    private static List<Object> colonne(List<Map<String, Object>> lignes, String champ) {
        return lignes.stream().map(m -> m.get(champ)).toList();
    }

    @Test
    void les_lignes_sont_ordonnees_par_libelle_sans_casse_puis_identifiant_non_signe_puis_peremption_absente_d_abord_puis_position() {
        String haut = "f0000000-0000-0000-0000-000000000001";
        String bas = "10000000-0000-0000-0000-000000000003";
        List<LigneDInventaire> lignes = List.of(
                ligne(haut, "Compresse", "H", LocalDate.of(2030, 1, 1), "1", null, null),
                ligne(bas, "COMPRESSE", "B2", LocalDate.of(2030, 1, 1), "1", null, null),
                ligne(bas, "COMPRESSE", "B1", LocalDate.of(2030, 1, 1), "1", null, null),
                ligne(bas, "COMPRESSE", "B0", null, "1", null, null),
                ligne("00000000-0000-0000-0000-0000000000a1", "zèbre", "Z", null, "1", null, null),
                ligne("00000000-0000-0000-0000-0000000000a2", null, "SANS", null, "1", null, null),
                ligne("00000000-0000-0000-0000-0000000000a3", "Éther", "E", null, "1", null, null));

        List<Map<String, Object>> cartes = LignesDuProcesVerbal.de(lignes, JOUR);

        assertEquals(List.of("SANS", "B0", "B2", "B1", "H", "Z", "E"), colonne(cartes, "LOT_LIBELLE"),
                "article sans libellé d'abord ; COMPRESSE ex æquo : identifiant non signé (f0... en dernier), péremption absente d'abord, "
                        + "position ; puis zèbre avant Éther (comparaison des caractères UTF-16, comme avant)");
    }

    @Test
    void un_lot_trouve_s_appelle_comme_avant_et_un_lot_sans_numero_est_sans_lot() {
        LigneDInventaire trouve = ligne("00000000-0000-0000-0000-000000000001", "A", "L9", null, "1", "1", null).commeAjoutee();
        LigneDInventaire sans = ligne("00000000-0000-0000-0000-000000000002", "B", null, null, "1", "1", null);

        List<Map<String, Object>> cartes = LignesDuProcesVerbal.de(List.of(trouve, sans), JOUR);

        assertEquals(List.of("L9 (trouvé)", "sans lot"), colonne(cartes, "LOT_LIBELLE"));
        assertEquals("00000000-0000-0000-0000-000000000001", cartes.get(0).get("ARTICLE_KEY"));
    }

    @Test
    void l_etat_de_peremption_se_lit_au_jour_d_edition() {
        String a = "00000000-0000-0000-0000-000000000001";
        List<LigneDInventaire> lignes = List.of(
                ligne(a, "A", "hier", JOUR.minusDays(1), "1", null, null),
                ligne(a, "A", "aujourdhui", JOUR, "1", null, null),
                ligne(a, "A", "j90", JOUR.plusDays(90), "1", null, null),
                ligne(a, "A", "j91", JOUR.plusDays(91), "1", null, null),
                ligne(a, "A", "aucune", null, "1", null, null));

        List<Map<String, Object>> cartes = LignesDuProcesVerbal.de(lignes, JOUR);

        assertEquals(java.util.Arrays.asList(null, "EXPIRE", "PROCHE", "PROCHE", null), colonne(cartes, "PEREMPTION_ETAT"));
        assertEquals(Date.valueOf(JOUR.minusDays(1)), cartes.get(1).get("DATE_PEREMPTION"));
        assertNull(cartes.get(0).get("DATE_PEREMPTION"));
    }

    @Test
    void les_motifs_les_ecarts_et_les_lots_non_comptes_s_expriment_comme_dans_l_ancienne_requete() {
        List<LigneDInventaire> lignes = List.of(
                ligne("00000000-0000-0000-0000-000000000001", "A", "1", null, "10", "8", "CASSE"),
                ligne("00000000-0000-0000-0000-000000000002", "B", "2", null, "10", "12", "Motif libre"),
                ligne("00000000-0000-0000-0000-000000000003", "C", "3", null, "10", "9", null),
                ligne("00000000-0000-0000-0000-000000000004", "D", "4", null, "10", "10", null),
                ligne("00000000-0000-0000-0000-000000000005", "E", "5", null, "10", null, null));

        List<Map<String, Object>> cartes = LignesDuProcesVerbal.de(lignes, JOUR);

        assertEquals(java.util.Arrays.asList("Casse", "Motif libre", "À justifier", null, null), colonne(cartes, "MOTIF"));
        assertEquals(List.of(0, 0, 0, 0, 1), colonne(cartes, "NON_COMPTE"));
        assertEquals(new BigDecimal("-5.00"), cartes.get(0).get("VAL_ECART"));
        assertEquals(new BigDecimal("2.5"), cartes.get(0).get("PMP"));
        assertNull(cartes.get(4).get("ECART"));
        assertNull(cartes.get(4).get("VAL_ECART"));
        assertNull(cartes.get(4).get("QUANTITE_COMPTEE"));
        assertEquals("Alice", cartes.get(0).get("COMPTE_PAR"));
    }
}
