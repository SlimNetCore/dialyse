package com.hemodialyse.backend.characterization.pilotagedirection.pd1;

import com.hemodialyse.backend.characterization.pilotagedirection.PilotageDirectionCharacterizationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PD1 : tableau de bord de la direction tel que servi par le LEGACY ({@code GET /api/v1/direction/societe}, {@code /overview}, {@code /breakdown} ;
 * {@code DirectionDashboardQueryService}, {@code DirectionBreakdownQueryService}, {@code AnonymityPolicy}, {@code DirectionAccessGuard}) : périmètre de la
 * société du jeton, masquage des effectifs de 1 à 4 par centre (et totaux calculés sur les bruts : fuite par différence, même famille que KB-MED-104),
 * effectif de la période, règlements de toutes dates, bornes de période, valeurs par défaut, droits. Rien n'est corrigé.
 */
class LeTableauDeBordDeLaDirectionCharacterizationIntegrationTest extends PilotageDirectionCharacterizationSupport {

    @Override
    protected String slice() {
        return "pd1";
    }

    @Override
    protected String dossier() {
        return "tableau_de_bord";
    }

    private String periode() {
        return "?from=" + fixe(0) + "&to=" + fixe(100);
    }

    private static List<String> centres(JsonNode corps) {
        List<String> l = new ArrayList<>();
        corps.path("centres").forEach(c -> l.add(c.path("centerId").asString() + " " + c.path("nom").asString()));
        return l;
    }

    @Test
    void la_societe_liste_ses_centres_tries_par_nom_y_compris_inactifs_et_rien_d_une_autre_societe() throws Exception {
        jdbc.update("UPDATE centers SET actif = FALSE WHERE id = ?", CENTRE_B);
        jdbc.update("UPDATE centers SET name = 'Zeta (centre A renomme)' WHERE id = ?", CENTRE_A);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 societe de test : B inactif liste, A renomme passe apres B (tri par nom)", reponse(direction("/societe")));
        m.put("02 direction de l'autre societe : son seul centre", reponse(doGet(DIRECTION + "/societe", directionAutre())));
        m.put("03 societe sans centre : liste vide", reponse(doGet(DIRECTION + "/societe", directionDe(U_DIRECTION_SANS_CENTRE, SOCIETE_SANS_CENTRE))));
        arbrePd("societe", m);
    }

    @Test
    void la_vue_d_ensemble_compte_l_effectif_de_la_periode_masque_les_petits_centres_et_additionne_les_bruts() throws Exception {
        jeuComplet();
        fixe(-1);
        fixe(-101);
        Resp r = direction("/overview" + periode());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 reponse complete (A : 7 presents dont 2 sous KT masques, sorti pendant compte, sorti avant et admis apres exclus ; B : 3 masques)", reponse(r));
        JsonNode t = corps(r).path("totaux");
        m.put("02 fuite par difference : totaux.patients - centre A publie = effectif masque de B",
                t.path("patients").asLong() - corps(r).path("centres").get(0).path("patients").asLong());
        m.put("03 encaisse du centre A inclut le reglement poste apres la periode (600 + 200 sur une facture de la periode)", corps(r).path("centres").get(0).path("encaisse").asString());
        arbrePd("overview", m);
    }

    @Test
    void la_repartition_detaille_sexe_age_caisse_et_anemie_par_centre_avec_masquage() throws Exception {
        jeuComplet();
        LocalDate aujourdhui = LocalDate.now();
        for (int i = 0; i < 5; i++) {
            patientPd(CENTRE_B, "B_18_ANS_AUJOURDHUI_" + i, "M", aujourdhui.minusYears(18), false, false, false, null);
            patientPd(CENTRE_B, "B_18_ANS_DEMAIN_" + i, "M", aujourdhui.minusYears(18).plusDays(1), false, false, false, null);
        }
        Resp r = direction("/breakdown" + periode());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 reponse complete (sexe par initiale M/F sinon autre ; tranches d'age au jour de l'appel ; caisses triees par centre puis CA HT decroissant ; "
                + "anemie)", reponse(r));
        Map<String, Object> ages = new LinkedHashMap<>();
        for (JsonNode ligne : corps(r).path("ages")) {
            if (CENTRE_B.toString().equals(ligne.path("centerId").asString())) {
                for (JsonNode tr : ligne.path("tranches")) ages.put(tr.path("code").asString(), tr.path("count").isNull() ? "masque" : tr.path("count").asString());
            }
        }
        m.put("02 centre B (13 patients : 3 nes en 1960 masques, 5 de 18 ans aujourd'hui, 5 qui auront 18 ans demain) : 18 ans revolus = 18_29, la veille = 0_17", ages);
        arbrePd("breakdown", m);
    }

