package com.hemodialyse.backend.characterization.seance.se7d;

import com.hemodialyse.backend.contexts.planification.application.SeancesRealisees;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SE7d lot d2 : le lecteur SQL de {@code seances} du contexte Planification ({@code SeancesRealiseesJdbc}, jointure {@code patients} sur
 * l'identifiant ET le centre), observé par {@code GET /api/v1/planning/semaine} sur une semaine FIXE du passé (dimanche 2026-03-01).
 * Complète P6 sur : la jointure au centre du patient, le nom recomposé (prénom nul ou blanc), deux séances réalisées du même patient le
 * même jour à deux places, une séance du centre B posée sur un patient du centre A.
 */
class LesSeancesLuesParLaPlanificationCharacterizationIntegrationTest extends Se7dCharacterizationSupport {

    @Autowired
    private SeancesRealisees seancesRealisees;

    @Override
    protected String dossier() {
        return "planification";
    }

    @Test
    void la_seance_d_un_patient_dont_la_fiche_est_dans_un_autre_centre_disparait_de_la_semaine() throws Exception {
        semaineObservee();
        decorDePlacement();
        UUID ici = place(CENTRE_A, "PAT_ICI", salle1, matin, g1a, JourSemaine.MERCREDI);
        UUID parti = place(CENTRE_A, "PAT_FICHE_EN_B", salle1, matin, g1b, JourSemaine.MERCREDI);
        seance(CENTRE_A, ici, jourObserve(1), "VALIDEE", salle2, apresMidi, g2a);
        seance(CENTRE_A, parti, jourObserve(1), "VALIDEE", salle2, apresMidi, g2a);
        jdbc.update("UPDATE patients SET center_id = ? WHERE id = ?", CENTRE_B, parti);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("port planification (centre A)", seancesRealisees.duCentre(new CentreId(CENTRE_A), jourObserve(0), jourObserve(6)).stream()
                .map(s -> s.nom() + " " + s.date()).toList());
        m.put("semaine", projectionDeLaSemaine(semaineDuPlanning(jourObserve(3), adminA())));
        arbre("fiche_dans_un_autre_centre", m);
    }

    @Test
    void le_nom_affiche_est_prenom_nom_rogne_et_un_prenom_vide_ou_blanc_laisse_le_nom_seul() throws Exception {
        // prenom NULL : refusé par le schéma H2 (colonne NOT NULL côté JPA) ; rejoué sur PostgreSQL 16 par le script de rejeu
        semaineObservee();
        decorDePlacement();
        UUID nul = place(CENTRE_A, "PAT_PRENOM_BLANC", salle1, matin, g1a, JourSemaine.MERCREDI);
        UUID blanc = place(CENTRE_A, "PAT_PRENOM_VIDE", salle1, matin, g1b, JourSemaine.MERCREDI);
        UUID espaces = place(CENTRE_A, "PAT_ESPACES", salle2, matin, g2a, JourSemaine.MERCREDI);
        jdbc.update("UPDATE patients SET prenom = '   ', nom = 'Blanc' WHERE id = ?", nul);
        jdbc.update("UPDATE patients SET prenom = '', nom = 'Vide' WHERE id = ?", blanc);
        jdbc.update("UPDATE patients SET prenom = '  Ana ', nom = ' Espaces  ' WHERE id = ?", espaces);
        seance(CENTRE_A, nul, jourObserve(1), "VALIDEE", null, null, null);
        seance(CENTRE_A, blanc, jourObserve(1), "SIGNEE", salle1, apresMidi, g1a);
        seance(CENTRE_A, espaces, jourObserve(2), "FACTUREE", salle2, apresMidi, null);

        Map<String, Object> m = new LinkedHashMap<>();
        List<String> noms = new ArrayList<>();
        seancesRealisees.duCentre(new CentreId(CENTRE_A), jourObserve(0), jourObserve(6))
                .forEach(s -> noms.add("[" + s.nom() + "] " + s.date()));
        noms.sort(String::compareTo);
        m.put("noms lus par le port (tries par le test)", noms);
        m.put("semaine", projectionDeLaSemaine(semaineDuPlanning(jourObserve(3), adminA())));
        arbre("nom_recompose", m);
    }

    @Test
    void deux_seances_realisees_du_meme_patient_le_meme_jour_n_occupent_qu_une_case() throws Exception {
        semaineObservee();
        decorDePlacement();
        UUID p = place(CENTRE_A, "PAT_DOUBLE", salle1, matin, g1a, JourSemaine.MERCREDI);
        seance(CENTRE_A, p, jourObserve(1), "VALIDEE", salle2, apresMidi, g2a);
        seance(CENTRE_A, p, jourObserve(1), "SIGNEE", salle2, matin, g2a);

        Map<String, Object> semaine = projectionDeLaSemaine(semaineDuPlanning(jourObserve(3), adminA()));
        @SuppressWarnings("unchecked")
        List<String> cases = (List<String>) semaine.get("cases");
        long casesDuLundi = cases.stream().filter(c -> c.contains("/ LUNDI ") && c.contains("Patient PAT_DOUBLE")).count();
        assertEquals(1, casesDuLundi, "une seule case le lundi pour le patient");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lignes rendues par le port", seancesRealisees.duCentre(new CentreId(CENTRE_A), jourObserve(0), jourObserve(6)).size());
        m.put("cases du lundi occupees par le patient", casesDuLundi);
        m.put("case retenue (Salle 2 / Matin ou Salle 2 / Apres-midi) NON verrouillee : ORDER BY date seule, KB-SEA-78", "voir KB");
        m.put("cases hors lundi", cases.stream().filter(c -> !c.contains("/ LUNDI ")).toList());
        m.put("seancesSansCase", semaine.get("seancesSansCase"));
        m.put("conflits", semaine.get("conflits"));
        arbre("deux_seances_le_meme_jour", m);
    }

    @Test
    void la_semaine_de_chaque_centre_ne_lit_que_ses_seances_realisees() throws Exception {
        semaineObservee();
        decorDePlacement();
        UUID pa = place(CENTRE_A, "PAT_CENTRE_A", salle1, matin, g1a, JourSemaine.MERCREDI);
        UUID pb = patientPlace(CENTRE_B, "PAT_CENTRE_B", salleB, creneauB, JourSemaine.MERCREDI);
        seance(CENTRE_A, pa, jourObserve(1), "VALIDEE", salle2, apresMidi, g2a);
        seance(CENTRE_B, pb, jourObserve(1), "VALIDEE", salleB, creneauB, null);
        seance(CENTRE_B, pa, jourObserve(2), "VALIDEE", salleB, creneauB, null);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("centre A", projectionDeLaSemaine(semaineDuPlanning(jourObserve(3), adminA())));
        m.put("centre B", projectionDeLaSemaine(semaineDuPlanning(jourObserve(3), adminB())));
        arbre("chaque_centre_ses_seances", m);
    }
}
