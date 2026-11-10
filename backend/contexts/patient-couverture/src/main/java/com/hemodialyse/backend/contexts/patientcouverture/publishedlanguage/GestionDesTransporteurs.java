package com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Langage publié {@code patientcouverture.v1} : la gestion des transporteurs d'un centre (RF1), pour l'administration des
 * référentiels de la plateforme. Le centre est celui que l'appelant a déjà contrôlé. Les refus sont des {@link RefusDeTransporteur}.
 */
public interface GestionDesTransporteurs {

    FicheDeTransporteur creer(UUID centre, String nom, String telephone);

    FicheDeTransporteur modifier(UUID centre, UUID id, String nom, String telephone);

    void supprimer(UUID centre, UUID id);

    PageDeTransporteurs lister(UUID centre, String recherche, int page, int taille);

    /**
     * Importe des lignes (numérotées comme dans le fichier) : tout ou rien. Les lignes sont contrôlées toutes ensemble ; une seule
     * anomalie refuse le fichier. Un transporteur déjà connu (même clé) est mis à jour. Sans {@code ecrire}, vérification seule.
     */
    BilanDImport importer(UUID centre, List<LigneAImporter> lignes, boolean ecrire);

    record FicheDeTransporteur(UUID id, String nom, String telephone) {
    }

    record PageDeTransporteurs(List<FicheDeTransporteur> lignes, long total, int page, int taille) {
    }

    record LigneAImporter(int numero, String nom, String telephone) {
    }

    /** Anomalie d'une saisie ({@code ligne} 0) ou d'une ligne importée. */
    record Anomalie(int ligne, String champ, String code, String message, Map<String, String> parametres) {
    }

    record BilanDImport(int aCreer, int aMettreAJour, List<Anomalie> anomalies, boolean appliquee) {
    }
}
