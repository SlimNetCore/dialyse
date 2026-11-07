package com.hemodialyse.backend.characterization.seance.se2;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SE2, lot 2 : signature d'une séance validée par le médecin ou l'administrateur ({@code POST /api/v1/seances/{id}/signer-medecin}) :
 * transition {@code VALIDEE -> SIGNEE}, matrice des statuts, double signature, compte enregistré, absence de règle de date, droits par
 * profil, cloisonnement par centre, corps invalides, diffusions (aucune), audit, écritures simultanées, parcours complet.
 */
class LaSignatureMedicaleCharacterizationIntegrationTest extends Se2CharacterizationSupport {

    @Override
    protected String dossier() {
        return "signature";
    }

    @Test
    void le_medecin_signe_une_seance_validee_sans_diffusion_et_une_seconde_signature_est_refusee() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        LocalDate jour = jour(0);
        UUID seance = seanceValidee(CENTRE_A, patient, "SEANCE", jour, "VALIDEE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 signature par le MEDECIN", reponse(signer(seance, medecinA())));
        m.put("02 etat (SIGNEE, signee par le compte du corps ; place non memorisee)", etatSe2());
        m.put("03 diffusions (aucune)", diffusionsLues());
        m.put("04 seconde signature (le statut n'est plus VALIDEE)", reponse(signer(seance, medecinA())));
        m.put("05 etat inchange", etatSe2());
        m.put("06 validation d'une seance signee (retour anticipe, rediffusion)", reponse(valider(seance, infirmierA())));
        m.put("07 etat final", etatSe2());
        arbre("nominal", m);
    }

    @Test
    void la_matrice_des_statuts_seule_une_seance_validee_est_signable_et_le_message_depend_de_l_ordre_des_gardes() throws Exception {
        LocalDate jour = jour(0);
        Map<String, Object> m = new LinkedHashMap<>();
        for (String statut : List.of("CREE", "VALIDEE", "SIGNEE", "FACTUREE", "ABSENT", "EN_COURS")) {
            Map<String, Object> cas = new LinkedHashMap<>();
            Map<String, RequestPostProcessor> acteurs = new LinkedHashMap<>();
            acteurs.put("medecin", medecinA());
            acteurs.put("administrateur", adminA());
            for (Map.Entry<String, RequestPostProcessor> qui : acteurs.entrySet()) {
                nettoyerSe1();
                UUID patient = patientCouvert(CENTRE_A, "PAT_A");
                UUID seance = seanceValidee(CENTRE_A, patient, "SEANCE", jour, statut);
                Resp r = signer(seance, qui.getValue());
                cas.put(qui.getKey(), issue(r, seance));
            }
            m.put(statut, cas);
        }
        nettoyerSe1();
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID incoherente = seanceValidee(CENTRE_A, patient, "SEANCE_INCOHERENTE", jour, "VALIDEE");
        jdbc.update("UPDATE seances SET signed_medecin_at = ?, signed_medecin_by = 'med-initial' WHERE id = ?", CREATION_FIXE, incoherente);
        m.put("VALIDEE portant deja une signature de medecin (ligne incoherente)", issue(signer(incoherente, medecinA()), incoherente));
        arbre("matrice_des_statuts", m);
    }

    @Test
    void le_compte_enregistre_comme_signataire_est_celui_du_corps_et_une_erreur_de_colonne_annule_la_signature() throws Exception {
        LocalDate jour = jour(0);
        Map<String, Object> m = new LinkedHashMap<>();
        String[] cas = {"corps sans userId", "userId vide", "userId d'un autre compte", "userId de 100 caracteres", "userId de 101 caracteres"};
        Object[] valeurs = {null, "", "autre-compte", "u".repeat(100), "u".repeat(101)};
        for (int i = 0; i < cas.length; i++) {
            nettoyerSe1();
            UUID patient = patientCouvert(CENTRE_A, "PAT_A");
            UUID seance = seanceValidee(CENTRE_A, patient, "SEANCE", jour, "VALIDEE");
            Resp r = doPost(signerApi(seance), corps("centerId", CENTRE_A, "userId", valeurs[i]), medecinA());
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("reponse", refus(r));
            c.put("signataire en base", lignes("SELECT statut, signed_medecin_by FROM seances WHERE id = ?", seance));
            m.put(String.format("%02d %s", i + 1, cas[i]), c);
        }
        arbre("compte_signataire", m);
    }

    @Test
    void la_signature_ne_depend_ni_du_jour_de_la_seance_ni_du_deverrouillage() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int decalage : new int[]{-400, -8, -1, 0, 1, 30}) {
            nettoyerSe1();
            UUID patient = patientCouvert(CENTRE_A, "PAT_A");
            UUID seance = seanceValidee(CENTRE_A, patient, "SEANCE", jour(decalage), "VALIDEE");
            Map<String, Object> cas = new LinkedHashMap<>();
            cas.put("medecin", issue(signer(seance, medecinA()), seance));
            m.put(String.format("%+04d jours", decalage), cas);
        }
        arbre("jours", m);
    }

    @Test
    void les_droits_par_profil_de_la_signature() throws Exception {
        LocalDate jour = jour(0);
        Map<String, Object> profils = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profilsSe1().entrySet()) {
            nettoyerSe1();
            UUID patient = patientCouvert(CENTRE_A, "PAT_A");
            UUID seance = seanceValidee(CENTRE_A, patient, "SEANCE", jour, "VALIDEE");
            profils.put(p.getKey(), statutEtCode(signer(seance, p.getValue())) + " ; " + lignes("SELECT statut FROM seances WHERE id = ?", seance));
        }
        nettoyerSe1();
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID seance = seanceValidee(CENTRE_A, patient, "SEANCE", jour, "VALIDEE");
        profils.put("anonyme", statutEtCode(appel("POST", signerApi(seance), corps("centerId", CENTRE_A, "userId", "med-01"), null))
                + " ; " + lignes("SELECT statut FROM seances WHERE id = ?", seance));
        arbre("profils", profils);
    }

    @Test
    void le_cloisonnement_par_centre_la_garde_de_la_seance_et_les_corps_invalides() throws Exception {
        LocalDate jour = jour(0);
        UUID patientA = patientCouvert(CENTRE_A, "PAT_A");
        UUID patientB = patientCouvert(CENTRE_B, "PAT_B");
        UUID seanceA = seanceValidee(CENTRE_A, patientA, "SEANCE_A", jour, "VALIDEE");
        UUID seanceB = seanceValidee(CENTRE_B, patientB, "SEANCE_B", jour, "VALIDEE");
        UUID inconnue = idStable("seance", "inconnue");
        aliasNommes.put(inconnue.toString(), "<SEANCE_INCONNUE>");
        String avant = empreinteDesSeances();
        RequestPostProcessor medecinB = as(CENTRE_B, "MEDECIN");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 session A vise le centre B", refus(doPost(signerApi(seanceB), corps("centerId", CENTRE_B, "userId", "u"), medecinA())));
        m.put("02 session B vise le centre A", refus(doPost(signerApi(seanceA), corps("centerId", CENTRE_A, "userId", "u"), medecinB)));
        m.put("03 session B et centre B, seance du centre A", refus(doPost(signerApi(seanceA), corps("centerId", CENTRE_B, "userId", "u"), medecinB)));
        m.put("04 session sans centre", refus(doPost(signerApi(seanceA), corps("centerId", CENTRE_A, "userId", "u"), as(null, "MEDECIN"))));
        m.put("05 seance inconnue", refus(doPost(signerApi(inconnue), corps("centerId", CENTRE_A, "userId", "u"), medecinA())));
        m.put("06 corps sans centerId", refus(doPost(signerApi(seanceA), corps("userId", "u"), medecinA())));
        m.put("07 corps illisible", refus(doPost(signerApi(seanceA), "{pas du json", medecinA())));
        m.put("08 corps absent", refus(doPost(signerApi(seanceA), "", medecinA())));
        m.put("09 corps null", refus(doPost(signerApi(seanceA), "null", medecinA())));
        m.put("10 identifiant de seance mal forme", refus(doPost(SEANCES_API + "/pas-un-uuid/signer-medecin", corps("centerId", CENTRE_A, "userId", "u"),
                medecinA())));
        m.put("11 aucune ecriture", empreinteDesSeances().equals(avant));
        m.put("12 SUPERADMIN + ADMIN de la session A vise le centre B (transverse)", refus(doPost(signerApi(seanceB),
                corps("centerId", CENTRE_B, "userId", "u"), as(CENTRE_A, "SUPERADMIN", "ADMIN"))));
        m.put("13 etat apres le superadmin", etatSe2());
        arbre("cloisonnement_et_corps", m);
    }

    @Test
    void les_signatures_et_leurs_refus_sont_journalisees_avec_la_route_et_le_statut() throws Exception {
        LocalDate jour = jour(0);
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID validee = seanceValidee(CENTRE_A, patient, "SEANCE_VALIDEE", jour, "VALIDEE");
        UUID creee = seanceEnBase(CENTRE_A, patient, "SEANCE_CREE", jour, "CREE");
        UUID inconnue = idStable("seance", "inconnue");
        aliasNommes.put(inconnue.toString(), "<SEANCE_INCONNUE>");
        monterLAudit();
        signer(validee, medecinA());
        signer(validee, medecinA());
        signer(creee, adminA());
        signer(inconnue, medecinA());
        signer(validee, infirmierA());
        doPost(signerApi(validee), corps("centerId", CENTRE_B, "userId", "u"), medecinA());
        doPost(signerApi(validee), "{pas du json", medecinA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("journal", journalDAudit());
        arbre("audit", m);
    }

    @Test
    void deux_signatures_simultanees_ne_sont_protegees_par_aucun_verrou() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID seance = seanceValidee(CENTRE_A, patient, "SEANCE", jour(0), "VALIDEE");
        List<Resp> reponses = enParallele(() -> signer(seance, medecinA()), () -> signer(seance, adminA()));
        long succes = reponses.stream().filter(r -> r.status() == 200).count();
        Map<String, Object> m = new LinkedHashMap<>();
        // Invariants seulement : sans verrou ni version, la seconde requete peut lire VALIDEE avant la premiere ecriture (200 et 200).
        m.put("01 au moins une signature reussit", succes >= 1);
        m.put("02 les autres reponses sont des refus 422 ou 500", reponses.stream().allMatch(r -> r.status() == 200 || r.status() == 422
                || r.status() == 500));
        m.put("03 une seule ligne, SIGNEE", lignes("SELECT statut FROM seances WHERE center_id = ?", CENTRE_A));
        arbre("ecritures_simultanees", m);
    }

    @Test
    void le_parcours_complet_creee_validee_signee_et_le_deverrouillage_d_une_seance_signee_est_refuse() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", jour(-2), "CREE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 la secretaire ne signe pas", refus(signer(seance, secretaireA())));
        m.put("02 le medecin ne signe pas une seance creee", refus(signer(seance, medecinA())));
        m.put("03 l'infirmier ne valide pas sans deverrouillage", refus(valider(seance, infirmierA())));
        m.put("04 deverrouillage par l'administrateur", refus(deverrouiller(seance, adminA())));
        m.put("05 validation par l'infirmier", refus(valider(seance, infirmierA())));
        m.put("06 l'infirmier ne signe pas", refus(signer(seance, infirmierA())));
        m.put("07 signature par le medecin", refus(signer(seance, medecinA())));
        m.put("08 deverrouillage d'une seance signee", refus(deverrouiller(seance, adminA())));
        m.put("09 validation d'une seance signee (idempotente)", refus(valider(seance, infirmierA())));
        m.put("10 etat final", etatSe2());
        arbre("parcours", m);
    }
}
