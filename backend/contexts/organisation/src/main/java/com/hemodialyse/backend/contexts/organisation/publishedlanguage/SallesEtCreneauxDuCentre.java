package com.hemodialyse.backend.contexts.organisation.publishedlanguage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Langage publié d'Organisation (organisation.v1, O4 lot D, ADR 0110) : les salles et les créneaux d'un centre, tels que la
 * Planification les lit pour placer les patients. Lecture seule ; aucune écriture (l'isolement d'une salle se commande par
 * {@link DefinirLesSallesDIsolement}).
 *
 * <p>Toutes les lectures sont BORNÉES au centre demandé : une salle ou un créneau d'un autre centre, ou inconnu, n'existe pas pour
 * l'appelant. Une salle fermée est une salle comme les autres (l'état d'ouverture n'est pas publié : la Planification ne l'a jamais lu).
 */
public interface SallesEtCreneauxDuCentre {

    /**
     * Une salle du centre. {@code estSalleDIsolement} vaut vrai quand la salle est réservée aux patients à risque infectieux ;
     * {@code capacite} est {@code null} quand elle n'a jamais été renseignée.
     */
    record SalleDuCentre(UUID id, String code, String nom, boolean estSalleDIsolement, Integer capacite) {
    }

    /** Un créneau (série) du centre. */
    record CreneauDuCentre(UUID id, String libelle) {
    }

    /** Une page de salles et le nombre total de salles du centre. */
    record PageDeSalles(List<SalleDuCentre> salles, long total) {
    }

    /** Les salles du centre, fermées comprises, triées par code. Un centre inconnu n'a aucune salle. */
    List<SalleDuCentre> sallesParCode(UUID centre);

    /** Une page de salles triées par code ({@code taille} lignes à partir de {@code decalage}) ; le total porte sur tout le centre. */
    PageDeSalles pageDesSalles(UUID centre, int taille, long decalage);

    /** Une salle du centre ; vide si elle est inconnue ou d'un autre centre. */
    Optional<SalleDuCentre> salleDuCentre(UUID centre, UUID salle);

    /** Les créneaux du centre triés par code. */
    List<CreneauDuCentre> creneauxParCode(UUID centre);

    /** Vrai si ce créneau existe ET appartient au centre. */
    boolean contientLeCreneau(UUID centre, UUID creneau);

    /** Le nombre de créneaux (séries) du centre. */
    int nombreDeCreneaux(UUID centre);
}
