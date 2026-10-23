package com.hemodialyse.backend.characterization.stock.referentiel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Filet de caractérisation S3 — bons de commande : {@code POST|GET /api/v1/stock/bons-commande},
 * {@code PUT /{id}}, {@code POST /{id}/valider}, {@code GET /{id}} (RG-STK-005, 010 à 012). Point d'observation : HTTP et
 * état SQL de {@code bons_commande}, {@code bons_commande_lignes} et du compteur {@code SEQ_BC} d'{@code app_settings}.
 * Écrit sur le legacy ({@code BonCommandeRestController}, {@code BonCommandeService}, {@code BonCommande},
 * {@code BonCommandeRepositoryAdapter}, {@code StockSequenceAdapter}).
 */
class BonsDeCommandeCharacterizationIntegrationTest extends ReferentielStockCharacterizationSupport {

    private static final String BONS = "/api/v1/stock/bons-commande";

    private static String ligne(UUID article, String quantite, String prix) {
        return "{'articleId':" + (article == null ? "null" : "'" + article + "'")
                + (quantite == null ? "" : ",'quantite':" + quantite)
                + (prix == null ? "" : ",'prixUnitaire':" + prix) + "}";
    }

    private static String creation(UUID centre, UUID fournisseur, String userId, String... lignes) {
        return json("{" + (centre == null ? "" : "'centerId':'" + centre + "',")
                + (fournisseur == null ? "" : "'fournisseurId':'" + fournisseur + "',")
                + (userId == null ? "" : "'userId':'" + userId + "',")
                + "'lignes':[" + String.join(",", lignes) + "]}");
    }

    private static String remplacement(UUID centre, String... lignes) {
        return json("{" + (centre == null ? "" : "'centerId':'" + centre + "',")
                + "'lignes':[" + String.join(",", lignes) + "]}");
    }

    private static String centre(UUID centre) {
        return json("{" + (centre == null ? "" : "'centerId':'" + centre + "'") + "}");
    }

    @Override
    protected String theme() {
        return "bons-commande";
    }

    // ------------------------------------------------------------------ données

