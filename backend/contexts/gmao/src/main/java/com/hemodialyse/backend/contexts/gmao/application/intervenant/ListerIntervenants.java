package com.hemodialyse.backend.contexts.gmao.application.intervenant;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;

/**
 * Liste paginée des intervenants du centre, désactivés compris, triés par nom. Les bornes reproduisent les refus du legacy
 * (messages de Spring Data exposés en 400, KB-GMAO-28) ; aucune borne haute sur {@code size}.
 */
public final class ListerIntervenants {

    private final LectureIntervenants lecture;

    public ListerIntervenants(LectureIntervenants lecture) {
        this.lecture = lecture;
    }

    public PageDIntervenants executer(CentreId centre, int page, int size) {
        if (page < 0) throw new IllegalArgumentException("Page index must not be less than zero");
        if (size < 1) throw new IllegalArgumentException("Page size must not be less than one");
        return lecture.duCentre(centre, page, size);
    }
}
