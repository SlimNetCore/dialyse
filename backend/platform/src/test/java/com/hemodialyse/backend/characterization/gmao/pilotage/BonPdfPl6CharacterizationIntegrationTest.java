package com.hemodialyse.backend.characterization.gmao.pilotage;

import com.hemodialyse.backend.application.audit.AuditActionResolver;
import com.hemodialyse.backend.application.audit.AuditWriterService;
import com.hemodialyse.backend.infrastructure.security.AuditRequestFilter;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * PL6 - complement du filet du bon d'intervention (GET /api/v1/gmao/interventions/{id}/bon), sans dupliquer
 * {@link BonPdfCharacterizationIntegrationTest} : garde de centre au niveau du jeton, parametres de chemin et de requete
 * hostiles, cas limites de contenu (texte long, caracteres speciaux, nombreuses lignes, montants nuls ou negatifs,
 * rectifications multiples), absence de journalisation d'audit. Le PDF est compare sur son texte extrait.
 */
class BonPdfPl6CharacterizationIntegrationTest extends PilotageCharacterizationSupport {

    @Autowired
    private AuditRequestFilter filtreDAudit;
    @Autowired
    private AuditWriterService ecrivainDAudit;

    private String bon(UUID intervention) {
        return GMAO + "/interventions/" + intervention + "/bon";
    }

    private UUID generateur(String label) {
        return equipement(label, CENTRE_A, e -> {
            e.designation = "Générateur " + label;
            e.fabricant = "Fresenius";
            e.modele = "5008S";
            e.serie = "SN-123";
        });
    }

    private static RequestPostProcessor principal(String centre, String... roles) {
        return user(UserPrincipal.create(ADMIN_A.toString(), centre, "u-" + ADMIN_A, "", List.of(roles), true));
    }

    private String essai(String path, RequestPostProcessor who) {
        try {
            Resp r = get(path, who);
            return r.status == 200 ? "200 PDF pages=" + pagesOuZero(r) : resume(r);
        } catch (Exception e) {
            return "exception " + e.getClass().getSimpleName();
        }
    }

    private int pagesOuZero(Resp r) throws Exception {
        return pagesDuPdf(r.bytes);
    }

    // ------------------------------------------------------------------ garde de centre

    @Test
    void la_garde_de_centre_du_bon_vient_du_jeton_et_ignore_les_parametres_de_centre_de_la_requete() throws Exception {
        UUID i = intervention("I_GARDE", generateur("G01"), CENTRE_A, x -> {
        });
        UUID inconnu = UUID.randomUUID();
        List<String> l = new ArrayList<>();
        l.add("ADMIN du centre A -> " + essai(bon(i), principal(CENTRE_A.toString(), "ADMIN")));
        l.add("ADMIN du centre A + ?centerId=B -> " + essai(bon(i) + "?centerId=" + CENTRE_B, principal(CENTRE_A.toString(), "ADMIN")));
        l.add("ADMIN du centre B + ?centerId=A -> " + essai(bon(i) + "?centerId=" + CENTRE_A, principal(CENTRE_B.toString(), "ADMIN")));
        l.add("ADMIN du centre B + ?centreId=A -> " + essai(bon(i) + "?centreId=" + CENTRE_A, principal(CENTRE_B.toString(), "ADMIN")));
        l.add("ADMIN sans centre -> " + essai(bon(i), principal(null, "ADMIN")));
        l.add("ADMIN d'un centre inexistant -> " + essai(bon(i), principal(inconnu.toString(), "ADMIN")));
        l.add("ADMIN dont le centre est illisible -> " + essai(bon(i), principal("pas-un-uuid", "ADMIN")));
        l.add("ADMIN dont le centre est vide -> " + essai(bon(i), principal("", "ADMIN")));
        l.add("ADMIN d'un centre d'une autre societe -> " + essai(bon(i), principal(CENTRE_C.toString(), "ADMIN")));
        l.add("SUPERADMIN sans centre -> " + essai(bon(i), principal(null, "SUPERADMIN")));
        l.add("SUPERADMIN + ADMIN sans centre -> " + essai(bon(i), principal(null, "SUPERADMIN", "ADMIN")));
        goldenLignes("bon_pl6_garde_de_centre", l);
    }

    // ------------------------------------------------------------------ parametres

