package com.hemodialyse.backend.characterization.pilotagedirection.pd2;

import com.hemodialyse.backend.characterization.pilotagedirection.PilotageDirectionCharacterizationSupport;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.InstantanesMensuelsDeLaDirection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PD2 : instantanés mensuels ({@code GET/POST /api/v1/direction/snapshots[/{mois}]}, table {@code direction_snapshot}, tâche du 1er du mois à
 * 02:30) : filet écrit sur le legacy ({@code DirectionSnapshotService}, retiré), exécuté depuis le lot B contre le contexte Pilotage direction
 * ({@code InstantanesMensuelsDeLaDirection}, ADR 0103) avec les MÊMES goldens : un mois écoulé est figé une fois (payload JSON
 * {@code overview + indicators + breakdown}), jamais recalculé ; mois contrôlés au fuseau de la JVM ({@code YearMonth.now()}, pas le fuseau du
 * centre) ; cloisonnement par société ; instantanés anciens sans répartition. Rien n'est corrigé.
 */
class LesInstantanesMensuelsDeLaDirectionCharacterizationIntegrationTest extends PilotageDirectionCharacterizationSupport {

    private static final String SNAPSHOTS = DIRECTION + "/snapshots";

    @Autowired
    private InstantanesMensuelsDeLaDirection snapshots;

    @Override
    protected String slice() {
        return "pd2";
    }

    @Override
    protected String dossier() {
        return "instantanes";
    }

    /** Projection lisible d'un instantané servi : identité, bornes de la vue d'ensemble, totaux, familles présentes. */
    private Map<String, Object> instantane(Resp r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", refus(r));
        if (r.status() != 200) return m;
        JsonNode c = corps(r);
        m.put("mois", c.path("mois").asString());
        m.put("generatedAt", c.path("generatedAt").asString());
        m.put("cles", cles(c));
        m.put("overview.from .. to", c.path("overview").path("from").asString() + " .. " + c.path("overview").path("to").asString());
        m.put("overview.periodePrecedente", c.path("overview").path("periodePrecedente").path("from").asString() + " .. "
                + c.path("overview").path("periodePrecedente").path("to").asString());
        m.put("overview.totaux.patients", c.path("overview").path("totaux").path("patients").isNull() ? "masque" : c.path("overview").path("totaux").path("patients").asString());
        m.put("overview.centres", c.path("overview").path("centres").size());
        m.put("indicators.cles", cles(c.path("indicators")));
        JsonNode stock = c.path("indicators").path("totaux").path("stock");
        m.put("indicators.totaux.stock", stock.isMissingNode() ? "absent" : stock);
        m.put("breakdown", c.path("breakdown").isMissingNode() ? "absent" : c.path("breakdown").isNull() ? "null" : cles(c.path("breakdown")));
        return m;
    }

    private static List<String> cles(JsonNode n) {
        List<String> l = new ArrayList<>();
        n.propertyNames().forEach(l::add);
        return l;
    }

    private List<String> liste(Resp r) {
        List<String> l = new ArrayList<>();
        corps(r).forEach(i -> l.add(i.path("mois").asString() + " genere " + i.path("generatedAt").asString()));
        return l;
    }

