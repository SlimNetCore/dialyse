package com.hemodialyse.backend.characterization.absencespatients.cyclevie;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Slice absences-patients-a2-cycle-de-vie : profils autorises sur les quatre ecritures ({@code @PreAuthorize}, filtre de
 * perimetre {@code RoleScopeFilter}, anonyme, session sans centre), droit de correction par profil et garde de centre
 * ({@code centerId} different de la session). Chaque cellule repart d'une base reinitialisee ; le resultat est le statut HTTP et
 * l'etat de l'absence apres l'appel (la requete a-t-elle ecrit ?).
 */
class AutorisationsDesEcrituresDAbsencesCharacterizationIntegrationTest extends CycleDeVieAbsencesCharacterizationSupport {

    @Override
    protected String theme() {
        return "autorisations";
    }

    private Map<String, RequestPostProcessor> profils() {
        Map<String, RequestPostProcessor> p = new LinkedHashMap<>();
        p.put("ADMIN", as(U_ADMIN, CENTRE_A, "ADMIN"));
        p.put("SECRETAIRE", as(U_SECRETAIRE, CENTRE_A, "SECRETAIRE"));
        p.put("MEDECIN seul", as(U_MEDECIN, CENTRE_A, "MEDECIN"));
        p.put("INFIRMIER seul", as(U_INFIRMIER, CENTRE_A, "INFIRMIER"));
        p.put("MEDECIN + INFIRMIER", as(U_MEDECIN, CENTRE_A, "MEDECIN", "INFIRMIER"));
        p.put("DIRECTION", direction());
        p.put("SUPERADMIN", as(U_ADMIN, CENTRE_A, "SUPERADMIN"));
        p.put("SUPERADMIN + ADMIN", as(U_ADMIN, CENTRE_A, "SUPERADMIN", "ADMIN"));
        p.put("role inconnu (COMPTABLE)", as(U_ADMIN, CENTRE_A, "COMPTABLE"));
        p.put("aucun role", as(U_ADMIN, CENTRE_A));
        p.put("ADMIN sans centre", as(U_ADMIN, null, "ADMIN"));
        return p;
    }

    private void reinitialiser() {
        jdbc.update("DELETE FROM absence_patient WHERE center_id = ?", CENTRE_A);
        jdbc.update("DELETE FROM seances WHERE center_id = ?", CENTRE_A);
    }

    /** Une route d'ecriture : prepare sa base, appelle avec le profil (nul = anonyme), rend « statut HTTP / etat ». */
    private interface Route {
        String appeler(RequestPostProcessor who) throws Exception;
    }

    private String etatApres(Resp r) {
        String statut = jdbc.query("SELECT statut FROM absence_patient WHERE center_id = ? ORDER BY date_seance, patient_id",
                (rs, i) -> rs.getString(1), CENTRE_A).stream().reduce((x, y) -> x + "+" + y).orElse("aucune ligne");
        return r.status() + " / " + statut;
    }

    private Map<String, Route> routes() {
        Map<String, Route> m = new LinkedHashMap<>();
        m.put("POST / (declarer)", who -> {
            reinitialiser();
            Resp r = who == null ? doPostAnonyme(ABSENCES, json("{'patientId':'" + P_AMRANI + "','dateSeance':'" + jour(3) + "'}"))
                    : doPost(ABSENCES, json("{'patientId':'" + P_AMRANI + "','dateSeance':'" + jour(3) + "'}"), who);
            return etatApres(r);
        });
        m.put("PUT /{id}/qualification (a qualifier)", who -> {
            reinitialiser();
            UUID id = abs(1, P_AMRANI, jour(8)).poser();
            Resp r = who == null ? doPutAnonyme(ABSENCES + "/" + id + "/qualification", json("{'motif':'MALADIE'}"))
                    : doPut(ABSENCES + "/" + id + "/qualification", json("{'motif':'MALADIE'}"), who);
            return etatApres(r);
        });
        m.put("PUT /{id}/rattrapage (a qualifier)", who -> {
            reinitialiser();
            UUID id = abs(1, P_AMRANI, jour(8)).poser();
            seance(P_AMRANI, CENTRE_A, jour(3), "VALIDEE");
            String corps = json("{'dateRattrapage':'" + jour(3) + "'}");
            Resp r = who == null ? doPutAnonyme(ABSENCES + "/" + id + "/rattrapage", corps)
                    : doPut(ABSENCES + "/" + id + "/rattrapage", corps, who);
            return etatApres(r);
        });
        m.put("PUT /{id}/annulation (a qualifier)", who -> {
            reinitialiser();
            UUID id = abs(1, P_AMRANI, jour(8)).poser();
            Resp r = who == null ? doPutAnonyme(ABSENCES + "/" + id + "/annulation", json("{'commentaire':'x'}"))
                    : doPut(ABSENCES + "/" + id + "/annulation", json("{'commentaire':'x'}"), who);
            return etatApres(r);
        });
        return m;
    }

