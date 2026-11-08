package com.hemodialyse.backend.characterization.seance.se7;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SE7a : l'audit des écritures des routes résiduelles (ajout et retrait d'un jour du calendrier, volet médical). Le journal d'audit est
 * tenu par un filtre générique du gabarit de route, indépendant du contrôleur : le routage vers {@code contexts/seance} ne doit rien
 * changer (ADR 0094 : la journalisation est REPRODUITE telle quelle, y compris pour les refus). Les lectures (journal, calendrier,
 * séances récentes) ne sont pas journalisées.
 */
class LAuditDesRoutesResiduellesCharacterizationIntegrationTest extends Se7CharacterizationSupport {

    @Override
    protected String dossier() {
        return "audit";
    }

    @Test
    void les_ecritures_du_calendrier_et_du_volet_medical_sont_journalisees_avec_la_route_et_le_statut() throws Exception {
        UUID patient = patientCouvert(CENTRE_A, "PAT_A");
        UUID seance = seanceValidee(CENTRE_A, patient, "SEANCE", jour(0), "VALIDEE");
        monterLAudit();
        ajouterJour("holiday", CENTRE_A, fixe(14), "Ferie", adminA());
        ajouterJour("holiday", CENTRE_A, fixe(14), "Ferie", adminA());
        ajouterJour("closure", CENTRE_A, fixe(19), "Fermeture", adminA());
        supprimerJour("holiday", UUID.randomUUID(), CENTRE_A, adminA());
        voletMedical(seance, "{\"centerId\":\"" + CENTRE_A + "\",\"prescription\":\"Fictive\"}", medecinA());
        voletMedical(seance, "{\"centerId\":\"" + CENTRE_A + "\"}", infirmierA());
        calendrier(CENTRE_A, "2026", "1", infirmierA());
        journal(CENTRE_A, fixe(0), infirmierA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("journal", journalDAudit());
        arbre("audit", m);
    }
}
