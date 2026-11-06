---
status: accepted
date: 2026-11-06
decision-makers: [utilisateur (décisions déléguées à l'assistant via le coordinateur, 2026-11-06), architecte]
context-scope: dossier-medical
related: ADR 0007, 0024 (refactoring sur place), 0065, 0072 (gel du module parallèle), 0076, 0079 ; `.ddd/migration/slices/dossier-medical-dm1-constantes.md`, `.ddd/safety-net/dossier-medical-dm1-constantes.md`
---

# 0080 - Dossier médical, DM1 : reprise sur place de la lecture des constantes

## À confirmer avec les métiers et le RGPD/DPO
Données de santé. Décisions prises par délégation, EN VIGUEUR mais À CONFIRMER :
1. **Droits** : le MEDECIN lit les constantes mais ne peut pas les saisir ; la lecture est refusée à l'INFIRMIER et à la SECRETAIRE (KB-MED-08). Reproduit.
2. **Audit des lectures** (KB-MED-04) : le legacy ne journalise que `GET .../dossier-medical` ; aucune autre lecture médicale n'est auditée. Rien n'est ajouté ; **question pour le DPO** : faut-il auditer toutes les lectures médicales (libellé métier, sans donnée de santé dans le journal) ?
3. **Diffusion du nom et du prénom du patient** sur le canal temps réel du centre à l'enregistrement du volet (KB-MED-05). Reproduite ; minimisation à décider.
4. **Bornes physiologiques** (poids ]0 ; 500], tension 20 à 400 mmHg, durée/débit/UF sans borne) et **ambiguïté cmHg** (« 12/8 » devient 120/80, « 30/20 » devient 300/200) (KB-MED-09/10) : reproduites telles quelles, à décider avec un expert clinique.
5. **Fuseau des statistiques** (`stats/paramedical` rend les dates au fuseau de la JVM, KB-MED-14) : reproduit (non touché).
6. **Plafond de taille de page à 100** et **patient inconnu = page vide en 200** (KB-MED-03, 06).

## Contexte
Le module `contexts/dossier-medical` était gelé (ADR 0072) : modèle parallèle sans parité, URL et tables inventées. Le filet DM1 (26 tests, 35 goldens, KB-MED-01 à 14) est vert sur le legacy depuis 542c7571. Le legacy n'a qu'une route « constantes » : `GET /patients/{id}/constantes`, lecture seule d'une jointure `seances` x `volet_paramedical`.

