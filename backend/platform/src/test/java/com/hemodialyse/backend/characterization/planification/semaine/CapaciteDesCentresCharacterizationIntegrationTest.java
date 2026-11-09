package com.hemodialyse.backend.characterization.planification.semaine;

import com.hemodialyse.backend.characterization.planification.SemaineCharacterizationSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.hemodialyse.backend.characterization.planification.JourSemaine.LUNDI;

/**
 * Filet de caractérisation P6 : capacité théorique des centres lue par la Direction ({@code GET /api/v1/direction/capacite},
 * {@code DirectionCapaciteQueryService} : lit {@code planning_parametres}, {@code position_creneau} et {@code patients}). Famille
 * d'alias {@code <J-n>} uniquement. Complète {@code DirectionCapaciteIntegrationTest} (niveau service) par l'observation HTTP.
 */
class CapaciteDesCentresCharacterizationIntegrationTest extends SemaineCharacterizationSupport {

    @Override
    protected String theme() {
        return "p6-semaine/capacite";
    }

    /** Les lignes {@code centers} des deux centres de test peuvent préexister (autres filets) : on les rattache à la société de test puis on les rétablit. */
    private final Map<UUID, Map<String, Object>> centresInitiaux = new LinkedHashMap<>();
    private boolean societeCreee;

