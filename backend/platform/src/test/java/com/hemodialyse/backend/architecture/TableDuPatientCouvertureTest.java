package com.hemodialyse.backend.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice PC5 : les tables de Patient &amp; couverture ({@code patients}, {@code mouvement_patient}, {@code assure},
 * {@code assure_patient}, {@code prise_en_charge}, {@code attestation_droit}) appartiennent au contexte patient-couverture. Aucun NOUVEAU
 * source hors de ce contexte ne doit les lire ou les écrire en SQL direct : les autres contextes passent par le langage publié
 * {@code patientcouverture.v1} ({@code FichesDesPatients}, {@code CouvertureDesPatients}, {@code CompteursDeCouverture}).
 * <p>
 * Garde de NON-RÉGRESSION sur le modèle de {@code TableDeLOrganisationTest} : les lecteurs et écrivains restants sont une LISTE
 * BLANCHE NOMMÉE, DATÉE, avec la raison et l'échéance de chaque jalon (acceptés le 2026-11-04). Un nouveau lecteur hors liste fait
 * échouer le test ; une entrée morte (fichier disparu ou migré) aussi : la liste doit être réduite.
 */
class TableDuPatientCouvertureTest {

    private static final Set<String> TABLES_DU_PATIENT_COUVERTURE = Set.of(
            "patients", "mouvement_patient", "assure", "assure_patient", "prise_en_charge", "attestation_droit");

    // Multiligne : « FROM » en fin de ligne et le nom de la table sur la suivante sont aussi détectés.
    private static final Pattern LECTURE_SQL = Pattern.compile(
            "\\b(FROM|JOIN|INTO|UPDATE|TABLE)\\s+([A-Za-z_][A-Za-z0-9_]*)", Pattern.CASE_INSENSITIVE);

    /**
     * Deuxième détection (revue du 2026-11-05) : le SQL CONSTRUIT n'a pas de littéral {@code FROM patients}. On repère donc les noms
     * de tables passés à un descripteur ({@code new Table("attestation_droit", …)}, {@code new Usage("prise_en_charge", …)}) ou
     * déclarés par {@code @Table(name = "patients")} sur une entité JPA.
     */
    private static final Pattern LITTERAL_DE_TABLE = Pattern.compile(
            "(?:new\\s+(?:Table|Usage)\\s*\\(|@Table\\s*\\(\\s*name\\s*=\\s*)\"([A-Za-z_][A-Za-z0-9_]*)\"");

    /** Troisième détection : l'accès par JPA aux entités historiques du patient et de l'assuré (aucun SQL dans le source). */
    private static final Pattern ENTITE_JPA = Pattern.compile(
            "\\b(PatientJpaEntity|AssureJpaEntity|AssurePatientJpaEntity|PatientJpaRepository|AssureJpaRepository|AssurePatientJpaRepository)\\b");

    private static final String PLACE ="2026-11-04 : colonnes de PLACE de patients, propriété de la Planification (ADR 0052) ; "
            + "échéance : inversion de la propriété de la place, hors périmètre (aucune échéance)";
    private static final String CYCLE = "2026-11-04 : dépendance Maven CIRCULAIRE (contexts-patient-couverture dépend de "
            + "contexts-planification) ; échéance : extraction des interfaces publiées dans un module d'API partagé (le port est prêt)";
    private static final String HORS_VERROU = "2026-11-05 : écrivain hors module et hors verrou du centre, à lancer hors activité du centre "
            + "(ADR 0077 point 12, ADR 0078 point 10) ; échéance : reprise de la migration par le module";
    private static final String JOINTURE ="2026-11-04 : jointure avec les tables du contexte consommateur dans un GROUP BY / un tri "
            + "(la découper changerait le résultat) ; échéance : reprise du contexte consommateur";

