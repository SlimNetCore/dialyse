package com.hemodialyse.backend.characterization.dossiermedical.dm5;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DM5, lot 3 (abords vasculaires), enregistrement et modification : {@code POST} et {@code PUT} sur
 * {@code /api/v1/patients/{id}/abords-vasculaires}. Le {@code PUT} est un « enregistrer » (upsert) : il crée l'abord si l'identifiant est
 * inconnu et remplace tous les champs.
 */
class LEnregistrementEtLaModificationDesAbordsVasculairesCharacterizationIntegrationTest extends Dm5CharacterizationSupport {

    @Override
    protected String dossier() {
        return "abords/enregistrement";
    }

    private UUID patientA;
    private UUID patientB;

    private void deuxCentres() {
        patientA = patientDm5(CENTRE_A, "PAT_A");
        patientB = patientDm5(CENTRE_B, "PAT_B");
    }

    private String ab(String type, String cote, String localisation, Object creation, Object fin, Boolean actif) {
        return corps("centerId", CENTRE_A, "typeAbord", type, "cote", cote, "localisation", localisation, "dateCreation", creation, "dateFin", fin,
                "actif", actif);
    }

    private String creer(String corps) throws Exception {
        return refus(doPost(abordsApi(patientA), corps, medecinA()));
    }

    @Test
    void un_abord_enregistre_garde_tous_ses_champs_et_est_actif_par_defaut() throws Exception {
        deuxCentres();
        Resp complet = doPost(abordsApi(patientA), corps("centerId", CENTRE_A, "typeAbord", "FAV", "cote", "GAUCHE", "localisation", "Avant-bras fictif",
                "dateCreation", fixe(-200), "dateFin", fixe(-100), "actif", false, "complications", "Complication fictive"), medecinA());
        aliasNommes.put(idDe(complet), "<ABORD_COMPLET>");
        Resp minimal = doPost(abordsApi(patientA), corps("centerId", CENTRE_A, "typeAbord", "KT_AIGU"), medecinA());
        aliasNommes.put(idDe(minimal), "<ABORD_MINIMAL>");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reponse complete", reponse(complet));
        m.put("reponse minimale (actif par defaut)", reponse(minimal));
        m.put("etat", etatDesAbords());
        arbre("abord", m);
    }

