package com.hemodialyse.backend.contexts.facturation.application.parametres;

import com.hemodialyse.backend.contexts.facturation.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.parametres.ParametresDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.parametres.ParametresDeFacturationRepository;

import java.time.Clock;

/**
 * Cas d'usage des paramètres de facturation du centre (RG-FAC-010, 011 ; slice F1) : les consulter et les modifier.
 */
public final class ConfigurerLaFacturation {

    private final ParametresDeFacturationRepository parametres;
    private final UniteDeTravail uniteDeTravail;
    private final Clock horloge;

    public ConfigurerLaFacturation(ParametresDeFacturationRepository parametres, UniteDeTravail uniteDeTravail, Clock horloge) {
        this.parametres = parametres;
        this.uniteDeTravail = uniteDeTravail;
        this.horloge = horloge;
    }

    /**
     * Les paramètres du centre ; les valeurs par défaut s'il n'a rien enregistré, <b>sans rien écrire</b> (KB-FAC-08 corrigé).
     */
    public ParametresDeFacturation consulter(CentreId centre) {
        return uniteDeTravail.executer(() -> parametres.duCentre(centre).orElse(ParametresDeFacturation.PAR_DEFAUT));
    }

    /**
     * Remplace les paramètres du centre et les rend tels qu'enregistrés. Le modèle de numérotation est contrôlé (KB-FAC-09).
     */
    public ParametresDeFacturation modifier(CentreId centre, Commande commande, String auteur) {
        ParametresDeFacturation saisis = ParametresDeFacturation.saisis(commande.modele(), commande.regroupementMultiForfait(),
                horloge.instant());
        return uniteDeTravail.executer(() -> {
            parametres.enregistrer(centre, saisis, auteur);
            return parametres.duCentre(centre).orElseThrow();
        });
    }

    public record Commande(String modele, boolean regroupementMultiForfait) {
    }
}
