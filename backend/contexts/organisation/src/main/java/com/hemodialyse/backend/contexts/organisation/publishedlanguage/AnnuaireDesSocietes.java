package com.hemodialyse.backend.contexts.organisation.publishedlanguage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Langage publié d'Organisation (organisation.v1, O4 lot A, ADR 0107) : les sociétés et le rattachement des centres à leur société.
 * Lecture seule.
 */
public interface AnnuaireDesSocietes {

    /** Les identifiants des sociétés actives ({@code actif} vrai ; une société sans état renseigné n'est pas active), ordre non spécifié. */
    List<UUID> societesActives();

    /** La société d'un centre ; vide si le centre est inconnu. */
    Optional<UUID> societeDuCentre(UUID centre);

    /** Une société avec ses centres (par nom croissant) ; vide si la société est inconnue. */
    Optional<SocieteAvecSesCentres> societe(UUID societe);

    record SocieteAvecSesCentres(UUID id, String raisonSociale, List<CentreDeLaSociete> centres) {
        public SocieteAvecSesCentres {
            centres = List.copyOf(centres);
        }
    }

    /** Un centre d'une société ; {@code actif} est vrai tant que le centre n'a pas été explicitement désactivé. */
    record CentreDeLaSociete(UUID id, String nom, boolean actif) {
    }
}
