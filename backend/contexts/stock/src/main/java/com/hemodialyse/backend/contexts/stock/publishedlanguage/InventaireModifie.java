package com.hemodialyse.backend.contexts.stock.publishedlanguage;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Langage publié : un inventaire a changé d'état (ouvert, clôturé, annulé) ; la plateforme le relaie au WebSocket du centre.
 */
public record InventaireModifie(UUID centre, String statut, String reference, LocalDate date, String par) {
}

