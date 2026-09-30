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
import static com.hemodialyse.backend.domain.migration.model.ColumnType.DECIMAL;
import static com.hemodialyse.backend.domain.migration.model.ColumnType.EMAIL;
import static com.hemodialyse.backend.domain.migration.model.ColumnType.INTEGER;
import static com.hemodialyse.backend.domain.migration.model.ColumnType.PHONE;
import static com.hemodialyse.backend.domain.migration.model.ColumnType.REFERENCE;

/**
 * Données reprises d'un système existant, dans l'ordre de chargement imposé par leurs dépendances.
 * <p>
 * Chaque ligne porte l'identifiant qu'elle avait dans l'ancien système ({@code legacy_id}) ; à défaut, la donnée
 * est reconnue par sa clé métier (patient + date…). Rejouer un fichier met donc à jour au lieu de dupliquer.
 * Les données rattachées à un patient le désignent par son identifiant d'origine (colonne « Patient »).
 */
public enum MigrationEntity {

    ASSURES("assures", "Assurés", 1, List.of(
            text("legacyId", "Identifiant d'origine", true, 150, "ASS-0001", "legacy id", "id", "identifiant"),
            text("numeroAssurance", "N° d'assurance", true, 100, "851234567890", "numero assurance", "n assurance", "nss"),
            text("nom", "Nom", true, 255, "BENALI"),
            text("prenom", "Prénom", false, 255, "Karim"),
            new ColumnDef("sexe", "Sexe", ColumnType.ENUM, false, 0, List.of("M", "F"), null, Synonyms.SEXE, "M",
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
            enumeration("sexe", "Sexe", List.of("M", "F"), null, Synonyms.SEXE, "M", "genre"),
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
                    Synonyms.QUALITE, "ASSURE_LUI_MEME", "qualite", "lien"),
            text("assureNumeroAssurance", "N° d'assurance de l'assuré", false, 100, "",
                    "numero assure", "assure", "n assurance assure"),
            reference("centrePayeur", "Centre payeur (code)", 50, "CP-ALG-01", "centre payeur", "code centre payeur"),
            reference("medecinTraitant", "Médecin traitant (nom prénom)", 255, "OUARET Mustapha", "medecin", "medecin traitant"),
            reference("salle", "Salle (code)", 50, "S1", "salle", "code salle"),
            reference("creneau", "Créneau (code)", 50, "CR1", "creneau", "position", "code creneau"),
            reference("generateur", "Générateur (numéro)", 50, "G01", "generateur", "machine", "numero generateur"),
            reference("transporteurAller", "Transporteur aller (nom)", 255, "Ambulances Rouiba", "transporteur aller", "transport aller"),
            reference("transporteurRetour", "Transporteur retour (nom)", 255, "Ambulances Rouiba", "transporteur retour", "transport retour"),
            of("joursDialyse", "Jours de dialyse", DAYS, false, "Lun, Mer, Ven", "jours", "seances"),
            text("observation", "Observation", false, 4000, "", "remarque", "commentaire")
    )),

    AFFECTATIONS("affectations", "Affectations assuré ↔ patient", 3, List.of(
            text("legacyId", "Identifiant d'origine", false, 150, "AFF-0001", "legacy id", "id"),
            patient(),
            text("assureNumeroAssurance", "N° d'assurance de l'assuré", true, 100, "851234567890",
                    "numero assure", "assure", "n assurance assure", "numero assurance"),
            of("dateDebut", "Date de début", DATE, false, "01/01/2020", "debut", "du"),
            of("dateFin", "Date de fin", DATE, false, "", "fin", "au"),
            new ColumnDef("principale", "Affectation principale", BOOLEAN, false, 0, null, "non", null, "oui",
                    List.of("principal", "actuelle", "en cours"))
    )),

    ATTESTATIONS("attestations", "Attestations d'ouverture de droit", 4, List.of(
            text("legacyId", "Identifiant d'origine", false, 150, "ATT-0001", "legacy id", "id"),
            patient(),
            of("dateDebut", "Date de début", DATE, true, "01/01/2026", "debut", "du", "valable du"),
            of("dateFin", "Date de fin", DATE, true, "31/12/2026", "fin", "au", "valable au")
    )),

