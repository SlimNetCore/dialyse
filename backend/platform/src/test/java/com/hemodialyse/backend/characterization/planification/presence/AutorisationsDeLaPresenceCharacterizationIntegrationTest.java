package com.hemodialyse.backend.characterization.planification.presence;

import com.hemodialyse.backend.characterization.planification.PresenceCharacterizationSupport;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Filet de caractérisation P4 : profils autorisés sur les cinq routes de lecture de la présence (semaine, alertes, remplaçants,
 * charge, impression) et garde de centre (paramètre {@code centerId} confronté au centre du jeton). Aucune de ces routes n'écrit :
 * la matrice dit seulement le statut HTTP (et le code d'erreur). Les droits sont portés par le contrôleur
 * ({@code ADMIN, SECRETAIRE, MEDECIN, INFIRMIER}), le filtre de périmètre ({@code RoleScopeFilter}) et la garde de centre.
 */
class AutorisationsDeLaPresenceCharacterizationIntegrationTest extends PresenceCharacterizationSupport {

    private interface Appel {
        Resp executer(RequestPostProcessor who) throws Exception;
    }

    private UUID amrani;
    private LocalDate mercredi;

    @Override
    protected String theme() {
        return "p4-presence/autorisations";
    }

    private void amorcer() {
        decor();
        mercredi = semaine(1, JourSemaine.MERCREDI);
        semaine(1);
        amrani = infirmierAffecte(CENTRE_A, "INF_AMRANI", "Amrani", "Sara", false, salle1, matin, "LUNDI,MERCREDI");
        patientsPlaces(CENTRE_A, "PAT_A", 5, salle1, matin, JourSemaine.LUNDI, JourSemaine.MERCREDI);
        inf(CENTRE_B, "INF_B", "Brahimi", "Nadia", false);
        affectation(CENTRE_B, "AFF_B", UUID.nameUUIDFromBytes("planification-p2-INF_B".getBytes()), salleB, creneauB, "MERCREDI");
        patientsPlaces(CENTRE_B, "PAT_B", 2, salleB, creneauB, JourSemaine.MERCREDI);
    }

    private Map<String, Appel> routes() {
        Map<String, Appel> routes = new LinkedHashMap<>();
        routes.put("GET /presence/semaine", who -> doGet(PRESENCE + "/semaine?date=" + mercredi, who));
        routes.put("GET /presence/alertes", who -> doGet(PRESENCE + "/alertes?jours=3", who));
        routes.put("GET /presence/remplacants", who -> doGet(PRESENCE + "/remplacants?date=" + mercredi + "&salleId=" + salle1
                + "&creneauId=" + matin, who));
        routes.put("GET /presence/charge", who -> doGet(PRESENCE + "/charge?mois=" + MOIS_REFERENCE, who));
        routes.put("GET /presence/imprimer", who -> doGet(PRESENCE + "/imprimer?date=" + mercredi, who));
        return routes;
    }

    @Test
    void les_cinq_routes_de_lecture_selon_les_onze_profils_et_l_anonyme() throws Exception {
        amorcer();
        Map<String, Object> matrice = new LinkedHashMap<>();
        for (Map.Entry<String, Appel> route : routes().entrySet()) {
            Map<String, RequestPostProcessor> profils = new LinkedHashMap<>(profils());
            profils.put("anonyme", null);
            for (Map.Entry<String, RequestPostProcessor> profil : profils.entrySet()) {
                Resp r = profil.getValue() == null
                        ? doGetAnonymous(urlDe(route.getKey()))
                        : route.getValue().executer(profil.getValue());
                matrice.put(route.getKey() + " [" + profil.getKey() + "]", statutEtCode(r));
            }
        }
        arbre("matrice_routes_x_profils", matrice);
    }

    /**
     * Route ajoutée par la slice P4 (KB-PLN-50) : elle suit le filtre de périmètre existant (médecin seul et infirmier seul refusés par
     * préfixe, SUPERADMIN et DIRECTION refusés) ; matrice à part de celle des cinq routes d'origine, qui reste fidèle au legacy.
     */
    @Test
    void la_route_des_alertes_de_sureffectif_selon_les_onze_profils_et_l_anonyme() throws Exception {
        amorcer();
        Map<String, Object> matrice = new LinkedHashMap<>();
        Map<String, RequestPostProcessor> profils = new LinkedHashMap<>(profils());
        profils.put("anonyme", null);
        for (Map.Entry<String, RequestPostProcessor> profil : profils.entrySet()) {
            Resp r = profil.getValue() == null ? doGetAnonymous(PRESENCE + "/alertes-sureffectif?jours=3")
                    : doGet(PRESENCE + "/alertes-sureffectif?jours=3", profil.getValue());
            matrice.put("GET /presence/alertes-sureffectif [" + profil.getKey() + "]", statutEtCode(r));
        }
        arbre("matrice_alertes_sureffectif_x_profils", matrice);
    }

    private String urlDe(String route) {
        return switch (route) {
            case "GET /presence/semaine" -> PRESENCE + "/semaine?date=" + mercredi;
            case "GET /presence/alertes" -> PRESENCE + "/alertes?jours=3";
            case "GET /presence/remplacants" -> PRESENCE + "/remplacants?date=" + mercredi + "&salleId=" + salle1 + "&creneauId=" + matin;
            case "GET /presence/charge" -> PRESENCE + "/charge?mois=" + MOIS_REFERENCE;
            default -> PRESENCE + "/imprimer?date=" + mercredi;
        };
    }

    @Test
    void les_formes_de_refus_filtre_de_perimetre_autorisation_de_methode_et_anonyme() throws Exception {
        amorcer();
        lecture("refus_medecin_seul_remplacants_filtre_de_perimetre", doGet(PRESENCE + "/remplacants?date=" + mercredi + "&salleId="
                + salle1 + "&creneauId=" + matin, as(CENTRE_A, "MEDECIN")));
        lecture("refus_infirmier_seul_alertes", doGet(PRESENCE + "/alertes", as(CENTRE_A, "INFIRMIER")));
        lecture("refus_direction_charge", doGet(PRESENCE + "/charge", direction()));
        lecture("refus_role_inconnu_remplacants", doGet(PRESENCE + "/remplacants?date=" + mercredi + "&salleId=" + salle1
                + "&creneauId=" + matin, as(CENTRE_A, "COMPTABLE")));
        lecture("refus_anonyme_imprimer", doGetAnonymous(PRESENCE + "/imprimer"));
        lecture("refus_admin_sans_centre_semaine", doGet(PRESENCE + "/semaine", as(null, "ADMIN")));
    }

    @Test
    void la_garde_de_centre_confronte_le_centre_demande_au_centre_du_jeton() throws Exception {
        amorcer();
        Map<String, Object> r = new LinkedHashMap<>();
        for (String chemin : new String[]{"semaine?date=" + mercredi, "alertes?jours=3", "charge?mois=" + MOIS_REFERENCE,
                "remplacants?date=" + mercredi + "&salleId=" + salleB + "&creneauId=" + creneauB, "imprimer?date=" + mercredi}) {
            String sep = chemin.contains("?") ? "&" : "?";
            String nom = chemin.substring(0, chemin.indexOf('?'));
            r.put(nom + " ADMIN A demande A", statutEtCode(doGet(PRESENCE + "/" + chemin + sep + "centerId=" + CENTRE_A, adminA())));
            r.put(nom + " ADMIN A demande B", statutEtCode(doGet(PRESENCE + "/" + chemin + sep + "centerId=" + CENTRE_B, adminA())));
            r.put(nom + " SECRETAIRE A demande B", statutEtCode(doGet(PRESENCE + "/" + chemin + sep + "centerId=" + CENTRE_B, secretaire())));
            r.put(nom + " MEDECIN+INFIRMIER A demande B", statutEtCode(doGet(PRESENCE + "/" + chemin + sep + "centerId=" + CENTRE_B,
                    as(CENTRE_A, "MEDECIN", "INFIRMIER"))));
            r.put(nom + " SUPERADMIN A demande B", statutEtCode(doGet(PRESENCE + "/" + chemin + sep + "centerId=" + CENTRE_B,
                    as(CENTRE_A, "SUPERADMIN"))));
            r.put(nom + " SUPERADMIN sans centre demande B", statutEtCode(doGet(PRESENCE + "/" + chemin + sep + "centerId=" + CENTRE_B,
                    as(null, "SUPERADMIN"))));
            r.put(nom + " SUPERADMIN sans centre sans centerId", statutEtCode(doGet(PRESENCE + "/" + chemin,
                    as(null, "SUPERADMIN"))));
            r.put(nom + " ADMIN A centre inconnu", statutEtCode(doGet(PRESENCE + "/" + chemin + sep + "centerId=" + UUID.nameUUIDFromBytes(
                    "centre-inconnu".getBytes()), adminA())));
            r.put(nom + " ADMIN A centerId illisible", statutEtCode(doGet(PRESENCE + "/" + chemin + sep + "centerId=abc", adminA())));
        }
        arbre("garde_de_centre", r);
        // le SUPERADMIN seul est refusé par le filtre de périmètre avant la garde de centre, même pour son propre centre
        lecture("superadmin_seul_refuse_meme_pour_son_centre", doGet(PRESENCE + "/semaine?centerId=" + CENTRE_A + "&date=" + mercredi,
                as(CENTRE_A, "SUPERADMIN")));
        lecture("admin_a_demande_b_refus_semaine", doGet(PRESENCE + "/semaine?centerId=" + CENTRE_B + "&date=" + mercredi, adminA()));
    }
}
