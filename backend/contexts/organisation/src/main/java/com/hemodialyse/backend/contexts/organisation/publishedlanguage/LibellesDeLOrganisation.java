package com.hemodialyse.backend.contexts.organisation.publishedlanguage;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Langage publié d'Organisation (organisation.v1, O4 lot C, ADR 0109) : les libellés des salles, des créneaux et des médecins, que les
 * autres contextes affichent à côté de leurs propres lignes. Lecture seule ; aucun état, aucune écriture.
 *
 * <p>Chaque lecture se fait PAR LOT (identifiants distincts, {@code null} ignorés) et rend une table identifiant -> libellé : un
 * identifiant inconnu, d'un autre centre (quand la lecture est bornée au centre) ou sans libellé n'a PAS d'entrée. L'ordre des lignes
 * du consommateur et ses doublons restent les siens : il lit la table pour chacune de ses lignes.
 */
public interface LibellesDeLOrganisation {

    /** Code et libellé d'un créneau. */
    record LibelleDeCreneau(String code, String libelle) {
    }

    /** Le code des salles DU CENTRE (une salle d'un autre centre n'a pas d'entrée). */
    Map<UUID, String> codesDesSalles(UUID centre, Collection<UUID> salles);

    /** Le code et le libellé des créneaux DU CENTRE (un créneau d'un autre centre n'a pas d'entrée). */
    Map<UUID, LibelleDeCreneau> creneauxDuCentre(UUID centre, Collection<UUID> creneaux);

    /**
     * Le nom des salles, SANS borne de centre : une salle d'un autre centre est rendue comme les autres (reproduit la jointure du journal
     * des mouvements de Patient et couverture, KB-ORG-10). Un nom absent (NULL) n'a pas d'entrée ; un nom blanc est rendu tel quel.
     */
    Map<UUID, String> nomsDesSalles(Collection<UUID> salles);

    /** Le libellé des créneaux, SANS borne de centre (même raison que {@link #nomsDesSalles}, KB-ORG-10). */
    Map<UUID, String> libellesDesCreneaux(Collection<UUID> creneaux);

    /**
     * Le libellé des médecins DU CENTRE : « nom prénom » sans blanc de bord, le prénom étant facultatif. Un médecin désactivé garde son
     * libellé (il reste affiché sur les fiches existantes) ; un libellé vide n'a pas d'entrée.
     */
    Map<UUID, String> nomsDesMedecins(UUID centre, Collection<UUID> medecins);

    /**
     * Les médecins du centre dont le nom, le prénom (dans les deux ordres) OU l'identifiant contient le terme, sans tenir compte de la
     * casse ni des blancs de bord du terme. Les caractères {@code %} et {@code _} du terme ne sont pas échappés (reproduit, KB-PAT-11).
     */
    Set<UUID> medecinsCorrespondant(UUID centre, String terme);

    /** Les créneaux du centre dont le code, le libellé OU l'identifiant contient le terme (mêmes règles que {@link #medecinsCorrespondant}). */
    Set<UUID> creneauxCorrespondant(UUID centre, String terme);
}