    PRISES_EN_CHARGE("prises-en-charge", "Prises en charge", 5, List.of(
            text("legacyId", "Identifiant d'origine", false, 150, "PEC-0001", "legacy id", "id", "numero pec"),
            patient(),
            of("dateDebutDemande", "Début demandé", DATE, true, "01/01/2026", "debut demande", "date debut"),
            of("dateFinDemande", "Fin demandée", DATE, true, "30/06/2026", "fin demande", "date fin"),
            new ColumnDef("forfaitDemande", "Forfait demandé (code)", REFERENCE, true, 50, null, null, null, "HD-CONV",
                    List.of("forfait", "forfait demande")),
            enumeration("statut", "Statut", List.of("CREE", "VALIDEE", "CLOTUREE"), "VALIDEE", Synonyms.PEC,
                    "VALIDEE", "etat"),
            of("dateDebutEffectif", "Début accordé", DATE, false, "", "debut effectif", "debut accorde"),
            of("dateFinEffectif", "Fin accordée", DATE, false, "", "fin effective", "fin accordee"),
            reference("forfaitEffectif", "Forfait accordé (code)", 50, "", "forfait effectif", "forfait accorde")
    )),

    DOSSIERS_MEDICAUX("dossiers-medicaux", "Dossiers médicaux", 6, List.of(
            patient(),
            of("dateMiseEnDialyse", "Date de mise en dialyse", DATE, false, "15/06/2018", "mise en dialyse", "debut dialyse"),
            text("nephropathieInitiale", "Néphropathie initiale", false, 255, "Néphropathie diabétique", "nephropathie"),
            text("hepatiteBStatut", "Statut hépatite B", false, 20, "NEGATIF", "hepatite b", "vhb"),
            text("hepatiteCStatut", "Statut hépatite C", false, 20, "NEGATIF", "hepatite c", "vhc"),
            text("observationGlobale", "Observation", false, 4000, "", "observation", "remarque"),
            text("conclusionMedicale", "Conclusion médicale", false, 4000, "", "conclusion")
    )),

    ANTECEDENTS("antecedents", "Antécédents", 7, List.of(
            text("legacyId", "Identifiant d'origine", false, 150, "", "legacy id", "id"),
            patient(),
            enumeration("typeAntecedent", "Type", List.of("MEDICAL", "CHIRURGICAL", "FAMILIAL", "OBSTETRICAL", "COMORBIDITE"),
                    "MEDICAL", Synonyms.ANTECEDENT, "MEDICAL", "type antecedent", "type"),
            text("libelle", "Libellé", true, 255, "Diabète de type 2", "antecedent", "diagnostic"),
            text("codeCim10", "Code CIM-10", false, 20, "E11", "cim10", "cim 10", "code"),
            of("dateDebut", "Date de début", DATE, true, "01/01/2010", "debut", "date"),
            of("dateFin", "Date de fin", DATE, false, "", "fin"),
            enumeration("statutClinique", "Statut clinique", List.of("ACTIF", "RESOLU", "INACTIF"), "ACTIF",
                    Map.of("EN_COURS", "ACTIF", "GUERI", "RESOLU", "TERMINE", "RESOLU"), "ACTIF", "statut"),
            text("severite", "Sévérité", false, 50, "", "gravite"),
            text("note", "Note", false, 4000, "", "commentaire")
    )),

    SEROLOGIES("serologies", "Sérologies", 8, List.of(
            text("legacyId", "Identifiant d'origine", false, 150, "", "legacy id", "id"),
            patient(),
            enumeration("marqueur", "Marqueur", List.of("VIH_AC", "AG_HBS", "AC_HBS", "AC_HBC", "AC_VHC", "ARN_VHC", "TPHA",
                    "CMV_IGG", "CMV_IGM", "TOXO_IGG", "TOXO_IGM", "EBV_IGG", "EBV_IGM"), null, Synonyms.MARQUEUR, "AG_HBS"),
            enumeration("resultat", "Résultat", List.of("POSITIF", "NEGATIF", "DOUTEUX", "EN_COURS"), null,
                    Map.of("POS", "POSITIF", "NEG", "NEGATIF", "P", "POSITIF", "N", "NEGATIF", "DOUTE", "DOUTEUX"),
                    "NEGATIF"),
            of("datePrelevement", "Date de prélèvement", DATE, true, "15/01/2026", "date", "prelevement"),
            of("titre", "Titre", DECIMAL, false, "", "valeur"),
            text("unite", "Unité", false, 20, ""),
            text("laboratoire", "Laboratoire", false, 255, "", "labo")
    )),

