package com.hemodialyse.backend.contexts.documents.interfaces.http;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Lecture d'un corps JSON APRÈS l'autorisation (le corps n'est pas lu par le cadre avant le contrôle des droits) : un autre type de contenu
 * est un 415, un corps absent, mal formé ou de forme inattendue un 400 ({@link CorpsIllisible}).
 */
final class CorpsJson {

    private CorpsJson() {
    }

    static <T> T lire(HttpServletRequest requete, ObjectMapper mapper, Class<T> type) throws HttpMediaTypeNotSupportedException {
        exigerDuJson(requete);
        byte[] octets;
        try {
            octets = requete.getInputStream().readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (octets.length == 0) {
            throw new CorpsIllisible("Corps de requête absent");
        }
        T lu;
        try {
            lu = mapper.readValue(octets, type);
        } catch (JacksonException e) {
            throw new CorpsIllisible("Corps de requête illisible");
        }
        if (lu == null) {
            throw new CorpsIllisible("Corps de requête absent");
        }
        return lu;
    }

    private static void exigerDuJson(HttpServletRequest requete) throws HttpMediaTypeNotSupportedException {
        String entete = requete.getContentType();
        if (entete != null) {
            try {
                MediaType lu = MediaType.parseMediaType(entete);
                if (MediaType.APPLICATION_JSON.isCompatibleWith(lu) || lu.getSubtype().endsWith("+json")) {
                    return;
                }
            } catch (InvalidMediaTypeException illisible) {
                // traité comme un type non supporté
            }
        }
        throw new HttpMediaTypeNotSupportedException(entete == null ? "Content-Type absent" : entete + " non supporté",
                List.of(MediaType.APPLICATION_JSON));
    }
}
