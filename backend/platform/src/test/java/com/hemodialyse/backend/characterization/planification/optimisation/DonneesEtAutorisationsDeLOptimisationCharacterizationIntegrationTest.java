package com.hemodialyse.backend.characterization.planification.optimisation;

import com.hemodialyse.backend.characterization.planification.OptimisationCharacterizationSupport;
import com.hemodialyse.backend.characterization.planification.JourSemaine;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Filet de caractérisation P8, lot 4 : autorisations des routes d'optimisation (onze profils).
 * <p>
 * La lecture directe des données à optimiser et les déplacements temporaires sont désormais couverts par le contexte
 * {@code contexts/planification} (tests {@code LireLesDonneesDeLOptimisationTest}, {@code DeplacementTemporaireTest}
 * et {@code OptimisationJdbcContratTest}) ; le code legacy correspondant a été retiré (slice P8-S3).
 */
class DonneesEtAutorisationsDeLOptimisationCharacterizationIntegrationTest extends OptimisationCharacterizationSupport {

    @Override
    protected String theme() {
        return "p8-optimisation/donnees";
    }

    @Test
    void les_onze_profils_et_l_anonyme_face_aux_huit_routes_de_l_optimisation() throws Exception {
        petitDecor();
        nommerLesSemaines(0, 3);
        UUID inconnu = UUID.randomUUID();
        String base = OPTIMISATIONS + "/" + inconnu;
        Map<String, Map<String, String>> matrice = new LinkedHashMap<>();
        Map<String, RequestPostProcessor> profils = profils();
        List<String> noms = new ArrayList<>(profils.keySet());
        noms.add("anonyme");
        for (String nom : noms) {
            RequestPostProcessor who = profils.get(nom);
            Map<String, String> lignes = new LinkedHashMap<>();
            lignes.put("GET historique", statutEtCode(appel("GET", OPTIMISATIONS, null, who)));
            lignes.put("GET execution", statutEtCode(appel("GET", base, null, who)));
            lignes.put("GET calendrier", statutEtCode(appel("GET", base + "/calendrier", null, who)));
            lignes.put("GET impression", statutEtCode(appel("GET", base + "/impression", null, who)));
            lignes.put("POST lancer (corps vide {})", statutEtCode(appel("POST", OPTIMISATIONS, "{}", who)));
            lignes.put("POST lancer (corps valide, calcul vide)", statutEtCode(appel("POST", OPTIMISATIONS,
                    lancement("ROULEMENT", semaine(1, JourSemaine.DIMANCHE), 1), who)));
            attendreLaFinDeTousLesCalculs();
            lignes.put("POST arret", statutEtCode(appel("POST", base + "/arret", "", who)));
            lignes.put("POST application", statutEtCode(appel("POST", base + "/application", "", who)));
            lignes.put("DELETE execution", statutEtCode(appel("DELETE", base, null, who)));
            matrice.put(nom, lignes);
        }
        arbre("routes_par_profil", matrice);
    }
}
