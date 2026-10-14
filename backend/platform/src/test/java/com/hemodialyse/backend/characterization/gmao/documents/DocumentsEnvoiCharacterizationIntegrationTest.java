package com.hemodialyse.backend.characterization.gmao.documents;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Filet G5 : joindre une pièce (POST multipart). Chaque test décrit ce que le code FAIT, pas ce qu'il devrait faire
 * (défauts : known-bugs.md, KB-GMAO-58 et suivants).
 */
class DocumentsEnvoiCharacterizationIntegrationTest extends DocumentsCharacterizationSupport {

    // ------------------------------------------------------------------ nominal

    @Test
    void joindre_un_pdf_sans_type_le_classe_AUTRE_et_stocke_octets_et_metadonnees() throws Exception {
        Resp r = upload(I_PLAN, "devis.pdf", PDF);
        golden("joindre_pdf_sans_type", r);
        assertEquals(201, r.status);
        assertArrayEquals(PDF, octetsEnBase(idCree(r)));
        assertEquals(List.of("AUTRE|devis.pdf|application/pdf|" + PDF.length + "|<CENTRE_A>|<ADMIN_A>"),
                documentsEnBase(I_PLAN));
    }

    @Test
    void joindre_un_png_de_type_PHOTO_deduit_le_type_MIME_du_contenu() throws Exception {
        Resp r = upload(I_PLAN, "avant.png", PNG, "PHOTO", adminA());
        golden("joindre_png_photo", r);
        assertArrayEquals(PNG, octetsEnBase(idCree(r)));
    }

    @Test
    void joindre_un_jpeg_de_type_FACTURE() throws Exception {
        Resp r = upload(I_PLAN, "facture.jpg", JPEG, "FACTURE", adminA());
        golden("joindre_jpeg_facture", r);
        assertArrayEquals(JPEG, octetsEnBase(idCree(r)));
    }

    @Test
    void joindre_un_pdf_de_type_BON_INTERVENTION() throws Exception {
        golden("joindre_pdf_bon_intervention", upload(I_PLAN, "bon.pdf", PDF, "BON_INTERVENTION", adminA()));
    }

    @Test
    void le_type_MIME_declare_par_le_client_est_ignore_seul_le_contenu_compte() throws Exception {
        List<String> l = new ArrayList<>();
        l.add("PNG declare application/pdf : " + resume(upload(I_PLAN, "x.pdf", "application/pdf", PNG, null, adminA())));
        l.add("PDF declare text/html : " + resume(upload(I_PLAN, "x.html", "text/html", PDF, null, adminA())));
        l.add("JPEG declare vide : " + resume(upload(I_PLAN, "x.bin", "", JPEG, null, adminA())));
        // nouveau code : avec un nom cohérent, seul le contenu compte encore (le type déclaré reste ignoré)
        l.add("PNG declare application/pdf, nom x.png : " + resume(upload(I_PLAN, "x.png", "application/pdf", PNG, null, adminA())));
        l.add("PDF declare text/html, nom x.pdf : " + resume(upload(I_PLAN, "x.pdf", "text/html", PDF, null, adminA())));
        l.add("JPEG declare vide, nom x.jpg : " + resume(upload(I_PLAN, "x.jpg", "", JPEG, null, adminA())));
        goldenLignes("type_mime_declare_ignore_nouveau_code", l);
    }

    @Test
    void l_extension_du_nom_doit_correspondre_au_contenu_un_pdf_nomme_exe_est_refuse_nouveau_code() throws Exception {
        List<String> l = new ArrayList<>();
        l.add(resume(upload(I_PLAN, "malware.exe", PDF)));
        l.add(resume(upload(I_PLAN, "sans-extension", PNG)));
        l.add(resume(upload(I_PLAN, "photo.pdf", JPEG)));
        // nouveau code : extensions cohérentes (casse ignorée), double extension (limite connue : seule la dernière compte)
        l.add("photo.JPG : " + resume(upload(I_PLAN, "photo.JPG", JPEG)));
        l.add("scan.jpeg : " + resume(upload(I_PLAN, "scan.jpeg", JPEG)));
        l.add("malware.exe.pdf : " + resume(upload(I_PLAN, "malware.exe.pdf", PDF)));
        l.add("archive.tar.gz : " + resume(upload(I_PLAN, "archive.tar.gz", PDF)));
        l.add("Rapport 12.5 final (pas une extension) : " + resume(upload(I_PLAN, "Rapport 12.5 final", PDF)));
        goldenLignes("extension_non_verifiee_nouveau_code", l);
    }

