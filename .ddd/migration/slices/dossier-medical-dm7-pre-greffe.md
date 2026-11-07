---
slice: dossier-medical-dm7-pre-greffe
status: built
date: 2026-11-07
order: 7
context: dossier-medical
sources: [.ddd/tactical/dossier-medical.md, ADR 0065, ADR 0086, .ddd/safety-net/dossier-medical-dm7-pre-greffe.md, known-bugs.md KB-MED-85 à 97]
---

> **Refactoring SUR PLACE** (ADR 0024, 0072, 0086) : pas de façade, de routage ni de flag. Mêmes tables (`bilans_pre_greffe`, `decisions_rcp`, `etapes_bilan_pre_greffe`, `donneurs_vivants`), mêmes URL (`greffe/{bilan,etapes,donneurs,kdigo,export-pdf}`), mêmes rôles. Bascule = déploiement du commit ; rollback = retour au commit précédent. KDIGO et l'export PDF restent dans la plateforme (calculs et contenu reproduits), avec la garde de patient en plus.

# DM7 — Bilan pré-greffe : bilan du receveur, RCP, étapes, donneurs vivants, KDIGO, export PDF

## RISQUE CLINIQUE PRIORITAIRE (à confirmer par les médecins et le DPO)
KB-MED-94 est REPRODUIT : pour un même marqueur sérologique, KDIGO retient le résultat le plus ANCIEN ; une alerte « positif récent derrière un négatif ancien » peut manquer. Voir l'ADR 0086 (en tête).

