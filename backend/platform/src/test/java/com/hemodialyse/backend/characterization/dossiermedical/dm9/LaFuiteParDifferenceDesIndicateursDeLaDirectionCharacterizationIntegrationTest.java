package com.hemodialyse.backend.characterization.dossiermedical.dm9;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DM9 : KB-MED-104 (RISQUE DE CONFIDENTIALITÉ PRIORITAIRE, décision DPO requise) REPRODUIT et rendu explicite pour l'arbitrage :
 * {@code GET /api/v1/direction/indicators} ({@code DirectionIndicatorsQueryService}, {@code AnonymityPolicy}) masque par centre tout effectif
 * compris entre 1 et 4, mais les totaux additionnent les effectifs NON masqués ({@code Raw.merge}) : la différence « totaux moins centres
 * visibles » rend l'effectif masqué, et deux centres masqués dont la somme atteint 5 publient leur somme. DM8 ({@code dossiermedical/dm8/direction},
 * {@code forme}, {@code greffes}) l'observe au passage ; ici la déduction est CALCULÉE dans le golden pour les patients évalués (hémoglobine), les
 * sérologies positives et les bilans de greffe. Rien n'est corrigé.
 */
class LaFuiteParDifferenceDesIndicateursDeLaDirectionCharacterizationIntegrationTest extends Dm9CharacterizationSupport {

    @Override
    protected String dossier() {
        return "direction";
    }

    private final Map<UUID, Map<String, Object>> centresInitiaux = new LinkedHashMap<>();
    private boolean societeCreee;

