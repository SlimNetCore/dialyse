package com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Langage publié {@code patientcouverture.v1} : les transporteurs d'un centre (RF1), pour les contextes qui les nomment (séance,
 * planification, documents). Lecture seule, toujours bornée au centre indiqué ; aucune règle d'accès (le consommateur a déjà
 * garanti le centre).
 */
public interface Transporteurs {

    /** Les transporteurs du centre, par nom. */
    List<TransporteurPublie> lister(UUID centre);

    /** Le nom du transporteur, s'il est de ce centre. */
    Optional<String> nom(UUID centre, UUID id);

    /** Ce transporteur existe-t-il dans ce centre ? */
    boolean existe(UUID centre, UUID id);

    record TransporteurPublie(UUID id, String nom) {
    }
}