    @Test
    void les_quatre_ecritures_selon_le_profil_sur_une_absence_a_qualifier() throws Exception {
        Map<String, Object> matrice = new LinkedHashMap<>();
        for (Map.Entry<String, Route> route : routes().entrySet()) {
            for (Map.Entry<String, RequestPostProcessor> profil : profils().entrySet()) {
                matrice.put(route.getKey() + " [" + profil.getKey() + "]", route.getValue().appeler(profil.getValue()));
            }
            matrice.put(route.getKey() + " [anonyme]", route.getValue().appeler(null));
        }
        goldenNode("matrice_profils_x_ecritures_nouveau_code", mapper.valueToTree(matrice));
    }

    /** Corrections d'une absence deja qualifiee (qualification, rattrapage, annulation) selon le profil. */
    @Test
    void les_corrections_d_une_absence_qualifiee_selon_le_profil() throws Exception {
        Map<String, Function<UUID, Route>> operations = new LinkedHashMap<>();
        operations.put("qualification", id -> who -> {
            Resp r = doPut(ABSENCES + "/" + id + "/qualification", json("{'motif':'VOYAGE','commentaire':'correction'}"), who);
            return etatApres(r);
        });
        operations.put("rattrapage", id -> who -> {
            Resp r = doPut(ABSENCES + "/" + id + "/rattrapage", json("{'dateRattrapage':'" + jour(3) + "'}"), who);
            return etatApres(r);
        });
        operations.put("annulation", id -> who -> {
            Resp r = doPut(ABSENCES + "/" + id + "/annulation", json("{'commentaire':'correction'}"), who);
            return etatApres(r);
        });
        Map<String, Object> matrice = new LinkedHashMap<>();
        for (var op : operations.entrySet()) {
            for (Map.Entry<String, RequestPostProcessor> profil : profils().entrySet()) {
                reinitialiser();
                UUID id = abs(1, P_AMRANI, jour(8)).statut("JUSTIFIEE").motif("MALADIE").commentaire("origine").poser();
                seance(P_AMRANI, CENTRE_A, jour(3), "VALIDEE");
                matrice.put(op.getKey() + " d'une absence JUSTIFIEE [" + profil.getKey() + "]",
                        op.getValue().apply(id).appeler(profil.getValue()));
            }
        }
        goldenNode("matrice_corrections_x_profils_nouveau_code", mapper.valueToTree(matrice));
    }

    @Test
    void le_centre_demande_doit_etre_celui_de_la_session() throws Exception {
        UUID id = abs(1, P_AMRANI, jour(8)).poser();
        seance(P_AMRANI, CENTRE_A, jour(3), "VALIDEE");
        ObjectNode o = mapper.createObjectNode();
        String autre = "?centerId=" + CENTRE_B;
        String memeCentre = "?centerId=" + CENTRE_A;
        o.set("declarer_centre_B_par_admin_A", resume(doPost(ABSENCES + autre,
                json("{'patientId':'" + P_AMRANI + "','dateSeance':'" + jour(2) + "'}"), admin())));
        o.set("qualifier_centre_B_par_admin_A", resume(doPut(ABSENCES + "/" + id + "/qualification" + autre,
                json("{'motif':'VOYAGE'}"), admin())));
        o.set("rattraper_centre_B_par_admin_A", resume(doPut(ABSENCES + "/" + id + "/rattrapage" + autre,
                json("{'dateRattrapage':'" + jour(3) + "'}"), admin())));
        o.set("annuler_centre_B_par_admin_A", resume(doPut(ABSENCES + "/" + id + "/annulation" + autre,
                json("{'commentaire':'x'}"), admin())));
        o.set("base_apres_les_refus", etat(CENTRE_A));
        o.set("declarer_centre_A_explicite", refus(doPost(ABSENCES + memeCentre,
                json("{'patientId':'" + P_AMRANI + "','dateSeance':'" + jour(2) + "'}"), admin())));
        o.set("centre_illisible", refus(doPost(ABSENCES + "?centerId=abc",
                json("{'patientId':'" + P_AMRANI + "','dateSeance':'" + jour(1) + "'}"), admin())));
        o.set("superadmin_sans_centre_de_session_avec_centerId_refuse_par_le_filtre_de_perimetre", refus(doPost(ABSENCES + memeCentre,
                json("{'patientId':'" + P_AMRANI + "','dateSeance':'" + jour(1) + "'}"), as(U_ADMIN, null, "SUPERADMIN"))));
        o.set("base_finale", etat(CENTRE_A));
        goldenNode("garde_de_centre_nouveau_code", o);
    }

    @Test
    void les_refus_ont_deux_formes_selon_qu_ils_viennent_du_filtre_de_perimetre_ou_de_l_autorisation_de_methode() throws Exception {
        String corps = json("{'patientId':'" + P_AMRANI + "','dateSeance':'" + jour(3) + "'}");
        golden("refus_medecin_declaration_autorisation_de_methode", doPost(ABSENCES, corps, medecin()));
        golden("refus_direction_filtre_de_perimetre", doPost(ABSENCES, corps, direction()));
        golden("refus_role_inconnu_autorisation_de_methode", doPost(ABSENCES, corps, as(U_ADMIN, CENTRE_A, "COMPTABLE")));
        golden("refus_anonyme", doPostAnonyme(ABSENCES, corps));
        golden("refus_session_sans_centre", doPost(ABSENCES, corps, as(U_ADMIN, null, "ADMIN")));
        golden("refus_centre_etranger", doPost(ABSENCES + "?centerId=" + CENTRE_B, corps, admin()));
    }
}
