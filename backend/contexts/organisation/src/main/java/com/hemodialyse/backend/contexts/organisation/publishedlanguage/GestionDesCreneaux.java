package com.hemodialyse.backend.contexts.organisation.publishedlanguage;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Langage publié {@code organisation.v1} : la gestion des créneaux (positions) d'un centre (PL11-2, ADR 0158), pour l'administration
 * des référentiels de la plateforme. Le centre est celui que l'appelant a déjà contrôlé. Les refus sont des {@link RefusDeCreneau}.
 */
public interface GestionDesCreneaux {

    FicheDeCreneau creer(UUID centre, String code, String libelle);

    FicheDeCreneau modifier(UUID centre, UUID id, String code, String libelle);

    /**
     * Supprime un créneau que plus rien ne référence ; s'il est référencé (patients, mouvements, séances, plannings, affectations
     * d'infirmiers), il est désactivé et n'est plus proposé (D4). Si le contrôle d'usage échoue, le créneau est conservé (D10).
     *
     * @return {@code true} si le créneau a été désactivé plutôt que supprimé
     */
    boolean supprimer(UUID centre, UUID id);

    PageDeCreneaux lister(UUID centre, String recherche, int page, int taille);

    /** Les créneaux proposés du centre (appariement de la reprise de données, en attendant PL11-5). */
    List<FicheDeCreneau> proposes(UUID centre);

    /**
     * Importe des lignes (numérotées comme dans le fichier) : tout ou rien. Un créneau déjà connu (même code) est mis à jour ; un
     * créneau désactivé de même code est réactivé. Sans {@code ecrire}, vérification seule.
     */
    BilanDImport importer(UUID centre, List<LigneAImporter> lignes, boolean ecrire);

    record FicheDeCreneau(UUID id, String code, String libelle) {
    }

    record PageDeCreneaux(List<FicheDeCreneau> lignes, long total, int page, int taille) {
    }

    record LigneAImporter(int numero, String code, String libelle) {
    }

    /** Anomalie d'une saisie ({@code ligne} 0) ou d'une ligne importée. */
    record Anomalie(int ligne, String champ, String code, String message, Map<String, String> parametres) {
    }

    record BilanDImport(int aCreer, int aMettreAJour, List<Anomalie> anomalies, boolean appliquee) {
    }
}
