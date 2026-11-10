---
status: accepted
date: 2026-11-10
review-by: 2026-12-15
decision-makers: [utilisateur (demande explicite de CORRIGER les anomalies reproduites jusqu'ici, 2026-11-10 ; confirmation des métiers Absences, Planification, Reprise, Soins (infirmier référent) et des médecins attendue au plus tard le 2026-12-15), architecte]
context-scope: seance (infrastructure/persistence LecturesDesSeancesRealiseesJdbc, ConstantesDesSeancesJdbc, StatistiquesDesSeancesJdbc, SeancesDeLaRepriseJdbc), dossier-medical (DerniereCreatinineJdbc, Javadoc EvaluationKdigoDuCandidat), plateforme (cahier_dialyse.jrxml, goldens), tests de contrat (mémoire, H2, PostgreSQL 16)
related: ADR 0098 (SE7d), 0099 (SE7e), 0100 (SE7f) (reproductions), 0024 et 0075 (refactoring sur place), 0116 (lot L4) ; `.ddd/migration/corrections-kb-2026-11-09.md` (section 5, lot L5) ; `.ddd/safety-net/known-bugs.md` (KB-SEA-77 à 96) ; `.ddd/migration/verifications-production.md` §79 ; RG-SEA-062 à 068 et 071 (annexe 16)
---

# 0117 - Lot L5a (Séance : tris déterministes, divergences de SGBD, lectures) : KB-SEA-77, 78, 79, 81, 86, 88, 89 et 96 (a) CORRIGÉS ; KB-SEA-87 et 96 (b) CLOS

Ce n'est PAS une migration à parité : le comportement CHANGE volontairement, à la demande de l'utilisateur. Les ADR 0098 à 0100 avaient REPRODUIT ces anomalies
(refactoring sur place, mêmes tables, mêmes URL, mêmes rôles). Ni façade, ni flag, ni projection, AUCUNE migration Flyway, AUCUNE donnée réécrite.
La **confirmation des métiers reste à obtenir (au plus tard le 2026-12-15)**.

## Décisions
| KB | Règle appliquée | Où |
|---|---|---|
| KB-SEA-77 | Jours réalisés du centre : `ORDER BY s.date_seance, s.patient_id` (colonnes de la sélection `DISTINCT`) | `LecturesDesSeancesRealiseesJdbc.joursRealisesDuCentre` |
| KB-SEA-78 | Séances réalisées avec leur place : `ORDER BY s.date_seance, s.created_at NULLS LAST, s.id` : la première lue par la Planification est la plus ancienne créée (intention « première rencontrée » conservée, choisie plutôt que « dernière créée ») ; la Javadoc menteuse (« date puis identifiant ») est remplacée | `LecturesDesSeancesRealiseesJdbc.duCentre` |
| KB-SEA-79 | Historique des constantes : `created_at DESC NULLS FIRST` écrit : l'ordre de PRODUCTION (PostgreSQL 16) est conservé, H2 est aligné. `NULLS LAST` (sémantiquement plus juste) est écarté parce qu'il changerait l'ordre déjà servi aux utilisateurs | `ConstantesDesSeancesJdbc` |
| KB-SEA-81 | `absenceLiee` ne bloque que si la séance liée est réellement exclue : `AND statut = 'ABSENT'`. Un lien résiduel sur une séance revenue à un autre statut ne bloque plus ; il RESTE en base (aucun `UPDATE` de nettoyage) | `LecturesDesSeancesRealiseesJdbc.absenceLiee` |
| KB-SEA-86 | Statistiques : tri `date_seance, created_at NULLS LAST, id` (poids et tension) ; clés des lignes de l'évolution du poids normalisées en minuscules par l'adaptateur (`evolutionDuPoids`), ce qui est le contrat de production (PG16). Frontend (`patient-stats.component.ts`, lit les deux casses) et PDF (`safeCell`, deux casses) NON modifiés | `StatistiquesDesSeancesJdbc` |
| KB-SEA-87 | **CLOS, non corrigeable en code** : `AVG` d'un NUMERIC rend 12 décimales sur H2, 16 sur PG16. Aucun chiffre affiché ne change. Précision GARANTIE documentée : 10 décimales ; le contrat compare avec une tolérance de 1e-10 (plus de variante par SGBD) ; le PDF arrondit déjà à 2 décimales. Arrondir dans l'adaptateur (changement de contrat) n'est pas fait | `PilotageEtRapportsDesSeancesContrat` |
| KB-SEA-88 | Cahier de dialyse : une surcharge sans code recopié retombe sur le référentiel : `COALESCE(s.forfait_override_code, fo.code)` et `COALESCE(s.forfait_override_nom, fo.libelle)` avec `LEFT JOIN forfait fo ON fo.id = s.forfait_override_id AND fo.center_id = s.center_id` (borné au centre). Le PRIX reste celui de la surcharge (vide si absent). Non corrigé : la cause (`poserLeForfait` n'écrit pas le code, KB-SEA-47) et la casse du statut (KB-SEA-82). Un modèle de document PERSONNALISÉ du centre garde sa propre requête (§64.8) | `cahier_dialyse.jrxml` |
| KB-SEA-89 | Reprise : une séance `ANNULEE` du même centre, jour et patient ne bloque plus l'import (une séance annulée n'est pas une séance tenue) : `(statut IS NULL OR statut <> 'ANNULEE')` ; tous les autres statuts continuent de bloquer. KB-SEA-90 (séances du patient d'un autre centre bloquant la suppression du patient) et KB-SEA-91 (forfait imposé de n'importe quel centre) restent REPRODUITS : prudence voulue | `SeancesDeLaRepriseJdbc.seanceDejaPresente` |
| KB-SEA-96 (a) | Dernière créatinine : `ORDER BY date_prelevement DESC, created_at DESC NULLS LAST, id DESC LIMIT 1` sur les analyses du patient au centre à créatinine NON nulle (le `max(date)` en Java, redondant, disparaît) : à date égale la DERNIÈRE SAISIE fait foi. Alternative à faire valider par les médecins : la créatinine la plus haute (prudence clinique) | `DerniereCreatinineJdbc` |
| KB-SEA-96 (b) | **CLOS sans changement** : une créatinine à 0 reste retenue par le port et jugée « non évaluable » par `EvaluationKdigoDuCandidat` (créatinine ≤ 0), le comportement sûr : aucun stade faux. Sauter la valeur nulle ferait staguer le patient sur une mesure ancienne (plus risqué). La bonne correction est de refuser le 0 à la saisie (DM6, `AnalyseDemande`) : décision médicale, **validation des médecins attendue**. Javadoc ajoutée | `EvaluationKdigoDuCandidat` |

## Données et production
Aucun `UPDATE`/`DELETE`, aucune migration : les liens d'exclusion résiduels restent en base. `.ddd/migration/verifications-production.md` §79 donne quatre SELECT de détection (liens résiduels, créations nulles, surcharges sans code, égalités de date de créatinine). Rien n'est activé : aucun flag, aucune bascule (ADR 0024).

## Conséquences visibles
- Une absence dont le seul lien est sur une séance non `ABSENT` peut de nouveau être annulée.
- La case de la grille de planification d'un patient ayant deux séances le même jour est figée (la plus ancienne créée).
- Le cahier imprime le code et le libellé du forfait du référentiel pour une surcharge sans code (prix toujours vide dans ce cas).
- Un import de reprise ajoute la séance d'un jour où seule une séance annulée existait.
- Le stade KDIGO d'un patient ayant deux analyses de même date de prélèvement suit la dernière saisie.
- JSON des statistiques : clés en minuscules sur toute base (inchangé en production).

## Tests et goldens
Tests d'abord, ÉCHEC constaté sur le comportement actuel (contrat `SeancesRealiseesEtConstantesContrat` : mémoire 4 échecs, H2 3 échecs (le tri de KB-SEA-77 passait par chance sur H2) ; `PilotageEtRapportsDesSeancesContrat` H2 : 4 échecs ; `DerniereCreatinineContrat` mémoire et H2 : 2 échecs chacun ; golden du cahier : écart de texte). Contrats PG16 : les `*PostgresContractIT` sont les mêmes tests que le double et H2 ; rejeux `backend/scripts/seance-se7d-pg16-rejeu.sh`, `seance-se7e-pg16-rejeu.sh`, `seance-se7f-pg16-rejeu.sh` mis à jour. Goldens approuvés à la main, un par un, ancien archivé sous `_ecarts-legacy` :
| Golden | Changement | Archive |
|---|---|---|
| `seance/se7e/cahier/.../surcharge_de_forfait_nouveau_code` | page 2 : « Forfait : - » devient « Forfait : SURCH — Forfait SURCH » | `seance/se7e/_ecarts-legacy/cahier/surcharge_de_forfait_avant_l5a.legacy.json` |
| `seance/se7d/absences/.../annulation_et_lien_d_exclusion_nouveau_code` | lien sur séance `VALIDEE` : 422 devient 200 (absence annulée) ; port `false` (inchangé pour les trois autres cas) | `seance/se7d/_ecarts-legacy/absences/annulation_et_lien_d_exclusion_avant_l5a.legacy.json` |
| `seance/se7d/dossier-medical/une_creation_nulle_se_classe_en_prem/creation_nulle_en_premier_nouveau_code` (ex `creation_nulle_sur_h2`) | création nulle : en dernier devient en premier | `seance/se7d/_ecarts-legacy/dossier-medical/creation_nulle_sur_h2_avant_l5a.legacy.json` |
| `seance/se7e/reprise/.../deja_presente_et_annulation_bloquee_nouveau_code` | ligne du jour d'une séance `ANNULEE` : importée (créée 3 sur 4 au lieu de 2) ; un cas VALIDEE ajouté qui reste conservé | `seance/se7e/_ecarts-legacy/reprise/deja_presente_et_annulation_bloquee_avant_l5a.legacy.json` |
| `seance/se7e/statistiques/.../bornes_de_periode_nouveau_code`, `.../tous_statuts_et_volet_d_un_autre_centre_nouveau_code` | clés `DATE_SEANCE`, `POIDS_*` en minuscules (valeurs et ordre inchangés) | `seance/se7e/_ecarts-legacy/statistiques/*_avant_l5a.legacy.json` |
| `dossiermedical/dm1/ecriture/.../stats_nouveau_code`, `dossiermedical/dm1/lecture/.../autres_lecteurs_nouveau_code` | mêmes clés en minuscules | `dossiermedical/dm1/_ecarts-legacy/**/*_avant_l5a.legacy.json` |
`seance/se7e/statistiques/export_pdf` et les goldens de la Planification sont INCHANGÉS.

## Alternatives écartées
- `NULLS LAST` pour KB-SEA-79 : change l'ordre servi en production.
- « Dernière créée » pour KB-SEA-78 : change l'intention de la Planification (première rencontrée).
- Arrondir les moyennes à 2 décimales dans l'API (KB-SEA-87) : changement de contrat, non nécessaire.
- Sauter la créatinine à 0 (KB-SEA-96 b) : stade sur une mesure ancienne, plus risqué que « non évaluable ».
- Nettoyer les liens d'exclusion résiduels (KB-SEA-81) : `UPDATE` de données, interdit sans décision écrite.