## Décision
1. **Reprise SUR PLACE** : le module est ré-écrit contre les vraies tables (`seances`, `volet_paramedical`), aucune table parallèle ni colonne ajoutée (`center_id` compris), même URL, mêmes noms de champs JSON, mêmes rôles (`MEDECIN`, `ADMIN`). Le contenu gelé est retiré du module (historique git, 542c7571) et remis au réacteur (`backend/pom.xml`, `platform/pom.xml`, `Dockerfile`).
2. **Lecture pure, sans agrégat** : DM1 n'a aucun invariant d'écriture ; la modélisation en agrégat `Constante` (tactique) était fausse. Le domaine porte des valeurs (`Constantes`, `ConstantesDeSeance`, `Pagination`, `PageDeConstantes`) et un port de lecture `HistoriqueDesConstantes`. Les constantes sont saisies par la Séance (`PUT volet-paramedical`, SE2) ; DM1 n'en reprend pas l'écriture. FHIR et KDIGO ne lisent pas les constantes et n'y seront pas branchés.
3. **Garde de centre** (écart décidé) : le centre est celui du jeton (`SessionCourante`, ACL `SessionLegacy`) ; un `centerId` différent est refusé (403). Mêmes motifs de refus que `CenterAccessGuard`.
4. **Entrées illisibles** (écart décidé) : paramètre, identifiant ou `centerId` illisible : 400 (avant : 500) ; page ≥ 0, taille de 1 à 100 : 400 sinon (avant : taille illimitée).
5. **Reproduit** : tri date de séance puis création décroissants, tous statuts de séance, volet d'un autre centre ignoré, séance sans volet = constantes vides, valeurs brutes non normalisées (KB-MED-11), absence de cache. **Écart de détail (déterminisme, revue DM1)** : l'identifiant de séance décroissant est AJOUTÉ en départage final ; le legacy laissait l'égalité complète au SGBD. Ce n'est pas du legacy reproduit.
6. **Sécurité immédiate** (KB-MED-01 et 02, petites éditions legacy autorisées, seams fermés le jour même) : garde de centre (`CenterAccessGuard.requireCenter`, centre demandé ou ciblé comparé à celui du jeton, 403 sinon, comportement inchangé pour le bon centre) sur :
   - écriture : `PUT /seances/{id}/volet-paramedical` (le `centerId` du corps n'est plus cru) ;
   - lectures de mesures : `GET /seances/{id}`, `GET /seances` (liste), `GET /seances/journal`, `GET /seances/calendar`, `GET /patients/{id}/stats/paramedical` et `stats/medical`, `GET /patients/{id}/stats/export` (CSV/PDF), et les trois `POST` de recherche correspondants (`stats/paramedical/search`, `stats/medical/search`, `stats/export/search` : centre RÉSOLU) ;
   - écritures de séance : `POST /seances`, `POST /seances/scan`, `PUT /seances/{id}`.
   Les goldens des autres filets (Séance, Stock, Facturation, Planification) n'ont changé que pour des scénarios inter-centres (voir la fiche).
7. **Reste SANS garde de centre, par décision** (périmètre Séance, SE2 à SE6, hors constantes) : `PUT /seances/{id}/forfait`, `POST .../valider`, `POST .../deverrouiller-regularisation`, `POST .../signer-medecin`, consommables (`POST/PUT/DELETE`), jours fériés et fermetures (`/calendar/holiday`, `/calendar/closure`). Ces routes écrivent sur la séance du `centerId` fourni ; leur fuite éventuelle est à fermer avec leur slice (échéance : SE2 à SE6) ; recensées dans KB-MED-02. Les lecteurs SQL de la sérologie (DM4/DM8) restent en place.
8. **ACL de session par réflexion** : `SessionLegacy` lit `getCenterId()` du principal par réflexion (le module n'a pas de dépendance Maven vers la plateforme). **Fragilité** : renommer cet accesseur ferait refuser (403) toute lecture ; les goldens et `SeanceDetailsIntegrationTest` (principal réel) échoueraient immédiatement. Retrait daté avec l'extraction d'Identité et accès ; remplacer la réflexion par un type de principal publié à ce moment.

## Découpage réel DM1..DM8 (trous d'inventaire acceptés)
DM2 = ordonnances, prescriptions, anémie/observance/alertes/administrations et planificateur 06:30 ; DM3 = demandes d'examen ; DM4 = sérologie ; DM5 = antécédents, allergies, abords vasculaires ; DM6 = biologie ; DM7 = bilan pré-greffe, donneurs vivants ; DM8 = `DossierMedicalPatient`, FHIR, `stats/medical`, lecteurs Direction et Planification.

## Conséquences
- Écarts de parité : 8 goldens `_nouveau_code`, ancien comportement archivé dans `characterization/dossiermedical/dm1/_ecarts-legacy`.
- Preuves : contrat du port sur le double, H2 et PostgreSQL 16 (`backend/scripts/dossier-medical-dm1-pg16-rejeu.sh`, aussi les 14 entités médicales contre `information_schema`), ArchUnit, `check-arch.ts`, PIT du domaine et de l'application.
- Le tactique `.ddd/tactical/dossier-medical.md` (agrégat `Constante`, `EnregistrerConstante`) est périmé pour DM1 ; il est révisé slice par slice.