    @Test
    void un_mois_ecoule_est_fige_a_la_premiere_demande_puis_servi_tel_quel_meme_si_les_donnees_changent() throws Exception {
        YearMonth m1 = mois(-1);
        nommerLaPeriodePrecedenteDe(m1, "<PERIODE_AVANT_M-1>");
        for (int i = 1; i <= 5; i++) patientPd(CENTRE_A, "A" + i);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 liste initiale", liste(direction("/snapshots")));
        m.put("02 lecture avant la capture", refus(direction("/snapshots/" + m1)));
        Resp capture = doPost(SNAPSHOTS + "/" + m1, "", direction());
        m.put("03 capture (POST) : instantane complet du mois ecoule", instantane(capture));
        m.put("04 vue d'ensemble figee (corps complet)", corps(capture).path("overview"));
        List<Map<String, Object>> lignes = lignes("SELECT id, societe_id, mois, generated_at, LENGTH(payload) AS taille FROM direction_snapshot WHERE societe_id = ?", SOCIETE);
        lignes.forEach(l -> l.put("taille", l.get("taille") == null ? null : "non nulle : " + (Long.parseLong(String.valueOf(l.get("taille"))) > 100)));
        m.put("05 ligne en base", lignes);
        String premierHorodatage = jdbc.queryForObject("SELECT CAST(generated_at AS VARCHAR) FROM direction_snapshot WHERE societe_id = ?", String.class, SOCIETE);
        Resp seconde = doPost(SNAPSHOTS + "/" + m1, "", direction());
        m.put("06 seconde capture : idempotente, meme generated_at, une seule ligne", refus(seconde) + " | meme generated_at : "
                + premierHorodatage.equals(jdbc.queryForObject("SELECT CAST(generated_at AS VARCHAR) FROM direction_snapshot WHERE societe_id = ?", String.class, SOCIETE))
                + " | lignes : " + jdbc.queryForObject("SELECT COUNT(*) FROM direction_snapshot WHERE societe_id = ?", Integer.class, SOCIETE)
                + " | meme corps que la premiere : " + capture.raw().equals(seconde.raw()));
        for (int i = 6; i <= 10; i++) patientPd(CENTRE_A, "A" + i);
        Resp relecture = direction("/snapshots/" + m1);
        m.put("07 apres l'ajout de 5 patients : l'instantane relu garde 5, la vue en direct du meme mois en compte 10",
                "instantane : " + corps(relecture).path("overview").path("totaux").path("patients").asString() + " | direct : "
                        + corps(direction("/overview?from=" + m1.atDay(1) + "&to=" + m1.atEndOfMonth())).path("totaux").path("patients").asString()
                        + " | meme corps que la capture : " + capture.raw().equals(relecture.raw()));
        m.put("08 liste apres capture", liste(direction("/snapshots")));
        arbrePd("cycle_de_vie", m);
    }

    @Test
    void seuls_les_mois_ecoules_valides_et_de_moins_de_cinq_ans_peuvent_etre_figes() throws Exception {
        YearMonth m0 = mois(0);
        YearMonth m1 = mois(1);
        YearMonth m60 = mois(-60);
        YearMonth m61 = mois(-61);
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> post = new LinkedHashMap<>();
        post.put("mois courant", refus(doPost(SNAPSHOTS + "/" + m0, "", direction())));
        post.put("mois prochain", refus(doPost(SNAPSHOTS + "/" + m1, "", direction())));
        post.put("il y a exactement 60 mois (borne incluse)", instantane(doPost(SNAPSHOTS + "/" + m60, "", direction())).get("statut") + " | mois servi : "
                + corps(doPost(SNAPSHOTS + "/" + m60, "", direction())).path("mois").asString());
        post.put("il y a 61 mois", refus(doPost(SNAPSHOTS + "/" + m61, "", direction())));
        Map<String, String> illisibles = new LinkedHashMap<>();
        illisibles.put("mois 13", "2026-13");
        illisibles.put("lettres", "abcd-ef");
        illisibles.put("mois sur un chiffre", "2026-1");
        illisibles.put("date complete AAAA-MM-JJ", "2026-01-01");
        illisibles.put("sans tiret", "202601");
        illisibles.put("mois 00", "2026-00");
        for (Map.Entry<String, String> e : illisibles.entrySet()) {
            post.put("mois illisible (" + e.getKey() + ")", refus(doPost(SNAPSHOTS + "/" + e.getValue(), "", direction())));
        }
        m.put("01 capture (POST)", post);
        Map<String, Object> get = new LinkedHashMap<>();
        get.put("mois prochain (non fige : introuvable, pas de controle du futur en lecture)", refus(direction("/snapshots/" + m1)));
        get.put("mois illisible : 2026-13", refus(direction("/snapshots/2026-13")));
        get.put("rapport d'un mois illisible", refus(direction("/snapshots/2026-13/report")));
        get.put("rapport d'un mois non fige", refus(direction("/snapshots/" + m1 + "/report")));
        m.put("02 lecture (GET)", get);
        m.put("03 lignes en base apres ces appels", jdbc.queryForList("SELECT mois FROM direction_snapshot WHERE societe_id = ? ORDER BY mois", String.class, SOCIETE));
        arbrePd("mois_refuses", m);
    }

