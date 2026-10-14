package com.hemodialyse.backend.characterization.gmao.documents;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Filet G5 : lister, télécharger (octets et en-têtes exacts) et retirer une pièce jointe, parcours complet.
 */
class DocumentsLectureSuppressionCharacterizationIntegrationTest extends DocumentsCharacterizationSupport {

    private String entetes(Resp r) {
        return "status=" + r.status
                + " Content-Type=" + r.header("Content-Type")
                + " Content-Disposition=" + r.header("Content-Disposition")
                + " X-Content-Type-Options=" + r.header("X-Content-Type-Options")
                + " Content-Length=" + r.header("Content-Length")
                + " octets=" + r.bytes.length
                + " Cache-Control=" + r.header("Cache-Control");
    }

    // ------------------------------------------------------------------ télécharger

    @Test
    void telecharger_restitue_les_octets_exacts_et_les_en_tetes_de_chaque_type() throws Exception {
        List<String> l = new ArrayList<>();
        byte[][] contenus = {PDF, PNG, JPEG};
        String[] noms = {"devis.pdf", "avant.png", "facture.jpg"};
        for (int i = 0; i < 3; i++) {
            UUID d = idCree(upload(I_PLAN, noms[i], contenus[i]));
            Resp r = telecharger(I_PLAN, d, adminA());
            assertArrayEquals(contenus[i], r.bytes);
            l.add(noms[i] + " : " + entetes(r));
        }
        goldenLignes("telechargement_entetes_par_type", l);
    }

    @Test
    void telecharger_encode_le_nom_assaini_dans_content_disposition_nouveau_code() throws Exception {
        List<String> l = new ArrayList<>();
        String[] noms = {"simple.pdf", "avec espaces.pdf", "été 2026.pdf", "日本語.pdf", "emoji-\uD83D\uDE00.pdf",
                "a;b,c.pdf", "pourcent%20.pdf", "apostrophe'.pdf", "ligne\r\nX-Injecte: 1.pdf", "backslash\\\"quote.pdf",
                "..", "document"};
        for (String n : noms) {
            Resp up = upload(I_PLAN, n, PDF);
            Resp r = telecharger(I_PLAN, idCree(up), adminA());
            l.add("[" + n.replace("\r", "\\r").replace("\n", "\\n") + "] nom stocke=[" + json(up).path("nom").asString()
                    .replace("\r", "\\r").replace("\n", "\\n") + "] : Content-Disposition=" + r.header("Content-Disposition"));
        }
        goldenLignes("telechargement_content_disposition_nouveau_code", l);
    }

    @Test
    void telecharger_une_piece_heritee_est_servie_avec_un_nom_et_un_type_re_controles_nouveau_code() throws Exception {
        semer(doc(1), I_PLAN, CENTRE_A, "AUTRE", "heritage.html", "text/html", octets("<html>x</html>"), 1);
        semer(doc(2), I_PLAN, CENTRE_A, "AUTRE", "type-inconnu.bin", "application/x-inconnu", octets("data"), 2);
        semer(doc(3), I_PLAN, CENTRE_A, "AUTRE", "type-invalide.bin", "pas un type mime", octets("data"), 3);
        semer(doc(4), I_PLAN, CENTRE_A, "AUTRE", "ancien nom \"quote\".pdf", "application/pdf", PDF, 4);
        semer(doc(5), I_PLAN, CENTRE_A, "AUTRE", "../../traversal.pdf", "application/pdf", PDF, 5);
        List<String> l = new ArrayList<>();
        for (int n = 1; n <= 5; n++) {
            l.add("<DOC_0" + n + "> : " + resume(telecharger(I_PLAN, doc(n), adminA())).replace("\n", "\\n") + " | "
                    + entetes(telecharger(I_PLAN, doc(n), adminA())));
        }
        goldenLignes("telechargement_pieces_heritees_non_conformes_nouveau_code", l);
    }

    @Test
    void telecharger_tolere_une_piece_dont_le_centre_stocke_differe_de_celui_de_l_intervention() throws Exception {
        semer(doc(1), I_PLAN, CENTRE_B, "AUTRE", "centre-incoherent.pdf", "application/pdf", PDF, 1);
        golden("telechargement_centre_de_la_piece_incoherent", telecharger(I_PLAN, doc(1), adminA()));
        Resp l = liste(I_PLAN, null, adminA());
        golden("liste_centre_de_la_piece_incoherent", l);
    }

