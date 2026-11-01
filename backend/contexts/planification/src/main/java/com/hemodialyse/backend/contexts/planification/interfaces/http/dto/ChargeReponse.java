package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import com.hemodialyse.backend.contexts.planification.domain.Presence.ChargeInfirmier;

import java.util.List;

public record ChargeReponse(List<ChargeInfirmier> items, long total, int page, int size, double moyenne) {
}
