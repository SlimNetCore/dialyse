package com.hemodialyse.backend.contexts.organisation.publishedlanguage;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Langage publié {@code organisation.v1} : la gestion des médecins d'un centre (PL11-3, ADR 0159), pour l'administration des
 * référentiels de la plateforme. Le centre est celui que l'appelant a déjà contrôlé. Les refus sont des {@link RefusDeMedecin}.
 * <p>
 * Une seule règle d'unicité par centre (D7) : le nom et le prénom (casse et espaces ignorés, prénom absent = vide) désignent un seul
 * médecin proposé.
 */
public interface GestionDesMedecins {

    /** Crée un médecin ; si un médecin désactivé porte le même nom et prénom, il est réactivé avec la saisie (D6). */
    FicheDeMedecin creer(UUID centre, String nom, String prenom, String specialite);

    /** Remplace le nom, le prénom et la spécialité d'un médecin proposé. */
    FicheDeMedecin modifier(UUID centre, UUID id, String nom, String prenom, String specialite);

    /** Désactive un médecin proposé, utilisé ou non : ses références (patients, prescriptions, ordonnances, séances) restent valides (D5). */
    void desactiver(UUID centre, UUID id);

    PageDeMedecins lister(UUID centre, String recherche, int page, int taille);

    /** Les médecins proposés du centre (appariement de la reprise de données). */
    List<FicheDeMedecin> proposes(UUID centre);

    /**
     * Importe des lignes (numérotées comme dans le fichier) : tout ou rien. Un médecin déjà connu (même nom et prénom) est mis à jour ;
     * un médecin désactivé de même nom et prénom est réactivé (D6). Sans {@code ecrire}, vérification seule.
     */
    BilanDImport importer(UUID centre, List<LigneAImporter> lignes, boolean ecrire);

    record FicheDeMedecin(UUID id, String nom, String prenom, String specialite) {
    }

    record PageDeMedecins(List<FicheDeMedecin> lignes, long total, int page, int taille) {
    }

    record LigneAImporter(int numero, String nom, String prenom, String specialite) {
    }

    /** Anomalie d'une saisie ({@code ligne} 0) ou d'une ligne importée. */
    record Anomalie(int ligne, String champ, String code, String message, Map<String, String> parametres) {
    }

    record BilanDImport(int aCreer, int aMettreAJour, List<Anomalie> anomalies, boolean appliquee) {
    }
}
