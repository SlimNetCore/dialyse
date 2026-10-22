package com.hemodialyse.backend.characterization.absencespatients.consultation;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Profils autorises sur les quatre lectures de {@code /api/v1/absences-patients} : {@code @PreAuthorize}, {@code RoleScopeFilter}
 * (roles restreints, infirmier seul, medecin seul), anonyme, session sans centre, centre demande different de la session.
 */
class AutorisationsDesLecturesDAbsencesCharacterizationIntegrationTest extends ConsultationAbsencesCharacterizationSupport {

    @Override
    protected String theme() {
        return "autorisations";
    }

    private Map<String, RequestPostProcessor> profils() {
        Map<String, RequestPostProcessor> p = new LinkedHashMap<>();
        p.put("ADMIN", as(CENTRE_A, "ADMIN"));
        p.put("SECRETAIRE", as(CENTRE_A, "SECRETAIRE"));
        p.put("MEDECIN seul", as(CENTRE_A, "MEDECIN"));
        p.put("INFIRMIER seul", as(CENTRE_A, "INFIRMIER"));
        p.put("MEDECIN + INFIRMIER", as(CENTRE_A, "MEDECIN", "INFIRMIER"));
        p.put("DIRECTION", direction());
        p.put("SUPERADMIN", as(CENTRE_A, "SUPERADMIN"));
        p.put("SUPERADMIN + ADMIN", as(CENTRE_A, "SUPERADMIN", "ADMIN"));
        p.put("role inconnu (COMPTABLE)", as(CENTRE_A, "COMPTABLE"));
        p.put("aucun role", as(CENTRE_A));
        p.put("ADMIN sans centre", as(null, "ADMIN"));
        return p;
    }

    @Test
    void les_quatre_lectures_sont_ouvertes_a_admin_secretaire_medecin_et_infirmier_et_refusees_aux_autres_profils() throws Exception {
        abs(1, P_AMRANI, jour(5)).poser();
        String id = idAbsence(1).toString();
        List<String> routes = List.of(ABSENCES, ABSENCES + "/semaine", ABSENCES + "/synthese", ABSENCES + "/" + id + "/seances-rattrapage");
        Map<String, Object> matrice = new LinkedHashMap<>();
        for (String route : routes) {
            String nom = route.replace(id, "{id}").replace(ABSENCES, "");
            for (Map.Entry<String, RequestPostProcessor> profil : profils().entrySet()) {
                matrice.put("GET /" + (nom.isEmpty() ? "" : nom.substring(1)) + " [" + profil.getKey() + "]",
                        doGet(route, profil.getValue()).status());
            }
            matrice.put("GET /" + (nom.isEmpty() ? "" : nom.substring(1)) + " [anonyme]", doGetAnonyme(route).status());
        }

        goldenNode("matrice_profils_x_lectures", mapper.valueToTree(matrice));
    }

    @Test
    void les_refus_ont_deux_formes_selon_qu_ils_viennent_du_filtre_de_perimetre_ou_de_l_autorisation_de_methode() throws Exception {
        golden("refus_direction_filtre_de_perimetre", doGet(ABSENCES, direction()));
        golden("refus_superadmin_filtre_de_perimetre", doGet(ABSENCES + "/synthese", as(CENTRE_A, "SUPERADMIN")));
        golden("refus_role_inconnu_autorisation_de_methode", doGet(ABSENCES, as(CENTRE_A, "COMPTABLE")));
        golden("refus_anonyme", doGetAnonyme(ABSENCES));
        golden("refus_session_sans_centre", doGet(ABSENCES + "/synthese", as(null, "ADMIN")));
    }
}