    @Test
    void telecharger_matrice_des_erreurs() throws Exception {
        semer(doc(1), I_PLAN, CENTRE_A, "AUTRE", "a.pdf", "application/pdf", PDF, 1);
        semer(doc(2), I_AUTRE, CENTRE_A, "AUTRE", "b.pdf", "application/pdf", PDF, 2);
        semer(doc(3), I_B, CENTRE_B, "AUTRE", "c.pdf", "application/pdf", PDF, 3);
        semer(doc(4), I_SUPPR, CENTRE_A, "AUTRE", "d.pdf", "application/pdf", PDF, 4);
        List<String> l = new ArrayList<>();
        l.add("piece ok : " + resume(telecharger(I_PLAN, doc(1), adminA())).substring(0, 3));
        l.add("piece d'une autre intervention du meme centre : " + resume(telecharger(I_PLAN, doc(2), adminA())));
        l.add("piece du centre B via l'intervention A : " + resume(telecharger(I_PLAN, doc(3), adminA())));
        l.add("piece du centre B via l'intervention B par admin A : " + resume(telecharger(I_B, doc(3), adminA())));
        l.add("piece du centre B par admin B : " + resume(telecharger(I_B, doc(3), adminB())).substring(0, 3));
        l.add("piece inconnue : " + resume(telecharger(I_PLAN, doc(99), adminA())));
        l.add("intervention inconnue : " + resume(telecharger(I_INCONNU, doc(1), adminA())));
        l.add("intervention supprimee logiquement : " + resume(telecharger(I_SUPPR, doc(4), adminA())));
        l.add("id piece illisible : " + resume(envoyer(HttpMethod.GET, base(I_PLAN) + "/pas-un-uuid", adminA())));
        l.add("id intervention illisible : " + resume(envoyer(HttpMethod.GET,
                "/api/v1/gmao/interventions/xyz/documents/" + doc(1), adminA())));
        l.add("piece avec suffixe de chemin : " + resume(envoyer(HttpMethod.GET, base(I_PLAN) + "/" + doc(1) + "/", adminA())));
        goldenLignes("telechargement_erreurs", l);
    }

    @Test
    void telecharger_sert_une_piece_quel_que_soit_le_statut_de_l_intervention() throws Exception {
        List<String> l = new ArrayList<>();
        int n = 0;
        for (UUID i : List.of(I_PLAN, I_COURS, I_TERM, I_ANN, I_ATT)) {
            semer(doc(++n), i, CENTRE_A, "AUTRE", "s.pdf", "application/pdf", PDF, n);
            l.add(i + " : " + telecharger(i, doc(n), adminA()).status);
        }
        goldenLignes("telechargement_tous_statuts", l.stream().map(s -> s.replace(I_PLAN.toString(), "PLANIFIEE")
                .replace(I_COURS.toString(), "EN_COURS").replace(I_TERM.toString(), "TERMINEE")
                .replace(I_ANN.toString(), "ANNULEE").replace(I_ATT.toString(), "EN_ATTENTE_VALIDATION")).toList());
    }

    // ------------------------------------------------------------------ lister

    @Test
    void liste_vide_d_une_intervention_sans_piece() throws Exception {
        golden("liste_vide", liste(I_PLAN, null, adminA()));
    }

    @Test
    void liste_triee_de_la_plus_recente_a_la_plus_ancienne_sans_le_contenu_binaire() throws Exception {
        semer(doc(1), I_PLAN, CENTRE_A, "FACTURE", "ancienne.pdf", "application/pdf", PDF, 1);
        semer(doc(2), I_PLAN, CENTRE_A, "PHOTO", "milieu.png", "image/png", PNG, 2);
        semer(doc(3), I_PLAN, CENTRE_A, "BON_INTERVENTION", "recente.jpg", "image/jpeg", JPEG, 3);
        semer(doc(4), I_AUTRE, CENTRE_A, "AUTRE", "autre-intervention.pdf", "application/pdf", PDF, 4);
        semer(doc(5), I_B, CENTRE_B, "AUTRE", "centre-b.pdf", "application/pdf", PDF, 5);
        golden("liste_tri_et_metadonnees", liste(I_PLAN, null, adminA()));
    }

