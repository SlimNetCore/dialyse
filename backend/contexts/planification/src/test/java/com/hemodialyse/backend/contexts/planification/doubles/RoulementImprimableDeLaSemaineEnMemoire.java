package com.hemodialyse.backend.contexts.planification.doubles;

import com.hemodialyse.backend.contexts.planification.application.LigneDuPlanningDePresence.Situation;
import com.hemodialyse.backend.contexts.planification.application.RoulementImprimableDeLaSemaine;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Double en mémoire du roulement imprimé d'une semaine (DOC5e, ADR 0197) : le même contrat que l'adapter JDBC. */
public class RoulementImprimableDeLaSemaineEnMemoire implements RoulementImprimableDeLaSemaine {

    private static final String[] JOURS = {"DIMANCHE", "LUNDI", "MARDI", "MERCREDI", "JEUDI", "VENDREDI", "SAMEDI"};

    private record Infirmier(CentreId centre, String nom, String prenom, boolean actif) {
        String complet() {
            return prenom == null ? nom : prenom + " " + nom;
        }
    }

    private record Affectation(CentreId centre, UUID infirmier, UUID salle, UUID creneau, String jours) {
    }

    private record Absence(CentreId centre, UUID infirmier, LocalDate debut, LocalDate fin) {
    }

    private record Remplacement(CentreId centre, UUID infirmier, LocalDate jour, UUID salle, UUID creneau) {
    }

    private final Map<UUID, Infirmier> infirmiers = new HashMap<>();
    private final List<Affectation> affectations = new ArrayList<>();
    private final List<Absence> absences = new ArrayList<>();
    private final List<Remplacement> remplacements = new ArrayList<>();

    public void infirmier(CentreId centre, UUID id, String nom, String prenom, boolean actif) {
        infirmiers.put(id, new Infirmier(centre, nom, prenom, actif));
    }

    public void affectation(CentreId centre, UUID infirmier, UUID salle, UUID creneau, String jours) {
        affectations.add(new Affectation(centre, infirmier, salle, creneau, jours));
    }

    public void absence(CentreId centre, UUID infirmier, LocalDate debut, LocalDate fin) {
        absences.add(new Absence(centre, infirmier, debut, fin));
    }

    public void remplacement(CentreId centre, UUID infirmier, LocalDate jour, UUID salle, UUID creneau) {
        remplacements.add(new Remplacement(centre, infirmier, jour, salle, creneau));
    }

    @Override
    public List<LigneDuRoulement> lignes(CentreId centre, LocalDate debutDeSemaine) {
        List<LigneDuRoulement> resultat = new ArrayList<>();
        for (int rang = 0; rang < JOURS.length; rang++) {
            LocalDate jour = debutDeSemaine.plusDays(rang);
            String nomDuJour = JOURS[rang];
            for (Affectation a : affectations) {
                Infirmier i = infirmiers.get(a.infirmier());
                if (!a.centre().equals(centre) || i == null || !i.centre().equals(centre) || !i.actif() || !a.jours().contains(nomDuJour)) continue;
                boolean absent = absences.stream().anyMatch(b -> b.centre().equals(a.centre()) && b.infirmier().equals(a.infirmier())
                        && !jour.isBefore(b.debut()) && !jour.isAfter(b.fin()));
                resultat.add(new LigneDuRoulement(jour, a.salle(), a.creneau(), i.complet(), absent ? Situation.ABSENT : Situation.PREVU));
            }
            for (Remplacement r : remplacements) {
                Infirmier i = infirmiers.get(r.infirmier());
                if (!r.centre().equals(centre) || i == null || !i.centre().equals(centre) || !r.jour().equals(jour)) continue;
                resultat.add(new LigneDuRoulement(jour, r.salle(), r.creneau(), i.complet(), Situation.REMPLACANT));
            }
        }
        return resultat;
    }
}
