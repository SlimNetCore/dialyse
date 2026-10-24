package com.hemodialyse.backend.characterization.stock.referentiel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Filet de caractérisation S7 — inventaire de stock : {@code /api/v1/stock/inventaires} (RG-STK-050 à 057). Point d'observation :
 * HTTP et état SQL de {@code inventaire_lignes}, {@code lots}, {@code stock_movements} (mouvements figés, mouvement {@code INVENTAIRE})
 * et {@code articles} (stock, PMP). Écrit sur le legacy ({@code InventaireRestController}, {@code InventaireService},
 * {@code InventaireJdbcAdapter}). Dates relatives au jour d'exécution, normalisées en {@code <J-n>} ; l'inventaire est ouvert à la
 * date d'hier pour rester insensible au changement de jour UTC/local. Non couvert ici : le recalcul en cours (verrou de
 * recalcul) et la concurrence (ouverture pendant une réception, deux ouvertures), observés dans les filets S5.
 */
class InventaireCharacterizationIntegrationTest extends ReferentielStockCharacterizationSupport {

    private static final String INV = "/api/v1/stock/inventaires";

    private static String date(int moinsN) {
        return LocalDate.now().minusDays(moinsN).toString();
    }

    private static OffsetDateTime minuit(int moinsN) {
        return LocalDate.now().minusDays(moinsN).atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    @Override
    protected String theme() {
        return "inventaire";
    }

    // ------------------------------------------------------------------ données

    @BeforeEach
    @AfterEach
    void viderLInventaire() {
        for (UUID c : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM inventaire_lignes WHERE inventaire_id IN (SELECT id FROM inventaires WHERE center_id = ?)", c);
            jdbc.update("DELETE FROM inventaires WHERE center_id = ?", c);
            jdbc.update("DELETE FROM bons_sortie_lignes WHERE bon_sortie_id IN (SELECT id FROM bons_sortie WHERE center_id = ?)", c);
            jdbc.update("DELETE FROM bons_sortie WHERE center_id = ?", c);
            jdbc.update("DELETE FROM stock_movements WHERE center_id = ?", c);
            jdbc.update("DELETE FROM lots WHERE center_id = ?", c);
            jdbc.update("DELETE FROM app_settings WHERE center_id = ? AND cle IN ('INV', 'SEQ_INV', 'SEQ_BS')", c);
        }
    }

    private UUID art(String alias, String code, boolean actif, boolean parLot, String stock, String pmp) {
        UUID id = article(CENTRE_A, alias, code, "Article " + code, null, null, actif, null, stock, pmp);
        if (parLot) {
            jdbc.update("UPDATE articles SET gere_par_lot = true WHERE id = ?", id);
        }
        return id;
    }

    private UUID lot(UUID article, String alias, String numero, int jours, String quantite, String prix) {
        UUID lot = UUID.randomUUID();
        jdbc.update("INSERT INTO lots (id, center_id, article_id, numero_lot, date_peremption, quantite_initiale, quantite_restante, pmp, "
                        + "created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", lot, CENTRE_A, article, numero, LocalDate.now().plusDays(jours),
                new BigDecimal(quantite), new BigDecimal(quantite), new BigDecimal(prix), minuit(10));
        jdbc.update("INSERT INTO stock_movements (id, center_id, article_id, lot_id, mouvement_type, quantite, prix_unitaire, created_by, "
                        + "created_at, saisi_le) VALUES (?, ?, ?, ?, 'ENTREE', ?, ?, 'test', ?, ?)", UUID.randomUUID(), CENTRE_A, article, lot,
                new BigDecimal(quantite), new BigDecimal(prix), minuit(10), minuit(10).plusHours(1));
        aliasNommes.put(lot.toString(), "<" + alias + ">");
        return lot;
    }

    /**
     * Catalogue type : A1 (deux lots + un lot épuisé), A2 (stock ancien sans lot), A3 inactif (exclu).
     */
    private UUID[] catalogue() {
        UUID a1 = art("ART_1", "A1", true, true, "8", "5");
        lot(a1, "LOT_A1_TOT", "L-TOT", 10, "3", "5");
        lot(a1, "LOT_A1_TARD", "L-TARD", 40, "5", "5");
        lot(a1, "LOT_A1_VIDE", "L-VIDE", 20, "0", "5");
        UUID a2 = art("ART_2", "A2", true, false, "2", "7");
        UUID a3 = art("ART_3", "A3", false, false, "4", "3");
        lot(a3, "LOT_A3", "L-A3", 15, "4", "3");
        return new UUID[]{a1, a2, a3};
    }

    // ------------------------------------------------------------------ requêtes

    private Resp jours(Resp r) {
        String brut = r.raw();
        if (brut == null) {
            return r;
        }
        brut = brut.replaceAll("\\d{4}-\\d{2}-\\d{2}T[\\d:.]+(Z|[+-]\\d{2}:?\\d{2})?", "<HORODATAGE>");
        for (int n = -120; n <= 120; n++) {
            String jour = n >= 0 ? "<J-" + n + ">" : "<J+" + (-n) + ">";
            brut = brut.replace(LocalDate.now().minusDays(n).toString(), jour);
            brut = brut.replace(LocalDate.now().minusDays(n).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")), jour);
        }
        return new Resp(r.status(), r.contentType(), brut);
    }

    private Resp lire(String chemin, RequestPostProcessor qui) throws Exception {
        return jours(send(get(java.net.URI.create(chemin)).with(qui)));
    }

    private Resp ecrire(boolean ajout, String chemin, String corps, RequestPostProcessor qui) throws Exception {
        var requete = ajout ? post(java.net.URI.create(chemin)) : put(java.net.URI.create(chemin));
        return jours(send(requete.contentType(MediaType.APPLICATION_JSON).content(corps).with(qui)));
    }

    private Resp action(String chemin, RequestPostProcessor qui) throws Exception {
        return jours(send(post(java.net.URI.create(chemin)).with(qui)));
    }

    private Resp ouvrir(int jourMoinsN, String commentaire) throws Exception {
        return ouvrirLe(date(jourMoinsN), commentaire, adminA());
    }

    private Resp ouvrirLe(String jour, String commentaire, RequestPostProcessor qui) throws Exception {
        return ecrire(true, INV, json("{'dateInventaire':'" + jour + "'" + (commentaire == null ? "" : ",'commentaire':'" + commentaire + "'") + "}"), qui);
    }

    private UUID ouvertOk(int jourMoinsN) throws Exception {
        Resp r = ouvrir(jourMoinsN, "Inventaire annuel");
        assertEquals(201, r.status(), r.raw());
        return UUID.fromString(json(r).path("id").asString());
    }

    private JsonNode detail(UUID inventaire) throws Exception {
        return json(lire(INV + "/" + inventaire, adminA()));
    }

    private UUID ligne(UUID inventaire, String code, String numeroLot) throws Exception {
        for (JsonNode l : detail(inventaire).path("details")) {
            String lot = l.path("numeroLot").isNull() ? null : l.path("numeroLot").asString();
            if (code.equals(l.path("articleCode").asString()) && java.util.Objects.equals(numeroLot, lot)) {
                return UUID.fromString(l.path("id").asString());
            }
        }
        throw new IllegalStateException("ligne introuvable " + code + "/" + numeroLot);
    }

    private Resp compter(UUID inventaire, UUID ligne, String quantite, String motif, RequestPostProcessor qui) throws Exception {
        return ecrire(false, INV + "/" + inventaire + "/lignes/" + ligne,
                json("{'quantite':" + quantite + (motif == null ? "" : ",'motif':'" + motif + "'") + "}"), qui);
    }

    private Resp compterAdmin(UUID inventaire, UUID ligne, String quantite, String motif) throws Exception {
        return compter(inventaire, ligne, quantite, motif, adminA());
    }

    private void goldenLignes(String name) {
        goldenSql(name, "SELECT l.position_ligne, l.article_code, l.numero_lot, l.quantite_theorique, l.quantite_comptee, l.pmp, "
                + "l.motif_ecart, l.compte_par, (l.lot_id IS NULL) AS sans_lot, l.ajoutee FROM inventaire_lignes l "
                + "JOIN inventaires i ON i.id = l.inventaire_id WHERE i.center_id = '" + CENTRE_A + "' ORDER BY i.reference, l.position_ligne");
    }

    private void goldenStock(String prefixe) {
        goldenSql(prefixe + "_lots", "SELECT l.numero_lot, l.quantite_initiale, l.quantite_restante, l.pmp FROM lots l "
                + "WHERE l.center_id = '" + CENTRE_A + "' ORDER BY l.numero_lot");
        goldenSql(prefixe + "_mouvements", "SELECT m.mouvement_type, m.quantite, m.prix_unitaire, (m.inventaire_id IS NOT NULL) AS fige "
                + "FROM stock_movements m WHERE m.center_id = '" + CENTRE_A + "' ORDER BY m.created_at, m.mouvement_type, m.quantite, m.id");
        goldenArticles(prefixe + "_articles", CENTRE_A);
    }

    private Resp synthese(ObjectNode o) {
        return new Resp(200, "application/json", mapper.writerWithDefaultPrettyPrinter().writeValueAsString(o));
    }

    // ------------------------------------------------------------------ ouverture

    @Test
    void ouverture_fige_le_theorique_des_lots_non_epuises_des_articles_actifs() throws Exception {
        catalogue();
        golden("etat_sans_inventaire", lire(INV + "/etat", adminA()));
        Resp ouverture = ouvrir(1, "Inventaire annuel");
        golden("ouverture_nominal", ouverture);
        goldenLignes("ouverture_lignes");
        golden("etat_apres_ouverture", lire(INV + "/etat", adminA()));
        golden("liste_apres_ouverture", lire(INV, adminA()));
    }

    @Test
    void ouverture_sans_date_prend_la_date_du_jour_et_refuse_une_date_future() throws Exception {
        golden("ouverture_date_future", ouvrirLe(date(-2), null, adminA()));
        golden("ouverture_sans_date", ecrire(true, INV, "{}", adminA()));
    }

    @Test
    void un_seul_inventaire_en_cours_par_centre() throws Exception {
        ouvertOk(1);
        golden("ouverture_deja_en_cours", ouvrir(2, null));
    }

    @Test
    void la_date_doit_suivre_la_derniere_cloture_et_le_dernier_mouvement() throws Exception {
        UUID inv = ouvertOk(5);
        assertEquals(200, action(INV + "/" + inv + "/cloturer", adminA()).status());
        golden("ouverture_avant_derniere_cloture", ouvrir(6, null));
        golden("ouverture_a_la_date_de_derniere_cloture", ouvrir(5, null));
        golden("etat_apres_cloture_vide", lire(INV + "/etat", adminA()));

        UUID a = art("ART_1", "A1", true, false, "3", "5");
        lot(a, "LOT_X", "L-X", 10, "3", "5");
        jdbc.update("UPDATE stock_movements SET created_at = ? WHERE center_id = ?", minuit(2), CENTRE_A);
        golden("ouverture_avant_dernier_mouvement", ouvrir(3, null));
        golden("ouverture_apres_dernier_mouvement", ouvrir(1, null));
    }

    // ------------------------------------------------------------------ comptage

    @Test
    void comptage_quantite_motif_et_refus() throws Exception {
        catalogue();
        UUID inv = ouvertOk(1);
        UUID tot = ligne(inv, "A1", "L-TOT");
        UUID tard = ligne(inv, "A1", "L-TARD");
        golden("comptage_nominal", compterAdmin(inv, tot, "2", "CASSE"));
        golden("comptage_trois_decimales", compterAdmin(inv, tard, "4.125", null));
        golden("comptage_quatre_decimales", compterAdmin(inv, tard, "4.1255", null));
        golden("comptage_negatif", compterAdmin(inv, tard, "-1", null));
        golden("comptage_sans_quantite", ecrire(false, INV + "/" + inv + "/lignes/" + tard, "{}", adminA()));
        golden("comptage_ligne_inconnue", compterAdmin(inv, UUID.randomUUID(), "1", null));
        golden("comptage_motif_trop_long", compterAdmin(inv, tard, "1", "x".repeat(256)));
        goldenLignes("comptage_lignes");
    }

    @Test
    void ajout_et_retrait_de_lignes() throws Exception {
        UUID[] a = catalogue();
        UUID inv = ouvertOk(1);
        String corps = "{'articleId':'%s','numeroLot':%s,'quantite':%s}";
        golden("ajout_article_par_lot_sans_numero", ecrire(true, INV + "/" + inv + "/lignes",
                json(corps.formatted(a[0], "null", "2")), adminA()));
        golden("ajout_lot_epuise_rattache_au_lot_existant", ecrire(true, INV + "/" + inv + "/lignes",
                json(corps.formatted(a[0], "'L-VIDE'", "2")), adminA()));
        golden("ajout_doublon", ecrire(true, INV + "/" + inv + "/lignes", json(corps.formatted(a[0], "'L-VIDE'", "1")), adminA()));
        golden("ajout_lot_inconnu_avec_peremption", ecrire(true, INV + "/" + inv + "/lignes",
                json("{'articleId':'" + a[0] + "','numeroLot':'L-NEUF','datePeremption':'" + LocalDate.now().plusDays(90) + "','quantite':1,'motif':'RETOUR'}"), adminA()));
        golden("ajout_article_inconnu", ecrire(true, INV + "/" + inv + "/lignes",
                json(corps.formatted(UUID.randomUUID(), "null", "1")), adminA()));
        golden("ajout_article_non_gere_par_lot", ecrire(true, INV + "/" + inv + "/lignes", json(corps.formatted(a[1], "null", "1")), adminA()));

        UUID ajoutee = ligne(inv, "A1", "L-NEUF");
        UUID theorique = ligne(inv, "A1", "L-TOT");
        golden("retrait_ligne_theorique_refuse", jours(send(delete(java.net.URI.create(INV + "/" + inv + "/lignes/" + theorique)).with(adminA()))));
        golden("retrait_ligne_ajoutee", jours(send(delete(java.net.URI.create(INV + "/" + inv + "/lignes/" + ajoutee)).with(adminA()))));
        goldenLignes("ajout_retrait_lignes");
    }

    @Test
    void report_du_theorique_compte_chaque_ligne_a_son_theorique() throws Exception {
        catalogue();
        UUID inv = ouvertOk(1);
        golden("report_theorique", action(INV + "/" + inv + "/reporter-theorique", adminA()));
        goldenLignes("report_theorique_lignes");
    }

    // ------------------------------------------------------------------ clôture, annulation

    @Test
    void cloture_refusee_si_une_ligne_n_est_pas_comptee_ou_un_ecart_sans_motif() throws Exception {
        catalogue();
        UUID inv = ouvertOk(1);
        golden("cloture_incomplete", action(INV + "/" + inv + "/cloturer", adminA()));
        action(INV + "/" + inv + "/reporter-theorique", adminA());
        compterAdmin(inv, ligne(inv, "A1", "L-TOT"), "2", null);
        golden("cloture_ecart_sans_motif", action(INV + "/" + inv + "/cloturer", adminA()));
        goldenStock("cloture_refusee");
    }

    @Test
    void cloture_pose_le_stock_compte_fige_les_mouvements_et_recalcule_le_pmp() throws Exception {
        UUID[] a = catalogue();
        UUID inv = ouvertOk(1);
        compterAdmin(inv, ligne(inv, "A1", "L-TOT"), "2", "CASSE");
        compterAdmin(inv, ligne(inv, "A1", "L-TARD"), "5", null);
        compterAdmin(inv, ligne(inv, "A2", null), "3", "RETOUR");
        ecrire(true, INV + "/" + inv + "/lignes", json("{'articleId':'" + a[0] + "','numeroLot':'L-VIDE','quantite':1,'motif':'RETOUR'}"), adminA());
        golden("cloture_nominal", action(INV + "/" + inv + "/cloturer", adminA()));
        goldenStock("cloture");
        goldenLignes("cloture_lignes");
        golden("etat_apres_cloture", lire(INV + "/etat", adminA()));
        golden("liste_apres_cloture", lire(INV, adminA()));
        golden("cloture_deja_cloture", action(INV + "/" + inv + "/cloturer", adminA()));
        golden("comptage_apres_cloture", compterAdmin(inv, ligne(inv, "A1", "L-TOT"), "1", null));
        golden("annulation_apres_cloture", action(INV + "/" + inv + "/annuler", adminA()));
    }

    @Test
    void annulation_sans_effet_sur_le_stock_puis_les_mouvements_reprennent() throws Exception {
        UUID[] a = catalogue();
        UUID inv = ouvertOk(1);
        compterAdmin(inv, ligne(inv, "A1", "L-TOT"), "1", "CASSE");
        golden("annulation_nominal", action(INV + "/" + inv + "/annuler", adminA()));
        goldenStock("annulation");
        golden("annulation_deja_annule", action(INV + "/" + inv + "/annuler", adminA()));
        golden("cloture_apres_annulation", action(INV + "/" + inv + "/cloturer", adminA()));
        golden("etat_apres_annulation", lire(INV + "/etat", adminA()));

        UUID lotTot = jdbc.queryForObject("SELECT id FROM lots WHERE center_id = ? AND numero_lot = 'L-TOT'", UUID.class, CENTRE_A);
        Resp sortie = ecrire(true, "/api/v1/stock/bons-sortie", json("{'centerId':'" + CENTRE_A + "','dateSortie':'" + date(1)
                + "','items':[{'articleId':'" + a[0] + "','lotId':'" + lotTot + "','quantite':1}]}"), adminA());
        assertEquals(200, sortie.status(), sortie.raw());
        goldenStock("annulation_puis_sortie");
        golden("ouverture_apres_annulation", ouvrir(1, null));
    }

    // ------------------------------------------------------------------ rôles et centres

    @Test
    void matrice_des_roles_et_cloisonnement_par_centre() throws Exception {
        catalogue();
        ObjectNode matrice = mapper.createObjectNode();
        for (String role : List.of("ADMIN", "PHARMACIEN", "INFIRMIER", "DIRECTEUR_ADMINISTRATIF", "MEDECIN", "SECRETAIRE")) {
            RequestPostProcessor qui = as(CENTRE_A, role);
            UUID ouvert = null;
            Resp ouverture = ouvrirLe(date(1), null, qui);
            matrice.put("ouvrir [" + role + "]", ouverture.status());
            if (ouverture.status() == 201) {
                ouvert = UUID.fromString(json(ouverture).path("id").asString());
            }
            UUID inv = ouvert != null ? ouvert : ouvertOk(1);
            UUID ligne = ligne(inv, "A1", "L-TOT");
            matrice.put("lire l'état [" + role + "]", lire(INV + "/etat", qui).status());
            matrice.put("lire le détail [" + role + "]", lire(INV + "/" + inv, qui).status());
            matrice.put("compter [" + role + "]", compter(inv, ligne, "1", null, qui).status());
            matrice.put("reporter le théorique [" + role + "]", action(INV + "/" + inv + "/reporter-theorique", qui).status());
            matrice.put("feuille de comptage [" + role + "]", lire(INV + "/" + inv + "/feuille-comptage", qui).status());
            matrice.put("annuler [" + role + "]", action(INV + "/" + inv + "/annuler", qui).status());
            if (ouvert == null) {
                action(INV + "/" + inv + "/annuler", adminA());
            }
        }
        UUID inv = ouvertOk(1);
        matrice.put("lire le détail d'un autre centre [ADMIN centre B]", lire(INV + "/" + inv, adminB()).status());
        matrice.put("ouvrir pour un autre centre [ADMIN centre A]", ecrire(true, INV, json("{'centerId':'" + CENTRE_B + "'}"), adminA()).status());
        matrice.put("lire l'état sans authentification", send(get(java.net.URI.create(INV + "/etat"))).status());
        golden("matrice_roles", synthese(matrice));
    }

    // ------------------------------------------------------------------ feuille de comptage et PV

    private byte[] feuille(UUID inv, boolean aveugle) throws Exception {
        return mvc.perform(get(java.net.URI.create(INV + "/" + inv + "/feuille-comptage?aveugle=" + aveugle)).with(adminA()))
                .andReturn().getResponse().getContentAsByteArray();
    }

    private Resp importer(UUID inv, String nom, byte[] contenu) throws Exception {
        MockMultipartFile fichier = new MockMultipartFile("file", nom, "application/octet-stream", contenu);
        org.springframework.mock.web.MockHttpServletResponse r = mvc.perform(
                multipart(java.net.URI.create(INV + "/" + inv + "/feuille-comptage")).file(fichier).with(adminA())).andReturn().getResponse();
        String type = r.getContentType() == null ? null : r.getContentType().split(";")[0];
        return jours(new Resp(r.getStatus(), type, r.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private ObjectNode resumeDeLaFeuille(byte[] contenu) throws Exception {
        ObjectNode o = mapper.createObjectNode();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(contenu))) {
            Sheet sheet = wb.getSheetAt(0);
            o.put("feuille", sheet.getSheetName());
            Row titre = sheet.getRow(0);
            o.put("titre", titre.getCell(1).getStringCellValue().replaceAll("INV-[0-9A-Za-z-]+", "<REFERENCE>")
                    .replaceAll("\\d{2}/\\d{2}/\\d{4}", "<DATE>"));
            ArrayNode entetes = o.putArray("entetes");
            Row entete = sheet.getRow(3);
            for (int c = 0; c < entete.getLastCellNum(); c++) {
                entetes.add(entete.getCell(c).getStringCellValue());
            }
            o.put("colonneAMasquee", sheet.isColumnHidden(0));
            ArrayNode lignes = o.putArray("lignes");
            for (int r = 4; r <= sheet.getLastRowNum(); r++) {
                ArrayNode ligne = lignes.addArray();
                Row row = sheet.getRow(r);
                for (int c = 1; c < row.getLastCellNum(); c++) {
                    Cell cell = row.getCell(c);
                    String valeur = cell == null ? "" : switch (cell.getCellType()) {
                        case NUMERIC -> String.valueOf(cell.getNumericCellValue());
                        default -> cell.getStringCellValue();
                    };
                    ligne.add(valeur.replaceAll("\\d{2}/\\d{2}/\\d{4}", "<DATE>"));
                }
            }
        }
        return o;
    }

    @Test
    void feuille_de_comptage_telechargement_puis_import() throws Exception {
        catalogue();
        UUID inv = ouvertOk(1);
        byte[] aveugle = feuille(inv, true);
        golden("feuille_aveugle", synthese(resumeDeLaFeuille(aveugle)));
        golden("feuille_avec_theorique", synthese(resumeDeLaFeuille(feuille(inv, false))));

        byte[] remplie;
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(aveugle)); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.getSheetAt(0);
            // colonnes (feuille à l'aveugle) : 1 code, 4 lot, 6 quantité comptée, 7 motif
            // lignes : A1/L-TOT (ligne 4), A1/L-TARD (5), A2 sans lot (6) ; une ligne d'article inconnu est ajoutée (7)
            sheet.getRow(4).createCell(6).setCellValue(2);
            sheet.getRow(4).createCell(7).setCellValue("Casse");
            sheet.getRow(5).createCell(6).setCellValue(-3);
            sheet.getRow(6).createCell(6).setCellValue("abc");
            Row inconnue = sheet.createRow(7);
            inconnue.createCell(1).setCellValue("ZZZ");
            inconnue.createCell(6).setCellValue(1);
            wb.write(out);
            remplie = out.toByteArray();
        }
        golden("import_feuille_remplie", importer(inv, "feuille.xlsx", remplie));
        golden("import_feuille_remplie_une_seconde_fois", importer(inv, "feuille.xlsx", remplie));
        goldenLignes("import_lignes");

        byte[] autreInventaire;
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(aveugle)); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.getSheetAt(0).getRow(0).getCell(1).setCellValue("Inventaire INV-AUTRE du 01/01/2026");
            wb.write(out);
            autreInventaire = out.toByteArray();
        }
        golden("import_feuille_d_un_autre_inventaire", importer(inv, "feuille.xlsx", autreInventaire));
        golden("import_format_non_excel", importer(inv, "feuille.txt", "pas un classeur".getBytes()));
        golden("import_fichier_vide", importer(inv, "vide.xlsx", new byte[0]));
        golden("import_classeur_sans_colonnes", importer(inv, "vide.xlsx", classeurSansEnTetes()));
        action(INV + "/" + inv + "/annuler", adminA());
        golden("import_inventaire_annule", importer(inv, "feuille.xlsx", remplie));
    }

    private byte[] classeurSansEnTetes() throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.createSheet("Vide").createRow(0).createCell(0).setCellValue("rien");
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void proces_verbal_en_pdf_provisoire_puis_definitif() throws Exception {
        catalogue();
        UUID inv = ouvertOk(1);
        ObjectNode pv = mapper.createObjectNode();
        var provisoire = mvc.perform(get(java.net.URI.create(INV + "/" + inv + "/rapport")).with(adminA())).andReturn().getResponse();
        pv.put("encours.status", provisoire.getStatus());
        pv.put("encours.contentType", String.valueOf(provisoire.getContentType()));
        pv.put("encours.pdf", provisoire.getContentAsByteArray().length > 4
                && new String(provisoire.getContentAsByteArray(), 0, 4).equals("%PDF"));
        pv.put("encours.disposition", String.valueOf(provisoire.getHeader("Content-Disposition")).replaceAll("INV-[0-9A-Za-z-]+", "<REFERENCE>"));
        action(INV + "/" + inv + "/reporter-theorique", adminA());
        action(INV + "/" + inv + "/cloturer", adminA());
        var definitif = mvc.perform(get(java.net.URI.create(INV + "/" + inv + "/rapport")).with(as(CENTRE_A, "DIRECTEUR_ADMINISTRATIF")))
                .andReturn().getResponse();
        pv.put("cloture.status", definitif.getStatus());
        pv.put("cloture.pdf", definitif.getContentAsByteArray().length > 4 && new String(definitif.getContentAsByteArray(), 0, 4).equals("%PDF"));
        pv.put("inconnu.status", mvc.perform(get(java.net.URI.create(INV + "/" + UUID.randomUUID() + "/rapport")).with(adminA()))
                .andReturn().getResponse().getStatus());
        golden("proces_verbal", synthese(pv));
    }
}