    @Test
    void liste_pagination_et_valeurs_limites() throws Exception {
        for (int i = 1; i <= 5; i++) {
            semer(doc(i), I_PLAN, CENTRE_A, "AUTRE", "p" + i + ".pdf", "application/pdf", PDF, i);
        }
        List<String> l = new ArrayList<>();
        for (String q : new String[]{null, "page=0&size=2", "page=1&size=2", "page=2&size=2", "page=3&size=2", "page=99&size=2",
                "size=1", "size=0", "size=-1", "page=-1", "page=-1&size=-1", "size=abc", "page=abc", "size=", "page=&size=",
                "size=100", "size=1000", "size=100000", "size=2147483647", "page=2147483647&size=2", "page=1&size=2147483647",
                "size=99999999999"}) {
            Resp r = liste(I_PLAN, q, adminA());
            String corps = r.status == 200
                    ? "total=" + json(r).path("total").asString() + " page=" + json(r).path("page").asString()
                    + " size=" + json(r).path("size").asString() + " noms=" + noms(r)
                    : resume(r);
            l.add("?" + q + " : " + r.status + " " + corps);
        }
        goldenLignes("liste_pagination", l);
    }

    private String noms(Resp r) {
        List<String> n = new ArrayList<>();
        json(r).path("items").forEach(x -> n.add(x.path("nom").asString()));
        return n.toString();
    }

    @Test
    void liste_matrice_du_statut_et_de_l_isolation() throws Exception {
        List<String> l = new ArrayList<>();
        int n = 0;
        for (UUID i : List.of(I_PLAN, I_COURS, I_TERM, I_ANN, I_ATT)) {
            semer(doc(++n), i, CENTRE_A, "AUTRE", "x.pdf", "application/pdf", PDF, n);
            Resp r = liste(i, null, adminA());
            l.add(statut(i) + " : " + r.status + " total=" + json(r).path("total").asString());
        }
        l.add("intervention supprimee logiquement : " + resume(liste(I_SUPPR, null, adminA())));
        l.add("intervention inconnue : " + resume(liste(I_INCONNU, null, adminA())));
        l.add("intervention du centre B vue par A : " + resume(liste(I_B, null, adminA())));
        l.add("intervention du centre B vue par B : " + liste(I_B, null, adminB()).status);
        l.add("id illisible : " + resume(envoyer(HttpMethod.GET, "/api/v1/gmao/interventions/xyz/documents", adminA())));
        goldenLignes("liste_statuts_et_isolation", l);
    }

    private String statut(UUID i) {
        return i.equals(I_PLAN) ? "PLANIFIEE" : i.equals(I_COURS) ? "EN_COURS" : i.equals(I_TERM) ? "TERMINEE"
                : i.equals(I_ANN) ? "ANNULEE" : "EN_ATTENTE_VALIDATION";
    }

    // ------------------------------------------------------------------ retirer

    @Test
    void retirer_supprime_physiquement_la_ligne_et_les_octets_et_repond_204_sans_corps() throws Exception {
        UUID d1 = idCree(upload(I_PLAN, "a.pdf", PDF));
        UUID d2 = idCree(upload(I_PLAN, "b.png", PNG));
        Resp r = retirer(I_PLAN, d1, adminA());
        golden("retirer_nominal", r);
        assertEquals(1, nombre(I_PLAN));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM gmao_documents_intervention WHERE id = ?", Integer.class, d1));
        assertArrayEquals(PNG, octetsEnBase(d2));
        // physique : la deuxieme suppression ne trouve plus rien, et le telechargement non plus
        golden("retirer_deux_fois", retirer(I_PLAN, d1, adminA()));
        golden("telecharger_apres_retrait", telecharger(I_PLAN, d1, adminA()));
    }

