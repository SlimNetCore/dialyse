package com.hemodialyse.backend.contexts.patientcouverture.domain;

import java.util.UUID;

/**
 * Couverture saisie sur la fiche : numéro d'assurance du patient, qualité d'assuré, centre payeur et pièces. Les colonnes
 * de l'assuré tiers (PC2) et la prise en charge (PC3) restent portées par leurs propres slices ; ici seulement le numéro de
 * l'assuré tiers et l'état civil recopié sur la fiche.
 */
public record Couverture(NumeroAssurance numeroAssurance, String qualiteAssure, UUID centrePayeurId,
                         String assureNumeroAssurance, AssureSurLaFiche assure, String assureHistoriqueJson,
                         String piecesJointesJson) {

    public static final String ASSURE_LUI_MEME = "ASSURE_LUI_MEME";

    public Couverture {
        qualiteAssure = qualiteAssure == null || qualiteAssure.isBlank() ? ASSURE_LUI_MEME : qualiteAssure;
        assure = assure == null ? AssureSurLaFiche.vide() : assure;
    }

    public boolean assureLuiMeme() {
        return ASSURE_LUI_MEME.equals(qualiteAssure);
    }

    /** Identité de l'assuré telle que recopiée sur la fiche (textes libres du legacy, dates en texte). */
    public record AssureSurLaFiche(String sexe, String nom, String prenom, String dateNaissance, String telPersonnel,
                                   String adresse, String groupeSanguin, String telMobile, String telBureau) {
        public static AssureSurLaFiche vide() {
            return new AssureSurLaFiche(null, null, null, null, null, null, null, null, null);
        }
    }
}
