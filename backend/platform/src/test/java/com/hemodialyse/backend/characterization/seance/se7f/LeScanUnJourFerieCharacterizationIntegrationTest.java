package com.hemodialyse.backend.characterization.seance.se7f;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SE7f : la situation du planning fournie à la Séance par {@code PlanningFourniLegacy} → {@code SeancePlanningJdbcAdapter} (legacy) : le
 * centre est « fermé » si le jour compte dans {@code center_holiday} (jour férié) OU {@code center_closure_day} (fermeture). SE1 ne fige que
 * la fermeture exceptionnelle ; ici le jour férié, le cumul des deux, et le férié d'un autre centre ou d'une autre date (ignorés). Le scan
 * n'a pas de date : il porte sur le jour du centre ({@code <J>}, KB-SEA-01), seul jour posé en férié.
 */
class LeScanUnJourFerieCharacterizationIntegrationTest extends Se7fCharacterizationSupport {

    @Override
    protected String dossier() {
        return "planning";
    }

    @Test
    void un_jour_ferie_du_centre_ferme_le_centre_comme_une_fermeture_et_seul_l_administrateur_le_franchit_avec_un_motif() throws Exception {
        LocalDate aujourdhui = aujourdhuiNomme();
        patientCouvert(CENTRE_A, "PAT_A");
        jourFerie(CENTRE_A, aujourdhui, "Ferie fictif");
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, String> a = new LinkedHashMap<>();
        a.put("01 INFIRMIER sans motif", refus(scanner("SE1-PAT_A", null, null, infirmierA())));
        a.put("02 INFIRMIER avec motif URGENCE", refus(scanner("SE1-PAT_A", "URGENCE", null, infirmierA())));
        a.put("03 SECRETAIRE", refus(scanner("SE1-PAT_A", null, null, secretaireA())));
        a.put("04 ADMIN sans motif", refus(scanner("SE1-PAT_A", null, null, adminA())));
        a.put("05 aucune ecriture", String.valueOf(nombreDeSeances(CENTRE_A)));
        a.put("06 ADMIN avec motif", refus(scanner("SE1-PAT_A", "URGENCE", "Urgence un jour ferie", adminA())));
        m.put("01 jour ferie du centre A", a);
        m.put("02 etat apres le scan de l'administrateur", etatDesSeances());

        nettoyerSe1();
        patientCouvert(CENTRE_A, "PAT_A");
        jourFerie(CENTRE_A, aujourdhui, "Ferie fictif");
        fermerLeCentre(CENTRE_A, aujourdhui);
        Map<String, String> b = new LinkedHashMap<>();
        b.put("01 INFIRMIER avec motif (ferie ET fermeture le meme jour)", refus(scanner("SE1-PAT_A", "URGENCE", null, infirmierA())));
        b.put("02 ADMIN avec motif", refus(scanner("SE1-PAT_A", "URGENCE", "Urgence", adminA())));
        m.put("03 ferie et fermeture cumules", b);

        nettoyerSe1();
        patientCouvert(CENTRE_A, "PAT_A");
        jourFerie(CENTRE_B, aujourdhui, "Ferie du centre B");
        jourFerie(CENTRE_A, jour(-1), "Ferie d'hier");
        jourFerie(CENTRE_A, jour(1), "Ferie de demain");
        Map<String, String> c = new LinkedHashMap<>();
        c.put("01 INFIRMIER sans motif (ferie d'un autre centre, d'hier et de demain : ignores)", refus(scanner("SE1-PAT_A", null, null, infirmierA())));
        c.put("02 seances ecrites", String.valueOf(nombreDeSeances(CENTRE_A)));
        m.put("04 feries d'un autre centre ou d'une autre date", c);
        arbre("jour_ferie", m);
    }
}
