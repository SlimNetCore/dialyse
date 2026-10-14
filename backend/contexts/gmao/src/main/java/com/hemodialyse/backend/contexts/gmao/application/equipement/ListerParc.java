package com.hemodialyse.backend.contexts.gmao.application.equipement;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;

/**
 * Liste paginée du parc du centre (équipements supprimés logiquement exclus), du plus récent au plus ancien, filtrable par
 * statut. Les bornes reproduisent les refus du legacy (messages de Spring Data exposés en 400, KB-GMAO-35) ; aucune borne
 * haute sur {@code size}.
 */
public final class ListerParc {

    private final LectureEquipements lecture;

    public ListerParc(LectureEquipements lecture) {
        this.lecture = lecture;
    }

    public PageDEquipements executer(CentreId centre, String statut, int page, int size) {
        if (page < 0) throw new IllegalArgumentException("Page index must not be less than zero");
        if (size < 1) throw new IllegalArgumentException("Page size must not be less than one");
        return lecture.duCentre(centre, statut, page, size);
    }
}
