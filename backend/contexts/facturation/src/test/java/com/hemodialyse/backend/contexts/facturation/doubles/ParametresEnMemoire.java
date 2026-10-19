package com.hemodialyse.backend.contexts.facturation.doubles;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.parametres.ParametresDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.parametres.ParametresDeFacturationRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Double en mémoire des paramètres de facturation : même contrat que l'adaptateur JDBC (voir les tests de contrat).
 */
public class ParametresEnMemoire implements ParametresDeFacturationRepository {

    private final Map<CentreId, ParametresDeFacturation> parametres = new HashMap<>();
    private final Map<CentreId, String> auteurs = new HashMap<>();
    private int ecritures;

    @Override
    public Optional<ParametresDeFacturation> duCentre(CentreId centre) {
        return Optional.ofNullable(parametres.get(centre));
    }

    @Override
    public void enregistrer(CentreId centre, ParametresDeFacturation valeurs, String auteur) {
        parametres.put(centre, valeurs);
        auteurs.put(centre, auteur);
        ecritures++;
    }

    public String auteur(CentreId centre) {
        return auteurs.get(centre);
    }

    public int ecritures() {
        return ecritures;
    }
}