    @Test
    void les_parametres_hostiles_du_bon_sont_toleres_ou_refuses_sans_fuite() throws Exception {
        UUID i = intervention("I_PARAMS", generateur("G01"), CENTRE_A, x -> {
        });
        String id = i.toString();
        String longTz = "Europe/" + "X".repeat(3000);
        Map<String, String> chemins = new LinkedHashMap<>();
        chemins.put("id en majuscules", GMAO + "/interventions/" + id.toUpperCase() + "/bon");
        chemins.put("id nil", GMAO + "/interventions/00000000-0000-0000-0000-000000000000/bon");
        chemins.put("id sans tirets", GMAO + "/interventions/" + id.replace("-", "") + "/bon");
        chemins.put("id tronque", GMAO + "/interventions/" + id.substring(0, 8) + "/bon");
        chemins.put("suffixe .pdf", GMAO + "/interventions/" + id + "/bon.pdf");
        chemins.put("barre finale", GMAO + "/interventions/" + id + "/bon/");
        chemins.put("pas d'id", GMAO + "/interventions//bon");
        chemins.put("id numerique", GMAO + "/interventions/12345/bon");
        List<String> l = new ArrayList<>();
        for (var e : chemins.entrySet()) {
            l.add(e.getKey() + " -> " + essai(e.getValue(), adminA()));
        }
        Map<String, String> tz = new LinkedHashMap<>();
        tz.put("balise", "<script>alert(1)</script>");
        tz.put("gabarit Jasper", "$P{BON_NUMERO}");
        tz.put("tres long", longTz);
        tz.put("decalage hors bornes", "+99:00");
        tz.put("decalage valide avec secondes", "+05:30:15");
        tz.put("GMT+1", "GMT+1");
        tz.put("UTC+01:00", "UTC+01:00");
        tz.put("Etc/GMT-14", "Etc/GMT-14");
        for (var e : tz.entrySet()) {
            Resp r = getParam(bon(i), adminA(), "tz", e.getValue());
            String suite = r.status == 200
                    ? textePdf(r).lines().filter(x -> x.contains("fuseau") || x.startsWith("Début")).toList().toString()
                    : resume(r);
            l.add("tz " + e.getKey() + " -> " + (e.getKey().equals("tres long") ? suite.replace("X".repeat(100), "<X...>") : suite));
        }
        goldenLignes("bon_pl6_parametres_hostiles", l);
    }

    // ------------------------------------------------------------------ contenu

    @Test
    void les_caracteres_speciaux_et_les_retours_a_la_ligne_sont_rendus_tels_quels_sans_interpretation() throws Exception {
        UUID g = equipement("G_SPE", CENTRE_A, e -> {
            e.designation = "Pompe <b>à</b> & \"sang\" $P{BON_NUMERO}";
            e.fabricant = "Nüvoo ™";
            e.modele = "100% / 5008";
            e.serie = "SN'; DROP TABLE x;--";
        });
        UUID i = intervention("I_SPE", g, CENTRE_A, x -> {
            x.description = "Ligne 1\nLigne 2 avec <i>balise</i> & esperluette\n\nLigne 4 après vide";
            x.symptome = "Alarme \"pression\" > 300 mmHg ; 日本語 ★ œ";
            x.cause = "${java:version} $F{libelle} {0}";
            x.actions = "\tTabulation et    espaces multiples";
            x.pieceRemplacee = "Joint 12 mm (insécable)";
        });
        ligne(i, "PIECE", "Joint <u>torique</u> & \"cle\"", "1", "10.00");
        goldenTexte("bon_pl6_caracteres_speciaux_texte", textePdf(get(bon(i), adminA())));
    }

    @Test
    void les_textes_tres_longs_sont_coupes_ou_etales_sur_plusieurs_pages_sans_erreur() throws Exception {
        UUID g = generateur("G01");
        String mot = "Mot".repeat(120);
        String phrase = "Diagnostic detaille de la panne numero %d avec description ".repeat(1);
        StringBuilder long1 = new StringBuilder();
        for (int n = 0; n < 40; n++) long1.append(String.format(phrase, n)).append("\n");
        long1.append("FIN-DESCRIPTION");
        UUID i = intervention("I_LONG", g, CENTRE_A, x -> {
            x.description = long1.toString();
            x.symptome = mot + " FIN-SYMPTOME";
            x.cause = long1.toString().replace("FIN-DESCRIPTION", "FIN-CAUSE");
            x.actions = "A".repeat(1000) + " FIN-ACTIONS";
        });
        Resp r = get(bon(i), adminA());
        String texte = textePdf(r);
        List<String> l = new ArrayList<>();
        l.add("statut=" + r.status + " pages=" + pagesDuPdf(r.bytes));
        for (String marque : List.of("FIN-DESCRIPTION", "FIN-SYMPTOME", "FIN-CAUSE", "FIN-ACTIONS", "COÛT TOTAL", "Signature", "Ligne de temps")) {
            l.add("contient " + marque + " = " + texte.contains(marque));
        }
        l.add("lignes de texte = " + texte.lines().count());
        goldenLignes("bon_pl6_textes_tres_longs", l);
    }

