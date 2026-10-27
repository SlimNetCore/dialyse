package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre.Creneau;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre.Fermeture;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre.Generateur;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre.Salle;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Jeu de données des places pour les tests : un centre, deux salles ordinaires (A avec deux générateurs, B avec un), une salle
 * d'isolement (I avec deux générateurs), deux créneaux (matin, soir), ouvert tous les jours sauf le dimanche par défaut.
 */
public final class JeuDePlaces {

    public static final CentreId CENTRE = new CentreId(id(1));
    public static final SalleId A = new SalleId(id(10));
    public static final SalleId B = new SalleId(id(11));
    public static final SalleId ISOLEMENT = new SalleId(id(12));
    public static final CreneauId MATIN = new CreneauId(id(20));
    public static final CreneauId SOIR = new CreneauId(id(21));
    public static final GenerateurId A1 = new GenerateurId(id(30));
    public static final GenerateurId A2 = new GenerateurId(id(31));
    public static final GenerateurId B1 = new GenerateurId(id(32));
    public static final GenerateurId I1 = new GenerateurId(id(33));
    public static final GenerateurId I2 = new GenerateurId(id(34));
    public static final GenerateurId HORS_SERVICE = new GenerateurId(id(39));
    public static final Set<JourDeLaSemaine> OUVERTS = EnumSet.range(JourDeLaSemaine.LUNDI, JourDeLaSemaine.SAMEDI);

    private JeuDePlaces() {
    }

    public static UUID id(int n) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", n));
    }

    public static PatientId patient(int n) {
        return new PatientId(id(1000 + n));
    }

    public static RessourcesDuCentre ressources() {
        return ressources(OUVERTS, List.of());
    }

    public static RessourcesDuCentre ressources(Set<JourDeLaSemaine> ouverts, List<Fermeture> fermetures) {
        return new RessourcesDuCentre(
                List.of(new Salle(A, "Salle A", false), new Salle(B, "Salle B", false), new Salle(ISOLEMENT, "Isolement", true)),
                List.of(new Creneau(MATIN, "Matin", 1), new Creneau(SOIR, "Soir", 2)),
                List.of(new Generateur(A1, "G-A1", A), new Generateur(A2, "G-A2", A), new Generateur(B1, "G-B1", B),
                        new Generateur(I1, "G-I1", ISOLEMENT), new Generateur(I2, "G-I2", ISOLEMENT)),
                ouverts, fermetures);
    }

    public static Occupation occupation(PatientId patient, SalleId salle, CreneauId creneau, GenerateurId generateur,
                                        CategorieDIsolement categorie, JourDeLaSemaine... jours) {
        return new Occupation(patient, salle, creneau, generateur, jours.length == 0 ? Set.of() : EnumSet.copyOf(List.of(jours)),
                categorie);
    }

    public static Place place(SalleId salle, CreneauId creneau, GenerateurId generateur, JourDeLaSemaine... jours) {
        return new Place(salle, creneau, generateur, EnumSet.copyOf(List.of(jours)));
    }

    public static PlacesDuCentre places(Occupation... occupations) {
        return PlacesDuCentre.de(CENTRE, ressources(), List.of(occupations));
    }

    public static Fermeture fermeture(LocalDate date, String motif) {
        return new Fermeture(date, motif);
    }
}