    ABORDS_VASCULAIRES("abords-vasculaires", "Abords vasculaires", 9, List.of(
            text("legacyId", "Identifiant d'origine", false, 150, "", "legacy id", "id"),
            patient(),
            enumeration("typeAbord", "Type d'abord", List.of("FAV", "PTFE", "KT_TUNNELISE", "KT_AIGU"), null,
                    Synonyms.ABORD, "FAV", "type", "abord"),
            new ColumnDef("cote", "Côté", ColumnType.ENUM, false, 0, List.of("GAUCHE", "DROIT"), null,
                    Map.of("G", "GAUCHE", "D", "DROIT", "DROITE", "DROIT"), "GAUCHE", List.of()),
            text("localisation", "Localisation", false, 255, "Radio-céphalique", "site"),
            of("dateCreation", "Date de création", DATE, false, "10/03/2018", "creation", "date"),
            of("dateFin", "Date de fin", DATE, false, "", "fin"),
            new ColumnDef("actif", "Actif", BOOLEAN, false, 0, null, "oui", null, "oui", List.of("en service")),
            text("complications", "Complications", false, 500, "")
    )),

    ANALYSES("analyses", "Résultats d'analyses", 10, List.of(
            text("legacyId", "Identifiant d'origine", false, 150, "", "legacy id", "id"),
            patient(),
            of("datePrelevement", "Date de prélèvement", DATE, true, "05/01/2026", "date", "prelevement"),
            of("hbGDl", "Hémoglobine (g/dL)", DECIMAL, false, "10,8", "hb", "hemoglobine"),
            of("htPct", "Hématocrite (%)", DECIMAL, false, "33", "ht", "hematocrite"),
            of("plaquettes", "Plaquettes", INTEGER, false, "210000"),
            of("ferritineNgMl", "Ferritine (ng/mL)", DECIMAL, false, "350", "ferritine"),
            of("cstfPct", "CSTf (%)", DECIMAL, false, "25", "cstf", "saturation transferrine"),
            of("ureePreMgDl", "Urée pré-dialyse (mg/dL)", DECIMAL, false, "150", "uree pre"),
            of("ureePostMgDl", "Urée post-dialyse (mg/dL)", DECIMAL, false, "45", "uree post"),
            of("creatinineMgDl", "Créatinine (mg/dL)", DECIMAL, false, "8,5", "creatinine"),
            of("ktVMensuel", "Kt/V", DECIMAL, false, "1,3", "ktv", "kt v"),
            of("phosphoreMgDl", "Phosphore (mg/dL)", DECIMAL, false, "4,8", "phosphore"),
            of("calciumMgDl", "Calcium (mg/dL)", DECIMAL, false, "9,2", "calcium"),
            of("pthPgMl", "PTH (pg/mL)", DECIMAL, false, "320", "pth"),
            of("albumineGDl", "Albumine (g/dL)", DECIMAL, false, "3,9", "albumine"),
            of("proteinesGDl", "Protéines (g/dL)", DECIMAL, false, "6,8", "proteines"),
            of("crpMgL", "CRP (mg/L)", DECIMAL, false, "4", "crp")
    )),

    SEANCES("seances", "Séances", 11, List.of(
            text("legacyId", "Identifiant d'origine", false, 150, "", "legacy id", "id"),
            patient(),
            of("dateSeance", "Date de séance", DATE, true, "05/01/2026", "date", "date seance"),
            new ColumnDef("facturee", "Facturée dans l'ancien logiciel", BOOLEAN, false, 0, null, "oui", null, "oui",
                    List.of("facturee", "facture"))
    )),

