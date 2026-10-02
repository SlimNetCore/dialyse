package com.hemodialyse.backend.domain.infirmier.service;

import com.hemodialyse.backend.domain.infirmier.model.AbsenceInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.AffectationInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.Presence.DonneesPresence;
import com.hemodialyse.backend.domain.infirmier.model.Presence.InfirmierRef;
import com.hemodialyse.backend.domain.infirmier.model.QualificationInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.RemplacementInfirmier;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.Planning.CreneauRef;
import com.hemodialyse.backend.domain.planning.model.Planning.DonneesPlanning;
import com.hemodialyse.backend.domain.planning.model.Planning.Fermeture;
import com.hemodialyse.backend.domain.planning.model.Planning.Occupation;
import com.hemodialyse.backend.domain.planning.model.Planning.SalleRef;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Jeu de données commun aux tests du planning de présence : deux salles (dont une d'isolement), deux créneaux et un
 * constructeur fluide. Semaine de référence : du dimanche 27/09/2026 au samedi 03/10/2026.
 */
final class PresenceTestData {

    static final LocalDate DIMANCHE = LocalDate.of(2026, 9, 27);
    static final LocalDate LUNDI = DIMANCHE.plusDays(1);
    static final LocalDate MARDI = DIMANCHE.plusDays(2);
    static final UUID CENTRE = UUID.randomUUID();
    static final SalleRef SALLE = new SalleRef(UUID.randomUUID(), "Salle A");
    static final SalleRef SALLE_B = new SalleRef(UUID.randomUUID(), "Salle B");
    static final SalleRef ISO = new SalleRef(UUID.randomUUID(), "Isolement");
    static final CreneauRef MATIN = new CreneauRef(UUID.randomUUID(), "Matin", 1);
    static final CreneauRef SOIR = new CreneauRef(UUID.randomUUID(), "Soir", 2);

    private final List<InfirmierRef> infirmiers = new ArrayList<>();
    private final List<AffectationInfirmier> affectations = new ArrayList<>();
    private final List<AbsenceInfirmier> absences = new ArrayList<>();
    private final List<RemplacementInfirmier> remplacements = new ArrayList<>();
    private final List<Occupation> occupations = new ArrayList<>();
    private final List<Fermeture> fermetures = new ArrayList<>();
    private Set<JourSemaine> ouverts = EnumSet.allOf(JourSemaine.class);
    private int ratio = 4;

    InfirmierRef infirmier(String nom, boolean habiliteIsolement) {
        InfirmierRef ref = new InfirmierRef(UUID.randomUUID(), nom, QualificationInfirmier.INFIRMIER, habiliteIsolement);
        infirmiers.add(ref);
        return ref;
    }

    PresenceTestData affecter(InfirmierRef infirmier, SalleRef salle, CreneauRef creneau, JourSemaine... jours) {
        affectations.add(AffectationInfirmier.creer(CENTRE, infirmier.id(), salle.id(), creneau.id(),
                EnumSet.copyOf(List.of(jours))));
        return this;
    }

    PresenceTestData patients(int nombre, SalleRef salle, CreneauRef creneau, JourSemaine... jours) {
        for (int i = 0; i < nombre; i++) {
            occupations.add(new Occupation(UUID.randomUUID(), salle.id(), creneau.id(), null,
                    EnumSet.copyOf(List.of(jours)), false));
        }
        return this;
    }

    PresenceTestData absence(InfirmierRef infirmier, LocalDate debut, LocalDate fin) {
        absences.add(AbsenceInfirmier.creer(CENTRE, infirmier.id(), debut, fin,
                com.hemodialyse.backend.domain.infirmier.model.TypeAbsence.CONGE, null));
        return this;
    }

    RemplacementInfirmier remplacer(LocalDate date, SalleRef salle, CreneauRef creneau, InfirmierRef remplacant) {
        RemplacementInfirmier r = RemplacementInfirmier.creer(CENTRE, date, salle.id(), creneau.id(), remplacant.id(), null);
        remplacements.add(r);
        return r;
    }

    PresenceTestData fermeture(LocalDate date) {
        fermetures.add(new Fermeture(date, "Férié"));
        return this;
    }

    PresenceTestData joursOuverts(Set<JourSemaine> jours) {
        this.ouverts = jours;
        return this;
    }

    PresenceTestData ratio(int ratio) {
        this.ratio = ratio;
        return this;
    }

    DonneesPresence build() {
        DonneesPlanning planning = new DonneesPlanning(List.of(SALLE, SALLE_B, ISO), List.of(MATIN, SOIR), List.of(),
                occupations, ouverts, Set.of(ISO.id()), fermetures);
        return new DonneesPresence(planning, ratio, infirmiers, affectations, absences, remplacements);
    }
}
