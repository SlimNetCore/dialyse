package com.hemodialyse.backend.characterization.stock.referentiel;

import com.hemodialyse.backend.application.audit.AuditActionResolver;
import com.hemodialyse.backend.application.audit.AuditWriterService;
import com.hemodialyse.backend.characterization.gmao.plans.GoldenMaster;
import com.hemodialyse.backend.infrastructure.security.AuditRequestFilter;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * PL7 - complement du filet S7 ({@link InventaireCharacterizationIntegrationTest}) pour le proces-verbal d'inventaire
 * ({@code GET /api/v1/stock/inventaires/{id}/rapport}, PDF) et la feuille de comptage Excel
 * ({@code GET|POST /{id}/feuille-comptage}). S7 ne verrouillait du PV que le statut, le type et l'en-tete %PDF : ici le
 * texte extrait du PDF (provisoire, cloture, annule, cas limites, pagination, fuseau), le contenu des cellules de la
 * feuille, ses cas limites d'import, la garde de centre, les roles, les parametres illisibles et l'audit.
 */
class InventairePl7CharacterizationIntegrationTest extends ReferentielStockCharacterizationSupport {

    private static final String INV = "/api/v1/stock/inventaires";
    private static final Path DOSSIER = Path.of("src", "test", "resources", "characterization", "stock", "referentiel", "inventaire_pl7");

    @Autowired
    private AuditRequestFilter filtreDAudit;
    @Autowired
    private AuditWriterService ecrivainDAudit;

    private record Bin(int status, String contentType, String disposition, byte[] octets) {
        String texte() {
            return new String(octets, StandardCharsets.UTF_8);
        }
    }

    @Override
    protected String theme() {
        return "inventaire_pl7";
    }

    // ------------------------------------------------------------------ donnees

    @BeforeEach
    @AfterEach
    void viderLInventaire() {
        for (UUID c : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM inventaire_lignes WHERE inventaire_id IN (SELECT id FROM inventaires WHERE center_id = ?)", c);
            jdbc.update("DELETE FROM inventaires WHERE center_id = ?", c);
            jdbc.update("DELETE FROM stock_movements WHERE center_id = ?", c);
            jdbc.update("DELETE FROM lots WHERE center_id = ?", c);
            jdbc.update("DELETE FROM app_settings WHERE center_id = ? AND cle IN ('INV', 'SEQ_INV', 'SEQ_BS')", c);
            jdbc.update("DELETE FROM audit_log WHERE center_id = ?", c);
        }
    }

    private static String date(int moinsN) {
        return LocalDate.now().minusDays(moinsN).toString();
    }

    private static OffsetDateTime minuit(int moinsN) {
        return LocalDate.now().minusDays(moinsN).atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    private UUID art(String code, String libelle, boolean parLot, String stock, String pmp) {
        UUID id = article(CENTRE_A, "ART_" + code, code, libelle, null, null, true, null, stock, pmp);
        if (parLot) {
            jdbc.update("UPDATE articles SET gere_par_lot = true WHERE id = ?", id);
        }
        return id;
    }

    private void lot(UUID article, String numero, int jours, String quantite, String prix) {
        UUID lot = UUID.randomUUID();
        jdbc.update("INSERT INTO lots (id, center_id, article_id, numero_lot, date_peremption, quantite_initiale, quantite_restante, pmp, "
                        + "created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", lot, CENTRE_A, article, numero, LocalDate.now().plusDays(jours),
                new BigDecimal(quantite), new BigDecimal(quantite), new BigDecimal(prix), minuit(10));
        jdbc.update("INSERT INTO stock_movements (id, center_id, article_id, lot_id, mouvement_type, quantite, prix_unitaire, created_by, "
                        + "created_at, saisi_le) VALUES (?, ?, ?, ?, 'ENTREE', ?, ?, 'test', ?, ?)", UUID.randomUUID(), CENTRE_A, article, lot,
                new BigDecimal(quantite), new BigDecimal(prix), minuit(10), minuit(10).plusHours(1));
    }

    /**
     * A1 (deux lots), A2 (sans lot), plus quatre articles sans lot pour les cas d'import.
     */
    private void catalogue() {
        UUID a1 = art("A1", "Article A1", true, "8", "5");
        lot(a1, "L-TOT", 10, "3", "5");
        lot(a1, "L-TARD", 40, "5", "5");
        art("A2", "Article A2", false, "2", "7");
        art("A4", "Article A4", false, "10", "2");
        art("A5", "Article A5", false, "10", "2");
        art("A6", "Article A6", false, "10", "2");
        art("A7", "Article A7", false, "10", "2");
    }

    // ------------------------------------------------------------------ requetes

    private static String jours(String brut) {
        if (brut == null) {
            return null;
        }
        brut = brut.replaceAll("\\d{4}-\\d{2}-\\d{2}T[\\d:.]+(Z|[+-]\\d{2}:?\\d{2})?", "<HORODATAGE>");
        for (int n = -120; n <= 120; n++) {
            String jour = n >= 0 ? "<J-" + n + ">" : "<J+" + (-n) + ">";
            brut = brut.replace(LocalDate.now().minusDays(n).toString(), jour);
            brut = brut.replace(LocalDate.now().minusDays(n).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), jour);
        }
        return brut;
    }

