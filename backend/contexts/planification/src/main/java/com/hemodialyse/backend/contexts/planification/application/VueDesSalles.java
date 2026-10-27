package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;

import java.util.List;
import java.util.UUID;

/**
 * Port sortant (lecture seule) : la vue d'ensemble des salles d'un centre, composée par l'ACL à partir d'Organisation (salles, capacité)
 * et de GMAO (générateurs affectés, hors réformés et désactivés). Pas de cache : la réponse reflète le statut courant.
 */
public interface VueDesSalles {

    /** Les salles du centre triées par code, une page à la fois ; le total compte toutes les salles du centre. */
    Page lister(CentreId centre, Pagination pagination);

    record Page(List<SalleVue> items, long total) {
    }

    /** @param capacite nombre maximal de générateurs de la salle ; nul si illimité */
    record SalleVue(UUID id, String code, String nom, boolean isolement, Integer capacite, List<GenerateurVue> generateurs) {

        public SalleVue {
            generateurs = generateurs == null ? List.of() : List.copyOf(generateurs);
        }

        public int nombreDeGenerateurs() {
            return generateurs.size();
        }

        /** Places encore disponibles ; nul si la capacité est illimitée. */
        public Integer placesRestantes() {
            return capacite == null ? null : capacite - nombreDeGenerateurs();
        }

        /** La salle porte plus de générateurs que sa capacité (capacité réduite après affectation). */
        public boolean depassement() {
            return capacite != null && nombreDeGenerateurs() > capacite;
        }
    }

    record GenerateurVue(UUID id, String code, String designation, String statut) {
    }
}
