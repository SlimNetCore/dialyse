package com.hemodialyse.backend.characterization.documents.doc5j;

import com.hemodialyse.backend.characterization.dossiermedical.dm2.PrescriptionsCharacterizationSupport;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * DOC5j : filet de rendu du procès-verbal d'inventaire de stock ({@code INVENTAIRE_STOCK}), TOUT le texte du PDF, avant la conversion du
 * gabarit {@code inventaire_stock.jrxml} de son SQL embarqué (lignes de {@code inventaire_lignes}, triées par libellé sans casse, identifiant
 * d'article, péremption, position) vers un jeu de données fourni par le Stock. Deux voies : le procès-verbal du Stock
 * ({@code GET /api/v1/stock/inventaires/{id}/rapport}) et l'impression directe de Documents par type et par modèle (l'inventaire est alors
 * désigné par le paramètre {@code INVENTAIRE_ID}). DONNÉES FICTIVES ; date d'édition masquée.
 */
class InventaireStock5jCharacterizationIntegrationTest extends PrescriptionsCharacterizationSupport {

    private static final UUID MODELE_A = UUID.fromString("99997800-0000-0000-0000-0000000f5a9a");
    private static final UUID MODELE_B = UUID.fromString("99997800-0000-0000-0000-0000000f5b9a");
    private static final OffsetDateTime OUVERTURE = OffsetDateTime.of(2026, 3, 10, 8, 30, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime CLOTURE = OffsetDateTime.of(2026, 3, 11, 17, 45, 0, 0, ZoneOffset.UTC);

    @Override
    protected String dossier() {
        return "doc5j";
    }

    @BeforeEach
    void nettoyerLInventaire() {
        retirer();
    }

    @AfterEach
    void retirer() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM inventaire_lignes WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM inventaires WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM modele_document WHERE center_id = ? AND type_document = 'INVENTAIRE_STOCK'", centre);
        }
    }

    private void modeles() {
        for (UUID[] m : new UUID[][]{{MODELE_A, CENTRE_A}, {MODELE_B, CENTRE_B}}) {
            jdbc.update("INSERT INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, format_impression, "
                            + "description, active, created_at) VALUES (?,?,?,?,?,?,?,?,?,?)", m[0], m[1], "INVENTAIRE_STOCK_5J", "Document 5j",
                    "INVENTAIRE_STOCK", "reports/inventaire_stock.jrxml", "PDF", "Document fictif 5j", true, CREATION_FIXE);
        }
    }

    private UUID inventaire(UUID centre, String alias, String statut) {
        UUID id = idStable("inventaire5j", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        boolean ferme = !"EN_COURS".equals(statut);
        jdbc.update("INSERT INTO inventaires (id, center_id, reference, date_inventaire, statut, commentaire, created_by, created_at, "
                        + "closed_by, closed_at) VALUES (?,?,?,?,?,?,?,?,?,?)", id, centre, "INV-5J-" + alias, java.sql.Date.valueOf("2026-03-10"),
                statut, "Commentaire fictif", "Ouvreur", OUVERTURE, ferme ? "Cloturant" : null, ferme ? CLOTURE : null);
        return id;
    }

    private int position = 0;

    /** Une ligne ; {@code comptee} nulle = non comptée ; {@code pmp} nul = PMP absent. */
    private void ligne(UUID inventaire, UUID centre, String articleId, String code, String libelle, String lot, String peremption,
                       String theorique, String comptee, String pmp, String motif, String comptePar, boolean ajoutee) {
        jdbc.update("INSERT INTO inventaire_lignes (id, inventaire_id, center_id, position_ligne, article_id, article_code, article_libelle, "
                        + "unite, lot_id, numero_lot, date_peremption, quantite_theorique, quantite_comptee, pmp, motif_ecart, compte_par, "
                        + "compte_le, ajoutee) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", UUID.randomUUID(), inventaire, centre, position++,
                UUID.fromString(articleId), code, libelle, code == null ? null : "boîte", null, lot,
                peremption == null ? null : java.sql.Date.valueOf(peremption), new BigDecimal(theorique),
                comptee == null ? null : new BigDecimal(comptee), pmp == null ? null : new BigDecimal(pmp), motif, comptePar,
                comptee == null ? null : OUVERTURE, ajoutee);
    }

    /** Un jeu aux ordres délicats : libellés égaux à la casse près, identifiants d'article de bit de poids fort différent, valeurs absentes. */
    private void jeuRiche(UUID inventaire, UUID centre) {
        position = 0;
        String haut = "f0000000-0000-0000-0000-000000000001";
        String milieu = "7fffffff-0000-0000-0000-000000000002";
        String bas = "10000000-0000-0000-0000-000000000003";
        ligne(inventaire, centre, haut, "EGAL-H", "Compresse stérile", "LH-1", "2099-01-01", "10", "8", "1500.00", "CASSE", "Alice", false);
        ligne(inventaire, centre, bas, "EGAL-B", "COMPRESSE STÉRILE", "LB-1", "2099-01-01", "4", "4", "20.50", null, "Bob", false);
        ligne(inventaire, centre, milieu, "EGAL-M", "compresse stérile", null, null, "7.250", "9.500", "3.3333", "RETOUR", null, false);
        ligne(inventaire, centre, bas, "EGAL-B", "COMPRESSE STÉRILE", "LB-0", "2020-01-01", "6", null, "20.50", null, null, false);
        ligne(inventaire, centre, bas, "EGAL-B", "COMPRESSE STÉRILE", null, null, "1", "0", "20.50", "PEREMPTION", "Bob", false);
        ligne(inventaire, centre, bas, "EGAL-B", "COMPRESSE STÉRILE", "LB-X", "2099-01-01", "2", "3", "20.50", "Autre motif libre", "Bob", true);
        ligne(inventaire, centre, "00000000-0000-0000-0000-0000000000a1", "ZEB", "Zèbre", "Z1", "2030-06-15", "100", "99", "0.005", "PERTE", "Chloé", false);
        ligne(inventaire, centre, "00000000-0000-0000-0000-0000000000a2", "ALP", "alpha", "A1", "2025-12-31", "5", "6", null, "ERREUR_SAISIE", "Dan", false);
        ligne(inventaire, centre, "00000000-0000-0000-0000-0000000000a3", "ETH", "Éther", "E1", "2026-04-20", "3", "2", "99999999.99", "NON_ENREGISTRE", "Éva", false);
        ligne(inventaire, centre, "00000000-0000-0000-0000-0000000000a4", "SANS", null, "S1", "2031-01-01", "2", "2", "1.00", null, "Fred", false);
        ligne(inventaire, centre, "00000000-0000-0000-0000-0000000000a5", "AUT", "Autre <b>& \"spécial\"</b> $P{INV_DATE}", "A5", "2031-01-01",
                "12", "10", "2.00", "AUTRE", "Gaël", false);
        ligne(inventaire, centre, "00000000-0000-0000-0000-0000000000a6", "JUST", "Écart sans motif", "J1", "2031-01-01", "12", "13", "2.00",
                null, "Hugo", false);
        ligne(inventaire, centre, "00000000-0000-0000-0000-0000000000a6", "JUST", "Écart sans motif", "J0", "2031-01-01", "1", "1", "2.00",
                null, "Hugo", false);
    }

    private Map<String, Object> resume(MockHttpServletResponse r) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", r.getStatus());
        String type = r.getContentType() == null ? null : r.getContentType().split(";")[0];
        m.put("contentType", type);
        byte[] octets = r.getContentAsByteArray();
        if ("application/pdf".equals(type)) {
            try (PDDocument doc = PDDocument.load(octets)) {
                m.put("pages", doc.getNumberOfPages());
                List<String> lignes = new ArrayList<>(new PDFTextStripper().getText(doc).replace("\r\n", "\n").lines().toList());
                lignes.replaceAll(l -> l.replaceAll("Édité le \\d{2}/\\d{2}/\\d{4} à \\d{2}:\\d{2}", "Édité le <HORODATAGE_EDITION>")
                        .replaceAll("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "<UUID>"));
                m.put("texte", lignes);
            }
        } else if (type != null && type.startsWith("text/")) {
            m.put("texte", new String(octets, StandardCharsets.UTF_8));
        } else {
            m.put("octets", octets.length > 0);
        }
        return m;
    }

    private MockHttpServletResponse rapport(UUID centre, UUID inventaire) throws Exception {
        return mvc.perform(get(java.net.URI.create("/api/v1/stock/inventaires/" + inventaire + "/rapport")).with(as(centre, "ADMIN")))
                .andReturn().getResponse();
    }

    private MockHttpServletResponse parType(UUID centre, String params) throws Exception {
        return mvc.perform(post("/api/v1/documents/print").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"centerId\":\"" + centre + "\",\"typeDocument\":\"INVENTAIRE_STOCK\",\"params\":{" + params + "}}")
                .with(as(centre, "MEDECIN"))).andReturn().getResponse();
    }

    private MockHttpServletResponse parModele(UUID modele, UUID centre, String params) throws Exception {
        return mvc.perform(post("/api/v1/documents/print/" + modele).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"centerId\":\"" + centre + "\",\"params\":{" + params + "}}").with(as(centre, "MEDECIN"))).andReturn().getResponse();
    }

    private static String param(String nom, Object valeur) {
        return "\"" + nom + "\":\"" + valeur + "\"";
    }

    @Test
    void le_proces_verbal_du_stock_s_imprime_dans_l_ordre_des_libelles_par_article_et_par_lot() throws Exception {
        UUID cloture = inventaire(CENTRE_A, "CLOTURE", "CLOTURE");
        jeuRiche(cloture, CENTRE_A);
        UUID enCours = inventaire(CENTRE_A, "EN_COURS", "EN_COURS");
        jeuRiche(enCours, CENTRE_A);
        UUID annule = inventaire(CENTRE_A, "ANNULE", "ANNULE");
        jeuRiche(annule, CENTRE_A);
        UUID vide = inventaire(CENTRE_A, "VIDE", "EN_COURS");
        UUID autreCentre = inventaire(CENTRE_B, "AUTRE_CENTRE", "CLOTURE");
        jeuRiche(autreCentre, CENTRE_B);

        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("inventaire cloture", resume(rapport(CENTRE_A, cloture)));
        cas.put("inventaire en cours", resume(rapport(CENTRE_A, enCours)));
        cas.put("inventaire annule", resume(rapport(CENTRE_A, annule)));
        cas.put("inventaire sans ligne", resume(rapport(CENTRE_A, vide)));
        cas.put("inventaire d'un autre centre", resume(rapport(CENTRE_A, autreCentre)));
        arbre("inventaire_doc5j_pv_texte", cas);
    }

    @Test
    void l_impression_directe_par_type_et_par_modele_lit_l_inventaire_designe_par_son_identifiant() throws Exception {
        modeles();
        UUID inventaireA = inventaire(CENTRE_A, "INV_A", "CLOTURE");
        jeuRiche(inventaireA, CENTRE_A);
        UUID inventaireB = inventaire(CENTRE_B, "INV_B", "EN_COURS");
        jeuRiche(inventaireB, CENTRE_B);

        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("par type, inventaire du centre", resume(parType(CENTRE_A, param("INVENTAIRE_ID", inventaireA))));
        cas.put("par modele, inventaire du centre", resume(parModele(MODELE_A, CENTRE_A, param("INVENTAIRE_ID", inventaireA))));
        cas.put("par type, inventaire d'un autre centre", resume(parType(CENTRE_A, param("INVENTAIRE_ID", inventaireB))));
        cas.put("par modele, inventaire d'un autre centre", resume(parModele(MODELE_A, CENTRE_A, param("INVENTAIRE_ID", inventaireB))));
        cas.put("par modele, centre B et son inventaire", resume(parModele(MODELE_B, CENTRE_B, param("INVENTAIRE_ID", inventaireB))));
        cas.put("par type, inventaire inconnu", resume(parType(CENTRE_A, param("INVENTAIRE_ID", idStable("inventaire5j", "INCONNU")))));
        cas.put("par type, aucun identifiant", resume(parType(CENTRE_A, "")));
        cas.put("par type, identifiant vide", resume(parType(CENTRE_A, param("INVENTAIRE_ID", ""))));
        cas.put("par type, identifiant illisible", resume(parType(CENTRE_A, param("INVENTAIRE_ID", "pas-un-uuid"))));
        cas.put("par modele, aucun identifiant", resume(parModele(MODELE_A, CENTRE_A, "")));
        cas.put("par modele, identifiant vide", resume(parModele(MODELE_A, CENTRE_A, param("INVENTAIRE_ID", ""))));
        cas.put("par modele, identifiant illisible", resume(parModele(MODELE_A, CENTRE_A, param("INVENTAIRE_ID", "pas-un-uuid"))));
        arbre("inventaire_doc5j_direct_texte", cas);
    }
}