    @Test
    void retirer_matrice_des_erreurs_annulee_terminee_ou_supprimee_refusent_le_retrait_nouveau_code() throws Exception {
        List<String> l = new ArrayList<>();
        int n = 0;
        for (UUID i : List.of(I_PLAN, I_COURS, I_TERM, I_ANN, I_ATT)) {
            semer(doc(++n), i, CENTRE_A, "AUTRE", "x.pdf", "application/pdf", PDF, n);
            Resp r = retirer(i, doc(n), adminA());
            l.add("retrait sur " + statut(i) + " : " + r.status + " restant=" + nombre(i));
        }
        semer(doc(10), I_PLAN, CENTRE_A, "AUTRE", "a.pdf", "application/pdf", PDF, 10);
        semer(doc(11), I_AUTRE, CENTRE_A, "AUTRE", "b.pdf", "application/pdf", PDF, 11);
        semer(doc(12), I_B, CENTRE_B, "AUTRE", "c.pdf", "application/pdf", PDF, 12);
        semer(doc(13), I_SUPPR, CENTRE_A, "AUTRE", "d.pdf", "application/pdf", PDF, 13);
        l.add("piece d'une autre intervention du centre : " + resume(retirer(I_PLAN, doc(11), adminA())) + " | restant="
                + nombre(I_AUTRE));
        l.add("piece du centre B via l'intervention A : " + resume(retirer(I_PLAN, doc(12), adminA())) + " | restant="
                + nombre(I_B));
        l.add("piece du centre B via l'intervention B par admin A : " + resume(retirer(I_B, doc(12), adminA())) + " | restant="
                + nombre(I_B));
        l.add("piece inconnue : " + resume(retirer(I_PLAN, doc(99), adminA())));
        l.add("intervention inconnue : " + resume(retirer(I_INCONNU, doc(10), adminA())));
        l.add("intervention supprimee logiquement : " + resume(retirer(I_SUPPR, doc(13), adminA())) + " | restant="
                + nombre(I_SUPPR));
        l.add("id piece illisible : " + resume(envoyer(HttpMethod.DELETE, base(I_PLAN) + "/xyz", adminA())));
        l.add("retrait de la derniere piece de A par admin B (centre B) : " + resume(retirer(I_PLAN, doc(10), adminB()))
                + " | restant=" + nombre(I_PLAN));
        goldenLignes("retirer_erreurs_et_statuts_nouveau_code", l);
    }

    @Test
    void retirer_une_piece_ne_touche_ni_l_intervention_ni_les_autres_pieces() throws Exception {
        String avant = jdbc.queryForObject("SELECT statut FROM gmao_interventions WHERE id = ?", String.class, I_PLAN);
        UUID d = idCree(upload(I_PLAN, "a.pdf", PDF));
        UUID e = idCree(upload(I_AUTRE, "e.pdf", PDF));
        retirer(I_PLAN, d, adminA());
        assertEquals(avant, jdbc.queryForObject("SELECT statut FROM gmao_interventions WHERE id = ?", String.class, I_PLAN));
        assertEquals(1, nombre(I_AUTRE));
        assertEquals(1, nombreTotal());
        assertEquals(e, jdbc.queryForObject("SELECT id FROM gmao_documents_intervention WHERE intervention_id = ?",
                UUID.class, I_AUTRE));
    }

    // ------------------------------------------------------------------ parcours complet

    @Test
    void parcours_complet_joindre_lister_telecharger_retirer_et_relister() throws Exception {
        List<String> l = new ArrayList<>();
        UUID pdf = idCree(upload(I_COURS, "devis.pdf", PDF, "FACTURE", adminA()));
        UUID png = idCree(upload(I_COURS, "photo-après.png", PNG, "PHOTO", adminA()));
        // l'ordre de la liste suit l'horloge de l'envoi (egalite possible a la meme microseconde) : date posee en base
        jdbc.update("UPDATE gmao_documents_intervention SET ajoute_le = ? WHERE id = ?",
                OffsetDateTime.of(2026, 4, 1, 9, 1, 0, 0, ZoneOffset.UTC), pdf);
        jdbc.update("UPDATE gmao_documents_intervention SET ajoute_le = ? WHERE id = ?",
                OffsetDateTime.of(2026, 4, 1, 9, 2, 0, 0, ZoneOffset.UTC), png);
        Resp liste = liste(I_COURS, null, adminA());
        l.add("liste : total=" + json(liste).path("total").asString() + " noms=" + noms(liste));
        Resp d = telecharger(I_COURS, png, adminA());
        l.add("telechargement png : " + entetes(d));
        assertArrayEquals(PNG, d.bytes);
        l.add("retrait pdf : " + retirer(I_COURS, pdf, adminA()).status);
        Resp apres = liste(I_COURS, null, adminA());
        l.add("liste apres : total=" + json(apres).path("total").asString() + " noms=" + noms(apres));
        l.add("telechargement pdf retire : " + resume(telecharger(I_COURS, pdf, adminA())));
        l.add("retrait png : " + retirer(I_COURS, png, adminA()).status);
        l.add("liste finale : total=" + json(liste(I_COURS, null, adminA())).path("total").asString());
        goldenLignes("parcours_complet", l);
    }
}
