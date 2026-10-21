---
status: proposed
date: 2026-10-21
slice: facturation-f5-consultation-pilotage
sources: [.ddd/migration/roadmap.md § 1 ter ligne F5, .ddd/tactical/facturation.md, safety-net gmao-g6 / facturation-f2..f4 (méthode, harnais), lecture de FacturationRestController, FacturationApplicationService, SeanceFacturationJdbcAdapter, FacturationSyntheseMensuelleReportService, DocumentRestController (voie documents), Direction*QueryService, grep ciblé des lecteurs de factures / facture_lignes / facture_reglements]
---
# Filet de caractérisation — F5 consultation et pilotage de la facturation (refonte sur place, ADR 0024/0030..0033)

Lectures seules : aucune route de F5 n'écrit de facture, de ligne ni de règlement. Des chiffres d'argent exacts sont pourtant servis (CA HT/TTC,
encaissé, reste à recouvrer, groupes par caisse et par situation) : ils sont verrouillés à la décimale, avec arrondis, tri, bornes et jeux vides.

## Périmètre exact
| Route | Classes legacy | Verrouillé |
|---|---|---|
| `GET /api/v1/facturation/dashboard?centerId&month=yyyy-MM` (ADMIN, MEDECIN, SECRETAIRE ; MEDECIN seul refusé par `RoleScopeFilter`) | `FacturationRestController.dashboard` → `FacturationApplicationService` (`@Transactional(readOnly)`) → `FacturationDomainService` (seule règle : mois obligatoire) → `SeanceFacturationPort` / `SeanceFacturationJdbcAdapter` (SQL `factures`, `seances`, `agence`, `caisse_assurance`) ; `bc-facturation` : `FacturationUseCase`, `FacturationDashboardQuery/Result/Bucket/StatusBucket` | oui (12 tests) |
| `POST /api/v1/facturation/synthese/print` (mêmes profils) | `FacturationRestController.printSynthese` → `FacturationSyntheseMensuelleReportService` (SQL de KPI et répartitions, **gabarit JRXML construit à la volée** : une colonne HT et TTC par caisse, fichier temporaire compilé par `JasperReportService`), `FacturationSynthesePrintRequest` | oui (14 tests) |
| `POST /api/v1/documents/print` avec `typeDocument = SYNTHESE_FACTURATION_MENSUELLE` (voie « documents », **seconde implémentation** de la même synthèse) | `DocumentRestController.print` (module Documents, reste hors slice) + gabarit statique `reports/synthese_mensuelle_facturation.jrxml` (SQL sur `factures`/`facture_lignes`) | contenu financier du gabarit uniquement (5 tests) |
| `GET /api/v1/direction/overview`, `/breakdown`, `/absences` (rôle DIRECTION) | `DirectionDashboardQueryService`, `DirectionBreakdownQueryService`, `DirectionAbsencesQueryService` (SQL `factures`, `facture_reglements`, `seances`) | **part financière seulement** (12 tests) |
| Cohérence avec les écritures du nouveau module | `POST /facturation/validate` (F3), `POST /reglements/{id}/paiements` (F4) puis lectures F5 | 3 tests |
Aucune route de consultation de facture individuelle ne reste dans le legacy (la liste, la situation et l'historique des factures sont servis par F4). Le reporting
« PDF direction » (`DirectionReportPdfService`) n'est pas relu ici : il reprend les lectures ci-dessus (G6 l'a déjà noté sans donnée GMAO).

