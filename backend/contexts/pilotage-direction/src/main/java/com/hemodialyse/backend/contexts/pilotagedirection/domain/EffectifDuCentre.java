package com.hemodialyse.backend.contexts.pilotagedirection.domain;

/** Les patients de l'effectif de la période d'un centre, dont ceux qui sont sous cathéter veineux (KT). */
public record EffectifDuCentre(long patients, long sousKt) {

    public static EffectifDuCentre aucun() {
        return new EffectifDuCentre(0, 0);
    }
}