    @Test
    void les_instantanes_sont_cloisonnes_par_societe_et_un_instantane_ancien_sans_repartition_reste_servi() throws Exception {
        YearMonth m1 = mois(-1);
        YearMonth m2 = mois(-2);
        YearMonth m3 = mois(-3);
        doPost(SNAPSHOTS + "/" + m1, "", direction());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 autre societe : liste vide, lecture et rapport introuvables", liste(doGet(SNAPSHOTS, directionAutre())) + " | "
                + refus(doGet(SNAPSHOTS + "/" + m1, directionAutre())) + " | " + refus(doGet(SNAPSHOTS + "/" + m1 + "/report", directionAutre())));
        jdbc.update("INSERT INTO direction_snapshot (id, societe_id, mois, payload, generated_at) VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), SOCIETE,
                m2.toString(), "{\"overview\":{\"societeNom\":\"Ancien instantane\",\"totaux\":{\"patients\":12}},\"indicators\":{\"centres\":[]}}", CREATION_FIXE);
        m.put("02 instantane ancien sans 'breakdown' (anterieur aux repartitions) : servi avec breakdown absent/null tel que le legacy le serialise",
                instantane(direction("/snapshots/" + m2)));
        m.put("03 corps brut servi pour l'ancien instantane", corps(direction("/snapshots/" + m2)));
        jdbc.update("INSERT INTO direction_snapshot (id, societe_id, mois, payload, generated_at) VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), SOCIETE,
                m3.toString(), "pas du json", CREATION_FIXE);
        m.put("04 payload illisible en base : lecture", refus(direction("/snapshots/" + m3)));
        m.put("05 liste : du plus recent au plus ancien, l'illisible compris", liste(direction("/snapshots")));
        String doublon;
        try {
            jdbc.update("INSERT INTO direction_snapshot (id, societe_id, mois, payload, generated_at) VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), SOCIETE,
                    m1.toString(), "{}", CREATION_FIXE);
            doublon = "accepte (aucune contrainte)";
        } catch (org.springframework.dao.DuplicateKeyException e) {
            doublon = "refuse par la contrainte d'unicite (societe_id, mois)";
        }
        m.put("06 second instantane du meme mois pour la meme societe, insere en base", doublon);
        m.put("07 capture du meme mois pour l'autre societe : independante", instantane(doPost(SNAPSHOTS + "/" + m1, "", directionAutre())).get("statut")
                + " | lignes de l'autre societe : " + jdbc.queryForObject("SELECT COUNT(*) FROM direction_snapshot WHERE societe_id = ?", Integer.class, AUTRE_SOCIETE));
        arbrePd("cloisonnement_et_anciens", m);
    }

    @Test
    void la_tache_planifiee_fige_le_mois_ecoule_des_societes_actives_et_est_rejouable() throws Exception {
        YearMonth m1 = mois(-1);
        List<UUID> dejaLa = jdbc.queryForList("SELECT id FROM direction_snapshot WHERE mois = ?", UUID.class, m1.toString());
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            int crees = snapshots.figerLeMoisEcoule();
            Map<String, Object> premiere = new LinkedHashMap<>();
            premiere.put("au moins les deux societes actives a centres creees", crees >= 2);
            for (UUID s : List.of(SOCIETE, AUTRE_SOCIETE, SOCIETE_SANS_CENTRE, SOCIETE_INACTIVE)) {
                premiere.put(aliasDe(s), jdbc.queryForList("SELECT mois FROM direction_snapshot WHERE societe_id = ?", String.class, s));
            }
            m.put("01 premiere execution (mois ecoule de chaque societe active ; societe inactive ignoree ; societe sans centre figee)", premiere);
            m.put("02 seconde execution : rejouable, rien de plus a figer", snapshots.figerLeMoisEcoule());
            m.put("03 l'instantane de la tache est celui que la direction lit ensuite", instantane(direction("/snapshots/" + m1)).get("statut"));
            arbrePd("tache_planifiee", m);
        } finally {
            List<UUID> apres = jdbc.queryForList("SELECT id FROM direction_snapshot WHERE mois = ?", UUID.class, m1.toString());
            for (UUID id : apres) if (!dejaLa.contains(id)) jdbc.update("DELETE FROM direction_snapshot WHERE id = ?", id);
        }
    }

    /**
     * KB-DIR-01 / KB-MED-104 corriges le 2026-11-09 (lot L1, ADR 0112) : un instantane fige AVANT la correction porte des totaux de patients bruts
     * (10 = 7 + 3). Il est relu masque (totaux supprimes des qu'un centre est masque) SANS jamais reecrire {@code direction_snapshot.payload}.
     */
    @Test
    void un_instantane_ancien_aux_totaux_bruts_est_relu_masque_sans_reecrire_son_contenu_nouveau_code() throws Exception {
        YearMonth m1 = mois(-1);
        nommerLaPeriodePrecedenteDe(m1, "<PERIODE_AVANT_M-1>");
        doPost(SNAPSHOTS + "/" + m1, "", direction());
        String ancien = "{\"overview\":{\"societeNom\":\"Ancien\",\"seuilAnonymat\":5,\"centres\":[{\"nom\":\"Centre A\",\"patients\":7,\"patientsSousKt\":5},"
                + "{\"nom\":\"Centre B\",\"patients\":null,\"patientsSousKt\":null}],\"totaux\":{\"patients\":10,\"patientsSousKt\":8,\"seances\":6}},"
                + "\"indicators\":{\"centres\":[{\"nom\":\"Centre A\",\"clinique\":{\"vhbPositifs\":6,\"vhcPositifs\":0,\"hemoglobine\":{\"evalues\":8,\"pctDansCible\":50.0}}},"
                + "{\"nom\":\"Centre B\",\"clinique\":{\"vhbPositifs\":null,\"vhcPositifs\":0,\"hemoglobine\":{\"evalues\":null,\"pctDansCible\":null}}}],"
                + "\"totaux\":{\"clinique\":{\"vhbPositifs\":8,\"vhcPositifs\":0,\"hemoglobine\":{\"evalues\":11,\"pctDansCible\":54.5,\"pctSousCible\":9.1,\"pctAuDessus\":36.4}}}},"
                + "\"breakdown\":{\"caisses\":[{\"caisseCode\":\"CNAS\",\"patients\":7},{\"caisseCode\":\"CNAS\",\"patients\":null},{\"caisseCode\":\"CASNOS\",\"patients\":9}],"
                + "\"caisseTotaux\":[{\"caisseCode\":\"CNAS\",\"patients\":10,\"seances\":4},{\"caisseCode\":\"CASNOS\",\"patients\":9,\"seances\":2}]}}";
        jdbc.update("UPDATE direction_snapshot SET payload = ? WHERE societe_id = ? AND mois = ?", ancien, SOCIETE, m1.toString());
        Resp r = direction("/snapshots/" + m1);
        JsonNode c = corps(r);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 vue d'ensemble relue : totaux.patients et patientsSousKt supprimes (Centre B masque), seances inchangees", c.path("overview").path("totaux"));
        m.put("02 consolides cliniques : vhbPositifs supprime (B masque), vhcPositifs (0 et 0) garde, taux du marqueur supprimes",
                c.path("indicators").path("totaux").path("clinique"));
        m.put("03 totaux par caisse : CNAS supprime (une cellule masquee), CASNOS garde", c.path("breakdown").path("caisseTotaux"));
        m.put("04 les valeurs des centres sont inchangees", c.path("overview").path("centres"));
        m.put("05 le contenu stocke n'est JAMAIS reecrit (RG-DIR-090)",
                ancien.equals(jdbc.queryForObject("SELECT payload FROM direction_snapshot WHERE societe_id = ? AND mois = ?", String.class, SOCIETE, m1.toString())));
        arbrePd("ancien_instantane_masque_a_la_lecture_nouveau_code", m);
    }

    private String aliasDe(UUID id) {
        return aliasNommes.getOrDefault(id.toString(), id.toString());
    }
}
