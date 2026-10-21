package com.hemodialyse.backend.characterization.facturation.pilotage;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * F5 : seconde voie d'impression de la même synthèse, par le module Documents ({@code POST /api/v1/documents/print} avec
 * {@code typeDocument = SYNTHESE_FACTURATION_MENSUELLE}, gabarit statique {@code reports/synthese_mensuelle_facturation.jrxml} :
 * colonnes fixes CNAS / CASNOS / CAMSSP / Autres). Le module Documents reste hors de la slice ; seul le contenu financier du
 * gabarit (SQL sur {@code factures} / {@code facture_lignes}) est verrouillé ici.
 */
class SyntheseViaModeleDeDocumentCharacterizationIntegrationTest extends PilotageFacturationSupport {

    @Override
    protected String theme() {
        return "synthese-modele-document";
    }

    private void modele(UUID centre, boolean actif) {
        UUID id = UUID.nameUUIDFromBytes(("f5:modele:" + centre).getBytes());
        nommer(id, "<MODELE_" + (centre.equals(CENTRE_A) ? "A" : "B") + ">");
        jdbc.update("INSERT INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, format_impression, "
                        + "description, active, created_at) VALUES (?, ?, 'SYNTHESE_FACTURATION_MENSUELLE', 'Synthèse mensuelle facturation', "
                        + "'SYNTHESE_FACTURATION_MENSUELLE', 'reports/synthese_mensuelle_facturation.jrxml', 'PDF', 'x', ?, ?)",
                id, centre, actif, Timestamp.valueOf("2026-01-01 00:00:00"));
    }

    private String corps(UUID centre, String debut, String fin, String format) {
        StringBuilder sb = new StringBuilder("{'centerId':'" + centre + "','typeDocument':'SYNTHESE_FACTURATION_MENSUELLE'");
        if (format != null) sb.append(",'formatOverride':'").append(format).append("'");
        sb.append(",'params':{");
        if (debut != null) sb.append("'PERIOD_START':'").append(debut).append("'");
        if (debut != null && fin != null) sb.append(",");
        if (fin != null) sb.append("'PERIOD_END':'").append(fin).append("'");
        return json(sb.append("}}").toString());
    }

    private Resp imprimer(UUID centre, String debut, String fin, String format, org.springframework.test.web.servlet.request.RequestPostProcessor who) throws Exception {
        return doPost("/api/v1/documents/print", corps(centre, debut, fin, format), who);
    }

    private void jeuDeFevrier() {
        UUID f1 = facture(CENTRE_A, P_AMRANI, "FAC-0001", "2026-02-01", "19.00", "1000.00", "190.00", "1190.00", AGENCE_1, "PERMANENT");
        ligne(f1, CENTRE_A, "Forfait HD", "100.05", 9, "900.45");
        ligne(f1, CENTRE_A, "Forfait Transport", "9.99", 10, "99.90");
        UUID f2 = facture(CENTRE_A, P_BELKACEM, "FAC-0002", "2026-02-15", "9.00", "500.00", "45.00", "545.00", AGENCE_2, "VACANCIER");
        ligne(f2, CENTRE_A, "Forfait HD", "0.50", 1, "0.50");
        ligne(f2, CENTRE_A, "Forfait Dialyse Nuit", "499.50", 1, "499.50");
        UUID f3 = facture(CENTRE_A, P_CHIKHI, "FAC-0003", "2026-02-28", "1.25", "10.00", "0.13", "10.13", null, null);
        ligne(f3, CENTRE_A, "Forfait HD", "10.00", 1, "10.00");
        UUID hors = facture(CENTRE_A, P_AMRANI, "FAC-0006", "2026-03-01", "0.00", "6000.00", "0.00", "6000.00", AGENCE_1, "PERMANENT");
        ligne(hors, CENTRE_A, "Forfait HD", "6000.00", 1, "6000.00");
        UUID b = facture(CENTRE_B, P_CENTRE_B, "FAC-B-1", "2026-02-03", "0.00", "9999.00", "0.00", "9999.00", AGENCE_B, "PERMANENT");
        ligne(b, CENTRE_B, "Forfait B", "9999.00", 1, "9999.00");
    }

    @Test
    void le_gabarit_statique_classe_les_caisses_par_prefixe_de_code_cnas_casnos_camssp_autres() throws Exception {
        modele(CENTRE_A, true);
        jeuDeFevrier();
        // CAMSSP2 (préfixe), code en minuscules pour CNAS (comparaison en majuscules), code sans correspondance
        UUID camssp = UUID.nameUUIDFromBytes("f5:camssp".getBytes());
        UUID agenceCamssp = UUID.nameUUIDFromBytes("f5:agence-camssp".getBytes());
        UUID autre = UUID.nameUUIDFromBytes("f5:autre".getBytes());
        UUID agenceAutre = UUID.nameUUIDFromBytes("f5:agence-autre".getBytes());
        nommer(camssp, "<CAISSE_CAMSSP>");
        nommer(agenceCamssp, "<AGENCE_CAMSSP>");
        nommer(autre, "<CAISSE_AUTRE>");
        nommer(agenceAutre, "<AGENCE_AUTRE>");
        caisse(camssp, CENTRE_A, "CAMSSP2", "CAMSSP Sud");
        agence(agenceCamssp, CENTRE_A, camssp, "AG-C", "Agence CAMSSP");
        caisse(autre, CENTRE_A, "mutuelle", "Mutuelle privée");
        agence(agenceAutre, CENTRE_A, autre, "AG-M", "Agence mutuelle");
        UUID f4 = facture(CENTRE_A, P_AMRANI, "FAC-0004", "2026-02-10", "0.00", "300.00", "0.00", "300.00", agenceCamssp, "PERMANENT");
        ligne(f4, CENTRE_A, "Forfait HD", "300.00", 1, "300.00");
        UUID f5 = facture(CENTRE_A, P_BELKACEM, "FAC-0005", "2026-02-11", "0.00", "40.00", "0.00", "40.00", agenceAutre, "PERMANENT");
        ligne(f5, CENTRE_A, "Forfait HD", "40.00", 1, "40.00");

        goldenPdf("fevrier_2026_pdf_nouveau_code", imprimer(CENTRE_A, "2026-02-01", "2026-02-28", "PDF", adminA()));
    }

