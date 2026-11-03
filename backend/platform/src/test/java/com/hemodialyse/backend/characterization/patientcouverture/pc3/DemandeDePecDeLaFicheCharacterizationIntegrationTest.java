package com.hemodialyse.backend.characterization.patientcouverture.pc3;

import com.hemodialyse.backend.characterization.patientcouverture.CouvertureCharacterizationSupport;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PC3 : la demande de prise en charge saisie sur la fiche patient ({@code POST}/{@code PUT /api/v1/patients}, champs
 * {@code pecId}, {@code pecDateDebutDemande}, {@code pecDateFinDemande}, {@code pecForfaitDemandeId}, RG-PEC-007) : création,
 * révision, jamais de retour à « créée ». Le statut des fiches et les autres champs sont verrouillés par PC1.
 */
class DemandeDePecDeLaFicheCharacterizationIntegrationTest extends CouvertureCharacterizationSupport {

    @Override
    protected String racine() {
        return "../patientcouverture/pc3/";
    }

    @Override
    protected String dossier() {
        return "fiche";
    }

    private Map<String, Object> ficheAvecDemande(String nom, String numero, UUID pecId, Object debut, Object fin, UUID forfait) {
        Map<String, Object> f = ficheMap(nom, numero);
        f.put("pecId", pecId);
        f.put("pecDateDebutDemande", debut == null ? null : debut.toString());
        f.put("pecDateFinDemande", fin == null ? null : fin.toString());
        f.put("pecForfaitDemandeId", forfait);
        return f;
    }

    private UUID idPec(String alias) {
        UUID id = idStable("pec-fiche", alias);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        return id;
    }

    @Test
    void la_fiche_creee_avec_une_demande_complete_cree_une_pec_au_statut_cree_sous_l_identifiant_donne_sans_controle_d_attestation() throws Exception {
        UUID forfait = forfait(CENTRE_A, "HD3", "9500.00");
        UUID pec = idPec("PEC_FICHE");
        Resp r = creerLaFiche("PATIENT", ficheAvecDemande("Dupont", "PC3-001", pec, jour(-5), jour(25), forfait), adminA());
        ecriture("reponse", sansCodeAleatoire(r));
        etatDesPec("pec_ecrite");
        lecture("pec_du_patient", pecsDuPatient(UUID.fromString(corps(r).path("id").asString()), CENTRE_A, adminA()));
    }

    @Test
    void une_demande_incomplete_ou_absente_ne_cree_aucune_pec_et_une_demande_sans_forfait_ni_identifiant_est_acceptee() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sans début", refus(doPost(PATIENTS, corps(ficheAvecDemande("A", "PC3-001", null, null, jour(25), null)), adminA())));
        m.put("sans fin", refus(doPost(PATIENTS, corps(ficheAvecDemande("B", "PC3-002", null, jour(-5), null, null)), adminA())));
        m.put("aucune demande", refus(doPost(PATIENTS, corps(ficheMap("C", "PC3-003")), adminA())));
        m.put("fin avant début", refus(doPost(PATIENTS, corps(ficheAvecDemande("D", "PC3-004", null, jour(25), jour(-5), null)), adminA())));
        m.put("sans forfait ni identifiant", refus(doPost(PATIENTS, corps(ficheAvecDemande("E", "PC3-005", null, jour(-5), jour(25), null)), adminA())));
        m.put("forfait inconnu", refus(doPost(PATIENTS, corps(ficheAvecDemande("F", "PC3-006", null, jour(-5), jour(25), UUID.nameUUIDFromBytes("f".getBytes()))), adminA())));
        arbre("statuts_des_creations", m);
        etatDesPec("pec_ecrites");
    }

    @Test
    void enregistrer_la_fiche_ne_ramene_jamais_une_pec_validee_a_cree_et_ne_modifie_que_la_demande() throws Exception {
        UUID forfait = forfait(CENTRE_A, "HD3", "9500.00");
        UUID autre = forfait(CENTRE_A, "HD4", "11000.00");
        UUID pec = idPec("PEC_FICHE");
        Resp cree = creerLaFiche("PATIENT", ficheAvecDemande("Dupont", "PC3-001", pec, jour(-5), jour(25), forfait), adminA());
        UUID patient = UUID.fromString(corps(cree).path("id").asString());
        UUID accorde = forfait(CENTRE_A, "HD5", "12000.00");
        ecriture("validation_par_l_api", valider(pec, CENTRE_A, jour(0), jour(20), accorde, adminA()));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("même demande, même identifiant", refus(modifierLaFiche(patient, ficheAvecDemande("Dupont", "PC3-001", pec, jour(-5), jour(25), forfait), adminA())));
        etatDesPec("apres_la_meme_demande");
        m.put("même demande sans identifiant", refus(modifierLaFiche(patient, ficheAvecDemande("Dupont", "PC3-001", null, jour(-5), jour(25), forfait), adminA())));
        etatDesPec("apres_la_meme_demande_sans_identifiant");
        m.put("dates et forfait modifiés, même identifiant", refus(modifierLaFiche(patient, ficheAvecDemande("Dupont", "PC3-001", pec, jour(-3), jour(30), autre), adminA())));
        etatDesPec("apres_la_demande_revisee");
        m.put("fiche sans demande", refus(modifierLaFiche(patient, ficheMap("Dupont", "PC3-001"), adminA())));
        etatDesPec("apres_une_fiche_sans_demande");
        arbre("statuts_des_modifications", m);
        lecture("pec_du_patient", pecsDuPatient(patient, CENTRE_A, adminA()));
    }

    @Test
    void une_demande_nouvelle_ou_sans_identifiant_correspondant_ajoute_une_pec_et_les_anciennes_restent() throws Exception {
        UUID forfait = forfait(CENTRE_A, "HD3", "9500.00");
        UUID pec = idPec("PEC_FICHE");
        Resp cree = creerLaFiche("PATIENT", ficheAvecDemande("Dupont", "PC3-001", pec, jour(-5), jour(25), forfait), adminA());
        UUID patient = UUID.fromString(corps(cree).path("id").asString());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("autres dates sans identifiant", refus(modifierLaFiche(patient, ficheAvecDemande("Dupont", "PC3-001", null, jour(30), jour(60), forfait), adminA())));
        UUID neuve = idPec("PEC_NEUVE");
        m.put("autres dates avec un identifiant nouveau", refus(modifierLaFiche(patient, ficheAvecDemande("Dupont", "PC3-001", neuve, jour(70), jour(90), null), adminA())));
        m.put("identifiant ancien et dates modifiées", refus(modifierLaFiche(patient, ficheAvecDemande("Dupont", "PC3-001", neuve, jour(70), jour(95), null), adminA())));
        arbre("statuts", m);
        etatDesPec("pec_apres_les_ajouts");
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("nombre de PEC", nombreDePec());
        arbre("nombre", n);
    }

    @Test
    void un_identifiant_de_pec_deja_pris_par_un_autre_patient_fait_echouer_la_fiche_en_entier() throws Exception {
        UUID pec = idPec("PEC_PRISE");
        creerLaFiche("PREMIER", ficheAvecDemande("Premier", "PC3-001", pec, jour(-5), jour(25), null), adminA());
        Resp second = doPost(PATIENTS, corps(ficheAvecDemande("Second", "PC3-002", pec, jour(-5), jour(25), null)), adminA());
        arbre("refus", Map.of("création de la fiche du second patient avec le même identifiant", refus(second)));
        sql("patients_ecrits", "SELECT nom FROM patients WHERE center_id = '" + CENTRE_A + "' ORDER BY nom");
        etatDesPec("pec_ecrites");
    }
}
