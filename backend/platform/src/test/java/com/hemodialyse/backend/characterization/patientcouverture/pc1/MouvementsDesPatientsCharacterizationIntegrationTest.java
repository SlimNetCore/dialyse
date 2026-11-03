package com.hemodialyse.backend.characterization.patientcouverture.pc1;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PC1 : {@code GET /api/v1/mouvements-patients} (lecture seule). Les mouvements sont posés en base pour couvrir chaque type, l'ordre et
 * les jointures ; l'écriture par les routes de la fiche est verrouillée avec la modification et la création, la libération des places
 * (PLACE_LIBEREE) et la matrice des rôles par le filet P7.
 */
class MouvementsDesPatientsCharacterizationIntegrationTest extends PatientCouvertureCharacterizationSupport {

    private int sequence;

    @Override
    protected String dossier() {
        return "mouvements";
    }

    private void mouvement(String alias, String type, int effet, String precedent, String nouveau, UUID salle, UUID creneau,
                           UUID generateur, String jours, boolean automatique, int minute) {
        UUID id = UUID.nameUUIDFromBytes(("pc1-mouvement-" + (++sequence)).getBytes());
        aliasNommes.put(id.toString(), "<MVT_" + sequence + ">");
        jdbc.update("INSERT INTO mouvement_patient (id, center_id, patient_id, type, date_effet, etat_precedent, etat_nouveau, salle_id, "
                        + "position_id, generateur_id, jours_dialyse, automatique, cree_le) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)", id, CENTRE_A,
                idDe(alias), type, java.sql.Date.valueOf(jour(effet)), precedent, nouveau, salle, creneau, generateur, jours, automatique,
                Timestamp.from(Instant.parse("2026-01-05T09:00:00Z").plusSeconds(60L * minute)));
    }

    /** Trois patients et un mouvement de chaque type. */
    private void jeuComplet() {
        decorDePlacement();
        patientSansPlace("A");
        patientSansPlace("B");
        patientSansPlace("C");
        mouvement("A", "ADMISSION", -40, null, "PERMANENT", null, null, null, "", false, 1);
        mouvement("A", "SEJOUR_TEMPORAIRE", -30, "PERMANENT", "OCCASIONNEL", null, null, null, "", false, 2);
        mouvement("A", "REPRISE", -20, "OCCASIONNEL", "PERMANENT", salle1, matin, g1a, "LUNDI,MERCREDI", false, 3);
        mouvement("B", "ADMISSION", -35, null, "PERMANENT", salle2, apresMidi, g2a, "MARDI,JEUDI", false, 4);
        mouvement("B", "TRANSFERT", -10, "PERMANENT", "TRANSFERE", salle2, apresMidi, g2a, "MARDI,JEUDI", false, 5);
        mouvement("B", "PLACE_LIBEREE", -5, "TRANSFERE", "TRANSFERE", salle2, apresMidi, g2a, "MARDI,JEUDI", true, 6);
        mouvement("C", "DECES", -3, "PERMANENT", "DECEDE", null, null, null, "", false, 7);
        mouvement("C", "GREFFE", -2, "PERMANENT", "GREFFE", null, null, null, "", false, 8);
        mouvement("C", "GUERISON", -1, "PERMANENT", "GUERRI", null, null, null, "", false, 9);
    }

    private List<String> lignes(Resp r) {
        List<String> l = new ArrayList<>();
        for (JsonNode i : corps(r).path("items")) {
            l.add(i.path("dateEffet").asString() + " " + i.path("type").asString() + " " + i.path("patientNom").asString());
        }
        return l;
    }

    @Test
    void la_liste_rend_chaque_type_de_mouvement_avec_le_patient_la_place_figee_et_les_libelles() throws Exception {
        jeuComplet();
        lecture("liste_complete", doGet(MOUVEMENTS, adminA()));
    }

