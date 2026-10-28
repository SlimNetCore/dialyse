package com.hemodialyse.backend.contexts.planification.domain;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Port sortant : les absences des infirmiers (un dépôt par racine d'agrégat). Toujours borné au centre.
 */
public interface AbsencesRepository {

    void enregistrer(AbsenceInfirmier absence);

    Optional<AbsenceInfirmier> parId(CentreId centre, AbsenceId id);

    /**
     * Les absences du centre, des plus récentes aux plus anciennes (début décroissant, puis identifiant).
     */
    PageDeResultats<AbsenceInfirmier> page(CentreId centre, Pagination pagination);

    /**
     * Les absences d'un infirmier, même ordre.
     */
    PageDeResultats<AbsenceInfirmier> pageDeLInfirmier(CentreId centre, InfirmierId infirmier, Pagination pagination);

    /**
     * Une absence de cet infirmier partage-t-elle un jour avec cette période ?
     */
    boolean existeUneAbsenceSurLaPeriode(CentreId centre, InfirmierId infirmier, PeriodeDAbsence periode);

    /**
     * L'infirmier est-il absent ce jour-là ?
     */
    boolean absentLe(CentreId centre, InfirmierId infirmier, LocalDate jour);

    void retirer(CentreId centre, AbsenceId id);
}
