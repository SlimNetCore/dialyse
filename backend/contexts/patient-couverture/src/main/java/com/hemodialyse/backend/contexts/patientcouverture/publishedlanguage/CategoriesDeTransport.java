package com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage;

import java.util.List;
import java.util.UUID;

/**
 * Langage publié {@code patientcouverture.v1} : les catégories de transport d'un centre (RF1), en lecture seule. Aucun écrivain
 * dans l'application aujourd'hui : les lignes viennent de la base (KB-RF1-02).
 */
public interface CategoriesDeTransport {

    /** Les catégories du centre, par libellé. */
    List<CategorieDeTransportPubliee> lister(UUID centre);

    record CategorieDeTransportPubliee(UUID id, String libelle) {
    }
}
