package com.hemodialyse.backend.characterization.planification.infirmiers;

import com.hemodialyse.backend.characterization.planification.InfirmiersCharacterizationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Filet de caractérisation P2 : profils autorisés sur les neuf routes du référentiel des infirmiers et des comptes
 * (RG-INF-004 : lecture ADMIN, SECRETAIRE, MEDECIN, INFIRMIER ; écriture ADMIN ; liste des comptes liables en ADMIN seul).
 * Chaque cellule repart d'une base réinitialisée ; le résultat est « statut HTTP (et code) / la requête a-t-elle écrit ? ».
 */
class AutorisationsDesInfirmiersCharacterizationIntegrationTest extends InfirmiersCharacterizationSupport {

    @Override
    protected String theme() {
        return "p2-infirmiers/autorisations";
    }

    private interface Appel {
        Resp executer(RequestPostProcessor who) throws Exception;
    }

    private UUID sansCompte;
    private UUID avecCompte;
    private UUID inactive;
    private UUID compteLibre;

    private void amorcer() {
        nettoyerInfirmiers();
        cleanupPlanification();
        aliasNommes.clear();
        UUID dejaLie = compte("COMPTE_DEJA_LIE", "deja", "Compte deja lie", "INFIRMIER", true, CENTRE_A);
        compteLibre = compte("COMPTE_LIBRE", "libre", "Compte libre", "INFIRMIER", true, CENTRE_A);
        sansCompte = fiche(CENTRE_A, "INFIRMIER_1", "M1", "Amrani", "Samia", true);
        avecCompte = fiche(CENTRE_A, "INFIRMIER_2", "M2", "Benali", "Karim", null, "MAJOR", true, true, dejaLie);
        inactive = fiche(CENTRE_A, "INFIRMIER_3", "M3", "Chikhi", "Nadia", false);
    }

