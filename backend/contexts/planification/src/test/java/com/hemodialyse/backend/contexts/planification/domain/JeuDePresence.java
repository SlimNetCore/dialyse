package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Presence.AbsenceConnue;
import com.hemodialyse.backend.contexts.planification.domain.Presence.AffectationPrevue;
import com.hemodialyse.backend.contexts.planification.domain.Presence.Creneau;
import com.hemodialyse.backend.contexts.planification.domain.Presence.DonneesDePresence;
import com.hemodialyse.backend.contexts.planification.domain.Presence.Fermeture;
import com.hemodialyse.backend.contexts.planification.domain.Presence.InfirmierDeLaPresence;
import com.hemodialyse.backend.contexts.planification.domain.Presence.Occupation;
import com.hemodialyse.backend.contexts.planification.domain.Presence.Personnel;
import com.hemodialyse.backend.contexts.planification.domain.Presence.PlanningDesPlaces;
import com.hemodialyse.backend.contexts.planification.domain.Presence.RemplacementPose;
import com.hemodialyse.backend.contexts.planification.domain.Presence.Salle;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Jeu de données de présence pour les tests du domaine : deux créneaux (matin, soir), trois salles (dont une d'isolement), une semaine
 * qui commence le dimanche 2026-10-25.
 */
final class JeuDePresence {
    static final LocalDate DIMANCHE = LocalDate.of(2026, 10, 25);
    static final LocalDate LUNDI = DIMANCHE.plusDays(1);
    static final LocalDate MARDI = DIMANCHE.plusDays(2);
    static final Salle SALLE = new Salle(UUID.randomUUID(), "Salle 1");
    static final Salle SALLE_B = new Salle(UUID.randomUUID(), "Salle 2");
    static final Salle ISO = new Salle(UUID.randomUUID(), "Isolement");
    static final Creneau MATIN = new Creneau(UUID.randomUUID(), "Matin", 1);
    static final Creneau SOIR = new Creneau(UUID.randomUUID(), "Soir", 2);

    private final List<Occupation> occupations = new ArrayList<>();
    private final List<InfirmierDeLaPresence> infirmiers = new ArrayList<>();
    private final List<AffectationPrevue> affectations = new ArrayList<>();
    private final List<AbsenceConnue> absences = new ArrayList<>();
    private final List<RemplacementPose> remplacements = new ArrayList<>();
    private final List<Fermeture> fermetures = new ArrayList<>();
    private Set<JourDeLaSemaine> ouverts = EnumSet.allOf(JourDeLaSemaine.class);
    private int ratio = 4;

    JeuDePresence ratio(int ratio) {
        this.ratio = ratio;
        return this;
    }

    JeuDePresence ouverts(JourDeLaSemaine... jours) {
        this.ouverts = EnumSet.copyOf(List.of(jours));
        return this;
    }

    JeuDePresence fermeture(LocalDate date) {
        fermetures.add(new Fermeture(date, "Férié"));
        return this;
    }

    JeuDePresence patients(int nombre, Salle salle, Creneau creneau, JourDeLaSemaine... jours) {
        for (int i = 0; i < nombre; i++) {
            occupations.add(new Occupation(salle.id(), creneau.id(), EnumSet.copyOf(List.of(jours)), null, null));
        }
        return this;
    }

    JeuDePresence patientSortiLe(Salle salle, Creneau creneau, LocalDate dernierJour, JourDeLaSemaine... jours) {
        occupations.add(new Occupation(salle.id(), creneau.id(), EnumSet.copyOf(List.of(jours)), null, dernierJour));
        return this;
    }

    JeuDePresence occupationAPartirDu(Salle salle, Creneau creneau, LocalDate premierJour, JourDeLaSemaine... jours) {
        occupations.add(new Occupation(salle.id(), creneau.id(), EnumSet.copyOf(List.of(jours)), premierJour, null));
        return this;
    }

    InfirmierDeLaPresence infirmier(String nom, String prenom, boolean habilite) {
        InfirmierDeLaPresence i = new InfirmierDeLaPresence(UUID.randomUUID(), nom, prenom, Qualification.INFIRMIER, habilite);
        infirmiers.add(i);
        return i;
    }

    InfirmierDeLaPresence infirmier(String nom, boolean habilite) {
        return infirmier(nom, null, habilite);
    }

    JeuDePresence affecter(InfirmierDeLaPresence i, Salle salle, Creneau creneau, JourDeLaSemaine... jours) {
        affectations.add(new AffectationPrevue(i.id(), salle.id(), creneau.id(), EnumSet.copyOf(List.of(jours))));
        return this;
    }

    JeuDePresence absence(InfirmierDeLaPresence i, LocalDate debut, LocalDate fin) {
        absences.add(new AbsenceConnue(i.id(), debut, fin, TypeAbsence.CONGE));
        return this;
    }

    JeuDePresence remplacement(InfirmierDeLaPresence i, LocalDate date, Salle salle, Creneau creneau) {
        remplacements.add(new RemplacementPose(UUID.randomUUID(), date, salle.id(), creneau.id(), i.id()));
        return this;
    }

    DonneesDePresence build() {
        PlanningDesPlaces planning = new PlanningDesPlaces(List.of(SALLE, SALLE_B, ISO), List.of(MATIN, SOIR), occupations, ouverts,
                new HashSet<>(Set.of(ISO.id())), fermetures);
        return new DonneesDePresence(planning, new Personnel(infirmiers, affectations, absences, remplacements,
                RatioDeLaPresence.depuisLaBase(ratio)));
    }
}
