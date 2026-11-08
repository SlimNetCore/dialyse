package com.hemodialyse.backend.characterization.seance.se4;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SE4, lot 2 : le cycle de régularisation d'une séance oubliée, de bout en bout (rappel, verrou des jours passés de SE2, déverrouillage
 * par l'administrateur, validation par l'infirmier, rappel suivant), le déverrouillage d'une séance dont le patient a disparu, et les
 * lectures qui exposent le déverrouillage. Les séances sont à des dates FIXES nommées (janvier 2026, toujours passées au jour du
 * centre) ; le jour de la demande du rappel est lui aussi fixe : aucune dépendance à l'heure.
 */
class LeCycleDeRegularisationCharacterizationIntegrationTest extends Se4CharacterizationSupport {

    @Override
    protected String dossier() {
        return "cycle";
    }

    @Test
    void le_rappel_decroit_a_mesure_que_l_administrateur_deverrouille_ou_valide_les_seances_oubliees() throws Exception {
        LocalDate aujourdhui = jourDeLaDemande(30);
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID s29 = seanceEnBase(CENTRE_A, patient, "SEANCE_29", fixe(29), "CREE");
        UUID s28 = seanceEnBase(CENTRE_A, patient, "SEANCE_28", fixe(28), "CREE");
        UUID s27 = seanceEnBase(CENTRE_A, patient, "SEANCE_27", fixe(27), "CREE");
        Map<String, Object> m = new LinkedHashMap<>();
        rappeler(CENTRE_A, aujourdhui);
        m.put("01 rappel initial (3 oubliees)", rappelLu());
        viderLesRappels();
        m.put("02 l'infirmier ne peut pas valider la 28e avant le deverrouillage", refus(valider(s28, infirmierA())));
        m.put("03 deverrouillage de la 28e par l'administrateur", refus(deverrouiller(s28, adminA())));
        m.put("04 diffusion du deverrouillage (infirmiers)", diffusionsLues());
        viderLesRappels();
        rappeler(CENTRE_A, aujourdhui);
        m.put("05 rappel apres deverrouillage (2 oubliees : la 28e n'est plus rappelee)", rappelLu());
        viderLesRappels();
        m.put("06 l'infirmier valide la 28e", refus(valider(s28, infirmierA())));
        viderLesRappels();
        rappeler(CENTRE_A, aujourdhui);
        m.put("07 rappel apres validation de la 28e (inchange : 2)", rappelLu());
        viderLesRappels();
        m.put("08 l'administrateur valide la 27e sans deverrouillage prealable", refus(valider(s27, adminA())));
        viderLesRappels();
        rappeler(CENTRE_A, aujourdhui);
        m.put("09 rappel (1 oubliee : la 29e)", rappelLu());
        viderLesRappels();
        m.put("10 deverrouillage de la 29e", refus(deverrouiller(s29, adminA())));
        viderLesRappels();
        rappeler(CENTRE_A, aujourdhui);
        m.put("11 rappel (plus aucune seance a regulariser : aucune diffusion, aucun journal)", rappelLu());
        m.put("12 etat final", etatSe2());
        m.put("13 lignes de derogations_regularisation (-1 : table absente du schema H2)", lignesDeDerogation());
        arbre("cycle_complet", m);
    }

