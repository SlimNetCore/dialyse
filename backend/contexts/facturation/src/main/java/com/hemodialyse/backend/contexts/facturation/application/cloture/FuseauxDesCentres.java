package com.hemodialyse.backend.contexts.facturation.application.cloture;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;

import java.time.ZoneId;

/**
 * Port sortant : le fuseau d'un centre, pour dater « aujourd'hui » au centre (un mois est terminé au fuseau du centre, ADR 0044).
 */
@FunctionalInterface
public interface FuseauxDesCentres {
    ZoneId de(CentreId centre);
}

