package com.hemodialyse.backend.contexts.gmao.application.intervenant;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantId;

import java.util.Optional;

/**
 * Consulte un intervenant du centre. Nouvelle capacité minimale (décision utilisateur 2026-10-10, KB-GMAO-22) : le legacy
 * n'a pas cette route. Un intervenant inconnu ou d'un autre centre est indistinguable (vide).
 */
public final class ConsulterIntervenant {

    private final LectureIntervenants lecture;

    public ConsulterIntervenant(LectureIntervenants lecture) {
        this.lecture = lecture;
    }

    public Optional<IntervenantVue> executer(IntervenantId id, CentreId centre) {
        return lecture.parId(id).filter(vue -> vue.centreId().equals(centre.valeur()));
    }
}
