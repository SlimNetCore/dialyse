package com.hemodialyse.backend.infrastructure.optimisation.timefold;

import com.hemodialyse.backend.domain.infirmier.model.AbsenceInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.AffectationInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.Presence.DonneesPresence;
import com.hemodialyse.backend.domain.infirmier.model.Presence.InfirmierRef;
import com.hemodialyse.backend.domain.infirmier.model.QualificationInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.RemplacementInfirmier;
import com.hemodialyse.backend.domain.infirmier.model.TypeAbsence;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.Planning.CreneauRef;
import com.hemodialyse.backend.domain.planning.model.Planning.DonneesPlanning;
import com.hemodialyse.backend.domain.planning.model.Planning.Fermeture;
import com.hemodialyse.backend.domain.planning.model.Planning.GenerateurRef;
import com.hemodialyse.backend.domain.planning.model.Planning.Occupation;
import com.hemodialyse.backend.domain.planning.model.Planning.SalleRef;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation.PatientAPlacer;
import com.hemodialyse.backend.domain.planning.optimisation.model.Poste;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Centre de test : salles, créneaux, générateurs, patients, infirmiers. Semaine de référence : dimanche 27/09/2026.
 */
public final class OptimisationFixture {

    public static final LocalDate DIMANCHE = LocalDate.of(2026, 9, 27);
    public static final UUID CENTRE = UUID.randomUUID();

    public final List<SalleRef> salles = new ArrayList<>();
    public final List<CreneauRef> creneaux = new ArrayList<>();
    public final List<GenerateurRef> generateurs = new ArrayList<>();
    public final List<PatientAPlacer> patients = new ArrayList<>();
    public final List<InfirmierRef> infirmiers = new ArrayList<>();
    public final List<AffectationInfirmier> affectations = new ArrayList<>();
    public final List<AbsenceInfirmier> absences = new ArrayList<>();
    public final List<RemplacementInfirmier> remplacements = new ArrayList<>();
    public final List<Fermeture> fermetures = new ArrayList<>();
    public final Set<UUID> isolement = new HashSet<>();
    public Set<JourSemaine> ouverts = EnumSet.allOf(JourSemaine.class);
    public int ratio = 4;

    public SalleRef salle(String nom, int nbGenerateurs) {
        SalleRef salle = new SalleRef(UUID.randomUUID(), nom);
        salles.add(salle);
        for (int i = 1; i <= nbGenerateurs; i++) {
            generateurs.add(new GenerateurRef(UUID.randomUUID(), nom.charAt(nom.length() - 1) + "-G" + i, salle.id()));
        }
        return salle;
    }

    public SalleRef salleIsolement(String nom, int nbGenerateurs) {
        SalleRef salle = salle(nom, nbGenerateurs);
        isolement.add(salle.id());
        return salle;
    }

    public CreneauRef creneau(String libelle) {
        CreneauRef creneau = new CreneauRef(UUID.randomUUID(), libelle, creneaux.size() + 1);
        creneaux.add(creneau);
        return creneau;
    }

    public GenerateurRef generateur(SalleRef salle, int rang) {
        return generateurs.stream().filter(g -> g.salleId().equals(salle.id())).skip(rang).findFirst().orElseThrow();
    }

    /**
     * Patient placé sur un générateur précis (ou sans générateur si {@code generateur} est nul).
     */
    public PatientAPlacer patient(String nom, boolean aRisque, SalleRef salle, CreneauRef creneau, GenerateurRef generateur,
                                  JourSemaine... jours) {
        Poste poste = salle == null ? null : new Poste(salle.id(), creneau.id(),
                generateur == null ? null : generateur.id(), generateur == null ? null : generateur.code());
        PatientAPlacer p = new PatientAPlacer(UUID.randomUUID(), nom, EnumSet.copyOf(List.of(jours)), aRisque, poste, null, null);
        patients.add(p);
        return p;
    }

    public PatientAPlacer patientNonPlace(String nom, boolean aRisque, JourSemaine... jours) {
        return patient(nom, aRisque, null, null, null, jours);
    }

    public InfirmierRef infirmier(String nom, QualificationInfirmier qualification, boolean habiliteIsolement) {
        InfirmierRef ref = new InfirmierRef(UUID.randomUUID(), nom, qualification, habiliteIsolement);
        infirmiers.add(ref);
        return ref;
    }

    public InfirmierRef infirmier(String nom) {
        return infirmier(nom, QualificationInfirmier.INFIRMIER, false);
    }

    public OptimisationFixture affecter(InfirmierRef infirmier, SalleRef salle, CreneauRef creneau, JourSemaine... jours) {
        affectations.add(AffectationInfirmier.creer(CENTRE, infirmier.id(), salle.id(), creneau.id(),
                EnumSet.copyOf(List.of(jours))));
        return this;
    }

    public OptimisationFixture absence(InfirmierRef infirmier, LocalDate debut, LocalDate fin) {
        absences.add(AbsenceInfirmier.creer(CENTRE, infirmier.id(), debut, fin, TypeAbsence.CONGE, null));
        return this;
    }

    public DonneesOptimisation build() {
        List<Occupation> occupations = new ArrayList<>();
        for (PatientAPlacer p : patients) {
            if (p.actuelle() != null) occupations.add(p.versOccupation(p.actuelle()));
        }
        DonneesPlanning planning = new DonneesPlanning(List.copyOf(salles), List.copyOf(creneaux), List.copyOf(generateurs),
                occupations, ouverts, Set.copyOf(isolement), List.copyOf(fermetures));
        return new DonneesOptimisation(new DonneesPresence(planning, ratio, List.copyOf(infirmiers),
                List.copyOf(affectations), List.copyOf(absences), List.copyOf(remplacements)), List.copyOf(patients));
    }
}
