package com.hemodialyse.backend.contexts.comptabilite.contrat;

import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.NumerotationDesDocuments;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Double en mémoire de la numérotation des documents (S-DDL2) : même contrat que l'adapter JDBC. */
public final class NumerotationDesDocumentsEnMemoire implements NumerotationDesDocuments {

    private record Compteur(String prefixe, long valeur) {
    }

    private final Map<String, Compteur> compteurs = new HashMap<>();

    public void precharger(UUID centre, String type, String prefixe, long valeur) {
        compteurs.put(centre + "/" + type, new Compteur(prefixe, valeur));
    }

    @Override
    public synchronized String suivant(UUID centre, String type, String prefixeParDefaut) {
        Compteur courant = compteurs.get(centre + "/" + type);
        Compteur suivant = courant == null ? new Compteur(prefixeParDefaut, 1) : new Compteur(courant.prefixe(), courant.valeur() + 1);
        compteurs.put(centre + "/" + type, suivant);
        return suivant.prefixe() + String.format("%05d", suivant.valeur());
    }
}
