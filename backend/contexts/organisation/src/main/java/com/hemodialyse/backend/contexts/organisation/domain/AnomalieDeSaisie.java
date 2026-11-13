package com.hemodialyse.backend.contexts.organisation.domain;

import java.util.Map;

/** Une anomalie de saisie d'un champ : sa clé, son code, son message et ses paramètres. */
public record AnomalieDeSaisie(String champ, String code, String message, Map<String, String> parametres) {
}