    /** Les lignes {@code centers} des deux centres de test peuvent préexister (autres filets) : on les rattache à la société de test puis on les rétablit. */
    @BeforeEach
    void societeEtCentres() {
        aliasNommes.put(SOCIETE.toString(), "<SOCIETE>");
        centresInitiaux.clear();
        societeCreee = jdbc.queryForObject("SELECT COUNT(*) FROM societes WHERE id = ?", Integer.class, SOCIETE) == 0;
        if (societeCreee) {
            jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)", SOCIETE, "ZT-DM9", "Societe DM9");
        }
        jdbc.update("DELETE FROM app_user_societe WHERE user_id = ?", U_ADMIN);
        jdbc.update("DELETE FROM app_user WHERE id = ?", U_ADMIN);
        jdbc.update("INSERT INTO app_user (id, username, password_hash, email, full_name, active) VALUES (?,?,?,?,?,TRUE)", U_ADMIN, "zt-dm9-direction", "x", null,
                "Direction DM9");
        jdbc.update("INSERT INTO app_user_societe (user_id, societe_id) VALUES (?, ?)", U_ADMIN, SOCIETE);
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            var existants = jdbc.queryForList("SELECT code, name, societe_id, actif FROM centers WHERE id = ?", centre);
            if (existants.isEmpty()) {
                jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", centre, centre.equals(CENTRE_A) ? "ZT-DM9-A" : "ZT-DM9-B",
                        centre.equals(CENTRE_A) ? "Centre A" : "Centre B", SOCIETE);
            } else {
                centresInitiaux.put(centre, existants.get(0));
                jdbc.update("UPDATE centers SET societe_id = ?, name = ?, actif = TRUE WHERE id = ?", SOCIETE, centre.equals(CENTRE_A) ? "Centre A" : "Centre B", centre);
            }
        }
    }

    @AfterEach
    void nettoyerDirection() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            Map<String, Object> avant = centresInitiaux.get(centre);
            if (avant == null) {
                jdbc.update("DELETE FROM centers WHERE id = ?", centre);
            } else {
                jdbc.update("UPDATE centers SET societe_id = ?, name = ?, actif = ? WHERE id = ?", avant.get("SOCIETE_ID"), avant.get("NAME"), avant.get("ACTIF"), centre);
            }
        }
        jdbc.update("DELETE FROM app_user_societe WHERE user_id = ?", U_ADMIN);
        jdbc.update("DELETE FROM app_user WHERE id = ?", U_ADMIN);
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-DM9%'");
        if (societeCreee) jdbc.update("DELETE FROM societes WHERE id = ?", SOCIETE);
        jdbc.update("DELETE FROM societes WHERE code LIKE 'ZT-DM9%'");
    }

    // ------------------------------------------------------------------ fixtures et projections

    private Resp indicateurs() throws Exception {
        return doGet(INDICATEURS + "?from=" + fixe(0) + "&to=" + fixe(100), direction());
    }

    /** {@code n} patients du centre, chacun avec une hémoglobine à 12 g/dl au <D+10>. */
    private void hemoglobines(UUID centre, String prefixe, int n) {
        for (int i = 0; i < n; i++) {
            analyseEnBase(centre, patientDm9(centre, prefixe + i), prefixe + "_HB_" + i, fixe(10), "hb_g_dl", "12");
        }
    }

    private JsonNode centre(Resp r, UUID id) {
        for (JsonNode c : corps(r).path("centres")) {
            if (id.toString().equals(c.path("centerId").asString())) return c;
        }
        return mapper.createObjectNode();
    }

    private JsonNode totaux(Resp r) {
        return corps(r).path("totaux");
    }

    private String effectif(JsonNode bloc, String... chemin) {
        JsonNode n = bloc.path("clinique");
        for (String c : chemin) n = n.path(c);
        return n.isMissingNode() || n.isNull() ? "masque" : n.asString();
    }

    /** Ce qu'un lecteur déduit par soustraction : totaux moins les centres publiés (ou « indeductible » si les totaux sont masqués). */
    private String deduction(Resp r, String... chemin) {
        String t = effectif(totaux(r), chemin);
        String a = effectif(centre(r, CENTRE_A), chemin);
        String b = effectif(centre(r, CENTRE_B), chemin);
        if ("masque".equals(t)) return "indeductible (totaux masques)";
        long total = Long.parseLong(t);
        if (!"masque".equals(a) && "masque".equals(b)) return "centre B = " + (total - Long.parseLong(a));
        if ("masque".equals(a) && !"masque".equals(b)) return "centre A = " + (total - Long.parseLong(b));
        if ("masque".equals(a)) return "somme des deux centres masques = " + total;
        return "rien a deduire (tout est publie)";
    }

    private Map<String, Object> lecture(Resp r, String libelle, String... chemin) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("centre A", effectif(centre(r, CENTRE_A), chemin));
        m.put("centre B", effectif(centre(r, CENTRE_B), chemin));
        m.put("totaux", effectif(totaux(r), chemin));
        m.put("deduction par difference", deduction(r, chemin));
        Map<String, Object> enveloppe = new LinkedHashMap<>();
        enveloppe.put(libelle, m);
        return enveloppe;
    }

    @Test
    void l_effectif_masque_d_un_centre_se_deduit_des_totaux_qui_additionnent_les_effectifs_masques() throws Exception {
        hemoglobines(CENTRE_A, "A", 5);
        hemoglobines(CENTRE_B, "B", 3);
        Resp r = indicateurs();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 statut", r.status());
        m.put("02 seuil d'anonymat de la reponse", corps(r).path("seuilAnonymat").asString());
        m.putAll(lecture(r, "03 patients evalues en hemoglobine : A = 5 publies, B = 3 masques", "hemoglobine", "evalues"));
        m.put("04 taux dans la cible des totaux (calcule sur les 8 patients, masques compris)", effectif(totaux(r), "hemoglobine", "pctDansCible"));
        arbre("un_centre_masque", m);
    }

    @Test
    void deux_centres_masques_publient_leur_somme_des_que_celle_ci_atteint_le_seuil() throws Exception {
        hemoglobines(CENTRE_A, "A", 2);
        hemoglobines(CENTRE_B, "B", 2);
        Map<String, Object> m = new LinkedHashMap<>();
        m.putAll(lecture(indicateurs(), "01 A = 2, B = 2 : somme 4 sous le seuil", "hemoglobine", "evalues"));
        hemoglobines(CENTRE_A, "A_SUPPLEMENTAIRE", 1);
        m.putAll(lecture(indicateurs(), "02 A = 3, B = 2 : somme 5 au seuil", "hemoglobine", "evalues"));
        arbre("deux_centres_masques", m);
    }

    @Test
    void les_serologies_positives_et_les_bilans_de_greffe_fuient_de_la_meme_facon_jusqu_a_un_seul_patient() throws Exception {
        for (int i = 0; i < 5; i++) {
            serologieEnBase(CENTRE_A, patientDm9(CENTRE_A, "VHB_A" + i), "VHB_A_" + i, "AG_HBS", "POSITIF", fixe(10));
            bilanEnBase(CENTRE_A, patientDm9(CENTRE_A, "ATT_A" + i), "ATT_A_" + i, "INSCRIT_LISTE_ATTENTE", null);
        }
        serologieEnBase(CENTRE_B, patientDm9(CENTRE_B, "VHB_B_UNIQUE"), "VHB_B", "AG_HBS", "POSITIF", fixe(10));
        for (int i = 0; i < 4; i++) {
            bilanEnBase(CENTRE_B, patientDm9(CENTRE_B, "ATT_B" + i), "ATT_B_" + i, "INSCRIT_LISTE_ATTENTE", null);
        }
        Resp r = indicateurs();
        Map<String, Object> m = new LinkedHashMap<>();
        m.putAll(lecture(r, "01 porteurs AgHBs positifs : A = 5, B = 1 (un seul patient du centre B)", "vhbPositifs"));
        m.putAll(lecture(r, "02 inscrits en liste d'attente de greffe : A = 5, B = 4", "greffeListeAttente"));
        m.putAll(lecture(r, "03 bilans en cours : aucun (zero reste zero)", "greffeBilanEnCours"));
        arbre("serologies_et_greffes", m);
    }
}