    @Test
    void joindre_deux_fois_le_meme_fichier_cree_deux_pieces_sans_detection_de_doublon() throws Exception {
        Resp a = upload(I_PLAN, "devis.pdf", PDF);
        Resp b = upload(I_PLAN, "devis.pdf", PDF);
        assertEquals(201, a.status);
        assertEquals(201, b.status);
        assertEquals(2, nombre(I_PLAN));
        assertEquals(false, idCree(a).equals(idCree(b)));
    }

    // ------------------------------------------------------------------ type de pièce

    @Test
    void matrice_du_parametre_type() throws Exception {
        List<String> l = new ArrayList<>();
        for (String t : new String[]{"BON_INTERVENTION", "FACTURE", "PHOTO", "AUTRE", "photo", "Photo", "FOO", "", " ",
                "PHOTO ", "null", "RAPPORT", "0"}) {
            l.add("type=[" + t + "] : " + resume(upload(I_PLAN, "t.pdf", PDF, t, adminA())));
        }
        goldenLignes("matrice_parametre_type", l);
    }

    @Test
    void type_invalide_est_refuse_avant_le_contenu_et_avant_le_quota() throws Exception {
        List<String> l = new ArrayList<>();
        l.add("type FOO + fichier vide : " + resume(upload(I_PLAN, "v.pdf", new byte[0], "FOO", adminA())));
        l.add("type FOO + faux format : " + resume(upload(I_PLAN, "v.pdf", octets("texte"), "FOO", adminA())));
        l.add("type valide + fichier vide : " + resume(upload(I_PLAN, "v.pdf", new byte[0], "PHOTO", adminA())));
        goldenLignes("ordre_validation_type_contenu", l);
        assertEquals(0, nombre(I_PLAN));
    }

    // ------------------------------------------------------------------ contenu (signature)

