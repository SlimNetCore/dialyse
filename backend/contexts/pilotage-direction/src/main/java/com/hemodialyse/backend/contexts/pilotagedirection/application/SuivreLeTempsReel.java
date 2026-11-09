package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.Changement;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.ConstatDesIndicateurs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Temps réel du tableau de bord de la direction : détecte, société par société, tout changement des indicateurs affichés et le diffuse à la
 * direction connectée ; chaque recalcul alimente aussi l'historique des alertes (KB-DIR-04 : pas de direction connectée, pas d'historique).
 * <p>
 * La détection compare des constats recalculés (et non des événements applicatifs isolés) : elle couvre donc toute modification, quel qu'en
 * soit le chemin (saisie, import, transfert, tâche planifiée). Les signaux d'un centre ne servent qu'à déclencher le recalcul immédiatement
 * plutôt qu'au prochain balayage. Seules les sociétés qui ont au moins un abonné sont recalculées.
 */
public final class SuivreLeTempsReel {

    /** Nombre maximal de changements par diffusion (le client relit de toute façon les données). */
    public static final int CHANGEMENTS_MAX = 60;

    private static final Logger journal = LoggerFactory.getLogger(SuivreLeTempsReel.class);

    private final ConstatsDuTableauDeBord constats;
    private final EnregistrerLesAlertes alertes;
    private final SocieteDUnCentre societes;
    private final DiffusionDesChangements diffusion;
    private final HorlogeDuPilotage horloge;

    private final Map<UUID, ConstatDesIndicateurs> dernier = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> abonnes = new ConcurrentHashMap<>();
    private final Set<UUID> asignaler = ConcurrentHashMap.newKeySet();
    private final Map<UUID, UUID> societeParCentre = new ConcurrentHashMap<>();

    public SuivreLeTempsReel(ConstatsDuTableauDeBord constats, EnregistrerLesAlertes alertes, SocieteDUnCentre societes,
                             DiffusionDesChangements diffusion, HorlogeDuPilotage horloge) {
        this.constats = constats;
        this.alertes = alertes;
        this.societes = societes;
        this.diffusion = diffusion;
        this.horloge = horloge;
    }

    public void abonneAjoute(UUID societeId) {
        abonnes.merge(societeId, 1, Integer::sum);
        signaler(societeId); // établit le constat de référence dès la première connexion
    }

    public void abonneRetire(UUID societeId) {
        abonnes.computeIfPresent(societeId, (k, n) -> n <= 1 ? null : n - 1);
        if (!abonnes.containsKey(societeId)) {
            dernier.remove(societeId);
        }
    }

    public boolean estSuivie(UUID societeId) {
        return abonnes.containsKey(societeId);
    }

    /** Une donnée d'un centre vient de changer : sa société sera recalculée au prochain passage rapide (sans effet si aucun abonné). */
    public void unCentreAChange(UUID centreId) {
        if (abonnes.isEmpty() || centreId == null) return;
        UUID societeId = societeParCentre.get(centreId);
        if (societeId == null) {
            societeId = societes.de(centreId).orElse(null);
            if (societeId != null) societeParCentre.put(centreId, societeId);
        }
        if (societeId != null) {
            signaler(societeId);
        }
    }

    private void signaler(UUID societeId) {
        if (societeId != null && abonnes.containsKey(societeId)) {
            asignaler.add(societeId);
        }
    }

    /** Recalcule les sociétés signalées (appelé chaque seconde). */
    public void traiterLesSignalements() {
        for (UUID societeId : List.copyOf(asignaler)) {
            asignaler.remove(societeId);
            rafraichir(societeId);
        }
    }

    /** Balayage de sécurité : recalcule toutes les sociétés suivies (couvre les modifications sans signal). */
    public void balayer() {
        for (UUID societeId : List.copyOf(abonnes.keySet())) {
            rafraichir(societeId);
        }
    }

    /**
     * Recalcule le constat de la société et diffuse les changements depuis le précédent.
     *
     * @return les changements constatés (vide au premier calcul ou sans changement, ou si le recalcul échoue)
     */
    public List<Changement> rafraichir(UUID societeId) {
        try {
            var lecture = constats.lire(societeId);
            alertes.execute(societeId, lecture.alertes());
            ConstatDesIndicateurs courant = lecture.constat();
            ConstatDesIndicateurs precedent = dernier.put(societeId, courant);
            if (precedent == null) {
                return List.of();
            }
            List<Changement> changements = courant.changementsDepuis(precedent);
            if (!changements.isEmpty()) {
                List<Changement> plafonnes = changements.size() > CHANGEMENTS_MAX ? changements.subList(0, CHANGEMENTS_MAX) : changements;
                diffusion.diffuser(societeId, horloge.maintenant().toInstant(), List.copyOf(plafonnes));
            }
            return changements;
        } catch (RuntimeException e) {
            journal.warn("Recalcul temps réel de la société {} impossible : {}", societeId, e.getMessage());
            return List.of();
        }
    }
}