    @Test
    void la_liste_est_triee_par_date_d_effet_decroissante_puis_creation_decroissante_puis_identifiant() throws Exception {
        decorDePlacement();
        patientSansPlace("A");
        patientSansPlace("B");
        mouvement("A", "ADMISSION", -5, null, "PERMANENT", null, null, null, "", false, 1);
        mouvement("B", "ADMISSION", -5, null, "PERMANENT", null, null, null, "", false, 3);
        mouvement("A", "REPRISE", -5, "TRANSFERE", "PERMANENT", null, null, null, "", false, 2);
        mouvement("B", "DECES", -5, "PERMANENT", "DECEDE", null, null, null, "", false, 3);
        mouvement("A", "GREFFE", -6, "PERMANENT", "GREFFE", null, null, null, "", false, 9);
        mouvement("B", "GUERISON", -4, "PERMANENT", "GUERRI", null, null, null, "", false, 0);
        Resp r = doGet(MOUVEMENTS, adminA());
        List<String> ordre = new ArrayList<>();
        for (JsonNode i : corps(r).path("items")) {
            ordre.add(i.path("dateEffet").asString() + " " + i.path("type").asString() + " " + i.path("patientNom").asString() + " "
                    + aliasNommes.getOrDefault(i.path("id").asString(), "?"));
        }
        arbre("ordre", ordre);
    }

    @Test
    void les_filtres_par_patient_type_et_dates_sont_cumulatifs_et_les_bornes_de_date_inclusives() throws Exception {
        jeuComplet();
        Map<String, Object> m = new LinkedHashMap<>();
        String[] filtres = {"", "patientId=" + idDe("A"), "patientId=" + idDe("C"), "patientId=" + UUID.nameUUIDFromBytes("inconnu".getBytes()),
                "type=ADMISSION", "type=SEJOUR_TEMPORAIRE", "type=REPRISE", "type=TRANSFERT", "type=DECES", "type=GREFFE", "type=GUERISON",
                "type=PLACE_LIBEREE", "type=admission", "type=BIDON", "type=", "patientId=x", "patientId=",
                "from=" + jour(-20), "to=" + jour(-20), "from=" + jour(-20) + "&to=" + jour(-20), "from=" + jour(-10) + "&to=" + jour(-30),
                "from=" + jour(1), "to=" + jour(-41), "from=pas-une-date", "from=2026-02-30",
                "patientId=" + idDe("B") + "&type=ADMISSION", "patientId=" + idDe("A") + "&type=GREFFE",
                "patientId=" + idDe("B") + "&from=" + jour(-10), "type=ADMISSION&from=" + jour(-36) + "&to=" + jour(-34)};
        for (String f : filtres) {
            Resp r = doGet(MOUVEMENTS + (f.isEmpty() ? "" : "?" + f), adminA());
            String cle = f.replace(idDe("A").toString(), "<A>").replace(idDe("B").toString(), "<B>").replace(idDe("C").toString(), "<C>")
                    .replace("2026-02-30", "<DATE_IMPOSSIBLE>");
            for (Map.Entry<String, String> e : datesNommees.entrySet()) cle = cle.replace(e.getKey(), e.getValue());
            m.put(f.isEmpty() ? "aucun filtre" : cle, r.status() == 200 ? lignes(r) : refus(r));
        }
        arbre("filtres_nouveau_code", m);
    }

    @Test
    void la_pagination_rend_le_total_la_page_et_la_taille_et_refuse_les_valeurs_hors_bornes() throws Exception {
        jeuComplet();
        Map<String, Object> m = new LinkedHashMap<>();
        String[] pages = {"", "page=0&size=4", "page=1&size=4", "page=2&size=4", "page=3&size=4", "page=0&size=1", "page=8&size=1", "page=0&size=0",
                "page=0&size=-1", "page=-1&size=4", "page=0&size=1000", "page=0&size=abc", "page=abc", "size=2"};
        for (String p : pages) {
            Resp r = doGet(MOUVEMENTS + (p.isEmpty() ? "" : "?" + p), adminA());
            if (r.status() == 200) {
                JsonNode c = corps(r);
                m.put(p.isEmpty() ? "défauts" : p, Map.of("total", c.path("total").asInt(), "page", c.path("page").asInt(), "size", c.path("size").asInt(),
                        "lignes", lignes(r)));
            } else {
                m.put(p, refus(r));
            }
        }
        arbre("pages_nouveau_code", m);
    }

