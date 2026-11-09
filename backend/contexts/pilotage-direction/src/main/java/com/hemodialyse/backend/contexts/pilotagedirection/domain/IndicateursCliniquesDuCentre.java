package com.hemodialyse.backend.contexts.pilotagedirection.domain;

/**
 * Les indicateurs cliniques d'un centre (ou des centres consolidés) sur la période : cinq marqueurs face à leur cible, sérologies positives,
 * observance et greffes. Effectifs et taux sont déjà masqués par le Dossier médical (voir {@link MarqueurClinique}).
 * <p>
 * {@code observanceEnRetard} est la valeur BRUTE (au moins un patient en retard) que la politique d'alertes lit : l'effectif
 * {@code patientsObservanceEnRetard} est, lui, masqué, mais une alerte ne désigne personne. Elle n'est pas publiée.
 */
public record IndicateursCliniquesDuCentre(MarqueurClinique ktV, MarqueurClinique hemoglobine, MarqueurClinique phosphore, MarqueurClinique pth,
                                           MarqueurClinique albumine, Long vhbPositifs, Long vhcPositifs, Long vihPositifs,
                                           Long patientsObservanceEnRetard, boolean observanceEnRetard,
                                           Long greffeListeAttente, Long greffeBilanEnCours, Long greffesPeriode) {
}
