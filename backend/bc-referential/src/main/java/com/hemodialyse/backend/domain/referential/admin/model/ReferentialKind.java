package com.hemodialyse.backend.domain.referential.admin.model;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.hemodialyse.backend.domain.referential.admin.model.ReferentialField.decimal;
import static com.hemodialyse.backend.domain.referential.admin.model.ReferentialField.enumeration;
import static com.hemodialyse.backend.domain.referential.admin.model.ReferentialField.phone;
import static com.hemodialyse.backend.domain.referential.admin.model.ReferentialField.reference;
import static com.hemodialyse.backend.domain.referential.admin.model.ReferentialField.text;

/**
 * Référentiels administrables d'un centre : champs, clé naturelle (unicité + mise à jour à l'import) et ordre
 * d'import conseillé (un référentiel cible doit exister avant ceux qui le référencent).
 */
public enum ReferentialKind {

    FORFAIT("forfaits", "Forfaits", 1, List.of(
            text("code", "Code", true, 50, "HD-CONV", "code forfait"),
            text("libelle", "Libellé", true, 255, "Hémodialyse conventionnelle", "description", "designation"),
            decimal("prix", "Prix", false, "5600.00", "tarif", "montant")
    ), List.of("code")),

    CRENEAU("creneaux", "Créneaux", 1, List.of(
            text("code", "Code", true, 50, "CR1", "code creneau", "code position"),
            text("libelle", "Libellé", true, 255, "Matin (06h30 – 10h30)", "horaire", "plage horaire", "creneau")
    ), List.of("code")),

    SALLE("salles", "Salles", 1, List.of(
            text("code", "Code", true, 50, "S1", "code salle"),
            text("nom", "Nom", true, 255, "Salle 1", "libelle", "salle")
    ), List.of("code")),

    MEDECIN("medecins", "Médecins traitants", 1, List.of(
            text("nom", "Nom", true, 255, "OUARET", "nom medecin"),
            text("prenom", "Prénom", false, 255, "Mustapha", "prenom medecin"),
            text("specialite", "Spécialité", false, 255, "Néphrologue")
    ), List.of("nom", "prenom")),

    GENERATEUR("generateurs", "Générateurs d'hémodialyse", 2, List.of(
            text("numero", "Numéro", true, 50, "G01", "numero generateur", "code", "n"),
            reference("salle", "Salle (code)", "salles", "S1", "salle", "code salle", "salle code"),
            text("marque", "Marque", false, 100, "Fresenius"),
            text("modele", "Modèle", false, 100, "5008S"),
            enumeration("etat", "État", List.of("FONCTIONNEL", "EN_MAINTENANCE", "EN_PANNE", "HORS_SERVICE"),
                    "FONCTIONNEL", "statut")
    ), List.of("numero")),

    CAISSE("caisses", "Caisses", 1, List.of(
            text("code", "Code", true, 50, "CNAS", "code caisse"),
            text("nom", "Nom", true, 255, "Caisse nationale des assurances sociales", "libelle", "caisse"),
            enumeration("typeCaisse", "Type de caisse", List.of("STANDARD", "VACANCIER"), "STANDARD", "type")
    ), List.of("code")),

    AGENCE("agences", "Agences", 2, List.of(
            text("code", "Code", true, 50, "CNAS-AG1", "code agence"),
            text("nom", "Nom", true, 255, "Agence CNAS Alger", "libelle", "agence"),
            reference("caisse", "Caisse (code)", "caisses", "CNAS", "caisse", "code caisse", "caisse code")
    ), List.of("code")),

    CENTRE_PAYEUR("centres-payeurs", "Centres payeurs", 3, List.of(
            text("code", "Code", true, 50, "CP-ALG-01", "code centre payeur", "code centre"),
            text("nom", "Nom", true, 255, "Centre payeur Alger Centre", "libelle", "centre payeur"),
            text("adresse", "Adresse", false, 255, "12 rue Didouche Mourad, Alger"),
            reference("agence", "Agence (code)", "agences", "CNAS-AG1", "agence", "code agence", "agence code")
    ), List.of("code")),

    TRANSPORTEUR("transporteurs", "Transporteurs", 1, List.of(
            text("nom", "Nom", true, 255, "Ambulances Rouiba", "transporteur", "raison sociale"),
            phone("telephone", "Téléphone", false, 50, "0795006136", "tel", "telephone portable", "mobile")
    ), List.of("nom"));

    private final String slug;
    private final String label;
    private final int importOrder;
    private final List<ReferentialField> fields;
    private final List<String> naturalKey;

    ReferentialKind(String slug, String label, int importOrder, List<ReferentialField> fields, List<String> naturalKey) {
        this.slug = slug;
        this.label = label;
        this.importOrder = importOrder;
        this.fields = List.copyOf(fields);
        this.naturalKey = List.copyOf(naturalKey);
    }

    public static ReferentialKind fromSlug(String slug) {
        return Arrays.stream(values())
                .filter(k -> k.slug.equalsIgnoreCase(slug))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Référentiel inconnu : " + slug));
    }

    public String slug() {
        return slug;
    }

    public String label() {
        return label;
    }

    /**
     * 1 = aucun prérequis ; 2 = dépend d'un référentiel d'ordre 1 ; 3 = dépend d'un référentiel d'ordre 2.
     */
    public int importOrder() {
        return importOrder;
    }

    public List<ReferentialField> fields() {
        return fields;
    }

    /**
     * Champs formant la clé d'unicité (un même code ne peut exister deux fois dans un centre).
     */
    public List<String> naturalKey() {
        return naturalKey;
    }

    public Optional<ReferentialField> field(String key) {
        return fields.stream().filter(f -> f.key().equals(key)).findFirst();
    }

    /**
     * Clé naturelle normalisée (casse et espaces ignorés) d'un jeu de valeurs.
     */
    public String naturalKeyOf(Map<String, String> values) {
        return naturalKey.stream()
                .map(k -> values.get(k) == null ? "" : values.get(k).trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT))
                .collect(Collectors.joining("|"));
    }
}

