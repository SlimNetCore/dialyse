package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.PlaceTenue;
import com.hemodialyse.backend.contexts.planification.domain.VerrouDesPlaces;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.CachesDesPatients;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.NotificationsDeLaLiberation;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Libération nocturne des places échues (RG-PLN-080 à 083, job de 04:00 au fuseau du centre, APRÈS la détection des absences de
 * 02:30 qui a encore besoin des jours de dialyse de la veille). Pour chaque centre ACTIF, « aujourd'hui » est le jour du centre ;
 * chaque patient est libéré dans sa propre transaction : un patient en échec est rendu dans le résultat, SIGNALÉ à l'administrateur
 * (une alerte par centre et par jour), et les autres sont libérés ; la nuit suivante réessaie (le job est idempotent).
 */
public class LibererLesPlacesEchues {

    private final CentresActifs centres;
    private final HorlogeDesCentres horloge;
    private final PlacesTenues places;
    private final LibererUnePlace liberation;
    private final NotificationsDeLaLiberation notifications;

    public LibererLesPlacesEchues(CentresActifs centres, HorlogeDesCentres horloge, UniteDeTravail uniteDeTravail,
                                  VerrouDesPlaces verrou, PlacesTenues places, MouvementsDesPatients mouvements,
                                  CachesDesPatients caches, NotificationsDeLaLiberation notifications, Clock instant) {
        this.centres = centres;
        this.horloge = horloge;
        this.places = places;
        this.liberation = new LibererUnePlace(uniteDeTravail, verrou, places, mouvements, caches, instant);
        this.notifications = notifications;
    }

    private static String description(String etape, RuntimeException e) {
        return etape + " : " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " : " + e.getMessage());
    }

    public Bilan liberer() {
        List<CentreId> actifs;
        try {
            actifs = centres.liste();
        } catch (RuntimeException e) {
            return new Bilan(List.of(), description("liste des centres actifs", e));
        }
        List<ResultatDuCentre> resultats = new ArrayList<>();
        for (CentreId centre : actifs) {
            resultats.add(liberer(centre));
        }
        return new Bilan(resultats, null);
    }

    public ResultatDuCentre liberer(CentreId centre) {
        LocalDate jour;
        List<PlaceTenue> tenues;
        try {
            jour = horloge.aujourdhui(centre);
            tenues = places.duCentre(centre);
        } catch (RuntimeException e) {
            return new ResultatDuCentre(centre, null, 0, List.of(description("lecture du centre", e)), false);
        }
        int liberees = 0;
        List<String> incidents = new ArrayList<>();
        for (PlaceTenue tenue : tenues) {
            if (!tenue.estEchueLe(jour)) continue;
            try {
                if (liberation.liberer(centre, tenue.patient(), jour, jour)) liberees++;
            } catch (RuntimeException e) {
                incidents.add("patient " + tenue.patient().valeur() + " : " + description("libération", e));
            }
        }
        boolean alerte = false;
        if (!incidents.isEmpty()) {
            try {
                alerte = notifications.liberationEnEchec(centre.valeur(), jour, incidents.size());
            } catch (RuntimeException e) {
                incidents.add(description("alerte de l'administrateur", e));
            }
        }
        return new ResultatDuCentre(centre, jour, liberees, List.copyOf(incidents), alerte);
    }

    /**
     * @param liberees    places libérées
     * @param incidents   un incident par patient (ou par étape) en échec, vide si tout s'est bien passé
     * @param alerteEmise l'alerte « libération en échec » a été émise par cette exécution
     */
    public record ResultatDuCentre(CentreId centre, LocalDate jour, int liberees, List<String> incidents,
                                   boolean alerteEmise) {
    }

    /**
     * @param centres  un résultat par centre actif traité
     * @param incident cause de l'échec de la liste des centres actifs (aucun centre n'a alors été traité), nulle sinon
     */
    public record Bilan(List<ResultatDuCentre> centres, String incident) {
    }
}
