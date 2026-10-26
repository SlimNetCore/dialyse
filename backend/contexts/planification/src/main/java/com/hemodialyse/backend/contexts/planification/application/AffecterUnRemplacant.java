package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.publishedlanguage.CalendrierDuCentre;
import com.hemodialyse.backend.contexts.planification.domain.AbsencesRepository;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.Infirmier;
import com.hemodialyse.backend.contexts.planification.domain.InfirmiersRepository;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.RegleDePlanificationViolee;
import com.hemodialyse.backend.contexts.planification.domain.RemplacantEligible;
import com.hemodialyse.backend.contexts.planification.domain.Remplacement;
import com.hemodialyse.backend.contexts.planification.domain.RemplacementsRepository;
import com.hemodialyse.backend.contexts.planification.domain.RoulementsRepository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Affecte un remplaçant éligible sur une case (date, salle, créneau), éventuellement à la place d'un collègue (RG-INF-050 à 052).
 * Ordre des contrôles (décisions du 26/10/2026) : jamais soi-même (400), salle, créneau, date non passée, jour ouvert, remplacé
 * (existe, absent ou prévu sur la case), remplaçant éligible. La fiche du remplaçant est verrouillée pendant le jugement et l'écriture
 * (409 si concurrente, KB-PLN-29).
 */
public class AffecterUnRemplacant {
    private final UniteDeTravail uniteDeTravail;
    private final InfirmiersRepository infirmiers;
    private final RoulementsRepository roulements;
    private final AbsencesRepository absences;
    private final RemplacementsRepository remplacements;
    private final SallesDuCentre salles;
    private final CreneauxDuCentre creneaux;
    private final CalendrierDuCentre calendrier;
    private final HorlogeDesCentres horloge;
    private final NouveauxIdentifiantsDuPlanning identifiants;

    @SuppressWarnings("java:S107")
    public AffecterUnRemplacant(UniteDeTravail uniteDeTravail, InfirmiersRepository infirmiers, RoulementsRepository roulements,
                                AbsencesRepository absences, RemplacementsRepository remplacements, SallesDuCentre salles,
                                CreneauxDuCentre creneaux, CalendrierDuCentre calendrier, HorlogeDesCentres horloge,
                                NouveauxIdentifiantsDuPlanning identifiants) {
        this.uniteDeTravail = uniteDeTravail;
        this.infirmiers = infirmiers;
        this.roulements = roulements;
        this.absences = absences;
        this.remplacements = remplacements;
        this.salles = salles;
        this.creneaux = creneaux;
        this.calendrier = calendrier;
        this.horloge = horloge;
        this.identifiants = identifiants;
    }

    /**
     * @param remplace le collègue remplacé, facultatif
     */
    public RemplacementLu executer(CentreId centre, LocalDate date, SalleId salle, CreneauId creneau, InfirmierId remplacant,
                                   InfirmierId remplace) {
        Remplacement.personnesDistinctes(remplacant, remplace);
        CaseDuCentre.exiger(salles, creneaux, centre, salle, creneau);
        Remplacement.exigerUneDateDeTravail(date, horloge.aujourdhui(centre), calendrier.ouvert(centre.valeur(), date));
        JourDeLaSemaine jour = JourDeLaSemaine.de(date.getDayOfWeek());
        return uniteDeTravail.executer(() -> {
            if (remplace != null) {
                infirmiers.parId(centre, remplace).orElseThrow(
                        () -> new RegleDePlanificationViolee("INFIRMIER_INTROUVABLE", "Infirmier introuvable"));
                Remplacement.exigerUnRemplaceConcerne(absences.absentLe(centre, remplace, date),
                        roulements.de(centre, remplace).prevuSurLaCase(salle, creneau, jour));
            }
            Optional<Infirmier> fiche = infirmiers.pourEcriture(centre, remplacant);
            if (fiche.isEmpty()) {
                throw RemplacantEligible.nonEligible();
            }
            RemplacantEligible preuve = RemplacantEligible.prouver(new RemplacantEligible.Situation(remplacant, fiche.get().actif(),
                    fiche.get().habiliteIsolement(), salles.sallesDIsolement(centre).contains(salle),
                    absences.absentLe(centre, remplacant, date),
                    roulements.de(centre, remplacant).prevuSurLeCreneau(creneau, jour)
                            || remplacements.remplaceDeja(centre, remplacant, date, creneau)));
            Remplacement remplacement = Remplacement.affecter(identifiants.remplacement(), centre, date, salle, creneau, preuve, remplace);
            remplacements.enregistrer(remplacement);
            return RemplacementLu.depuis(remplacement);
        });
    }
}
