package com.hemodialyse.backend.contexts.seance.domain;

import java.time.LocalDate;

/**
 * La période d'un cahier de dialyse, bornes incluses. Une borne absente vaut la plus large ({@value #DEBUT_PAR_DEFAUT} / {@value
 * #FIN_PAR_DEFAUT}) : les valeurs par défaut ne valent que si le paramètre est absent. Un début postérieur à la fin est refusé.
 */
public record PeriodeDuCahier(LocalDate du, LocalDate au) {

    public static final String DEBUT_PAR_DEFAUT = "1900-01-01";
    public static final String FIN_PAR_DEFAUT = "2999-12-31";

    public PeriodeDuCahier {
        if (du != null && au != null && du.isAfter(au)) throw CahierRefuse.periodeInvalide();
        du = du == null ? LocalDate.parse(DEBUT_PAR_DEFAUT) : du;
        au = au == null ? LocalDate.parse(FIN_PAR_DEFAUT) : au;
    }
}
