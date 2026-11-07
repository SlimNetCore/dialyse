---
slice: dossier-medical-dm2-prescriptions
status: characterized
date: 2026-11-07
sources: [.ddd/safety-net/dossier-medical-inventaire.md, .ddd/migration/slices/dossier-medical-dm2-prescriptions.md, ADR 0024, 0065, 0072, 0080, GEL.md, known-bugs.md KB-MED-15 à 32]
---

# Filet DM2 : prescriptions, ordonnances, anémie et observance (legacy, refactoring sur place)

> Périmètre **décidé par délégation, à confirmer avec les métiers** : DM2 = prescriptions + ordonnances (lot 1) ET le sous-ensemble anémie / observance qui en dérive (lot 2). Le texte précédent de ce fichier (2026-11-01) décrivait des scénarios non écrits ; il est remplacé.

## Point d'observation
HTTP (MockMvc, contexte Spring complet, sécurité réelle + `RoleScopeFilter`, `CenterAccessGuard`, H2 `test`), état SQL (`ordonnances`, `lignes_ordonnance`, `prescriptions_medicales`, `administrations_anemie`, `alertes_observance`, `app_settings` `SEQ_ORD`), audit (`AuditRequestFilter`), diffusions, notifications durables. Aucun mock du code caractérisé, **aucun code de production modifié, aucun seam** (`seamAllowed` vide). Harnais : `characterization/dossiermedical/dm2/PrescriptionsCharacterizationSupport` (au-dessus des harnais Planification : onze profils, deux centres, diffusions, audit). Données fictives. Un pont de TEST `infrastructure/scheduling/ObservanceSchedulerPontDeCaracterisation` (même paquet, côté tests) appelle `controlerCentre` (visibilité paquet).

## Scénarios (69 tests, 113 goldens `src/test/resources/characterization/dossiermedical/dm2/<classe>/<scénario>/*.approved.json`)

**Lot 1 : prescriptions et ordonnances (34)**
| Classe | Tests | Contenu |
|---|---|---|
| `LesOrdonnancesCharacterizationIntegrationTest` | 15 | création (lignes avec/sans code ATC, ordre saisi), date par défaut masquée, 15 formes de lignes invalides, patient inconnu / autre centre, numérotation `ORD-nnnnn` par centre (ordre des signatures), matrice 4 statuts x 3 actions, refus qui consomme un numéro, rejeu des transitions (état inchangé), recherche par (id, patient, centre), routes inexistantes, liste triée / paginée / bornes, lignes détaillées, droits (14 profils + anonyme x 5 routes), cloisonnement, audit / notifications / diffusions, signatures simultanées (invariants) |
| `LesPrescriptionsCharacterizationIntegrationTest` | 15 | création + audit + diffusion `PRESCRIPTION_CHANGED`, date par défaut masquée, poids sec 20 à 300 kg (arrondi), entrées illisibles / hors bornes, catalogue (code, libellé, article étranger ou inconnu), conversion de dose (RG-STK-006), modification (remplacement total, `created_at` réécrit), PUT d'un id inconnu, **PUT d'un id d'un autre centre**, suppression (portée par id / patient / centre), liste (tri, filtres, pagination, bornes), recherche par corps, prescription en vigueur, droits x 6 routes, cloisonnement |
| `ImpressionDeLOrdonnanceCharacterizationIntegrationTest` | 4 | `POST /documents/print` type `ORDONNANCE` : PDF (en-tête) et contenu tableur (chaînes du classeur), statuts, erreurs, garde par profil et par centre |

