package com.hemodialyse.backend.characterization.pilotagedirection.pl9;

import com.hemodialyse.backend.application.audit.AuditActionResolver;
import com.hemodialyse.backend.application.audit.AuditWriterService;
import com.hemodialyse.backend.characterization.pilotagedirection.PilotageDirectionCharacterizationSupport;
import com.hemodialyse.backend.infrastructure.security.AuditRequestFilter;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * PL9 : complément du filet du rapport PDF de la direction ({@code GET /api/v1/direction/report}, {@code GET /snapshots/{mois}/report},
 * {@code DirectionReportPdfService}) avant son déplacement vers le Pilotage via le port {@code documents.v1}. Complète PD4
 * ({@code LeRapportImprimeDeLaDirectionCharacterizationIntegrationTest}) sans le dupliquer : défauts et formes des paramètres de période,
 * gardes de société et combinaisons de rôles, montants énormes et arrondis, textes longs et hostiles, volume (32 centres, 36 mois),
 * en-tête complet (logo, mentions), instantanés atypiques, fuseau des horodatages, audit, méthodes et en-têtes. PDF observé par son TEXTE.
 */
class LeRapportDeLaDirectionPl9CharacterizationIntegrationTest extends PilotageDirectionCharacterizationSupport {

    private static final Pattern PERIODE = Pattern.compile("période (\\S+) au (\\S+) —");
    private static final Pattern HEURE = Pattern.compile("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}");

    @Autowired
    private AuditRequestFilter filtreDAudit;
    @Autowired
    private AuditWriterService ecrivainDAudit;

    @Override
    protected String slice() {
        return "pl9";
    }

    @Override
    protected String dossier() {
        return "rapport";
    }

    @AfterEach
    void rendreLIdentiteDeLaSociete() {
        jdbc.update("UPDATE societes SET logo = NULL, logo_content_type = NULL, nis = NULL, rc = NULL, email = NULL, site_web = NULL, wilaya = NULL "
                + "WHERE id = ?", SOCIETE);
    }

    // ------------------------------------------------------------------ outils

    private LocalDate nommee(LocalDate d) {
        return fixe((int) ChronoUnit.DAYS.between(REPERE, d));
    }

    private static RequestPostProcessor jeton(String utilisateur, String centre, String societe, String... roles) {
        List<GrantedAuthority> droits = new ArrayList<>();
        for (String r : roles) droits.add(new SimpleGrantedAuthority(r));
        return user(new UserPrincipal(utilisateur, centre, "u-pl9", "", droits, true, societe));
    }

