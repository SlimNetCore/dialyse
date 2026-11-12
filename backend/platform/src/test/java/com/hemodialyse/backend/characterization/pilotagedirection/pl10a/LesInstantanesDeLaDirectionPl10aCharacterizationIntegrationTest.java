package com.hemodialyse.backend.characterization.pilotagedirection.pl10a;

import com.hemodialyse.backend.application.audit.AuditActionResolver;
import com.hemodialyse.backend.application.audit.AuditWriterService;
import com.hemodialyse.backend.characterization.pilotagedirection.PilotageDirectionCharacterizationSupport;
import com.hemodialyse.backend.infrastructure.security.AuditRequestFilter;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * PL10a : complément du filet des instantanés mensuels de la direction ({@code GET /api/v1/direction/snapshots}, {@code GET /snapshots/{mois}},
 * {@code POST /snapshots/{mois}} ; contrôleur {@code DirectionSnapshotRestController}, table {@code direction_snapshot}). Complète PD2
 * ({@code LesInstantanesMensuelsDeLaDirection...}, cycle de vie, mois refusés, cloisonnement, tâche planifiée, masque à la relecture) et PL9
 * (rapport {@code /snapshots/{mois}/report}) sans les dupliquer : rôles, jetons et garde de société sur les trois routes, formes illisibles du
 * mois dans le chemin, paramètres de requête sans effet, contenus stockés atypiques (vide, non JSON, trop profond, gros), seuil d'anonymat à
 * la capture (4, 5, 6), instantanés hors bornes déjà présents, tri de la liste, forme des horodatages, concurrence, audit, méthodes et en-têtes.
 */
class LesInstantanesDeLaDirectionPl10aCharacterizationIntegrationTest extends PilotageDirectionCharacterizationSupport {

    private static final String SNAPSHOTS = DIRECTION + "/snapshots";

    @Autowired
    private AuditRequestFilter filtreDAudit;
    @Autowired
    private AuditWriterService ecrivainDAudit;

    @Override
    protected String slice() {
        return "pl10a";
    }

    @Override
    protected String dossier() {
        return "instantanes";
    }

    // ------------------------------------------------------------------ outils

    private static RequestPostProcessor jeton(String utilisateur, String centre, String societe, String... roles) {
        List<GrantedAuthority> droits = new ArrayList<>();
        for (String r : roles) droits.add(new SimpleGrantedAuthority(r));
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .user(new UserPrincipal(utilisateur, centre, "u-pl10a", "", droits, true, societe));
    }

    private Resp doPostAnonyme(String chemin) throws Exception {
        return send(post(java.net.URI.create(chemin)));
    }

    private JsonNode noeud(Resp r) {
        if (r.raw() == null || r.raw().isBlank()) return mapper.valueToTree("<corps vide>");
        try {
            return mapper.readTree(r.raw());
        } catch (RuntimeException e) {
            return mapper.valueToTree("<pas du JSON : " + r.raw().length() + " caracteres>");
        }
    }

    /** Statut et code ; pour une liste servie, le nombre de mois. */
    private String etat(Resp r, boolean liste) {
        String s = refus(r);
        if (liste && r.status() == 200) s += " x" + noeud(r).size();
        return s;
    }

    private String ligneDeRoutes(RequestPostProcessor who, YearMonth m) throws Exception {
        return "liste : " + etat(doGet(SNAPSHOTS, who), true) + " | lecture : " + etat(doGet(SNAPSHOTS + "/" + m, who), false)
                + " | capture : " + etat(doPost(SNAPSHOTS + "/" + m, "", who), false);
    }

    private String formes(String texte) {
        return texte == null ? null : texte.replaceAll("\\d", "9");
    }

    // ------------------------------------------------------------------ garde et rôles

