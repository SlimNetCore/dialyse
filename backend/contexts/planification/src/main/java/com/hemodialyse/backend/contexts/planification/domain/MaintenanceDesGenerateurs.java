package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.DonneesDOptimisation.IndisponibiliteDatee;
import com.hemodialyse.backend.contexts.planification.domain.DonneesDOptimisation.PatientAPlacer;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Service de domaine pur (RG-PLN-094, 096) : séances datées touchées par l'indisponibilité d'un générateur (intervention planifiée ou en
 * cours) et places réellement occupées à une date. Un déplacement temporaire déjà enregistré remplace, pour sa date, la place habituelle
 * du patient.
 */
public final class MaintenanceDesGenerateurs {

    private MaintenanceDesGenerateurs() {
    }

    /**
     * Séances, entre deux dates incluses, dont le générateur effectif est indisponible ce jour-là ; les jours où le centre est fermé ne
     * comptent pas.
     */
    public static List<SeanceImpactee> seancesImpactees(DonneesDOptimisation donnees, LocalDate du, LocalDate au) {
        Map<GenerateurId, String> codes = codes(donnees);
        Set<LocalDate> fermetures = fermetures(donnees);
        Set<JourDeLaSemaine> ouverts = donnees.joursOuverts();
        Map<String, DeplacementTemporaire> temporaires = temporaires(donnees);
        List<SeanceImpactee> impactees = new ArrayList<>();
        for (LocalDate date = du; !date.isAfter(au); date = date.plusDays(1)) {
            JourDeLaSemaine jour = JourDeLaSemaine.de(date.getDayOfWeek());
            if (!ouverts.contains(jour) || fermetures.contains(date)) continue;
            for (PatientAPlacer p : donnees.patients()) {
                if (!occupe(p) || !p.jours().contains(jour) || !occupeLe(p, date)) continue;
                Poste poste = posteEffectif(p, date, temporaires, codes);
                if (poste.generateur() == null) continue;
                Optional<IndisponibiliteDatee> indispo = indisponibilite(donnees, poste.generateur(), date);
                if (indispo.isPresent()) {
                    impactees.add(new SeanceImpactee(p.patient(), date, jour, poste, p.aRisque(), indispo.get().motif()));
                }
            }
        }
        return impactees;
    }

    /**
     * Places (« générateur|créneau ») occupées à une date, hors patients cités dans {@code exclus} (ceux qu'on déplace).
     */
    public static Set<String> postesOccupes(DonneesDOptimisation donnees, LocalDate date, Set<PatientId> exclus) {
        Map<GenerateurId, String> codes = codes(donnees);
        Map<String, DeplacementTemporaire> temporaires = temporaires(donnees);
        JourDeLaSemaine jour = JourDeLaSemaine.de(date.getDayOfWeek());
        Set<String> occupes = new HashSet<>();
        for (PatientAPlacer p : donnees.patients()) {
            if (!occupe(p) || exclus.contains(p.patient()) || !p.jours().contains(jour) || !occupeLe(p, date)) continue;
            Poste poste = posteEffectif(p, date, temporaires, codes);
            if (poste.generateur() != null) occupes.add(cle(poste.generateur(), poste.creneau()));
        }
        return occupes;
    }

    /**
     * Générateur indisponible à cette date ?
     */
    public static Optional<IndisponibiliteDatee> indisponibilite(DonneesDOptimisation donnees, GenerateurId generateur, LocalDate date) {
        return donnees.indisponibilites().stream().filter(i -> i.generateur().equals(generateur) && i.couvre(date)).findFirst();
    }

    public static String cle(GenerateurId generateur, CreneauId creneau) {
        return generateur.valeur() + "|" + (creneau == null ? null : creneau.valeur());
    }

    /**
     * Le patient tient une place de salle et de créneau, à des jours donnés.
     */
    private static boolean occupe(PatientAPlacer p) {
        return p.actuelle() != null && p.actuelle().salle() != null && p.actuelle().creneau() != null && !p.jours().isEmpty();
    }

    private static boolean occupeLe(PatientAPlacer p, LocalDate date) {
        return (p.premierJour() == null || !date.isBefore(p.premierJour())) && (p.dernierJour() == null || !date.isAfter(p.dernierJour()));
    }

    private static Poste posteEffectif(PatientAPlacer p, LocalDate date, Map<String, DeplacementTemporaire> temporaires,
                                       Map<GenerateurId, String> codes) {
        DeplacementTemporaire t = temporaires.get(p.patient().valeur() + "|" + date);
        if (t != null) return new Poste(t.salle(), t.creneau(), t.generateur(), codes.get(t.generateur()));
        Poste actuelle = p.actuelle();
        return new Poste(actuelle.salle(), actuelle.creneau(), actuelle.generateur(),
                actuelle.generateur() == null ? null : codes.get(actuelle.generateur()));
    }

    private static Map<GenerateurId, String> codes(DonneesDOptimisation donnees) {
        Map<GenerateurId, String> codes = new HashMap<>();
        donnees.ressources().generateurs().forEach(g -> codes.put(g.id(), g.code()));
        return codes;
    }

    private static Set<LocalDate> fermetures(DonneesDOptimisation donnees) {
        Set<LocalDate> dates = new HashSet<>();
        donnees.ressources().fermetures().forEach(f -> dates.add(f.date()));
        return dates;
    }

    private static Map<String, DeplacementTemporaire> temporaires(DonneesDOptimisation donnees) {
        Map<String, DeplacementTemporaire> parCle = new HashMap<>();
        for (DeplacementTemporaire t : donnees.temporaires()) parCle.put(t.patient().valeur() + "|" + t.date(), t);
        return parCle;
    }

    /**
     * Séance d'un patient à une date, sur sa place effective (habituelle ou temporaire déjà décidée).
     */
    public record SeanceImpactee(PatientId patient, LocalDate date, JourDeLaSemaine jour, Poste poste, boolean aRisque,
                                 String motif) {
    }
}
