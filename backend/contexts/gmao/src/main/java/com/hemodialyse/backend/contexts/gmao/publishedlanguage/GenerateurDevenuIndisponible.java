package com.hemodialyse.backend.contexts.gmao.publishedlanguage;

import java.time.Instant;
import java.util.UUID;

/**
 * Événement publié {@code gmao.v1} : un générateur de dialyse a changé de statut (mise hors service, état constaté par une
 * intervention, réforme). Le consommateur juge de la perte de disponibilité avec {@code statutPrecedent} (KB-GMAO-37 :
 * seule la sortie de {@code EN_SERVICE} compte). Livré au moins une fois, sans ordre garanti : le consommateur est idempotent.
 * Records à types JDK uniquement ; un changement incompatible sera publié en {@code v2}, en parallèle.
 *
 * @param statut code du statut atteint ({@code HORS_SERVICE}, {@code EN_MAINTENANCE}, {@code A_REFORMER}, {@code REFORME}...)
 * @param designation désignation du générateur au moment de l'événement
 */
public record GenerateurDevenuIndisponible(UUID equipementId, UUID centreId, String code, String designation,
                                           String statutPrecedent, String statut, Instant survenuLe) {
}