    @Test
    void les_bornes_de_periode_et_les_valeurs_par_defaut_de_la_vue_d_ensemble_et_de_la_repartition() throws Exception {
        jeuComplet();
        LocalDate d0 = fixe(0);
        LocalDate cinqAns = alias(d0.plusYears(5), "<D+0 plus 5 ans>");
        LocalDate cinqAnsEtUnJour = alias(d0.plusYears(5).plusDays(1), "<D+0 plus 5 ans et 1 jour>");
        LocalDate futur = alias(LocalDate.of(2099, 1, 1), "<FUTUR_2099>");
        Map<String, Object> m = new LinkedHashMap<>();
        for (String route : List.of("/overview", "/breakdown")) {
            Map<String, Object> cas = new LinkedHashMap<>();
            cas.put("debut apres fin", refus(direction(route + "?from=" + fixe(100) + "&to=" + d0)));
            cas.put("exactement 5 ans", refus(direction(route + "?from=" + d0 + "&to=" + cinqAns)));
            cas.put("5 ans et 1 jour", refus(direction(route + "?from=" + d0 + "&to=" + cinqAnsEtUnJour)));
            cas.put("date illisible (from=hier)", refus(direction(route + "?from=hier&to=" + fixe(100))));
            cas.put("debut seul dans le futur (fin = aujourd'hui)", refus(direction(route + "?from=" + futur)));
            Resp toSeul = direction(route + "?to=" + fixe(100));
            cas.put("fin seule : debut = 1er janvier de l'annee de la fin", refus(toSeul) + " | from=" + corps(toSeul).path("from").asString()
                    + " | to=" + corps(toSeul).path("to").asString());
            Resp fromSeul = direction(route + "?from=" + d0);
            cas.put("debut seul : fin = aujourd'hui (JVM, pas le fuseau du centre)", refus(fromSeul) + " | to est aujourd'hui de la JVM : "
                    + LocalDate.now().toString().equals(corps(fromSeul).path("to").asString()));
            Resp sansRien = direction(route);
            cas.put("sans parametre : du 1er janvier de l'annee courante a aujourd'hui", refus(sansRien) + " | from est le 1er janvier de cette annee : "
                    + LocalDate.of(LocalDate.now().getYear(), 1, 1).toString().equals(corps(sansRien).path("from").asString()));
            Resp societeParam = direction(route + periode() + "&societeId=" + AUTRE_SOCIETE);
            cas.put("societeId en parametre : ignore, perimetre du jeton", refus(societeParam) + " | societeId=" + corps(societeParam).path("societeId").asString());
            m.put(route, cas);
        }
        Map<String, Object> illisible = new LinkedHashMap<>();
        for (String route : List.of("/overview", "/breakdown", "/report", "/indicators", "/capacite", "/absences", "/gmao", "/stock-groupes")) {
            illisible.put(route, refus(direction(route + "?from=hier")));
        }
        m.put("date illisible selon la route (advice limite a 4 routes : KB)", illisible);
        arbrePd("bornes", m);
    }

    @Test
    void une_societe_sans_centre_a_une_vue_d_ensemble_et_une_repartition_vides() throws Exception {
        RequestPostProcessor sansCentre = directionDe(U_DIRECTION_SANS_CENTRE, SOCIETE_SANS_CENTRE);
        fixe(-1);
        fixe(-101);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 vue d'ensemble : totaux a zero, aucune periode precedente", reponse(doGet(DIRECTION + "/overview" + periode(), sansCentre)));
        m.put("02 repartition : listes vides", reponse(doGet(DIRECTION + "/breakdown" + periode(), sansCentre)));
        m.put("03 indicateurs (le langage publie du Dossier medical est interroge avec une liste de centres vide)",
                refus(doGet(DIRECTION + "/indicators" + periode(), sansCentre)));
        m.put("04 rapport en direct", binaire(DIRECTION + "/report" + periode(), sansCentre).getStatus());
        m.put("05 capture d'un instantane du mois ecoule", refus(doPost(DIRECTION + "/snapshots/" + mois(-1), "", sansCentre)));
        arbrePd("sans_centre", m);
    }

    @Test
    void le_tableau_de_bord_est_reserve_a_la_direction_de_la_societe_du_jeton_reverifiee_en_base() throws Exception {
        jeuComplet();
        patientPd(CENTRE_AUTRE, "X1");
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Map<String, String>> profils = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profils().entrySet()) {
            Map<String, String> parRoute = new LinkedHashMap<>();
            for (String route : List.of("/societe", "/overview" + periode(), "/breakdown" + periode())) {
                parRoute.put(route, refus(doGet(DIRECTION + route, p.getValue())));
            }
            profils.put(p.getKey(), parRoute);
        }
        Map<String, String> anonyme = new LinkedHashMap<>();
        for (String route : List.of("/societe", "/overview" + periode(), "/breakdown" + periode())) {
            anonyme.put(route, refus(doGetAnonymous(DIRECTION + route)));
        }
        profils.put("anonyme", anonyme);
        m.put("01 onze profils et l'anonyme", profils);
        Map<String, Object> directions = new LinkedHashMap<>();
        directions.put("direction de l'autre societe : ses centres seuls", centres(corps(doGet(DIRECTION + "/overview" + periode(), directionAutre()))));
        directions.put("direction dont le compte est desactive", refus(doGet(DIRECTION + "/overview" + periode(), directionDe(U_DIRECTION_INACTIVE, SOCIETE))));
        directions.put("direction non rattachee a la societe du jeton", refus(doGet(DIRECTION + "/overview" + periode(), directionDe(U_DIRECTION_DETACHEE, SOCIETE))));
        directions.put("direction d'une societe inactive", refus(doGet(DIRECTION + "/overview" + periode(), directionDe(U_DIRECTION_SOCIETE_INACTIVE, SOCIETE_INACTIVE))));
        directions.put("jeton portant une societe inconnue", refus(doGet(DIRECTION + "/overview" + periode(), directionDe(U_ADMIN, UUID.fromString("99990000-0000-0000-0000-000000000bad")))));
        directions.put("jeton DIRECTION sans societe", refus(doGet(DIRECTION + "/overview" + periode(), directionDe(U_ADMIN, null))));
        directions.put("role DIRECTION porte par une session de centre (sans societe)", refus(doGet(DIRECTION + "/overview" + periode(), as(CENTRE_A, "DIRECTION"))));
        directions.put("ecriture refusee : POST /overview", refus(doPost(DIRECTION + "/overview", "{}", direction())));
        m.put("02 revérification en base et perimetre du jeton", directions);
        arbrePd("droits", m);
    }
}
