package com.hemodialyse.backend.contexts.planification.doubles;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.publishedlanguage.CachesDesPatients;
import com.hemodialyse.backend.contexts.planification.application.CentresActifs;
import com.hemodialyse.backend.contexts.planification.application.HorlogeDesCentres;
import com.hemodialyse.backend.contexts.planification.application.MouvementsDesPatients;
import com.hemodialyse.backend.contexts.planification.application.PlacesTenues;
import com.hemodialyse.backend.contexts.planification.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.planification.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.PlaceTenue;
import com.hemodialyse.backend.contexts.planification.domain.PlaceTenue.PlaceLiberee;
import com.hemodialyse.backend.contexts.planification.domain.VerrouDesPlaces;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.NotificationsDeLaLiberation;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Le monde en mémoire de la libération des places : fiches avec place, mouvements, caches, centres, jour de chaque centre, alertes.
 * L'unité de travail annule les écritures si l'action échoue (comme une transaction). Pannes injectables par patient.
 */
public class LiberationEnMemoire implements UniteDeTravail, VerrouDesPlaces, PlacesTenues, MouvementsDesPatients, CachesDesPatients,
        CentresActifs, HorlogeDesCentres, NotificationsDeLaLiberation {

    public final Map<PatientId, PlaceTenue> fiches = new LinkedHashMap<>();
    public final List<PlaceLiberee> mouvements = new ArrayList<>();
    public final List<PatientId> cachesEvinces = new ArrayList<>();
    public final List<CentreId> centresActifs = new ArrayList<>();
    public final Map<CentreId, LocalDate> jours = new LinkedHashMap<>();
    public final Set<String> alertes = new HashSet<>();
    public final List<String> alertesEmises = new ArrayList<>();
    public final Set<PatientId> mouvementEnPanne = new HashSet<>();
    public final Set<CentreId> verrouTenuAilleurs = new HashSet<>();
    public boolean listeDesCentresEnPanne;

    // ── UniteDeTravail ──
    @Override
    public <T> T executer(Supplier<T> action) {
        Map<PatientId, PlaceTenue> fichesAvant = new LinkedHashMap<>(fiches);
        List<PlaceLiberee> mouvementsAvant = new ArrayList<>(mouvements);
        List<PatientId> cachesAvant = new ArrayList<>(cachesEvinces);
        try {
            return action.get();
        } catch (RuntimeException e) {
            fiches.clear();
            fiches.putAll(fichesAvant);
            mouvements.clear();
            mouvements.addAll(mouvementsAvant);
            cachesEvinces.clear();
            cachesEvinces.addAll(cachesAvant);
            throw e;
        }
    }

    // ── VerrouDesPlaces ──
    @Override
    public void prendre(CentreId centre) {
        if (verrouTenuAilleurs.contains(centre)) throw new EcritureConcurrente();
    }

    // ── PlacesTenues ──
    @Override
    public List<PlaceTenue> duCentre(CentreId centre) {
        return fiches.values().stream().filter(f -> f.centre().equals(centre) && f.place().estTenue())
                .sorted(java.util.Comparator.comparing(f -> f.patient().valeur())).toList();
    }

    @Override
    public Optional<PlaceTenue> de(CentreId centre, PatientId patient) {
        return Optional.ofNullable(fiches.get(patient)).filter(f -> f.centre().equals(centre) && f.place().estTenue());
    }

    @Override
    public Optional<PlaceTenue> verrouillee(CentreId centre, PatientId patient) {
        return de(centre, patient);
    }

    @Override
    public void liberer(PlaceLiberee liberee) {
        PlaceTenue avant = fiches.get(liberee.patient());
        fiches.put(liberee.patient(), new PlaceTenue(avant.centre(), avant.patient(), avant.etat(), avant.dernierJourDePlace(),
                new PlaceTenue.PlaceQuittee(null, null, null, Set.of())));
    }

    // ── MouvementsDesPatients ──
    @Override
    public boolean dejaTrace(PlaceLiberee liberee) {
        return !mouvements.isEmpty() && mouvements.get(mouvements.size() - 1).patient().equals(liberee.patient())
                && mouvements.get(mouvements.size() - 1).placeQuittee().equals(liberee.placeQuittee())
                && mouvements.get(mouvements.size() - 1).etat().equals(liberee.etat());
    }

    @Override
    public void tracer(PlaceLiberee liberee) {
        if (mouvementEnPanne.contains(liberee.patient())) throw new IllegalStateException("mouvement refusé");
        mouvements.add(liberee);
    }

    // ── CachesDesPatients ──
    @Override
    public void evincer(UUID centre, UUID patient) {
        cachesEvinces.add(new PatientId(patient));
    }

    // ── CentresActifs / HorlogeDesCentres ──
    @Override
    public List<CentreId> liste() {
        if (listeDesCentresEnPanne) throw new IllegalStateException("centres illisibles");
        return List.copyOf(centresActifs);
    }

    @Override
    public LocalDate aujourdhui(CentreId centre) {
        LocalDate jour = jours.get(centre);
        if (jour == null) throw new IllegalStateException("fuseau du centre illisible");
        return jour;
    }

    // ── NotificationsDeLaLiberation ──
    @Override
    public boolean liberationEnEchec(UUID centre, LocalDate jour, int nbPatients) {
        String cle = centre + "|" + jour;
        if (!alertes.add(cle)) return false;
        alertesEmises.add(cle + "|" + nbPatients);
        return true;
    }
}
