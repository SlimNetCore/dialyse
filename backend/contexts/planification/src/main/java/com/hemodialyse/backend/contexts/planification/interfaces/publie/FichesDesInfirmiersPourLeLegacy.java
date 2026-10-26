package com.hemodialyse.backend.contexts.planification.interfaces.publie;

import com.hemodialyse.backend.contexts.planification.application.DelierAvantLaSuppressionDuCompte;
import com.hemodialyse.backend.contexts.planification.application.SuivreLEtatDuCompte;
import com.hemodialyse.backend.contexts.planification.application.VerrouillerLesIdentifiantsDeConnexion;
import com.hemodialyse.backend.contexts.planification.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CompteId;
import com.hemodialyse.backend.contexts.planification.domain.RegleDePlanificationViolee;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.FichesDesInfirmiers;

import java.util.UUID;

/**
 * Adapter entrant du langage publié {@link FichesDesInfirmiers} : traduit les identifiants et les refus du domaine (règle violée,
 * écriture concurrente) en exceptions du langage publié, sans logique.
 */
public class FichesDesInfirmiersPourLeLegacy implements FichesDesInfirmiers {
    private static final String COMPTE_LIE_A_UNE_FICHE_ACTIVE = "COMPTE_LIE_A_UNE_FICHE_ACTIVE";

    private final SuivreLEtatDuCompte suivre;
    private final DelierAvantLaSuppressionDuCompte delier;
    private final VerrouillerLesIdentifiantsDeConnexion verrouillerLesIdentifiants;

    public FichesDesInfirmiersPourLeLegacy(SuivreLEtatDuCompte suivre, DelierAvantLaSuppressionDuCompte delier,
                                           VerrouillerLesIdentifiantsDeConnexion verrouillerLesIdentifiants) {
        this.suivre = suivre;
        this.delier = delier;
        this.verrouillerLesIdentifiants = verrouillerLesIdentifiants;
    }

    @Override
    public void suivreLEtatDuCompte(UUID compte, boolean actif, UUID centre) {
        try {
            suivre.executer(new CompteId(compte), actif, centre == null ? null : new CentreId(centre));
        } catch (EcritureConcurrente concurrente) {
            throw new EcritureConcurrenteDesFiches(concurrente.getMessage());
        }
    }

    @Override
    public void delierAvantLaSuppressionDuCompte(UUID compte) {
        try {
            delier.executer(new CompteId(compte));
        } catch (RegleDePlanificationViolee refus) {
            if (COMPTE_LIE_A_UNE_FICHE_ACTIVE.equals(refus.code())) {
                throw new CompteLieAUneFicheActive(refus.getMessage());
            }
            throw refus;
        } catch (EcritureConcurrente concurrente) {
            throw new EcritureConcurrenteDesFiches(concurrente.getMessage());
        }
    }

    @Override
    public void verrouillerLesIdentifiantsDeConnexion() {
        try {
            verrouillerLesIdentifiants.executer();
        } catch (EcritureConcurrente concurrente) {
            throw new EcritureConcurrenteDesFiches(concurrente.getMessage());
        }
    }
}
