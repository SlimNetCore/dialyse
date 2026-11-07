---
slice: dossier-medical-dm8-lectures-publiees
status: built
date: 2026-11-07
order: 8
context: dossier-medical
sources: [.ddd/tactical/dossier-medical.md, ADR 0065]
---

> Statut vrai au 2026-11-02 : `planned` (module gelé, `backend/contexts/dossier-medical/GEL.md`, ADR 0072). Rien n'est retiré du legacy ; la garde de tables `TableDuDossierMedicalTest` est désactivée (elle ne listait pas les tables réelles).

> **Découpage réel (2026-11-06, ADR 0080)** : `planned`. DM8 porte : `DossierMedicalPatient` (`dossier-medical` GET/POST/PUT, seule lecture auditée), l'export **FHIR** (`dossier-medical/export-fhir` ; ne lit pas les constantes et n'y sera pas branché), `stats/medical`, `stats/export`, et les lecteurs SQL de la **Direction** (`DirectionIndicatorsQueryService`, `DirectionBreakdownQueryService`) et de la **Planification** (`RisquesInfectieuxJdbc`, `ContraintesDesPatientsJdbc`) ; volet médical de la séance (`volet_medical`) lu ici, écrit par la Séance. Tables : `dossier_medical_patient`, `volet_medical`. À la fin de DM8, la garde de tables sera réécrite sur les tables réelles.

## État de la reprise (2026-11-07, ADR 0087) : `built`, refactoring SUR PLACE, ni façade, ni flag, ni projection, ni événement

**Fait** : dossier de base (GET, POST, PUT) et export FHIR repris dans `contexts/dossier-medical` (agrégat `DossierMedicalDeBase`, cas d'usage, adapters JDBC, ACL sur le langage publié `FichesDesPatients.identite`, contrôleurs `DossierMedicalDeBaseControleur` et `ExportFhirControleur`, advice) ; mêmes URL, rôles et table ; garde de patient et 400/403/404/409 ; couche d'erreur des statistiques médicales et des indicateurs Direction alignée (400 au lieu de 500, 403 avant 400) ; code legacy mort retiré (voir ADR 0087) ; verrou d'architecture ; RG-MED-049b à 049e, glossaire, `verifications-production.md` §52, `known-bugs.md` « Décisions DM8 », `seams.md` (FERMÉ, `seamAllowed` vide).

**Parité** : 38 scénarios du filet ; 26 inchangés, 12 écarts décidés (`*_nouveau_code`) : KB-MED-98 (dossier : `sans_garde_de_patient`, `ecritures_simultanees`), KB-MED-99 (`valeurs_et_bornes`, `refus`, `audit`), KB-MED-102 (FHIR : `patient_inconnu_ou_autre_centre`, `lignes_illisibles`), KB-MED-103 (stats : `periodes`, `csv`, `recherche`, `sans_donnees`), KB-MED-105 (Direction : `periodes`). Reproduits sans changement de golden : KB-MED-100, 101, 104.

**Preuve PostgreSQL 16 (2026-11-07, cluster jetable, port 55497)** : `backend/scripts/dossier-medical-dm8-pg16-rejeu.sh` : chaîne Flyway, 11 colonnes, unicité de `patient_id`, requêtes du §52, ordre FHIR des abords sans date (`a4,a1,a3,a2`), 23 tests de contrat verts, code de sortie 0.

**Reste à faire (NON fait, volontairement)** : (1) reprise de KDIGO et du PDF de greffe (plateforme, garde seulement) ; (2) reprise des statistiques médicales et de leur export CSV/PDF (le module n'a pas de bibliothèque) ; (3) reprise des lecteurs Direction (`DirectionIndicatorsQueryService`, `DirectionBreakdownQueryService`) et Planification SQL (`RisquesInfectieuxJdbc`, `ContraintesDesPatientsJdbc`) par des lectures publiées ; (4) volet médical de la séance (`volet_medical`) lu mais non repris ; (5) retrait de `bc-medical` et réécriture de la garde de tables `TableDuDossierMedicalTest` (toujours `@Disabled`) ; liste nominative et jalons dans l'ADR 0087, revue au plus tard le 2026-12-15.

**Conditions de mise en production** : arbitrage écrit du DPO sur KB-MED-104 (fuite par différence des totaux Direction) et des médecins/DPO sur KB-MED-101 (export FHIR tronqué en silence), avant le 2026-12-15 (ADR 0087, en tête).

**Plan de bascule** : sans objet (refactoring sur place, ADR 0024/0072 : pas de palier ni de flag). Surveillance et rollback : `verifications-production.md` §52 (hausse attendue des 400 et 404, surveiller les 409 `DOSSIER_MEDICAL_ECRITURE_CONCURRENTE` et les WARN d'ignorance) ; rollback = revenir au commit précédent.

# DM8 — Lectures publiées et retrait du legacy Dossier médical

Tous les consommateurs (Seance, Facturation, Planification, Pilotage, Patient & couverture) passent par
dossiermedical.v1 et ports publiés (`RisquesInfectieux`, `AbordsVasculaires`). Retrait de bc-medical. Garde « pas de SQL
sur les tables du Dossier médical hors contexte ». Données de santé : cloisonnement strict.