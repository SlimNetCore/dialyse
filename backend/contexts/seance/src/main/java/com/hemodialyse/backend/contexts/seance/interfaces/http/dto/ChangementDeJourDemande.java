package com.hemodialyse.backend.contexts.seance.interfaces.http.dto;

import com.hemodialyse.backend.contexts.seance.domain.SaisieInvalide;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * Corps de {@code PUT /api/v1/seances/{id}} : mêmes champs JSON que le legacy ({@code centerId} obligatoire, {@code dateSeance}
 * facultative). Le jour est lu à la main, APRÈS le contrôle du centre (403 avant 400) : absent, {@code null} ou chaîne vide, c'est le jour du
 * centre (reproduit) ; une date ISO est lue, une date avec heure aussi (heure ignorée, reproduit) ; tout le reste, un nombre compris
 * ({@code 20261231} était lu comme une date d'année 57443), est un 400 (KB-SEA-56, corrigé : le legacy répondait 500 ou acceptait). Les
 * champs inconnus ({@code status}, {@code factureId}) sont ignorés, comme le legacy.
 */
public record ChangementDeJourDemande(@NotNull UUID centerId, Object dateSeance) {

    /** Le jour demandé, ou nul pour « le jour du centre ». */
    public LocalDate jour() {
        if (dateSeance == null) return null;
        if (!(dateSeance instanceof String texte)) throw new SaisieInvalide("Date de séance illisible");
        String lu = texte.strip();
        if (lu.isEmpty()) return null;
        try {
            return LocalDate.parse(lu);
        } catch (DateTimeParseException pasUneDate) {
            try {
                return LocalDateTime.parse(lu).toLocalDate();
            } catch (DateTimeParseException pasUneDateHeure) {
                throw new SaisieInvalide("Date de séance illisible");
            }
        }
    }
}