    @Test
    void le_gabarit_statique_sans_facture_dans_la_periode_donne_un_pdf_sans_tableau() throws Exception {
        modele(CENTRE_A, true);
        jeuDeFevrier();

        goldenPdf("periode_sans_facture", imprimer(CENTRE_A, "2026-05-01", "2026-05-31", "PDF", adminA()));
    }

    @Test
    void la_synthese_par_les_documents_ne_filtre_pas_par_le_centre_de_l_utilisateur() throws Exception {
        modele(CENTRE_B, true);
        jeuDeFevrier();

        // Nouveau code F5 : 403 si le centre du corps n'est pas celui de la session (KB-FAC-52 corrigé)
        golden("centre_b_vu_par_admin_a_nouveau_code", imprimer(CENTRE_B, "2026-02-01", "2026-02-28", "PDF", adminA()));
        goldenPdf("centre_b_par_son_administrateur_nouveau_code", imprimer(CENTRE_B, "2026-02-01", "2026-02-28", "PDF", adminB()));
    }

    @Test
    void les_refus_de_la_voie_documents_sont_en_texte_brut() throws Exception {
        jeuDeFevrier();
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("aucun modele pour le centre", resume(imprimer(CENTRE_A, "2026-02-01", "2026-02-28", "PDF", adminA())));
        modele(CENTRE_A, false);
        cas.put("modele desactive", resume(imprimer(CENTRE_A, "2026-02-01", "2026-02-28", "PDF", adminA())));
        jdbc.update("UPDATE modele_document SET active = TRUE WHERE center_id = ?", CENTRE_A);
        cas.put("fin absente", resume(imprimer(CENTRE_A, "2026-02-01", null, "PDF", adminA())));
        cas.put("debut absent", resume(imprimer(CENTRE_A, null, "2026-02-28", "PDF", adminA())));
        cas.put("periode inversee acceptee", resume(imprimer(CENTRE_A, "2026-03-01", "2026-02-01", "PDF", adminA())));
        cas.put("date illisible", resume(imprimer(CENTRE_A, "01/02/2026", "28/02/2026", "PDF", adminA())));
        cas.put("format excel", resume(imprimer(CENTRE_A, "2026-02-01", "2026-02-28", "EXCEL", adminA())));
        cas.put("format html", resume(imprimer(CENTRE_A, "2026-02-01", "2026-02-28", "HTML", adminA())));
        cas.put("format absent (celui du modele)", resume(imprimer(CENTRE_A, "2026-02-01", "2026-02-28", null, adminA())));
        goldenNode("refus_et_formats", mapper.valueToTree(cas));
    }

    @Test
    void la_voie_documents_repond_selon_le_profil() throws Exception {
        modele(CENTRE_A, true);
        jeuDeFevrier();
        Map<String, Object> cas = new LinkedHashMap<>();
        String[][] profils = {{"ADMIN", "ADMIN"}, {"SECRETAIRE", "SECRETAIRE"}, {"MEDECIN seul", "MEDECIN"},
                {"INFIRMIER seul", "INFIRMIER"}, {"COMPTABLE", "COMPTABLE"}, {"SUPERADMIN", "SUPERADMIN"}};
        for (String[] p : profils) {
            cas.put(p[0], imprimer(CENTRE_A, "2026-02-01", "2026-02-28", "PDF", as(CENTRE_A, p[1])).status());
        }
        cas.put("DIRECTION", imprimer(CENTRE_A, "2026-02-01", "2026-02-28", "PDF", direction()).status());
        cas.put("aucun role", imprimer(CENTRE_A, "2026-02-01", "2026-02-28", "PDF", as(CENTRE_A)).status());
        cas.put("anonyme", doPostAnonyme("/api/v1/documents/print", corps(CENTRE_A, "2026-02-01", "2026-02-28", "PDF")).status());
        // Nouveau code F5 : même profil que POST /facturation/synthese/print (KB-FAC-60 corrigé)
        goldenNode("profils_nouveau_code", mapper.valueToTree(cas));
    }

    private ObjectNode resume(Resp r) {
        ObjectNode o = mapper.createObjectNode();
        o.put("status", r.status());
        o.put("contentType", r.contentType());
        String type = r.contentType() == null ? "" : r.contentType();
        if (type.contains("text/plain")) {
            o.put("corps", new Scrubber().scrubString(r.raw()));
        } else if (type.contains("problem+json")) {
            o.put("code", mapper.readTree(r.raw()).path("code").asString(""));
        } else {
            o.put("disposition", r.disposition());
        }
        return o;
    }
}
