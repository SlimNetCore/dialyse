package com.hemodialyse.backend.contexts.facturation.doubles;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SequenceDeFacturation;

import java.util.HashMap;
import java.util.Map;

/**
 * Double de la séquence de numérotation : même contrat que l'adaptateur JDBC (voir {@code SequenceContrat}).
 */
public class SequenceEnMemoire implements SequenceDeFacturation {

    private final Map<String, Integer> valeurs = new HashMap<>();

    public void poser(CentreId centre, int annee, int valeur) {
        valeurs.put(centre.valeur() + "/" + annee, valeur);
    }

    @Override
    public int courante(CentreId centre, int annee) {
        return valeurs.getOrDefault(centre.valeur() + "/" + annee, 0);
    }
}
