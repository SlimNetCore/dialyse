package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;

import java.time.ZoneId;

/**
 * Port sortant : le fuseau horaire d'un centre (décision 7 du 21/10). Le centre n'a pas encore de fuseau en base : l'adaptateur
 * rend le fuseau par défaut (Africa/Algiers).
 */
public interface FuseauxDesCentres {

    ZoneId de(CentreId centre);
}
