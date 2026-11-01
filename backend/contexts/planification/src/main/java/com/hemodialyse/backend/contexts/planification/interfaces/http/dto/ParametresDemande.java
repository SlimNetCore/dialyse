package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;

import java.util.List;
import java.util.UUID;

/**
 * @param patientsParInfirmier    ratio de sécurité ; absent = valeur par défaut
 * @param patientsParPosteEtSerie patients suivis par poste et par série (capacité théorique) ; absent = défaut
 */
public record ParametresDemande(List<JourDeLaSemaine> joursOuverts, List<UUID> sallesIsolement,
                                Integer patientsParInfirmier, Integer patientsParPosteEtSerie) {
}
