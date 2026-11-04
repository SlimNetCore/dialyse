package com.hemodialyse.backend.application.centre;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port : « aujourd'hui » dans le fuseau d'un centre (PC4, décision du 2026-11-04, KB-PAT-47), jamais le jour de la JVM ni d'UTC.
 * Utilisé par le tableau de bord (échéance des attestations et des PEC) et par la création ou le changement de date d'une séance
 * sans date. Implémenté par {@code infrastructure.acl.centres.JourDuCentreLegacy}.
 */
public interface JourDuCentre {

    LocalDate aujourdhui(UUID centre);
}
