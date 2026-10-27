package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre.Fermeture;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre.Generateur;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Index d'occupation (interne au domaine) : générateurs occupés par jour et créneau, places de salle prises sans générateur
 * précis (générateur inconnu, hors service ou d'une autre salle), générateurs dédiés à des patients à risque ou servant des
 * patients sans risque (RG-PLN-021, 022, 024). Un générateur ne sert qu'un patient par jour et par créneau.
 */
final class IndexDOccupation {

    private final RessourcesDuCentre ressources;
    private final Map<SalleId, List<Generateur>> generateursParSalle = new HashMap<>();
    private final Map<String, Integer> generateursOccupes = new HashMap<>();
    private final Map<String, Integer> sansGenerateur = new HashMap<>();
    private final Set<String> generateurJourCreneauOccupe = new HashSet<>();
    private final Set<GenerateurId> generateursARisque = new HashSet<>();
    private final Set<GenerateurId> generateursSansRisque = new HashSet<>();

    IndexDOccupation(RessourcesDuCentre ressources, Collection<Occupation> occupations) {
        this.ressources = ressources;
        Map<GenerateurId, Generateur> operationnels = new HashMap<>();
        for (Generateur g : ressources.generateurs()) {
            operationnels.put(g.id(), g);
            generateursParSalle.computeIfAbsent(g.salle(), k -> new ArrayList<>()).add(g);
        }
        generateursParSalle.values().forEach(l -> l.sort(Comparator.comparing(Generateur::code)));

        for (Occupation o : occupations) {
            Generateur g = o.generateur() == null ? null : operationnels.get(o.generateur());
            boolean surGenerateurDeLaSalle = g != null && g.salle().equals(o.salle());
            if (surGenerateurDeLaSalle) {
                (o.categorie().aRisque() ? generateursARisque : generateursSansRisque).add(g.id());
            }
            if (o.salle() == null || o.creneau() == null) continue;
            for (JourDeLaSemaine jour : o.jours()) {
                String caseKey = caseKey(o.salle(), o.creneau(), jour);
                if (surGenerateurDeLaSalle && generateurJourCreneauOccupe.add(genKey(g.id(), o.creneau(), jour))) {
                    generateursOccupes.merge(caseKey, 1, Integer::sum);
                } else if (!surGenerateurDeLaSalle) {
                    sansGenerateur.merge(caseKey, 1, Integer::sum);
                }
            }
        }
    }

    private static String caseKey(SalleId salle, CreneauId creneau, JourDeLaSemaine jour) {
        return salle.valeur() + "|" + creneau.valeur() + "|" + jour;
    }

    private static String genKey(GenerateurId generateur, CreneauId creneau, JourDeLaSemaine jour) {
        return generateur.valeur() + "|" + creneau.valeur() + "|" + jour;
    }

    RessourcesDuCentre ressources() {
        return ressources;
    }

    Set<JourDeLaSemaine> joursOuverts() {
        return ressources.joursOuverts();
    }

    List<Fermeture> fermetures() {
        return ressources.fermetures();
    }

    List<Generateur> generateursDe(SalleId salle) {
        return generateursParSalle.getOrDefault(salle, List.of());
    }

    boolean estIsolement(SalleId salle) {
        return ressources.estIsolement(salle);
    }

    /** Un générateur ne mélange jamais patients à risque et patients sans risque. */
    boolean generateurCompatible(Generateur g, boolean aRisque) {
        return aRisque ? !generateursSansRisque.contains(g.id()) : !generateursARisque.contains(g.id());
    }

    int libres(SalleId salle, CreneauId creneau, JourDeLaSemaine jour) {
        String key = caseKey(salle, creneau, jour);
        int capacite = generateursDe(salle).size();
        int occupes = generateursOccupes.getOrDefault(key, 0) + sansGenerateur.getOrDefault(key, 0);
        return Math.max(0, capacite - occupes);
    }

    boolean generateurLibre(Generateur g, CreneauId creneau, JourDeLaSemaine jour) {
        return !generateurJourCreneauOccupe.contains(genKey(g.id(), creneau, jour)) && libres(g.salle(), creneau, jour) > 0;
    }

    double ratioLibreMoyen(SalleId salle, CreneauId creneau, Set<JourDeLaSemaine> jours) {
        int capacite = generateursDe(salle).size();
        if (capacite == 0 || jours.isEmpty()) return 0;
        double somme = 0;
        for (JourDeLaSemaine jour : jours) somme += (double) libres(salle, creneau, jour) / capacite;
        return somme / jours.size();
    }
}