**Lot 2 : anémie, observance, planificateur (35)**
| Classe | Tests | Contenu |
|---|---|---|
| `SuiviEtObservanceDeLAnemieCharacterizationIntegrationTest` | 10 | `suivi-anemie` (vide, agrégé, évaluations KDIGO, limites de 10), `observance-anemie` (EPO en dose, sans dose, administrations écartées, unités de fréquence JOUR à ANNEE, dernière prescription seule, fer), droits, cloisonnement |
| `LesAlertesDObservanceCharacterizationIntegrationTest` | 5 | liste (ordre, résolues comprises), résolution et rejeu, erreurs 422, droits, cloisonnement + audit |
| `LesAdministrationsDeLAnemieCharacterizationIntegrationTest` | 8 | saisie effective / non faite, 18 règles de saisie, conversion de quantité depuis la fiche article, doublon, liste (tri, pages, bornes), droits, cloisonnement |
| `PlanificateurDObservanceCharacterizationIntegrationTest` | 12 | retard en quantité / en nombre, rappel puis résolution automatique, silence (loin de l'échéance, prescription récente / future, période soldée), périodes en jours, fer, rejeu de nuit, alerte acquittée rouverte, sélection de la dernière prescription, cloisonnement des centres, cron `0 30 6 * * *` + passage de tous les centres, API des alertes |

## Déterminisme (3 exécutions vertes : 1 ordre par défaut, 2 avec `MethodOrderer$Random` demandé)
Prescriptions et ordonnances : dates FIXES nommées `<D+n>` (aucune horloge). Observance et planificateur : l'application lit `LocalDate.now()` (JVM, KB-MED-15) : dates relatives au jour de la JVM nommées `<J-3>`, messages d'alerte `jj/mm/aaaa` ramenés aux alias, garde d'attente de minuit (fuseau de la JVM, 2 minutes), jamais de dépendance au jour UTC vs jour du centre Africa/Algiers. Identifiants des lignes de fixtures calculés par nom ; identifiants générés `<UUID_GENERE_n>` ; horodatages `<HORODATAGE>` ; clés JSON triées ; les ordres non garantis (lignes d'ordonnance, KB-MED-16) sont triés dans l'assertion. Une flakiness évitée en cours de route : ordre d'égalité de `resolved_at` dans la lecture des alertes (clé de tri ajoutée dans la lecture du test).

## Seams
Aucun requis ni appliqué (`seamAllowed` vide) ; seam d'horloge proposé et non appliqué dans `.ddd/safety-net/seams.md`.

## Limites (NON verrouillé)
- H2, pas PostgreSQL : ordre SQL non garanti (KB-MED-26 ne vaut que pour H2), précisions numériques, messages SGBD (masqués), clé étrangère patient (absente de H2, présente dans `db/migration-legacy/V24`).
- Contenu visuel du PDF `ordonnance.jrxml` (mise en page, police, logo) : seuls l'en-tête PDF et les chaînes du classeur sont observés ; les cellules numériques ou dates (« Né(e) le », « Date ») ne sont pas dans les chaînes.
- Sortie de stock déclenchée par une administration avec séance et article (`ConsommationsDeSeance`) : seule la conversion de quantité est observée ; séance facturée : filet Facturation F6 (`administration_traitement_seance_facturee`).
- `KdigoEvaluationPolicy` : seuils observés sur 4 bilans seulement ; export FHIR (`MedicationRequest` depuis les ordonnances) hors périmètre (DM8).
- Concurrence : invariants seulement (numéros distincts de deux signatures simultanées).
- Prescriptions de même date pour un patient (égalité de tri, `prescriptions/active`) : non verrouillé (ordre non défini).
- Rôles personnalisés réels (table `roles`) : seul un rôle inconnu du serveur est simulé.
- Pas de couverture de branches ni de mutation mesurées (outil non exécuté dans cette session).

## Fichiers legacy ajoutés à `covered.json` (56)
Contrôleurs `Ordonnance`, `PrescriptionMedicale`, `SuiviAnemie`, `ObservanceAnemie`, `AlerteObservance`, `AdministrationTraitement` ; adaptateurs, entités, dépôts JPA, spécification et générateur de numéro ; DTO d'entrée et de sortie ; `SuiviAnemieQueryService`, `ObservanceAnemieQueryService` ; `ObservancePrescriptionScheduler`, `ObservanceMesure`, `ObservancePeriodMath` ; domaine `bc-medical` (ordonnance, anémie) et `bc-seance` (`PrescriptionMedicale`, `PrescriptionMedicaleDomainService`) ; `reports/ordonnance.jrxml`. Non ajoutés : ports / interfaces et événements (aucun scénario ne les exerce seuls), `KdigoEvaluationPolicy`, `DocumentRestController` (déjà couvert), `FhirExportService`.

## Bugs connus et trous de sécurité
KB-MED-15 à 32 (`.ddd/safety-net/known-bugs.md`). **Sécurité (non corrigés, verrouillés)** : KB-MED-17 (PUT d'une prescription d'un autre centre la réaffecte au centre de la session), KB-MED-18 (impression d'ordonnance sans contrôle de profil ni de centre), KB-MED-19 (aucun contrôle d'appartenance du patient), KB-MED-20 (trous dans la numérotation légale).

## Questions de décision pour l'utilisateur
Périmètre DM2 (anémie / observance rattachés), droits (KB-MED-23), correction de KB-MED-17 et 18 avant tout déploiement, séquence `ORD` sans trou (KB-MED-20), impression des brouillons (KB-MED-21), horloge du centre (KB-MED-15), minimisation des diffusions (KB-MED-25), comportement du planificateur (KB-MED-28).
