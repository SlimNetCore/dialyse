package com.hemodialyse.backend.contexts.comptabilite.application.tva;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un type de TVA tel que l'écran « Types de TVA » le montre ({@code /tva-types}) : la règle avec son identité, son libellé,
 * son état et ses informations de création.
 *
 * @param dateFinValidite {@code null} si la règle est ouverte
 * @param creeLe          {@code null} si la base ne le connaît pas
 */
public record TypeDeTvaVue(UUID id, UUID centre, String libelle, BigDecimal taux, String typePrestation,
                           boolean exonere,
                           LocalDate dateDebutValidite, LocalDate dateFinValidite, String texteReference, boolean actif,
                           OffsetDateTime creeLe, String creePar) {
}

