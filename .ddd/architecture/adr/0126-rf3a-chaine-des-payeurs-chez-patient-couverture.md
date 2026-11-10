---
status: accepted
date: 2026-11-10
review-by: 2026-12-15
decision-makers: [architecte (mise en oeuvre de l'ADR 0121, décision 2)]
context-scope: patient-couverture (propriétaire), platform (administration générique des référentiels, ACL, reprise de l'historique)
related: ADR 0121 (propriété), 0122 et 0124 (RF1 et RF2, modèles suivis), 0024 et 0075 (refactoring sur place), 0079 (`patientcouverture.v1`), 0070 (admin générique) ; fiche `.ddd/migration/slices/patient-couverture-rf3-payeurs.md` ; known-bugs KB-RF3-01 à 05
---

# 0126 - RF3a : la chaîne des payeurs (caisses, agences, centres payeurs) chez Patient & couverture

Refactoring SUR PLACE : mêmes tables (`caisse_assurance`, `agence`, `centre_payeur`), mêmes URLs, mêmes rôles, mêmes codes d'erreur, même JSON ; ni façade, ni flag, ni projection, ni événement. Périmètre RF3a : CRUD, import ordonné, invalidation des caches, retrait des trois types de l'administration générique. Les lectures `/referentials/{caisses,agences,centres-payeurs,centres-payeurs-details}` restent servies par Organisation (RF3b/RF3c) ; le langage publié `PayeursDuCentre` est RF3b.

Parité : 24 tests de caractérisation HTTP (45 goldens, écrits et verts sur le legacy AVANT tout changement de production) passent sans un seul golden modifié ; aucun golden `_nouveau_code`, aucune archive `_ecarts-legacy`. Les goldens existants de la Séance, de la Facturation, de la Comptabilité, du pilotage, des Absences, du reporting et d'Organisation sont inchangés.

Addendum du 2026-11-10 (RF4, constats 5 et 6 de la revue indépendante RF1-RF3d, `.ddd/migration/reviews/revue-RF1-RF3d.md`) :
- **Dérogation tracée** : contrairement à la règle des ADR 0086 à 0120 (« jamais `-Dcharacterization.approve=true` »), les 45 goldens de RF3a ont été GÉNÉRÉS avec `-Dcharacterization.approve=true` en une seule passe sur le legacy, puis RELUS un par un et comparés au code legacy (tri `code, id`, comptes d'usage, messages d'import et de suppression) avant tout changement de production. Dérogation de l'architecte lors de l'ouverture de RF3a, non consignée à l'époque ; l'écart de procédure est reconnu ici. Aucune autre slice RF n'a utilisé `approve=true`.
- **Renvoi vers l'ADR 0129** : « aucun golden `_nouveau_code` » ne vaut QUE pour RF3a à sa livraison. Depuis RF3d (2026-11-10, ADR 0129), 9 goldens des payeurs sont `_nouveau_code` (400 au lieu de 500), anciens archivés dans `_ecarts-legacy/**/*_avant_rf3d.legacy.json`. RF4 (ADR 0130) n'en change aucun.
- **Décision 3, compte d'usage** : borné au centre depuis RF4 (ADR 0130, KB-RF3-05 corrigé) ; le texte d'origine ci-dessous décrit RF3a.

## Décisions
| N° | Décision | Raison |
|---|---|---|
| 1 | Domaine pur : `CaisseDAssurance` (type STANDARD/VACANCIER), `Agence` (caisse obligatoire), `CentrePayeur` (agence obligatoire, adresse facultative) sous l'interface scellée `Payeur` ; contrôle de saisie unique (`ControleDeSaisieDePayeur`) qui rend TOUTES les anomalies dans l'ordre des champs, avec les messages et les codes du validateur générique | règles auparavant dispersées entre `ReferentialKind`, le validateur générique et le SQL dynamique |
| 2 | Clé naturelle = `code` sans casse ni espaces répétés, unique par centre et par niveau ; le parent est donné par code OU par identifiant et se résout DANS le centre | comportement du legacy |
| 3 | Un port par niveau (`Caisses`, `Agences`, `CentresPayeurs`, génériques `Payeurs<P>`), lecture de page `LectureDesPayeurs` et usage `UsageDesPayeurs` (agences d'une caisse, centres payeurs d'une agence, patients d'un centre payeur : comptés par identité de payeur) | compte et message de suppression identiques |
| 4 | Cas d'usage unique `GererLesPayeurs` (créer, modifier, supprimer, lister, toutes, importer tout-ou-rien) ; modification : existence, puis saisie, puis unicité | ordre des refus du legacy |
| 5 | Langage publié `patientcouverture.v1` : `GestionDesPayeurs` (+ `Niveau`) et `RefusDePayeur` (SAISIE_INVALIDE, DEJA_EXISTANT, INTROUVABLE, UTILISE) | modèle RF1/RF2 ; seul le STRICT minimum (la lecture « annuaire » `PayeursDuCentre` est RF3b) |
| 6 | Quatre classes JDBC sur les mêmes tables : `CaissesJdbc`, `AgencesJdbc`, `CentresPayeursJdbc`, `LectureDesPayeursJdbc` (page avec parent du MÊME centre, libellé « code · nom », recherche sans casse jokers littéraux, tri `code, id`). Contrat de tests UNIQUE : double en mémoire, H2 et PostgreSQL 16 (script `backend/scripts/patient-couverture-rf3-pg16-rejeu.sh`) | le fake et l'adapter réel ne peuvent pas diverger |
| 7 | L'administration générique GARDE la lecture du fichier et DÉLÈGUE : ACL `PayeursParPatientCouverture` (troisième délégué, après transporteurs et forfaits) ; `ReferentialAdminTables` n'a plus les trois entrées ; la reprise de l'historique retrouve un payeur par `GestionDesPayeurs.toutes` | modèle RF1/RF2 |
| 8 | Invalidation APRÈS commit PAR NIVEAU (`InvalidationDesListesDePayeurs`) : caisse -> `ref.caisses` + `ref.centresPayeursDetails` ; agence -> `ref.agences` + `ref.centresPayeursDetails` ; centre payeur -> `ref.centresPayeurs` + `ref.centresPayeursDetails` | exactement les caches que l'administration générique invalidait ; le filet vérifie qu'une écriture ne rafraîchit pas les listes des autres niveaux |
| 9 | Garde nominative `TableDesPayeursTest` (liste blanche NOMMÉE et DATÉE, effectif asserté 11, échec sur entrée morte) | modèle `TableDesForfaitsTest` |

## Écarts de parité
Aucun. Les comportements du legacy, 500 compris, sont REPRODUITS et consignés (KB-RF3-01 à 05). Aucune décision « corriger » n'est prise sans accord de l'utilisateur.

## Résiduels nominatifs
| ID | Résiduel | Raison / retrait |
|---|---|---|
| RF3-R1 | `ReferentialKind.CAISSE/AGENCE/CENTRE_PAYEUR` (descripteurs : champs, alias d'en-têtes, modèles d'import, libellés) restent dans l'administration générique | lecteur de fichiers commun ; retrait avec l'administration générique (butoir : fin de RF3) |
| RF3-R2 | Les lectures `/referentials/{caisses,agences,centres-payeurs,centres-payeurs-details}` restent servies par Organisation (`ReferentielsCommunsJdbc`, caches `ref.*`) | RF3b/RF3c |
| RF3-R3 | **SOLDÉ le 2026-11-10 par RF3d (ADR 0129) : KB-RF3-01 à 03 corrigés, 400 étendu à toute l'administration générique.** Texte d'origine : Les 500 du legacy (KB-RF3-01 à 03) ne sont PAS corrigés ; l'advice 400 de RF1c/RF2c n'est pas étendu aux payeurs | décision de l'utilisateur requise (modèle RF1c/RF2c) |
| RF3-R4 | Tri `ORDER BY code` selon la collation de la base (binaire sous H2) | inchangé par rapport au legacy |
| RF3-R5 | Les lecteurs des autres contextes (liste blanche de `TableDesPayeursTest`) lisent encore les trois tables | RF3b (annuaire) et RF3c (Comptabilité, jointures `GROUP BY` de Facturation) |

## Conséquences
- Fichiers de la plateforme touchés (filet RF3 vert, inscrits dans `covered.json`) : `ReferentialAdminTables`, `ReferentialAdminJdbcAdapter`, `DomainServiceConfig`, `PayeursParPatientCouverture` (nouveau). Aucun seam (`seamAllowed` reste `[]`).
- Retour arrière : revert Git.

## Questions ouvertes
- Confirmation métier de la propriété (ADR 0121, décision 2 : le centre payeur est un attribut du patient) avant le 2026-12-15.
- Faut-il corriger les 500 (KB-RF3-01 à 03) en 400 comme RF1c/RF2c ?
- Termes « Caisse d'assurance », « Agence », « Centre payeur » au glossaire de PC (`proposed`) ; « Agence » est-il assez précis (agence de la caisse) ?
