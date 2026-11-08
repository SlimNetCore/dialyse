package com.hemodialyse.backend.characterization.seance.se7;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SE7a : le volet médical d'une séance ({@code PUT /api/v1/seances/{id}/volet-medical}, servi par {@code VoletMedicalRestController} et
 * {@code VoletMedicalDomainService}, réservé à ADMIN et MEDECIN) : création puis remplacement, matrice des statuts, droits, séances d'un
 * autre centre, corps. Le garde de centre du corps et le refus d'une séance facturée sont déjà verrouillés par DM1 et par Facturation. Les
 * observations médicales sont CONFIDENTIELLES : les cas ci-dessous n'utilisent que du texte fictif. Décrit le comportement ACTUEL.
 */
class LeVoletMedicalDUneSeanceCharacterizationIntegrationTest extends Se7CharacterizationSupport {

    @Override
    protected String dossier() {
        return "volet";
    }

    private String corpsVolet(UUID centre, String champs) {
        return "{\"centerId\":" + (centre == null ? "null" : "\"" + centre + "\"") + (champs == null || champs.isEmpty() ? "" : "," + champs) + "}";
    }

    private static final String COMPLET = "\"prescription\":\"Prescription fictive\",\"toleranceSeance\":\"Bonne\",\"examenClinique\":\"RAS\","
            + "\"resultatsBiologiques\":\"Normaux\",\"ajustementsTherapeutiques\":\"Aucun\",\"conclusionMedicale\":\"Stable\"";

    @Test
    void le_volet_medical_est_cree_puis_remplace_en_entier_et_un_champ_absent_efface_la_valeur() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID seance = seanceValidee(CENTRE_A, patient, "SEANCE", jour(0), "VALIDEE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 avant", voletsMedicaux(seance));
        m.put("02 creation par le medecin", reponse(voletMedical(seance, corpsVolet(CENTRE_A, COMPLET), medecinA())));
        m.put("03 apres la creation", voletsMedicaux(seance));
        String identifiant = jdbc.queryForObject("SELECT CAST(id AS VARCHAR(36)) FROM volet_medical WHERE seance_id = ?", String.class, seance);
        m.put("04 remplacement partiel (une seule observation)", reponse(voletMedical(seance, corpsVolet(CENTRE_A, "\"conclusionMedicale\":\"Revue\""), adminA())));
        m.put("05 apres le remplacement : les autres champs sont effaces", voletsMedicaux(seance));
        m.put("06 le volet est le meme enregistrement", identifiant.equals(jdbc.queryForObject(
                "SELECT CAST(id AS VARCHAR(36)) FROM volet_medical WHERE seance_id = ?", String.class, seance)));
        m.put("07 nombre de volets pour la seance", compter("SELECT COUNT(*) FROM volet_medical WHERE seance_id = ?", seance));
        m.put("08 corps sans aucun champ", reponse(voletMedical(seance, corpsVolet(CENTRE_A, null), medecinA())));
        m.put("09 apres le corps vide : tout est efface", voletsMedicaux(seance));
        m.put("10 diffusions", diffusionsLues());
        arbre("creation_et_remplacement", m);
    }

    @Test
    void le_volet_medical_n_est_refuse_que_pour_les_statuts_cree_absent_et_facture_la_matrice_des_statuts() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String statut : List.of("CREE", "ABSENT", "VALIDEE", "SIGNEE", "FACTUREE", "EN_COURS")) {
            nettoyerSe1();
            UUID patient = patientCouvert(CENTRE_A, "PAT_A");
            UUID seance = seanceValidee(CENTRE_A, patient, "SEANCE", jour(0), "CREE");
            jdbc.update("UPDATE seances SET statut = ? WHERE id = ?", statut, seance);
            m.put(statut, refus(voletMedical(seance, corpsVolet(CENTRE_A, COMPLET), medecinA())) + " ; volets="
                    + compter("SELECT COUNT(*) FROM volet_medical WHERE seance_id = ?", seance));
        }
        arbre("matrice_des_statuts", m);
    }

    @Test
    void les_droits_du_volet_medical_par_profil_et_les_corps_inattendus() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID seance = seanceValidee(CENTRE_A, patient, "SEANCE", jour(0), "VALIDEE");
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, String> profils = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profilsSe1().entrySet()) {
            profils.put(p.getKey(), refus(voletMedical(seance, corpsVolet(CENTRE_A, COMPLET), p.getValue())));
        }
        profils.put("anonyme", refus(doSansSession("PUT", SEANCES_API + "/" + seance + "/volet-medical", corpsVolet(CENTRE_A, COMPLET))));
        m.put("01 profils", profils);
        m.put("02 volets en base apres la matrice", compter("SELECT COUNT(*) FROM volet_medical WHERE seance_id = ?", seance));
        Map<String, String> corps = new LinkedHashMap<>();
        corps.put("centre absent", refus(voletMedical(seance, "{\"prescription\":\"x\"}", medecinA())));
        corps.put("centre nul", refus(voletMedical(seance, corpsVolet(null, "\"prescription\":\"x\""), medecinA())));
        corps.put("corps absent", refus(voletMedical(seance, "", medecinA())));
        corps.put("JSON illisible", refus(voletMedical(seance, "{", medecinA())));
        corps.put("champ inconnu", refus(voletMedical(seance, corpsVolet(CENTRE_A, "\"diagnostic\":\"x\""), medecinA())));
        corps.put("champ de 20000 caracteres", refus(voletMedical(seance, corpsVolet(CENTRE_A, "\"examenClinique\":\"" + "x".repeat(20000) + "\""), medecinA())));
        corps.put("identifiant de seance illisible", refus(doPut(SEANCES_API + "/xx/volet-medical", corpsVolet(CENTRE_A, COMPLET), medecinA())));
        corps.put("seance inconnue", refus(voletMedical(UUID.randomUUID(), corpsVolet(CENTRE_A, COMPLET), medecinA())));
        m.put("03 corps et identifiants", corps);
        arbre("droits_et_corps", m);
    }

    @Test
    void le_volet_medical_d_une_seance_d_un_autre_centre_est_introuvable_et_n_ecrit_rien() throws Exception {
        UUID patientB = patientCouvert(CENTRE_B, "PAT_B");
        UUID seanceB = seanceValidee(CENTRE_B, patientB, "SEANCE_B", jour(0), "VALIDEE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 seance du centre B avec le centre A de la session", refus(voletMedical(seanceB, corpsVolet(CENTRE_A, COMPLET), medecinA())));
        m.put("02 seance du centre B avec le centre B demande par la session A", refus(voletMedical(seanceB, corpsVolet(CENTRE_B, COMPLET), medecinA())));
        m.put("03 volets du centre B", compter("SELECT COUNT(*) FROM volet_medical WHERE seance_id = ?", seanceB));
        arbre("cloisonnement", m);
    }
}