    @Test
    void les_trois_routes_selon_le_role_le_jeton_et_la_societe() throws Exception {
        YearMonth m1 = mois(-1);
        nommerLaPeriodePrecedenteDe(m1, "<PERIODE_AVANT_M-1>");
        doPost(SNAPSHOTS + "/" + m1, "", direction());
        String membre = U_ADMIN.toString();
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, String> profils = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profils().entrySet()) {
            profils.put(p.getKey(), ligneDeRoutes(p.getValue(), m1));
        }
        profils.put("DIRECTION portant un centre", ligneDeRoutes(jeton(membre, CENTRE_A.toString(), SOCIETE.toString(), "ROLE_DIRECTION"), m1));
        m.put("01 profils de Planification (le profil DIRECTION est le nominal)", profils);
        Map<String, String> cumuls = new LinkedHashMap<>();
        cumuls.put("DIRECTION + ADMIN", ligneDeRoutes(jeton(membre, null, SOCIETE.toString(), "ROLE_DIRECTION", "ROLE_ADMIN"), m1));
        cumuls.put("DIRECTION + SUPERADMIN", ligneDeRoutes(jeton(membre, null, SOCIETE.toString(), "ROLE_DIRECTION", "ROLE_SUPERADMIN"), m1));
        cumuls.put("DIRECTION + MEDECIN", ligneDeRoutes(jeton(membre, null, SOCIETE.toString(), "ROLE_DIRECTION", "ROLE_MEDECIN"), m1));
        cumuls.put("SUPERADMIN sans societe", ligneDeRoutes(jeton(membre, null, null, "ROLE_SUPERADMIN"), m1));
        cumuls.put("role en minuscules", ligneDeRoutes(jeton(membre, null, SOCIETE.toString(), "ROLE_direction"), m1));
        cumuls.put("role sans prefixe", ligneDeRoutes(jeton(membre, null, SOCIETE.toString(), "DIRECTION"), m1));
        m.put("02 cumuls de roles", cumuls);
        Map<String, String> gardes = new LinkedHashMap<>();
        gardes.put("jeton d'une autre societe, utilisateur membre de la premiere", ligneDeRoutes(directionDe(U_ADMIN, AUTRE_SOCIETE), m1));
        gardes.put("utilisateur inconnu en base", ligneDeRoutes(directionDe(UUID.randomUUID(), SOCIETE), m1));
        gardes.put("utilisateur inactif", ligneDeRoutes(directionDe(U_DIRECTION_INACTIVE, SOCIETE), m1));
        gardes.put("utilisateur sans rattachement (jeton sans societe)", ligneDeRoutes(directionDe(U_DIRECTION_DETACHEE, null), m1));
        gardes.put("utilisateur detache (jeton portant la societe)", ligneDeRoutes(directionDe(U_DIRECTION_DETACHEE, SOCIETE), m1));
        gardes.put("societe inactive", ligneDeRoutes(directionDe(U_DIRECTION_SOCIETE_INACTIVE, SOCIETE_INACTIVE), m1));
        gardes.put("societe du jeton blanche", ligneDeRoutes(jeton(membre, null, "  ", "ROLE_DIRECTION"), m1));
        gardes.put("societe du jeton illisible", ligneDeRoutes(jeton(membre, null, "pas-un-uuid", "ROLE_DIRECTION"), m1));
        gardes.put("identifiant d'utilisateur illisible", ligneDeRoutes(jeton("abc", null, SOCIETE.toString(), "ROLE_DIRECTION"), m1));
        gardes.put("societe active sans centre", ligneDeRoutes(directionDe(U_DIRECTION_SANS_CENTRE, SOCIETE_SANS_CENTRE), m1));
        m.put("03 garde de societe", gardes);
        Map<String, String> anonyme = new LinkedHashMap<>();
        anonyme.put("liste", etat(doGetAnonymous(SNAPSHOTS), false));
        anonyme.put("lecture", etat(doGetAnonymous(SNAPSHOTS + "/" + m1), false));
        anonyme.put("capture", etat(doPostAnonyme(SNAPSHOTS + "/" + m1), false));
        m.put("04 anonyme", anonyme);
        m.put("05 instantanes en base apres la matrice (societes actives : une capture par societe autorisee)",
                lignes("SELECT societe_id, mois FROM direction_snapshot WHERE societe_id IN (?, ?, ?, ?) ORDER BY societe_id, mois",
                        SOCIETE, AUTRE_SOCIETE, SOCIETE_INACTIVE, SOCIETE_SANS_CENTRE));
        arbrePd("garde_roles_et_jetons", m);
    }

    // ------------------------------------------------------------------ mois du chemin et paramètres

    @Test
    void les_formes_illisibles_du_mois_dans_le_chemin_et_les_parametres_sans_effet() throws Exception {
        YearMonth m1 = mois(-1);
        nommerLaPeriodePrecedenteDe(m1, "<PERIODE_AVANT_M-1>");
        doPost(SNAPSHOTS + "/" + m1, "", direction());
        Map<String, String> segments = new LinkedHashMap<>();
        segments.put("annee sur deux chiffres", "26-01");
        segments.put("annee sur cinq chiffres", "12026-01");
        segments.put("espace final", m1 + "%20");
        segments.put("espace initial", "%20" + m1);
        segments.put("retour ligne final", m1 + "%0A");
        segments.put("tiret long", "2026%E2%80%9301");
        segments.put("chiffres arabes-indiens", "%D9%A2%D9%A0%D9%A2%D9%A6-%D9%A0%D9%A1");
        segments.put("barre oblique encodee", "2026%2F01");
        segments.put("point-virgule (parametre de chemin)", m1 + ";x=1");
        segments.put("octet nul", m1 + "%00");
        segments.put("extension .json", m1 + ".json");
        segments.put("null", "null");
        segments.put("undefined", "undefined");
        segments.put("annee 0000", "0000-01");
        segments.put("annee 0001", "0001-01");
        segments.put("annee 9999", "9999-12");
        segments.put("mois 99", "2026-99");
        segments.put("barre finale", m1 + "/");
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> cas = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : segments.entrySet()) {
            cas.put(e.getKey(), "lecture : " + etat(doGet(SNAPSHOTS + "/" + e.getValue(), direction()), false)
                    + " | capture : " + etat(doPost(SNAPSHOTS + "/" + e.getValue(), "", direction()), false));
        }
        m.put("01 formes du mois (le mois figé existe : " + m1 + ")", cas);
        Resp liste = doGet(SNAPSHOTS, direction());
        Resp lecture = doGet(SNAPSHOTS + "/" + m1, direction());
        Resp capture = doPost(SNAPSHOTS + "/" + m1, "", direction());
        Map<String, Object> params = new LinkedHashMap<>();
        String[] parasites = {"?societeId=" + AUTRE_SOCIETE, "?mois=2020-05","?from=hier&to=demain", "?centreId=" + CENTRE_A,
                "?page=5&size=1&sort=mois,asc"};
        for (String q : parasites) {
            String nom = q.substring(1, q.indexOf('='));
            params.put("liste, parametre " + nom, "meme corps : " + liste.raw().equals(doGet(SNAPSHOTS + q, direction()).raw()));
            params.put("lecture, parametre " + nom, "meme corps : " + lecture.raw().equals(doGet(SNAPSHOTS + "/" + m1 + q, direction()).raw()));
            params.put("capture, parametre " + nom, "meme corps : " + capture.raw().equals(doPost(SNAPSHOTS + "/" + m1 + q, "", direction()).raw()));
        }
        m.put("02 parametres de requete : la societe vient toujours de la session", params);
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("capture avec un corps JSON quelconque", etat(doPost(SNAPSHOTS + "/" + m1, "{\"mois\":\"2020-01\",\"societeId\":\"" + AUTRE_SOCIETE + "\"}", direction()), false));
        corps.put("capture avec un corps illisible", etat(doPost(SNAPSHOTS + "/" + m1, "{pas du json", direction()), false));
        corps.put("lignes de l'autre societe", jdbc.queryForObject("SELECT COUNT(*) FROM direction_snapshot WHERE societe_id = ?", Integer.class, AUTRE_SOCIETE));
        m.put("03 corps de la capture ignore", corps);
        arbrePd("mois_du_chemin_et_parametres", m);
    }

    // ------------------------------------------------------------------ contenus stockés

    @Test
    void les_contenus_stockes_atypiques_sont_servis_tels_quels_ou_refuses_en_422() throws Exception {
        YearMonth m1 = mois(-1);
        nommerLaPeriodePrecedenteDe(m1, "<PERIODE_AVANT_M-1>");
        doPost(SNAPSHOTS + "/" + m1, "", direction());
        Map<String, String> contenus = new LinkedHashMap<>();
        contenus.put("chaine vide", "");
        contenus.put("blancs", "   ");
        contenus.put("objet vide", "{}");
        contenus.put("tableau vide", "[]");
        contenus.put("null JSON", "null");
        contenus.put("nombre", "42");
        contenus.put("overview texte, breakdown nombre", "{\"overview\":\"x\",\"indicators\":[],\"breakdown\":5}");
        contenus.put("centres qui ne sont pas des objets", "{\"overview\":{\"totaux\":{\"patients\":10},\"centres\":[1,\"a\",null]}}");
        contenus.put("effectifs en texte ou decimaux", "{\"overview\":{\"totaux\":{\"patients\":\"12\"},\"centres\":[{\"patients\":\"7\"},{\"patients\":7.9}]}}");
        contenus.put("centre sous le seuil stocke en clair (seuil stocke 2) : la relecture ne masque pas un centre isole",
                "{\"overview\":{\"seuilAnonymat\":2,\"totaux\":{\"patients\":3},\"centres\":[{\"nom\":\"Petit\",\"patients\":3}]}}");
        contenus.put("cle dupliquee", "{\"overview\":{\"a\":1,\"a\":2}}");
        contenus.put("texte apres le JSON", "{\"overview\":{\"x\":1}} garbage");
        contenus.put("marque d'ordre des octets", "﻿{\"overview\":{}}");
        contenus.put("commentaire", "/* c */ {\"overview\":{}}");
        contenus.put("NaN non standard", "{\"overview\":{\"v\":NaN}}");
        contenus.put("JSON tronque", "{\"overview\":{\"totaux\":");
        contenus.put("profondeur 2000", "[".repeat(2000) + "]".repeat(2000));
        contenus.put("nombre astronomique", "{\"overview\":{\"v\":1e999999,\"w\":123456789012345678901234567890}}");
        contenus.put("accents, emoji, caracteres de controle echappes", "{\"overview\":{\"societeNom\":\"Soci\\u00e9t\\u00e9 \\ud83d\\ude00 \\u0000 <b>\"}}");
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> servis = new LinkedHashMap<>();
        for (Map.Entry<String, String> p : contenus.entrySet()) {
            jdbc.update("UPDATE direction_snapshot SET payload = ? WHERE societe_id = ? AND mois = ?", p.getValue(), SOCIETE, m1.toString());
            Resp lecture = doGet(SNAPSHOTS + "/" + m1, direction());
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("lecture", refus(lecture));
            if (lecture.status() == 200) ligne.put("corps", noeud(lecture));
            ligne.put("capture (deja figee)", etat(doPost(SNAPSHOTS + "/" + m1, "", direction()), false));
            ligne.put("liste", etat(doGet(SNAPSHOTS, direction()), true));
            ligne.put("contenu stocke inchange", p.getValue().equals(jdbc.queryForObject(
                    "SELECT payload FROM direction_snapshot WHERE societe_id = ? AND mois = ?", String.class, SOCIETE, m1.toString())));
            servis.put(p.getKey(), ligne);
        }
        m.put("01 contenus", servis);
        // gros instantane : 3000 centres, 24 mois, 2000 alertes, un centre masque parmi les 3000
        StringBuilder gros = new StringBuilder("{\"overview\":{\"societeNom\":\"Gros\",\"totaux\":{\"patients\":36000,\"seances\":9},\"centres\":[");
        for (int i = 0; i < 3000; i++) {
            gros.append(i == 0 ? "" : ",").append("{\"nom\":\"Centre ").append(i).append("\",\"patients\":").append(i == 1234 ? "null" : "12").append(",\"seances\":3}");
        }
        gros.append("],\"mensuel\":[");
        for (int i = 0; i < 24; i++) gros.append(i == 0 ? "" : ",").append("{\"mois\":\"").append(2024 + i / 12).append("-").append(String.format("%02d", i % 12 + 1)).append("\",\"seances\":1}");
        gros.append("]},\"indicators\":{\"centres\":[],\"alertes\":[");
        for (int i = 0; i < 2000; i++) gros.append(i == 0 ? "" : ",").append("{\"code\":\"A").append(i).append("\",\"severity\":\"WARNING\"}");
        gros.append("]}}");
        jdbc.update("UPDATE direction_snapshot SET payload = ? WHERE societe_id = ? AND mois = ?", gros.toString(), SOCIETE, m1.toString());
        Resp lecture = doGet(SNAPSHOTS + "/" + m1, direction());
        JsonNode c = noeud(lecture);
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("statut", refus(lecture));
        g.put("taille du contenu stocke (caracteres)", gros.length());
        g.put("taille de la reponse >= contenu stocke", lecture.raw().length() >= gros.length() - 100);
        g.put("centres servis", c.path("overview").path("centres").size());
        g.put("alertes servies", c.path("indicators").path("alertes").size());
        g.put("mois servis", c.path("overview").path("mensuel").size());
        g.put("totaux.patients : supprime car un centre est masque", c.path("overview").path("totaux").path("patients").isNull());
        g.put("totaux.seances", c.path("overview").path("totaux").path("seances").asString());
        g.put("capture (deja figee)", etat(doPost(SNAPSHOTS + "/" + m1, "", direction()), false));
        m.put("02 gros instantane", g);
        arbrePd("contenus_stockes_atypiques", m);
    }

    // ------------------------------------------------------------------ seuil d'anonymat à la capture

    @Test
    void la_capture_masque_les_effectifs_de_un_a_quatre_et_supprime_les_totaux_qui_les_composent() throws Exception {
        for (int i = 1; i <= 4; i++) patientPd(CENTRE_A, "A" + i);
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, String> brut = new LinkedHashMap<>();
        YearMonth[] mois = {mois(-1), mois(-2), mois(-3), mois(-4), mois(-5)};
        String[] etapes = {"A=4, B=0", "A=4, B=3", "A=5, B=5", "A=6, B=5", "A=6, B=1"};
        Map<String, Object> captures = new LinkedHashMap<>();
        Map<String, String> bruts = new LinkedHashMap<>();
        for (int k = 0; k < mois.length; k++) {
            if (k == 1) for (int i = 1; i <= 3; i++) patientPd(CENTRE_B, "B" + i);
            if (k == 2) {
                patientPd(CENTRE_A, "A5");
                patientPd(CENTRE_B, "B4");
                patientPd(CENTRE_B, "B5");
            }
            if (k == 3) patientPd(CENTRE_A, "A6");
            if (k == 4) jdbc.update("DELETE FROM patients WHERE center_id = ? AND code_patient IN ('PD-B2','PD-B3','PD-B4','PD-B5')", CENTRE_B);
            Resp r = doPost(SNAPSHOTS + "/" + mois[k], "", direction());
            bruts.put(etapes[k], r.raw());
            Map<String, Object> projection = new LinkedHashMap<>();
            projection.put("statut", refus(r));
            JsonNode overview = noeud(r).path("overview");
            List<String> centres = new ArrayList<>();
            overview.path("centres").forEach(c -> centres.add(c.path("nom").asString() + " : patients " + (c.path("patients").isNull() ? "masque" : c.path("patients").asString())));
            projection.put("centres", centres);
            projection.put("totaux.patients", overview.path("totaux").path("patients").isNull() ? "masque" : overview.path("totaux").path("patients").asString());
            projection.put("totaux.patientsSousKt", overview.path("totaux").path("patientsSousKt").isNull() ? "masque" : overview.path("totaux").path("patientsSousKt").asString());
            projection.put("seuilAnonymat", overview.path("seuilAnonymat").asString());
            String stocke = jdbc.queryForObject("SELECT payload FROM direction_snapshot WHERE societe_id = ? AND mois = ?", String.class, SOCIETE, mois[k].toString());
            projection.put("contenu stocke deja masque (aucun effectif brut de 1 a 4)", !stocke.matches("(?s).*\"patients\":[1-4][,}].*"));
            captures.put("étape " + (k + 1) + " : " + etapes[k] + " patients, mois " + mois[k], projection);
        }
        m.put("01 captures successives (les effectifs changent entre deux captures)", captures);
        Map<String, Object> relectures = new LinkedHashMap<>();
        for (int k = 0; k < mois.length; k++) {
            relectures.put("étape " + (k + 1), "relecture identique a la capture : " + bruts.get(etapes[k]).equals(doGet(SNAPSHOTS + "/" + mois[k], direction()).raw()));
        }
        m.put("02 relectures apres changement des effectifs", relectures);
        arbrePd("seuil_d_anonymat_a_la_capture", m);
    }

    // ------------------------------------------------------------------ hors bornes, tri, horodatages, idempotence

    @Test
    void les_instantanes_hors_bornes_la_liste_triee_les_horodatages_et_la_capture_concurrente() throws Exception {
        YearMonth m1 = mois(-1);
        Map<String, Object> m = new LinkedHashMap<>();
        // instantanes deja presents hors des bornes de capture
        String vide = "{\"overview\":{\"societeNom\":\"Present\",\"totaux\":{\"patients\":12}},\"indicators\":{\"centres\":[]}}";
        for (int decalage : new int[]{-61, -120, 0, 1, 14}) {
            jdbc.update("INSERT INTO direction_snapshot (id, societe_id, mois, payload, generated_at) VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), SOCIETE,
                    mois(decalage).toString(), vide, CREATION_FIXE);
        }
        Map<String, Object> hors = new LinkedHashMap<>();
        for (int decalage : new int[]{-61, -120, 0, 1, 14}) {
            hors.put("mois " + mois(decalage), "lecture : " + etat(doGet(SNAPSHOTS + "/" + mois(decalage), direction()), false)
                    + " | capture : " + etat(doPost(SNAPSHOTS + "/" + mois(decalage), "", direction()), false));
        }
        m.put("01 instantane deja present mais hors des bornes de capture : lu, jamais re-capturable", hors);
        // lignes aux mois non canoniques
        for (String bizarre : new String[]{"2026-1", "abc", "0001-01", "26-1-01"}) {
            jdbc.update("INSERT INTO direction_snapshot (id, societe_id, mois, payload, generated_at) VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), SOCIETE,
                    bizarre, "{}", CREATION_FIXE);
        }
        m.put("02 liste (tri par mois decroissant, texte compris les mois non canoniques)", mapper.valueToTree(
                noeudMois(doGet(SNAPSHOTS, direction()))));
        Map<String, Object> lecturesBizarres = new LinkedHashMap<>();
        for (String bizarre : new String[]{"2026-1", "abc", "0001-01", "26-1-01"}) {
            lecturesBizarres.put(bizarre, etat(doGet(SNAPSHOTS + "/" + bizarre, direction()), false));
        }
        m.put("03 lecture des mois non canoniques presents en base", lecturesBizarres);
        jdbc.update("DELETE FROM direction_snapshot WHERE societe_id = ?", SOCIETE);
        // horodatages
        Map<String, Object> horo = new LinkedHashMap<>();
        Resp capture = doPost(SNAPSHOTS + "/" + m1, "", direction());
        String genere = noeud(capture).path("generatedAt").asString();
        OffsetDateTime g = OffsetDateTime.parse(genere);
        horo.put("forme de generatedAt apres capture", formes(genere));
        horo.put("capture horodatee par la base (heure reelle), pas par l'horloge figee du Pilotage (23:30 UTC la veille)", g.toInstant().isAfter(INSTANT_FIXE));
        jdbc.update("INSERT INTO direction_snapshot (id, societe_id, mois, payload, generated_at) VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), SOCIETE,
                mois(-2).toString(), vide, OffsetDateTime.parse("2026-01-01T10:00:00+05:00"));
        horo.put("forme servie d'un instantane stocke avec un decalage +05:00 (lecture)",
                formes(noeud(doGet(SNAPSHOTS + "/" + mois(-2), direction())).path("generatedAt").asString()) + " | meme instant : "
                        + OffsetDateTime.parse(noeud(doGet(SNAPSHOTS + "/" + mois(-2), direction())).path("generatedAt").asString())
                        .toInstant().equals(OffsetDateTime.parse("2026-01-01T10:00:00+05:00").toInstant()));
        horo.put("forme servie dans la liste", formes(noeud(doGet(SNAPSHOTS, direction())).get(1).path("generatedAt").asString()));
        horo.put("cle de la reponse de liste", cles(noeud(doGet(SNAPSHOTS, direction())).get(0)));
        m.put("04 horodatages", horo);
        // volume : 72 mois figes
        for (int i = 3; i <= 74; i++) {
            jdbc.update("INSERT INTO direction_snapshot (id, societe_id, mois, payload, generated_at) VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), SOCIETE,
                    MOIS_COURANT_DU_PILOTAGE.minusMonths(i).toString(), vide, CREATION_FIXE);
        }
        List<String> liste = noeudMois(doGet(SNAPSHOTS, direction()));
        Map<String, Object> volume = new LinkedHashMap<>();
        volume.put("nombre de mois listes", liste.size());
        volume.put("premier", liste.get(0));
        volume.put("dernier", liste.get(liste.size() - 1));
        volume.put("lecture du plus ancien (hors borne de capture)", etat(doGet(SNAPSHOTS + "/" + liste.get(liste.size() - 1), direction()), false));
        m.put("05 volume", volume);
        jdbc.update("DELETE FROM direction_snapshot WHERE societe_id = ?", SOCIETE);
        // concurrence
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<Resp>> futurs = new ArrayList<>();
            for (int i = 0; i < 4; i++) futurs.add(pool.submit(() -> doPost(SNAPSHOTS + "/" + m1, "", direction())));
            Set<Integer> statuts = new LinkedHashSet<>();
            Set<String> corps = new LinkedHashSet<>();
            for (Future<Resp> f : futurs) {
                Resp r = f.get();
                statuts.add(r.status());
                corps.add(r.raw());
            }
            Map<String, Object> conc = new LinkedHashMap<>();
            conc.put("statuts distincts des quatre captures simultanees", statuts);
            conc.put("corps distincts", corps.size());
            conc.put("lignes en base", jdbc.queryForObject("SELECT COUNT(*) FROM direction_snapshot WHERE societe_id = ? AND mois = ?", Integer.class, SOCIETE, m1.toString()));
            m.put("06 captures simultanees du meme mois", conc);
        } finally {
            pool.shutdownNow();
        }
        arbrePd("hors_bornes_tri_horodatages_concurrence", m);
    }

    private List<String> noeudMois(Resp r) {
        List<String> l = new ArrayList<>();
        noeud(r).forEach(i -> l.add(i.path("mois").asString()));
        return l;
    }

    private static List<String> cles(JsonNode n) {
        List<String> l = new ArrayList<>();
        n.propertyNames().forEach(l::add);
        return l;
    }

    // ------------------------------------------------------------------ audit

    @Test
    void les_instantanes_dans_le_journal_d_audit() throws Exception {
        YearMonth m1 = mois(-1);
        mois(0);
        nommerLaPeriodePrecedenteDe(m1, "<PERIODE_AVANT_M-1>");
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).addFilters(roleScopeFilter, filtreDAudit).build();
        ecrivainDAudit.flush();
        jdbc.update("DELETE FROM audit_log WHERE route_template LIKE '%/direction/%'");
        doGet(SNAPSHOTS, direction());
        doGet(SNAPSHOTS + "/" + m1, direction());
        doPost(SNAPSHOTS + "/" + m1, "", direction());
        doPost(SNAPSHOTS + "/" + m1, "", direction());
        doGet(SNAPSHOTS + "/" + m1, direction());
        doGet(SNAPSHOTS + "/2026-13", direction());
        doPost(SNAPSHOTS + "/" + mois(0), "", direction());
        doGet(SNAPSHOTS, as(CENTRE_A, "ADMIN"));
        doPost(SNAPSHOTS + "/" + m1, "", as(CENTRE_A, "SECRETAIRE"));
        doGetAnonymous(SNAPSHOTS);
        ecrivainDAudit.flush();
        List<String> lignes = new ArrayList<>();
        lignes.add("GET /snapshots suivi = " + AuditActionResolver.isTracked("GET", DIRECTION + "/snapshots"));
        lignes.add("GET /snapshots/{mois} suivi = " + AuditActionResolver.isTracked("GET", DIRECTION + "/snapshots/{mois}"));
        lignes.add("POST /snapshots/{mois} suivi = " + AuditActionResolver.isTracked("POST", DIRECTION + "/snapshots/{mois}"));
        for (Map<String, Object> row : jdbc.queryForList("SELECT action_code, http_method, route_template, status_code, COUNT(*) AS n "
                + "FROM audit_log WHERE route_template LIKE '%/direction/%' GROUP BY action_code, http_method, route_template, status_code "
                + "ORDER BY route_template, http_method, status_code")) {
            lignes.add("journal : " + row.get("ACTION_CODE") + " " + row.get("HTTP_METHOD") + " " + row.get("ROUTE_TEMPLATE") + " "
                    + row.get("STATUS_CODE") + " x" + row.get("N"));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lignes", lignes);
        jdbc.update("DELETE FROM audit_log WHERE route_template LIKE '%/direction/%'");
        arbrePd("audit", m);
    }

    // ------------------------------------------------------------------ méthodes et en-têtes

    private String enveloppe(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requete) throws Exception {
        MockHttpServletResponse r = mvc.perform(requete).andReturn().getResponse();
        String type = r.getContentType() == null ? "-" : r.getContentType().split(";")[0];
        byte[] corps = r.getContentAsByteArray();
        String code = "";
        if (r.getStatus() >= 400 && corps.length > 0) {
            try {
                JsonNode c = mapper.readTree(new String(corps, StandardCharsets.UTF_8));
                code = c.has("code") ? " code=" + c.path("code").asString() : "";
            } catch (RuntimeException e) {
                code = "";
            }
        }
        return r.getStatus() + code + " type=" + type + " allow=" + (r.getHeader("Allow") == null ? null : java.util.Arrays.stream(r.getHeader("Allow").split(",\\s*")).sorted()
                .collect(java.util.stream.Collectors.joining(","))) + " cache=" + r.getHeader("Cache-Control")
                + " corps=" + (corps.length == 0 ? "vide" : "present");
    }

    @Test
    void les_methodes_les_chemins_voisins_et_les_en_tetes_des_instantanes() throws Exception {
        YearMonth m1 = mois(-1);
        nommerLaPeriodePrecedenteDe(m1, "<PERIODE_AVANT_M-1>");
        doPost(SNAPSHOTS + "/" + m1, "", direction());
        Map<String, String> cas = new LinkedHashMap<>();
        for (String chemin : List.of(SNAPSHOTS, SNAPSHOTS + "/" + m1)) {
            for (String methode : List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")) {
                cas.put(methode + " " + chemin.replace(DIRECTION, ""), enveloppe(request(HttpMethod.valueOf(methode), java.net.URI.create(chemin)).with(direction())));
            }
        }
        cas.put("DELETE /snapshots/<M-1> anonyme", enveloppe(request(HttpMethod.DELETE, java.net.URI.create(SNAPSHOTS + "/" + m1))));
        cas.put("PUT /snapshots/<M-1> par un ADMIN", enveloppe(request(HttpMethod.PUT, java.net.URI.create(SNAPSHOTS + "/" + m1)).with(as(CENTRE_A, "ADMIN"))));
        cas.put("GET /snapshots/ (barre finale)", enveloppe(request(HttpMethod.GET, java.net.URI.create(SNAPSHOTS + "/")).with(direction())));
        cas.put("GET /Snapshots (casse)", enveloppe(request(HttpMethod.GET, java.net.URI.create(DIRECTION + "/Snapshots")).with(direction())));
        cas.put("GET /snapshots.json", enveloppe(request(HttpMethod.GET, java.net.URI.create(SNAPSHOTS + ".json")).with(direction())));
        cas.put("POST /snapshots (collection)", enveloppe(request(HttpMethod.POST, java.net.URI.create(SNAPSHOTS)).with(direction())));
        cas.put("GET /snapshots/<M-1>/inconnu", enveloppe(request(HttpMethod.GET, java.net.URI.create(SNAPSHOTS + "/" + m1 + "/inconnu")).with(direction())));
        cas.put("POST /snapshots/<M-1>/report", enveloppe(request(HttpMethod.POST, java.net.URI.create(SNAPSHOTS + "/" + m1 + "/report")).with(direction())));
        for (String accept : List.of("application/json", "application/xml", "text/html", "text/plain", "*/*", "application/pdf", "n'importe quoi")) {
            cas.put("GET /snapshots Accept: " + accept, enveloppe(request(HttpMethod.GET, java.net.URI.create(SNAPSHOTS)).header("Accept", accept).with(direction())));
            cas.put("POST /snapshots/<M-1> Accept: " + accept, enveloppe(request(HttpMethod.POST, java.net.URI.create(SNAPSHOTS + "/" + m1)).header("Accept", accept).with(direction())));
        }
        cas.put("POST /snapshots/<M-1> Content-Type: application/xml avec corps", enveloppe(request(HttpMethod.POST, java.net.URI.create(SNAPSHOTS + "/" + m1))
                .contentType("application/xml").content("<a/>").with(direction())));
        cas.put("POST /snapshots/<M-1> Content-Type: text/plain", enveloppe(request(HttpMethod.POST, java.net.URI.create(SNAPSHOTS + "/" + m1))
                .contentType("text/plain").content("x").with(direction())));
        cas.put("GET /snapshots avec corps JSON", enveloppe(request(HttpMethod.GET, java.net.URI.create(SNAPSHOTS)).contentType("application/json")
                .content("{\"societeId\":\"" + AUTRE_SOCIETE + "\"}").with(direction())));
        cas.put("GET /snapshots avec en-tete X-Societe-Id d'une autre societe", enveloppe(request(HttpMethod.GET, java.net.URI.create(SNAPSHOTS))
                .header("X-Societe-Id", AUTRE_SOCIETE.toString()).with(direction())));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("lignes en base apres ces appels", jdbc.queryForObject("SELECT COUNT(*) FROM direction_snapshot WHERE societe_id IN (?, ?)", Integer.class, SOCIETE, AUTRE_SOCIETE));
        arbrePd("methodes_chemins_et_en_tetes", m);
    }
}