    @Test
    void un_mouvement_sans_patient_dans_le_centre_est_absent_de_la_liste_et_du_total() throws Exception {
        jeuComplet();
        UUID orphelin = UUID.nameUUIDFromBytes("pc1-patient-supprime".getBytes());
        jdbc.update("INSERT INTO mouvement_patient (id, center_id, patient_id, type, date_effet, etat_nouveau, automatique, cree_le) "
                        + "VALUES (?,?,?,?,?,?,?,?)", UUID.randomUUID(), CENTRE_A, orphelin, "ADMISSION", java.sql.Date.valueOf(jour(-1)),
                "PERMANENT", false, Timestamp.from(Instant.parse("2026-01-05T09:00:00Z")));
        jdbc.update("INSERT INTO mouvement_patient (id, center_id, patient_id, type, date_effet, etat_nouveau, automatique, cree_le) "
                        + "VALUES (?,?,?,?,?,?,?,?)", UUID.randomUUID(), CENTRE_B, idDe("A"), "ADMISSION", java.sql.Date.valueOf(jour(-1)),
                "PERMANENT", false, Timestamp.from(Instant.parse("2026-01-05T09:00:00Z")));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total centre A", corps(doGet(MOUVEMENTS, adminA())).path("total").asInt());
        m.put("total centre B (mouvement du centre B sur un patient du centre A)", corps(doGet(MOUVEMENTS, adminB())).path("total").asInt());
        arbre("orphelins", m);
    }

    @Test
    void le_nom_du_patient_est_prenom_puis_nom_et_les_libelles_de_place_sont_vides_si_la_place_n_existe_plus() throws Exception {
        jeuComplet();
        jdbc.update("UPDATE patients SET prenom = '' WHERE id = ?", idDe("A"));
        jdbc.update("UPDATE patients SET prenom = '   ' WHERE id = ?", idDe("B"));
        jdbc.update("DELETE FROM salle WHERE id = ?", salle1);
        jdbc.update("DELETE FROM position_creneau WHERE id = ?", matin);
        jdbc.update("UPDATE gmao_equipements SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", g2a);
        jdbc.update("DELETE FROM gmao_equipements WHERE id = ?", g1a);
        Resp r = doGet(MOUVEMENTS + "?type=REPRISE", adminA());
        Resp b = doGet(MOUVEMENTS + "?type=TRANSFERT", adminA());
        Resp c = doGet(MOUVEMENTS + "?type=DECES", adminA());
        Map<String, Object> m = new LinkedHashMap<>();
        for (Resp x : List.of(r, b, c)) {
            JsonNode i = corps(x).path("items").path(0);
            m.put(i.path("type").asString(), Map.of("patientNom", i.path("patientNom").asString(), "salle", i.path("salle").toString(),
                    "creneau", i.path("creneau").toString(), "generateur", i.path("generateur").toString()));
        }
        arbre("libelles", m);
    }

    @Test
    void un_type_de_mouvement_inconnu_en_base_fait_echouer_toute_la_liste() throws Exception {
        jeuComplet();
        mouvement("A", "TYPE_HERITE", -50, null, "PERMANENT", null, null, null, "", false, 1);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("liste complète", refus(doGet(MOUVEMENTS, adminA())));
        m.put("liste filtrée sur un autre type", doGet(MOUVEMENTS + "?type=GREFFE", adminA()).status());
        m.put("liste filtrée hors de la ligne héritée (dates)", doGet(MOUVEMENTS + "?from=" + jour(-45), adminA()).status());
        arbre("type_inconnu_nouveau_code", m);
    }

    @Test
    void le_centre_est_celui_de_la_session_sauf_demande_explicite_du_meme_centre() throws Exception {
        jeuComplet();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sans centerId (centre de la session)", corps(doGet(MOUVEMENTS, adminA())).path("total").asInt());
        m.put("centerId du centre de la session", corps(doGet(MOUVEMENTS + "?centerId=" + CENTRE_A, adminA())).path("total").asInt());
        m.put("centerId d'un autre centre", refus(doGet(MOUVEMENTS + "?centerId=" + CENTRE_B, adminA())));
        m.put("ADMIN du centre B sans centerId", corps(doGet(MOUVEMENTS, adminB())).path("total").asInt());
        m.put("ADMIN sans centre", refus(doGet(MOUVEMENTS, as(null, "ADMIN"))));
        m.put("centerId illisible", refus(doGet(MOUVEMENTS + "?centerId=x", adminA())));
        arbre("centre_nouveau_code", m);
    }
}
