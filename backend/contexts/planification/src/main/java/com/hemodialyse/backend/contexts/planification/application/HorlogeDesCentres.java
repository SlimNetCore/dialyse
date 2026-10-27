package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Port sortant : « aujourd'hui » d'un centre, jugé à son fuseau horaire (Africa/Algiers par défaut, ADR 0054) et non en UTC : entre
 * minuit et 1 h à Alger, le jour du centre n'est plus la veille (KB-PLN-32).
 */
public interface HorlogeDesCentres {

    LocalDate aujourdhui(CentreId centre);

    /**
     * Le jour du centre auquel appartient cet instant (clé d'idempotence des alertes rejouables). Par défaut, « aujourd'hui »
     * (doubles de test à horloge fixe) ; l'ACL des centres la juge au fuseau du centre.
     */
    default LocalDate jourDe(CentreId centre, Instant instant) {
        return aujourdhui(centre);
    }
}