## Fait
| Couche | Contenu |
|---|---|
| Domaine | `BilanPreGreffe` (racine avec ses `DecisionRcp`, machine à états portée par `StatutDuBilanPreGreffe`, bornes), `EtapeDeBilan`, `DonneurVivant`, `ListeStandardDesEtapes` (23 étapes), énumérations `AvisRcp`, `CategorieDEtape`, `StatutDEtape`, `LienDeParente`, `StatutDuBilanDuDonneur`, `ResultatDeCrossmatch`, ports `BilansPreGreffe`, `EtapesDeBilan`, `DonneursVivants`, refus `EtapeDeBilanIntrouvable`, `DonneurVivantIntrouvable`, `BilanPreGreffeIllisible`, `EcritureConcurrenteSurUnBilanPreGreffe`, `EcritureConcurrenteSurUneEtapeDeBilan`, `EcritureConcurrenteSurUnDonneurVivant` |
| Application | `ConsulterLeBilanPreGreffe`, `ChangerLeStatutDuBilanPreGreffe`, `MettreAJourLeBilanImmunologique`, `MettreAJourLesNotesDuBilan`, `AjouterUneDecisionDeRcp` (ouverture commune `OuvertureDuBilanPreGreffe`), `ConsulterLesEtapesDeBilan`, `AjouterUneEtapeDeBilan`, `GenererLesEtapesStandard`, `MettreAJourUneEtapeDeBilan`, `SupprimerUneEtapeDeBilan`, `ConsulterLesDonneursVivants`, `EnregistrerUnDonneurVivant`, `MettreAJourUnDonneurVivant`, `SupprimerUnDonneurVivant` ; réutilise `PatientsDuCentre`, `SessionCourante`, `UniteDeTravail` (lock_timeout 5 s), `HorlogeDuCentre`, `GenerateurDIdentifiants` |
| Infrastructure | `BilansPreGreffeJdbc`, `EtapesDeBilanJdbc`, `DonneursVivantsJdbc`, `VerrousJdbc` (55P03 vers 409, verrous consultatifs `bilan-pre-greffe|` et `etapes-pre-greffe|`) |
| Interface | `BilanPreGreffeControleur` (5 routes), `EtapesDeBilanControleur` (5), `DonneursVivantsControleur` (4), DTO, `ErreursDuDossierMedicalAdvice` étendu (409 x3, 404 étape et donneur, 422 bilan illisible, 403 avant 400), marqueur `SousLaGardeDuDossierMedical` ; garde de patient 404 sur `KdigoGreffeRestController` et `GreffeExportRestController` (plateforme) |
| Composition | `DossierMedicalConfiguration` (horloge UTC dédiée `horlogeDuBilanPreGreffe`, 3 adapters, 14 cas d'usage, 3 contrôleurs) |

## Parité
Filet `characterization/dossiermedical/dm7` : **38/38 scénarios verts sur le nouveau code**, 38 goldens : 19 identiques au legacy, **19 écarts décidés** en goldens `*_nouveau_code`, legacy archivé octet pour octet depuis 9e60ec9f dans `dm7/<famille>/_ecarts-legacy/` (vérifié par comparaison avec `git show`). Aucun golden hors DM7 modifié ; calculs KDIGO et contenu du PDF inchangés.

| Écart | Décision |
|---|---|
| KB-MED-85 / 86 (aucune garde de patient, une lecture qui écrit pour un patient invalide) | corrigé (404) ; création paresseuse reproduite pour un patient valide |
| KB-MED-87 / 89 (500 au lieu de 400, 403 après le corps, noms de classe) | corrigé |
| KB-MED-88 (introuvable = 400, DELETE audité sans effet) | corrigé : 404, DELETE sans audit |
| KB-MED-90 (doublons de bilan, étapes dupliquées, ligne illisible bloquante) | corrigé sans changement de schéma (verrous consultatifs, lecture robuste) |
| KB-MED-91 (dernier écrivain gagnant, décisions perdues) | corrigé (verrou de ligne, 409) |
| KB-MED-92, 93, 95, 96, 97 | reproduits, consignés (ADR 0086, `known-bugs.md` « Décisions DM7 ») |
| KB-MED-94 (le plus ancien résultat sérologique l'emporte) | **reproduit, risque clinique prioritaire** |

## Reste à faire
- Confirmation métiers, médecins et DPO des décisions reproduites, KB-MED-94 d'abord (ADR 0086).
- **Retrait des résidus Java lus par KDIGO, le PDF, les statistiques et la direction** : agrégats et ports legacy du greffe (liste nominative dans l'ADR 0086) ; **revue au plus tard le 2026-12-15**.
- Reprise de KDIGO et du PDF dans le module (bibliothèque PDF et port de lecture des sérologies) : hors DM7.
- Nettoyage des doublons et des lignes illisibles en base : décision métiers et DBA (`verifications-production.md` §51).

## Conditions de mise en production (revue DM7)
1. KB-MED-94 : décision médicale écrite OU acceptation écrite du risque (direction médicale + DPO) avant la mise en production, au plus tard avant la revue du 2026-12-15 (ADR 0086).
2. Rejeu vert de `dossier-medical-dm7-pg16-rejeu.sh` (unicité du bilan, que H2 ne prouve pas) : dernier rejeu 2026-11-07, 33 tests verts, mot de passe par `DOSSIERMEDICAL_PG16_PASSWORD`.
3. Verrou ArchUnit `FrontieresDesContextesTest` : aucun appelant des écritures legacy résiduelles.

## Preuves
Domaine (21), application avec fakes (8), contrat par port sur double (20), H2 (28), PostgreSQL 16 jetable (33 tests dont 6 verrous à deux connexions ou consultatifs, tous verts) via `backend/scripts/dossier-medical-dm7-pg16-rejeu.sh` (port 55486, jamais 5432 : chaîne Flyway, colonnes exactes, absence de contrainte d'unicité, requêtes du §51), advice (7), ArchUnit du module. Les cas d'usage n'écrivent qu'un agrégat par transaction : pas de test de transaction multi-écritures. `check-arch.ts` 0 violation, `FrontieresDesContextesTest`, `./mvnw -o test -fae` vert.

## Plan de bascule (refactoring sur place, pas de flag)
Recette de `verifications-production.md` §51 avant déploiement ; surveiller les 400 / 403 / 404 / 409 / 422 et les WARN « illisible en base » et « doublon » ; rollback = commit précédent. Aucune bascule progressive, aucune activation de production par l'assistant. État du flag : aucun.
