package com.hemodialyse.backend.characterization.patientcouverture.pc3;

import com.hemodialyse.backend.characterization.patientcouverture.CouvertureCharacterizationSupport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PC3 : cycle {@code CREE -> VALIDEE -> CLOTUREE} ({@code validate}, {@code close}), {@code session-allowed}, suppression,
 * notifications, diffusions, audit, concurrence simple. Droits de rôles et garde de centre : classe
 * {@code AutorisationsEtCentresDeLaPriseEnCharge}.
 */
class CycleDeLaPriseEnChargeCharacterizationIntegrationTest extends CouvertureCharacterizationSupport {

    @Override
    protected String racine() {
        return "../patientcouverture/pc3/";
    }

    @Override
    protected String dossier() {
        return "cycle";
    }

    private UUID vacancier() {
        return patientCouvert(CENTRE_A, "VACANCIER", true, null);
    }

    private UUID pecAuStatut(String statut) {
        UUID forfait = forfait(CENTRE_A, "HD3", "9500.00");
        return pecEnBase(CENTRE_A, "PEC_" + statut, vacancier(), statut, jour(-5), jour(25), forfait, null, null, null, 1);
    }

    @Test
    void le_cycle_nominal_valide_puis_cloture_et_notifie_le_centre_a_chaque_etape() throws Exception {
        monterLAudit();
        UUID patient = vacancier();
        UUID demande = forfait(CENTRE_A, "HD3", "9500.00");
        UUID accorde = forfait(CENTRE_A, "HD4", "11000.00");
        Resp c = creerLaPec("PEC_1", patient, CENTRE_A, jour(-5), jour(25), demande, adminA());
        UUID pec = UUID.fromString(corps(c).path("id").asString());
        ecriture("creation", c);
        diffusions.clear();
        ecriture("validation", valider(pec, CENTRE_A, jour(0), jour(20), accorde, adminA()));
        etatDesPec("apres_la_validation");
        lecture("session_autorisee_apres_la_validation", sessionAllowed(pec, CENTRE_A, adminA()));
        ecriture("cloture", cloturer(pec, CENTRE_A, adminA()));
        etatDesPec("apres_la_cloture");
        lecture("session_autorisee_apres_la_cloture", sessionAllowed(pec, CENTRE_A, adminA()));
        notifications("notifications_durables");
        diffusionsCapturees("diffusions_nouveau_code");
        audit("audit_nouveau_code");
    }

