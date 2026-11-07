---
slice: seance-se2-validation-signature
status: characterized
date: 2026-11-07
sources: [.ddd/migration/slices/seance-se2-validation-signature.md, ADR 0024, 0062, 0074, 0088, .ddd/safety-net/seance-se1-creation.md, known-bugs.md KB-SEA-11 à 21]
---

# Filet SE2 : validation, signature, déverrouillage pour régularisation (legacy, refactoring sur place)

Remplace le filet du 2026-11-01, qui ne citait que des tests classiques (aucun golden, aucun test HTTP de la matrice des statuts, des droits ni des effets de bord) et affirmait des comportements inexacts (éviction de caches `ref.seances.*` qui n'existent pas, voir KB-SEA-19).

## Périmètre vérifié par lecture du code
- `SeanceRestController` : `POST /api/v1/seances/{id}/valider` (ADMIN, INFIRMIER), `POST /api/v1/seances/{id}/signer-medecin` (ADMIN, MEDECIN), `POST /api/v1/seances/{id}/deverrouiller-regularisation?centerId=` (ADMIN). Le contrôleur lit la séance et applique le verrou des jours passés (`exigerDeverrouillagePourSeancePassee`, jour de la JVM), puis délègue.
- `SeanceApplicationService` (frontière transactionnelle) / `SeanceDomainService.validate | signByMedecin | unlockForRegularisation` / `Seance.validerParInfirmier | signerParMedecin | deverrouillerPourRegularisation | memoriserPlace` ; adaptateurs `SeanceRepositoryAdapter` (table `seances`), `SeancePlaceJdbcAdapter` (déplacement temporaire du jour puis place de la fiche, lue chez Planification), `ConsommationsDeSeanceDuStock` / `GestionDesConsommations` (imputation, aucun mouvement de stock), `NotificationService` (`notifySeanceValidated`, `notifySaisieInfirmier`, `notifySeanceDeverrouillee`), `AuditActionResolver`.
- Aucun événement de domaine ni outbox ; la signature ne diffuse rien. Aucun cache de séances (KB-SEA-19).
- **Sortie de stock à la validation** : aucune. Le Stock impute les consommations sur la sortie pour séances du jour (ADR 0045) ; les mouvements et les lots ne bougent pas. Couverture partagée : le port publié `ConsommationsDeSeance` (imputation, rapprochement, retour en stock, concurrence de la pièce) et les refus d'article inactif ou inconnu à la validation sont dans **Stock S6b** (`SortiesPourSeancesIntegrationTest`) et **S8** (`ArticlesDeLaSeance…`, golden `validation_article_inactif_nouveau_code`) ; le chemin HTTP `valider` avec consommations (imputation sans pièce, sur une pièce, dépassement, retour arrière de la séance et de la place) est **ici**.
- Consommateurs voisins cités et non rejoués : Facturation F3 et retrait (`SeanceFactureeImmuable…` : refus des trois routes sur `FACTUREE`, repris ici dans les matrices), Stock S6b, Planification P6 (`LecturesDePlaceConsommeesParLaSeance…`, point d'observation `SeanceUseCase.validate`), `SeanceRegularisationService` + `SeanceRegularisationScheduler` (rappel des 7 jours, tests existants), `SeanceDeverrouillageIntegrationTest`, `SeanceScanValidationControllerTest` (appel direct du contrôleur : sa signature de constructeur ne doit pas changer), Pilotage et Dossier médical (constantes de séance) lisent les statuts `VALIDEE`/`SIGNEE`/`FACTUREE`.

## Point d'observation
HTTP (MockMvc, contexte Spring complet, sécurité réelle + `RoleScopeFilter` + `CenterAccessGuard`, H2 `test`, écart minimal de 2 ms entre requêtes du harnais Planification), état SQL de la table réelle `seances` et des tables de consommations du Stock, diffusions temps réel (canal du courtier), audit (`audit_log`). **Aucun seam, aucun code de production modifié** (`seamAllowed` vide, `seams.md` inchangé). Seul fichier de test existant touché : `Se1CharacterizationSupport` (méthode `tranche()` ajoutée, valeur `se1` par défaut, 31 goldens SE1 inchangés et verts).

## Scénarios (33 tests, 33 goldens `src/test/resources/characterization/seance/se2/<dossier>/<scénario>/*.approved.json`)
| Dossier | Classe | Tests | Contenu |
|---|---|---|---|
| validation | `LaValidationParLInfirmier…` | 16 | nominal (champs, diffusions, seconde validation : horodatage conservé, rediffusion) ; compte signataire du corps (absent, vide, autre compte, 100 et 101 caractères) ; matrice des 6 statuts x infirmier/administrateur ; jours (-400 à +30) avec et sans déverrouillage ; verrou évalué avant statut ; droits (15 profils + anonyme) ; cloisonnement par centre et garde séance/patient ; corps et formes (11) ; consommations imputées et cumulées ; retour arrière (8 échecs : séance, place et imputations annulées, puis validation valide) ; sortie pour séances du lendemain avec dépassement ; place mémorisée (6 cas) ; absence de cache ; audit ; écritures simultanées ; patient disparu et statut illisible |
| signature | `LaSignatureMedicale…` | 9 | nominal (aucune diffusion, seconde signature refusée) ; matrice des statuts + ligne incohérente ; compte signataire ; indifférence au jour de la séance (-400 à +30) ; droits (15 profils + anonyme) ; cloisonnement et corps ; audit ; écritures simultanées ; parcours complet CREE → VALIDEE → SIGNEE |
| deverrouillage | `LeDeverrouillagePourRegularisation…` | 8 | nominal (effet sur l'infirmier, idempotence, diffusion) ; bornes de jour (-400 à +30) ; matrice des statuts ; droits (15 profils + anonyme) ; cloisonnement et paramètres ; audit ; écritures simultanées ; effets limités (statut, place, consommations) |

Transitions couvertes : `CREE → VALIDEE` (infirmier, administrateur), `VALIDEE → SIGNEE` (médecin, administrateur), `CREE` déverrouillée (état inchangé) ; refus : validation de `ABSENT` / statut inconnu / `FACTUREE`, signature hors `VALIDEE`, déverrouillage hors `CREE` d'un jour passé ; idempotences : validation de `VALIDEE` et `SIGNEE`, second déverrouillage.

## Déterminisme
Dates fixes ou relatives nommées `<J>`, `<J-2>`, `<J+1>` (le jour du centre et celui de la JVM portent le même nom, KB-SEA-01, garde de minuit sur les deux fuseaux héritée de SE1) ; toute date réelle non nommée fait échouer le golden ; identifiants stables par alias ; horodatages masqués ; ordres de liste fixés par identifiants stables ; concurrence verrouillée par invariants uniquement ; données fictives. Goldens approuvés un par un (lecture du `received`, puis écriture de l'`approved`), jamais `-Dcharacterization.approve=true`.

Exécutions : suites `characterization.seance` (SE1 + SE2, 64 tests) 3 fois consécutives (2 en ordre par défaut, 1 en `-Dsurefire.runOrder=random`) : 64 verts à chaque fois, 0 flaky. Suite complète `./mvnw -o test -fae` (sans relance des échecs, sortie relue) : BUILD SUCCESS, 6 969 tests tous modules (module `platform` : 2 965, 0 échec, 12 ignorés préexistants ; `contexts/seance` 148), 0 rerun.

## covered.json
Ajoutés : `ValidateSeanceRequest`, `ValidateSeanceArticleRequest`, `SignSeanceMedecinRequest`, `SeanceArticleConsumption`, `SeanceUseCase`. Déjà présents et désormais exercés par la slice : `SeanceRestController`, `SeanceApplicationService`, `SeanceDomainService`, `Seance`, `SeanceRepositoryAdapter`, `SeancePlaceJdbcAdapter`, `SeancePlacePort`, `PlaceSeance`, `NotificationService`, `ApiExceptionHandler`, `SeanceFactureeNonModifiableException`, `AuditActionResolver`, `AuditRequestFilter`. **Non ajoutés** : `SeanceRegularisationService` et `SeanceRegularisationScheduler` (couverts par leurs tests existants, pas par un golden de cette slice).

## Limites (non verrouillé)
- Couverture de branches non mesurée et aucune mutation exécutée (aucun outil configuré dans le module, PIT documenté dans `.ddd/research/mutation-testing-pit.md`, tests Spring trop lents) ; la couverture annoncée est celle des scénarios listés.
- H2 seulement : schéma PostgreSQL non rejoué ; concurrence par invariants (pas de contrat à deux connexions, KB-SEA-18).
- Horloge : le verrou des jours passés et le déverrouillage lisent le jour de la JVM, la création le jour du centre (KB-SEA-01) ; aucun test à minuit ; le rappel quotidien (UTC) n'est pas rejoué.
- Sortie pour séances : un seul périmètre (« journée », sans salle ni demi-journée) ; périmètres par salle et par demi-journée, pièce rapprochée et verrou de pièce restent dans Stock S6b ; stock des lots non décrémenté à la validation (constaté).
- Hors slice (SE3 à SE7) : ajout, modification et retrait de consommables après validation, forfait surchargé, date modifiée (`PUT /{id}`), suppression, journal, tableau de bord, calendrier, volets médical et paramédical, lectures de la liste filtrée `deverrouillee`.
- Séances facturées : seul le refus des trois routes est ici (matrices) ; l'émission et le retrait de facture sont dans Facturation F3 et F6.
- Aucun test du JWT réel (sessions simulées par le harnais) ni des licences.

## Questions ouvertes
Reproduire ou corriger KB-SEA-11 à 21, en particulier l'auteur lu dans le corps (11, médico-légal), la validation d'une séance à venir (12), la fenêtre de 7 jours qui ne borne pas le déverrouillage (13), les consommations cumulées par validation répétée (15), l'audit `SEANCES_CREATION` (17) et l'absence de verrou d'écriture (18). Statut `ABSENT` : retrait prévu avec Facturation F2 / Absences (ADR 0002).