    @Test
    void matrice_des_signatures_acceptees_et_refusees() throws Exception {
        List<String> l = new ArrayList<>();
        Object[][] cas = {
                {"PDF minimal %PDF- (5 octets)", octets("%PDF-")},
                {"%PDF sans tiret (4 octets)", octets("%PDF")},
                {"%PDF-1.7 + texte", octets("%PDF-1.7 bonjour")},
                {"entete PDF en minuscules", octets("%pdf-1.4")},
                {"entete PDF precede d'un espace", octets(" %PDF-1.4")},
                {"entete PDF precede d'un BOM UTF-8", new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, '%', 'P', 'D', 'F', '-'}},
                {"PNG signature complete 8 octets", new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}},
                {"PNG 7 octets (signature tronquee)", new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A}},
                {"PNG : 4 premiers octets exacts puis 4 octets quelconques", new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4}},
                {"PNG sans 0x89", new byte[]{0x01, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}},
                {"JPEG FFD8FF (3 octets)", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}},
                {"JPEG FFD8 (2 octets)", new byte[]{(byte) 0xFF, (byte) 0xD8}},
                {"JPEG FFD8 puis 00", new byte[]{(byte) 0xFF, (byte) 0xD8, 0x00, (byte) 0xE0}},
                {"GIF89a", octets("GIF89a....")},
                {"ZIP PK", octets("PK\u0003\u0004....")},
                {"executable MZ", octets("MZ......")},
                {"HTML", octets("<html><script>alert(1)</script></html>")},
                {"SVG", octets("<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>")},
                {"texte brut", octets("juste du texte")},
                {"un seul octet", new byte[]{'%'}},
                {"fichier vide", new byte[0]},
                {"PDF complet (nouveau code)", PDF},
                {"PDF complet suivi de blancs", concat(PDF, octets("\r\n"))},
                {"PNG complet (nouveau code)", PNG},
                {"JPEG complet (nouveau code)", JPEG},
        };
        for (Object[] c : cas) {
            l.add(c[0] + " : " + resume(upload(I_PLAN, "f", (byte[]) c[1], null, adminA())));
        }
        goldenLignes("matrice_signatures_nouveau_code", l);
    }

    @Test
    void le_contenu_apres_la_signature_est_verifie_pdf_polyglotte_et_images_corrompues_refuses_nouveau_code() throws Exception {
        List<String> l = new ArrayList<>();
        l.add("PDF puis HTML/script : " + resume(upload(I_PLAN, "p.pdf", octets("%PDF-1.4\n<html><script>alert(1)</script>"))));
        l.add("JPEG puis HTML : " + resume(upload(I_PLAN, "p.jpg",
                concat(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, octets("<script>alert(1)</script>")))));
        l.add("PNG puis octets aleatoires : " + resume(upload(I_PLAN, "p.png",
                concat(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}, octets("pas une image")))));
        l.add("PDF termine puis HTML : " + resume(upload(I_PLAN, "p.pdf", octets("%PDF-1.4\n%%EOF\n<html><script>alert(1)</script>"))));
        l.add("PNG tronque avant IEND : " + resume(upload(I_PLAN, "p.png", java.util.Arrays.copyOf(PNG, PNG.length - 12))));
        l.add("JPEG tronque (sans EOI) : " + resume(upload(I_PLAN, "p.jpg", java.util.Arrays.copyOf(JPEG, JPEG.length - 2))));
        goldenLignes("contenu_apres_signature_non_verifie_nouveau_code", l);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    // ------------------------------------------------------------------ taille

    @Test
    void cinq_mo_exactement_sont_acceptes_et_cinq_mo_plus_un_octet_refuse() throws Exception {
        long max = 5L * 1024 * 1024;
        List<String> l = new ArrayList<>();
        Resp ok = upload(I_PLAN, "limite.pdf", avecEntete(PDF, (int) max));
        l.add("5 Mo exactement : " + resume(ok));
        l.add("5 Mo + 1 octet : " + resume(upload(I_PLAN, "trop.pdf", avecEntete(PDF, (int) max + 1))));
        l.add("5 Mo - 1 octet : " + resume(upload(I_PLAN, "presque.pdf", avecEntete(PDF, (int) max - 1))));
        goldenLignes("limite_taille_5_mo", l);
        assertEquals(2, nombre(I_PLAN));
        assertEquals(max, octetsEnBase(idCree(ok)).length);
    }

    @Test
    void les_plafonds_multipart_du_conteneur_ne_sont_pas_observables_en_MockMvc_leur_configuration_est_figee() {
        var env = context.getEnvironment();
        List<String> l = List.of(
                "spring.servlet.multipart.max-file-size=" + env.getProperty("spring.servlet.multipart.max-file-size"),
                "spring.servlet.multipart.max-request-size=" + env.getProperty("spring.servlet.multipart.max-request-size"));
        goldenLignes("configuration_multipart_conteneur", l);
    }

    // ------------------------------------------------------------------ nom de fichier

    @Test
    void matrice_du_nettoyage_du_nom_de_fichier_nouveau_code() throws Exception {
        List<String> l = new ArrayList<>();
        String[] noms = {
                "devis.pdf", "../../etc/passwd.pdf", "..\\..\\windows\\system32\\x.pdf", "a/b/c.pdf", "C:\\dir\\sub\\f.pdf",
                "/absolu/chemin.pdf", "dossier/", "/", "\\", "..", "../", "a/..", ".", "...", ".hidden.pdf", "  espaces  .pdf  ",
                "   ", "", "tab\tdans\tnom.pdf", "ligne\nnouvelle\r\n.pdf", "nul\u0000.pdf", "del\u007f.pdf", "c1\u0085ctrl.pdf",
                "guillemets\"doubles\".pdf", "inf<sup>.pdf", "deux:points.pdf", "etoile*.pdf", "question?.pdf", "pipe|.pdf",
                "apostrophe'.pdf", "point-virgule;.pdf", "pourcent%20.pdf", "esperluette&.pdf", "dollar$HOME.pdf",
                "${jndi:ldap://x}.pdf", "<script>alert(1)</script>.pdf", "con.pdf", "NUL", "rapport été 2026.pdf",
                "日本語のファイル.pdf", "Ω≈ç√.pdf", "emoji-\uD83D\uDE00.pdf", "retour\u2028ligne.pdf", "rtl\u202Egnp.exe.pdf",
                "a\u0301.pdf", "nom avec  deux  espaces.pdf", "x.pdf\u00A0", "\u00A0nbsp.pdf",
        };
        for (String n : noms) {
            Resp r = upload(I_PLAN, n, PDF);
            l.add(visible(n) + " -> " + resume(r));
        }
        goldenLignes("matrice_nettoyage_nom_nouveau_code", l);
    }

    @Test
    void nom_tres_long_garde_les_150_derniers_caracteres_sans_couper_un_emoji_nouveau_code() throws Exception {
        List<String> l = new ArrayList<>();
        String n149 = "a".repeat(146) + ".pdf";
        l.add("150 car. : " + resume(upload(I_PLAN, "b".repeat(146) + ".pdf", PDF)));
        l.add("149 car. : " + resume(upload(I_PLAN, n149.substring(1), PDF)));
        l.add("151 car. (debut perdu) : " + resume(upload(I_PLAN, "X" + "b".repeat(146) + ".pdf", PDF)));
        l.add("300 car. numerotes : " + resume(upload(I_PLAN, numerote(300) + ".pdf", PDF)));
        l.add("espace en position 151 (le rognage est rejoue apres la troncature : pas d'espace de tete) : "
                + resume(upload(I_PLAN, "x " + "c".repeat(145) + ".pdf", PDF)));
        l.add("5000 car. : " + longueur(upload(I_PLAN, "z".repeat(5000) + ".pdf", PDF)));
        l.add("150 emoji (300 unites UTF-16) : " + longueur(upload(I_PLAN, "\uD83D\uDE00".repeat(150), PDF)));
        l.add("75 emoji + .pdf = 154 unites : " + resume(upload(I_PLAN, "\uD83D\uDE00".repeat(75) + ".pdf", PDF)));
        l.add("troncature au milieu d'un emoji : " + visible(nomStocke(upload(I_PLAN, "\uD83D\uDE00" + "d".repeat(149), PDF))));
        goldenLignes("matrice_nom_long_nouveau_code", l);
    }

    private String nomStocke(Resp r) {
        return json(r).path("nom").asString("?");
    }

    private String longueur(Resp r) {
        return r.status + " longueurNom=" + nomStocke(r).length();
    }

    private static String numerote(int n) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (sb.length() < n) sb.append(String.format("%03d-", i++));
        return sb.substring(0, n);
    }

    /** Rend visibles les caractères invisibles pour un golden relisible. */
    private static String visible(String s) {
        StringBuilder sb = new StringBuilder("[");
        for (char c : s.toCharArray()) {
            if (c < 0x20 || c == 0x7f || (c >= 0x80 && c < 0xA0) || c == 0x2028 || c == 0x202E || c == 0xA0) {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.append("]").toString();
    }

    // ------------------------------------------------------------------ quota

    @Test
    void cinquante_pieces_sont_acceptees_la_cinquante_et_unieme_est_refusee_puis_acceptee_apres_un_retrait() throws Exception {
        UUID premier = null;
        for (int i = 1; i <= 50; i++) {
            Resp r = upload(I_PLAN, "p" + i + ".pdf", PDF);
            assertEquals(201, r.status, "piece " + i);
            if (i == 1) premier = idCree(r);
        }
        assertEquals(50, nombre(I_PLAN));
        List<String> l = new ArrayList<>();
        l.add("51e (PDF valide) : " + resume(upload(I_PLAN, "p51.pdf", PDF)));
        l.add("51e (fichier invalide) : " + resume(upload(I_PLAN, "p51.txt", octets("texte"))));
        l.add("51e (type invalide) : " + resume(upload(I_PLAN, "p51.pdf", PDF, "FOO", adminA())));
        l.add("51e sur une autre intervention du centre : " + resume(upload(I_AUTRE, "ok.pdf", PDF)));
        assertEquals(204, retirer(I_PLAN, premier, adminA()).status);
        l.add("apres retrait d'une piece : " + resume(upload(I_PLAN, "p52.pdf", PDF)));
        l.add("de nouveau 50, 51e : " + resume(upload(I_PLAN, "p53.pdf", PDF)));
        goldenLignes("quota_50_par_intervention", l);
        assertEquals(50, nombre(I_PLAN));
    }

    @Test
    void le_quota_se_verifie_avant_le_contenu_une_intervention_pleine_masque_les_autres_erreurs() throws Exception {
        for (int i = 0; i < 50; i++) {
            semer(doc(i), I_PLAN, CENTRE_A, "AUTRE", "s" + i + ".pdf", "application/pdf", PDF, i);
        }
        List<String> l = new ArrayList<>();
        l.add("fichier vide : " + resume(upload(I_PLAN, "v.pdf", new byte[0])));
        l.add("texte : " + resume(upload(I_PLAN, "v.txt", octets("texte"))));
        l.add("type invalide : " + resume(upload(I_PLAN, "v.pdf", PDF, "FOO", adminA())));
        l.add("intervention supprimee, 0 piece, meme fichier vide : " + resume(upload(I_SUPPR, "v.pdf", new byte[0])));
        goldenLignes("quota_masque_autres_erreurs_nouveau_code", l);
        assertEquals(50, nombre(I_PLAN));
    }

    @Test
    void les_pieces_semees_a_52_ne_bloquent_que_les_nouveaux_envois_la_liste_et_le_telechargement_restent_possibles()
            throws Exception {
        for (int i = 0; i < 52; i++) {
            semer(doc(i), I_PLAN, CENTRE_A, "AUTRE", "s" + i + ".pdf", "application/pdf", PDF, i);
        }
        List<String> l = new ArrayList<>();
        l.add("envoi : " + resume(upload(I_PLAN, "n.pdf", PDF)));
        Resp page = liste(I_PLAN, "size=1", adminA());
        l.add("liste total : " + json(page).path("total").asString());
        l.add("telechargement : " + telecharger(I_PLAN, doc(0), adminA()).status);
        goldenLignes("quota_depasse_en_base", l);
    }

    // ------------------------------------------------------------------ intervention cible

    @Test
    void matrice_du_statut_de_l_intervention_annulee_terminee_ou_supprimee_refusent_l_envoi_nouveau_code() throws Exception {
        List<String> l = new ArrayList<>();
        for (UUID i : List.of(I_PLAN, I_COURS, I_TERM, I_ANN, I_ATT, I_SUPPR, I_B, I_INCONNU)) {
            l.add(statutDe(i) + " : " + resume(upload(i, "s.pdf", PDF)));
        }
        goldenLignes("matrice_statut_intervention_envoi_nouveau_code", l);
        assertEquals(0, nombre(I_B));
        assertEquals(0, nombre(I_INCONNU));
    }

    private String statutDe(UUID i) {
        if (i.equals(I_PLAN)) return "PLANIFIEE";
        if (i.equals(I_COURS)) return "EN_COURS";
        if (i.equals(I_TERM)) return "TERMINEE";
        if (i.equals(I_ANN)) return "ANNULEE";
        if (i.equals(I_ATT)) return "EN_ATTENTE_VALIDATION";
        if (i.equals(I_SUPPR)) return "supprimee logiquement (deleted_at)";
        if (i.equals(I_B)) return "intervention du centre B appelee par le centre A";
        return "intervention inconnue";
    }

    @Test
    void intervention_inconnue_ou_d_un_autre_centre_donne_400_et_non_404() throws Exception {
        golden("joindre_intervention_inconnue", upload(I_INCONNU, "x.pdf", PDF));
        golden("joindre_intervention_autre_centre", upload(I_B, "x.pdf", PDF));
    }

    @Test
    void joindre_a_l_intervention_du_centre_B_par_l_admin_B_enregistre_le_centre_de_l_intervention() throws Exception {
        Resp r = upload(I_B, "b.pdf", PDF, null, adminB());
        assertEquals(201, r.status);
        assertEquals(List.of("AUTRE|b.pdf|application/pdf|" + PDF.length + "|<CENTRE_B>|<ADMIN_B>"), documentsEnBase(I_B));
    }

    @Test
    void id_d_intervention_illisible_donne_une_erreur_de_conversion() throws Exception {
        var b =
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/v1/gmao/interventions/pas-un-uuid/documents")
                        .file(new org.springframework.mock.web.MockMultipartFile("file", "x.pdf", "application/pdf", PDF))
                        .with(adminA());
        golden("joindre_id_intervention_illisible", send(b));
    }

    // ------------------------------------------------------------------ multipart / corps illisible

    @Test
    void multipart_sans_partie_file_ou_mal_nommee() throws Exception {
        List<String> l = new ArrayList<>();
        l.add("aucune partie : " + resume(send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart(base(I_PLAN)).with(adminA()))));
        l.add("partie nommee document : " + resume(send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart(base(I_PLAN))
                .file(new org.springframework.mock.web.MockMultipartFile("document", "x.pdf", "application/pdf", PDF))
                .with(adminA()))));
        l.add("partie file vide (0 octet, sans nom) : " + resume(send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart(base(I_PLAN))
                .file(new org.springframework.mock.web.MockMultipartFile("file", "", "application/pdf", new byte[0]))
                .with(adminA()))));
        l.add("deux parties file (la premiere est retenue ?) : " + resume(send(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(base(I_PLAN))
                        .file(new org.springframework.mock.web.MockMultipartFile("file", "premier.pdf", "application/pdf", PDF))
                        .file(new org.springframework.mock.web.MockMultipartFile("file", "second.png", "image/png", PNG))
                        .with(adminA()))));
        l.add("parametre type repete : " + resume(send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart(base(I_PLAN))
                .file(new org.springframework.mock.web.MockMultipartFile("file", "r.pdf", "application/pdf", PDF))
                .param("type", "PHOTO", "FACTURE").with(adminA()))));
        l.add("en base : " + documentsEnBase(I_PLAN));
        goldenLignes("multipart_incomplet", l);
    }

    @Test
    void corps_non_multipart_est_refuse_par_le_mapping() throws Exception {
        List<String> l = new ArrayList<>();
        l.add("JSON : " + resume(send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post(base(I_PLAN)).contentType("application/json").content("{\"file\":\"x\"}").with(adminA()))));
        l.add("octet-stream : " + resume(send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post(base(I_PLAN)).contentType("application/octet-stream").content(PDF).with(adminA()))));
        l.add("sans content-type : " + resume(send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post(base(I_PLAN)).with(adminA()))));
        l.add("multipart declare mais corps texte : " + resume(send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post(base(I_PLAN)).contentType("multipart/form-data; boundary=xyz").content("pas du multipart").with(adminA()))));
        goldenLignes("corps_non_multipart", l);
        assertEquals(0, nombre(I_PLAN));
    }

    @Test
    void methodes_non_prevues_sur_la_collection_et_la_piece() throws Exception {
        List<String> l = new ArrayList<>();
        l.add("PUT collection : " + resume(envoyer(org.springframework.http.HttpMethod.PUT, base(I_PLAN), adminA())));
        l.add("DELETE collection : " + resume(envoyer(org.springframework.http.HttpMethod.DELETE, base(I_PLAN), adminA())));
        l.add("POST sur une piece : " + resume(envoyer(org.springframework.http.HttpMethod.POST, base(I_PLAN) + "/" + doc(1), adminA())));
        l.add("PUT piece : " + resume(envoyer(org.springframework.http.HttpMethod.PUT, base(I_PLAN) + "/" + doc(1), adminA())));
        goldenLignes("methodes_non_prevues", l);
    }
}