    @Test
    void un_bon_a_nombreuses_lignes_de_cout_deborde_sur_plusieurs_pages_et_garde_le_total_exact() throws Exception {
        UUID g = generateur("G01");
        UUID i = intervention("I_NOMBREUSES", g, CENTRE_A, x -> x.actions = "Remplacements en serie");
        for (int n = 1; n <= 60; n++) {
            ligne(i, n % 2 == 0 ? "PIECE" : "AUTRE", "Article numero " + n, "2", "100.25");
        }
        Resp r = get(bon(i), adminA());
        String texte = espacesSimples(textePdf(r));
        List<String> l = new ArrayList<>();
        l.add("pages=" + pagesDuPdf(r.bytes));
        for (int n : new int[]{1, 30, 60}) l.add("contient Article numero " + n + " = " + texte.contains("Article numero " + n + " "));
        l.add("Articles listes = " + texte.lines().filter(x -> x.contains("Article numero")).count());
        l.addAll(texte.lines().filter(x -> x.startsWith("Pièces de rechange") || x.startsWith("Autres frais") || x.startsWith("COÛT TOTAL")).toList());
        goldenLignes("bon_pl6_nombreuses_lignes_de_cout", l);
    }

    @Test
    void les_montants_nuls_negatifs_ou_enormes_sont_additionnes_tels_quels() throws Exception {
        UUID g = generateur("G01");
        List<String> l = new ArrayList<>();
        String[][][] cas = {
                {{"quantite 0", "PIECE", "Piece", "0", "500.00"}},
                {{"prix 0", "PIECE", "Piece", "3", "0.00"}},
                {{"prix negatif (avoir)", "AUTRE", "Remise", "1", "-250.00"}},
                {{"total negatif", "PIECE", "Piece", "1", "100.00"}, {"", "AUTRE", "Avoir", "1", "-400.00"}},
                {{"quantite negative", "MAIN_OEUVRE", "Retour", "-2", "300.00"}},
                {{"enorme", "PIECE", "Piece", "99999999", "99999999.99"}},
                {{"quantite tres precise", "PIECE", "Piece", "0.0001", "10.00"}},
        };
        int n = 0;
        for (String[][] c : cas) {
            UUID i = intervention("I_MONTANT_" + (n++), g, CENTRE_A, x -> x.actions = "Montants");
            for (String[] ligne : c) ligne(i, ligne[1], ligne[2], ligne[3], ligne[4]);
            Resp r = get(bon(i), adminA());
            if (r.status != 200) {
                l.add(c[0][0] + " -> " + resume(r));
                continue;
            }
            l.add(c[0][0] + " -> " + espacesSimples(textePdf(r)).lines()
                    .filter(x -> x.startsWith("Pièces de rechange") || x.startsWith("Main d'œuvre :") || x.startsWith("Autres frais")
                            || x.startsWith("COÛT TOTAL")).toList());
        }
        goldenLignes("bon_pl6_montants_limites", l);
    }

    @Test
    void les_rectifications_multiples_et_les_noms_absents_sont_portes_par_la_ligne_de_temps() throws Exception {
        UUID g = generateur("G01");
        UUID i = intervention("I_RECTS", g, CENTRE_A, x -> {
            x.demarrePar = TECH;
            x.demarreLe = T0.plusMinutes(5);
            x.cloturePar = TECH;
            x.clotureLe = T0.plusHours(2);
            x.modification = T0.plusDays(5);
            x.modifiePar = INCONNU;
        });
        rectification(i, "Premier motif", ADMIN_A, T0.plusDays(1), T0.plusHours(2), TECH);
        rectification(i, "Deuxième motif <b>gras</b> & \"cité\"", ADMIN_A, T0.plusDays(2), T0.plusHours(3), TECH);
        rectification(i, "Motif sans clôture antérieure", INCONNU, T0.plusDays(3), null, null);
        rectification(i, "   ", ADMIN_A, T0.plusDays(4), T0.plusHours(4), TECH);
        goldenTexte("bon_pl6_rectifications_multiples_texte", textePdf(get(bon(i), adminA())));
    }

