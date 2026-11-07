package com.hemodialyse.backend.characterization.dossiermedical.dm4;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DM4, lot 2 : liste paginée, synthèse (dernier résultat par marqueur), correction ({@code PUT}) et suppression ({@code DELETE}).
 */
class LaConsultationEtLaCorrectionDesSerologiesCharacterizationIntegrationTest extends SerologieCharacterizationSupport {

    @Override
    protected String dossier() {
        return "consultation";
    }

    private UUID patientA;
    private UUID patientB;

    private void deuxCentres() {
        patientA = patientDm4(CENTRE_A, "PAT_A");
        patientB = patientDm4(CENTRE_B, "PAT_B");
    }

    private Map<String, Object> page(UUID patient, String parametres) throws Exception {
        Resp r = doGet(serologiesApi(patient) + (parametres.isEmpty() ? "" : "?" + parametres), medecinA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", refus(r));
        if (r.status() == 200) {
            List<Object> items = new ArrayList<>();
            corps(r).path("items").forEach(i -> items.add(serologieLisible(i).get("marqueur") + " " + i.path("datePrelevement").asString()
                    + " " + i.path("resultat").asString()));
            m.put("total", corps(r).path("total").asInt());
            m.put("page", corps(r).path("page").asInt());
            m.put("size", corps(r).path("size").asInt());
            m.put("items", items);
        }
        return m;
    }

    @Test
    void la_liste_est_triee_par_date_de_prelevement_decroissante_et_paginee() throws Exception {
        deuxCentres();
        serologieEnBase(CENTRE_A, patientA, "S1", "AG_HBS", "NEGATIF", fixe(1), null);
        serologieEnBase(CENTRE_A, patientA, "S2", "AG_HBS", "POSITIF", fixe(30), CONDUITE);
        serologieEnBase(CENTRE_A, patientA, "S3", "VIH_AC", "NEGATIF", fixe(15), null);
        serologieEnBase(CENTRE_A, patientA, "S4", "TPHA", "EN_COURS", fixe(60), null);
        serologieEnBase(CENTRE_A, patientA, "S5", "CMV_IGG", "DOUTEUX", fixe(45), null);
        serologieEnBase(CENTRE_B, patientB, "S6", "TPHA", "NEGATIF", fixe(70), null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("defaut", page(patientA, ""));
        m.put("size=2", page(patientA, "size=2"));
        m.put("size=2&page=1", page(patientA, "size=2&page=1"));
        m.put("size=2&page=2", page(patientA, "size=2&page=2"));
        m.put("size=2&page=9", page(patientA, "size=2&page=9"));
        m.put("size=0", page(patientA, "size=0"));
        m.put("size=-5", page(patientA, "size=-5"));
        m.put("size=1000", page(patientA, "size=1000"));
        m.put("page=-1", page(patientA, "page=-1"));
        m.put("size=abc", page(patientA, "size=abc"));
        m.put("page=abc", page(patientA, "page=abc"));
        arbre("pages", m);
    }

    @Test
    void la_forme_complete_d_une_serologie_lue_avec_son_en_tete_de_cache() throws Exception {
        deuxCentres();
        UUID s = serologieEnBase(CENTRE_A, patientA, "S1", "AC_VHC", "POSITIF", fixe(5), CONDUITE);
        jdbc.update("UPDATE serologies_patient SET titre = 4.5, unite = 'S/CO', date_prochain_controle = ? WHERE id = ?",
                java.sql.Date.valueOf(fixe(95)), s);
        lecture("liste", doGet(serologiesApi(patientA), medecinA()));
        lecture("synthese", doGet(serologiesApi(patientA) + "/synthese", medecinA()));
    }

    @Test
    void la_synthese_rend_le_dernier_resultat_de_chaque_marqueur_dans_l_ordre_des_marqueurs() throws Exception {
        deuxCentres();
        serologieEnBase(CENTRE_A, patientA, "S1", "AG_HBS", "POSITIF", fixe(1), CONDUITE);
        serologieEnBase(CENTRE_A, patientA, "S2", "AG_HBS", "NEGATIF", fixe(20), null);
        serologieEnBase(CENTRE_A, patientA, "S3", "VIH_AC", "NEGATIF", fixe(5), null);
        serologieEnBase(CENTRE_A, patientA, "S4", "VIH_AC", "POSITIF", fixe(10), CONDUITE);
        serologieEnBase(CENTRE_A, patientA, "S5", "TPHA", "EN_COURS", fixe(30), null);
        serologieEnBase(CENTRE_A, patientA, "S6", "TPHA", "POSITIF", fixe(2), CONDUITE);
        serologieEnBase(CENTRE_A, patientA, "S7", "EBV_IGM", "DOUTEUX", fixe(40), null);
        serologieEnBase(CENTRE_A, patientA, "S8", "AC_HBS", "NEGATIF", fixe(3), null);
        serologieEnBase(CENTRE_B, patientB, "S9", "AG_HBS", "POSITIF", fixe(50), CONDUITE);
        Resp r = doGet(serologiesApi(patientA) + "/synthese", medecinA());
        List<Object> lignes = new ArrayList<>();
        corps(r).forEach(i -> lignes.add(i.path("marqueur").asString() + " " + i.path("datePrelevement").asString() + " "
                + i.path("resultat").asString()));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", r.status());
        m.put("synthese", lignes);
        m.put("synthese du patient B (session B, une serologie)", corps(doGet(serologiesApi(patientB) + "/synthese", as(CENTRE_B, "ADMIN"))).size());
        m.put("patient inconnu", refus(doGet(serologiesApi(UUID.nameUUIDFromBytes("x".getBytes())) + "/synthese", medecinA())));
        m.put("patient du centre B vu par le centre A", corps(doGet(serologiesApi(patientB) + "/synthese", medecinA())).size());
        arbre("synthese", m);
    }

    @Test
    void la_correction_ne_change_que_le_resultat_et_la_conduite_et_efface_la_conduite_si_elle_est_absente() throws Exception {
        deuxCentres();
        UUID s = serologieEnBase(CENTRE_A, patientA, "S1", "AG_HBS", "NEGATIF", fixe(5), null);
        jdbc.update("UPDATE serologies_patient SET titre = 1.5, unite = 'UI/mL', date_prochain_controle = ? WHERE id = ?",
                java.sql.Date.valueOf(fixe(95)), s);
        Map<String, Object> m = new LinkedHashMap<>();
        // Le corps peut porter d'autres champs : ils sont ignores.
        Resp vers = doPut(serologieApi(patientA, s), "{\"centerId\":\"" + CENTRE_A + "\",\"resultat\":\"POSITIF\",\"conduiteATenir\":\""
                + CONDUITE + "\",\"marqueur\":\"VIH_AC\",\"datePrelevement\":\"" + fixe(9) + "\",\"titre\":99}", medecinA());
        m.put("vers positif", refus(vers) + " -> " + serologieLisible(corps(vers)));
        etatDesSerologies("apres_positif");
        Resp retour = doPut(serologieApi(patientA, s), corpsMaj(CENTRE_A, "NEGATIF", null), medecinA());
        m.put("retour negatif sans conduite", refus(retour) + " -> conduite " + texte(corps(retour), "conduiteATenir"));
        m.put("positif sans conduite", refus(doPut(serologieApi(patientA, s), corpsMaj(CENTRE_A, "POSITIF", null), medecinA())));
        m.put("positif conduite blanche", refus(doPut(serologieApi(patientA, s), corpsMaj(CENTRE_A, "POSITIF", "  "), medecinA())));
        m.put("resultat inconnu", refus(doPut(serologieApi(patientA, s), corpsMaj(CENTRE_A, "PEUT_ETRE", null), medecinA())));
        m.put("resultat absent", statutEtCode(doPut(serologieApi(patientA, s), corpsMaj(CENTRE_A, null, null), medecinA())));
        m.put("corps vide", statutEtCode(doPut(serologieApi(patientA, s), "{}", medecinA())));
        m.put("EN_COURS puis DOUTEUX", refus(doPut(serologieApi(patientA, s), corpsMaj(CENTRE_A, "EN_COURS", null), medecinA()))
                + " / " + refus(doPut(serologieApi(patientA, s), corpsMaj(CENTRE_A, "DOUTEUX", "A recontroler"), medecinA())));
        arbre("correction", m);
        etatDesSerologies("apres_corrections");
    }

    @Test
    void corriger_une_serologie_inconnue_ou_d_un_autre_patient_ou_centre_donne_400_et_n_ecrit_rien() throws Exception {
        deuxCentres();
        UUID autrePatient = patientDm4(CENTRE_A, "PAT_A2");
        UUID s = serologieEnBase(CENTRE_A, patientA, "S1", "AG_HBS", "NEGATIF", fixe(5), null);
        UUID sb = serologieEnBase(CENTRE_B, patientB, "SB", "AG_HBS", "NEGATIF", fixe(5), null);
        UUID inconnue = UUID.nameUUIDFromBytes("dm4-inconnue".getBytes());
        aliasNommes.put(inconnue.toString(), "<INCONNUE>");
        String avant = empreinte();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("identifiant inconnu", refus(doPut(serologieApi(patientA, inconnue), corpsMaj(CENTRE_A, "DOUTEUX", null), medecinA())));
        m.put("serologie d'un autre patient", refus(doPut(serologieApi(autrePatient, s), corpsMaj(CENTRE_A, "DOUTEUX", null), medecinA())));
        m.put("serologie du centre B (session A)", refus(doPut(serologieApi(patientB, sb), corpsMaj(CENTRE_A, "DOUTEUX", null), medecinA())));
        m.put("serologie du centre B, centerId B (session A)", refus(doPut(serologieApi(patientB, sb), corpsMaj(CENTRE_B, "DOUTEUX", null),
                medecinA())));
        m.put("identifiant illisible", refus(doPut(serologiesApi(patientA) + "/pas-un-uuid", corpsMaj(CENTRE_A, "DOUTEUX", null), medecinA())));
        arbre("introuvable", m);
        assertThat(empreinte()).isEqualTo(avant);
    }

    @Test
    void la_suppression_est_definitive_et_silencieuse_meme_pour_une_serologie_inconnue_ou_hors_perimetre() throws Exception {
        deuxCentres();
        UUID autrePatient = patientDm4(CENTRE_A, "PAT_A2");
        UUID s1 = serologieEnBase(CENTRE_A, patientA, "S1", "AG_HBS", "NEGATIF", fixe(5), null);
        UUID s2 = serologieEnBase(CENTRE_A, patientA, "S2", "VIH_AC", "NEGATIF", fixe(6), null);
        UUID sb = serologieEnBase(CENTRE_B, patientB, "SB", "AG_HBS", "NEGATIF", fixe(5), null);
        UUID inconnue = UUID.nameUUIDFromBytes("dm4-inconnue".getBytes());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("inconnue", refus(doDelete(serologieApi(patientA, inconnue), medecinA())));
        m.put("d'un autre patient du centre", refus(doDelete(serologieApi(autrePatient, s1), medecinA())));
        m.put("du centre B (session A)", refus(doDelete(serologieApi(patientB, sb), medecinA())));
        m.put("du centre B avec centerId B (session A)", refus(doDelete(serologieApi(patientB, sb) + "?centerId=" + CENTRE_B, medecinA())));
        m.put("identifiant illisible", refus(doDelete(serologiesApi(patientA) + "/pas-un-uuid", medecinA())));
        m.put("rien n'a ete supprime", nombreDeSerologies());
        m.put("la sienne", refus(doDelete(serologieApi(patientA, s1), medecinA())));
        m.put("la meme une seconde fois", refus(doDelete(serologieApi(patientA, s1), medecinA())));
        arbre("suppression", m);
        etatDesSerologies("serologies");
        assertThat(resultatEnBase(s2)).isEqualTo("NEGATIF");
    }

    @Test
    void les_lectures_ne_verifient_ni_le_patient_ni_son_centre_et_rendent_une_liste_vide() throws Exception {
        deuxCentres();
        serologieEnBase(CENTRE_B, patientB, "SB", "AG_HBS", "POSITIF", fixe(5), CONDUITE);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("patient inconnu", page(UUID.nameUUIDFromBytes("inconnu".getBytes()), ""));
        m.put("patient du centre B vu par le centre A", page(patientB, ""));
        m.put("patient du centre B vu par le centre B", Map.of("session B", corpsLongueur(doGet(serologiesApi(patientB), as(CENTRE_B, "MEDECIN")))));
        m.put("patient illisible", refus(doGet(PATIENTS_API + "/pas-un-uuid/serologies", medecinA())));
        arbre("lectures_non_verifiees", m);
    }

    private int corpsLongueur(Resp r) {
        JsonNode c = corps(r);
        return c.path("items").size();
    }
}
