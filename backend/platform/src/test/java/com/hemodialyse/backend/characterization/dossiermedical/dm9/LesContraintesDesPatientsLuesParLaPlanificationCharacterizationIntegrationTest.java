package com.hemodialyse.backend.characterization.dossiermedical.dm9;

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
 * DM9 : le lecteur SQL de la Planification sur {@code abords_vasculaires} et {@code patients}, {@code ContraintesDesPatientsJdbc}
 * ({@code OptimisationConfiguration:84}, RG-PLN-097). DM5 ({@code dossiermedical/dm5/abords/planification}) fige la compétence CATHETER
 * (types, activité, fin, autre centre, abord sans fiche) ; ici le centre vide, le patient sans contrainte (absent), les transporteurs, la
 * compétence PEDIATRIE à la borne des dix-huit ans, le cumul, et l'abord posé au centre A pour une fiche du centre B.
 */
class LesContraintesDesPatientsLuesParLaPlanificationCharacterizationIntegrationTest extends Dm9CharacterizationSupport {

    @Autowired
    private ContraintesDesPatients contraintes;

    @Override
    protected String dossier() {
        return "planification";
    }

    /** Contraintes par alias de patient du centre, au jour donné (transporteurs par alias, compétences triées). */
    private Map<String, String> contraintes(UUID centre, LocalDate jour) {
        Map<String, String> sortie = new TreeMap<>();
        contraintes.duCentre(new CentreId(centre), jour).forEach((patient, c) -> sortie.put(aliasDe(patient.valeur()),
                "competences=" + c.competences().stream().map(Enum::name).sorted().toList() + " transporteurs=" + aliasTries(c.transporteurs())));
        return sortie;
    }

    @Test
    void un_centre_vide_ne_rend_rien_et_un_patient_sans_transporteur_ni_competence_est_absent() throws Exception {
        LocalDate jour = fixe(100);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 centre A sans patient", contraintes(CENTRE_A, jour));
        patientDm9(CENTRE_A, "ADULTE_SANS_RIEN");
        UUID aller = idStable("transporteur", "ALLER");
        UUID retour = idStable("transporteur", "RETOUR");
        aliasNommes.put(aller.toString(), "<TRANSPORTEUR_ALLER>");
        aliasNommes.put(retour.toString(), "<TRANSPORTEUR_RETOUR>");
        patientDm9(CENTRE_A, "TRANSPORTEUR_ALLER_SEUL", "M", null, aller, null);
        patientDm9(CENTRE_A, "TRANSPORTEUR_RETOUR_SEUL", "F", null, null, retour);
        patientDm9(CENTRE_A, "MEME_TRANSPORTEUR_ALLER_ET_RETOUR", "M", null, aller, aller);
        patientDm9(CENTRE_A, "DEUX_TRANSPORTEURS", "M", null, aller, retour);
        patientDm9(CENTRE_B, "TRANSPORTEUR_DU_CENTRE_B", "M", null, aller, null);
        String avant = empreinteLecteurs();
        m.put("02 centre A : transporteurs (un patient sans rien absent, meme transporteur compte une fois)", contraintes(CENTRE_A, jour));
        m.put("03 centre B", contraintes(CENTRE_B, jour));
        m.put("04 aucune ecriture", empreinteLecteurs().equals(avant));
        arbre("transporteurs", m);
    }

    @Test
    void la_competence_pediatrie_vaut_sous_dix_huit_ans_au_jour_donne_la_borne_exclue_et_se_cumule_avec_le_catheter() throws Exception {
        LocalDate jour = fixe(100);
        patientDm9(CENTRE_A, "NE_18_ANS_MOINS_UN_JOUR_AVANT", "M", jour.minusYears(18).plusDays(1), null, null);
        patientDm9(CENTRE_A, "NE_EXACTEMENT_18_ANS_AVANT", "M", jour.minusYears(18), null, null);
        patientDm9(CENTRE_A, "NE_18_ANS_ET_UN_JOUR_AVANT", "M", jour.minusYears(18).minusDays(1), null, null);
        patientDm9(CENTRE_A, "NE_LE_JOUR_MEME", "F", jour, null, null);
        patientDm9(CENTRE_A, "NAISSANCE_FUTURE", "F", jour.plusDays(30), null, null);
        patientDm9(CENTRE_A, "SANS_DATE_DE_NAISSANCE", "M", null, null, null);
        UUID aller = idStable("transporteur", "ALLER");
        aliasNommes.put(aller.toString(), "<TRANSPORTEUR_ALLER>");
        UUID cumul = patientDm9(CENTRE_A, "ENFANT_AVEC_CATHETER_ET_TRANSPORTEUR", "M", jour.minusYears(5), aller, null);
        abordEnBase(CENTRE_A, cumul, "KT_CUMUL", "KT_TUNNELISE", fixe(10), null, true);
        UUID ficheB = patientDm9(CENTRE_B, "FICHE_B_ABORD_POSE_AU_CENTRE_A", "M", null, null, null);
        abordEnBase(CENTRE_A, ficheB, "KT_FICHE_B", "KT_AIGU", fixe(10), null, true);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 au jour <D+100>", contraintes(CENTRE_A, jour));
        m.put("02 au lendemain <D+101> (la borne des dix-huit ans a bouge d'un jour)", contraintes(CENTRE_A, fixe(101)));
        m.put("03 centre B (fiche B dont l'abord est pose au centre A)", contraintes(CENTRE_B, jour));
        arbre("pediatrie", m);
    }
}
