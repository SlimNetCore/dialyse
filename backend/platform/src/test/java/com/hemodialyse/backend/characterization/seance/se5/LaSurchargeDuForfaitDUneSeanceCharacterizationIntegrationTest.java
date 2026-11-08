package com.hemodialyse.backend.characterization.seance.se5;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SE5, lot 1 : surcharge du forfait d'une séance ({@code PUT /api/v1/seances/{id}/forfait}) : instantané du forfait du référentiel
 * (code, libellé, prix, auteur, instant) posé sur la séance, remplacement par une nouvelle surcharge, prix absent ou nul, auteur pris
 * dans le corps, matrice des statuts, contrôle des paramètres, droits, cloisonnement, audit.
 */
class LaSurchargeDuForfaitDUneSeanceCharacterizationIntegrationTest extends Se5CharacterizationSupport {

    @Override
    protected String dossier() {
        return "surcharge";
    }

    private JsonNodeLecture lecture() {
        return new JsonNodeLecture();
    }

    /** Lectures du forfait courant d'une séance, par le détail et par la liste. */
    private final class JsonNodeLecture {
        Object detail(UUID seance) throws Exception {
            return corps(doGet(detailApi(seance, CENTRE_A), adminA())).path("forfait");
        }

        Object liste() throws Exception {
            return corps(doGet(SEANCES_API + "?centerId=" + CENTRE_A, adminA())).path("items").get(0).path("forfait");
        }
    }

