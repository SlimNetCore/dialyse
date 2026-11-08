package com.hemodialyse.backend.characterization.seance.se5;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SE5, revue (KB-SEA-49) : {@code PUT /api/v1/seances/{id}} (changement de date, route NON reprise par SE5) réécrit toute la ligne de la
 * séance depuis l'entité legacy relue. Ce scénario observe ce qu'il advient de {@code facture_id} et des colonnes
 * {@code forfait_override_*} d'une séance non {@code FACTUREE} qui porte une facture. Il décrit le comportement ACTUEL, même s'il est faux.
 */
class LaModificationDeDateEtLaFactureDUneSeanceCharacterizationIntegrationTest extends Se5CharacterizationSupport {

    @Override
    protected String dossier() {
        return "date";
    }

    @Test
    void le_changement_de_date_d_une_seance_non_facturee_porteuse_d_une_facture_et_d_un_forfait_impose() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID forfait = forfaitSe5(CENTRE_A, "F1", "100.00");
        UUID seance = seanceEnBase(CENTRE_A, patient, "SEANCE", fixe(1), "SIGNEE");
        UUID facture = idStable("facture", "F-SE5");
        aliasNommes.put(facture.toString(), "<FACTURE>");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("01 imposition du forfait", refus(surcharger(seance, forfait, "compta-01", adminA())));
        jdbc.update("UPDATE seances SET facture_id = ? WHERE id = ?", facture, seance);
        m.put("02 avant : facture et forfait imposé", factureEtForfait(seance));
        String empreinte = empreinteDeLaSurcharge(seance);
        m.put("03 changement de date", refus(doPut(SEANCES_API + "/" + seance, corps("centerId", CENTRE_A, "dateSeance", fixe(2)), adminA())));
        m.put("04 après : facture et forfait imposé", factureEtForfait(seance));
        m.put("05 forfait imposé intact", empreinte.equals(empreinteDeLaSurcharge(seance)));
        arbre("facture_effacee", m);
    }

    private Object factureEtForfait(UUID seance) {
        return lignes("SELECT statut, facture_id, forfait_override_code, forfait_override_prix, forfait_override_updated_by, date_seance "
                + "FROM seances WHERE id = ?", seance);
    }
}
