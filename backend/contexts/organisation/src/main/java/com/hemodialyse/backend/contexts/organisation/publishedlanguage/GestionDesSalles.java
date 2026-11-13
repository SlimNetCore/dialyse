package com.hemodialyse.backend.contexts.organisation.publishedlanguage;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Langage publié {@code organisation.v1} : la gestion des salles d'un centre (PL11-4, ADR 0160), pour l'administration des référentiels
 * de la plateforme. Le centre est celui que l'appelant a déjà contrôlé. Les refus sont des {@link RefusDeSalle}.
 * <p>
 * D8 : l'isolement et la capacité non précisés (absents ou vides) sont conservés à la modification et à l'import d'une salle existante.
 */
public interface GestionDesSalles {

    FicheDeSalle creer(UUID centre, String code, String nom, String isolement, String capacite);

    FicheDeSalle modifier(UUID centre, UUID id, String code, String nom, String isolement, String capacite);

    /**
     * Supprime une salle que plus rien ne référence ; si elle est référencée (patients, mouvements, séances, plannings, infirmiers,
     * équipements), elle est désactivée et n'est plus proposée (D4). Si le contrôle d'usage échoue, la salle est conservée (D10).
     *
     * @return {@code true} si la salle a été désactivée plutôt que supprimée
     */
    boolean supprimer(UUID centre, UUID id);

    PageDeSalles lister(UUID centre, String recherche, int page, int taille);

    /** Les salles proposées du centre (appariement de la reprise de données). */
    List<FicheDeSalle> proposees(UUID centre);

    /**
     * Importe des lignes (numérotées comme dans le fichier) : tout ou rien. Une salle déjà connue (même code) est mise à jour ; une salle
     * désactivée de même code est réactivée. Sans {@code ecrire}, vérification seule.
     */
    BilanDImport importer(UUID centre, List<LigneAImporter> lignes, boolean ecrire);

    /** {@code isolement} vaut {@code OUI} ou {@code NON} ; {@code capacite} est absente si inconnue. */
    record FicheDeSalle(UUID id, String code, String nom, String isolement, Integer capacite) {
    }

    record PageDeSalles(List<FicheDeSalle> lignes, long total, int page, int taille) {
    }

    record LigneAImporter(int numero, String code, String nom, String isolement, String capacite) {
    }

    /** Anomalie d'une saisie ({@code ligne} 0) ou d'une ligne importée. */
    record Anomalie(int ligne, String champ, String code, String message, Map<String, String> parametres) {
    }

    record BilanDImport(int aCreer, int aMettreAJour, List<Anomalie> anomalies, boolean appliquee) {
    }
}
