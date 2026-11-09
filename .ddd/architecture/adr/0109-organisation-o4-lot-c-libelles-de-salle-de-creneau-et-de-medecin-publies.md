---
status: accepted
date: 2026-11-09
review-by: 2026-12-15
decision-makers: [utilisateur (demande explicite de réaliser O4, 2026-11-09), architecte]
context-scope: organisation (publishedlanguage), seance, patient-couverture
related: ADR 0107 (lot A, plan en lots), 0108 (lot B), 0096 (lectures publiées de la Séance), 0024 et 0075 (refactoring sur place), 0063 (Organisation) ; `.ddd/migration/slices/organisation-o4-lectures-publiees.md`, `.ddd/safety-net/known-bugs.md` (KB-ORG-10), `.ddd/migration/verifications-production.md` (§73)
---

# 0109 - Organisation O4, lot C : libellés de salle, de créneau et de médecin publiés

Refactoring SUR PLACE (ADR 0024/0075) : mêmes tables, mêmes URL, mêmes rôles, mêmes codes ; ni façade, ni flag, ni projection, ni métrique, ni
événement. Les points « à confirmer » le sont par les métiers / DPO au plus tard le **2026-12-15**.

## Contexte
Trois lecteurs lisaient `salle`, `position_creneau` et `medecin` en SQL : Séance `LecturesPublieesDesSeancesJdbc` (code de la salle, code et libellé du
créneau d'une séance, bornés au centre), Patient et couverture `LectureDesMouvementsJdbc` (nom de la salle et libellé du créneau par `LEFT JOIN … ON id`,
SANS borne de centre) et `LectureDesPatientsJdbc` (libellé du médecin, code et libellé du créneau, et les filtres de recherche par libellé ou
identifiant du médecin et du créneau, tous bornés au centre). Séance et Patient et couverture dépendent déjà de `contexts-organisation` (lot A) :
aucun `pom.xml` modifié, aucun nouveau lien de module.

## Décisions
1. **Langage publié `LibellesDeLOrganisation`** (organisation.v1, noms du glossaire), lectures PAR LOT (`Collection<id>` -> `Map`), ne publiant que ce qui
   est lu : `codesDesSalles(centre, ids)`, `creneauxDuCentre(centre, ids)` (record `LibelleDeCreneau(code, libelle)`), `nomsDesSalles(ids)`,
   `libellesDesCreneaux(ids)`, `nomsDesMedecins(centre, ids)`, `medecinsCorrespondant(centre, terme)`, `creneauxCorrespondant(centre, terme)`. Implémentation
   `LibellesDeLOrganisationJdbc` (SQL repris à l'identique : colonnes, bornes de centre, médecin désactivé non filtré, libellé vide exclu, `TRIM`, jokers
   `LIKE` non échappés). Un identifiant inconnu, d'un autre centre ou sans libellé n'a pas d'entrée : le consommateur retrouve `null` comme avant.
2. **Pas de N+1 ajouté.** La recherche des patients lisait déjà ses libellés par lot (même nombre de requêtes). Le journal des mouvements joignait
   salle et créneau dans la requête de page : les deux jointures sur clé primaire sont retirées (elles ne changeaient ni le nombre, ni l'ordre, ni les
   doublons des lignes, ni le total), puis le nom de la salle et le libellé du créneau sont lus en UN lot chacun pour la page. Le tri, la pagination et les
   filtres du journal ne portaient pas sur ces colonnes : aucun lecteur n'est laissé en résiduel. Séance lit une salle et un créneau (lot d'un).
3. **Consommateurs** : les trois lecteurs reçoivent `LibellesDeLOrganisation` en constructeur (adapters de persistance, sans nouvelle couche) ; câblage dans
   `SeanceConfiguration` et `PatientCouvertureConfiguration`. Organisation expose le bean `libellesDeLOrganisation`. Aucun fichier de la plateforme.
4. **KB-ORG-10, reproduit sans corriger** (décision déléguée) : le journal des mouvements n'est PAS borné au centre pour la salle et le créneau. Le langage
   publié le dit par deux méthodes distinctes (`nomsDesSalles`, `libellesDesCreneaux`), les autres lectures restant bornées. Contrôle de données :
   `verifications-production.md` §73 (SELECT). Si 0 ligne concernée, la lecture pourra être bornée au centre : décision métiers/DPO.
5. **Filet AVANT migration** (vert sur les lecteurs SQL d'origine, mentionné dans la fiche) : `LibellesDeLOrganisationDansLesLecturesTest` (8 tests,
   patient-couverture, H2) caractérise ce que les goldens ne couvraient pas : libellé du médecin (nom seul, prénom blanc, désactivé, inconnu, autre centre,
   NULL), créneau (autre centre, inconnu), recherches par libellé et par identifiant (bornées au centre, identifiants pris tels quels), journal avec salle
   ou créneau d'un autre centre, inconnu, NULL. Mêmes assertions après migration (seule la construction change). Déjà couverts et inchangés : goldens PC1
   des mouvements (place disparue), contrat des lectures publiées des séances (autre centre, disparu, absent), contrat `PatientsJdbcContrat`. Nouveau contrat
   `LibellesContrat` exécuté sur un faux en mémoire et sur H2 (8 tests chacun).
6. **Liste blanche** `TableDeLOrganisationTest` : 18 -> 15 (les trois fichiers ne lisent plus aucune table d'Organisation).
7. **Seams** : aucun (tout dans `backend/contexts/**`) ; `seamAllowed` resté `[]`.

## Conséquences et risques
- Aucun écart de comportement observable : aucun golden `_nouveau_code`, aucune archive `_ecarts-legacy`, aucun `.approved` modifié.
- L'absence de borne du journal est une dette consignée (KB-ORG-10), pas un choix métier.
- Les contrats de `LibellesDeLOrganisation` ne tournent que sur H2 (mode PostgreSQL) : variante PostgreSQL 16 non faite (O4-R7 reste ouvert, étendu à ces contrats).
- `medecinsCorrespondant` et `creneauxCorrespondant` rendent un `Set` non ordonné (comme avant : le résultat alimente un `IN (...)`).