    @Test
    void le_deverrouillage_d_une_seance_dont_le_patient_a_disparu_est_refuse_en_404_sans_ecriture() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID sansPatient = seanceEnBase(CENTRE_A, patient, "SEANCE_SANS_PATIENT", fixe(20), "CREE");
        UUID autre = patientCouvert(CENTRE_A, "PAT_B");
        UUID avecPatient = seanceEnBase(CENTRE_A, autre, "SEANCE_AVEC_PATIENT", fixe(20), "CREE");
        jdbc.update("DELETE FROM patients WHERE id = ?", patient);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 patient disparu : administrateur", refus(deverrouiller(sansPatient, adminA())));
        m.put("02 aucune diffusion", diffusionsLues());
        m.put("03 etat (ni deverrouillage ni validation)", etatSe2());
        m.put("04 le rappel compte quand meme la seance sans patient", rappelApres(jourDeLaDemande(25)));
        m.put("05 la seance au patient present se deverrouille", refus(deverrouiller(avecPatient, adminA())));
        arbre("patient_disparu", m);
    }

    private Map<String, Object> rappelApres(LocalDate jour) throws Exception {
        viderLesRappels();
        rappeler(CENTRE_A, jour);
        return rappelLu();
    }

    @Test
    void le_deverrouillage_est_visible_dans_le_detail_et_dans_la_liste_filtree_et_ne_l_est_que_pour_le_centre_de_la_seance() throws Exception {
        UUID patientA = patientCouvert(CENTRE_A, "PAT_A");
        UUID patientB = patientCouvert(CENTRE_B, "PAT_B");
        UUID oubliee = seanceEnBase(CENTRE_A, patientA, "OUBLIEE", fixe(20), "CREE");
        seanceEnBase(CENTRE_A, patientA, "AUTRE_OUBLIEE", fixe(21), "CREE");
        seanceEnBase(CENTRE_B, patientB, "AUTRE_CENTRE", fixe(20), "CREE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 detail avant", detailLu(oubliee));
        m.put("02 deverrouillage", refus(deverrouiller(oubliee, adminA())));
        m.put("03 detail apres", detailLu(oubliee));
        m.put("04 liste deverrouillee=true (administrateur)", liste("&deverrouillee=true", adminA()));
        m.put("05 liste deverrouillee=false (administrateur)", liste("&deverrouillee=false", adminA()));
        m.put("06 liste deverrouillee=true (infirmier)", liste("&deverrouillee=true", infirmierA()));
        m.put("07 liste sans filtre (infirmier)", liste("", infirmierA()));
        m.put("08 liste du centre B (aucune deverrouillee)", refus(doGet(SEANCES_API + "?centerId=" + CENTRE_B + "&deverrouillee=true", adminB())));
        arbre("lectures", m);
    }

    private String detailLu(UUID seance) throws Exception {
        Resp r = doGet(detailApi(seance, CENTRE_A), adminA());
        if (r.status() != 200) return refus(r);
        JsonNode s = corps(r).path("seance");
        return "200 status=" + s.path("status").asString() + " ; regularisationDeverrouilleeAt "
                + (!s.has("regularisationDeverrouilleeAt") ? "absent du detail" : s.path("regularisationDeverrouilleeAt").isNull()
                ? "= null" : "renseigne");
    }

    private String liste(String parametres, org.springframework.test.web.servlet.request.RequestPostProcessor who) throws Exception {
        Resp r = doGet(SEANCES_API + "?centerId=" + CENTRE_A + parametres, who);
        if (r.status() != 200) return refus(r);
        JsonNode c = corps(r);
        List<String> lignes = new ArrayList<>();
        for (JsonNode i : c.path("items")) {
            lignes.add(i.path("dateSeance").asString() + " " + i.path("status").asString() + " deverrouillee="
                    + (i.path("regularisationDeverrouilleeAt").isNull() ? "non" : "oui"));
        }
        return "200 total=" + c.path("total").asString() + " " + lignes;
    }

    @Test
    void une_seance_deverrouillee_puis_validee_reste_marquee_deverrouillee_et_ne_peut_plus_etre_deverrouillee_ni_rappelee() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", fixe(20), "CREE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 deverrouillage", refus(deverrouiller(seance, adminA())));
        m.put("02 validation par l'infirmier", refus(valider(seance, infirmierA())));
        viderLesRappels();
        m.put("03 nouveau deverrouillage d'une seance validee", refus(deverrouiller(seance, adminA())));
        m.put("04 diffusions du refus", diffusionsLues());
        m.put("05 etat (validee, deverrouillage conserve)", etatSe2());
        m.put("06 la seance validee n'est plus rappelee", rappelApres(jourDeLaDemande(25)));
        m.put("07 signature du medecin (la seance reste signable)", refus(signer(seance, medecinA())));
        m.put("08 deverrouillage d'une seance signee", refus(deverrouiller(seance, adminA())));
        m.put("09 etat final", etatSe2());
        arbre("deverrouillee_puis_validee", m);
    }
}
