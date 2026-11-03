package com.hemodialyse.backend.characterization.patientcouverture.pc1;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PC1 : {@code PUT /api/v1/patients/{id}} (identité, numéro d'assurance, changements d'état et mouvements, reprise, attestation,
 * bascule de qualité d'assuré, écritures simultanées). Les 8 règles de place (P5) et la libération des places échues (P7) sont
 * verrouillées par leurs filets ; les patients de ces scénarios n'ont pas de place.
 */
class ModificationEtEtatsDeLaFichePatientCharacterizationIntegrationTest extends PatientCouvertureCharacterizationSupport {

    @Override
    protected String dossier() {
        return "modification";
    }

    private Map<String, Object> enEtat(String alias, String etat, Integer decalage) {
        Map<String, Object> f = ficheDuPatient(alias);
        if (etat != null) f.put("etatPatient", etat);
        else f.remove("etatPatient");
        if (decalage != null) f.put("dateEvenementEtat", jour(decalage).toString());
        return f;
    }

    private String libelle(String etat, Integer decalage) {
        return etat + (decalage == null ? " sans date" : decalage < 0 ? " J" + decalage : " J+" + decalage);
    }

    // ------------------------------------------------------------------ identité

    @Test
    void une_modification_nominale_change_l_identite_garde_le_code_patient_et_n_ajoute_aucun_mouvement() throws Exception {
        UUID id = patientSansPlace("P1");
        Map<String, Object> f = ficheDuPatient("P1");
        f.put("nom", "Nouveau nom");
        f.put("prenom", "Nouveau prenom");
        f.put("sexe", "F");
        f.put("civilite", "Mme");
        f.put("dateNaissance", jour(-21000).toString());
        f.put("lieuNaissance", "Lieu");
        f.put("adresse", "Une adresse");
        f.put("telMobile", "0555000000");
        f.put("email", "a@b.c");
        f.put("nombreEnfants", 3);
        f.put("sousKt", true);
        f.put("enSommeil", true);
        ecriture("reponse", sansCodeAleatoire(modifier(id, f, adminA())));
        etatDeLIdentite("fiche_apres");
        sql("code_patient_conserve", "SELECT code_patient FROM patients WHERE id = '" + id + "'");
        etatDesMouvements("mouvements");
    }

    @Test
    void le_numero_d_assurance_du_corps_n_est_pas_applique_a_une_modification_seul_le_numero_de_l_assure_l_est() throws Exception {
        UUID id = patientSansPlace("P1");
        Map<String, Object> f = ficheDuPatient("P1");
        f.put("numeroAssurance", "PC1-AUTRE-NUMERO");
        Resp r = modifier(id, f, adminA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statut", refus(r));
        m.put("numero_assurance en base", jdbc.queryForObject("SELECT numero_assurance FROM patients WHERE id = ?", String.class, id));
        m.put("assure_numero_assurance en base", jdbc.queryForObject("SELECT assure_numero_assurance FROM patients WHERE id = ?", String.class, id));
        arbre("numero_d_assurance_ignore_nouveau_code", m);
        Map<String, Object> pris = ficheDuPatient("P2");
        patientSansPlace("P2");
        pris.put("numeroAssurance", "ASS-P3-P1");
        arbre("numero_d_un_autre_patient", Map.of("statut", refus(modifier(idDe("P2"), pris, adminA()))));
    }

    @Test
    void un_patient_inconnu_ou_un_identifiant_illisible_est_refuse() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("identifiant inconnu", refus(modifier(UUID.nameUUIDFromBytes("inconnu".getBytes()), ficheDuPatient("X"), adminA())));
        m.put("identifiant illisible", refus(doPut(PATIENTS + "/pas-un-uuid", corps(ficheDuPatient("X")), adminA())));
        m.put("fiches écrites", nombreDeFiches());
        arbre("patient_inconnu_nouveau_code", m);
    }

    // ------------------------------------------------------------------ états et mouvements

    /**
     * Revue M4, scénario NEUF : un état non nul hors de la liste fermée est rendu tel quel, n'est jamais réécrit par un {@code PUT}
     * sans état, et le patient n'est pas plaçable (422 {@code PATIENT_ETAT_INCONNU}) tant que l'état n'est pas corrigé.
     */
    @Test
    void un_etat_inconnu_en_base_est_conserve_et_le_patient_n_est_pas_placable() throws Exception {
        decorDePlacement();
        joursOuvertsDuCentre(CENTRE_A, TOUS_LES_JOURS);
        Map<String, Object> m = new LinkedHashMap<>();
        UUID a = patientSansPlace("U1");
        etatEnBase(a, "BIDON", null);
        m.put("PUT sans état sur BIDON", refus(modifier(a, enEtat("U1", null, null), adminA())));
        m.put("état en base après", jdbc.queryForObject("SELECT etat_patient FROM patients WHERE id = ?", String.class, a));
        m.put("fiche rendue", corps(lireLaFiche(a, CENTRE_A, adminA())).path("etatPatient").asString());
        UUID b = patientSansPlace("U2");
        etatEnBase(b, "permanent", null);
        Map<String, Object> placee = enEtat("U2", null, null);
        placee.put("salleId", salle1.toString());
        placee.put("positionId", matin.toString());
        placee.put("generateurId", g1a.toString());
        placee.put("jourLundi", true);
        m.put("PUT avec une place sur 'permanent'", refus(modifier(b, placee, adminA())));
        m.put("état en base après la tentative", jdbc.queryForObject("SELECT etat_patient FROM patients WHERE id = ?", String.class, b));
        m.put("salle en base après la tentative", String.valueOf(jdbc.queryForObject("SELECT salle_id FROM patients WHERE id = ?", UUID.class, b)));
        UUID c = patientSansPlace("U3");
        etatEnBase(c, "BIDON", null);
        m.put("PUT avec un état valide sur BIDON", refus(modifier(c, enEtat("U3", "GUERRI", 2), adminA())));
        m.put("état en base après correction", jdbc.queryForObject("SELECT etat_patient FROM patients WHERE id = ?", String.class, c));
        arbre("etat_inconnu_conserve", m);
    }

    @Test
    void chaque_changement_d_etat_ecrit_le_mouvement_de_son_type_avec_l_etat_precedent_et_la_date_d_effet() throws Exception {
        Object[][] cas = {
                {"PERMANENT", null, "TRANSFERE", 5}, {"PERMANENT", null, "DECEDE", 5}, {"PERMANENT", null, "GREFFE", 5},
                {"PERMANENT", null, "GUERRI", 5}, {"PERMANENT", null, "OCCASIONNEL", 5}, {"PERMANENT", null, "VACANCIER_LOCAL", 5},
                {"PERMANENT", null, "VACANCIER_ETRANGER", 5}, {"PERMANENT", null, "OCCASIONNEL", null},
                {"PERMANENT", null, "TRANSFERE", -5}, {"PERMANENT", null, "DECEDE", -5}, {"PERMANENT", null, "BIDON", null},
                {"PERMANENT", null, "PERMANENT", null}, {"PERMANENT", null, "PERMANENT", 5},
                {"GUERRI", 5, "PERMANENT", null}, {"TRANSFERE", 5, "PERMANENT", null}, {"DECEDE", 5, "PERMANENT", null},
                {"GUERRI", 5, "PERMANENT", -3}, {"OCCASIONNEL", 5, "PERMANENT", null}, {"VACANCIER_LOCAL", 5, "PERMANENT", null},
                {"TRANSFERE", 5, "TRANSFERE", 6}, {"TRANSFERE", 5, "TRANSFERE", 5}, {"OCCASIONNEL", 5, "OCCASIONNEL", 6},
                {"OCCASIONNEL", 5, "OCCASIONNEL", null}, {"DECEDE", 5, "GREFFE", 5}, {"GUERRI", 5, "TRANSFERE", 4},
                {"OCCASIONNEL", 5, "VACANCIER_LOCAL", 5}, {"VACANCIER_LOCAL", 5, "DECEDE", 6}};
        Map<String, Object> statuts = new LinkedHashMap<>();
        int i = 0;
        for (Object[] c : cas) {
            String alias = String.format("T%02d", ++i);
            UUID id = patientSansPlace(alias);
            etatEnBase(id, (String) c[0], (Integer) c[1]);
            Resp r = modifier(id, enEtat(alias, (String) c[2], (Integer) c[3]), adminA());
            statuts.put(alias + " " + libelle((String) c[0], (Integer) c[1]) + " -> " + libelle((String) c[2], (Integer) c[3]), refus(r));
        }
        arbre("statuts_nouveau_code", statuts);
        etatDeLIdentite("fiches_apres_nouveau_code");
        etatDesMouvements("mouvements_nouveau_code");
    }

    @Test
    void un_etat_de_sortie_sans_date_ou_une_date_avant_l_admission_est_refuse_et_la_fiche_est_inchangee() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        int i = 0;
        for (String etat : List.of("TRANSFERE", "DECEDE", "GREFFE", "GUERRI")) {
            String alias = "S" + (++i);
            patientSansPlace(alias);
            String avant = empreinteDeLaFiche();
            m.put(etat + " sans date", refus(modifier(idDe(alias), enEtat(alias, etat, null), adminA())) + (avant.equals(empreinteDeLaFiche()) ? "" : " [ECRIT]"));
        }
        for (String etat : List.of("TRANSFERE", "OCCASIONNEL", "PERMANENT")) {
            String alias = "A" + (++i);
            patientSansPlace(alias);
            String avant = empreinteDeLaFiche();
            m.put(etat + " J-401, admission J-400", refus(modifier(idDe(alias), enEtat(alias, etat, -401), adminA())) + (avant.equals(empreinteDeLaFiche()) ? "" : " [ECRIT]"));
        }
        patientSansPlace("B1");
        Map<String, Object> egale = enEtat("B1", "TRANSFERE", -400);
        m.put("TRANSFERE le jour même de l'admission", refus(modifier(idDe("B1"), egale, adminA())));
        patientSansPlace("B2");
        Map<String, Object> admissionReculee = enEtat("B2", "TRANSFERE", -300);
        admissionReculee.put("dateAdmission", jour(-200).toString());
        m.put("admission reculée après la date d'événement saisie", refus(modifier(idDe("B2"), admissionReculee, adminA())));
        arbre("sorties_refusees", m);
        etatDeLIdentite("fiches_apres");
        etatDesMouvements("mouvements");
    }

    @Test
    void sans_etat_dans_le_corps_la_fiche_garde_son_etat_courant_mais_la_date_d_evenement_du_corps_s_applique() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        UUID a = patientSansPlace("K1");
        etatEnBase(a, "GUERRI", 5);
        m.put("GUERRI J+5, corps sans état ni date", refus(modifier(a, enEtat("K1", null, null), adminA())));
        UUID b = patientSansPlace("K2");
        etatEnBase(b, "GUERRI", 5);
        Map<String, Object> avecDate = enEtat("K2", null, 6);
        m.put("GUERRI J+5, corps sans état avec date J+6", refus(modifier(b, avecDate, adminA())));
        UUID c = patientSansPlace("K3");
        etatEnBase(c, "OCCASIONNEL", 5);
        m.put("OCCASIONNEL J+5, corps sans état ni date", refus(modifier(c, enEtat("K3", null, null), adminA())));
        UUID d = patientSansPlace("K4");
        etatEnBase(d, "PERMANENT", null);
        Map<String, Object> nul = enEtat("K4", null, null);
        nul.put("etatPatient", null);
        m.put("PERMANENT, état explicitement nul", refus(modifier(d, nul, adminA())));
        arbre("etat_absent_du_corps_nouveau_code", m);
        etatDeLIdentite("fiches_apres_nouveau_code");
        etatDesMouvements("mouvements_nouveau_code");
    }

    @Test
    void la_reprise_a_l_etat_permanent_efface_ou_garde_la_date_selon_le_corps_et_date_le_mouvement() throws Exception {
        UUID a = patientSansPlace("R1");
        etatEnBase(a, "TRANSFERE", 3);
        ecriture("reprise_sans_date", modifier(a, enEtat("R1", "PERMANENT", null), adminA()));
        UUID b = patientSansPlace("R2");
        etatEnBase(b, "TRANSFERE", 3);
        ecriture("reprise_datee", modifier(b, enEtat("R2", "PERMANENT", -2), adminA()));
        etatDeLIdentite("fiches_apres");
        etatDesMouvements("mouvements");
    }

    @Test
    void deux_modifications_simultanees_de_la_meme_fiche_sont_acceptees_et_la_derniere_ecriture_gagne() throws Exception {
        // Le verrou du centre est pris en tête de toute écriture (revue M3). La ligne de paramètres du centre existe en production ;
        // sa toute première création concurrente est une autre règle (la perdante reçoit 409, ADR 0059) : on la pose ici.
        joursOuvertsDuCentre(CENTRE_A, TOUS_LES_JOURS);
        UUID id = patientSansPlace("C1");
        Map<String, Object> a = ficheDuPatient("C1");
        a.put("adresse", "Adresse A");
        Map<String, Object> b = ficheDuPatient("C1");
        b.put("adresse", "Adresse B");
        List<Resp> reponses = enParallele(() -> modifier(id, a, adminA()), () -> modifier(id, b, adminA()));
        String finale = jdbc.queryForObject("SELECT adresse FROM patients WHERE id = ?", String.class, id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statuts triés", reponses.stream().map(Resp::status).sorted().toList());
        m.put("adresse finale est l'une des deux", List.of("Adresse A", "Adresse B").contains(finale));
        m.put("mouvements écrits", nombreDeMouvements());
        arbre("ecritures_simultanees", m);
    }

    // ------------------------------------------------------------------ attestation

    @Test
    void sans_attestation_dans_le_corps_une_attestation_existante_suffit_et_un_vacancier_n_en_a_pas_besoin() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        UUID sans = patientSansPlace("T1");
        Map<String, Object> f1 = ficheDuPatient("T1");
        f1.remove("attestationDebut");
        f1.remove("attestationFin");
        m.put("permanent sans attestation existante ni saisie", refus(modifier(sans, f1, adminA())));
        UUID avec = patientSansPlace("T2");
        jdbc.update("INSERT INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), avec, CENTRE_A, java.sql.Date.valueOf(jour(-400)), java.sql.Date.valueOf(jour(3000)));
        Map<String, Object> f2 = ficheDuPatient("T2");
        f2.remove("attestationDebut");
        f2.remove("attestationFin");
        m.put("permanent avec attestation existante", refus(modifier(avec, f2, adminA())));
        UUID vac = patientSansPlace("T3");
        Map<String, Object> f3 = enEtat("T3", "VACANCIER_LOCAL", 5);
        f3.remove("attestationDebut");
        f3.remove("attestationFin");
        m.put("vacancier sans attestation", refus(modifier(vac, f3, adminA())));
        UUID demi = patientSansPlace("T4");
        Map<String, Object> f4 = ficheDuPatient("T4");
        f4.remove("attestationFin");
        m.put("début seul sans attestation existante", refus(modifier(demi, f4, adminA())));
        arbre("attestation_obligatoire", m);
        etatDesAttestations("attestations");
    }

    @Test
    void une_attestation_identique_n_est_jamais_dupliquee_une_attestation_differente_s_ajoute_a_l_historique() throws Exception {
        UUID id = patientSansPlace("D1");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("première saisie", refus(modifier(id, ficheDuPatient("D1"), adminA())));
        m.put("même saisie", refus(modifier(id, ficheDuPatient("D1"), adminA())));
        Map<String, Object> autre = ficheDuPatient("D1");
        autre.put("attestationFin", jour(3001).toString());
        m.put("autre date de fin", refus(modifier(id, autre, adminA())));
        Map<String, Object> inversee = ficheDuPatient("D1");
        inversee.put("attestationDebut", jour(5).toString());
        inversee.put("attestationFin", jour(-5).toString());
        m.put("fin avant début", refus(modifier(id, inversee, adminA())));
        arbre("saisies", m);
        etatDesAttestations("attestations");
    }

    // ------------------------------------------------------------------ assuré

    @Test
    void la_bascule_de_assure_lui_meme_a_un_assure_tiers_et_retour_ouvre_et_clot_l_affectation_principale() throws Exception {
        UUID id = patientSansPlace("Q1");
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> enfant = ficheDuPatient("Q1");
        enfant.put("qualiteAssure", "ENFANT");
        m.put("enfant sans assuré", refus(modifier(id, enfant, adminA())));
        enfant.put("assureNumeroAssurance", "PC1-ASSURE-1");
        enfant.put("assureNom", "Parent");
        enfant.put("assurePrenom", "Premier");
        m.put("enfant avec assuré", refus(modifier(id, enfant, adminA())));
        sql("affectations_apres_enfant", "SELECT numero_assurance, is_primary, date_debut_affectation, date_fin_affectation FROM assure_patient "
                + "WHERE patient_id = '" + id + "' ORDER BY numero_assurance, is_primary");
        Map<String, Object> luiMeme = ficheDuPatient("Q1");
        m.put("retour à assuré lui-même", refus(modifier(id, luiMeme, adminA())));
        arbre("statuts", m);
        sql("affectations_apres_retour", "SELECT numero_assurance, is_primary, date_debut_affectation, date_fin_affectation FROM assure_patient "
                + "WHERE patient_id = '" + id + "' ORDER BY numero_assurance, is_primary");
        sql("assures", "SELECT numero_assurance, nom, prenom FROM assure WHERE center_id = '" + CENTRE_A + "' ORDER BY numero_assurance");
        etatDeLIdentite("fiche_apres");
    }
}
