package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;

import java.time.LocalDate;

public record JourReponse(JourDeLaSemaine jour, LocalDate date, boolean ouvertHebdomadaire, String fermetureMotif) {
}