    private Resp appel(String methode, String path, String corps, RequestPostProcessor who) throws Exception {
        if (who == null) {
            return switch (methode) {
                case "GET" -> doGetAnonymous(path);
                default -> send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .request(org.springframework.http.HttpMethod.valueOf(methode), java.net.URI.create(path))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(corps == null ? "" : json(corps)));
            };
        }
        return switch (methode) {
            case "GET" -> doGet(path, who);
            case "DELETE" -> doDelete(path, who);
            case "PUT" -> doPut(path, json(corps), who);
            default -> doPost(path, json(corps), who);
        };
    }

    @Test
    void les_neuf_routes_selon_les_onze_profils_et_l_anonyme() throws Exception {
        amorcer();
        Map<String, Appel> routes = new LinkedHashMap<>();
        routes.put("GET /", who -> appel("GET", INFIRMIERS, null, who));
        routes.put("POST /", who -> appel("POST", INFIRMIERS,
                "{'matricule':'NEW','nom':'Nouveau','qualification':'INFIRMIER','habiliteIsolement':false}", who));
        routes.put("PUT /{id}", who -> appel("PUT", INFIRMIERS + "/" + sansCompte,
                "{'matricule':'M1','nom':'Renomme','qualification':'MAJOR','habiliteIsolement':true}", who));
        routes.put("POST /{id}/desactiver", who -> appel("POST", INFIRMIERS + "/" + sansCompte + "/desactiver", "{}", who));
        routes.put("POST /{id}/reactiver", who -> appel("POST", INFIRMIERS + "/" + inactive + "/reactiver", "{}", who));
        routes.put("GET /comptes-liables", who -> appel("GET", INFIRMIERS + "/comptes-liables", null, who));
        routes.put("POST /{id}/compte/lier", who -> appel("POST", INFIRMIERS + "/" + sansCompte + "/compte/lier",
                "{'userId':'" + compteLibre + "'}", who));
        routes.put("POST /{id}/compte/creer", who -> appel("POST", INFIRMIERS + "/" + sansCompte + "/compte/creer",
                "{'identifiant':'p2.nouveau','email':'nouveau@example.dz'}", who));
        routes.put("DELETE /{id}/compte", who -> appel("DELETE", INFIRMIERS + "/" + avecCompte + "/compte", null, who));

        Map<String, Object> matrice = new LinkedHashMap<>();
        for (Map.Entry<String, Appel> route : routes.entrySet()) {
            Map<String, RequestPostProcessor> profils = new LinkedHashMap<>(profils());
            profils.put("anonyme", null);
            for (Map.Entry<String, RequestPostProcessor> profil : profils.entrySet()) {
                amorcer();
                String avant = empreinte();
                Resp r = route.getValue().executer(profil.getValue());
                boolean ecrit = !avant.equals(empreinte());
                matrice.put(route.getKey() + " [" + profil.getKey() + "]",
                        statutEtCode(r) + (route.getKey().startsWith("GET") ? "" : " / " + (ecrit ? "ecrit" : "rien ecrit")));
            }
        }
        goldenNode("matrice_routes_x_profils", mapper.valueToTree(matrice));
    }

    @Test
    void les_formes_de_refus_filtre_de_perimetre_autorisation_de_methode_et_anonyme_et_leur_corps() throws Exception {
        amorcer();
        goldenSimple("refus_medecin_seul_lecture_filtre_de_perimetre", doGet(INFIRMIERS, as(CENTRE_A, "MEDECIN")));
        goldenSimple("refus_secretaire_ecriture_autorisation_de_methode", doPost(INFIRMIERS, json(
                "{'matricule':'NEW','nom':'Nouveau','qualification':'INFIRMIER','habiliteIsolement':false}"), as(CENTRE_A, "SECRETAIRE")));
        goldenSimple("refus_secretaire_comptes_liables_autorisation_de_methode",
                doGet(INFIRMIERS + "/comptes-liables", as(CENTRE_A, "SECRETAIRE")));
        goldenSimple("refus_infirmier_seul_lecture_filtre_de_perimetre", doGet(INFIRMIERS, as(CENTRE_A, "INFIRMIER")));
        goldenSimple("refus_anonyme_lecture", doGetAnonymous(INFIRMIERS));
        goldenSimple("refus_direction_lecture", doGet(INFIRMIERS, direction()));
        goldenSimple("refus_admin_sans_centre_lecture", doGet(INFIRMIERS, as(null, "ADMIN")));
    }

    @Test
    void le_corps_est_lu_avant_l_autorisation_de_methode_donc_un_corps_sans_booleen_donne_500_meme_a_un_role_refuse_KB_PLN_12() throws Exception {
        amorcer();
        // KB-PLN-12 : `habiliteIsolement` est un boolean primitif : absent, il fait échouer la désérialisation (500
        // INTERNAL_ERROR) AVANT le contrôle @PreAuthorize ; avec un corps lisible, le même profil reçoit 403.
        goldenSimple("secretaire_corps_vide_500_avant_le_403", doPost(INFIRMIERS, json("{}"), as(CENTRE_A, "SECRETAIRE")));
        goldenSimple("secretaire_corps_sans_habilite_isolement_500_avant_le_403", doPost(INFIRMIERS, json(
                "{'matricule':'NEW','nom':'Nouveau','qualification':'INFIRMIER'}"), as(CENTRE_A, "SECRETAIRE")));
        goldenSimple("secretaire_corps_complet_403", doPost(INFIRMIERS, json(
                "{'matricule':'NEW','nom':'Nouveau','qualification':'INFIRMIER','habiliteIsolement':false}"), as(CENTRE_A, "SECRETAIRE")));
        goldenSimple("refus_secretaire_fiche_inconnue_403", doPost(INFIRMIERS + "/" + UUID.nameUUIDFromBytes("x".getBytes())
                + "/desactiver", "{}", as(CENTRE_A, "SECRETAIRE")));
    }
}