    private Map<String, Object> pdf(MockHttpServletResponse r) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", r.getStatus());
        m.put("contentType", r.getContentType());
        m.put("cacheControl", r.getHeader("Cache-Control"));
        m.put("contentDisposition", r.getHeader("Content-Disposition"));
        byte[] corps = r.getContentAsByteArray();
        if (r.getStatus() == 200) {
            m.put("sections", sections(texte(corps)));
        } else {
            m.put("corps", corps.length == 0 ? "" : new String(corps, StandardCharsets.UTF_8));
        }
        return m;
    }

    /** Statut puis, si 200, la période imprimée ; sinon code et détail du refus. */
    private String resume(String chemin, RequestPostProcessor who) throws Exception {
        MockHttpServletResponse r = binaire(DIRECTION + chemin, who);
        if (r.getStatus() == 200) {
            Matcher m = PERIODE.matcher(texte(r.getContentAsByteArray()));
            return "200 " + (m.find() ? m.group(1) + " au " + m.group(2) : "<periode introuvable>");
        }
        return refusBrut(r);
    }

    /** Comme {@link #refus} (statut, code, détail sauf 5xx), à partir de la réponse brute. */
    private String refusBrut(MockHttpServletResponse r) throws Exception {
        String raw = r.getContentAsString(StandardCharsets.UTF_8);
        if (raw.isBlank()) return String.valueOf(r.getStatus());
        try {
            tools.jackson.databind.JsonNode c = mapper.readTree(raw);
            String code = c.has("code") ? " " + c.path("code").asString() : "";
            String detail = r.getStatus() < 500 && c.has("detail") ? " | " + c.path("detail").asString() : "";
            return r.getStatus() + code + detail;
        } catch (RuntimeException e) {
            return String.valueOf(r.getStatus());
        }
    }

    private static String texteBrut(byte[] pdf) throws Exception {
        try (PDDocument d = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(d).replaceAll("\\s+", " ").trim();
        }
    }

    private static int images(byte[] pdf) throws Exception {
        int n = 0;
        try (PDDocument d = PDDocument.load(pdf)) {
            for (PDPage p : d.getPages()) {
                for (COSName ignore : p.getResources().getXObjectNames()) n++;
            }
        }
        return n;
    }

    private static byte[] petitPng() throws Exception {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private void centreSupplementaire(String code, String nom) {
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", idStable("centre", code), code, nom, SOCIETE);
    }

    // ------------------------------------------------------------------ période

    @Test
    void les_defauts_et_les_formes_des_parametres_de_periode_du_rapport_en_direct() throws Exception {
        nommee(AUJOURDHUI_DU_PILOTAGE);
        fixe(0);
        fixe(100);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 sans parametre : du 1er janvier a aujourd'hui a Alger (et non a la date UTC)", resume("/report", direction()));
        m.put("02 fin seule", resume("/report?to=" + fixe(100), direction()));
        m.put("03 debut seul : fin = aujourd'hui", resume("/report?from=" + fixe(0), direction()));
        m.put("04 debut = fin", resume("/report?from=" + fixe(10) + "&to=" + fixe(10), direction()));
        m.put("05 cinq ans pile", resume("/report?from=" + fixe(0) + "&to=" + nommee(REPERE.plusYears(5)), direction()));
        m.put("06 cinq ans et un jour", resume("/report?from=" + fixe(0) + "&to=" + nommee(REPERE.plusYears(5).plusDays(1)), direction()));
        m.put("07 fin dans le futur (dans la limite des 5 ans)", resume("/report?from=" + nommee(AUJOURDHUI_DU_PILOTAGE) + "&to="
                + nommee(AUJOURDHUI_DU_PILOTAGE.plusYears(2)), direction()));
        m.put("08 debut tres ancien (1900) : plus de 5 ans", resume("/report?from=1900-01-01&to=" + fixe(0), direction()));
        nommee(LocalDate.of(2099, 1, 1));
        m.put("09 fin lointaine (2099) sans debut : le debut par defaut est le 1er janvier de l'annee de la FIN", resume("/report?to=" + nommee(LocalDate.of(2099, 12, 31)), direction()));
        m.put("10 debut vide", resume("/report?from=&to=" + fixe(100), direction()));
        m.put("11 fin vide", resume("/report?from=" + fixe(0) + "&to=", direction()));
        m.put("12 debut repete", resume("/report?from=" + fixe(0) + "&from=" + fixe(5) + "&to=" + fixe(100), direction()));
        m.put("13 jour inexistant (30 fevrier)", resume("/report?from=2026-02-30", direction()));
        m.put("14 date-heure", resume("/report?from=2026-01-01T00:00:00&to=" + fixe(100), direction()));
        m.put("15 sans tirets", resume("/report?from=20260101&to=" + fixe(100), direction()));
        m.put("16 jour sur un chiffre", resume("/report?from=2026-1-1&to=" + fixe(100), direction()));
        m.put("17 parametre inconnu ignore", resume("/report?foo=bar&from=" + fixe(0) + "&to=" + fixe(100), direction()));
        m.put("18 debut apres fin d'un jour", resume("/report?from=" + fixe(11) + "&to=" + fixe(10), direction()));
        m.put("19 annee bissextile : 29 fevrier 2028 accepte", resume("/report?from=" + nommee(LocalDate.of(2028, 2, 29)) + "&to=" + nommee(LocalDate.of(2028, 3, 1)), direction()));
        m.put("20 annee 0001", resume("/report?from=" + nommee(LocalDate.of(1, 1, 1)) + "&to=" + nommee(LocalDate.of(2, 1, 1)), direction()));
        arbrePd("periodes_par_defaut_et_formes", m);
    }

    // ------------------------------------------------------------------ gardes

    @Test
    void la_garde_de_societe_et_les_combinaisons_de_roles_du_rapport() throws Exception {
        jeuComplet();
        for (int i = 0; i < 5; i++) patientPd(CENTRE_AUTRE, "X" + i);
        String membre = U_ADMIN.toString();
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, String> jetons = new LinkedHashMap<>();
        jetons.put("DIRECTION nominale", resume("/report?from=" + fixe(0) + "&to=" + fixe(100), direction()));
        jetons.put("DIRECTION + ADMIN", resume("/report?from=" + fixe(0) + "&to=" + fixe(100), jeton(membre, null, SOCIETE.toString(), "ROLE_DIRECTION", "ROLE_ADMIN")));
        jetons.put("DIRECTION + SUPERADMIN", resume("/report?from=" + fixe(0) + "&to=" + fixe(100), jeton(membre, null, SOCIETE.toString(), "ROLE_DIRECTION", "ROLE_SUPERADMIN")));
        jetons.put("DIRECTION + MEDECIN", resume("/report?from=" + fixe(0) + "&to=" + fixe(100), jeton(membre, null, SOCIETE.toString(), "ROLE_DIRECTION", "ROLE_MEDECIN")));
        jetons.put("DIRECTION portant un centre", resume("/report?from=" + fixe(0) + "&to=" + fixe(100), jeton(membre, CENTRE_A.toString(), SOCIETE.toString(), "ROLE_DIRECTION")));
        jetons.put("jeton d'une autre societe, utilisateur membre de la premiere", resume("/report?from=" + fixe(0) + "&to=" + fixe(100), directionDe(U_ADMIN, AUTRE_SOCIETE)));
        jetons.put("utilisateur inconnu en base", resume("/report?from=" + fixe(0) + "&to=" + fixe(100), directionDe(UUID.randomUUID(), SOCIETE)));
        jetons.put("utilisateur inactif", resume("/report?from=" + fixe(0) + "&to=" + fixe(100), directionDe(U_DIRECTION_INACTIVE, SOCIETE)));
        jetons.put("utilisateur sans rattachement a une societe (jeton sans societe)", resume("/report?from=" + fixe(0) + "&to=" + fixe(100), directionDe(U_DIRECTION_DETACHEE, null)));
        jetons.put("utilisateur detache (jeton portant la societe)", resume("/report?from=" + fixe(0) + "&to=" + fixe(100), directionDe(U_DIRECTION_DETACHEE, SOCIETE)));
        jetons.put("societe inactive", resume("/report?from=" + fixe(0) + "&to=" + fixe(100), directionDe(U_DIRECTION_SOCIETE_INACTIVE, SOCIETE_INACTIVE)));
        jetons.put("societe du jeton blanche", resume("/report?from=" + fixe(0) + "&to=" + fixe(100), jeton(membre, null, "  ", "ROLE_DIRECTION")));
        jetons.put("societe du jeton illisible", resume("/report?from=" + fixe(0) + "&to=" + fixe(100), jeton(membre, null, "pas-un-uuid", "ROLE_DIRECTION")));
        jetons.put("identifiant d'utilisateur illisible", resume("/report?from=" + fixe(0) + "&to=" + fixe(100), jeton("abc", null, SOCIETE.toString(), "ROLE_DIRECTION")));
        jetons.put("role en minuscules", resume("/report?from=" + fixe(0) + "&to=" + fixe(100), jeton(membre, null, SOCIETE.toString(), "ROLE_direction")));
        jetons.put("role sans prefixe", resume("/report?from=" + fixe(0) + "&to=" + fixe(100), jeton(membre, null, SOCIETE.toString(), "DIRECTION")));
        m.put("01 jetons et rattachements", jetons);

        MockHttpServletResponse sansCentre = binaire(DIRECTION + "/report?from=" + fixe(0) + "&to=" + fixe(100), directionDe(U_DIRECTION_SANS_CENTRE, SOCIETE_SANS_CENTRE));
        m.put("02 societe active sans aucun centre : rapport vide", pdf(sansCentre));

        String duA = texteBrut(binaire(DIRECTION + "/report?from=" + fixe(0) + "&to=" + fixe(100), direction()).getContentAsByteArray());
        MockHttpServletResponse rAutre = binaire(DIRECTION + "/report?from=" + fixe(0) + "&to=" + fixe(100), directionAutre());
        String duAutre = texteBrut(rAutre.getContentAsByteArray());
        Map<String, Object> cloisonnement = new LinkedHashMap<>();
        cloisonnement.put("le rapport de la direction A cite le centre de l'autre societe", duA.contains("Centre Confidentiel"));
        cloisonnement.put("le rapport de la direction A cite l'autre societe", duA.contains("Autre Societe"));
        cloisonnement.put("le rapport de l'autre direction cite un centre de A", Pattern.compile("Centre [AB] [\\d<]").matcher(duAutre).find());
        cloisonnement.put("le rapport de l'autre direction cite la societe A", duAutre.contains("Clinique Pilotage"));
        cloisonnement.put("l'autre direction : finances", sections(duAutre).get("Finances et activité par centre"));
        m.put("03 cloisonnement des societes", cloisonnement);
        arbrePd("garde_de_societe_et_roles_nouveau_code", m);
    }

    // ------------------------------------------------------------------ contenu

    @Test
    void les_montants_enormes_les_arrondis_et_les_textes_longs_ou_hostiles_du_rapport() throws Exception {
        String nomLong = "Centre " + "N".repeat(60) + " & <b>\"q\"</b> ]]> $P{x} ' Œ€★中文العربية";
        jdbc.update("UPDATE centers SET name = ? WHERE id = ?", nomLong, CENTRE_A);
        jdbc.update("UPDATE centers SET name = ? WHERE id = ?", "<script>alert(1)</script>", CENTRE_B);
        jdbc.update("UPDATE societes SET raison_sociale = ?, adresse = ?, pied_page = ?, ville = ? WHERE id = ?",
                "Société " + "S".repeat(120) + " & Fils <i>SA</i> Œuvre €", "A".repeat(200) + " <rue> & \"quai\"", "P".repeat(400) + " ]]> $P{pied}",
                "Ville " + "V".repeat(80), SOCIETE);
        UUID[] caisse = caisse(CENTRE_A, "LONG", "CNAS " + "x".repeat(80) + " <&>");
        UUID a = patientPd(CENTRE_A, "A1", "M", LocalDate.of(1960, 6, 15), true, true, false, caisse[2]);
        UUID b = patientPd(CENTRE_B, "B1");
        facturePd(CENTRE_A, a, "ENORME", fixe(130), "800000000000.00", "952000000000.00", caisse[1]);
        facturePd(CENTRE_A, a, "AVOIR", fixe(160), "-100.00", "-119.00", caisse[1]);
        facturePd(CENTRE_A, a, "CENTIME", fixe(170), "0.01", "0.01", null);
        reglement(CENTRE_A, facturePd(CENTRE_A, a, "REGLEE", fixe(180), "1000.00", "1190.00", caisse[1]), "R", fixe(181), "500000000000.00");
        facturePd(CENTRE_B, b, "B_JAN", fixe(10), "2.50", "2.50", null);
        facturePd(CENTRE_B, b, "B_FEV", fixe(40), "3.50", "3.50", null);
        facturePd(CENTRE_B, b, "B_MARS", fixe(70), "0.50", "0.50", null);
        facturePd(CENTRE_B, b, "B_AVRIL", fixe(100), "1.50", "1.50", null);
        article(CENTRE_A, "ART", "99999999", "10", "123456789.55");
        arbrePd("montants_enormes_arrondis_et_textes_nouveau_code", pdf(binaire(DIRECTION + "/report?from=" + fixe(0) + "&to=" + fixe(200), direction())));
    }

    @Test
    void le_rapport_de_trente_deux_centres_sur_trente_six_mois_est_pagine() throws Exception {
        for (int i = 1; i <= 30; i++) centreSupplementaire(String.format("ZT-PD-N%02d", i), String.format("Centre N%02d", i));
        UUID[] cnas = caisse(CENTRE_A, "CNAS", "CNAS Nationale");
        UUID p = patientPd(CENTRE_A, "V1", "M", LocalDate.of(1960, 6, 15), false, false, false, cnas[2]);
        for (int i = 0; i < 36; i++) {
            facturePd(CENTRE_A, p, "MV" + i, LocalDate.of(2024, 1, 15).plusMonths(i), "1000.00", "1190.00", cnas[1]);
        }
        String chemin = "/report?from=" + nommee(LocalDate.of(2024, 1, 1)) + "&to=" + nommee(LocalDate.of(2026, 12, 31));
        MockHttpServletResponse r = binaire(DIRECTION + chemin, direction());
        Map<String, Object> m = pdf(r);
        Matcher pages = Pattern.compile("Page (\\d+) / (\\d+)").matcher(texteBrut(r.getContentAsByteArray()));
        LinkedHashSet<String> total = new LinkedHashSet<>();
        while (pages.find()) total.add(pages.group(2));
        m.put("nombre de pages annoncees", total);
        arbrePd("trente_deux_centres_trente_six_mois_nouveau_code", m);
    }

    @Test
    void l_en_tete_complet_avec_logo_mentions_legales_et_identites_degradees() throws Exception {
        patientPd(CENTRE_A, "A1");
        String chemin = DIRECTION + "/report?from=" + fixe(0) + "&to=" + fixe(100);
        Map<String, Object> m = new LinkedHashMap<>();
        jdbc.update("UPDATE societes SET nis = ?, rc = ?, email = ?, site_web = ?, wilaya = ? WHERE id = ?", "NIS-0042", "RC-16/00-1234567B01",
                "contact@clinique-pilotage.dz", "https://www.clinique-pilotage.dz", "Annaba", SOCIETE);
        m.put("01 mentions legales completes, sans logo", enTeteEtImages(binaire(chemin, direction())));
        jdbc.update("UPDATE societes SET logo = ?, logo_content_type = 'image/png' WHERE id = ?", petitPng(), SOCIETE);
        m.put("02 logo PNG valide", enTeteEtImages(binaire(chemin, direction())));
        jdbc.update("UPDATE societes SET logo = ? WHERE id = ?", new byte[]{(byte) 0xFF, (byte) 0xD8, 0, 1, 2, 3}, SOCIETE);
        m.put("03 logo dont le premier octet est 0xFF : declare JPEG, contenu illisible", enTeteEtImages(binaire(chemin, direction())));
        jdbc.update("UPDATE societes SET logo = ? WHERE id = ?", new byte[]{1, 2, 3, 4, 5, 6}, SOCIETE);
        m.put("04 logo en octets quelconques : declare PNG, contenu illisible", enTeteEtImages(binaire(chemin, direction())));
        jdbc.update("UPDATE societes SET logo = ? WHERE id = ?", new byte[]{1, 2}, SOCIETE);
        m.put("05 logo de deux octets : ignore", enTeteEtImages(binaire(chemin, direction())));
        jdbc.update("UPDATE societes SET logo = NULL, pied_page = '   ', raison_sociale = '   ', nis = '  ', rc = NULL WHERE id = ?", SOCIETE);
        m.put("06 raison sociale et pied de page blancs", enTeteEtImages(binaire(chemin, direction())));
        arbrePd("en_tete_complet_logo_et_mentions_nouveau_code", m);
    }

    private Map<String, Object> enTeteEtImages(MockHttpServletResponse r) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", r.getStatus());
        if (r.getStatus() == 200) {
            byte[] corps = r.getContentAsByteArray();
            m.put("images", images(corps));
            m.put("en-tete", sections(texte(corps)).get("en-tete"));
        } else {
            m.put("corps", r.getContentAsString(StandardCharsets.UTF_8));
        }
        return m;
    }

    // ------------------------------------------------------------------ instantanés

    @Test
    void le_rapport_mensuel_d_instantanes_atypiques_inconnus_ou_d_une_autre_societe() throws Exception {
        YearMonth m1 = mois(-1);
        nommerLaPeriodePrecedenteDe(m1, "<PERIODE_AVANT_M-1>");
        mois(0);
        mois(1);
        Map<String, Object> m = new LinkedHashMap<>();
        doPost(DIRECTION + "/snapshots/" + m1, "", directionAutre());
        Map<String, String> autre = new LinkedHashMap<>();
        autre.put("la direction A lit le mois figé par l'autre societe seulement", resume("/snapshots/" + m1 + "/report", direction()));
        autre.put("l'autre direction lit son propre mois", resume("/snapshots/" + m1 + "/report", directionAutre()));
        m.put("01 cloisonnement", autre);

        doPost(DIRECTION + "/snapshots/" + m1, "", direction());
        Map<String, String> chemins = new LinkedHashMap<>();
        chemins.put("mois courant non figé", resume("/snapshots/" + mois(0) + "/report", direction()));
        chemins.put("mois futur", resume("/snapshots/" + mois(1) + "/report", direction()));
        chemins.put("mois 2026-13", resume("/snapshots/2026-13/report", direction()));
        chemins.put("mois 2026-1", resume("/snapshots/2026-1/report", direction()));
        chemins.put("mois texte", resume("/snapshots/abc/report", direction()));
        chemins.put("mois avec jour", resume("/snapshots/2026-10-15/report", direction()));
        chemins.put("mois vide", resume("/snapshots//report", direction()));
        chemins.put("anonyme", refus(doGetAnonymous(DIRECTION + "/snapshots/" + m1 + "/report")));
        m.put("02 mois demandes", chemins);

        String debut = "{\"overview\":{\"societeNom\":\"Ancien\",\"from\":\"" + m1.atDay(1) + "\",\"to\":\"" + m1.atEndOfMonth() + "\",\"seuilAnonymat\":5,";
        Map<String, String> payloads = new LinkedHashMap<>();
        payloads.put("tout absent", "{\"overview\":{\"totaux\":null,\"centres\":null,\"mensuel\":null},\"indicators\":null}");
        payloads.put("objet vide", "{}");
        payloads.put("tableau", "[]");
        payloads.put("chaine", "\"x\"");
        payloads.put("null JSON", "null");
        payloads.put("totaux sans centres ni indicateurs",
                debut + "\"totaux\":{\"patients\":null,\"seances\":3,\"factures\":1,\"caHt\":1000.00,\"caTtc\":1190.00,\"encaisse\":600.00,"
                        + "\"resteARecouvrer\":590.00,\"tauxEncaissement\":50.4},\"centres\":null,\"mensuel\":null},\"indicators\":null}");
        StringBuilder alertes = new StringBuilder();
        for (int i = 0; i < 25; i++) {
            alertes.append(i == 0 ? "" : ",").append("{\"centre\":").append(i % 3 == 0 ? "null" : "\"Centre <&> " + i + "\"")
                    .append(",\"code\":\"").append(i % 2 == 0 ? "LOTS_PERIMES" : "CODE_" + i).append("\",\"severity\":\"")
                    .append(i % 4 == 0 ? "CRITICAL" : "WARNING").append("\",\"valeur\":").append(i % 5 == 0 ? "null" : String.valueOf(i * 1000000000000L)).append("}");
        }
        payloads.put("25 alertes, centres hostiles ou absents, valeurs enormes",
                debut + "\"totaux\":{\"patients\":1000000000000,\"seances\":9999999999,\"factures\":1,\"caHt\":123456789012345.50,\"caTtc\":0.5,"
                        + "\"encaisse\":-5.5,\"resteARecouvrer\":1.5,\"tauxEncaissement\":100.0},"
                        + "\"centres\":[{\"nom\":\"<script>x</script> ]]> " + "L".repeat(300) + "\",\"patients\":12,\"patientsSousKt\":0,\"seances\":1,\"factures\":1,"
                        + "\"caHt\":2.5,\"caTtc\":3.5,\"encaisse\":0.5,\"resteARecouvrer\":1.5,\"tauxEncaissement\":0.0},{\"nom\":null,\"patients\":null}],"
                        + "\"mensuel\":[{\"mois\":null,\"seances\":null,\"caHt\":null,\"caTtc\":null},{\"mois\":\"2024-01\",\"seances\":2,\"caHt\":2.5,\"caTtc\":3.5}]},"
                        + "\"indicators\":{\"centres\":[],\"totaux\":{\"clinique\":{},\"stock\":{}},\"alertes\":[" + alertes + "]}}");
        Map<String, Object> variantes = new LinkedHashMap<>();
        for (Map.Entry<String, String> p : payloads.entrySet()) {
            jdbc.update("UPDATE direction_snapshot SET payload = ? WHERE societe_id = ? AND mois = ?", p.getValue(), SOCIETE, m1.toString());
            MockHttpServletResponse r = binaire(DIRECTION + "/snapshots/" + m1 + "/report", direction());
            if (r.getStatus() == 200) {
                Map<String, String> s = sections(texte(r.getContentAsByteArray()));
                // l'horodatage de l'instantané figé (colonne de création) est celui de l'horloge réelle : masqué par texte()
                variantes.put(p.getKey(), s);
            } else {
                variantes.put(p.getKey(), refusBrut(r));
            }
        }
        m.put("03 contenus figes atypiques", variantes);
        arbrePd("rapport_mensuel_atypique_nouveau_code", m);
    }

    // ------------------------------------------------------------------ fuseau

    @Test
    void les_horodatages_du_rapport_suivent_l_horloge_du_pilotage_et_son_fuseau_de_reference() throws Exception {
        YearMonth m1 = mois(-1);
        nommerLaPeriodePrecedenteDe(m1, "<PERIODE_AVANT_M-1>");
        Map<String, Object> m = new LinkedHashMap<>();
        MockHttpServletResponse direct = binaire(DIRECTION + "/report?from=" + fixe(0) + "&to=" + fixe(100), direction());
        m.put("01 rapport en direct : horodatages imprimes (horloge figee a 23:30 UTC la veille = 00:30 a Alger)",
                distincts(texteBrut(direct.getContentAsByteArray())));
        doPost(DIRECTION + "/snapshots/" + m1, "", direction());
        MockHttpServletResponse fige = binaire(DIRECTION + "/snapshots/" + m1 + "/report", direction());
        m.put("02 rapport d'un instantane fige a la meme horloge", distincts(texteBrut(fige.getContentAsByteArray())));
        jdbc.update("UPDATE direction_snapshot SET payload = ? WHERE societe_id = ? AND mois = ?",
                "{\"overview\":{\"societeNom\":\"Ancien\",\"from\":\"" + m1.atDay(1) + "\",\"to\":\"" + m1.atEndOfMonth()
                        + "\",\"seuilAnonymat\":5,\"totaux\":null,\"centres\":null,\"mensuel\":null},\"indicators\":null}", SOCIETE, m1.toString());
        MockHttpServletResponse sansHorodatage = binaire(DIRECTION + "/snapshots/" + m1 + "/report", direction());
        m.put("03 instantane dont le contenu ne porte aucune date de generation", distincts(texteBrut(sansHorodatage.getContentAsByteArray())));
        arbrePd("horodatages_et_fuseau_nouveau_code", m);
    }

    private static List<String> distincts(String texte) {
        LinkedHashSet<String> s = new LinkedHashSet<>();
        Matcher mm = HEURE.matcher(texte);
        while (mm.find()) {
            s.add(mm.group().equals("10/11/2026 23:30") ? "<HEURE_UTC_DE_L_HORLOGE_FIGEE>"
                    : mm.group().equals("11/11/2026 00:30") ? "<HEURE_D_ALGER_DE_L_HORLOGE_FIGEE>" : "<HEURE_REELLE_DE_LA_JVM>");
        }
        return new ArrayList<>(s);
    }

    // ------------------------------------------------------------------ audit

    @Test
    void le_rapport_et_les_instantanes_dans_le_journal_d_audit() throws Exception {
        YearMonth m1 = mois(-1);
        nommerLaPeriodePrecedenteDe(m1, "<PERIODE_AVANT_M-1>");
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(roleScopeFilter, filtreDAudit).build();
        ecrivainDAudit.flush();
        jdbc.update("DELETE FROM audit_log WHERE route_template LIKE '%/direction/%'");
        binaire(DIRECTION + "/report?from=" + fixe(0) + "&to=" + fixe(100), direction());
        binaire(DIRECTION + "/report?from=" + fixe(100) + "&to=" + fixe(0), direction());
        binaire(DIRECTION + "/report?from=" + fixe(0) + "&to=" + fixe(100), as(CENTRE_A, "ADMIN"));
        binaire(DIRECTION + "/report", directionAutre());
        doPost(DIRECTION + "/snapshots/" + m1, "", direction());
        binaire(DIRECTION + "/snapshots/" + m1 + "/report", direction());
        binaire(DIRECTION + "/snapshots/" + m1 + "/report", as(CENTRE_A, "SECRETAIRE"));
        ecrivainDAudit.flush();
        List<String> lignes = new ArrayList<>();
        lignes.add("GET /report suivi = " + AuditActionResolver.isTracked("GET", DIRECTION + "/report"));
        lignes.add("GET /snapshots/{mois}/report suivi = " + AuditActionResolver.isTracked("GET", DIRECTION + "/snapshots/{mois}/report"));
        lignes.add("POST /snapshots/{mois} suivi = " + AuditActionResolver.isTracked("POST", DIRECTION + "/snapshots/" + m1));
        for (Map<String, Object> row : jdbc.queryForList("SELECT action_code, http_method, route_template, status_code, COUNT(*) AS n "
                + "FROM audit_log WHERE route_template LIKE '%/direction/%' GROUP BY action_code, http_method, route_template, status_code "
                + "ORDER BY route_template, http_method, status_code")) {
            lignes.add("journal : " + row.get("ACTION_CODE") + " " + row.get("HTTP_METHOD") + " " + row.get("ROUTE_TEMPLATE") + " "
                    + row.get("STATUS_CODE") + " x" + row.get("N"));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lignes", lignes);
        jdbc.update("DELETE FROM audit_log WHERE route_template LIKE '%/direction/%'");
        arbrePd("audit_nouveau_code", m);
    }

    // ------------------------------------------------------------------ méthodes et en-têtes

    private String etat(MockHttpServletRequestBuilder requete) throws Exception {
        MockHttpServletResponse r = mvc.perform(requete).andReturn().getResponse();
        String type = r.getContentType() == null ? "-" : r.getContentType().split(";")[0];
        return r.getStatus() + " type=" + type + " allow=" + r.getHeader("Allow") + " cache=" + r.getHeader("Cache-Control")
                + " longueur-du-corps=" + (r.getStatus() == 200 && r.getContentAsByteArray().length > 4 ? "pdf" : r.getContentAsByteArray().length);
    }

    @Test
    void les_methodes_les_chemins_voisins_et_les_en_tetes_accept_du_rapport() throws Exception {
        YearMonth m1 = mois(-1);
        nommerLaPeriodePrecedenteDe(m1, "<PERIODE_AVANT_M-1>");
        doPost(DIRECTION + "/snapshots/" + m1, "", direction());
        String q = "?from=" + fixe(0) + "&to=" + fixe(100);
        Map<String, String> cas = new LinkedHashMap<>();
        for (String methode : List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")) {
            cas.put(methode + " /report", etat(request(HttpMethod.valueOf(methode), java.net.URI.create(DIRECTION + "/report" + q)).with(direction())));
        }
        cas.put("POST /snapshots/<M-1>/report", etat(request(HttpMethod.POST, java.net.URI.create(DIRECTION + "/snapshots/" + m1 + "/report")).with(direction())));
        cas.put("HEAD /snapshots/<M-1>/report", etat(request(HttpMethod.HEAD, java.net.URI.create(DIRECTION + "/snapshots/" + m1 + "/report")).with(direction())));
        cas.put("POST /report anonyme", etat(request(HttpMethod.POST, java.net.URI.create(DIRECTION + "/report" + q))));
        cas.put("GET /report/ (barre finale)", etat(request(HttpMethod.GET, java.net.URI.create(DIRECTION + "/report/" + q)).with(direction())));
        cas.put("GET /report.pdf", etat(request(HttpMethod.GET, java.net.URI.create(DIRECTION + "/report.pdf" + q)).with(direction())));
        cas.put("GET /Report (casse)", etat(request(HttpMethod.GET, java.net.URI.create(DIRECTION + "/Report" + q)).with(direction())));
        cas.put("GET /report/<M-1> (sous-chemin)", etat(request(HttpMethod.GET, java.net.URI.create(DIRECTION + "/report/" + m1)).with(direction())));
        cas.put("GET /snapshots/report (mois = report)", etat(request(HttpMethod.GET, java.net.URI.create(DIRECTION + "/snapshots/report")).with(direction())));
        for (String accept : List.of("application/pdf", "application/json", "text/html", "*/*", "text/plain", "application/xml", "n'importe quoi")) {
            cas.put("GET /report Accept: " + accept, etat(request(HttpMethod.GET, java.net.URI.create(DIRECTION + "/report" + q))
                    .header("Accept", accept).with(direction())));
        }
        cas.put("GET /snapshots/<M-1>/report Accept: application/json", etat(request(HttpMethod.GET,
                java.net.URI.create(DIRECTION + "/snapshots/" + m1 + "/report")).header("Accept", "application/json").with(direction())));
        cas.put("GET /report avec corps JSON", etat(request(HttpMethod.GET, java.net.URI.create(DIRECTION + "/report" + q))
                .contentType("application/json").content("{\"from\":\"2020-01-01\"}").with(direction())));
        arbrePd("methodes_chemins_et_accept_nouveau_code", Map.of("cas", cas));
    }

    @SuppressWarnings("unused")
    private static BigDecimal inutile() {
        return BigDecimal.ZERO;
    }
}