    @Test
    void un_equipement_a_designation_tres_longue_et_un_intervenant_sans_nom_ne_cassent_pas_la_mise_en_page() throws Exception {
        UUID g = equipement("G_DESIGNATION", CENTRE_A, e -> {
            e.designation = "Désignation " + "longue ".repeat(25) + "FIN-DESIGNATION";
            e.fabricant = "F".repeat(40);
            e.modele = "M".repeat(40);
        });
        UUID i = intervention("I_DESIGNATION", g, CENTRE_A, x -> {
        });
        Resp r = get(bon(i), adminA());
        String texte = textePdf(r);
        List<String> l = new ArrayList<>();
        l.add("statut=" + r.status + " pages=" + pagesDuPdf(r.bytes));
        l.add("contient FIN-DESIGNATION = " + texte.contains("FIN-DESIGNATION"));
        l.add("lignes Équipement = " + texte.lines().filter(x -> x.startsWith("Équipement")).count());
        l.add("premiere ligne Équipement tronquee a 60 car. = " + texte.lines().filter(x -> x.startsWith("Équipement")).findFirst()
                .map(x -> x.substring(0, Math.min(60, x.length()))).orElse("?"));
        goldenLignes("bon_pl6_designation_tres_longue", l);
    }

    // ------------------------------------------------------------------ audit

    @Test
    void l_impression_du_bon_n_est_pas_journalisee_et_aucune_ecriture_hors_creation_du_modele() throws Exception {
        UUID i = interventionPourAudit();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
                .addFilters(roleScopeFilter, filtreDAudit).build();
        ecrivainDAudit.flush();
        jdbc.update("DELETE FROM audit_log WHERE center_id IN (?, ?)", CENTRE_A, CENTRE_B);
        String avant = empreinteDesInterventions();

        get(bon(i), adminA());
        get(bon(i) + "?tz=Africa/Algiers", adminA());
        get(bon(INCONNU), adminA());
        get(bon(i), roleSeul("TECHNICIEN"));
        call(HttpMethod.POST, bon(i), null, adminA());
        ecrivainDAudit.flush();

        List<String> l = new ArrayList<>();
        l.add("GET /bon suivi par le resolveur d'audit = " + AuditActionResolver.isTracked("GET", "/api/v1/gmao/interventions/{id}/bon"));
        l.add("POST /bon suivi par le resolveur d'audit = " + AuditActionResolver.isTracked("POST", "/api/v1/gmao/interventions/{id}/bon"));
        for (Map<String, Object> row : jdbc.queryForList("SELECT action_code, http_method, route_template, status_code, COUNT(*) AS n "
                + "FROM audit_log WHERE center_id IN (?, ?) GROUP BY action_code, http_method, route_template, status_code "
                + "ORDER BY route_template, http_method, status_code", CENTRE_A, CENTRE_B)) {
            l.add("journal : " + row);
        }
        l.add("lignes de journal = " + jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE center_id IN (?, ?)", Integer.class,
                CENTRE_A, CENTRE_B));
        l.add("interventions, lignes de cout et rectifications inchangees = " + empreinteDesInterventions().equals(avant));
        goldenLignes("bon_pl6_audit", l);
        assertEquals(avant, empreinteDesInterventions());
    }

    private UUID interventionPourAudit() {
        UUID i = intervention("I_AUDIT", generateur("G01"), CENTRE_A, x -> {
        });
        ligne(i, "PIECE", "Pompe", "1", "10.00");
        return i;
    }

    private String empreinteDesInterventions() {
        return jdbc.queryForList("SELECT id, statut, date_modification, modifie_par FROM gmao_interventions WHERE centre_id = ? ORDER BY id",
                CENTRE_A).toString()
                + jdbc.queryForObject("SELECT COUNT(*) FROM gmao_lignes_cout_intervention", Integer.class)
                + jdbc.queryForObject("SELECT COUNT(*) FROM gmao_rectifications_intervention", Integer.class);
    }
}