    /** Lecteurs et écrivains restants hors du contexte (chemin depuis {@code backend/}) : jalons acceptés le 2026-11-04. */
    private static final Map<String, String> JALONS = jalons(
            "contexts/planification/src/main/java/com/hemodialyse/backend/contexts/planification/infrastructure/acl/legacypatient/PlacesDuCentreJdbc.java", PLACE + " ; ÉCRIT la place",
            "contexts/planification/src/main/java/com/hemodialyse/backend/contexts/planification/infrastructure/acl/legacypatient/PlacesTenuesJdbc.java", PLACE + " ; ÉCRIT la place",
            "contexts/planification/src/main/java/com/hemodialyse/backend/contexts/planification/infrastructure/acl/legacypatient/MouvementsDesPatientsJdbc.java", PLACE + " ; ÉCRIT le mouvement PLACE_LIBEREE",
            "contexts/planification/src/main/java/com/hemodialyse/backend/contexts/planification/infrastructure/acl/legacypatient/PlanningDesPlacesDuCentreJdbc.java", PLACE,
            "contexts/planification/src/main/java/com/hemodialyse/backend/contexts/planification/infrastructure/acl/legacypatient/SejoursDesPatientsJdbc.java", PLACE,
            "contexts/seance/src/main/java/com/hemodialyse/backend/contexts/seance/infrastructure/persistence/LecturesDesSeancesRealiseesJdbc.java",
            "2026-11-08 (déplacée de planification/.../legacyseance/SeancesRealiseesJdbc, ADR 0098, SE7d) : jointure séances x patients sur "
                    + "l'identifiant ET le centre (KB-SEA-80, reproduit) publiée à la Planification avec le nom et le prénom de la fiche ; "
                    + "échéance : résidu D-R3, langage publié du Patient, revue au plus tard le 2026-12-15, butoir 2027-03-31",
            "contexts/seance/src/main/java/com/hemodialyse/backend/contexts/seance/infrastructure/persistence/SeancesRealiseesDesCentresJdbc.java",
            "2026-11-08 (déplacée de platform/.../DirectionBreakdownQueryService.loadCaisseSeances, ADR 0099, SE7e) : séances réalisées par "
                    + "caisse, jointure séances x patients sur l'identifiant ET le centre (KB-SEA-83, reproduit) et chaîne payeur / agence / caisse "
                    + "sans centre (KB-SEA-84) dans un GROUP BY (la découper changerait le résultat), publiée au pilotage de la Direction ; "
                    + "échéance : résidu E-R2, langage publié du Patient (caisse de la fiche), revue au plus tard le 2026-12-15, butoir 2027-03-31",
            "contexts/planification/src/main/java/com/hemodialyse/backend/contexts/planification/infrastructure/persistence/LectureDesPreferencesJdbc.java", PLACE,
            "contexts/planification/src/main/java/com/hemodialyse/backend/contexts/planification/infrastructure/acl/legacypatient/ContraintesDesPatientsJdbc.java",
            "2026-11-04 : lecture de la fiche par la Planification (transporteurs, date de naissance : règle des 18 ans, RG-PLN-097), pas une lecture de fiche publiée ; les abords vasculaires viennent de dossier-medical.v1 depuis DM9 (ADR 0101) ; échéance : langage publié du Patient",
            "contexts/planification/src/main/java/com/hemodialyse/backend/contexts/planification/infrastructure/acl/legacypatient/NomsDesPatientsJdbc.java", CYCLE + " (port FichesDesPatients.noms)",
            "contexts/planification/src/main/java/com/hemodialyse/backend/contexts/planification/infrastructure/acl/legacypatient/PatientsDuCentreJdbc.java", CYCLE + " (port FichesDesPatients.situation)",
            "contexts/seance/src/main/java/com/hemodialyse/backend/contexts/seance/infrastructure/persistence/FacturationDesSeancesJdbc.java",
            "2026-11-08 (déplacée de SeancesFacturablesJdbc, ADR 0097, SE7c2) : jointure séances x patients dans un tri nom/prénom et la lecture "
                    + "du payeur, publiées à la Facturation (la découper changerait l'ordre) ; la partie PEC passe déjà par le langage publié ; "
                    + "échéance : résidu C2-R6, langage publié du Patient, revue au plus tard le 2026-12-15, butoir 2027-03-31",
            "contexts/facturation/src/main/java/com/hemodialyse/backend/contexts/facturation/infrastructure/persistence/SituationsDesFacturesJdbc.java", JOINTURE + " : six tables de Facturation, KB-FAC-49",
            "contexts/pilotage-direction/src/main/java/com/hemodialyse/backend/contexts/pilotagedirection/infrastructure/persistence/PatientsDesCentresJdbc.java",
            "2026-11-09 (déplacée de platform/.../DirectionDashboardQueryService et DirectionBreakdownQueryService, PD1, ADR 0102) : effectif de "
                    + "la période (prédicat RG-PAT-032), sexe et naissance, patients par caisse (jointure patients x centre payeur x agence x caisse "
                    + "dans un GROUP BY : la découper changerait le résultat) et prescriptions d'EPO / de fer, agrégats anonymes de la Direction, "
                    + "requêtes reprises mot pour mot ; échéance : lecture publiée de l'effectif par le Patient, revue au plus tard le 2026-12-15, "
                    + "butoir 2027-03-31",
            "contexts/seance/src/main/java/com/hemodialyse/backend/contexts/seance/infrastructure/acl/legacypatient/PlanningDesPatientsJdbc.java",
            "2026-11-08 (déplacée de platform/.../persistence/adapter/SeancePlanningJdbcAdapter, SE7f, ADR 0100) : jours de dialyse, sommeil, état et "
                    + "dates de la fiche AU CENTRE pour le scan (requête reprise mot pour mot ; KB-SEA-95) ; " + PLACE + " (jours de dialyse) ; "
                    + "échéance : lecture publiée du Patient (programmation et situation d'une fiche), revue au plus tard le 2026-12-15, butoir 2027-03-31",
            "platform/src/main/java/com/hemodialyse/backend/infrastructure/persistence/adapter/migration/MigrationRollbackJdbcAdapter.java",
            "2026-11-04 : outil de migration, écrit/supprime en bloc (patients, assure, assure_patient) ; à lancer hors activité du centre (ADR 0077 point 12, ADR 0078 point 10) ; échéance : reprise de la migration par le module",
            // ---- accès que la première version de la garde ne voyait pas (SQL construit, JPA), ajoutés par la revue du 2026-11-05
            "platform/src/main/java/com/hemodialyse/backend/infrastructure/persistence/adapter/migration/HistoricalRecordJdbcAdapter.java", HORS_VERROU
                    + " : INSERT construit (« INSERT INTO » + nom de table) sur attestation_droit et prise_en_charge, import de la migration historique",
            "platform/src/main/java/com/hemodialyse/backend/infrastructure/persistence/adapter/referential/ReferentialAdminTables.java",
            "2026-11-05 : descripteurs des référentiels (forfait, créneau, salle, médecin…) dont les « usages » nomment prise_en_charge et patients ; "
                    + "ReferentialAdminJdbcAdapter les lit en COUNT dynamique (« SELECT COUNT(*) FROM » + table) pour refuser la suppression d'un "
                    + "référentiel utilisé ; échéance : reprise du référentiel d'administration (organisation.v1) ou port d'usage publié",
            "contexts/seance/src/main/java/com/hemodialyse/backend/contexts/seance/infrastructure/persistence/HistoriqueDesSeancesJdbc.java",
            "2026-11-08 (déplacée de platform/.../acl/seance/HistoriqueFourniLegacy, SE7f, ADR 0100) : historique des séances (SE1, ADR 0088) : jointure "
                    + "séances-patients avec filtre, tri et pagination en SQL (la découper changerait le résultat), requête reprise mot pour mot ; "
                    + "échéance : lecture publiée jointe du Patient, revue au plus tard le 2026-12-15, butoir 2027-03-31",
            "contexts/seance/src/main/java/com/hemodialyse/backend/contexts/seance/infrastructure/acl/legacypatient/PatientsDuJourJdbc.java",
            "2026-11-08 : journal du jour (SE7a, ADR 0094, résidu R2) : jointure séances-patients (code, nom, prénom) avec tri nom/prénom en SQL (la "
                    + "découper changerait l'ordre rendu : collation de la base), reprise à l'identique de SeanceRestController (parité) ; "
                    + "échéance : lecture publiée du Patient (code, nom, prénom d'un lot de patients), revue au plus tard le 2026-12-15, butoir 2027-03-31",
            "contexts/seance/src/main/java/com/hemodialyse/backend/contexts/seance/infrastructure/acl/legacypatient/PatientsDuTableauDeBordJdbc.java",
            "2026-11-08 : tableau de bord mensuel des séances (SE7b, ADR 0095, résidu R1) : programmation hebdomadaire, état et sommeil des patients du "
                    + "centre, jointure séances-patients des présents (sans filtre du centre du patient : KB-SEA-64, reproduit), identités du détail "
                    + "nominatif (KB-SEA-66) ; reprise à l'identique de SeanceRestController (parité) ; échéance : lecture publiée du Patient (effectif "
                    + "et programmation d'un mois), revue au plus tard le 2026-12-15, butoir 2027-03-31",
            "contexts/seance/src/main/java/com/hemodialyse/backend/contexts/seance/infrastructure/persistence/ForfaitsDeSeanceJdbc.java",
            "2026-11-08 (déplacée de platform/.../acl/seance/ForfaitsFournisLegacy, SE7f, ADR 0100) : forfait courant d'une séance (SE1, ADR 0088) : "
                    + "prise en charge couvrant le jour, requête reprise mot pour mot ; échéance : lecture publiée de la couverture "
                    + "(CouvertureDesPatients), revue au plus tard le 2026-12-15, butoir 2027-03-31");

