package com.hemodialyse.backend.domain.migration.model;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.hemodialyse.backend.domain.migration.model.ColumnDef.enumeration;
import static com.hemodialyse.backend.domain.migration.model.ColumnDef.of;
import static com.hemodialyse.backend.domain.migration.model.ColumnDef.text;
import static com.hemodialyse.backend.domain.migration.model.ColumnType.BOOLEAN;
import static com.hemodialyse.backend.domain.migration.model.ColumnType.DATE;
import static com.hemodialyse.backend.domain.migration.model.ColumnType.DAYS;
import static com.hemodialyse.backend.domain.migration.model.ColumnType.EMAIL;
import static com.hemodialyse.backend.domain.migration.model.ColumnType.INTEGER;
import static com.hemodialyse.backend.domain.migration.model.ColumnType.PHONE;
import static com.hemodialyse.backend.domain.migration.model.ColumnType.REFERENCE;

/**
 * Données reprises d'un système existant, dans l'ordre de chargement imposé par leurs dépendances.
 * <p>
 * Chaque ligne porte l'identifiant qu'elle avait dans l'ancien système ({@code legacy_id}) : rejouer un fichier
 * met à jour les lignes déjà reprises au lieu de les dupliquer.
 */
public enum MigrationEntity {

    ASSURES("assures", "Assurés", 1, List.of(
            text("legacyId", "Identifiant d'origine", true, 150, "ASS-0001", "legacy id", "id", "identifiant"),
            text("numeroAssurance", "N° d'assurance", true, 100, "851234567890", "numero assurance", "n assurance", "nss"),
            text("nom", "Nom", true, 255, "BENALI"),
            text("prenom", "Prénom", false, 255, "Karim"),
            new ColumnDef("sexe", "Sexe", ColumnType.ENUM, false, 0, List.of("M", "F"), null, Sexes.SYNONYMS, "M",
                    List.of("genre")),
            of("dateNaissance", "Date de naissance", DATE, false, "15/03/1962", "ne le", "naissance"),
            of("telPersonnel", "Téléphone personnel", PHONE, false, "0550123456", "telephone", "tel"),
            of("telMobile", "Téléphone mobile", PHONE, false, "0661123456", "mobile", "portable"),
            of("telBureau", "Téléphone bureau", PHONE, false, "", "tel bureau"),
            text("adresse", "Adresse", false, 500, "12 rue Didouche Mourad, Alger"),
            text("groupeSanguin", "Groupe sanguin", false, 20, "O+", "groupe")
    )),

