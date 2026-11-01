package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import com.hemodialyse.backend.contexts.planification.domain.ReglagesDePlanification;

public record ReglagesReponse(boolean replanificationAuto, int heuresParVacation, int heuresHebdoTempsPlein,
                              int reposHebdoMin) {
    public static ReglagesReponse depuis(ReglagesDePlanification r) {
        return new ReglagesReponse(r.replanificationNocturne(), r.heuresParVacation(), r.heuresHebdoTempsPlein(), r.reposHebdoMin());
    }
}
