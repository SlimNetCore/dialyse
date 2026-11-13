package com.hemodialyse.backend.contexts.documents.domain;

import java.util.UUID;

/**
 * Ce qu'un administrateur définit pour un modèle de document de son centre : code (unique dans le centre), libellé, type de document, gabarit
 * livré, format d'impression, description facultative, actif ou non. Les bornes sont celles des colonnes ({@code modele_document}) : un modèle
 * trop long ou incomplet est refusé ici, avec un message lisible, au lieu d'échouer en base.
 *
 * <p>Reproduit tel quel, à confirmer métiers/DPO avant le 2026-12-15 (KB-DOC4A-7) : le code et le libellé peuvent être vides ou blancs, le
 * type et le format sont libres (aucune liste fermée).
 */
public record DefinitionDeModele(UUID centre, String code, String libelle, String type, CheminDeGabarit gabarit, String format,
                                 String description, boolean actif) {

    public static final int CODE_MAX = 50;
    public static final int LIBELLE_MAX = 255;
    public static final int TYPE_MAX = 50;
    public static final int FORMAT_MAX = 20;
    public static final int DESCRIPTION_MAX = 500;

    /**
     * @param actif {@code null} = non renseigné (refusé : le statut du modèle est obligatoire, comme avant où son absence faisait échouer la requête)
     * @throws ModeleRefuse chemin de gabarit invalide (contrôlé en premier, comme avant), champ obligatoire absent ou trop long
     */
    public static DefinitionDeModele definir(UUID centre, String code, String libelle, String type, String cheminDuGabarit, String format,
                                             String description, Boolean actif) {
        CheminDeGabarit gabarit = CheminDeGabarit.de(cheminDuGabarit);
        obligatoire("code", code, CODE_MAX);
        obligatoire("libelle", libelle, LIBELLE_MAX);
        obligatoire("typeDocument", type, TYPE_MAX);
        obligatoire("formatImpression", format, FORMAT_MAX);
        if (description != null && description.length() > DESCRIPTION_MAX) {
            throw new ModeleRefuse("Champ trop long : description (" + DESCRIPTION_MAX + " caractères au plus)");
        }
        if (actif == null) {
            throw new ModeleRefuse("Champ obligatoire : active");
        }
        return new DefinitionDeModele(centre, code, libelle, type, gabarit, format, description, actif);
    }

    private static void obligatoire(String nom, String valeur, int max) {
        if (valeur == null) {
            throw new ModeleRefuse("Champ obligatoire : " + nom);
        }
        if (valeur.length() > max) {
            throw new ModeleRefuse("Champ trop long : " + nom + " (" + max + " caractères au plus)");
        }
    }
}