    PATIENTS("patients", "Patients", 2, List.of(
            text("legacyId", "Identifiant d'origine", true, 150, "PAT-0001", "legacy id", "id", "identifiant", "dossier"),
            text("codePatient", "Code patient", false, 50, "P0001", "code"),
            text("numeroAssurance", "N° d'assurance", true, 100, "851234567890", "numero assurance", "n assurance", "nss"),
            text("nom", "Nom", true, 255, "BENALI"),
            text("prenom", "Prénom", true, 255, "Karim"),
            enumeration("sexe", "Sexe", List.of("M", "F"), null, Sexes.SYNONYMS, "M", "genre"),
            of("dateNaissance", "Date de naissance", DATE, true, "15/03/1962", "ne le", "naissance"),
            of("dateAdmission", "Date d'admission", DATE, true, "02/01/2019", "admission", "date entree"),
            text("civilite", "Civilité", false, 20, "M."),
            text("lieuNaissance", "Lieu de naissance", false, 255, "Alger"),
            text("situationFamiliale", "Situation familiale", false, 50, "Marié"),
            of("nombreEnfants", "Nombre d'enfants", INTEGER, false, "3", "enfants"),
            text("profession", "Profession", false, 255, "Enseignant"),
            text("adresse", "Adresse", false, 500, "12 rue Didouche Mourad, Alger"),
            of("telPersonnel", "Téléphone personnel", PHONE, false, "0550123456", "telephone", "tel"),
            of("telMobile", "Téléphone mobile", PHONE, false, "0661123456", "mobile", "portable"),
            of("telBureau", "Téléphone bureau", PHONE, false, "", "tel bureau"),
            of("email", "E-mail", EMAIL, false, "", "mail", "courriel"),
            text("groupeSanguin", "Groupe sanguin", false, 20, "O+", "groupe"),
            enumeration("etatPatient", "État du patient",
                    List.of("PERMANENT", "OCCASIONNEL", "VACANCIER_LOCAL", "VACANCIER_ETRANGER", "TRANSFERE", "DECEDE",
                            "GREFFE", "GUERRI"),
                    "PERMANENT", Map.of("ACTIF", "PERMANENT", "CHRONIQUE", "PERMANENT", "DECES", "DECEDE",
                            "TRANSFERT", "TRANSFERE", "GREFFE_RENALE", "GREFFE", "VACANCIER", "VACANCIER_LOCAL"),
                    "PERMANENT", "etat", "statut"),
            of("dateEvenementEtat", "Date de l'événement", DATE, false, "", "date evenement", "date etat"),
            enumeration("typePatient", "Type de patient", List.of("NON_VACANCIER", "VACANCIER"), "NON_VACANCIER",
                    Map.of("PERMANENT", "NON_VACANCIER", "NORMAL", "NON_VACANCIER"), "NON_VACANCIER", "type"),
            enumeration("qualiteAssure", "Qualité de l'assuré",
                    List.of("ASSURE_LUI_MEME", "ENFANT", "CONJOINT", "ASCENDANT", "AUTRE"), "ASSURE_LUI_MEME",
                    Map.ofEntries(Map.entry("ASSURE", "ASSURE_LUI_MEME"), Map.entry("LUI_MEME", "ASSURE_LUI_MEME"),
                            Map.entry("ELLE_MEME", "ASSURE_LUI_MEME"), Map.entry("TITULAIRE", "ASSURE_LUI_MEME"),
                            Map.entry("EPOUX", "CONJOINT"), Map.entry("EPOUSE", "CONJOINT"), Map.entry("FILS", "ENFANT"),
                            Map.entry("FILLE", "ENFANT"), Map.entry("PERE", "ASCENDANT"), Map.entry("MERE", "ASCENDANT"),
                            Map.entry("AYANT_DROIT", "AUTRE")),
                    "ASSURE_LUI_MEME", "qualite", "lien"),
            text("assureNumeroAssurance", "N° d'assurance de l'assuré", false, 100, "",
                    "numero assure", "assure", "n assurance assure"),
            new ColumnDef("centrePayeur", "Centre payeur (code)", REFERENCE, false, 50, null, null, null, "CP-ALG-01",
                    List.of("centre payeur", "code centre payeur")),
            new ColumnDef("medecinTraitant", "Médecin traitant (nom prénom)", REFERENCE, false, 255, null, null, null,
                    "OUARET Mustapha", List.of("medecin", "medecin traitant")),
            new ColumnDef("salle", "Salle (code)", REFERENCE, false, 50, null, null, null, "S1", List.of("salle", "code salle")),
            new ColumnDef("creneau", "Créneau (code)", REFERENCE, false, 50, null, null, null, "CR1",
                    List.of("creneau", "position", "code creneau")),
            new ColumnDef("generateur", "Générateur (numéro)", REFERENCE, false, 50, null, null, null, "G01",
                    List.of("generateur", "machine", "numero generateur")),
            new ColumnDef("transporteurAller", "Transporteur aller (nom)", REFERENCE, false, 255, null, null, null,
                    "Ambulances Rouiba", List.of("transporteur aller", "transport aller")),
            new ColumnDef("transporteurRetour", "Transporteur retour (nom)", REFERENCE, false, 255, null, null, null,
                    "Ambulances Rouiba", List.of("transporteur retour", "transport retour")),
            of("joursDialyse", "Jours de dialyse", DAYS, false, "Lun, Mer, Ven", "jours", "seances"),
            text("observation", "Observation", false, 4000, "", "remarque", "commentaire")
    )),

    AFFECTATIONS("affectations", "Affectations assuré ↔ patient", 3, List.of(
            text("legacyId", "Identifiant d'origine", false, 150, "AFF-0001", "legacy id", "id"),
            new ColumnDef("patient", "Patient (identifiant d'origine)", REFERENCE, true, 150, null, null, null, "PAT-0001",
                    List.of("patient", "legacy patient", "id patient", "dossier")),
            text("assureNumeroAssurance", "N° d'assurance de l'assuré", true, 100, "851234567890",
                    "numero assure", "assure", "n assurance assure", "numero assurance"),
            of("dateDebut", "Date de début", DATE, false, "01/01/2020", "debut", "du"),
            of("dateFin", "Date de fin", DATE, false, "", "fin", "au"),
            new ColumnDef("principale", "Affectation principale", BOOLEAN, false, 0, null, "non", null, "oui",
                    List.of("principal", "actuelle", "en cours"))
    ));

    private final String slug;
    private final String label;
    private final int order;
    private final List<ColumnDef> columns;

    MigrationEntity(String slug, String label, int order, List<ColumnDef> columns) {
        this.slug = slug;
        this.label = label;
        this.order = order;
        this.columns = List.copyOf(columns);
    }

    public static MigrationEntity fromSlug(String slug) {
        return Arrays.stream(values())
                .filter(e -> e.slug.equalsIgnoreCase(slug))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Donnée de reprise inconnue : " + slug));
    }

    public String slug() {
        return slug;
    }

    public String label() {
        return label;
    }

    /**
     * Ordre de chargement : une entité ne peut être reprise qu'après celles d'ordre inférieur.
     */
    public int order() {
        return order;
    }

    public List<ColumnDef> columns() {
        return columns;
    }

    public Optional<ColumnDef> column(String key) {
        return columns.stream().filter(c -> c.key().equals(key)).findFirst();
    }

    /**
     * Synonymes du sexe partagés entre assurés et patients.
     */
    private static final class Sexes {
        static final Map<String, String> SYNONYMS = Map.of(
                "H", "M", "HOMME", "M", "MASCULIN", "M", "MALE", "M", "1", "M",
                "FEMME", "F", "FEMININ", "F", "FEMALE", "F", "2", "F");
    }
}