    @Test
    void la_surcharge_prime_sur_la_pec_et_capture_un_instantane_du_forfait_du_referentiel() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID surcharge = forfaitSe5(CENTRE_A, "SURCHARGE", "250.50");
        UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", fixe(1), "CREE");
        diffusions.clear();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 forfait avant (celui de la prise en charge, cinq cles)", lecture().detail(seance));
        m.put("02 surcharge par l'administrateur", reponse(surcharger(seance, surcharge, "compta-01", adminA())));
        m.put("03 colonnes de la seance", surchargeEnBase(seance));
        m.put("04 detail apres (sept cles)", lecture().detail(seance));
        m.put("05 liste apres", lecture().liste());
        jdbc.update("UPDATE forfait SET prix = 999.99, libelle = 'Libelle modifie', code = 'SE5-MODIFIE' WHERE id = ?", surcharge);
        viderLesCaches();
        m.put("06 le referentiel change : la seance garde l'instantane", lecture().detail(seance));
        m.put("07 colonnes de la seance (instantane)", surchargeEnBase(seance));
        m.put("08 diffusions temps reel (aucune)", diffusionsLues());
        m.put("09 statut de la seance inchange", etatDesSeances());
        arbre("nominal", m);
    }

    @Test
    void une_nouvelle_surcharge_remplace_la_precedente_sans_historique_et_renouvelle_l_instant() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID f1 = forfaitSe5(CENTRE_A, "F1", "100.00");
        UUID f2 = forfaitSe5(CENTRE_A, "F2", "180.00");
        UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", fixe(1), "VALIDEE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 premiere surcharge F1 par compta-01", refus(surcharger(seance, f1, "compta-01", adminA())));
        String instant1 = instantDeLaSurcharge(seance);
        m.put("02 colonnes", surchargeEnBase(seance));
        m.put("03 seconde surcharge F2 par compta-02", refus(surcharger(seance, f2, "compta-02", adminA())));
        String instant2 = instantDeLaSurcharge(seance);
        m.put("04 colonnes (F2 et compta-02 remplacent tout)", surchargeEnBase(seance));
        m.put("05 l'instant a change", !instant1.equals(instant2));
        m.put("06 troisieme surcharge : le meme forfait F2 par compta-03", refus(surcharger(seance, f2, "compta-03", adminA())));
        m.put("07 l'instant a encore change et l'auteur est le dernier", List.of(!instant2.equals(instantDeLaSurcharge(seance)),
                surchargeEnBase(seance)));
        m.put("08 aucune table d'historique : une seule ligne de seance", nombreDeSeances(CENTRE_A));
        arbre("remplacement", m);
    }

    @Test
    void le_prix_absent_ou_nul_est_accepte_et_recopie_tel_quel_sans_alerte_ni_arrondi() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        Map<String, Object> m = new LinkedHashMap<>();
        String[][] cas = {{"SANS_PRIX", null}, {"PRIX_ZERO", "0.00"}, {"PRIX_CENTIMES", "12.50"}, {"PRIX_ENTIER", "100"},
                {"PRIX_MAX", "99999999.99"}};
        int n = 0;
        for (String[] c : cas) {
            UUID forfait = forfaitSe5(CENTRE_A, c[0], c[1]);
            UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE_" + c[0], fixe(1 + n), "CREE");
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("reponse", reponse(surcharger(seance, forfait, "compta-01", adminA())));
            ligne.put("colonnes", surchargeEnBase(seance));
            ligne.put("detail", lecture().detail(seance));
            m.put(c[0], ligne);
            n++;
        }
        arbre("prix", m);
    }

    @Test
    void l_auteur_de_la_surcharge_est_celui_du_corps_nettoye_et_non_celui_de_la_session() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID f1 = forfaitSe5(CENTRE_A, "F1", "100.00");
        UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", fixe(1), "CREE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 auteur quelconque (aucun lien avec la session)", refus(surcharger(seance, f1, "quelqu-un-d-autre", adminA())));
        m.put("02 colonnes", surchargeEnBase(seance));
        m.put("03 auteur entoure d'espaces : nettoye", refus(surcharger(seance, f1, "   compta-02   ", adminA())));
        m.put("04 colonnes", surchargeEnBase(seance));
        m.put("05 auteur absent", refus(doPut(forfaitApi(seance), corps("centerId", CENTRE_A, "forfaitId", f1), adminA())));
        m.put("06 auteur vide", refus(surcharger(seance, f1, "", adminA())));
        m.put("07 auteur d'espaces", refus(surcharger(seance, f1, "     ", adminA())));
        m.put("08 colonnes inchangees par les refus", surchargeEnBase(seance));
        m.put("09 auteur de 100 caracteres", refus(surcharger(seance, f1, "x".repeat(100), adminA())));
        m.put("10 colonnes (longueur de l'auteur)", surchargeEnBase(seance).getFirst().get("forfait_override_updated_by").toString().length());
        m.put("11 auteur de 101 caracteres", refus(surcharger(seance, f1, "y".repeat(101), adminA())));
        m.put("12 colonnes apres le refus eventuel", surchargeEnBase(seance).getFirst().get("forfait_override_updated_by").toString().length());
        arbre("auteur", m);
    }

    @Test
    void la_surcharge_n_est_refusee_que_pour_une_seance_facturee_la_matrice_des_statuts() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String statut : List.of("CREE", "VALIDEE", "SIGNEE", "FACTUREE", "ABSENT", "EN_COURS")) {
            nettoyerSe1();
            UUID patient = patientCouvert(CENTRE_A, "PAT_A");
            UUID forfait = forfaitSe5(CENTRE_A, "F1", "100.00");
            UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", fixe(1), statut);
            m.put(statut, issueEtSurcharge(surcharger(seance, forfait, "compta-01", adminA()), seance));
        }
        arbre("matrice_des_statuts", m);
    }

    @Test
    void la_surcharge_d_une_seance_deja_facturee_garde_la_surcharge_posee_avant_la_facturation() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID f1 = forfaitSe5(CENTRE_A, "F1", "100.00");
        UUID f2 = forfaitSe5(CENTRE_A, "F2", "180.00");
        UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", fixe(1), "SIGNEE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 surcharge F1 avant facturation", refus(surcharger(seance, f1, "compta-01", adminA())));
        jdbc.update("UPDATE seances SET statut = 'FACTUREE' WHERE id = ?", seance);
        String avant = empreinteDeLaSurcharge(seance);
        m.put("02 surcharge F2 apres facturation", refus(surcharger(seance, f2, "compta-02", adminA())));
        m.put("03 colonnes inchangees", avant.equals(empreinteDeLaSurcharge(seance)));
        m.put("04 colonnes", surchargeEnBase(seance));
        arbre("facturee", m);
    }

    @Test
    void les_parametres_de_la_surcharge_sont_controles_et_un_refus_n_ecrit_rien() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID patientB = patientCouvert(CENTRE_B, "PAT_B");
        UUID f1 = forfaitSe5(CENTRE_A, "F1", "100.00");
        UUID forfaitB = forfaitSe5(CENTRE_B, "F_B", "300.00");
        UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", fixe(1), "CREE");
        UUID inconnu = idStable("forfait5", "inconnu");
        aliasNommes.put(inconnu.toString(), "<FORFAIT_INCONNU>");
        UUID seanceInconnue = idStable("seance", "inconnue");
        aliasNommes.put(seanceInconnue.toString(), "<SEANCE_INCONNUE>");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 seance inconnue", refus(surcharger(seanceInconnue, f1, "compta-01", adminA())));
        m.put("02 forfait inconnu", refus(surcharger(seance, inconnu, "compta-01", adminA())));
        m.put("03 forfait d'un autre centre", refus(surcharger(seance, forfaitB, "compta-01", adminA())));
        m.put("04 centre absent du corps", refus(doPut(forfaitApi(seance), corps("forfaitId", f1, "userId", "compta-01"), adminA())));
        m.put("05 forfait absent du corps", refus(doPut(forfaitApi(seance), corps("centerId", CENTRE_A, "userId", "compta-01"), adminA())));
        m.put("06 forfait mal forme", refus(doPut(forfaitApi(seance), "{\"centerId\":\"" + CENTRE_A + "\",\"forfaitId\":\"pas-un-uuid\",\"userId\":\"c\"}",
                adminA())));
        m.put("07 centre mal forme", refus(doPut(forfaitApi(seance), "{\"centerId\":\"abc\",\"forfaitId\":\"" + f1 + "\",\"userId\":\"c\"}", adminA())));
        m.put("08 corps vide", refus(doPut(forfaitApi(seance), "{}", adminA())));
        m.put("09 corps illisible", refus(doPut(forfaitApi(seance), "pas du json", adminA())));
        m.put("10 corps absent", refus(doPut(forfaitApi(seance), "", adminA())));
        m.put("11 identifiant de seance illisible", refus(doPut(SEANCES_API + "/abc/forfait", surcharge(f1, "compta-01"), adminA())));
        m.put("12 colonnes inchangees par tous ces refus", surchargeEnBase(seance));
        m.put("13 centre du corps B avec la session A", refus(doPut(forfaitApi(seance), corps("centerId", CENTRE_B, "forfaitId", forfaitB, "userId", "c"),
                adminA())));
        m.put("14 seance du centre A dont le corps vise le centre B (session B)", refus(doPut(forfaitApi(seance),
                corps("centerId", CENTRE_B, "forfaitId", forfaitB, "userId", "c"), adminB())));
        m.put("15 colonnes encore inchangees", surchargeEnBase(seance));
        m.put("16 une seance du centre B n'a pas bouge", surchargeEnBase(seanceEnBase(CENTRE_B, patientB, "SEANCE_B", fixe(1), "CREE")));
        arbre("parametres", m);
    }

    @Test
    void les_droits_par_profil_de_la_surcharge_seul_l_administrateur_peut_la_poser() throws Exception {
        Map<String, Object> profils = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profilsSe1().entrySet()) {
            nettoyerSe1();
            UUID patient = patientCouvert(CENTRE_A, "PAT_A");
            UUID forfait = forfaitSe5(CENTRE_A, "F1", "100.00");
            UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", fixe(1), "CREE");
            profils.put(p.getKey(), statutEtCode(surcharger(seance, forfait, "compta-01", p.getValue())) + " ; surcharge="
                    + surchargeEnBase(seance).getFirst().get("forfait_override_id"));
        }
        nettoyerSe1();
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID forfait = forfaitSe5(CENTRE_A, "F1", "100.00");
        UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", fixe(1), "CREE");
        profils.put("anonyme", statutEtCode(appel("PUT", forfaitApi(seance), surcharge(forfait, "compta-01"), null)) + " ; surcharge="
                + surchargeEnBase(seance).getFirst().get("forfait_override_id"));
        arbre("profils", profils);
    }

    @Test
    void le_cloisonnement_par_centre_de_la_surcharge() throws Exception {
        UUID patientA = patientCouvert(CENTRE_A, "PAT_A");
        UUID patientB = patientCouvert(CENTRE_B, "PAT_B");
        UUID forfaitA = forfaitSe5(CENTRE_A, "F_A", "100.00");
        UUID forfaitB = forfaitSe5(CENTRE_B, "F_B", "300.00");
        UUID seanceA = seanceEnBase(CENTRE_A, patientA, "SEANCE_A", fixe(1), "CREE");
        UUID seanceB = seanceEnBase(CENTRE_B, patientB, "SEANCE_B", fixe(1), "CREE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 administrateur B surcharge la seance du centre A (corps centre B)", refus(doPut(forfaitApi(seanceA),
                corps("centerId", CENTRE_B, "forfaitId", forfaitB, "userId", "c"), adminB())));
        m.put("02 administrateur B surcharge la seance du centre A avec un forfait du centre B (corps centre A)", refus(doPut(forfaitApi(seanceA),
                corps("centerId", CENTRE_A, "forfaitId", forfaitB, "userId", "c"), adminB())));
        m.put("03 administrateur A surcharge la seance du centre B (corps centre A)", refus(doPut(forfaitApi(seanceB),
                corps("centerId", CENTRE_A, "forfaitId", forfaitA, "userId", "c"), adminA())));
        m.put("04 colonnes des deux seances inchangees", List.of(surchargeEnBase(seanceA), surchargeEnBase(seanceB)));
        m.put("05 SUPERADMIN + ADMIN du centre A vise le centre B", refus(doPut(forfaitApi(seanceB),
                corps("centerId", CENTRE_B, "forfaitId", forfaitB, "userId", "c"), as(CENTRE_A, "SUPERADMIN", "ADMIN"))));
        m.put("06 colonnes des deux seances", List.of(surchargeEnBase(seanceA), surchargeEnBase(seanceB)));
        m.put("07 administrateur B surcharge sa propre seance", refus(doPut(forfaitApi(seanceB),
                corps("centerId", CENTRE_B, "forfaitId", forfaitB, "userId", "c"), adminB())));
        m.put("08 colonnes des deux seances", List.of(surchargeEnBase(seanceA), surchargeEnBase(seanceB)));
        arbre("cloisonnement", m);
    }

    @Test
    void les_surcharges_et_leurs_refus_sont_journalises_avec_la_route_et_le_statut() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID forfait = forfaitSe5(CENTRE_A, "F1", "100.00");
        UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", fixe(1), "CREE");
        UUID facturee = seanceEnBase(CENTRE_A, patient, "SEANCE_FACTUREE", fixe(2), "FACTUREE");
        monterLAudit();
        surcharger(seance, forfait, "compta-01", adminA());
        surcharger(seance, forfait, "compta-01", infirmierA());
        surcharger(facturee, forfait, "compta-01", adminA());
        surcharger(idStable("seance", "inconnue"), forfait, "compta-01", adminA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("journal", journalDAudit());
        arbre("audit", m);
    }
}
