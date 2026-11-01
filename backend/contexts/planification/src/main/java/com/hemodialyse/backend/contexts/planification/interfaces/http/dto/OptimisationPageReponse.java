package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import com.hemodialyse.backend.contexts.planification.application.OptimisationLue.ExecutionLue;

import java.util.List;

/**
 * Historique paginé : mêmes champs que le legacy ({@code items, total, page, size} dans cet ordre).
 */
public record OptimisationPageReponse(List<ExecutionLue> items, long total, int page, int size) {
}
