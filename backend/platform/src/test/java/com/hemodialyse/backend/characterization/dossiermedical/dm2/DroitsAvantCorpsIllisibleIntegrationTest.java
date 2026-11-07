package com.hemodialyse.backend.characterization.dossiermedical.dm2;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Revue DM2 : un profil SANS le droit d'écrire reçoit 403 même quand son corps est illisible (les droits priment sur la lecture du
 * corps) ; un profil autorisé reçoit 400. Écrit pour le module (le legacy donnait 500 dans les deux cas, KB-MED-22).
 */
class DroitsAvantCorpsIllisibleIntegrationTest extends PrescriptionsCharacterizationSupport {

    @Override
    protected String dossier() {
        return "droits-corps-illisible";
    }

    private String[] routes(UUID patient) {
        return new String[]{
                "POST " + ordonnancesApi(patient),
                "PUT " + ordonnancesApi(patient) + "/" + UUID.randomUUID() + "/signer",
                "POST " + prescriptionsApi(patient),
                "PUT " + prescriptionsApi(patient) + "/" + UUID.randomUUID(),
                "POST " + PATIENTS_API + "/prescriptions/search",
                "POST " + PATIENTS_API + "/" + patient + "/administrations-anemie"};
    }

    private String statut(String route, String corps, RequestPostProcessor who) throws Exception {
        String[] morceaux = route.split(" ", 2);
        return statutEtCode("POST".equals(morceaux[0]) ? doPost(morceaux[1], corps, who) : doPut(morceaux[1], corps, who));
    }

    @Test
    void un_profil_sans_le_droit_recoit_403_meme_avec_un_corps_illisible_et_un_profil_autorise_recoit_400() throws Exception {
        UUID patient = patientDm2(CENTRE_A, "PAT_A");
        Map<String, String> cas = new LinkedHashMap<>();
        for (String route : routes(patient)) {
            boolean administration = route.endsWith("administrations-anemie");
            String autorise = administration ? "INFIRMIER" : "MEDECIN";
            String refuse = "SECRETAIRE";
            cas.put(route.replaceAll("[0-9a-f]{8}-[0-9a-f-]{27}", "<id>") + " / " + refuse + " / corps illisible", statut(route, "{pas du json", as(CENTRE_A, refuse)));
            cas.put(route.replaceAll("[0-9a-f]{8}-[0-9a-f-]{27}", "<id>") + " / " + autorise + " / corps illisible", statut(route, "{pas du json", as(CENTRE_A, autorise)));
        }
        arbre("corps_illisible", cas);
        assertThat(cas.entrySet().stream().filter(e -> e.getKey().contains("SECRETAIRE")).map(Map.Entry::getValue))
                .allMatch(valeur -> valeur.startsWith("403"));
        assertThat(cas.entrySet().stream().filter(e -> !e.getKey().contains("SECRETAIRE")).map(Map.Entry::getValue))
                .allMatch(valeur -> valeur.startsWith("400"));
    }
}
