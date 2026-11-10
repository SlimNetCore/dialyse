package com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Langage publié {@code patientcouverture.v1} : la gestion du catalogue de forfaits d'un centre (RF2), pour l'administration des
 * référentiels de la plateforme. Le centre est celui que l'appelant a déjà contrôlé ; le prix est le texte saisi (ou lu dans le
 * fichier). Les refus sont des {@link RefusDeForfait}.
 */
public interface GestionDuCatalogueDeForfaits {

    FicheDeForfait creer(UUID centre, String code, String libelle, String prix);

    /** Remplace le code, le libellé ET le prix ; un prix omis efface le prix. */
    FicheDeForfait modifier(UUID centre, UUID id, String code, String libelle, String prix);

    void supprimer(UUID centre, UUID id);

    PageDeForfaits lister(UUID centre, String recherche, int page, int taille);

    /**
     * Importe des lignes (numérotées comme dans le fichier) : tout ou rien. Les lignes sont contrôlées toutes ensemble ; une seule
     * anomalie refuse le fichier. Un forfait déjà connu (même code) est remplacé. Sans {@code ecrire}, vérification seule.
     */
    BilanDImport importer(UUID centre, List<LigneAImporter> lignes, boolean ecrire);

    record FicheDeForfait(UUID id, String code, String libelle, BigDecimal prix) {
    }

    record PageDeForfaits(List<FicheDeForfait> lignes, long total, int page, int taille) {
    }

    record LigneAImporter(int numero, String code, String libelle, String prix) {
    }

    /** Anomalie d'une saisie ({@code ligne} 0) ou d'une ligne importée. */
    record Anomalie(int ligne, String champ, String code, String message, Map<String, String> parametres) {
    }

    record BilanDImport(int aCreer, int aMettreAJour, List<Anomalie> anomalies, boolean appliquee) {
    }
}