    @Test
    void le_type_la_cote_et_la_localisation_sont_du_texte_libre_borne_par_les_colonnes() throws Exception {
        deuxCentres();
        LocalDate d = fixe(-30);
        Map<String, String> cas = new LinkedHashMap<>();
        for (String type : new String[]{"FAV", "PTFE", "KT_TUNNELISE", "KT_AIGU", "kt_aigu", "N'IMPORTE QUOI", ""}) {
            cas.put("type '" + type + "'", creer(ab(type, null, null, d, null, null)));
        }
        cas.put("type absent", creer(ab(null, null, null, d, null, null)));
        cas.put("type de 20 caracteres", creer(ab("t".repeat(20), null, null, d, null, null)));
        cas.put("type de 21 caracteres", creer(ab("u".repeat(21), null, null, d, null, null)));
        cas.put("cote de 10 caracteres", creer(ab("COTE10", "c".repeat(10), null, d, null, null)));
        cas.put("cote de 11 caracteres", creer(ab("COTE11", "d".repeat(11), null, d, null, null)));
        cas.put("localisation de 255 caracteres", creer(ab("LOC255", null, "l".repeat(255), d, null, null)));
        cas.put("localisation de 256 caracteres", creer(ab("LOC256", null, "m".repeat(256), d, null, null)));
        cas.put("complications de 500 caracteres", creer(corps("centerId", CENTRE_A, "typeAbord", "COM500", "complications", "k".repeat(500))));
        cas.put("complications de 501 caracteres", creer(corps("centerId", CENTRE_A, "typeAbord", "COM501", "complications", "k".repeat(501))));
        cas.put("actif illisible", creer("{\"centerId\":\"" + CENTRE_A + "\",\"typeAbord\":\"ACTIF_ILLISIBLE\",\"actif\":\"peut-etre\"}"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("lignes", lignes("SELECT type_abord, LENGTH(COALESCE(cote, '')) AS cote, LENGTH(COALESCE(localisation, '')) AS localisation, "
                + "LENGTH(COALESCE(complications, '')) AS complications, actif FROM abords_vasculaires WHERE center_id = ? ORDER BY type_abord, actif",
                CENTRE_A));
        arbre("champs_libres", m);
    }

    @Test
    void les_dates_de_creation_et_de_fin_ne_sont_comparees_que_entre_elles() throws Exception {
        deuxCentres();
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("creation sans fin", creer(ab("A1", null, null, fixe(-10), null, null)));
        cas.put("ni creation ni fin", creer(ab("A2", null, null, null, null, null)));
        cas.put("fin avant la creation", creer(ab("A3", null, null, fixe(-10), fixe(-20), null)));
        cas.put("fin le meme jour que la creation", creer(ab("A4", null, null, fixe(-10), fixe(-10), null)));
        cas.put("fin sans creation", creer(ab("A5", null, null, null, fixe(-20), null)));
        cas.put("creation dans le futur (pas de limite)", creer(ab("A6", null, null, relatif(30), null, null)));
        cas.put("fin dans le futur", creer(ab("A7", null, null, fixe(-10), relatif(60), null)));
        cas.put("creation en 1900", creer(ab("A8", null, null, alias(LocalDate.of(1900, 1, 1), "<1900>"), null, null)));
        cas.put("creation illisible", creer(ab("A9", null, null, "demain", null, null)));
        cas.put("fin posee mais abord actif (aucune coherence imposee)", creer(ab("A10", null, null, fixe(-10), fixe(-5), true)));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("etat", etatDesAbords());
        arbre("dates", m);
    }

    @Test
    void le_put_remplace_tous_les_champs_et_reactive_l_abord_quand_actif_est_absent() throws Exception {
        deuxCentres();
        UUID a = abordEnBase(CENTRE_A, patientA, "ABORD_1", "FAV", "GAUCHE", "Avant-bras fictif", fixe(-200), fixe(-100), false);
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("1 PUT sans actif : l'abord redevient actif et les champs absents sont effaces", resumeEcriture(doPut(abordApi(patientA, a),
                ab("PTFE", null, null, fixe(-90), null, null), medecinA())));
        cas.put("2 PUT avec actif=false et des complications", resumeEcriture(doPut(abordApi(patientA, a),
                corps("centerId", CENTRE_A, "typeAbord", "PTFE", "actif", false, "complications", "Complication fictive"), medecinA())));
        cas.put("3 PUT sans type", resumeEcriture(doPut(abordApi(patientA, a), ab(null, "DROIT", null, null, null, null), medecinA())));
        cas.put("4 PUT avec fin avant creation", resumeEcriture(doPut(abordApi(patientA, a), ab("PTFE", null, null, fixe(-10), fixe(-20), null),
                medecinA())));
        cas.put("5 PUT corps absent", resumeEcriture(doPut(abordApi(patientA, a), "", medecinA())));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("etat", etatDesAbords());
        arbre("remplacement", m);
    }

    @Test
    void le_put_d_un_identifiant_inconnu_cree_l_abord_et_le_put_d_un_autre_patient_ou_centre_le_deplace() throws Exception {
        deuxCentres();
        UUID inconnu = idStable("abord", "inconnu");
        aliasNommes.put(inconnu.toString(), "<ABORD_INCONNU>");
        UUID autrePatient = abordEnBase(CENTRE_A, patientB, "ABORD_AUTRE_PATIENT", "FAV", "DROIT", null, fixe(-50), null, true);
        UUID autreCentre = abordEnBase(CENTRE_B, patientB, "ABORD_AUTRE_CENTRE", "KT_AIGU", "GAUCHE", null, fixe(-40), null, true);
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("1 PUT d'un identifiant inconnu : l'abord est cree avec cet identifiant", resumeEcriture(doPut(abordApi(patientA, inconnu),
                ab("PTFE", null, null, fixe(-10), null, null), medecinA())));
        cas.put("2 PUT de l'abord d'un AUTRE PATIENT du centre : la ligne change de patient", resumeEcriture(doPut(abordApi(patientA, autrePatient),
                ab("PTFE", null, null, fixe(-9), null, null), medecinA())));
        cas.put("3 PUT de l'abord d'un AUTRE CENTRE : la ligne change de centre", resumeEcriture(doPut(abordApi(patientA, autreCentre),
                ab("PTFE", null, null, fixe(-8), null, null), medecinA())));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("etat", etatDesAbords());
        arbre("upsert_et_deplacement", m);
        assertThat(nombreDe("abords_vasculaires", CENTRE_B)).isZero();
    }

    @Test
    void un_corps_illisible_ou_un_patient_inconnu() throws Exception {
        deuxCentres();
        UUID inconnu = idStable("patient", "inconnu");
        aliasNommes.put(inconnu.toString(), "<PAT_INCONNU>");
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("corps absent", refus(doPost(abordsApi(patientA), "", medecinA())));
        cas.put("corps vide {}", creer("{}"));
        cas.put("corps tableau []", creer("[]"));
        cas.put("corps illisible", creer("{pas du json"));
        cas.put("centre illisible", creer("{\"centerId\":\"pas-un-uuid\",\"typeAbord\":\"X\"}"));
        cas.put("identifiant de patient illisible", refus(doPost(PATIENTS_API + "/abc/abords-vasculaires", ab("X1", null, null, null, null, null),
                medecinA())));
        cas.put("patient inconnu", refus(doPost(abordsApi(inconnu), ab("X2", null, null, null, null, null), medecinA())));
        cas.put("patient d'un autre centre (session A)", refus(doPost(abordsApi(patientB), ab("X3", null, null, null, null, null), medecinA())));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("etat", etatDesAbords());
        arbre("corps_et_patient", m);
        assertThat(nombreDe("abords_vasculaires", CENTRE_B)).isZero();
    }

    /** Réponse d'écriture : statut et identifiant (nommé) si 200, sinon le refus. */
    private String resumeEcriture(Resp r) {
        return resume(r, "id", "patientId");
    }
}
