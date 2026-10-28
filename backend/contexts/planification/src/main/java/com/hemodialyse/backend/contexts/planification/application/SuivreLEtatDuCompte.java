package com.hemodialyse.backend.contexts.planification.application;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CompteId;
import com.hemodialyse.backend.contexts.planification.domain.Infirmier;
import com.hemodialyse.backend.contexts.planification.domain.InfirmiersRepository;

import java.util.List;

/**
 * Le compte a été activé ou désactivé dans l'administration des utilisateurs : les fiches liées suivent (RG-INF-003). Borné au
 * centre de l'administrateur quand il en a un (décision du 26/10/2026, KB-PLN-15) ; sans centre (accès transverse), toutes les
 * fiches du compte suivent. Joue dans la transaction de l'administration si elle en a ouvert une.
 */
public class SuivreLEtatDuCompte {
    private final UniteDeTravail uniteDeTravail;
    private final InfirmiersRepository infirmiers;

    public SuivreLEtatDuCompte(UniteDeTravail uniteDeTravail, InfirmiersRepository infirmiers) {
        this.uniteDeTravail = uniteDeTravail;
        this.infirmiers = infirmiers;
    }

    /** @param centre le centre de l'administrateur, ou {@code null} pour tous les centres */
    public void executer(CompteId compte, boolean compteActif, CentreId centre) {
        uniteDeTravail.executer(() -> {
            List<Infirmier> fiches = centre == null ? infirmiers.fichesDuCompte(compte)
                    : infirmiers.fichesDuCompteDuCentre(centre, compte);
            for (Infirmier fiche : fiches) {
                Infirmier suivie = fiche.suivreLeCompte(compteActif);
                if (suivie != fiche) {
                    infirmiers.enregistrer(suivie);
                }
            }
            return null;
        });
    }
}
