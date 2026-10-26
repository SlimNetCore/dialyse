package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.publishedlanguage.NotificationsDesAbsencesDInfirmiers;
import com.hemodialyse.backend.contexts.planification.domain.AbsenceInfirmier;
import com.hemodialyse.backend.contexts.planification.domain.AbsencesRepository;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CompteId;
import com.hemodialyse.backend.contexts.planification.domain.Infirmier;
import com.hemodialyse.backend.contexts.planification.domain.InfirmiersRepository;
import com.hemodialyse.backend.contexts.planification.domain.RegleDePlanificationViolee;

/**
 * L'infirmier connecté retire une de ses absences (RG-INF-032) : seulement les siennes (une absence d'un collègue est introuvable),
 * à venir au jour du centre ({@code ABSENCE_NON_ANNULABLE}) et déclarées par lui, non saisies par l'administration (KB-PLN-39).
 * Le retrait est notifié (KB-PLN-34).
 */
public class RetirerMonAbsence {
    private final UniteDeTravail uniteDeTravail;
    private final InfirmiersRepository infirmiers;
    private final AbsencesRepository absences;
    private final NotificationsDesAbsencesDInfirmiers notifications;
    private final HorlogeDesCentres horloge;

    public RetirerMonAbsence(UniteDeTravail uniteDeTravail, InfirmiersRepository infirmiers, AbsencesRepository absences,
                             NotificationsDesAbsencesDInfirmiers notifications, HorlogeDesCentres horloge) {
        this.uniteDeTravail = uniteDeTravail;
        this.infirmiers = infirmiers;
        this.absences = absences;
        this.notifications = notifications;
        this.horloge = horloge;
    }

    public void executer(CentreId centre, CompteId compte, AbsenceId id) {
        Infirmier fiche = FicheLiee.de(infirmiers, centre, compte);
        AbsenceInfirmier retiree = uniteDeTravail.executer(() -> {
            AbsenceInfirmier absence = absences.parId(centre, id).filter(a -> a.infirmier().equals(fiche.id())).orElseThrow(
                    () -> new RegleDePlanificationViolee("ABSENCE_INTROUVABLE", "Absence introuvable"));
            absence.exigerRetirablePar(compte, horloge.aujourdhui(centre));
            absences.retirer(centre, id);
            return absence;
        });
        notifications.absenceRetireeParLInfirmier(centre.valeur(), fiche.nomComplet(), retiree.periode().debut(), retiree.periode().fin());
    }
}
