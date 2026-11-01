package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

/**
 * @param motDePasseTemporaire communiqué une seule fois : il n'est jamais relisible ensuite
 */
public record CompteCreeReponse(InfirmierReponse infirmier, String motDePasseTemporaire) {
}
