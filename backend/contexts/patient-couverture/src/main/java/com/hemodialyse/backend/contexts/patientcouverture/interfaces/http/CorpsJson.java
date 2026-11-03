package com.hemodialyse.backend.contexts.patientcouverture.interfaces.http;

import com.hemodialyse.backend.contexts.patientcouverture.domain.SaisieInvalide;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Lecture du corps JSON APRÈS la vérification des droits : la méthode du contrôleur reçoit les octets (aucune conversion avant
 * l'autorisation), puis appelle {@link #lire}. Un refus de droits (403) passe donc avant un corps illisible (400) ou un type de
 * contenu non supporté (415), comme pour la Planification.
 */
final class CorpsJson {
    private CorpsJson() {
    }

    static <T> T lire(ObjectMapper lecteur, HttpServletRequest requete, byte[] corps, Class<T> type)
            throws HttpMediaTypeNotSupportedException {
        verifierLeType(requete, corps);
        if (corps == null || corps.length == 0) throw new SaisieInvalide("Corps de la requête illisible");
        T lu;
        try {
            lu = lecteur.readValue(corps, type);
        } catch (JacksonException e) {
            throw new SaisieInvalide("Corps de la requête illisible");
        }
        if (lu == null) throw new SaisieInvalide("Corps de la requête illisible");
        return lu;
    }

    private static void verifierLeType(HttpServletRequest requete, byte[] corps) throws HttpMediaTypeNotSupportedException {
        String entete = requete.getContentType();
        if (entete == null) {
            if (corps != null && corps.length > 0) {
                throw new HttpMediaTypeNotSupportedException((MediaType) null, List.of(MediaType.APPLICATION_JSON));
            }
            return;
        }
        MediaType type;
        try {
            type = MediaType.parseMediaType(entete);
        } catch (InvalidMediaTypeException e) {
            throw new HttpMediaTypeNotSupportedException((MediaType) null, List.of(MediaType.APPLICATION_JSON));
        }
        boolean json = MediaType.APPLICATION_JSON.isCompatibleWith(type) || type.getSubtype().endsWith("+json");
        if (!json) throw new HttpMediaTypeNotSupportedException(type, List.of(MediaType.APPLICATION_JSON));
    }
}
