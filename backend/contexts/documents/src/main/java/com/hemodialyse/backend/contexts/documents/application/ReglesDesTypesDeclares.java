package com.hemodialyse.backend.contexts.documents.application;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.RegleDImpressionDUnType;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Les règles d'impression que les contextes propriétaires publient (ADR 0166), retrouvées par le code du type (sans casse ni espaces
 * autour, comme la demande). Un code de type n'a qu'une règle, sinon le démarrage est refusé. Un type sans règle n'est pas gardé.
 */
public final class ReglesDesTypesDeclares implements ReglesDImpression {

    private final Map<String, RegleDImpressionDUnType> parType = new HashMap<>();

    public ReglesDesTypesDeclares(List<RegleDImpressionDUnType> regles) {
        for (RegleDImpressionDUnType regle : regles) {
            String code = normaliser(regle.type());
            if (code == null || code.isEmpty()) {
                throw new IllegalStateException("Une règle d'impression doit porter le code d'un type de document");
            }
            if (parType.put(code, regle) != null) {
                throw new IllegalStateException("Le type de document " + code + " a plus d'une règle d'impression");
            }
        }
    }

    private static String normaliser(String type) {
        return type == null ? null : type.trim().toUpperCase(Locale.ROOT);
    }

    @Override
    public void garderParType(String type, UUID centreDemande) {
        RegleDImpressionDUnType regle = parType.get(normaliser(type));
        if (regle != null) regle.garder(centreDemande);
    }

    @Override
    public void garderParModele(String typeDuModele, UUID centreDemande) {
        // Le type du modèle chargé (jamais un paramètre du client) décide de la garde
        RegleDImpressionDUnType regle = parType.get(normaliser(typeDuModele));
        if (regle != null && regle.gardeAussiParLeModele()) regle.garder(centreDemande);
    }

    @Override
    public Map<String, Object> donneesDuServeur(String type, UUID centre, Map<String, String> parametres) {
        RegleDImpressionDUnType regle = parType.get(normaliser(type));
        return regle == null ? Map.of() : regle.donneesDuServeur(centre, parametres);
    }
}