    @BeforeEach
    @AfterEach
    void viderLesBons() {
        for (UUID c : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM bons_commande_lignes WHERE bon_commande_id IN "
                    + "(SELECT id FROM bons_commande WHERE center_id = ?)", c);
            jdbc.update("DELETE FROM bons_commande WHERE center_id = ?", c);
            jdbc.update("DELETE FROM app_settings WHERE center_id = ? AND cle = 'SEQ_BC'", c);
        }
    }

    private Resp creer(String corps, RequestPostProcessor qui) throws Exception {
        return send(post(java.net.URI.create(BONS)).contentType(MediaType.APPLICATION_JSON).content(corps).with(qui));
    }

    private Resp remplacer(UUID id, String corps, RequestPostProcessor qui) throws Exception {
        return send(put(java.net.URI.create(BONS + "/" + id)).contentType(MediaType.APPLICATION_JSON).content(corps).with(qui));
    }

    private Resp valider(UUID id, String corps, RequestPostProcessor qui) throws Exception {
        return send(post(java.net.URI.create(BONS + "/" + id + "/valider")).contentType(MediaType.APPLICATION_JSON)
                .content(corps).with(qui));
    }

    private Resp lire(String chemin, RequestPostProcessor qui) throws Exception {
        return send(get(java.net.URI.create(BONS + chemin)).with(qui));
    }

    private UUID idDe(Resp r, String alias) {
        UUID id = UUID.fromString(json(r).path("id").asString());
        aliasNommes.put(id.toString(), "<" + alias + ">");
        return id;
    }

    private void goldenBons(String name, UUID centre) {
        goldenSql(name, "SELECT reference, statut, created_by, (fournisseur_id IS NULL) AS fournisseur_nul, "
                + "(created_at IS NULL) AS created_at_nul, "
                + "(SELECT COUNT(*) FROM bons_commande_lignes l WHERE l.bon_commande_id = b.id) AS nb_lignes "
                + "FROM bons_commande b WHERE b.center_id = '" + centre + "' ORDER BY reference");
    }

    private void goldenLignes(String name, UUID centre) {
        goldenSql(name, "SELECT b.reference, l.article_id, l.quantite, l.prix_unitaire FROM bons_commande_lignes l "
                + "JOIN bons_commande b ON b.id = l.bon_commande_id WHERE b.center_id = '" + centre
                + "' ORDER BY b.reference, l.quantite, l.prix_unitaire");
    }

    private void goldenCompteur(String name, UUID centre) {
        goldenSql(name, "SELECT prefixe, dernier_compteur FROM app_settings WHERE center_id = '" + centre
                + "' AND cle = 'SEQ_BC'");
    }

    // ------------------------------------------------------------------ création

    @Test
    void la_creation_produit_un_brouillon_reference_et_total() throws Exception {
        UUID a1 = article(CENTRE_A, "ART_1", "A1", "Article 1", null, null, true, null, "0", "0");
        UUID a2 = article(CENTRE_A, "ART_2", "A2", "Article 2", null, null, true, null, "0", "0");
        UUID f = fournisseur(CENTRE_A, "FOUR_1", "Fournisseur 1", true);
        golden("creation_nominale_200", creer(creation(CENTRE_A, f, "pharma-1",
                ligne(a1, "10", "2.50"), ligne(a2, "3", "1.333")), adminA()));
        goldenBons("creation_nominale_etat_base", CENTRE_A);
        goldenLignes("creation_nominale_lignes", CENTRE_A);
        goldenCompteur("creation_nominale_compteur", CENTRE_A);
    }

    @Test
    void la_reference_est_sequentielle_par_centre() throws Exception {
        UUID a1 = article(CENTRE_A, "ART_1", "A1", "Article 1", null, null, true, null, "0", "0");
        UUID b1 = article(CENTRE_B, "ART_B1", "B1", "Article B1", null, null, true, null, "0", "0");
        golden("sequence_1_centre_a", creer(creation(CENTRE_A, null, null, ligne(a1, "1", "1")), adminA()));
        golden("sequence_2_centre_a", creer(creation(CENTRE_A, null, null, ligne(a1, "1", "1")), adminA()));
        golden("sequence_1_centre_b", creer(creation(CENTRE_B, null, null, ligne(b1, "1", "1")), adminB()));
        golden("sequence_3_centre_a", creer(creation(CENTRE_A, null, null), adminA()));
        goldenBons("sequence_etat_base_centre_a", CENTRE_A);
        goldenCompteur("sequence_compteur_centre_a", CENTRE_A);
        goldenCompteur("sequence_compteur_centre_b", CENTRE_B);
    }

    @Test
    void l_auteur_par_defaut_est_system_et_les_lignes_incompletes_sont_traitees_comme_la_base_le_permet() throws Exception {
        UUID a1 = article(CENTRE_A, "ART_1", "A1", "Article 1", null, null, true, null, "0", "0");
        golden("creation_sans_auteur_200", creer(creation(CENTRE_A, null, null, ligne(a1, "4", "2")), adminA()));
        golden("creation_ligne_sans_prix_200", creer(creation(CENTRE_A, null, "u",
                ligne(a1, "4", null), ligne(a1, "2", "2")), adminA()));
        golden("creation_ligne_sans_quantite_400", creer(creation(CENTRE_A, null, "u", ligne(a1, null, "2")), adminA()));
        golden("creation_sans_ligne_200", creer(creation(CENTRE_A, null, "u"), adminA()));
        golden("creation_lignes_absentes_200", creer(json("{'centerId':'" + CENTRE_A + "'}"), adminA()));
        goldenBons("auteur_et_lignes_etat_base", CENTRE_A);
    }

    @Test
    void les_montants_negatifs_et_les_cas_limites_de_la_creation() throws Exception {
        UUID a1 = article(CENTRE_A, "ART_1", "A1", "Article 1", null, null, true, null, "0", "0");
        golden("creation_prix_negatif", creer(creation(CENTRE_A, null, null, ligne(a1, "2", "-1")), adminA()));
        golden("creation_quantite_negative", creer(creation(CENTRE_A, null, null, ligne(a1, "-2", "1")), adminA()));
        golden("creation_quantite_nulle", creer(creation(CENTRE_A, null, null, ligne(a1, "0", "1")), adminA()));
        golden("creation_article_inconnu", creer(creation(CENTRE_A, null, null,
                ligne(UUID.fromString("99997500-0000-0000-0001-999999999999"), "1", "1")), adminA()));
        golden("creation_ligne_sans_article", creer(creation(CENTRE_A, null, null, ligne(null, "1", "1")), adminA()));
        golden("creation_fournisseur_inconnu", creer(creation(CENTRE_A,
                UUID.fromString("99997500-0000-0000-0003-999999999999"), null, ligne(a1, "1", "1")), adminA()));
        goldenBons("limites_etat_base", CENTRE_A);
        goldenLignes("limites_lignes", CENTRE_A);
    }

    @Test
    void les_refus_de_validation_du_corps_de_creation() throws Exception {
        golden("refus_centre_absent", creer(json("{'lignes':[]}"), adminA()));
        golden("refus_corps_absent", send(post(java.net.URI.create(BONS)).contentType(MediaType.APPLICATION_JSON).with(adminA())));
        golden("refus_json_illisible", creer("{pas du json", adminA()));
        golden("refus_centre_illisible", creer(json("{'centerId':'pas-un-uuid'}"), adminA()));
        golden("sans_garde_de_centre_autre_centre", creer(creation(CENTRE_B, null, null), adminA()));
        goldenBons("sans_garde_de_centre_etat_base_centre_a", CENTRE_A);
        goldenBons("sans_garde_de_centre_etat_base_centre_b", CENTRE_B);
    }

    // ------------------------------------------------------------------ remplacement des lignes

    @Test
    void le_remplacement_des_lignes_en_brouillon() throws Exception {
        UUID a1 = article(CENTRE_A, "ART_1", "A1", "Article 1", null, null, true, null, "0", "0");
        UUID a2 = article(CENTRE_A, "ART_2", "A2", "Article 2", null, null, true, null, "0", "0");
        UUID bon = idDe(creer(creation(CENTRE_A, null, "u", ligne(a1, "1", "1")), adminA()), "BON_A");
        golden("remplacement_200", remplacer(bon, remplacement(CENTRE_A, ligne(a2, "5", "4"), ligne(a1, "2", "0.5")), adminA()));
        goldenLignes("remplacement_lignes", CENTRE_A);
        golden("remplacement_par_aucune_ligne_200", remplacer(bon, remplacement(CENTRE_A), adminA()));
        golden("remplacement_lignes_absentes_200", remplacer(bon, json("{'centerId':'" + CENTRE_A + "'}"), adminA()));
        goldenBons("remplacement_etat_base", CENTRE_A);
        goldenLignes("remplacement_vide_lignes", CENTRE_A);
    }

    @Test
    void le_remplacement_conserve_la_reference_et_le_statut_et_refuse_les_cas_invalides() throws Exception {
        UUID a1 = article(CENTRE_A, "ART_1", "A1", "Article 1", null, null, true, null, "0", "0");
        UUID bon = idDe(creer(creation(CENTRE_A, null, "u", ligne(a1, "1", "1")), adminA()), "BON_A");
        golden("refus_remplacement_inconnu", remplacer(UUID.fromString("99997500-0000-0000-0002-000000000001"),
                remplacement(CENTRE_A, ligne(a1, "1", "1")), adminA()));
        golden("refus_remplacement_centre_absent", remplacer(bon, json("{'lignes':[]}"), adminA()));
        golden("refus_remplacement_identifiant_illisible", send(put(java.net.URI.create(BONS + "/pas-un-uuid"))
                .contentType(MediaType.APPLICATION_JSON).content(remplacement(CENTRE_A)).with(adminA())));
        golden("sans_garde_de_centre_remplacement_autre_centre", remplacer(bon, remplacement(CENTRE_B), adminA()));
        goldenLignes("remplacement_refus_lignes_inchangees", CENTRE_A);
    }

    @Test
    void le_remplacement_est_refuse_hors_brouillon_ou_sur_un_bon_d_un_autre_centre() throws Exception {
        UUID a1 = article(CENTRE_A, "ART_1", "A1", "Article 1", null, null, true, null, "0", "0");
        UUID b1 = article(CENTRE_B, "ART_B1", "B1", "Article B1", null, null, true, null, "0", "0");
        UUID bon = idDe(creer(creation(CENTRE_A, null, "u", ligne(a1, "1", "1")), adminA()), "BON_A");
        UUID bonB = idDe(creer(creation(CENTRE_B, null, "u", ligne(b1, "1", "1")), adminB()), "BON_B");
        golden("refus_remplacement_bon_d_un_autre_centre", remplacer(bonB, remplacement(CENTRE_A, ligne(a1, "9", "9")), adminA()));
        assertEquals(200, valider(bon, centre(CENTRE_A), adminA()).status());
        golden("remplacement_apres_validation", remplacer(bon, remplacement(CENTRE_A, ligne(a1, "9", "9")), adminA()));
        jdbc.update("UPDATE bons_commande SET statut = 'RECU' WHERE id = ?", bon);
        golden("remplacement_apres_reception", remplacer(bon, remplacement(CENTRE_A, ligne(a1, "7", "7")), adminA()));
        goldenBons("remplacement_hors_brouillon_etat_base_centre_a", CENTRE_A);
        goldenLignes("remplacement_hors_brouillon_lignes_centre_a", CENTRE_A);
        goldenLignes("remplacement_hors_brouillon_lignes_centre_b", CENTRE_B);
    }

    // ------------------------------------------------------------------ validation

    @Test
    void la_validation_passe_un_brouillon_avec_lignes_a_valide() throws Exception {
        UUID a1 = article(CENTRE_A, "ART_1", "A1", "Article 1", null, null, true, null, "0", "0");
        UUID bon = idDe(creer(creation(CENTRE_A, null, "u", ligne(a1, "2", "3")), adminA()), "BON_A");
        golden("validation_200", valider(bon, centre(CENTRE_A), adminA()));
        goldenBons("validation_etat_base", CENTRE_A);
        golden("refus_seconde_validation", valider(bon, centre(CENTRE_A), adminA()));
        jdbc.update("UPDATE bons_commande SET statut = 'RECU' WHERE id = ?", bon);
        golden("refus_validation_d_un_bon_recu", valider(bon, centre(CENTRE_A), adminA()));
        goldenBons("validation_refus_etat_base", CENTRE_A);
    }

    @Test
    void la_validation_sans_ligne_est_refusee() throws Exception {
        UUID bon = idDe(creer(creation(CENTRE_A, null, "u"), adminA()), "BON_A");
        golden("refus_validation_sans_ligne", valider(bon, centre(CENTRE_A), adminA()));
        goldenBons("validation_sans_ligne_etat_base", CENTRE_A);
    }

    @Test
    void les_refus_de_la_validation() throws Exception {
        UUID a1 = article(CENTRE_A, "ART_1", "A1", "Article 1", null, null, true, null, "0", "0");
        UUID b1 = article(CENTRE_B, "ART_B1", "B1", "Article B1", null, null, true, null, "0", "0");
        UUID bon = idDe(creer(creation(CENTRE_A, null, "u", ligne(a1, "1", "1")), adminA()), "BON_A");
        UUID bonB = idDe(creer(creation(CENTRE_B, null, "u", ligne(b1, "1", "1")), adminB()), "BON_B");
        golden("refus_validation_inconnu", valider(UUID.fromString("99997500-0000-0000-0002-000000000001"),
                centre(CENTRE_A), adminA()));
        golden("refus_validation_bon_d_un_autre_centre", valider(bonB, centre(CENTRE_A), adminA()));
        golden("sans_garde_de_centre_validation_autre_centre", valider(bonB, centre(CENTRE_B), adminA()));
        golden("refus_validation_centre_absent", valider(bon, json("{}"), adminA()));
        golden("refus_validation_corps_absent", send(post(java.net.URI.create(BONS + "/" + bon + "/valider"))
                .contentType(MediaType.APPLICATION_JSON).with(adminA())));
        goldenBons("validation_refus_centre_a", CENTRE_A);
        goldenBons("validation_refus_centre_b", CENTRE_B);
    }

    // ------------------------------------------------------------------ lecture et liste

    @Test
    void la_lecture_et_la_liste() throws Exception {
        UUID a1 = article(CENTRE_A, "ART_1", "A1", "Article 1", null, null, true, null, "0", "0");
        UUID b1 = article(CENTRE_B, "ART_B1", "B1", "Article B1", null, null, true, null, "0", "0");
        UUID f = fournisseur(CENTRE_A, "FOUR_1", "Fournisseur 1", true);
        UUID bon1 = idDe(creer(creation(CENTRE_A, f, "u", ligne(a1, "2", "3")), adminA()), "BON_1");
        jdbc.update("UPDATE bons_commande SET created_at = TIMESTAMP WITH TIME ZONE '2026-01-01 10:00:00+00' WHERE id = ?", bon1);
        UUID bon2 = idDe(creer(creation(CENTRE_A, null, "u"), adminA()), "BON_2");
        jdbc.update("UPDATE bons_commande SET created_at = TIMESTAMP WITH TIME ZONE '2026-02-01 10:00:00+00' WHERE id = ?", bon2);
        UUID bonB = idDe(creer(creation(CENTRE_B, null, "u", ligne(b1, "1", "1")), adminB()), "BON_B");
        golden("lecture_200", lire("/" + bon1 + "?centerId=" + CENTRE_A, adminA()));
        golden("liste_centre_a_du_plus_recent_au_plus_ancien", lire("?centerId=" + CENTRE_A, adminA()));
        golden("liste_centre_b", lire("?centerId=" + CENTRE_B, adminB()));
        golden("refus_lecture_inconnu", lire("/" + UUID.fromString("99997500-0000-0000-0002-000000000001")
                + "?centerId=" + CENTRE_A, adminA()));
        golden("refus_lecture_bon_d_un_autre_centre", lire("/" + bonB + "?centerId=" + CENTRE_A, adminA()));
        golden("sans_garde_de_centre_lecture_autre_centre", lire("/" + bonB + "?centerId=" + CENTRE_B, adminA()));
        golden("refus_lecture_sans_centre", lire("/" + bon1, adminA()));
        golden("refus_liste_sans_centre", lire("", adminA()));
        golden("refus_liste_centre_illisible", lire("?centerId=pas-un-uuid", adminA()));
        golden("refus_lecture_identifiant_illisible", lire("/pas-un-uuid?centerId=" + CENTRE_A, adminA()));
        golden("sans_garde_de_centre_liste_autre_centre", lire("?centerId=" + CENTRE_B, adminA()));
    }

    // ------------------------------------------------------------------ rôles

    @Test
    void les_roles_d_ecriture_et_de_lecture() throws Exception {
        UUID a1 = article(CENTRE_A, "ART_1", "A1", "Article 1", null, null, true, null, "0", "0");
        UUID bon = idDe(creer(creation(CENTRE_A, null, "u", ligne(a1, "1", "1")), adminA()), "BON_A");
        golden("pharmacien_cree_201_ou_200", creer(creation(CENTRE_A, null, "p", ligne(a1, "1", "1")), as(CENTRE_A, "PHARMACIEN")));
        golden("pharmacien_remplace", remplacer(bon, remplacement(CENTRE_A, ligne(a1, "2", "2")), as(CENTRE_A, "PHARMACIEN")));
        golden("pharmacien_valide", valider(bon, centre(CENTRE_A), as(CENTRE_A, "PHARMACIEN")));
        for (String role : List.of("INFIRMIER", "MEDECIN", "SECRETAIRE", "DIRECTION")) {
            String r = role.toLowerCase();
            golden("refus_creation_" + r, creer(creation(CENTRE_A, null, null), as(CENTRE_A, role)));
            golden("refus_remplacement_" + r, remplacer(bon, remplacement(CENTRE_A), as(CENTRE_A, role)));
            golden("refus_validation_" + r, valider(bon, centre(CENTRE_A), as(CENTRE_A, role)));
        }
        golden("infirmier_lit_un_bon", lire("/" + bon + "?centerId=" + CENTRE_A, as(CENTRE_A, "INFIRMIER")));
        golden("infirmier_liste", lire("?centerId=" + CENTRE_A, as(CENTRE_A, "INFIRMIER")));
        golden("pharmacien_liste", lire("?centerId=" + CENTRE_A, as(CENTRE_A, "PHARMACIEN")));
        for (String role : List.of("MEDECIN", "SECRETAIRE", "DIRECTION")) {
            golden("refus_lecture_" + role.toLowerCase(), lire("/" + bon + "?centerId=" + CENTRE_A, as(CENTRE_A, role)));
            golden("refus_liste_" + role.toLowerCase(), lire("?centerId=" + CENTRE_A, as(CENTRE_A, role)));
        }
        golden("refus_anonyme_creation", send(post(java.net.URI.create(BONS)).contentType(MediaType.APPLICATION_JSON)
                .content(creation(CENTRE_A, null, null))));
        golden("refus_anonyme_liste", send(get(java.net.URI.create(BONS + "?centerId=" + CENTRE_A))));
        goldenBons("roles_etat_base", CENTRE_A);
    }
}





