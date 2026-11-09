---
status: accepted
date: 2026-11-09
review-by: 2026-12-15
decision-makers: [utilisateur (demande explicite de réaliser O4, 2026-11-09), architecte]
context-scope: organisation (publishedlanguage), planification
related: ADR 0107 (lot A, plan en lots), 0108 (lot B), 0109 (lot C), 0053 (isolement, propriété de la salle), 0056 (verrou du référentiel des infirmiers), 0024 et 0075 (refactoring sur place), 0063 (Organisation) ; `.ddd/migration/slices/organisation-o4-lectures-publiees.md`
---

# 0110 - Organisation O4, lot D : salles, créneaux, isolement et verrou du référentiel des infirmiers

Refactoring SUR PLACE (ADR 0024/0075) : mêmes tables, mêmes URL, mêmes rôles, mêmes codes ; ni façade, ni flag, ni projection, ni métrique, ni
événement. Les points « à confirmer » le sont par les métiers / DPO au plus tard le **2026-12-15**.

## Contexte
Six lecteurs de la Planification touchaient des tables d'Organisation : `SallesDuCentreJdbc` (lit `salle`, ÉCRIT `salle.isolement`),
`VueDesSallesLegacy` (page des salles, une salle), `LectureDesRessourcesLegacy` (salles et créneaux triés par code, plus le calendrier),
`CreneauxDuCentreJdbc` et `SeriesDuCentreJdbc` (`position_creneau`), `VerrouDuReferentielDesInfirmiersJdbc` (`UPDATE centers SET id = id`).
La Planification dépend déjà de `contexts-organisation` depuis le lot A : aucun `pom.xml` modifié, aucun cycle (Organisation dépend de
shared-kernel, stock et gmao ; Planification dépend de gmao, shared-kernel et organisation ; ni Stock ni GMAO ne dépendent de Planification).

## Décisions
1. **Lecture : `SallesEtCreneauxDuCentre`** (organisation.v1) : `sallesParCode`, `pageDesSalles`, `salleDuCentre`, `creneauxParCode`,
   `contientLeCreneau`, `nombreDeCreneaux`. Toutes bornées au centre, SQL repris à l'identique (colonnes, `ORDER BY code`, `LIMIT/OFFSET`,
   `COUNT`). La salle fermée reste une salle comme les autres (le lecteur d'origine ne lisait pas `ouverte`) ; l'état d'ouverture n'est pas publié.
   `toutes` et `sallesDIsolement` de la Planification se dérivent de `sallesParCode` (`isolement = 'OUI'` devient `estSalleDIsolement`).
2. **Écriture d'isolement : la commande `DefinirLesSallesDIsolement`** (organisation.v1), seule écriture d'Organisation sur `salle.isolement`. Le
   SQL est celui du legacy, instruction pour instruction : `UPDATE salle SET isolement = 'NON' WHERE center_id = ?` puis, pour chaque salle,
   `UPDATE … SET isolement = 'OUI' WHERE center_id = ? AND id = ?`. Salle inconnue ou d'un autre centre : ignorée sans erreur ; centre inconnu :
   rien. L'implémentation (`IsolementDesSallesJdbc`, classe distincte de la lecture : deux beans de même classe seraient ambigus à l'injection) n'est pas `@Transactional` : elle utilise le `JdbcTemplate` partagé, donc la
   connexion et la transaction de l'appelant (propagation REQUIRED implicite). Aucun `REQUIRES_NEW` n'est introduit. Preuves : un test de
   Planification (`LecturesDOrganisationDansLaPlanificationTest`, écrit vert sur le SQL d'origine) et un test d'Organisation vérifient que
   l'écriture est annulée avec la transaction de l'appelant et conservée quand elle est validée. `ParametresDuPlanningJdbc.enregistrer`
   (verrou du centre, paramétrage puis isolement dans la même transaction) n'est pas modifié.
3. **Verrou du référentiel des infirmiers** : il verrouille bien la ligne `centers` (table d'Organisation). `VerrouDuCentre` est étendu de
   `reserverLeCentre(UUID)` : `UPDATE centers SET id = id WHERE id = ?`, verrou `FOR NO KEY UPDATE` qui ne bloque pas les insertions des
   tables référençant le centre (ADR 0056). On ne réutilise PAS `verrouiller` (`SELECT … FOR UPDATE`, plus fort, qui bloquerait ces
   insertions : comportement observable différent). Le traducteur de la Planification garde la traduction du refus de verrou en
   `EcritureConcurrente` (409) : `RefusDeVerrou` devient public.
4. **Traducteurs minces** (Planification, `infrastructure/acl/organisation/`) : `SallesDuCentrePubliees`, `CreneauxDuCentrePublies`,
   `SeriesDuCentrePubliees`, `LectureDesRessourcesPubliees` (garde le SQL du calendrier : `center_holiday`/`center_closure_day`, hors Organisation, O4-R5),
   `VueDesSallesPubliees`, `VerrouDuReferentielDesInfirmiersParOrganisation`. Les 6 anciens fichiers et le dossier `acl/legacyorganisation` sont
   supprimés. Câblage dans `PlanificationConfiguration` ; beans Organisation `sallesEtCreneauxDuCentre`, `definirLesSallesDIsolement`.
5. **Aucun lecteur résiduel** : aucune jointure indissociable, aucun tri ni verrou à conserver autrement. Liste blanche 15 -> 9.
6. **Filet AVANT migration** (vert sur les lecteurs d'origine) : `LecturesDOrganisationDansLaPlanificationTest` (9 tests, H2) : salle fermée lue
   partout, salle d'un autre centre ou inconnue (lecture et isolement), capacité NULL, page au-delà de la dernière, isolement vidé, centre inconnu,
   transaction annulée ou validée, créneaux triés/numérotés/comptés. Verrou : déjà couvert par `InfirmiersJdbcContrat` (centre inconnu, autre centre
   non bloqué, ordre global des verrous). Mêmes assertions après (seule la construction change). Nouveau contrat `SallesEtCreneauxContrat`
   (faux en mémoire et H2, 7 tests chacun) plus 2 tests JDBC (transaction, réservation du centre).
7. **Seams** : aucun (tout dans `backend/contexts/**` et un test de plateforme déjà couvert) ; `seamAllowed` resté `[]`.

## Conséquences et risques
- Aucun écart de comportement observable : aucun golden `_nouveau_code`, aucune archive `_ecarts-legacy`, aucun `.approved` modifié, aucun KB nouveau.
- Les lectures de salles lisent désormais aussi `code` et `capacite` (une même requête pour toutes) : colonnes présentes en production (V15). Les
  schémas H2 de test de la Planification reçoivent `capacite`.
- Le verrou `reserverLeCentre` et la commande d'isolement ne sont exécutés que sur H2 par les tests d'Organisation ; la variante PostgreSQL 16 est
  exercée par `InfirmiersJdbcPostgresContractIT` et `ParametrageJdbcPostgresContractIT` (qui composent maintenant les nouvelles classes) lorsque
  Docker est disponible ; non lancée ici (O4-R7 reste ouvert).
- `SalleOuverte`, `SalleFermee`, `CreneauCree` toujours jamais émis (O4-R6 : pas d'événement en refactoring sur place).
