package com.hemodialyse.backend.characterization.dossiermedical.dm5;

import com.hemodialyse.backend.contexts.planification.application.ContraintesDesPatients;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * DM5, lot 3 (abords vasculaires) : l'effet des abords sur la Planification. Le lecteur est {@code ContraintesDesPatientsJdbc}
 * (ACL de la Planification, SQL en lecture seule sur {@code abords_vasculaires}, RG-PLN-097) : un patient exige la compétence
 * {@code CATHETER} si un abord ACTIF de type {@code KT_TUNNELISE} ou {@code KT_AIGU} n'est pas terminé au jour donné. Le bean est
 * appelé tel quel (aucune modification, aucun mock) avec un jour fixe, puis les écritures HTTP de DM5 sont rejouées pour voir leur effet.
 * <p>
 * Limite : l'usage de la compétence par l'optimisation (affectation des infirmiers) est verrouillé par les filets de Planification
 * (P7), pas ici.
 */
class LesAbordsVasculairesEtLaPlanificationCharacterizationIntegrationTest extends Dm5CharacterizationSupport {

    @Autowired
    private ContraintesDesPatients contraintes;

    @Override
    protected String dossier() {
        return "abords/planification";
    }

    /** Compétences exigées par patient du centre A au jour donné, par nom d'alias de patient. */
    private Map<String, String> competences(LocalDate jour) {
        Map<String, String> sortie = new TreeMap<>();
        contraintes.duCentre(new CentreId(CENTRE_A), jour).forEach((patient, c) -> {
            String alias = aliasNommes.getOrDefault(patient.valeur().toString(), patient.valeur().toString());
            sortie.put(alias, c.competences().stream().map(Enum::name).sorted().toList() + " transporteurs=" + c.transporteurs().size());
        });
        return sortie;
    }

    @Test
    void un_patient_exige_la_competence_catheter_si_son_abord_actif_est_un_catheter_non_termine_au_jour_donne() throws Exception {
        LocalDate jour = fixe(100);
        Object[][] cas = {
                // alias, type, creation, fin, actif
                {"KT_AIGU_ACTIF", "KT_AIGU", fixe(10), null, true},
                {"KT_TUNNELISE_ACTIF", "KT_TUNNELISE", fixe(10), null, true},
                {"KT_FIN_APRES_LE_JOUR", "KT_TUNNELISE", fixe(10), fixe(101), true},
                {"KT_FIN_LE_JOUR_MEME", "KT_TUNNELISE", fixe(10), fixe(100), true},
                {"KT_FIN_LA_VEILLE", "KT_TUNNELISE", fixe(10), fixe(99), true},
                {"KT_INACTIF", "KT_AIGU", fixe(10), null, false},
                {"KT_SANS_DATE_DE_CREATION", "KT_AIGU", null, null, true},
                {"KT_CREE_APRES_LE_JOUR", "KT_AIGU", fixe(200), null, true},
                {"KT_EN_MINUSCULES", "kt_aigu", fixe(10), null, true},
                {"FAV_ACTIVE", "FAV", fixe(10), null, true},
                {"PTFE_ACTIF", "PTFE", fixe(10), null, true},
        };
        Map<String, Object> sortie = new LinkedHashMap<>();
        for (Object[] c : cas) {
            UUID patient = patientDm5(CENTRE_A, (String) c[0]);
            abordEnBase(CENTRE_A, patient, "ABORD_" + c[0], (String) c[1], null, null, (LocalDate) c[2], (LocalDate) c[3], (Boolean) c[4]);
        }
        UUID fav = patientDm5(CENTRE_A, "FAV_ACTIVE_ET_KT_AIGU_ACTIF");
        abordEnBase(CENTRE_A, fav, "ABORD_FAV", "FAV", null, null, fixe(10), null, true);
        abordEnBase(CENTRE_A, fav, "ABORD_KT", "KT_AIGU", null, null, fixe(20), null, true);
        UUID termine = patientDm5(CENTRE_A, "KT_INACTIF_ET_FAV_ACTIVE");
        abordEnBase(CENTRE_A, termine, "ABORD_TERMINE", "KT_AIGU", null, null, fixe(10), fixe(50), false);
        abordEnBase(CENTRE_A, termine, "ABORD_FAV_DU_PATIENT_TERMINE", "FAV", null, null, fixe(60), null, true);
        UUID autreCentre = patientDm5(CENTRE_B, "KT_DU_CENTRE_B");
        abordEnBase(CENTRE_B, autreCentre, "ABORD_B", "KT_AIGU", null, null, fixe(10), null, true);
        UUID orphelin = idStable("patient", "sans_fiche");
        aliasNommes.put(orphelin.toString(), "<SANS_FICHE>");
        abordEnBase(CENTRE_A, orphelin, "ABORD_ORPHELIN", "KT_AIGU", null, null, fixe(10), null, true);
        sortie.put("1 au jour fixe(100)", competences(jour));
        sortie.put("2 au jour fixe(98) : la fin du 99 n'est pas encore passee", competences(fixe(98)));
        sortie.put("3 au jour fixe(101)", competences(fixe(101)));
        arbre("lecteur", sortie);
    }

    @Test
    void les_ecritures_de_dm5_changent_la_competence_exigee_au_prochain_calcul() throws Exception {
        LocalDate jour = fixe(100);
        UUID patient = patientDm5(CENTRE_A, "PATIENT");
        UUID inconnu = idStable("patient", "inconnu");
        aliasNommes.put(inconnu.toString(), "<INCONNU>");
        Map<String, Object> suite = new LinkedHashMap<>();
        suite.put("01 aucun abord", competences(jour));
        Resp c = doPost(abordsApi(patient), corps("centerId", CENTRE_A, "typeAbord", "KT_AIGU", "dateCreation", fixe(10)), medecinA());
        UUID abord = UUID.fromString(idDe(c));
        suite.put("02 apres POST d'un KT_AIGU sans actif (actif par defaut)", competences(jour));
        doPut(abordApi(patient, abord), corps("centerId", CENTRE_A, "typeAbord", "KT_AIGU", "dateCreation", fixe(10), "actif", false), medecinA());
        suite.put("03 apres PUT actif=false", competences(jour));
        doPut(abordApi(patient, abord), corps("centerId", CENTRE_A, "typeAbord", "KT_AIGU", "dateCreation", fixe(10)), medecinA());
        suite.put("04 apres PUT sans actif (reactive)", competences(jour));
        doPut(abordApi(patient, abord), corps("centerId", CENTRE_A, "typeAbord", "KT_AIGU", "dateCreation", fixe(10), "dateFin", fixe(90)), medecinA());
        suite.put("05 apres PUT avec une fin anterieure au jour", competences(jour));
        doPut(abordApi(patient, abord), corps("centerId", CENTRE_A, "typeAbord", "FAV", "dateCreation", fixe(10)), medecinA());
        suite.put("06 apres PUT en FAV", competences(jour));
        doPut(abordApi(patient, abord), corps("centerId", CENTRE_A, "typeAbord", "KT_TUNNELISE", "dateCreation", fixe(10)), medecinA());
        suite.put("07 apres PUT en KT_TUNNELISE", competences(jour));
        doDelete(abordApi(patient, abord), medecinA());
        suite.put("08 apres DELETE", competences(jour));
        doPost(abordsApi(inconnu), corps("centerId", CENTRE_A, "typeAbord", "KT_AIGU", "dateCreation", fixe(10)), medecinA());
        suite.put("09 apres POST d'un KT_AIGU pour un patient sans fiche", competences(jour));
        arbre("ecritures", suite);
    }
}
