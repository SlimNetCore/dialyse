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

    /**
     * Les sociétés actives qui possèdent au moins un centre actif (un état de centre non renseigné vaut actif), par raison sociale
     * croissante : le tri est celui d'Organisation (ADR 0174).
     */
    List<SocieteNommee> societesAvecUnCentreActif();

    /** Une société et sa raison sociale. */
    record SocieteNommee(UUID id, String raisonSociale) {
    }

    /**
     * Tous les centres, actifs ou non, chacun avec son nom, sa société et la raison sociale de celle-ci, par nom de centre croissant (le
     * tri est celui d'Organisation, IDN-licence, ADR 0175). La société et sa raison sociale sont {@code null} si la société est inconnue.
     */
    List<CentreRattache> centresAvecLeurSociete();

    /** Un centre avec son nom et sa société. */
    record CentreRattache(UUID centre, String nomDuCentre, UUID societe, String raisonSociale) {
    }

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
