package com.hemodialyse.backend.contexts.seance.publishedlanguage;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Langage publié (fourni à la Séance par la plateforme, ADR 0088) : « aujourd'hui » dans le fuseau du centre (fuseau par défaut de la
 * plateforme si le centre n'en a pas). Réutilise le calcul du legacy (PC4) : la Séance n'a pas de second calcul de fuseau.
 */
public interface JourDuCentreFourni {

    LocalDate aujourdhui(UUID centre);
}
