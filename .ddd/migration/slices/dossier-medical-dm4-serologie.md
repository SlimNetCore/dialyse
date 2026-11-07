---
slice: dossier-medical-dm4-serologie
status: built
date: 2026-11-07
order: 4
context: dossier-medical
sources: [.ddd/tactical/dossier-medical.md, ADR 0065, ADR 0083, .ddd/safety-net/dossier-medical-dm4-serologie.md, known-bugs.md KB-MED-45 à 57]
---

> **Refactoring SUR PLACE** (ADR 0024, 0072, 0083) : pas de façade, de routage ni de flag. Mêmes tables (`serologies_patient`, schéma gelé `serologies` ni lu ni écrit), mêmes URL, mêmes rôles. Bascule = déploiement du commit ; rollback = retour au commit précédent. Décisions déléguées, **à confirmer avec les métiers et le DPO**.

# DM4 — Sérologies du patient

## Fait
| Couche | Contenu |
|---|---|
| Domaine | `Serologie` (racine, règle de positivité, date future, bornes de colonne), `MarqueurSerologique` (13), `ResultatSerologique` (`exigeUneConduiteATenir`), `StatutSerologique` (dernier résultat par marqueur), `SerologieIntrouvable`, `EcritureConcurrenteSurUneSerologie`, port `Serologies`, `PageDeSerologies` |
| Application | `EnregistrerUneSerologie`, `CorrigerUneSerologie`, `SupprimerUneSerologie`, `ConsulterLesSerologiesDuPatient` ; port sortant `IsolementDesPatients` ; réutilise `SessionCourante`, `UniteDeTravail` (lock_timeout 5 s), `HorlogeDuCentre`, `GenerateurDIdentifiants` |
| Infrastructure | `SerologiesJdbc` (verrou de ligne `FOR UPDATE`, verrou consultatif du triplet sur PostgreSQL, 55P03 vers 409), ACL `IsolementDesPatientsParLaPlanification` (port publié `ReaffectationEnIsolement` P5 et `VerrouDesPlacesDuCentre`, inchangés) |
| Interface | `SerologiesControleur` (mêmes 5 routes), DTO, `ErreursDuDossierMedicalAdvice` étendu (403 avant 400, 409, 400 introuvable) |
| Composition | `DossierMedicalConfiguration` (horloge UTC dédiée `horlogeDesSerologies`) ; dépendance Maven vers `contexts-planification` (langage publié seulement) |

## Parité
Filet `characterization/dossiermedical/dm4` : **26/26 scénarios verts sur le nouveau code**. 41 goldens : 29 identiques au legacy, **12 écarts décidés** en goldens `*_nouveau_code`, legacy archivé octet pour octet depuis 863ff37d dans `dm4/_ecarts-legacy/{consultation,droits,enregistrement}/<scénario>__<nom>.legacy.json`.

| Écart | Décision |
|---|---|
| KB-MED-47 (500 vers 400 ; 403 avant 400) | corrigé |
| KB-MED-48 (nom de classe interne dans le détail) | corrigé |
| KB-MED-52 (bornes de colonne 500 vers 400) | corrigé |
| KB-MED-49 partiel (DELETE sans effet : 204 reproduit, plus d'audit mensonger ; introuvable 400 reproduit) | corrigé en partie |
| KB-MED-56 (unicité) | verrou consultatif, aucune migration ; doublons existants : `verifications-production.md` §48 requête 2 |
| KB-MED-45, 46, 50, 51, 53, 54, 55, 57 | reproduits et consignés (ADR 0083, `known-bugs.md` « Décisions DM4 ») |
| Golden P5 `requetes_et_perimetre_nouveau_code` | modifié sur une ligne (corps vide 500 vers 400, conséquence de KB-MED-47) ; ancienne version archivée dans `p5-placement/_ecarts-legacy/isolement/requetes_et_perimetre_avant_dm4.legacy.json` ; le reste du comportement P5 est inchangé |
| Lignes illisibles en base | ignorées par les lectures, donc ni corrigées ni supprimées par le module ; WARN avec identifiant seul ; repérage §48 requête 3 (limite consignée, ADR 0083) |

## Reste à faire
- Confirmation métiers et DPO des décisions reproduites (liste dans l'ADR 0083) ; contrainte d'unicité en base (migration non décidée).
- DM7 : KDIGO et `GreffePdfService` lisent encore `SerologieRepositoryPort` (adapter JPA et entité conservés, jalon de retrait DM7) ; DM8 : `DirectionIndicatorsQueryService`, import et rollback d'historique lisent la table en SQL.
- Prédicat unique de risque infectieux publié par le dossier médical (KB-MED-53) : hors DM4 (Planification et Direction non modifiées).

## Preuves
Domaine (30), application avec fakes (15), contrat fake / H2 / PostgreSQL 16 (8 + 11 + 14, dont verrou de ligne et verrou du triplet à deux connexions), transaction H2 (4), ACL (3), advice (8), ArchUnit du module ; script `backend/scripts/dossier-medical-dm4-pg16-rejeu.sh` ; `check-arch.ts` 0 violation.

## Plan de bascule (refactoring sur place, pas de flag)
Recette de `verifications-production.md` §48 avant déploiement ; surveiller 400 / 403 / 409 ; rollback = commit précédent.
