---
status: accepted
date: 2026-11-08
review-by: 2026-12-15
decision-makers: [utilisateur (décisions déléguées à l'assistant via le coordinateur, 2026-11-08), architecte]
context-scope: seance
related: ADR 0002, 0024 (refactoring sur place), 0062, 0074, 0088 (SE1), 0089 (SE2, déverrouillage), 0090 (SE3, même démarche) ; `.ddd/safety-net/seance-se4-derogation-regularisation.md` (net 10710fd0, 10 scénarios, 10 goldens), `.ddd/migration/slices/seance-se4-derogation-regularisation.md`, KB-SEA-35 à 40
---

# 0091 - Séance, SE4 : reprise sur place du rappel quotidien de régularisation (la « dérogation » n'est pas un agrégat)

## RISQUE À FAIRE CONFIRMER AVANT LA MISE EN PRODUCTION (décision REPRODUITE, condition de mise en production)
Toutes les décisions de cet ADR sont prises par délégation, EN VIGUEUR, **à confirmer métiers / DPO**. **Jalon daté : le risque ci-dessous (KB-SEA-37) est à arbitrer au plus tard le 2026-12-15 (revue) ; aucune mise en production avant sa décision écrite.**

1. **KB-SEA-37 : fenêtre du rappel de 7 jours alors que le verrou des jours passés n'a aucune borne.** L'infirmier ne peut pas valider une séance d'un jour passé tant que l'administrateur ne l'a pas déverrouillée (422, SE2) ; or le rappel quotidien ne regarde que les sept derniers jours (hier compris, aujourd'hui exclu). Une séance « créée » oubliée depuis **huit jours ou plus** reste donc bloquée pour l'infirmier, n'est plus jamais rappelée à l'administrateur, et le patient reste compté absent. REPRODUIT (fenêtre inchangée : `FenetreDeRegularisation.JOURS = 7`, test des bornes -9 à +30). **Condition de mise en production** : décision écrite des métiers parmi (a) rappel sans limite de fenêtre (ou fenêtre plus large), (b) borne du verrou alignée sur celle du rappel, (c) acceptation écrite du risque avec une procédure de revue périodique ; **requête de contrôle SELECT seule** : `verifications-production.md` section 56 (séances « créées » hors fenêtre, par centre). Tant que la décision n'est pas prise, l'exploitant doit lancer cette requête chaque semaine.

## Reproduits sans alerte
- **KB-SEA-38** : une séance « créée » dont le patient a disparu est comptée par le rappel, refusée au déverrouillage (404 `PATIENT_INTROUVABLE`, SE2) et à la validation : seule issue, une intervention en base (décision métier : suppression ou statut). La requête de contrôle est en section 56.
- **KB-SEA-39** : le détail `GET /api/v1/seances/{id}` ne porte pas `regularisationDeverrouilleeAt` (la liste le porte). Contrat inchangé.
- Droits (le rappel n'a ni route ni droit ; cible de la diffusion `ADMIN`), libellés et charge de l'alerte (`nbSeances`, `plusAncienne` vide si inconnue), cron à 07:00, un centre en erreur n'arrête pas les autres, tous les centres de `centers` sans filtre d'activité.

## Contexte
Le net SE4 (10 scénarios) a constaté qu'il n'existe **aucun agrégat `DerogationRegularisation`** : la « dérogation » du modèle tactique est le couple `seances.regularisation_deverrouillee_at/by`, déjà servi par le module depuis SE2 (`DeverrouillerUneSeance`, 8 goldens). Ce qui reste hors du module est le **rappel quotidien** : `SeanceRegularisationScheduler` (cron `0 0 7 * * *`, tous les centres), `SeanceRegularisationService.aRegulariser` (lecture de l'historique : statut `CREE`, 7 jours, non déverrouillées), `NotificationService.notifySeancesARegulariser` (diffusion temps réel `SEANCES_A_REGULARISER` cible `ADMIN` + ligne durable de `notification_evenement`).

## Décisions
1. **Dans `backend/contexts/seance`**, mêmes tables, aucune migration ni colonne ni table (ADR 0024).
2. **PAS d'agrégat `DerogationRegularisation`** (KB-SEA-40) : rien ne l'écrit, le déverrouillage ne demande ni motif ni autorité (l'auteur est le compte de session), créer l'agrégat serait inventer un comportement. **La table V50 `derogations_regularisation` n'est PAS supprimée** (aucune opération destructive, ADR 0024) : conservée, vide, jamais écrite par le module ; consignée en résidu (revue 2026-12-15) ; contenu contrôlé par une requête SELECT seule en production (section 56). La question « motif et autorité du déverrouillage » reste une demande métier ouverte.
3. **Domaine** (pur) : `FenetreDeRegularisation` (J-7 à J-1, aujourd'hui exclu, recherche des oubliées : `CREE`, non déverrouillées, plus ancienne d'abord), `SeancesARegulariser` (nombre, plus ancienne), `RappelDeRegularisation` (n'existe que s'il y a des séances à régulariser ; unique par centre et par jour du centre). Aucun nouvel événement : le rappel est une politique de lecture.
4. **Application** : `RappelerLesSeancesARegulariser` (`executer()` pour tous les centres à leur jour du centre ; `executerPourLeCentre(centre, jour)` pour un jour explicite, utilisé par les tests ; l'heure n'est jamais lue par le cas d'usage : `HorlogeDuCentre` est injectée). Ports : `CentresARappeler`, `RappelsDeRegularisation`, `VerrousDesRappels` ; lecture par le port existant `HistoriqueDesSeances` (une page d'une ligne : nombre total et plus ancienne).
5. **Adapters** : ACL `RappelsParLeLegacy` sur le langage publié `RappelsFournis` (fourni par la plateforme : `RappelsFournisLegacy`, identifiant déterministe UUID v3 de « SEANCES_A_REGULARISER | centre | jour du centre »), ACL `CentresARappelerJdbc` (lecture de `centers`), verrou `VerrousDesRappelsJdbc` (`pg_advisory_xact_lock`, préfixe `rappel-regularisation|`, 55P03 -> `EcritureConcurrente`). Planificateur entrant dans le module : lot B.
6. **Décisions par bug connu** :

| KB | Décision | Raison |
|---|---|---|
| KB-SEA-35 | **CORRIGER** : une alerte par centre et par jour du centre (identifiant déterministe dans le journal existant, aucune nouvelle table) ; le double appel du même jour n'émet ni ne journalise une seconde fois ; deux instances : verrou consultatif (centre, jour) puis contrôle d'existence, et repli sur la violation de clé du journal | multi-instances, comme P3 à P5 et S8 |
| KB-SEA-36 | **CORRIGER** : le jour du rappel est celui du fuseau du centre (`HorlogeDuCentre`), plus le jour UTC ; le travail planifié du module est lancé à 07:00 dans le fuseau par défaut de la plateforme (`app.seance.fuseau-par-defaut`, repli sur celui des Absences et du Stock) | cohérent avec le verrou, le déverrouillage et le 13e écart de SE2 |
| KB-SEA-37 | **REPRODUIRE** (risque en tête) | décision métier prioritaire |
| KB-SEA-38, 39 | REPRODUIRE sans alerte | sans risque d'intégrité |
| KB-SEA-40 | NE PAS créer l'agrégat, NE PAS supprimer la table | aucune opération destructive |
| Droits, libellés | REPRODUIRE | contrat du legacy |

**Conséquence assumée du correctif KB-SEA-35** : le rappel du jour est figé à son premier passage. Un second passage du même jour (relance manuelle) après un déverrouillage ne renvoie pas un nombre mis à jour ; le travail planifié n'a qu'un passage par jour, et l'administrateur voit le nombre à jour dans la liste filtrée `deverrouillee`. Si le métier veut un rappel rejouable, la clé devrait inclure le nombre ; non retenu (dédoublonnage plus faible).

## Lot B (livré 2026-11-08) : planificateur et parité
- Routage : le rappel est déclenché par `RappelDesRegularisationsPlanifie` (module, `@Scheduled` `0 0 7 * * *`, fuseau `app.seance.fuseau-par-defaut`), seul planificateur actif ; `SeanceRegularisationScheduler` et `SeanceRegularisationService` (et leurs deux tests legacy) sont supprimés. Seam inscrit puis refermé dans `.ddd/safety-net/seams.md` ; `seamAllowed` vide ; entrées retirées de `covered.json` et de la liste blanche `TableDeLOrganisationTest` (le lecteur de `centers` est désormais `CentresARappelerJdbc`).
- Parité : 10/10 verts, 9 scénarios identiques au legacy (dont `cycle_complet`, `patient_disparu`, `lectures`, `deverrouillee_puis_validee`, `nominal`, `fenetre_de_sept_jours`, `statuts_comptes`, `la_plus_ancienne`, `lecture_seule`), 1 écart DÉCIDÉ : `rappel/cloisonnement_et_repetition`, étape 04 (deux appels du même jour) : un seul rappel diffusé et journalisé au lieu de deux (KB-SEA-35 corrigé, « fige au premier passage » assumé ci-dessus) ; les étapes 01 à 03 sont inchangées. Approuvé en `cloisonnement_et_repetition_nouveau_code.approved.json` ; golden legacy archivé octet pour octet (R100, vérifié par `git show 10710fd0:<chemin> | cmp`) sous `se4/_ecarts-legacy/rappel/cloisonnement_et_repetition_avant_se4.legacy.json`. À confirmer métiers.
- Harnais : le filet appelle `executerPourLeCentre(centre, jourFixe)` au lieu de `rappelerCentre` par réflexion ; un incident échoue le scénario.
- Verrous ArchUnit (`FrontieresDesContextesTest`) : seul le fournisseur du langage publié (`infrastructure.acl.seance`) appelle `NotificationService.notifySeancesARegulariser` ; seul `contexts/seance` dépend du cas d'usage (un second déclencheur provoquerait un double rappel). Test du planificateur : un seul `@Scheduled`, cron 07:00.
- KB-SEA-36 non rejouable dans le filet (le travail public lit l'heure) : couvert par les tests d'application (jour du centre à 23:30 UTC, un jour par centre).

## Lot C (livré 2026-11-08) : documentation et résidus
RG-SEA-055, annexe 16, glossaire (proposed), `verifications-production.md` §56 (SELECT seulement), fichier de slice `built`. **Pas de flag : le planificateur du module est actif dès le déploiement** (le legacy est supprimé, pas de double déclenchement). **Condition de déploiement** : une seule instance porte le planificateur, ou le verrou consultatif (centre, jour) et l'identifiant déterministe du journal sont vérifiés sur le PostgreSQL de production. Résidus nominatifs (revue au plus tard le 2026-12-15, date butoir de retrait 2027-01-31) : table V50 (décision métier, jamais de `DROP` sans validation écrite), `notifySeancesARegulariser` à trois arguments, ACL et fournisseur du langage publié, `CentresARappelerJdbc` (O4), fenêtre de KB-SEA-37, KB-SEA-38 et 39, rejeu bout en bout du travail planifié. Table complète : `.ddd/migration/slices/seance-se4-derogation-regularisation.md`.

## Lot A (livré 2026-11-08) et suite
LOT A : ADR, domaine, cas d'usage avec doubles, langage publié `RappelsFournis` et son fournisseur de la plateforme, ACL, adapters JDBC et contrats (mémoire, ACL, H2, PostgreSQL 16 par `seance-se4-pg16-rejeu.sh`), câblage du cas d'usage ; AUCUN trafic changé (le planificateur legacy tourne encore, la surcharge `notifySeancesARegulariser(…, idImpose)` de `NotificationService` est ajoutée, comportement historique inchangé). LOT B : planificateur dans le module, retrait du service et du scheduler legacy, parité 10/10, verrou ArchUnit. LOT C : documentation, résidus, `built`.
