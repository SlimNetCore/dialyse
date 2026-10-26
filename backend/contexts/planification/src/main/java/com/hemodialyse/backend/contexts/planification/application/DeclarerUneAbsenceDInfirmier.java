package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.publishedlanguage.NotificationsDesAbsencesDInfirmiers;
import com.hemodialyse.backend.contexts.planification.domain.AbsenceInfirmier;
import com.hemodialyse.backend.contexts.planification.domain.AbsencesRepository;
import com.hemodialyse.backend.contexts.planification.domain.AbsencesSansChevauchement;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CompteId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Infirmier;
import com.hemodialyse.backend.contexts.planification.domain.InfirmiersRepository;
import com.hemodialyse.backend.contexts.planification.domain.PeriodeDAbsence;
import com.hemodialyse.backend.contexts.planification.domain.RegleDePlanificationViolee;
import com.hemodialyse.backend.contexts.planification.domain.TypeAbsence;

/**
 * Déclare une absence saisie par l'administration ou le secrétariat (RG-INF-030, 031). Entrées illisibles (400) d'abord, puis infirmier
 * (422 {@code INFIRMIER_INTROUVABLE}), actif, absence sans chevauchement ({@code ABSENCE_CHEVAUCHEMENT}) sous le verrou de la fiche.
 * Le médecin est prévenu, l'administrateur aussi quand la saisie ne vient pas de lui ; après l'enregistrement, hors transaction.
 */
public class DeclarerUneAbsenceDInfirmier {
    private final UniteDeTravail uniteDeTravail;
    private final InfirmiersRepository infirmiers;
    private final AbsencesRepository absences;
    private final NouveauxIdentifiantsDuPlanning identifiants;
    private final NotificationsDesAbsencesDInfirmiers notifications;

    public DeclarerUneAbsenceDInfirmier(UniteDeTravail uniteDeTravail, InfirmiersRepository infirmiers, AbsencesRepository absences,
                                        NouveauxIdentifiantsDuPlanning identifiants, NotificationsDesAbsencesDInfirmiers notifications) {
        this.uniteDeTravail = uniteDeTravail;
        this.infirmiers = infirmiers;
        this.absences = absences;
        this.identifiants = identifiants;
        this.notifications = notifications;
    }

    /**
     * @param auteur le compte qui saisit ; @param parAdministrateur la saisie vient de l'administrateur (et non du secrétariat)
     */
    public AbsenceLue executer(CentreId centre, InfirmierId infirmier, PeriodeDAbsence periode, TypeAbsence type, String motif,
                               CompteId auteur, boolean parAdministrateur) {
        AbsenceInfirmier.exigerUnMotifValide(motif);
        record Enregistree(AbsenceInfirmier absence, Infirmier fiche) {
        }
        Enregistree enregistree = uniteDeTravail.executer(() -> {
            Infirmier fiche = infirmiers.pourEcriture(centre, infirmier).orElseThrow(
                    () -> new RegleDePlanificationViolee("INFIRMIER_INTROUVABLE", "Infirmier introuvable"));
            fiche.exigerActifPourLePlanning();
            AbsencesSansChevauchement.verifier(absences, centre, infirmier, periode);
            AbsenceInfirmier absence = AbsenceInfirmier.declarer(identifiants.absence(), centre, infirmier, periode, type, motif, auteur);
            absences.enregistrer(absence);
            return new Enregistree(absence, fiche);
        });
        notifications.absenceEnregistree(centre.valeur(), enregistree.fiche().nomComplet(), periode.debut(), periode.fin(), !parAdministrateur);
        return AbsenceLue.depuis(enregistree.absence());
    }
}
