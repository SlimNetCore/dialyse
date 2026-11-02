# GEL du module seance (2026-11-02)

Statut : **gelé, non branché, hors réacteur Maven** (`backend/pom.xml` ne le liste plus, `backend/Dockerfile` ne copie plus son `pom.xml`, `platform/pom.xml` ne l'a jamais référencé). Le dossier, ses sources, ses tests, l'ADR 0062, les fiches (`.ddd/migration/slices/seance-se*.md`), le tactique (`.ddd/tactical/seance.md`) et le glossaire sont conservés pour la reprise. Décision déléguée à l'assistant par l'utilisateur le 2026-11-02. ADR : 0074.

## Pourquoi
- **Colonnes inexistantes** : `SeancesJdbc` lit et écrit `seances.pec_id`, `type_seance`, `verrouille_facturation` (absentes de Flyway V1..V52 ; prouvé par `backend/scripts/seance-pg16-rejeu.sh`). Le verrou n'est jamais relu (exception avalée, verrou contourné) ; forfait surchargé et déverrouillage ne sont jamais écrits ; l'UPDATE de masse du verrou frappe toutes les séances de la période.
- **Statuts incompatibles** : `StatutSeance` n'a ni `FACTUREE` ni `ABSENT` du legacy (`SeanceStatus`) : `valueOf` planterait sur les lignes réelles.
- **Aucune parité** : aucun golden Séance dédié, aucun `_nouveau_code`.
- **Pas de garde de centre** : centre fourni par la requête ou le corps, aucun `@PreAuthorize` (IDOR inter-centres).
- **URL déclarée aussi par le legacy** : `/api/v1/seances` (mapping ambigu si câblé) ; trois routes synonymes de déverrouillage.
- **Modèle parallèle contraire à l'ADR 0024** (refactoring sur place : mêmes tables, mêmes URL, mêmes statuts).
- Autres défauts : forfait sans prix accepté à 0, auteur par défaut `"system"`, horloge non injectée (UTC au lieu du fuseau du centre), `PecValidationJdbc` lit `prise_en_charge` en SQL, domaine sérialisé en HTTP, événements jamais publiés, garde `TableDesSeancesTest` inerte.

## Ce qui a été fait au gel
- Audit de production : aucun code legacy de Séance modifié sans filet. Résidus de `2978e639` : `CacheConfig` restauré (2 caches inutilisés), colonne H2 `seances.verrouille_facturation` retirée de `schema.sql`, entrée `SeanceUseCase.java` retirée de `covered.json`. Table `derogations_regularisation` (V50, ex-V22 en doublon) **conservée** (additive, idempotente, vide, inutilisée par le code déployé) : fiche SE4 et `verifications-production.md` § 39.
- `TableDesSeancesTest` désactivé (`@Disabled` motivé) ; module exclu du balayage de `ReglesDeGestionDocumentationTest` ; règle `FrontieresDesContextesTest` conservée (vide).

## Filet de caractérisation du legacy Séance existant (inventaire pour la reprise)
Aucun dossier `characterization/seance` : le filet est réparti entre les contextes qui consomment la séance.
| Domaine | Classes / goldens (sous `backend/platform/src/test`) |
|---|---|
| Articles de la séance (catalogue) | `characterization/stock/referentiel/ArticlesDeLaSeanceCharacterizationIntegrationTest` (15 goldens `articles-de-la-seance`) |
| Sorties de stock pour séances (consommables, S6b) | `characterization/stock/referentiel/SortiesPourSeancesIntegrationTest` (44 goldens `sorties`, 43 écarts legacy) |
| Place de la séance, déplacements, séances réalisées | `characterization/planification/semaine/DeplacementsEtSeancesRealiseesCharacterizationIntegrationTest`, `LecturesDePlaceConsommeesParLaSeanceCharacterizationIntegrationTest` (`SeancePlace`, P6) |
| Séances de rattrapage, lectures d'absences | `characterization/absencespatients/consultation/SeancesDeRattrapageCharacterizationIntegrationTest`, `LecturesDeAbsencesParLaDirectionEtLaSeance…` |
| Séance facturée immuable, validation de facturation | `characterization/facturation/retrait/SeanceFactureeImmuableCharacterizationIntegrationTest`, `characterization/facturation/validation/SeancesFactureesCharacterizationIntegrationTest` (validation, `FACTUREE`) |
| Tests classiques du legacy (hors goldens) | `infrastructure/web/rest/Seance{Details,Deverrouillage,Historique,List,Scan,ScanValidationController,Station,Suppression}*Test`, `SeanceRestControllerTest`, `application/seance/SeanceRegularisationServiceIntegrationTest`, `SuppressionSeanceServiceTest`, `infrastructure/scheduling/SeanceRegularisationSchedulerTest`, `bc-seance` `SeanceDomainServiceTest` |
Manques (à caractériser avant tout code) : création et unicité d'une séance (SE1), validation et signature avec horodatage et auteur (SE2), forfait surchargé et remplacement (SE5), déverrouillage > 7 jours et dérogation (SE4), liste blanche des 18 lecteurs SQL de `seances` (SE7).

## Jalons déjà posés (ordre de reprise : Séance reste la DERNIÈRE)
- Planification : port `attenduLe` ; règle de présence recopiée dans `SeancePlanningJdbcAdapter` ; colonnes de place de `patients` lues par `SeanceRestController` ; `SeanceRestController` (1 315 lignes) à découper par agrégat.
- Stock : `ConsommationsDeSeance` (langage publié) déjà intégré à `SeanceRestController` (SE3 = branchement, pas de réécriture).

## Plan de reprise (sur place)
1. **Filet de caractérisation du legacy Séance d'abord** : compléter les manques ci-dessus (HTTP, avec `/ddd-safety-net` par slice), `covered.json` mis à jour fichier par fichier.
2. **Établir la liste blanche nominative datée** des lecteurs SQL de `seances` avant de réécrire `TableDesSeancesTest` (scanner aussi `platform/src/main/java`, ne pas exclure `/acl/` ni `/persistence/`).
3. **Slices SE1 à SE7 sur place** : mêmes tables (`seances` et colonnes existantes), mêmes URL (`/api/v1/seances`, un seul contrôleur), statuts du **legacy** (`FACTUREE`, `ABSENT` compris ; retrait d'`ABSENT` avec Facturation F2 / Absences), garde de centre issue de la session, rôles du legacy, forfait sans prix refusé, auteur = utilisateur authentifié, horloge injectée (fuseau du centre), verrou posé sur les séances facturées de la facture (pas d'UPDATE de masse), PEC par port publié.
4. Rebrancher le module dans `backend/pom.xml`, `platform/pom.xml`, `Dockerfile` seulement après parité verte.