    private static Map<String, String> jalons(String... couples) {
        Map<String, String> m = new TreeMap<>();
        for (int i = 0; i < couples.length; i += 2) m.put(couples[i], couples[i + 1]);
        return m;
    }

    private static Path backend() {
        return Path.of("..").toAbsolutePath().normalize();
    }

    private static String chemin(Path p) {
        return p.toString().replace('\\', '/');
    }

    private static String relatif(Path backend, Path p) {
        return chemin(backend.relativize(p));
    }

    private static Stream<Path> sources(Path racine) {
        try {
            return Files.walk(racine).filter(p -> p.toString().endsWith(".java"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Racines {@code src/main/java} de tout le backend hors du contexte patient-couverture. */
    private static List<Path> racinesHorsDuContexte(Path backend) throws IOException {
        try (Stream<Path> dossiers = Files.list(backend)) {
            List<Path> modules = dossiers.filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().equals("contexts")).toList();
            List<Path> racines = new java.util.ArrayList<>(modules.stream().map(p -> p.resolve("src/main/java"))
                    .filter(Files::isDirectory).toList());
            try (Stream<Path> contextes = Files.list(backend.resolve("contexts"))) {
                racines.addAll(contextes.filter(p -> !p.getFileName().toString().equals("patient-couverture"))
                        .map(p -> p.resolve("src/main/java")).filter(Files::isDirectory).toList());
            }
            return racines;
        }
    }

    private static boolean mentionneUneTable(Path source) {
        try {
            String contenu = Files.readString(source);
            Matcher m = LECTURE_SQL.matcher(contenu);
            while (m.find()) {
                if (TABLES_DU_PATIENT_COUVERTURE.contains(m.group(2).toLowerCase())) return true;
            }
            Matcher litteral = LITTERAL_DE_TABLE.matcher(contenu);
            while (litteral.find()) {
                if (TABLES_DU_PATIENT_COUVERTURE.contains(litteral.group(1).toLowerCase())) return true;
            }
            return ENTITE_JPA.matcher(contenu).find();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Set<String> sourcesFautives(Path backend, List<Path> racines) {
        Set<String> fautives = new TreeSet<>();
        racines.stream().filter(Files::isDirectory).flatMap(TableDuPatientCouvertureTest::sources)
                .filter(TableDuPatientCouvertureTest::mentionneUneTable).forEach(p -> fautives.add(relatif(backend, p)));
        return fautives;
    }

    @Test
    void aucun_nouveau_lecteur_sql_des_tables_du_patient_couverture_hors_de_la_liste_blanche() throws IOException {
        Path backend = backend();
        Set<String> nouveaux = new TreeSet<>(sourcesFautives(backend, racinesHorsDuContexte(backend)));
        nouveaux.removeAll(JALONS.keySet());

        assertThat(nouveaux)
                .as("nouveaux lecteurs SQL des tables de Patient & couverture : passer par patientcouverture.v1 (FichesDesPatients, "
                        + "CouvertureDesPatients, CompteursDeCouverture) ou les inscrire (nommés, datés, avec raison et échéance)")
                .isEmpty();
    }

    @Test
    void aucune_entree_morte_dans_la_liste_blanche() throws IOException {
        Path backend = backend();
        Set<String> lecteurs = sourcesFautives(backend, racinesHorsDuContexte(backend));
        Set<String> mortes = new TreeSet<>(JALONS.keySet());
        mortes.removeAll(lecteurs);

        assertThat(mortes)
                .as("entrées mortes de la liste blanche (fichier disparu ou migré vers le langage publié) : réduire la liste")
                .isEmpty();
    }

    @Test
    void chaque_jalon_porte_une_date_et_une_echeance() {
        assertThat(JALONS.values()).allSatisfy(raison -> {
            // Jalons historiques : 2026-11-04, 05 ou 07, inchangés. Exceptions : les entrées du 2026-11-08 (SE7a, ADR 0094, R2 ; SE7c2,
            // ADR 0097, C2-R6, entrée déplacée), qui doivent porter leur revue (2026-12-15) ET leur butoir propre (2027-03-31).
            if (raison.startsWith("2026-11-08") || raison.startsWith("2026-11-09")) {
                assertThat(raison).contains("revue au plus tard le 2026-12-15").contains("butoir 2027-03-31");
            } else {
                assertThat(raison).matches("(?s)2026-11-0[457].*");
            }
            assertThat(raison).contains("échéance");
        });
    }

    @Test
    void la_garde_detecte_une_lecture_directe_meme_sur_deux_lignes_et_ignore_les_autres_tables() throws IOException {
        Path bac = Files.createTempDirectory("garde-patient-couverture-pc5");
        Files.writeString(bac.resolve("LectureDirecte.java"), """
                class LectureDirecte {
                    String lecture = "SELECT a.id FROM patients a WHERE a.center_id = ?";
                    String ecriture = "UPDATE prise_en_charge SET statut = ? WHERE id = ?";
                    String legitime = "SELECT s.id FROM seances s WHERE s.center_id = ?";
                }
                """);
        Files.writeString(bac.resolve("LectureSurDeuxLignes.java"), """
                class LectureSurDeuxLignes {
                    String lecture = "SELECT COUNT(1) FROM "
                            + "attestation_droit WHERE center_id = ?";
                    String jointure = \"""
                            SELECT s.id
                            FROM seances s
                            JOIN assure_patient ap ON ap.patient_id = s.patient_id
                            \""";
                }
                """);

        assertThat(sourcesFautives(bac, List.of(bac))).containsExactlyInAnyOrder("LectureDirecte.java", "LectureSurDeuxLignes.java");

        Path bacPropre = Files.createTempDirectory("garde-patient-couverture-propre");
        Files.writeString(bacPropre.resolve("LectureLegitime.java"), """
                class LectureLegitime {
                    String legitime = "SELECT s.id FROM seances s WHERE s.center_id = ?";
                }
                """);

        assertThat(sourcesFautives(bacPropre, List.of(bacPropre))).as("une table d'un autre contexte n'est pas signalée").isEmpty();
    }

    /** Test négatif (revue du 2026-11-05) : un lecteur à SQL CONSTRUIT ou en JPA doit faire échouer la garde. */
    @Test
    void la_garde_detecte_un_acces_construit_un_descripteur_de_table_et_un_acces_jpa() throws IOException {
        Path bac = Files.createTempDirectory("garde-patient-couverture-dynamique");
        Files.writeString(bac.resolve("SqlConstruit.java"), """
                class SqlConstruit {
                    static final Table T = new Table("attestation_droit", java.util.Set.of("patientId"), true, false);
                    void ecrit(String table) { jdbc.update("INSERT INTO " + table + " (id) VALUES (?)"); }
                }
                """);
        Files.writeString(bac.resolve("Usages.java"), """
                class Usages {
                    java.util.List<Usage> usages = java.util.List.of(new Usage("prise_en_charge", "forfait_demande_id"));
                }
                """);
        Files.writeString(bac.resolve("EntiteJpa.java"), """
                @Table(name = "patients")
                class EntiteJpa {
                }
                """);
        Files.writeString(bac.resolve("RechercheJpa.java"), """
                class RechercheJpa {
                    Root<PatientJpaEntity> patient = query.from(PatientJpaEntity.class);
                }
                """);

        assertThat(sourcesFautives(bac, List.of(bac)))
                .containsExactlyInAnyOrder("SqlConstruit.java", "Usages.java", "EntiteJpa.java", "RechercheJpa.java");

        Path bacPropre = Files.createTempDirectory("garde-patient-couverture-dynamique-propre");
        Files.writeString(bacPropre.resolve("Etiquettes.java"), """
                class Etiquettes {
                    Resolved r = new Resolved("SEROLOGIE_ENREGISTREE", "patients", "Sérologie enregistrée");
                    java.util.Map<String, Object> m = java.util.Map.of("patients", 3);
                    Table autre = new Table("forfait", java.util.List.of(), java.util.List.of(), java.util.List.of(), "t.code", java.util.List.of(), java.util.List.of());
                    Root<SeanceJpaEntity> seance = query.from(SeanceJpaEntity.class);
                    Object dossier = DossierMedicalPatientJpaEntity.class;
                }
                """);

        assertThat(sourcesFautives(bacPropre, List.of(bacPropre))).as("libellés, autres tables et entités voisines ne sont pas signalés")
                .isEmpty();
    }

    @Test
    void la_liste_des_tables_couvre_les_tables_lues_par_le_patient_couverture() throws IOException {
        Path contexte = backend().resolve("contexts/patient-couverture/src/main/java");

        Set<String> luesParLeContexte = new TreeSet<>();
        try (Stream<Path> sources = sources(contexte)) {
            for (Path source : sources.toList()) {
                Matcher m = LECTURE_SQL.matcher(Files.readString(source));
                while (m.find()) luesParLeContexte.add(m.group(2).toLowerCase());
            }
        }
        Set<String> effectivementLues = new TreeSet<>(luesParLeContexte);
        effectivementLues.retainAll(TABLES_DU_PATIENT_COUVERTURE);

        assertThat(TABLES_DU_PATIENT_COUVERTURE)
                .as("toute table lue en SQL par le contexte patient-couverture doit être listée")
                .containsAll(effectivementLues);
    }
}