## Point d'observation et harnais
HTTP via MockMvc sur le contexte Spring complet (`@SpringBootTest` MOCK, profil `test`, Spring Security et `RoleScopeFilter` réels, **H2**) ; fixtures SQL à valeurs maîtrisées.
Harnais : `backend/platform/src/test/java/com/hemodialyse/backend/characterization/facturation/pilotage/PilotageFacturationSupport.java` (identités ADMIN/SECRETAIRE/MEDECIN/INFIRMIER/DIRECTION, société, centres, caisses, agences, patients, factures, lignes, séances, règlements, goldens JSON normalisés, texte PDF par PDFBox) ; `GoldenMaster` de G1 réutilisé.
**Aucun mock du code caractérisé, aucun seam, aucun code de production modifié.**
- Alias : tout identifiant de fixture est NOMMÉ (`<CENTRE_A>`, `<FACTURE_n>`, `<CAISSE_1>`…) ; les identifiants non nommés deviennent `<UUID_GENERE_n>` et ne doivent pas apparaître (vérifié : aucun dans les goldens). **Alias de dates : un seul, `<AUJOURD_HUI>`** (jour d'exécution, défauts de période) ; toutes les dates de fixture sont des dates fixes de 2026 ; les horodatages sont remplacés par `<HORODATAGE>`. Aucune valeur de golden ne dépend du jour ni de l'année d'exécution (la date de facturation d'une facture émise par `/validate` est ramenée par SQL à `2026-01-31` avant lecture).
- PDF sans seam : type, `Content-Disposition`, signature `%PDF-`, nombre de pages et **texte extrait** (le PDF de la synthèse ne contient aucun horodatage d'édition ; rien à masquer). Excel : contenu des cellules ; HTML : PDF embarqué en base64 (seule la présence est verrouillée).
- Goldens : `backend/platform/src/test/resources/characterization/facturation/pilotage/{tableau-de-bord,synthese-imprimee,synthese-modele-document,lectures-direction,coherence-nouveau-module}/*.approved.json` (56 fichiers).

## Scénarios (46 tests, 5 classes)
| Classe | Tests | Scénarios |
|---|---|---|
| `TableauDeBordFacturationCharacterizationIntegrationTest` | 12 | février 2026 (5 factures dont bornes 01 et 28, une sans caisse ni situation, une situation blanche ; hors mois 31/01 et 01/03 ; centre B ; séances facturées avec/sans facture, validée, de janvier rattachée à une facture de février) ; janvier et mars isolés ; **aucun filtre de centre** (A lit B, B lit A) ; mois vide, centre vide, centre inconnu ; classement des caisses et situations ; lignes et règlements sans effet ; totaux stockés non recalculés (TTC ≠ HT + TVA) ; février 2024 bissextile et décembre 2025 ; 14 formats de mois ; 6 cas de `centerId` ; 12 profils/méthodes ; 4 refus (corps JSON de `ROLE_SCOPE`) |
| `SyntheseMensuelleImprimeeCharacterizationIntegrationTest` | 14 | tableau forfait x caisse HT/TTC, totaux, indicateurs, répartitions (février 2026, 3 caisses, TVA 19 / 9 / 1,25 / 0 %, arrondis à la paire) ; jour unique, bornes inclusives, deux années ; période sans facture, centre vide ; centre B vu par ADMIN A ; deux caisses de même code ; barres de répartition (2,5 -> 3 ; 7,5 -> 8) ; libellés avec apostrophe, `]]>`, `&`, `<b>` ; 6 caisses (page élargie) ; 13 formats ; format absent ; classeur Excel ; 13 refus de corps/période ; 10 profils ; fichiers temporaires supprimés |
| `SyntheseViaModeleDeDocumentCharacterizationIntegrationTest` | 5 | gabarit statique : colonnes CNAS / CASNOS / CAMSSP / Autres (préfixes, casse), totaux globaux non formatés, période sans facture, centre B vu par ADMIN A, refus (pas de modèle, modèle désactivé, période absente/inversée/illisible), formats, 9 profils |
| `LecturesFinancieresDirectionCharacterizationIntegrationTest` | 12 | vue d'ensemble de février 2026 (CA HT/TTC, encaissé dont règlement postérieur et remboursement, reste, taux, mensuel, période précédente) ; janvier-février ; un jour ; société 2 isolée ; taux à 0,0 et 125,0 ; périodes (inversée, 5 ans exactement, 5 ans + 1 jour, illisible, `from` futur) sur les trois routes ; défauts de période ; CA par caisse (agence figée, ligne sans caisse) ; part du CA des absences ; 17 profils/comptes (dont détaché de sa société, sans société dans le jeton) ; société sans facture ; société sans centre |
| `CoherenceAvecLesEcrituresDuNouveauModuleCharacterizationIntegrationTest` | 3 | validation F3 (3 factures, 5 séances) puis tableau de bord et synthèse ; règlement F4 de 50,00 puis vue d'ensemble Direction (encaissé, reste, taux) sans effet sur le tableau de bord ; séances passées à `FACTUREE` |

## Mesures
- **Suite** : 3 exécutions consécutives vertes, **46/46** (la 1re en ordre par défaut, les 2 autres avec `MethodOrderer$Random`), 0 flaky. Durée ≈ 1 min 30 par exécution, dont ≈ 35 s de démarrage Spring.
- **Mutation PIT 1.30.0** : 92/105 mutants tués (88 %), voir la section « Mutation ».
- **Couverture de branches** : JaCoCo n'est pas configuré dans le projet (comme G1 à G6 et F1 à F4). Sont exercés par un scénario nommé : les deux routes, les 3 formats de sortie de la synthèse (`PDF`, `EXCEL`/`XLS`/`XLSX`, `HTML`) + format absent/blanc/inconnu, chaque `IllegalArgumentException` du service d'impression, les valeurs `null` de caisse et de situation, la branche « liste vide » de `buildDistributionGraphText`, les deux gabarits, les 3 lectures Direction (bornes, société sans facture, société 2), le refus du `RoleScopeFilter` et de `@PreAuthorize`.

## Mutation
**PIT 1.30.0** (`backend/scripts/pit-f5-pilotage.sh`, `--mutators DEFAULTS`, tests = les classes HTTP du tableau de bord, de la synthèse, de la voie documents et de la Direction ; aucune classe de calcul de domaine pur ne subsiste dans `bc-facturation` : `FacturationDomainService` ne porte qu'une garde, les calculs sont dans les services de `platform`) sur `FacturationDomainService`, `DirectionDashboardQueryService` et `FacturationSyntheseMensuelleReportService` : **105 mutants, 92 tués (88 %)**, force des tests 88 %, couverture de lignes de la zone 99 %. Cible ≥ 70 % atteinte. Une première passe (avant l'ajout de « société sans centre » et « caisses de même code ») : 87/105 (83 %).
Les 13 survivants sont équivalents ou injoignables par HTTP : `bar` L502 (`max <= 0` jamais vrai : les lignes existent, donc max >= 1), `buildDistributionGraphText` L476-477 (message « Aucune donnee » jamais imprimé : Jasper n'imprime rien sans ligne de détail, KB-FAC-58), `buildDynamicTemplate` L219, L342, L352 (abscisses et largeurs des zones de texte : rendu visuel non verrouillé), `cdataSafe` L394 (libellé jamais nul : `COALESCE`), `generate` L34 et L37 (gardes `centerId`/période nuls : déjà refusés par `@Valid` avant le service), `loadCaisses` L122 (colonne `INCONNU` de repli, sans effet sans ligne), `textCellString`/`textCellNumber` L372/L381 (alignement à droite : visuel). Aucun mutant de `FacturationDomainService` ne survit.

## Lancement
Depuis `backend/` : `./mvnw -o -pl platform -am test '-Dtest=<liste des 5 classes en nom complet>' -Dsurefire.failIfNoSpecifiedTests=false` avec les noms complets
`com.hemodialyse.backend.characterization.facturation.pilotage.{TableauDeBordFacturationCharacterizationIntegrationTest,SyntheseMensuelleImprimeeCharacterizationIntegrationTest,SyntheseViaModeleDeDocumentCharacterizationIntegrationTest,LecturesFinancieresDirectionCharacterizationIntegrationTest,CoherenceAvecLesEcrituresDuNouveauModuleCharacterizationIntegrationTest}` (**un motif de paquet `…pilotage.*` ne sélectionne aucun test**). Ordre aléatoire : ajouter `'-DargLine=-Djunit.jupiter.testmethod.order.default=org.junit.jupiter.api.MethodOrderer$Random'`. Approbation après revue humaine du diff, sur la seule classe visée : `'-DargLine=-Dcharacterization.approve=true'`. Mutation : `./mvnw -o -q -pl platform -am test-compile`, `./mvnw -o -q -pl platform -am dependency:build-classpath -Dmdep.outputFile=<fichier>`, puis `CPFILE=<fichier> bash backend/scripts/pit-f5-pilotage.sh`.

## Seams
Aucun appliqué ni nécessaire ; `seamAllowed` inchangé (vide). Trois seams optionnels décrits dans `seams.md` (horloge des services Direction, gabarit Jasper en mémoire, extraction de `bar`) : accord utilisateur requis, non demandé.

## Bugs connus
KB-FAC-52 à 66 (`known-bugs.md`) : aucune isolation de centre, erreurs de protocole en 500, séances facturées ≠ répartitions, TTC de la synthèse recalculé et arrondi à la paire, indicateurs et tableau sur des bases différentes, PDF vide sans facture, codes d'erreur hétérogènes, voie documents sans contrôle de profil, deux gabarits divergents, encaissé de la Direction, périodes, échelle JSON, CA par caisse (agence figée) contre séances (payeur actuel), part du CA des absences.

## Lecteurs et consommateurs (listes exactes des lectures de `factures`, `facture_lignes`, `facture_reglements`, `seances.facture_id/statut`)
| Lecteur | Lit | Filet |
|---|---|---|
| `SeanceFacturationJdbcAdapter.loadDashboard` | `factures` (SUM total_ttc/total_ht, COUNT, COUNT DISTINCT patient_id), `seances` (statut `FACTUREE`, `facture_id`), `agence`, `caisse_assurance` | **F5** (tableau de bord) |
| `FacturationSyntheseMensuelleReportService` (4 requêtes + SQL du gabarit dynamique) | `factures`, `facture_lignes` (forfait_label, line_ht, tva_rate), `seances` (facture_id), `agence`, `caisse_assurance` | **F5** |
| `reports/synthese_mensuelle_facturation.jrxml` via `DocumentRestController.print` | `factures`, `facture_lignes`, `agence`, `caisse_assurance` | **F5** (contenu) |
| `DirectionDashboardQueryService.overview/previousPeriodTotals/monthly` | `factures` (COUNT, SUM total_ht/ttc par centre et par mois), `facture_reglements` joint `factures` (SUM montant), `seances` | **F5** (part financière) |
| `DirectionBreakdownQueryService.loadCaisseCa` | `factures` joint `agence`, `caisse_assurance` (CA HT par caisse), `seances` | **F5** (CA par caisse) |
| `DirectionAbsencesQueryService.absences` | `factures` (SUM total_ht par centre) pour `partCaHt` | **F5** (CA HT) |
| **Reporting** : `DirectionReportPdfService` | reprend les trois services Direction | hors F5 (aucune requête propre sur les factures) |
| **Comptabilité** : `FacturesAComptabiliserJdbc`, `ReglementsAComptabiliserJdbc` | `factures` (numéro, payeur, totaux, `date_facturation`, `period_end`), `facture_reglements` | C5 (déjà verrouillé) |
| **Facturation (F2-F4)** : `FacturesJdbc`, `Factures`, `ReglementsJdbc`, `Reglements`, `SituationsDesFacturesJdbc` | écritures et lectures du nouveau module | F3, F4 |
| **Stock** : `SeanceBillingStatusJdbcAdapter.isBilled` (via `SeanceBillingStatusPort`, `BonSortieService`) | `seances.statut = 'FACTUREE'` seulement (aucune table de factures) | `BonSortieServiceTest` (unitaire) ; pas de HTTP : hors F5 |
| **Absences** : `AbsenceDonneesJdbcAdapter.periodeFacturee` | `factures` (`patient_id`, `period_start <= date <= period_end`) | `AbsencePatientIntegrationTest` ; hors F5 |
| **Migration** : `OpeningBalanceJdbcAdapter` (écrit facture de reprise, lignes, règlements), `MigrationRollbackJdbcAdapter` (lit/supprime `factures`, `facture_lignes`, `facture_reglements`, `seances.facture_id`) | écrivains : hors F5 | tests de `bc-migration` ; non rejoués ici |
| `ReferentialAdminTables` | usage de `facture_lignes.forfait_id` (blocage de suppression d'un forfait) | F1 |
Seules ces classes lisent les tables de factures ; une recherche des mots `FROM factures`, `JOIN factures`, `facture_lignes`, `facture_reglements` dans `contexts/*`, `platform` et `bc-*` ne trouve aucun autre lecteur.

## Non verrouillé (limites)
- **Rendu visuel** de la synthèse (mise en page, couleurs, polices, découpe en pages d'un tableau très long) ; PDF octet par octet (métadonnées instables) ; contenu binaire exact du `.xlsx` (cellules seulement) ; HTML (présence du PDF embarqué seulement).
- **Gabarit personnalisé** (`modele_document` d'un centre avec JRXML propre) pour `SYNTHESE_FACTURATION_MENSUELLE` ; autres types de documents de `/documents/print`, `printById`, liste des modèles (module Documents, hors slice).
- **Parties non financières des lectures Direction** : patients, effectif, âge, sexe, anémie, motifs d'absence, masquage d'anonymat (seuil 5) au-delà de ce que montrent les goldens ; `/direction/indicators`, `/report`, `/alerts/history`, `/stock-groupes`, `/gmao`, `/capacite` (G6, `DirectionIntegrationTest`, `DirectionBreakdownIntegrationTest`).
- **Performance** (p95, 1 requête SQL par centre et par mois), **PostgreSQL** (arrondis de `SUM`, échelle des `NUMERIC`, ordre sans `ORDER BY`, `EXTRACT`, `CAST(... AS UUID)` du gabarit) : non testés (H2 seulement ; rejeu PostgreSQL 16 à prévoir comme `facturation-pg16-rejeu.sh`).
- **Concurrence** : lecture pendant une validation ou un règlement (lectures non transactionnelles entre les requêtes d'un même tableau de bord) : non testé.
- **Défauts de période qui dépendent du jour** de la Direction : vérifiés par assertion relative, pas par golden. Fuseau du serveur (`LocalDate.now()` contre `LocalDate.now(UTC)` des absences) : non exercé.
- **Front** (`facturation-api.service.ts`, écrans de tableau de bord, impression) : non observé (e2e à la charge de l'utilisateur).
- **Écrivains** `OpeningBalanceJdbcAdapter` / `MigrationRollbackJdbcAdapter` : hors F5.

## Classes legacy de facturation restantes et ce qui bloque leur retrait
`bc-facturation` (module Maven : `platform/pom.xml:57`, parent `pom.xml:35,106`) :
| Classe | Utilisée par | Retrait |
|---|---|---|
| `port/FacturationDashboardQuery`, `FacturationDashboardResult`, `FacturationDashboardBucket`, `FacturationDashboardStatusBucket` | `FacturationRestController`, `SeanceFacturationPort/JdbcAdapter`, `FacturationDomainService` | **F5** (à porter dans `contexts/facturation`) |
| `port/FacturationUseCase`, `service/FacturationDomainService`, `port/SeanceFacturationPort` ; `platform` : `FacturationApplicationService`, `SeanceFacturationJdbcAdapter`, `FacturationRestController` (restes `dashboard` et `printSynthese`) | tableau de bord | **F5** |
| `aggregate/TypeTVA`, `port/TypeTvaRepositoryPort` ; `platform` : `TypeTvaJdbcAdapter` | plus aucun consommateur de production (seul `MappingTvaConsommateursCharacterizationIntegrationTest` les importe, pour caractériser la Comptabilité C2) | **F6** : retirer avec le test qui les importe (le remplacer par le contexte Facturation publié) |
| Module `bc-facturation` entier | `platform/pom.xml`, `pom.xml` | **F6**, une fois les deux familles ci-dessus retirées |
`platform` à retirer avec F5 : `FacturationSyntheseMensuelleReportService` (déplacer le **rendu** derrière un port, comme le bon d'intervention G6 : le moteur Jasper, `JasperReportService`, `ModeleDocumentCatalog` et `DocumentRestController` restent partagés), `FacturationSynthesePrintRequest`, `reports/synthese_mensuelle_facturation.jrxml` (seconde voie ; décision à prendre) et les tests legacy `FacturationSynthesePrintRestControllerIntegrationTest`, `DocumentRestControllerSyntheseMensuelleIntegrationTest` (partie synthèse).
Fichiers partagés avec F6 que F5 devra modifier (protégés par `covered.json`, accord utilisateur identique aux slices précédentes) : `FacturationRestController` (supprimé quand ses deux routes seront servies par le contexte), `FacturationApplicationService`, `FacturationDomainService`, `FacturationUseCase`, `SeanceFacturationPort`, `SeanceFacturationJdbcAdapter` (déjà couverts par F2/F3), et les trois `Direction*QueryService` (**couverts partiellement** : seules les parties financières sont verrouillées ; toute modification des parties patients/anémie/motifs doit rester hors F5).

## Questions de décision pour la conception sur place de F5
1. **Où vit le tableau de bord ?** Proposition : read model `TableauDeBordDeFacturation` du contexte `facturation` (port de lecture `LectureDuTableauDeBord`, même URL, mêmes tables), `FacturationDomainService` disparaît (sa seule règle, « le mois est obligatoire », devient une validation du contrôleur).
2. **Corriger ou reproduire** : KB-FAC-52 (isolation de centre : comparer `centerId` au centre de session pour ADMIN/SECRETAIRE), KB-FAC-53/59 (400 au lieu de 500), KB-FAC-54 (définition de « séances facturées »), KB-FAC-56 (arrondi du TTC par ligne ou par facture), KB-FAC-58 (message « aucune donnée »), KB-FAC-60 (profil de la voie documents), KB-FAC-62 (encaissé à ce jour ou à la fin de période), KB-FAC-64 (échelle JSON).
3. **Deux gabarits de synthèse** : conserver les deux voies (dynamique par caisse + statique 4 colonnes) ou supprimer la voie statique ? Si la voie documents reste, quel contrat pour le modèle (`modele_document`) ?
4. **Rendu** : garder `FacturationSyntheseMensuelleReportService` (Jasper) derrière un port `ImpressionDeLaSynthese` de `platform`, comme le bon d'intervention, en ne portant dans le contexte que le **read model** des lignes et des indicateurs ?
5. **Direction** : migrer maintenant la **source financière** des trois services vers une lecture publiée (`published-language`, par exemple `SyntheseFinanciereDuCentre` : CA HT/TTC, encaissé, CA par caisse) en laissant services, endpoints et garde de société dans `platform` ; sinon `bc-facturation` ne dépend plus de rien mais les SQL sur `factures` restent dispersés. Ne pas extraire Pilotage direction maintenant.
6. **Base de calcul unique** du chiffre d'affaires : totaux stockés des factures (tableau de bord, Direction) ou somme des lignes (synthèse) ? Écart de 0,41 constaté sur le jeu de février (KB-FAC-56).
7. **Séances par caisse** (Direction) : payeur actuel du patient ou agence figée sur la facture (KB-FAC-65) ?
8. **Seams d'horloge** (Direction) : les appliquer (accord requis) ou conserver l'assertion relative ?