    SOLDES_OUVERTURE("soldes-ouverture", "Soldes d'ouverture (factures non soldées)", 12, List.of(
            text("numeroFacture", "N° de facture d'origine", true, 100, "F2026-0042", "numero facture", "facture", "legacy id"),
            patient(),
            of("dateFacture", "Date de facture", DATE, true, "31/01/2026", "date", "date facturation"),
            of("periodeDebut", "Période du", DATE, false, "01/01/2026", "debut periode", "du"),
            of("periodeFin", "Période au", DATE, false, "31/01/2026", "fin periode", "au"),
            new ColumnDef("montantTtc", "Montant TTC", DECIMAL, true, 0, null, null, null, "67200",
                    List.of("montant", "total ttc", "ttc")),
            new ColumnDef("montantRegle", "Montant déjà réglé", DECIMAL, false, 0, null, "0", null, "20000",
                    List.of("regle", "deja regle", "paye")),
            of("dateDernierReglement", "Date du dernier règlement", DATE, false, "", "date reglement"),
            text("libelle", "Libellé", false, 200, "Séances de janvier 2026", "designation")
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

    private static ColumnDef patient() {
        return new ColumnDef("patient", "Patient (identifiant d'origine)", REFERENCE, true, 150, null, null, null, "PAT-0001",
                List.of("patient", "legacy patient", "id patient", "dossier", "identifiant patient"));
    }

    private static ColumnDef reference(String key, String label, int maxLength, String example, String... aliases) {
        return new ColumnDef(key, label, REFERENCE, false, maxLength, null, null, null, example, List.of(aliases));
    }

    public static MigrationEntity fromSlug(String slug) {
        return Arrays.stream(values())
                .filter(e -> e.slug.equalsIgnoreCase(slug))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Donnée de reprise inconnue : " + slug));
    }

    public List<ColumnDef> columns() {
        return columns;
    }

    public Optional<ColumnDef> column(String key) {
        return columns.stream().filter(c -> c.key().equals(key)).findFirst();
    }

    public String slug() {
        return slug;
    }

    public String label() {
        return label;
    }

    /**
     * Ordre de chargement : une donnée ne peut être reprise qu'après celles d'ordre inférieur.
     */
    public int order() {
        return order;
    }

    /**
     * Valeurs de l'ancien logiciel reconnues d'office.
     */
    private static final class Synonyms {
        static final Map<String, String> SEXE = Map.of(
                "H", "M", "HOMME", "M", "MASCULIN", "M", "MALE", "M", "1", "M",
                "FEMME", "F", "FEMININ", "F", "FEMALE", "F", "2", "F");
        static final Map<String, String> QUALITE = Map.ofEntries(
                Map.entry("ASSURE", "ASSURE_LUI_MEME"), Map.entry("LUI_MEME", "ASSURE_LUI_MEME"),
                Map.entry("ELLE_MEME", "ASSURE_LUI_MEME"), Map.entry("TITULAIRE", "ASSURE_LUI_MEME"),
                Map.entry("EPOUX", "CONJOINT"), Map.entry("EPOUSE", "CONJOINT"), Map.entry("FILS", "ENFANT"),
                Map.entry("FILLE", "ENFANT"), Map.entry("PERE", "ASCENDANT"), Map.entry("MERE", "ASCENDANT"),
                Map.entry("AYANT_DROIT", "AUTRE"));
        static final Map<String, String> PEC = Map.ofEntries(
                Map.entry("ACCORDEE", "VALIDEE"), Map.entry("ACCEPTEE", "VALIDEE"), Map.entry("EN_COURS", "VALIDEE"),
                Map.entry("VALIDE", "VALIDEE"), Map.entry("DEMANDEE", "CREE"), Map.entry("EN_ATTENTE", "CREE"),
                Map.entry("CREEE", "CREE"), Map.entry("EXPIREE", "CLOTUREE"), Map.entry("TERMINEE", "CLOTUREE"),
                Map.entry("CLOSE", "CLOTUREE"), Map.entry("CLOTURE", "CLOTUREE"));
        static final Map<String, String> ANTECEDENT = Map.of(
                "CHIRURGIE", "CHIRURGICAL", "FAMILLE", "FAMILIAL", "OBSTETRIQUE", "OBSTETRICAL",
                "COMORBIDITES", "COMORBIDITE", "MEDICAUX", "MEDICAL");
        static final Map<String, String> MARQUEUR = Map.ofEntries(
                Map.entry("AGHBS", "AG_HBS"), Map.entry("HBS", "AG_HBS"), Map.entry("ANTIGENE_HBS", "AG_HBS"),
                Map.entry("ANTI_HBS", "AC_HBS"), Map.entry("ACHBS", "AC_HBS"), Map.entry("ANTI_HBC", "AC_HBC"),
                Map.entry("ACHBC", "AC_HBC"), Map.entry("HCV", "AC_VHC"), Map.entry("VHC", "AC_VHC"),
                Map.entry("ANTI_VHC", "AC_VHC"), Map.entry("HIV", "VIH_AC"), Map.entry("VIH", "VIH_AC"),
                Map.entry("SYPHILIS", "TPHA"));
        static final Map<String, String> ABORD = Map.ofEntries(
                Map.entry("FISTULE", "FAV"), Map.entry("FISTULE_ARTERIOVEINEUSE", "FAV"), Map.entry("PROTHESE", "PTFE"),
                Map.entry("CATHETER_TUNNELISE", "KT_TUNNELISE"), Map.entry("KT_TUNNELLISE", "KT_TUNNELISE"),
                Map.entry("CATHETER_TUNNELLISE", "KT_TUNNELISE"), Map.entry("CATHETER", "KT_AIGU"),
                Map.entry("CATHETER_AIGU", "KT_AIGU"), Map.entry("KT", "KT_AIGU"));
    }
}

