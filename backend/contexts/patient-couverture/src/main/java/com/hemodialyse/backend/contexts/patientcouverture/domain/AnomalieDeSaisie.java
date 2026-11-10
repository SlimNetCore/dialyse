package com.hemodialyse.backend.contexts.patientcouverture.domain;

import java.util.Map;

/** Une valeur saisie refusée : champ, code stable, message lisible et paramètres du message. */
public record AnomalieDeSaisie(String champ, String code, String message, Map<String, String> parametres) {
}
