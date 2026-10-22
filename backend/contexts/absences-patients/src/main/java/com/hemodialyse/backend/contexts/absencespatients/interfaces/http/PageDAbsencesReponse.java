package com.hemodialyse.backend.contexts.absencespatients.interfaces.http;

import com.hemodialyse.backend.contexts.absencespatients.application.LigneDuSuivi;
import com.hemodialyse.backend.contexts.absencespatients.domain.PageDeResultats;

import java.util.List;

/**
 * Page de la liste du suivi : {@code items}, {@code total}, {@code page}, {@code size} (taille effective, plafonnée).
 */
public record PageDAbsencesReponse(List<AbsenceReponse> items, long total, int page, int size) {

    static PageDAbsencesReponse depuis(PageDeResultats<LigneDuSuivi> page) {
        return new PageDAbsencesReponse(page.elements().stream().map(AbsenceReponse::depuis).toList(), page.total(),
                page.page(), page.taille());
    }
}