    @Test
    void chaque_transition_hors_du_cycle_est_refusee_et_ne_change_rien() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String statut : List.of("CREE", "VALIDEE", "CLOTUREE", "REFUSEE")) {
            UUID pec = pecAuStatut(statut);
            m.put("valider depuis " + statut, refus(valider(pec, CENTRE_A, jour(0), jour(20), null, adminA())));
            jdbc.update("UPDATE prise_en_charge SET statut = ?, date_debut_effectif = NULL, date_fin_effectif = NULL WHERE id = ?", statut, pec);
            m.put("clôturer depuis " + statut, refus(cloturer(pec, CENTRE_A, adminA())));
            m.put("état après depuis " + statut, jdbc.queryForObject("SELECT statut FROM prise_en_charge WHERE id = ?", String.class, pec));
            jdbc.update("DELETE FROM prise_en_charge WHERE id = ?", pec);
            jdbc.update("DELETE FROM patients WHERE center_id = ?", CENTRE_A);
            jdbc.update("DELETE FROM forfait WHERE center_id = ?", CENTRE_A);
        }
        arbre("transitions_nouveau_code", m);
    }

    @Test
    void la_validation_enregistre_la_periode_et_le_forfait_effectifs_sans_les_controler() throws Exception {
        UUID demande = forfait(CENTRE_A, "HD3", "9500.00");
        UUID autreCentre = forfait(CENTRE_B, "HDB", "7000.00");
        Map<String, Object> m = new LinkedHashMap<>();
        String[] noms = {"dates et forfait nuls", "fin avant début", "période passée", "forfait inconnu", "forfait d'un autre centre"};
        int i = 0;
        for (String cas : noms) {
            UUID patient = patientCouvert(CENTRE_A, "P" + (++i), true, null);
            UUID pec = pecEnBase(CENTRE_A, "PEC_" + i, patient, "CREE", jour(-5), jour(25), demande, null, null, null, i);
            Resp r = switch (i) {
                case 1 -> valider(pec, CENTRE_A, null, null, null, adminA());
                case 2 -> valider(pec, CENTRE_A, jour(20), jour(-5), null, adminA());
                case 3 -> valider(pec, CENTRE_A, jour(-100), jour(-50), null, adminA());
                case 4 -> valider(pec, CENTRE_A, jour(0), jour(20), UUID.nameUUIDFromBytes("forfait".getBytes()), adminA());
                default -> valider(pec, CENTRE_A, jour(0), jour(20), autreCentre, adminA());
            };
            m.put(cas, refus(r));
        }
        arbre("acceptations_sans_controle", m);
        sql("pec_validees", "SELECT p.nom AS patient, c.statut, c.date_debut_effectif, c.date_fin_effectif, c.forfait_effectif_id IS NOT NULL AS avec_forfait "
                + "FROM prise_en_charge c JOIN patients p ON p.id = c.patient_id WHERE c.center_id = '" + CENTRE_A + "' ORDER BY p.nom");
    }

    @Test
    void la_validation_et_la_cloture_refusent_une_pec_inconnue_ou_un_corps_illisible() throws Exception {
        UUID pec = pecAuStatut("CREE");
        UUID inconnue = UUID.nameUUIDFromBytes("pec-inconnue".getBytes());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("valider une PEC inconnue", refus(valider(inconnue, CENTRE_A, jour(0), jour(20), null, adminA())));
        m.put("valider sans centre", refus(doPost(PEC + "/" + pec + "/validate", json("{'dateDebutEffectif':null}"), adminA())));
        m.put("valider avec un corps vide", refus(doPost(PEC + "/" + pec + "/validate", "", adminA())));
        m.put("valider avec un identifiant illisible", refus(doPost(PEC + "/zzz/validate", json("{'centerId':'" + CENTRE_A + "'}"), adminA())));
        m.put("valider une PEC du centre A avec le centre B", refus(valider(pec, CENTRE_B, jour(0), jour(20), null, adminA())));
        m.put("clôturer une PEC inconnue", refus(cloturer(inconnue, CENTRE_A, adminA())));
        m.put("clôturer sans centre", refus(doPost(PEC + "/" + pec + "/close", json("{}"), adminA())));
        m.put("clôturer avec un corps vide", refus(doPost(PEC + "/" + pec + "/close", "", adminA())));
        m.put("clôturer avec un centre illisible", refus(doPost(PEC + "/" + pec + "/close", json("{'centerId':'zzz'}"), adminA())));
        m.put("clôturer avec un corps tableau", refus(doPost(PEC + "/" + pec + "/close", "[]", adminA())));
        arbre("refus_nouveau_code", m);
        etatDesPec("etat_inchange");
        notifications("aucune_notification");
    }

    @Test
    void la_session_n_est_autorisee_que_pour_une_pec_validee_et_pour_un_non_vacancier_avec_attestation_sans_regarder_les_periodes() throws Exception {
        supposerLeJourDeLaJvmEgalAuJourDuCentre();
        Map<String, Object> m = new LinkedHashMap<>();
        UUID forfait = forfait(CENTRE_A, "HD3", "9500.00");
        int i = 0;
        for (String statut : List.of("CREE", "VALIDEE", "CLOTUREE", "REFUSEE")) {
            UUID patient = patientCouvert(CENTRE_A, "V" + (++i), true, null);
            UUID pec = pecEnBase(CENTRE_A, "PEC_" + i, patient, statut, jour(-5), jour(25), forfait, null, null, null, i);
            m.put("vacancier, PEC " + statut, refus(sessionAllowed(pec, CENTRE_A, adminA())) + " " + corps(sessionAllowed(pec, CENTRE_A, adminA())).path("allowed"));
        }
        UUID sans = patientCouvert(CENTRE_A, "NV_SANS", false, null);
        m.put("non-vacancier validé sans attestation", corps(sessionAllowed(pecEnBase(CENTRE_A, "PEC_NV1", sans, "VALIDEE", jour(-5), jour(25), forfait, null, null, null, 10), CENTRE_A, adminA())).path("allowed").toString());
        UUID avec = patientCouvert(CENTRE_A, "NV_AVEC", false, null);
        attestation(CENTRE_A, avec, jour(-5), jour(5));
        m.put("non-vacancier validé avec attestation", corps(sessionAllowed(pecEnBase(CENTRE_A, "PEC_NV2", avec, "VALIDEE", jour(-5), jour(25), forfait, null, null, null, 11), CENTRE_A, adminA())).path("allowed").toString());
        UUID echue = patientCouvert(CENTRE_A, "NV_ECHUE", false, null);
        attestation(CENTRE_A, echue, jour(-50), jour(-1));
        m.put("non-vacancier validé, attestation échue hier", corps(sessionAllowed(pecEnBase(CENTRE_A, "PEC_NV3", echue, "VALIDEE", jour(-5), jour(25), forfait, null, null, null, 12), CENTRE_A, adminA())).path("allowed").toString());
        UUID perimee = patientCouvert(CENTRE_A, "V_PERIMEE", true, null);
        m.put("vacancier validé, période effective échue", corps(sessionAllowed(pecEnBase(CENTRE_A, "PEC_P", perimee, "VALIDEE", jour(-100), jour(-50), forfait, jour(-100), jour(-50), forfait, 13), CENTRE_A, adminA())).path("allowed").toString());
        UUID futur = patientCouvert(CENTRE_A, "V_FUTUR", true, null);
        m.put("vacancier validé, période effective future", corps(sessionAllowed(pecEnBase(CENTRE_A, "PEC_F", futur, "VALIDEE", jour(50), jour(100), forfait, jour(50), jour(100), forfait, 14), CENTRE_A, adminA())).path("allowed").toString());
        UUID sansPeriode = patientCouvert(CENTRE_A, "V_SANS_PERIODE", true, null);
        m.put("vacancier validé sans aucune date", corps(sessionAllowed(pecEnBase(CENTRE_A, "PEC_N", sansPeriode, "VALIDEE", null, null, null, null, null, null, 15), CENTRE_A, adminA())).path("allowed").toString());
        arbre("autorisations_de_session_nouveau_code", m);
        Map<String, Object> refus = new LinkedHashMap<>();
        refus.put("PEC inconnue", refus(sessionAllowed(UUID.nameUUIDFromBytes("inconnue".getBytes()), CENTRE_A, adminA())));
        refus.put("PEC du centre A lue avec le centre B", refus(sessionAllowed(pecEnBase(CENTRE_A, "PEC_Z", patientCouvert(CENTRE_A, "Z", true, null), "VALIDEE", jour(-5), jour(25), forfait, null, null, null, 20), CENTRE_B, adminA())));
        refus.put("utilisateur absent", refus(doGet(PEC + "/" + UUID.nameUUIDFromBytes("x".getBytes()) + "/session-allowed?centerId=" + CENTRE_A, adminA())));
        refus.put("centre absent", refus(doGet(PEC + "/" + UUID.nameUUIDFromBytes("x".getBytes()) + "/session-allowed?userId=u", adminA())));
        refus.put("identifiant illisible", refus(doGet(PEC + "/zzz/session-allowed?centerId=" + CENTRE_A + "&userId=u", adminA())));
        arbre("refus_de_la_lecture_nouveau_code", refus);
    }

    @Test
    void la_suppression_exige_une_pec_du_centre_quel_que_soit_son_statut_et_notifie_avec_le_patient_donne_dans_l_url() throws Exception {
        UUID forfait = forfait(CENTRE_A, "HD3", "9500.00");
        Map<String, Object> m = new LinkedHashMap<>();
        List<UUID> patients = new ArrayList<>();
        int i = 0;
        for (String statut : List.of("CREE", "VALIDEE", "CLOTUREE")) {
            UUID patient = patientCouvert(CENTRE_A, "S" + (++i), true, null);
            patients.add(patient);
            UUID pec = pecEnBase(CENTRE_A, "PEC_" + statut, patient, statut, jour(-5), jour(25), forfait, null, null, null, i);
            m.put("supprimer une PEC " + statut, refus(supprimer(pec, CENTRE_A, patient, adminA())));
        }
        UUID autre = patientCouvert(CENTRE_A, "AUTRE", true, null);
        UUID sansPatient = pecEnBase(CENTRE_A, "PEC_SP", autre, "CREE", jour(-5), jour(25), forfait, null, null, null, 5);
        m.put("supprimer sans patientId", refus(supprimer(sansPatient, CENTRE_A, null, adminA())));
        UUID mauvaisPatient = pecEnBase(CENTRE_A, "PEC_MP", autre, "CREE", jour(-5), jour(25), forfait, null, null, null, 6);
        m.put("supprimer avec le patientId d'un autre patient", refus(supprimer(mauvaisPatient, CENTRE_A, patients.get(0), adminA())));
        m.put("supprimer une PEC inconnue", refus(supprimer(UUID.nameUUIDFromBytes("inconnue".getBytes()), CENTRE_A, null, adminA())));
        UUID pecA = pecEnBase(CENTRE_A, "PEC_A", autre, "CREE", jour(-5), jour(25), forfait, null, null, null, 7);
        m.put("supprimer une PEC du centre A avec le centre B", refus(supprimer(pecA, CENTRE_B, null, adminA())));
        m.put("supprimer sans centre", refus(doDelete(PEC + "/" + pecA, adminA())));
        m.put("supprimer avec un identifiant illisible", refus(doDelete(PEC + "/zzz?centerId=" + CENTRE_A, adminA())));
        arbre("suppressions_nouveau_code", m);
        etatDesPec("pec_restantes");
        notifications("notifications_durables");
    }

    @Test
    void deux_validations_simultanees_de_la_meme_pec_se_terminent_validee_sans_verrou() throws Exception {
        // PC3 : les écritures de PEC prennent le verrou du centre en tête ; la ligne de paramètres du centre existe en production
        // (sa toute première création concurrente est une autre règle, 409, ADR 0059) : on la pose ici. Seules les issues possibles
        // (200 et 200, ou 200 et 422) sont verrouillées : avec le verrou, la seconde voit « VALIDEE » (200 et 422).
        joursOuvertsDuCentre(CENTRE_A, TOUS_LES_JOURS);
        UUID pec = pecAuStatut("CREE");
        List<Resp> reponses = enParallele(() -> valider(pec, CENTRE_A, jour(0), jour(20), null, adminA()),
                () -> valider(pec, CENTRE_A, jour(1), jour(21), null, adminA()));
        List<Integer> statuts = reponses.stream().map(Resp::status).sorted().toList();
        Map<String, Object> m = new LinkedHashMap<>();
        // Sans verrou, la seconde peut lire encore « CREE » (deux 200) ou voir « VALIDEE » (un 200 et un 422) : seules ces issues sont verrouillées.
        m.put("issue possible (200 et 200, ou 200 et 422)", statuts.equals(List.of(200, 200)) || statuts.equals(List.of(200, 422)));
        m.put("statut final", jdbc.queryForObject("SELECT statut FROM prise_en_charge WHERE id = ?", String.class, pec));
        arbre("issue_de_la_course", m);
    }

    /**
     * Revue PC2/PC3 : les écritures de PEC prennent le verrou du centre, dont la ligne {@code planning_parametres} est créée à la
     * première prise (ADR 0059). Un centre SANS cette ligne : une écriture seule réussit et crée la ligne ; deux premières
     * écritures simultanées peuvent voir le second refusé 409 (création concurrente de la ligne), jamais une erreur serveur.
     * Chemin non pré-posé par les autres scénarios (qui appellent {@code joursOuvertsDuCentre}) ; absent des centres de
     * production existants, relevé par {@code verifications-production.md} §42-11.
     */
    @Test
    void un_centre_sans_ligne_de_parametres_recoit_sa_ligne_a_la_premiere_ecriture_de_pec_et_une_course_initiale_se_termine_en_200_ou_409() throws Exception {
        jdbc.update("DELETE FROM planning_parametres WHERE center_id = ?", CENTRE_A);
        UUID forfait = forfait(CENTRE_A, "HD3", "9500.00");
        UUID patient = vacancier();
        UUID seule = pecEnBase(CENTRE_A, "PEC_SEULE", patient, "CREE", jour(-5), jour(25), forfait, null, null, null, 1);
        jdbc.update("DELETE FROM planning_parametres WHERE center_id = ?", CENTRE_A);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("une écriture seule", refus(valider(seule, CENTRE_A, jour(0), jour(20), null, adminA())));
        m.put("ligne créée", jdbc.queryForObject("SELECT COUNT(*) FROM planning_parametres WHERE center_id = ?", Integer.class, CENTRE_A));

        jdbc.update("DELETE FROM planning_parametres WHERE center_id = ?", CENTRE_A);
        UUID a = pecEnBase(CENTRE_A, "PEC_A", patient, "CREE", jour(-5), jour(25), forfait, null, null, null, 2);
        UUID b = pecEnBase(CENTRE_A, "PEC_B", patient, "CREE", jour(-5), jour(25), forfait, null, null, null, 3);
        jdbc.update("DELETE FROM planning_parametres WHERE center_id = ?", CENTRE_A);
        List<Resp> reponses = enParallele(() -> valider(a, CENTRE_A, jour(0), jour(20), null, adminA()),
                () -> valider(b, CENTRE_A, jour(0), jour(20), null, adminA()));
        List<Integer> statuts = reponses.stream().map(Resp::status).sorted().toList();
        m.put("course initiale : 200 et 200, ou 200 et 409 (création concurrente de la ligne)",
                statuts.equals(List.of(200, 200)) || statuts.equals(List.of(200, 409)));
        arbre("centre_sans_ligne_de_parametres", m);
    }
}
