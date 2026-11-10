package com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Langage publié {@code patientcouverture.v1} : la gestion de la chaîne des payeurs d'un centre (RF3a) - caisses d'assurance, agences,
 * centres payeurs - pour l'administration des référentiels de la plateforme. Le centre est celui que l'appelant a déjà contrôlé ; le
 * parent d'une agence (sa caisse) ou d'un centre payeur (son agence) est donné par son code ou son identifiant. Les refus sont des
 * {@link RefusDePayeur}. La lecture « annuaire » des autres contextes ({@code PayeursDuCentre}) viendra avec RF3b.
 */
public interface GestionDesPayeurs {

    /** Les trois niveaux de la chaîne, du plus haut au plus bas. */
    enum Niveau {CAISSE, AGENCE, CENTRE_PAYEUR}

    FicheDePayeur creer(UUID centre, Niveau niveau, SaisieDePayeur saisie);

    /** Remplace tous les champs du niveau ; l'adresse omise efface l'adresse, le type de caisse omis redevient STANDARD. */
    FicheDePayeur modifier(UUID centre, Niveau niveau, UUID id, SaisieDePayeur saisie);

    /** Refusée tant qu'un payeur de niveau inférieur ou un patient s'y rattache. */
    void supprimer(UUID centre, Niveau niveau, UUID id);

    PageDePayeurs lister(UUID centre, Niveau niveau, String recherche, int page, int taille);

    /** Tous les payeurs du niveau dans le centre (reprise des patients : retrouver un payeur par son code). */
    List<FicheDePayeur> toutes(UUID centre, Niveau niveau);

    /**
     * Importe des lignes (numérotées comme dans le fichier) : tout ou rien. Les lignes sont contrôlées toutes ensemble ; une seule
     * anomalie refuse le fichier. Un payeur déjà connu (même code) est remplacé. Sans {@code ecrire}, vérification seule. Un parent
     * doit exister AVANT l'import (importer les caisses, puis les agences, puis les centres payeurs).
     */
    BilanDImport importer(UUID centre, Niveau niveau, List<LigneAImporter> lignes, boolean ecrire);

    /**
     * Ce que l'on saisit : le code et le nom, le type (caisse seulement), l'adresse (centre payeur seulement), le parent (code ou
     * identifiant ; agence et centre payeur seulement). Les champs sans objet pour le niveau sont ignorés.
     */
    record SaisieDePayeur(String code, String nom, String typeCaisse, String adresse, String parent) {
    }

    /** Un payeur : {@code parent} est l'identité de la caisse (agence) ou de l'agence (centre payeur), {@code parentLibelle} « code · nom ». */
    record FicheDePayeur(UUID id, String code, String nom, String typeCaisse, String adresse, UUID parent, String parentLibelle) {
    }

    record PageDePayeurs(List<FicheDePayeur> lignes, long total, int page, int taille) {
    }

    record LigneAImporter(int numero, SaisieDePayeur saisie) {
    }

    /** Anomalie d'une saisie ({@code ligne} 0) ou d'une ligne importée. */
    record Anomalie(int ligne, String champ, String code, String message, Map<String, String> parametres) {
    }

    record BilanDImport(int aCreer, int aMettreAJour, List<Anomalie> anomalies, boolean appliquee) {
    }
}
