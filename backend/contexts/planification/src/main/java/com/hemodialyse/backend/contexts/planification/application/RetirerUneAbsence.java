package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.publishedlanguage.NotificationsDesAbsencesDInfirmiers;
import com.hemodialyse.backend.contexts.planification.domain.AbsenceInfirmier;
import com.hemodialyse.backend.contexts.planification.domain.AbsencesRepository;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.InfirmiersRepository;
import com.hemodialyse.backend.contexts.planification.domain.RegleDePlanificationViolee;

/**
 * Retire une absence (administration ou secrétariat, quelle qu'en soit la date ni l'auteur). Le retrait est notifié et diffusé : les
 * écrans de planification se rafraîchissent (décision du 26/10/2026, KB-PLN-34).
 */
public class RetirerUneAbsence {
    private final UniteDeTravail uniteDeTravail;
    private final AbsencesRepository absences;
    private final InfirmiersRepository infirmiers;
    private final NotificationsDesAbsencesDInfirmiers notifications;

    public RetirerUneAbsence(UniteDeTravail uniteDeTravail, AbsencesRepository absences, InfirmiersRepository infirmiers,
                             NotificationsDesAbsencesDInfirmiers notifications) {
        this.uniteDeTravail = uniteDeTravail;
        this.absences = absences;
        this.infirmiers = infirmiers;
        this.notifications = notifications;
    }

    public void executer(CentreId centre, AbsenceId id, boolean parAdministrateur) {
        AbsenceInfirmier retiree = uniteDeTravail.executer(() -> {
            AbsenceInfirmier absence = absences.parId(centre, id).orElseThrow(
                    () -> new RegleDePlanificationViolee("ABSENCE_INTROUVABLE", "Absence introuvable"));
            absences.retirer(centre, id);
            return absence;
        });
        String nom = infirmiers.parId(centre, retiree.infirmier()).map(f -> f.nomComplet()).orElse("");
        notifications.absenceRetireeParLAdministration(centre.valeur(), nom, retiree.periode().debut(), retiree.periode().fin(), !parAdministrateur);
    }
}