    private static String masque(String texte) {
        return jours(texte.replaceAll("[\\u00a0\\u202f]", " ")
                .replaceAll("Édité le \\d{2}/\\d{2}/\\d{4}( à)? \\d{2}:\\d{2}", "Édité le <DATE>")
                .replaceAll("INV-[0-9A-Za-z-]+", "<REFERENCE>")
                .replaceAll("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "<UUID>"));
    }

    private Resp lire(String chemin, RequestPostProcessor qui) throws Exception {
        Resp r = send(get(java.net.URI.create(chemin)).with(qui));
        return new Resp(r.status(), r.contentType(), jours(r.raw()));
    }

    private Resp ecrire(boolean ajout, String chemin, String corps, RequestPostProcessor qui) throws Exception {
        var requete = ajout ? post(java.net.URI.create(chemin)) : put(java.net.URI.create(chemin));
        return send(requete.contentType(MediaType.APPLICATION_JSON).content(corps).with(qui));
    }

    private Resp action(String chemin, RequestPostProcessor qui) throws Exception {
        return send(post(java.net.URI.create(chemin)).with(qui));
    }

    private UUID ouvertOk(String commentaire) throws Exception {
        String corps = "{'dateInventaire':'" + date(1) + "'" + (commentaire == null ? "" : ",'commentaire':" + mapper.writeValueAsString(commentaire)) + "}";
        Resp r = ecrire(true, INV, json(corps), adminA());
        assertEquals(201, r.status(), r.raw());
        return UUID.fromString(json(r).path("id").asString());
    }

    private JsonNode detail(UUID inv) throws Exception {
        return json(lire(INV + "/" + inv, adminA()));
    }

    private String reference(UUID inv) throws Exception {
        return detail(inv).path("reference").asString();
    }

    private UUID ligne(UUID inv, String code, String numeroLot) throws Exception {
        for (JsonNode l : detail(inv).path("details")) {
            String lot = l.path("numeroLot").isNull() ? null : l.path("numeroLot").asString();
            if (code.equals(l.path("articleCode").asString()) && java.util.Objects.equals(numeroLot, lot)) {
                return UUID.fromString(l.path("id").asString());
            }
        }
        throw new IllegalStateException("ligne introuvable " + code + "/" + numeroLot);
    }

    private void compter(UUID inv, UUID ligne, String quantite, String motif) throws Exception {
        Resp r = ecrire(false, INV + "/" + inv + "/lignes/" + ligne,
                json("{'quantite':" + quantite + (motif == null ? "" : ",'motif':'" + motif + "'") + "}"), adminA());
        assertEquals(200, r.status(), r.raw());
    }

    private Bin bin(String chemin, RequestPostProcessor qui) throws Exception {
        MockHttpServletResponse r = mvc.perform(get(java.net.URI.create(chemin)).with(qui)).andReturn().getResponse();
        String type = r.getContentType() == null ? null : r.getContentType().split(";")[0];
        return new Bin(r.getStatus(), type, r.getHeader("Content-Disposition"), r.getContentAsByteArray());
    }

    private Bin bin(String chemin) throws Exception {
        return bin(chemin, adminA());
    }

    private static String texteDuPdf(byte[] pdf) throws Exception {
        try (PDDocument doc = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(doc).replace("\r\n", "\n");
        }
    }

    private static int pages(byte[] pdf) throws Exception {
        try (PDDocument doc = PDDocument.load(pdf)) {
            return doc.getNumberOfPages();
        }
    }

    /**
     * Texte du PV (200 attendu), masque.
     */
    private List<String> pv(UUID inv) throws Exception {
        return pvBrut(inv).stream().map(x -> x.replaceAll("(<J[-+]\\d+>) à \\d{2}:\\d{2}", "$1 à <HH:MM>")).toList();
    }

    /**
     * Texte du PV avec les heures (pour le test de fuseau, dates fixes).
     */
    private List<String> pvBrut(UUID inv) throws Exception {
        Bin b = bin(INV + "/" + inv + "/rapport");
        assertEquals(200, b.status(), b.texte());
        return masque(texteDuPdf(b.octets())).lines().toList();
    }

    private void goldenLignes(String name, List<String> lignes) {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < lignes.size(); i++) {
            sb.append("  ").append(mapper.writeValueAsString(lignes.get(i))).append(i + 1 < lignes.size() ? ",\n" : "\n");
        }
        GoldenMaster.verify(DOSSIER, name, sb.append("]").toString());
    }

    private String sommaire(Bin b) {
        String code = "";
        if (b.contentType() != null && b.contentType().contains("json")) {
            try {
                code = " code=" + mapper.readTree(b.texte()).path("code").asString("");
            } catch (RuntimeException e) {
                code = " (corps non JSON)";
            }
        }
        return b.status() + " " + b.contentType() + code;
    }

    private static RequestPostProcessor principal(String centre, String... roles) {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(
                UserPrincipal.create(UUID.randomUUID().toString(), centre, "u", "", List.of(roles), true));
    }

    // ------------------------------------------------------------------ PV : texte

    @Test
    void le_proces_verbal_provisoire_porte_le_filigrane_et_le_theorique_puis_le_cloture_les_ecarts_par_motif() throws Exception {
        catalogue();
        UUID inv = ouvertOk("Inventaire annuel");
        goldenLignes("pv_pl7_provisoire_texte", pv(inv));
        compter(inv, ligne(inv, "A1", "L-TOT"), "2", "CASSE");
        compter(inv, ligne(inv, "A1", "L-TARD"), "7", "RETOUR");
        compter(inv, ligne(inv, "A2", null), "1", "PERTE");
        compter(inv, ligne(inv, "A4", null), "10", null);
        compter(inv, ligne(inv, "A5", null), "10", null);
        compter(inv, ligne(inv, "A6", null), "10", null);
        compter(inv, ligne(inv, "A7", null), "12", "ERREUR_SAISIE");
        goldenLignes("pv_pl7_en_cours_partiellement_compte_texte", pv(inv));
        assertEquals(200, action(INV + "/" + inv + "/cloturer", adminA()).status());
        goldenLignes("pv_pl7_cloture_ecarts_texte", pv(inv));
    }

    @Test
    void le_proces_verbal_d_un_inventaire_annule_porte_le_filigrane_annule_sans_effet_sur_le_stock() throws Exception {
        catalogue();
        UUID inv = ouvertOk(null);
        compter(inv, ligne(inv, "A2", null), "0", "PERTE");
        assertEquals(200, action(INV + "/" + inv + "/annuler", adminA()).status());
        goldenLignes("pv_pl7_annule_texte", pv(inv));
    }

    @Test
    void un_inventaire_sans_aucune_ligne_s_imprime_avec_des_indicateurs_a_zero() throws Exception {
        UUID inv = ouvertOk("Rien en stock");
        goldenLignes("pv_pl7_sans_ligne_texte_nouveau_code",pv(inv));
        assertEquals(200, action(INV + "/" + inv + "/cloturer", adminA()).status());
        goldenLignes("pv_pl7_sans_ligne_cloture_texte_nouveau_code",pv(inv));
    }

    @Test
    void les_lots_perimes_les_quantites_negatives_les_textes_longs_et_speciaux_sont_rendus_tels_quels() throws Exception {
        UUID a1 = art("S1", "Solution <b>saline</b> & \"stérile\" $P{INV_REFERENCE} ${java:version} 日本語 ★ œ " + "très longue ".repeat(10) + "FIN-LIBELLE", true, "5", "123456.789");
        lot(a1, "LOT'; DROP TABLE x;--", -30, "4", "123456.789");
        lot(a1, "L-NEG", 5, "-2", "10");
        lot(a1, "L-" + "X".repeat(60), 400, "0.001", "0.001");
        lot(a1, "L-ENORME", 20, "99999999", "99999999.99");
        String commentaire = "Ligne 1\nLigne 2 <i>balise</i> & esperluette $P{INV_STATUT}\n" + "Commentaire long ".repeat(30) + "FIN-COMMENTAIRE";
        UUID inv = ouvertOk(commentaire);
        jdbc.update("UPDATE inventaires SET created_by = ? WHERE id = ?", "Dr Ünï <script> & fils", inv);
        List<String> l = new ArrayList<>();
        Bin b = bin(INV + "/" + inv + "/rapport");
        l.add("statut=" + b.status() + " pages=" + (b.status() == 200 ? pages(b.octets()) : 0));
        if (b.status() == 200) {
            masque(texteDuPdf(b.octets())).lines().map(x -> x.replaceAll("(<J[-+]\\d+>) à \\d{2}:\\d{2}", "$1 à <HH:MM>")).forEach(l::add);
        } else {
            l.add(sommaire(b));
        }
        goldenLignes("pv_pl7_contenu_limites_texte", l);
    }

    @Test
    void un_inventaire_de_soixante_lignes_deborde_sur_plusieurs_pages_et_garde_ses_indicateurs() throws Exception {
        for (int n = 1; n <= 30; n++) {
            UUID a = art(String.format("P%02d", n), "Produit PL7 numero " + n, true, "6", "10.50");
            lot(a, "LA", 10, "2", "10.50");
            lot(a, "LB", 20, "4", "10.50");
        }
        UUID inv = ouvertOk(null);
        Bin b = bin(INV + "/" + inv + "/rapport");
        String texte = masque(texteDuPdf(b.octets()));
        List<String> l = new ArrayList<>();
        l.add("statut=" + b.status() + " pages=" + pages(b.octets()));
        l.add("lignes de texte = " + texte.lines().count());
        for (int n : new int[]{1, 15, 30}) {
            l.add("contient Produit PL7 numero " + n + " = " + texte.contains("Produit PL7 numero " + n));
        }
        l.add("lignes 'Produit PL7' = " + texte.lines().filter(x -> x.contains("Produit PL7")).count());
        texte.lines().filter(x -> x.contains(" / 60") || x.contains("avancement") || x.contains("PROVISOIRE")).forEach(l::add);
        goldenLignes("pv_pl7_soixante_lignes", l);
    }

    @Test
    void les_horodatages_du_proces_verbal_sont_affiches_en_utc_quel_que_soit_le_decalage_de_stockage() throws Exception {
        catalogue();
        UUID inv = ouvertOk("Fuseau");
        compter(inv, ligne(inv, "A1", "L-TOT"), "3", null);
        assertEquals(200, action(INV + "/" + inv + "/reporter-theorique", adminA()).status());
        assertEquals(200, action(INV + "/" + inv + "/cloturer", adminA()).status());
        jdbc.update("UPDATE inventaires SET created_at = ?, closed_at = ?, created_by = 'Ouvreur', closed_by = 'Cloturant' WHERE id = ?",
                OffsetDateTime.parse("2026-03-10T23:30:00+01:00"), OffsetDateTime.parse("2026-03-11T00:15:00+05:00"), inv);
        List<String> l = new ArrayList<>();
        pvBrut(inv).stream().filter(x -> x.contains("/03/2026") || x.contains("Ouvreur") || x.contains("Cloturant")).forEach(l::add);
        goldenLignes("pv_pl7_fuseau_texte", l);
    }

    // ------------------------------------------------------------------ garde de centre, roles, parametres

    @Test
    void la_garde_de_centre_les_roles_et_les_parametres_illisibles_du_proces_verbal_et_de_la_feuille() throws Exception {
        catalogue();
        UUID inv = ouvertOk(null);
        UUID inconnu = UUID.randomUUID();
        List<String> l = new ArrayList<>();
        for (String suffixe : List.of("/rapport", "/feuille-comptage")) {
            String p = INV + "/" + inv + suffixe;
            l.add(suffixe + " ADMIN du centre -> " + sommaire(bin(p)));
            l.add(suffixe + " ADMIN du centre + ?centerId=B -> " + sommaire(bin(p + "?centerId=" + CENTRE_B)));
            l.add(suffixe + " ADMIN du centre + ?centerId=A -> " + sommaire(bin(p + "?centerId=" + CENTRE_A)));
            l.add(suffixe + " ADMIN du centre + ?centerId=illisible -> " + sommaire(bin(p + "?centerId=pas-un-uuid")));
            l.add(suffixe + " ADMIN du centre + ?centerId= vide -> " + sommaire(bin(p + "?centerId=")));
            l.add(suffixe + " ADMIN du centre B -> " + sommaire(bin(p, adminB())));
            l.add(suffixe + " ADMIN du centre B + ?centerId=A -> " + sommaire(bin(p + "?centerId=" + CENTRE_A, adminB())));
            l.add(suffixe + " ADMIN sans centre -> " + sommaire(bin(p, principal(null, "ADMIN"))));
            l.add(suffixe + " ADMIN centre illisible -> " + sommaire(bin(p, principal("pas-un-uuid", "ADMIN"))));
            l.add(suffixe + " ADMIN centre inexistant -> " + sommaire(bin(p, principal(inconnu.toString(), "ADMIN"))));
            l.add(suffixe + " SUPERADMIN sans centre -> " + sommaire(bin(p, principal(null, "SUPERADMIN"))));
            l.add(suffixe + " SUPERADMIN + ?centerId=A -> " + sommaire(bin(p + "?centerId=" + CENTRE_A, principal(null, "SUPERADMIN"))));
            for (String role : List.of("PHARMACIEN", "INFIRMIER", "DIRECTEUR_ADMINISTRATIF", "MEDECIN", "SECRETAIRE", "TECHNICIEN", "DIRECTEUR_GENERAL")) {
                l.add(suffixe + " " + role + " -> " + sommaire(bin(p, as(CENTRE_A, role))));
            }
            l.add(suffixe + " anonyme -> " + sommaire(bin(p, request -> request)));
            l.add(suffixe + " inventaire inconnu -> " + sommaire(bin(INV + "/" + inconnu + suffixe)));
            l.add(suffixe + " id nil -> " + sommaire(bin(INV + "/00000000-0000-0000-0000-000000000000" + suffixe)));
            l.add(suffixe + " id majuscules -> " + sommaire(bin(INV + "/" + inv.toString().toUpperCase() + suffixe)));
            l.add(suffixe + " id sans tirets -> " + sommaire(bin(INV + "/" + inv.toString().replace("-", "") + suffixe)));
            l.add(suffixe + " id tronque -> " + sommaire(bin(INV + "/" + inv.toString().substring(0, 8) + suffixe)));
            l.add(suffixe + " id numerique -> " + sommaire(bin(INV + "/12345" + suffixe)));
            l.add(suffixe + " barre finale -> " + sommaire(bin(INV + "/" + inv + suffixe + "/")));
            l.add(suffixe + " suffixe .pdf -> " + sommaire(bin(INV + "/" + inv + suffixe + ".pdf")));
        }
        for (String valeur : List.of("true", "false", "TRUE", "1", "0", "oui", "", "abc")) {
            l.add("/feuille-comptage ?aveugle=" + valeur + " -> " + sommaire(bin(INV + "/" + inv + "/feuille-comptage?aveugle=" + valeur)));
        }
        l.add("/rapport ?format=xlsx (parametre ignore) -> " + sommaire(bin(INV + "/" + inv + "/rapport?format=xlsx")));
        l.add("/rapport ?tz=Africa/Algiers (parametre ignore) -> " + sommaire(bin(INV + "/" + inv + "/rapport?tz=Africa/Algiers")));
        l.add("/rapport en-tetes (ADMIN) -> " + masque(String.valueOf(bin(INV + "/" + inv + "/rapport").disposition())));
        l.add("/feuille-comptage en-tetes (ADMIN) -> " + masque(String.valueOf(bin(INV + "/" + inv + "/feuille-comptage").disposition())));
        l.add("DELETE /rapport -> " + mvc.perform(delete(java.net.URI.create(INV + "/" + inv + "/rapport")).with(adminA())).andReturn().getResponse().getStatus());
        l.add("POST /rapport -> " + mvc.perform(post(java.net.URI.create(INV + "/" + inv + "/rapport")).with(adminA())).andReturn().getResponse().getStatus());
        goldenLignes("pv_pl7_garde_roles_parametres_nouveau_code",l);
    }

    // ------------------------------------------------------------------ feuille : contenu

    private List<String> cellules(byte[] contenu) throws Exception {
        List<String> l = new ArrayList<>();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(contenu))) {
            l.add("feuilles=" + wb.getNumberOfSheets() + " nom=" + wb.getSheetAt(0).getSheetName());
            Sheet sheet = wb.getSheetAt(0);
            l.add("colonne A masquee=" + sheet.isColumnHidden(0) + " validations=" + sheet.getDataValidations().size()
                    + " derniere ligne=" + sheet.getLastRowNum());
            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    l.add(r + ": <absente>");
                    continue;
                }
                List<String> vals = new ArrayList<>();
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    Cell cell = row.getCell(c);
                    String v = cell == null ? "" : switch (cell.getCellType()) {
                        case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
                        case BLANK -> "";
                        default -> cell.getStringCellValue();
                    };
                    vals.add(c == 0 && r >= 4 ? "<ID>" : v);
                }
                l.add(r + ": " + masque(String.join(" | ", vals)));
            }
        }
        return l;
    }

    @Test
    void le_contenu_de_la_feuille_de_comptage_reprend_lots_peremptions_quantites_et_motifs_tels_quels() throws Exception {
        UUID a1 = art("S1", "Solution <b>saline</b> & \"stérile\" =SUM(1+1) 日本語 " + "longue ".repeat(20), true, "5", "10");
        lot(a1, "LOT'; DROP--", -30, "4", "10");
        lot(a1, "L-NEG", 5, "-2", "10");
        lot(a1, "L-DECIMAL", 400, "0.125", "10");
        art("S2", "Sans lot", false, "3", "10");
        UUID inv = ouvertOk(null);
        compter(inv, ligne(inv, "S1", "L-DECIMAL"), "0.5", "AUTRE");
        compter(inv, ligne(inv, "S2", null), "0", "PERTE");
        jdbc.update("UPDATE inventaire_lignes SET compte_par = 'Ünï <b>' WHERE inventaire_id = ? AND article_code = 'S2'", inv);
        List<String> l = new ArrayList<>();
        for (boolean aveugle : new boolean[]{true, false}) {
            Bin b = bin(INV + "/" + inv + "/feuille-comptage?aveugle=" + aveugle);
            l.add("--- aveugle=" + aveugle + " statut=" + b.status() + " type=" + b.contentType());
            l.addAll(cellules(b.octets()));
        }
        goldenLignes("feuille_pl7_contenu_limites", l);
    }

    @Test
    void la_feuille_de_comptage_d_un_inventaire_sans_ligne_n_a_ni_validation_ni_ligne() throws Exception {
        UUID inv = ouvertOk(null);
        List<String> l = new ArrayList<>(cellules(bin(INV + "/" + inv + "/feuille-comptage").octets()));
        goldenLignes("feuille_pl7_sans_ligne", l);
    }

    @Test
    void la_feuille_d_un_inventaire_cloture_ou_annule_reste_telechargeable() throws Exception {
        catalogue();
        UUID inv = ouvertOk(null);
        assertEquals(200, action(INV + "/" + inv + "/annuler", adminA()).status());
        List<String> l = new ArrayList<>();
        Bin b = bin(INV + "/" + inv + "/feuille-comptage");
        l.add("annule -> " + sommaire(b));
        l.add("annule lignes=" + (b.status() == 200 ? cellules(b.octets()).size() : 0));
        goldenLignes("feuille_pl7_inventaire_annule", l);
    }

    // ------------------------------------------------------------------ feuille : import

    private byte[] ecrireXlsx(Workbook wb) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.write(out);
            wb.close();
            return out.toByteArray();
        }
    }

    private Resp importer(UUID inv, byte[] contenu, RequestPostProcessor qui) throws Exception {
        MockMultipartFile fichier = new MockMultipartFile("file", "feuille.xlsx", "application/octet-stream", contenu);
        MockHttpServletResponse r = mvc.perform(multipart(java.net.URI.create(INV + "/" + inv + "/feuille-comptage")).file(fichier).with(qui))
                .andReturn().getResponse();
        String type = r.getContentType() == null ? null : r.getContentType().split(";")[0];
        return new Resp(r.getStatus(), type, jours(r.getContentAsString(StandardCharsets.UTF_8)));
    }

    private Resp importer(UUID inv, byte[] contenu) throws Exception {
        return importer(inv, contenu, adminA());
    }

    private void goldenLignesSql(String name) {
        goldenSql(name, "SELECT l.position_ligne, l.article_code, l.numero_lot, l.quantite_theorique, l.quantite_comptee, l.motif_ecart, "
                + "l.compte_par, l.ajoutee FROM inventaire_lignes l JOIN inventaires i ON i.id = l.inventaire_id WHERE i.center_id = '"
                + CENTRE_A + "' ORDER BY i.reference, l.position_ligne");
    }

    @Test
    void l_import_accepte_virgule_formule_motif_libre_et_rapprochement_par_code_et_lot_et_signale_le_reste() throws Exception {
        catalogue();
        UUID inv = ouvertOk(null);
        byte[] aveugle = bin(INV + "/" + inv + "/feuille-comptage").octets();
        byte[] remplie;
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(aveugle))) {
            Sheet s = wb.getSheetAt(0);
            // colonnes (aveugle) : 0 id, 1 code, 2 article, 3 unite, 4 lot, 5 peremption, 6 quantite, 7 motif
            // ordre des lignes : 4..: A1/L-TOT, A1/L-TARD, A2, A4, A5, A6, A7 (selon l'ouverture)
            for (int r = 4; r <= s.getLastRowNum(); r++) {
                String code = s.getRow(r).getCell(1).getStringCellValue();
                String lot = s.getRow(r).getCell(4).getStringCellValue();
                Cell q = s.getRow(r).createCell(6);
                Cell m = s.getRow(r).createCell(7);
                switch (code + "/" + lot) {
                    case "A1/L-TOT" -> {
                        q.setCellValue("2,5");
                        m.setCellValue("perte / vol");
                    }
                    case "A1/L-TARD" -> {
                        q.setCellFormula("2+4"); // PL7 (KB-PL7-4) : un motif sans écart est refusé (400), couvert par ailleurs
                    }
                    case "A2/" -> {
                        q.setCellValue(" 1 000,5 ");
                        s.getRow(r).getCell(0).setCellValue("pas-un-uuid");
                        m.setCellValue("CASSE");
                    }
                    case "A4/" -> {
                        q.setCellValue(0);
                        m.setCellValue("Raison inconnue");
                    }
                    case "A5/" -> q.setCellValue("1e2");
                    case "A6/" -> {
                        q.setCellValue(-0.0001);
                    }
                    default -> {
                        // A7 laissee vide : ignoree
                    }
                }
            }
            Row doublon = s.createRow(s.getLastRowNum() + 1);
            doublon.createCell(1).setCellValue("A2");
            doublon.createCell(6).setCellValue(4);
            Row sansQuantite = s.createRow(s.getLastRowNum() + 1);
            sansQuantite.createCell(1).setCellValue("A4");
            sansQuantite.createCell(7).setCellValue("autre");
            remplie = ecrireXlsx(wb);
        }
        golden("import_pl7_cas_mixtes_nouveau_code",importer(inv, remplie));
        goldenLignesSql("import_pl7_cas_mixtes_lignes_nouveau_code");
    }

    @Test
    void l_import_tolere_les_en_tetes_deplaces_le_titre_absent_ou_en_casse_differente_et_le_format_xls() throws Exception {
        catalogue();
        UUID inv = ouvertOk(null);
        String ref = reference(inv);
        List<String> l = new ArrayList<>();

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("Autre");
            s.createRow(0).createCell(0).setCellValue("inventaire " + ref.toLowerCase() + " du 01/01/2026");
            s.createRow(1).createCell(1).setCellValue("Inventaire " + ref.toLowerCase() + " du 01/01/2026");
            Row h = s.createRow(10);
            h.createCell(0).setCellValue("Article code");
            h.createCell(1).setCellValue("CODE");
            h.createCell(2).setCellValue("n° de lot");
            h.createCell(3).setCellValue("QUANTITÉ  comptée");
            h.createCell(4).setCellValue("Motif");
            Row r = s.createRow(11);
            r.createCell(1).setCellValue("A1");
            r.createCell(2).setCellValue("L-TOT");
            r.createCell(3).setCellValue(2); // PL7 (KB-PL7-4) : un motif exige un écart (3 = théorique serait refusé en 400)
            r.createCell(4).setCellValue("casse");
            l.add("titre en minuscules, entetes deplaces (ligne 11), colonnes dans un autre ordre -> " + resume(importer(inv, ecrireXlsx(wb))));
        }
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("Sans titre");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("Code");
            h.createCell(1).setCellValue("Quantité comptée");
            Row r = s.createRow(1);
            r.createCell(0).setCellValue("A2");
            r.createCell(1).setCellValue(2);
            l.add("sans titre (aucune verification de reference) -> " + resume(importer(inv, ecrireXlsx(wb))));
        }
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("Tard");
            for (int i = 0; i < 25; i++) s.createRow(i).createCell(0).setCellValue("x");
            Row h = s.createRow(25);
            h.createCell(0).setCellValue("Code");
            h.createCell(1).setCellValue("Quantité comptée");
            s.createRow(26).createCell(0).setCellValue("A4");
            l.add("entetes apres la ligne 21 -> " + resume(importer(inv, ecrireXlsx(wb))));
        }
        try (Workbook wb = new XSSFWorkbook()) {
            wb.createSheet("Vide premier");
            Sheet s = wb.createSheet("Bonne");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("Code");
            h.createCell(1).setCellValue("Quantité comptée");
            s.createRow(1).createCell(0).setCellValue("A5");
            l.add("seule la premiere feuille est lue (premiere vide) -> " + resume(importer(inv, ecrireXlsx(wb))));
        }
        try (Workbook wb = new HSSFWorkbook()) {
            Sheet s = wb.createSheet("Ancien");
            s.createRow(0).createCell(1).setCellValue("Inventaire " + ref + " du 01/01/2026");
            Row h = s.createRow(1);
            h.createCell(0).setCellValue("Code");
            h.createCell(1).setCellValue("Quantité comptée");
            Row r = s.createRow(2);
            r.createCell(0).setCellValue("A6");
            r.createCell(1).setCellValue(9);
            l.add("classeur .xls reel -> " + resume(importer(inv, ecrireXlsx(wb))));
        }
        goldenLignes("import_pl7_formats_et_entetes_nouveau_code",l);
        goldenLignesSql("import_pl7_formats_et_entetes_lignes_nouveau_code");
    }

    private String resume(Resp r) {
        if (r.status() == 200 && r.raw() != null && r.raw().startsWith("{\"inventaire\"")) {
            JsonNode n = mapper.readTree(r.raw());
            return "200 comptees=" + n.path("inventaire").path("comptees").asString() + " ecarts=" + n.path("inventaire").path("ecarts").asString()
                    + " misesAJour=" + n.path("lignesMisesAJour").asString() + " inchangees=" + n.path("lignesInchangees").asString()
                    + " vides=" + n.path("lignesVides").asString() + " anomalies=" + masque(n.path("anomalies").toString());
        }
        return r.status() + " " + r.contentType() + " " + masque(r.raw() == null ? "" : r.raw().replaceAll("\\s+", " "));
    }

    @Test
    void l_import_refuse_les_fichiers_illisibles_trop_gros_les_roles_en_lecture_seule_et_les_autres_centres() throws Exception {
        catalogue();
        UUID inv = ouvertOk(null);
        List<String> l = new ArrayList<>();
        byte[] gros = new byte[5 * 1024 * 1024 + 1];
        gros[0] = 'P';
        gros[1] = 'K';
        l.add("5 Mo + 1 octet -> " + resume(importer(inv, gros)));
        byte[] pkBidon = new byte[]{'P', 'K', 3, 4, 0, 0, 0, 0, 0, 0};
        l.add("zip bidon -> " + resume(importer(inv, pkBidon)));
        byte[] ole = new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1, 0, 0};
        l.add("OLE2 bidon -> " + resume(importer(inv, ole)));
        l.add("texte de 3 octets -> " + resume(importer(inv, "abc".getBytes())));
        l.add("sans partie file -> " + sommaire(new Bin(mvc.perform(multipart(java.net.URI.create(INV + "/" + inv + "/feuille-comptage")).with(adminA()))
                .andReturn().getResponse().getStatus(), "n/a", null, new byte[0])));
        byte[] valide;
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("Ok");
            // PL7 (KB-PL7-4) : le titre de référence est exigé ; sans lui l'import serait refusé en 400
            s.createRow(0).createCell(1).setCellValue("Inventaire " + reference(inv) + " du 01/01/2026");
            Row h = s.createRow(1);
            h.createCell(0).setCellValue("Code");
            h.createCell(1).setCellValue("Quantité comptée");
            Row r = s.createRow(2);
            r.createCell(0).setCellValue("A2");
            r.createCell(1).setCellValue(1);
            valide = ecrireXlsx(wb);
        }
        for (String role : List.of("PHARMACIEN", "INFIRMIER", "DIRECTEUR_ADMINISTRATIF", "MEDECIN", "SECRETAIRE")) {
            Resp r = importer(inv, valide, as(CENTRE_A, role));
            l.add("import par " + role + " -> " + r.status() + " " + r.contentType());
        }
        l.add("import par un ADMIN d'un autre centre -> " + resume(importer(inv, valide, adminB())));
        l.add("import anonyme -> " + resume(importer(inv, valide, request -> request)));
        goldenLignes("import_pl7_refus_nouveau_code",l);
        goldenLignesSql("import_pl7_refus_lignes");
    }

    // ------------------------------------------------------------------ audit

    @Test
    void ni_le_proces_verbal_ni_le_telechargement_ne_sont_journalises_mais_l_import_l_est() throws Exception {
        catalogue();
        UUID inv = ouvertOk(null);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(roleScopeFilter, filtreDAudit).build();
        ecrivainDAudit.flush();
        jdbc.update("DELETE FROM audit_log WHERE center_id IN (?, ?)", CENTRE_A, CENTRE_B);
        String avant = jdbc.queryForList("SELECT article_code, numero_lot, quantite_comptee FROM inventaire_lignes ORDER BY article_code, numero_lot")
                .toString();

        bin(INV + "/" + inv + "/rapport");
        bin(INV + "/" + inv + "/feuille-comptage");
        bin(INV + "/" + inv + "/rapport", as(CENTRE_A, "MEDECIN"));
        bin(INV + "/" + UUID.randomUUID() + "/rapport");
        ecrivainDAudit.flush();
        List<String> l = new ArrayList<>();
        l.add("GET rapport suivi = " + AuditActionResolver.isTracked("GET", INV + "/{id}/rapport"));
        l.add("GET feuille-comptage suivi = " + AuditActionResolver.isTracked("GET", INV + "/{id}/feuille-comptage"));
        l.add("POST feuille-comptage suivi = " + AuditActionResolver.isTracked("POST", INV + "/{id}/feuille-comptage"));
        l.add("lignes de journal apres 2 impressions et 1 telechargement = "
                + jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE center_id IN (?, ?)", Integer.class, CENTRE_A, CENTRE_B));
        l.add("lignes inchangees = " + avant.equals(jdbc.queryForList(
                "SELECT article_code, numero_lot, quantite_comptee FROM inventaire_lignes ORDER BY article_code, numero_lot").toString()));

        byte[] valide;
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("Ok");
            // PL7 (KB-PL7-4) : le titre de référence est exigé ; sans lui l'import serait refusé en 400
            s.createRow(0).createCell(1).setCellValue("Inventaire " + reference(inv) + " du 01/01/2026");
            Row h = s.createRow(1);
            h.createCell(0).setCellValue("Code");
            h.createCell(1).setCellValue("Quantité comptée");
            Row r = s.createRow(2);
            r.createCell(0).setCellValue("A2");
            r.createCell(1).setCellValue(1);
            valide = ecrireXlsx(wb);
        }
        l.add("import -> " + importer(inv, valide).status());
        ecrivainDAudit.flush();
        for (Map<String, Object> row : jdbc.queryForList("SELECT action_code, http_method, route_template, status_code, COUNT(*) AS n "
                + "FROM audit_log WHERE center_id IN (?, ?) GROUP BY action_code, http_method, route_template, status_code "
                + "ORDER BY route_template, http_method, status_code", CENTRE_A, CENTRE_B)) {
            l.add("journal : " + masque(row.toString()));
        }
        goldenLignes("pv_pl7_audit_nouveau_code",l);
    }
}
