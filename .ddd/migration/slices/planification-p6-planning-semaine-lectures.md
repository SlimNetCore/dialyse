---
slice: planification-p6-planning-semaine-lectures
status: built (2026-10-27, sessions 1 et 2) ; ADR 0060 proposed ; build complet `./mvnw -o test` vert, check-arch 0 violation, rejeu PostgreSQL 16 vert ; dette consignée avec jalons (roadmap « Dette de P6 »)
date: 2026-10-27
order: 6
context: planification
sources: [.ddd/safety-net/planification-p6-planning-semaine-lectures.md, .ddd/tactical/planification.md (P6), ADR 0049 à 0060, KB-PLN-67 à 76, RG-PLN-036, 040 à 046, 070 à 074]
---

# P6 — Planning de la semaine et lectures publiées (session 1)

Refonte sur place (ADR 0024) : ni façade, ni drapeau, ni projection ; mêmes tables et mêmes URLs. Décisions utilisateur du 2026-10-27 : voir ADR 0060 (400 pour les
entrées illisibles, fuseau du centre, sommeil exclu de la grille via une présence unique, férié = capacité 0, capacité de la Direction masquée par centre et limitée
aux générateurs en service, déplacement temporaire et place figée reproduits en lecture).

## Fait

| Couche | Contenu |
|---|---|
| Domaine | `SejourDuPatient` (l'unique calcul de présence : place tenue / présent / attendu / présent sur une période), `SemaineDuPlanning` (service de domaine : jours, cases, six conflits, déplacements, séances réalisées, patients à replanifier ; sommeil exclu ; capacité 0 un jour fermé) |
| Application | `ConsulterLaSemaine` (date par défaut = aujourd'hui du centre), `ConsulterLaPresenceDesPatients` (port publié `PresenceDesPatients`), `ConsulterLaBaseDeLaCapaciteTheorique` (port publié `BaseDeLaCapaciteTheorique`) ; ports sortants `SejoursDesPatients`, `SeancesRealisees`, `DeplacementsTemporaires`, `SeriesDuCentre` |
| Adapters sortants | ACL `SejoursDesPatientsJdbc` (+ `FinDOccupation.partLeDernierJour`), `SeancesRealiseesJdbc`, `DeplacementsTemporairesJdbc`, `SeriesDuCentreJdbc` ; câblage `PlanificationConfiguration` |
| Adapters entrants | `SemaineControleur` (mêmes URL, droits, JSON ; 400 via `EntreesDeLaPlanificationAdvice`) ; la route `/direction/capacite` rejoint `DatesIllisiblesDeLaDirectionAdvice` |
| GMAO (additif) | `GenerateursInstalles.enServiceParCentre` (port publié, JPA, double, contrat) |
| Absences | `PatientsAttendusJdbc` lit `PresenceDesPatients` (plus de SQL sur `patients`) ; dépendance `contexts-planification` |
| Direction | `DirectionCapaciteQueryService` : ports GMAO + Planification, masquage par centre, `centresMasques`, fuseau par défaut ; frontend : note « centres masqués », type, i18n fr/en/ar/kab, spec |
| Seam Séance | `SeancePlaceJdbcAdapter` lit la place de la fiche par `PlacesDesPatients.deLaFiche` |
| Retraits | `PlanningSemaineRestController`, son test, `GenerateurImpactJdbcAdapter` et `GenerateurImpactPort` (morts depuis P5) |
| Filet | 79/79 scénarios verts sur le nouveau code ; 12 goldens d'écart (`_nouveau_code`, anciens archivés dans `p6-semaine/_ecarts-legacy`, octet pour octet depuis `git show HEAD:`) + 2 goldens GMAO (`gmao/pilotage/_ecarts-legacy`) ; tests `DirectionCapaciteIntegrationTest` et `DirectionGmaoCharacterizationIntegrationTest` mis à jour |

## Parité

| Scénarios du filet | Verts | Écarts décidés |
|---|---|---|
| 79 | 79 | 12 : `date_illisible`, `garde_de_centre`, `periodes` (400), `jour_ferie`, `ferie_sans_libelle`, `cumul_ferme`, `fermeture_exceptionnelle_et_ferie` (capacité 0), `sommeil` (exclu), `file_masquee`, `reponse_complete`, `parametres_par_defaut` (masquage complet), `generateurs_comptes` (`EN_SERVICE`) |

Ordre des patients dans un conflit : tri prénom, nom, identifiant (le legacy n'avait pas d'`ORDER BY`) ; sans effet sur les goldens.

## Preuves

Domaine (`SejourDuPatientTest`, `SemaineDuPlanningTest`), application (`SemaineEtPresenceApplicationTest`), contrôleur (`SemaineControleurTest`), contrat des lectures
(`LecturesDeLaSemaineJdbcContratTest` H2, `LecturesDeLaSemaineJdbcPostgresContractIT` PostgreSQL 16 via `backend/scripts/planification-p6-pg16-rejeu.sh`, port 55466 : 5/5 et
détection des absences 5/5), oracle de la règle de présence `PatientsAttendusJdbcTest` (Absences, 40 cas) inchangé dans ses attentes, ArchUnit du module et `check-arch.ts` : 0 violation,
frontend direction : 66 tests verts, build complet `./mvnw -o test` : 2446 tests (3 rouges au premier passage : 2 goldens/assertions GMAO de la capacité, mis à jour ; 1 test
Absences à regex instable sur contenu aléatoire, `ClotureMensuelleDesAbsencesNouveauCodeIntegrationTest`, vert au rejeu isolé, non lié à P6).
PIT du module (`backend/scripts/pit-p6-module.sh`, domaine + application, 4 min) : 1149 mutants, 1035 tués (90 %), 37 sans couverture, force des tests 93 %, couverture de lignes 95 %.

## Session 2 (2026-10-27)

- `ClotureMensuelleDesAbsencesNouveauCodeIntegrationTest` : instabilité corrigée dans le test (l'extraction des mois lisait « dddd-dd » dans tout le texte, donc des fragments d'UUID : « mois 46 ») ; elle ne cible plus que les valeurs JSON égales à « AAAA-MM ». Production et goldens inchangés. Stabilité : 20 exécutions de suite vertes.
- `DirectionGmaoQueryService` : le chiffre « patients sur équipement indisponible » lit `GenerateursDuCentre` (GMAO) et le port publié `PatientsAuxGenerateurs` (tous états, comme avant) ; plus de SQL ni de `JdbcTemplate`. Seam inscrit avant édition, fermé.
- Build complet `./mvnw -o test` vert, `check-arch.ts` 0 violation, rejeu PostgreSQL 16 vert (contrat des lectures 5/5, détection des absences 5/5).

## Corrections de la revue P6 (`.ddd/verify/review-planification-p6.md`, 2026-10-27)

| Constat | Correction |
|---|---|
| major — masquage incomplet | `capacite`, `niveau`, `atteinte` d'un centre masqué sont nuls ; centre à 0 publié ; goldens `file_masquee`, `reponse_complete` (états précédents archivés `*_avant_masquage_complet`), `parametres_par_defaut` (HEAD archivé, `_nouveau_code`) ; frontend (affichage « — », note, `niveauClasse`, spec) ; i18n fr/en/ar/kab ; RG-PLN-074 ; KB-PLN-75 ; ADR 0060 |
| minor — total | « total = centres publiés » confirmé et documenté (ADR 0060) |
| minor — trois règles de présence | requête 7 de `verifications-production.md` (+ script PG16) ; risque : la vue de la semaine de l'infirmier montre encore les patients en sommeil jusqu'à P8, la Séance jusqu'à P9 |
| minor — sommeil / place tenue | documenté (RG-PLN-046, ADR 0060) ; aucun marqueur dans la grille (évolution P8) |
| minor — lecture médicale | `SejoursDesPatients.duCentreSansRisque` : présence, file active et patients d'un générateur ne lisent plus les sérologies (test de contrat) |
| minor — fuseau | coupure des générateurs = fin de période au fuseau par défaut de la planification ; un générateur installé la dernière heure locale du dernier jour n'est plus compté : la fixture « limite » du test GMAO passe de 23:59Z à 22:59Z (golden `direction_capacite_generateurs` inchangé par ce correctif) |
| minor — statuts GMAO en dur | `IndisponibilitesDesGenerateurs` parle d'interventions datées, pas de statuts : liste conservée et documentée ; écart inter-centres consigné (KB-PLN-77) avec `PatientsSurEquipementIndisponibleTest` |
| minor — docs | `roadmap.md`, `seams.md`, ADR 0060 alignés (12 goldens P6 + 2 GMAO, SalleVueJdbcAdapter = P9) |
| nit — ordre `GENERATEUR_MIXTE` | tri déterministe (générateur par code, patients par nom) ; aucun golden modifié |
| nit — exception comme contrôle de flux | conservé : la valeur enregistrée invalide n'existe que pour des données héritées (tolérance du legacy), un `default` sur le dépôt déplacerait seulement le `catch` |
| nit — kabyle | `MASKED_NOTE`, `VERDICT_MASQUE`, `CARTE_MASQUEE` restent en anglais : **traduction kabyle à faire relire par un locuteur** |
| nit — goldens GMAO sans suffixe | le support de test de `gmao/pilotage` n'a pas la convention `_nouveau_code` : goldens modifiés sur place, anciens archivés |

## Dette consignée avec jalon (aucun fichier invasif forcé ; détail : `roadmap.md` « Dette de P6 », `seams.md`)

| Code conservé | Jalon |
|---|---|
| `PlanningSemaineService`, `PlanningSemaineQueryService`, `PlanningSemainePort`, `PlanningDonneesJdbcAdapter` (la vue de la semaine de l'infirmier, `SemaineDeLInfirmierLegacy`, garde l'ancienne règle du sommeil jusque-là) | fin de P8 |
| `SalleVueJdbcAdapter`, `SalleVuePort`, `SalleGenerateursService`, `CapaciteSalleRegle`, ACL GMAO `CapaciteDesSallesLegacy` (contrôle RG-PLN-061 : port publié `CapaciteDesSalles` à créer) | fin de P9 |
| `MouvementPatientJdbcAdapter` | fin de P7 |
| `SeanceRestController` (jours du tableau de bord), `SeancePlanningJdbcAdapter`, `ReferentialRepositoryAdapter`, `ReferentialAdminTables` | P9 (contextes Séance / Organisation) |

Fuseau : la date par défaut de la semaine est testée par le cas d'usage (horloge injectée) ; la fenêtre 23:00-24:00 UTC n'est pas rejouée contre HTTP.

## Plan de bascule

Sans objet (refonte sur place, pas de drapeau). Retour arrière : revert du lot ; aucun schéma modifié. Observation après déploiement : voir `verifications-production.md` (Slice P6).