    @BeforeEach
    void societeEtCentres() {
        centresInitiaux.clear();
        societeCreee = jdbc.queryForObject("SELECT COUNT(*) FROM societes WHERE id = ?", Integer.class, SOCIETE) == 0;
        if (societeCreee) {
            jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                    SOCIETE, "ZT-PLN6", "Societe P6");
        }
        // le compte de direction est revérifié en base à chaque requête (DirectionAccessGuard)
        jdbc.update("DELETE FROM app_user_societe WHERE user_id = ?", U_ADMIN);
        jdbc.update("DELETE FROM app_user WHERE id = ?", U_ADMIN);
        jdbc.update("INSERT INTO app_user (id, username, password_hash, email, full_name, active) VALUES (?,?,?,?,?,TRUE)",
                U_ADMIN, "zt-pln6-direction", "x", null, "Direction P6");
        jdbc.update("INSERT INTO app_user_societe (user_id, societe_id) VALUES (?, ?)", U_ADMIN, SOCIETE);
        for (UUID centre : java.util.List.of(CENTRE_A, CENTRE_B)) {
            var existants = jdbc.queryForList("SELECT code, name, societe_id, actif FROM centers WHERE id = ?", centre);
            if (existants.isEmpty()) {
                jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", centre,
                        centre.equals(CENTRE_A) ? "ZT-PLN6-A" : "ZT-PLN6-B", centre.equals(CENTRE_A) ? "Centre A" : "Centre B", SOCIETE);
            } else {
                centresInitiaux.put(centre, existants.get(0));
                jdbc.update("UPDATE centers SET societe_id = ?, name = ?, actif = TRUE WHERE id = ?", SOCIETE,
                        centre.equals(CENTRE_A) ? "Centre A" : "Centre B", centre);
            }
        }
    }

    @AfterEach
    void nettoyerCapacite() {
        for (UUID centre : java.util.List.of(CENTRE_A, CENTRE_B)) {
            Map<String, Object> avant = centresInitiaux.get(centre);
            if (avant == null) {
                jdbc.update("DELETE FROM centers WHERE id = ?", centre);
            } else {
                jdbc.update("UPDATE centers SET societe_id = ?, name = ?, actif = ? WHERE id = ?", avant.get("SOCIETE_ID"),
                        avant.get("NAME"), avant.get("ACTIF"), centre);
            }
        }
        jdbc.update("DELETE FROM app_user_societe WHERE user_id = ?", U_ADMIN);
        jdbc.update("DELETE FROM app_user WHERE id = ?", U_ADMIN);
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-PLN6%'");
        if (societeCreee) jdbc.update("DELETE FROM societes WHERE id = ?", SOCIETE);
        jdbc.update("DELETE FROM societes WHERE code LIKE 'ZT-PLN6%'");
    }

    private void parametres(UUID centre, Integer patientsParPosteSerie) {
        jdbc.update("DELETE FROM planning_parametres WHERE center_id = ?", centre);
        jdbc.update("INSERT INTO planning_parametres (center_id, jours_ouverts, patients_par_infirmier, patients_par_poste_serie, "
                + "updated_at) VALUES (?, 'LUNDI', 4, ?, CURRENT_TIMESTAMP)", centre, patientsParPosteSerie);
    }

    private void patients(UUID centre, String prefixe, int n) {
        UUID s = salle(centre, "S_" + prefixe, "Salle " + prefixe, "NON");
        UUID c = creneau(centre, "C_" + prefixe, "Creneau " + prefixe);
        patientsPlaces(centre, prefixe, n, s, c, LUNDI);
    }

    private Resp capacite(LocalDate from, LocalDate to, RequestPostProcessor who) throws Exception {
        return doGet(CAPACITE_DIRECTION + "?from=" + from + "&to=" + to, who);
    }

    private void decorDeCapacite() {
        for (int i = 0; i < 4; i++) generateur(CENTRE_A, "A" + i, null, "EN_SERVICE");
        generateur(CENTRE_A, "A_REFORME", null, "REFORME");
        generateur(CENTRE_A, "A_DESACTIF", null, "DESACTIF");
        for (int i = 0; i < 2; i++) generateur(CENTRE_B, "B" + i, null, "EN_SERVICE");
        patients(CENTRE_A, "ALPHA", 6);
        creneau(CENTRE_A, "C_SOIR", "Soir");
        patients(CENTRE_B, "BETA", 2);
        parametres(CENTRE_B, 2);
    }

    @Test
    void reponse_complete_de_la_capacite_de_deux_centres() throws Exception {
        decorDeCapacite();
        lecture("reponse_complete", capacite(jour(-30), jour(0), direction()));
    }

    @Test
    void une_file_active_de_un_a_quatre_patients_est_masquee_avec_son_taux() throws Exception {
        decorDeCapacite();
        JsonNode c = corps(capacite(jour(-30), jour(0), direction()));
        Map<String, Object> m = new LinkedHashMap<>();
        for (JsonNode centre : c.path("centres")) {
            m.put(centre.path("nom").asString(), "fileActive=" + centre.path("capacite").path("fileActive") + " taux="
                    + centre.path("capacite").path("tauxOccupation") + " capacite=" + centre.path("capacite").path("capacite"));
        }
        m.put("total", "fileActive=" + c.path("total").path("fileActive") + " taux=" + c.path("total").path("tauxOccupation"));
        arbre("file_masquee", m);
    }

    @Test
    void les_parametres_absents_nuls_ou_negatifs_valent_la_valeur_par_defaut() throws Exception {
        decorDeCapacite();
        Map<String, Object> m = new LinkedHashMap<>();
        for (Integer k : new Integer[]{null, 0, -3, 1, 7}) {
            parametres(CENTRE_B, k);
            JsonNode b = corps(capacite(jour(-30), jour(0), direction())).path("centres");
            for (JsonNode centre : b) {
                if ("Centre B".equals(centre.path("nom").asString())) {
                    m.put("patients_par_poste_serie=" + k, "patientsParPosteEtSerie=" + centre.path("capacite").path("patientsParPosteEtSerie")
                            + " capacite=" + centre.path("capacite").path("capacite"));
                }
            }
        }
        jdbc.update("DELETE FROM planning_parametres WHERE center_id = ?", CENTRE_B);
        for (JsonNode centre : corps(capacite(jour(-30), jour(0), direction())).path("centres")) {
            if ("Centre B".equals(centre.path("nom").asString())) {
                m.put("aucune ligne", "patientsParPosteEtSerie=" + centre.path("capacite").path("patientsParPosteEtSerie"));
            }
        }
        arbre("parametres_par_defaut", m);
    }

    @Test
    void la_file_active_exclut_les_patients_en_sommeil_et_ceux_sortis_avant_la_periode_et_inclut_ceux_sortis_pendant() throws Exception {
        decorDeCapacite();
        UUID s = salle(CENTRE_A, "S_X", "Salle X", "NON");
        UUID c = creneau(CENTRE_A, "C_X", "Creneau X");
        sommeil(patientPlace(CENTRE_A, "SOMMEIL", s, c, LUNDI));
        etat(patientPlace(CENTRE_A, "SORTI_AVANT", s, c, LUNDI), "TRANSFERE", jour(-40));
        etat(patientPlace(CENTRE_A, "SORTI_PENDANT", s, c, LUNDI), "TRANSFERE", jour(-10));
        Map<String, Object> m = new LinkedHashMap<>();
        for (JsonNode centre : corps(capacite(jour(-30), jour(0), direction())).path("centres")) {
            m.put(centre.path("nom").asString(), "fileActive=" + centre.path("capacite").path("fileActive"));
        }
        arbre("file_active", m);
    }

    @Test
    void les_generateurs_installes_apres_la_fin_de_la_periode_ne_sont_pas_comptes_et_les_reformes_et_desactives_non_plus() throws Exception {
        decorDeCapacite();
        UUID futur = generateur(CENTRE_A, "A_FUTUR", null, "EN_SERVICE");
        jdbc.update("UPDATE gmao_equipements SET date_installation = ? WHERE id = ?",
                java.sql.Timestamp.valueOf(jour(1).atTime(9, 0)), futur);
        UUID supprime = generateur(CENTRE_A, "A_SUPPRIME", null, "EN_SERVICE");
        jdbc.update("UPDATE gmao_equipements SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", supprime);
        generateur(CENTRE_A, "A_HS", null, "HORS_SERVICE");
        generateur(CENTRE_A, "A_MAINT", null, "EN_MAINTENANCE");
        equipement(CENTRE_A, "A_EAU", "STATION_TRAITEMENT_EAU", null, "EN_SERVICE");
        Map<String, Object> m = new LinkedHashMap<>();
        for (JsonNode centre : corps(capacite(jour(-30), jour(0), direction())).path("centres")) {
            JsonNode k = centre.path("capacite");
            m.put(centre.path("nom").asString(), "generateurs=" + k.path("generateurs") + " secours=" + k.path("generateursSecours")
                    + " postes=" + k.path("postesActifs") + " series=" + k.path("series"));
        }
        arbre("generateurs_comptes", m);
    }

    @Test
    void les_periodes_invalides_trop_longues_ou_illisibles_sont_refusees() throws Exception {
        decorDeCapacite();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("debut apres fin", statutEtCode(capacite(jour(0), jour(-1), direction())));
        m.put("debut egal fin", statutEtCode(capacite(jour(0), jour(0), direction())));
        m.put("plus de cinq ans", statutEtCode(capacite(jour(-2000), jour(0), direction())));
        m.put("from illisible", statutEtCode(doGet(CAPACITE_DIRECTION + "?from=abc&to=" + jour(0), direction())));
        m.put("to illisible", statutEtCode(doGet(CAPACITE_DIRECTION + "?to=abc", direction())));
        arbre("periodes", m);
    }

    @Test
    void sans_periode_la_capacite_va_du_premier_janvier_a_aujourd_hui() throws Exception {
        decorDeCapacite();
        Resp r = doGet(CAPACITE_DIRECTION, direction());
        if (r.status() != 200) {
            arbre("periode_par_defaut", Map.of("statut", statutEtCode(r)));
            return;
        }
        JsonNode c = corps(r);
        LocalDate from = LocalDate.parse(c.path("from").asString());
        LocalDate to = LocalDate.parse(c.path("to").asString());
        arbre("periode_par_defaut", Map.of("statut", r.status(), "from est le 1er janvier de l'annee de to",
                from.equals(LocalDate.of(to.getYear(), 1, 1)), "to est aujourd'hui", to.equals(jour(0)),
                "centres", c.path("centres").size()));
    }

    @Test
    void une_societe_sans_centre_repond_un_total_nul_et_un_centre_inactif_est_liste() throws Exception {
        UUID autreSociete = UUID.fromString("99997800-0000-0000-0000-0000000000f3");
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                autreSociete, "ZT-PLN6-V", "Societe deplacee");
        jdbc.update("UPDATE centers SET societe_id = ? WHERE id IN (?, ?)", autreSociete, CENTRE_A, CENTRE_B);
        Map<String, Object> m = new LinkedHashMap<>();
        JsonNode vide = corps(capacite(jour(-30), jour(0), direction()));
        m.put("sans centre", "centres=" + vide.path("centres").size() + " generateurs=" + vide.path("total").path("generateurs")
                + " capacite=" + vide.path("total").path("capacite"));
        jdbc.update("UPDATE centers SET societe_id = ?, name = 'Centre inactif', actif = FALSE WHERE id = ?", SOCIETE, CENTRE_A);
        generateur(CENTRE_A, "A0", null, "EN_SERVICE");
        JsonNode inactif = corps(capacite(jour(-30), jour(0), direction()));
        m.put("centre inactif", "centres=" + inactif.path("centres").size() + " actif=" + inactif.path("centres").path(0).path("actif")
                + " generateurs=" + inactif.path("total").path("generateurs"));
        arbre("societe_sans_centre_et_centre_inactif", m);
    }

    @Test
    void une_autre_societe_n_apparait_jamais() throws Exception {
        decorDeCapacite();
        UUID autreSociete = UUID.fromString("99997800-0000-0000-0000-0000000000f2");
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                autreSociete, "ZT-PLN6-X", "Autre societe");
        UUID autreCentre = UUID.fromString("99997800-0000-0000-0000-0000000000d1");
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", autreCentre, "ZT-PLN6-XC", "Centre autre societe", autreSociete);
        generateur(autreCentre, "X0", null, "EN_SERVICE");
        try {
            JsonNode c = corps(capacite(jour(-30), jour(0), direction()));
            Map<String, Object> m = new LinkedHashMap<>();
            c.path("centres").forEach(x -> m.put(x.path("nom").asString(), x.path("capacite").path("generateurs").asInt()));
            arbre("autre_societe", m);
        } finally {
            jdbc.update("DELETE FROM gmao_equipements WHERE centre_id = ?", autreCentre);
        }
    }

    @Test
    void la_capacite_selon_les_onze_profils_et_l_anonyme() throws Exception {
        decorDeCapacite();
        Map<String, Object> matrice = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> profil : profils().entrySet()) {
            Resp r = capacite(jour(-30), jour(0), profil.getValue());
            matrice.put(profil.getKey(), statutEtCode(r));
        }
        matrice.put("anonyme", statutEtCode(doGetAnonymous(CAPACITE_DIRECTION + "?from=" + jour(-30) + "&to=" + jour(0))));
        arbre("matrice_profils", matrice);
    }
}
