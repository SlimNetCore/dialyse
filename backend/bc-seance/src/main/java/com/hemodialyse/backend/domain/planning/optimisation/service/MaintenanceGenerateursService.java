package com.hemodialyse.backend.domain.planning.optimisation.service;

import com.hemodialyse.backend.domain.planning.model.DeplacementTemporaire;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.Planning.Fermeture;
import com.hemodialyse.backend.domain.planning.model.Planning.GenerateurRef;
import com.hemodialyse.backend.domain.planning.model.Planning.Occupation;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.IndisponibiliteGenerateur;
import com.hemodialyse.backend.domain.planning.optimisation.model.Poste;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Service de domaine pur : séances datées touchées par l'indisponibilité d'un générateur (intervention GMAO planifiée
 * ou en cours) et places réellement occupées à une date. Un déplacement temporaire déjà enregistré remplace, pour sa
 * date, la place habituelle du patient.
 */
public final class MaintenanceGenerateursService {

    private MaintenanceGenerateursService() {
    }

    /**
     * Séance d'un patient à une date, sur sa place effective (habituelle ou temporaire déjà décidée).
     */
    public record SeanceImpactee(UUID patientId, LocalDate date, JourSemaine jour, Poste poste, boolean aRisque,
                                 String motif) {
    }

    /**
     * Séances, entre deux dates incluses, dont le générateur effectif est indisponible ce jour-là ; les jours où le
     * centre est fermé ne comptent pas.
     */
    public static List<SeanceImpactee> seancesImpactees(DonneesOptimisation donnees, LocalDate du, LocalDate au) {
        Map<UUID, String> codes = codes(donnees);
        Set<LocalDate> fermetures = fermetures(donnees);
        Set<JourSemaine> ouverts = donnees.joursOuverts();
        Map<String, DeplacementTemporaire> temporaires = temporaires(donnees);
        List<SeanceImpactee> impactees = new ArrayList<>();
        for (LocalDate date = du; !date.isAfter(au); date = date.plusDays(1)) {
            JourSemaine jour = JourSemaine.de(date.getDayOfWeek());
            if (!ouverts.contains(jour) || fermetures.contains(date)) continue;
            for (Occupation o : donnees.planning().occupations()) {
                if (!o.jours().contains(jour) || !o.occupeLe(date)) continue;
                Poste poste = posteEffectif(o, date, temporaires, codes);
                if (poste.generateurId() == null) continue;
                Optional<IndisponibiliteGenerateur> indispo = indisponibilite(donnees, poste.generateurId(), date);
                if (indispo.isPresent()) {
                    impactees.add(new SeanceImpactee(o.patientId(), date, jour, poste, o.aRisque(),
                            indispo.get().motif()));
                }
            }
        }
        return impactees;
    }

    /**
     * Places (« générateur|créneau ») occupées à une date, hors patients cités dans {@code exclus} (ceux qu'on déplace).
     */
    public static Set<String> postesOccupes(DonneesOptimisation donnees, LocalDate date, Set<UUID> exclus) {
        Map<UUID, String> codes = codes(donnees);
        Map<String, DeplacementTemporaire> temporaires = temporaires(donnees);
        JourSemaine jour = JourSemaine.de(date.getDayOfWeek());
        Set<String> occupes = new HashSet<>();
        for (Occupation o : donnees.planning().occupations()) {
            if (exclus.contains(o.patientId()) || !o.jours().contains(jour) || !o.occupeLe(date)) continue;
            Poste poste = posteEffectif(o, date, temporaires, codes);
            if (poste.generateurId() != null) occupes.add(cle(poste.generateurId(), poste.creneauId()));
        }
        return occupes;
    }

    /**
     * Générateur indisponible à cette date ?
     */
    public static Optional<IndisponibiliteGenerateur> indisponibilite(DonneesOptimisation donnees, UUID generateurId,
                                                                      LocalDate date) {
        return donnees.indisponibilites().stream()
                .filter(i -> i.generateurId().equals(generateurId) && i.couvre(date)).findFirst();
    }

    public static String cle(UUID generateurId, UUID creneauId) {
        return generateurId + "|" + creneauId;
    }

    private static Poste posteEffectif(Occupation o, LocalDate date, Map<String, DeplacementTemporaire> temporaires,
                                       Map<UUID, String> codes) {
        DeplacementTemporaire t = temporaires.get(o.patientId() + "|" + date);
        if (t != null) return new Poste(t.salleId(), t.creneauId(), t.generateurId(), codes.get(t.generateurId()));
        return new Poste(o.salleId(), o.creneauId(), o.generateurId(),
                o.generateurId() == null ? null : codes.get(o.generateurId()));
    }

    private static Map<UUID, String> codes(DonneesOptimisation donnees) {
        Map<UUID, String> codes = new HashMap<>();
        for (GenerateurRef g : donnees.planning().generateurs()) codes.put(g.id(), g.code());
        return codes;
    }

    private static Set<LocalDate> fermetures(DonneesOptimisation donnees) {
        Set<LocalDate> dates = new HashSet<>();
        if (donnees.planning().fermetures() != null) {
            for (Fermeture f : donnees.planning().fermetures()) dates.add(f.date());
        }
        return dates;
    }

    private static Map<String, DeplacementTemporaire> temporaires(DonneesOptimisation donnees) {
        Map<String, DeplacementTemporaire> parCle = new HashMap<>();
        for (DeplacementTemporaire t : donnees.temporaires()) parCle.put(t.patientId() + "|" + t.date(), t);
        return parCle;
    }
}
