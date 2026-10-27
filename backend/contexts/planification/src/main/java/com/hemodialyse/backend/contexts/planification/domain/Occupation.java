package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Ce qu'occupe aujourd'hui un patient actif du centre. Peut être <b>incomplète</b> (placement hérité : salle sans générateur,
 * jours sans salle...) : elle compte alors comme une place de salle sans générateur précis (RG-PLN-022). Les patients sortis et
 * les séjours terminés ou non commencés sont écartés par le chargement (RG-PLN-010).
 *
 * @param categorie catégorie issue des sérologies du patient (RG-PLN-012)
 */
public record Occupation(PatientId patient, SalleId salle, CreneauId creneau, GenerateurId generateur,
                         Set<JourDeLaSemaine> jours, CategorieDIsolement categorie) {

    public Occupation {
        if (patient == null) throw new SaisieInvalide("Patient requis");
        jours = jours == null || jours.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(jours));
        categorie = categorie == null ? CategorieDIsolement.SANS_RISQUE : categorie;
    }

    public boolean estVide() {
        return salle == null && creneau == null && generateur == null && jours.isEmpty();
    }

    public boolean estComplete() {
        return salle != null && creneau != null && generateur != null && !jours.isEmpty();
    }

    public boolean correspondA(Place place) {
        return place.salle().equals(salle) && place.creneau().equals(creneau) && place.generateur().equals(generateur)
                && place.jours().equals(jours);
    }
}
