package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.publishedlanguage.NotificationsDesAbsencesDInfirmiers;
import com.hemodialyse.backend.contexts.planification.domain.AbsenceInfirmier;
import com.hemodialyse.backend.contexts.planification.domain.AbsencesRepository;
import com.hemodialyse.backend.contexts.planification.domain.AbsencesSansChevauchement;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CompteId;
import com.hemodialyse.backend.contexts.planification.domain.Infirmier;
import com.hemodialyse.backend.contexts.planification.domain.InfirmiersRepository;
import com.hemodialyse.backend.contexts.planification.domain.PeriodeDAbsence;
import com.hemodialyse.backend.contexts.planification.domain.TypeAbsence;

/**
 * L'infirmier connecté déclare une absence à venir ou en cours (RG-INF-032). Ordre des contrôles : entrées illisibles (400), fiche
 * liée ({@code INFIRMIER_NON_LIE}), actif, absence non passée ({@code ABSENCE_PASSEE}, au jour du centre), non chevauchante. L'administration,
 * le secrétariat et le médecin sont prévenus après l'enregistrement.
 */
public class DeclarerMonAbsence {
    private final UniteDeTravail uniteDeTravail;
    private final InfirmiersRepository infirmiers;
    private final AbsencesRepository absences;
    private final NouveauxIdentifiantsDuPlanning identifiants;
    private final NotificationsDesAbsencesDInfirmiers notifications;
    private final HorlogeDesCentres horloge;

    @SuppressWarnings("java:S107")
    public DeclarerMonAbsence(UniteDeTravail uniteDeTravail, InfirmiersRepository infirmiers, AbsencesRepository absences,
                              NouveauxIdentifiantsDuPlanning identifiants, NotificationsDesAbsencesDInfirmiers notifications,
                              HorlogeDesCentres horloge) {
        this.uniteDeTravail = uniteDeTravail;
        this.infirmiers = infirmiers;
        this.absences = absences;
        this.identifiants = identifiants;
        this.notifications = notifications;
        this.horloge = horloge;
    }

    public AbsenceLue executer(CentreId centre, CompteId compte, PeriodeDAbsence periode, TypeAbsence type, String motif) {
        AbsenceInfirmier.exigerUnMotifValide(motif);
        Infirmier fiche = FicheLiee.de(infirmiers, centre, compte);
        AbsenceInfirmier absence = uniteDeTravail.executer(() -> {
            Infirmier verrouillee = infirmiers.pourEcriture(centre, fiche.id()).orElseThrow(() -> FicheLiee.nonLie());
            if (verrouillee.compte().filter(compte::equals).isEmpty()) {
                throw FicheLiee.nonLie(); // le compte a été délié (P2) entre la lecture de la fiche et son verrou
            }
            verrouillee.exigerActifPourLePlanning();
            AbsenceInfirmier declaree = AbsenceInfirmier.declarerParLInfirmier(identifiants.absence(), centre, fiche.id(), periode, type,
                    motif, compte, horloge.aujourdhui(centre));
            AbsencesSansChevauchement.verifier(absences, centre, fiche.id(), periode);
            absences.enregistrer(declaree);
            return declaree;
        });
        notifications.absenceDeclareeParLInfirmier(centre.valeur(), fiche.nomComplet(), periode.debut(), periode.fin());
        return AbsenceLue.depuis(absence);
    }
}
