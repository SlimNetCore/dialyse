package com.hemodialyse.backend.contexts.documents.publishedlanguage;

import java.util.Map;
import java.util.UUID;

/**
 * Règle d'impression d'un type de document, publiée à Documents par le contexte qui possède ce type (documents.v1, ADR 0166) : la garde
 * (profil, centre) que les routes d'impression appliquent AVANT toute lecture de données, et les paramètres que le serveur pose lui-même
 * (jamais ceux du client). Documents orchestre : il retrouve la règle par le code du type, il ne connaît ni le profil exigé ni les totaux.
 *
 * <p>Un code de type n'a qu'une règle : Documents refuse le démarrage sinon. Un type sans règle n'est pas gardé (tout authentifié
 * imprime, KB-DOC1-3, à confirmer métiers/DPO).
 *
 * <p>Un refus est un {@code org.springframework.security.access.AccessDeniedException} (403, {@code ACCESS_DENIED}) ; toute autre
 * exception levée par la règle est un échec d'impression.
 */
public interface RegleDImpressionDUnType {

    /** Le code du type de document gardé, en majuscules (ex. {@code ORDONNANCE}). */
    String type();

    /**
     * Garde du type : profil et centre. Exécutée avant toute validation de la demande et toute lecture de données.
     *
     * @param centreDemande le centre du corps de la demande, {@code null} s'il est absent
     */
    void garder(UUID centreDemande);

    /**
     * La même garde s'applique-t-elle quand le document est imprimé par l'identifiant d'un modèle de ce type ? Par défaut non : le type du
     * modèle chargé (jamais un paramètre du client) ne déclenche pas la garde (comportement historique de l'ordonnance, question ouverte
     * de l'ADR 0142).
     */
    default boolean gardeAussiParLeModele() {
        return false;
    }

    /** Les paramètres posés par le serveur pour ce type (ex. totaux enregistrés) ; ils l'emportent toujours sur ceux du client. */
    default Map<String, Object> donneesDuServeur(UUID centre, Map<String, String> parametres) {
        return Map.of();
    }
}
