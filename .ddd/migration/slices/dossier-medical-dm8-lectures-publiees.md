---
slice: dossier-medical-dm8-lectures-publiees
status: built
date: 2026-11-07
order: 8
context: dossier-medical
sources: [.ddd/tactical/dossier-medical.md, ADR 0065, ADR 0087]
---

> **PÉRIMÈTRE RÉEL LIVRÉ (revue DM8, 2026-11-07) : dossier médical de base + export FHIR SEULEMENT.** `built` ne vaut que pour ce périmètre. Le reliquat annoncé à l'origine (statistiques médicales et leur export, indicateurs Direction, lecteurs SQL Planification, `volet_medical`, KDIGO et PDF de greffe, retrait de `bc-medical`) n'est PAS repris : il est suivi dans la slice `dossier-medical-dm9-statistiques-et-lectures-sql` (`planned`, `.ddd/migration/slices/dossier-medical-dm9-statistiques-et-lectures-sql.md`). Pour les statistiques et la Direction, DM8 n'a aligné que la couche d'erreur (400, 403 avant 400).

> Historique (non à jour) : « Statut vrai au 2026-11-02 : `planned` (module gelé, ADR 0072) » et découpage du 2026-11-06 (ADR 0080) qui rangeait statistiques, Direction, Planification et `volet_medical` dans DM8 ; ce périmètre élargi est désormais celui de DM9.

## État de la reprise (2026-11-07, ADR 0087) : refactoring SUR PLACE, ni façade, ni flag, ni projection, ni événement

**Fait** : dossier de base (GET, POST, PUT) et export FHIR repris dans `contexts/dossier-medical` (agrégat `DossierMedicalDeBase`, cas d'usage, adapters JDBC, ACL sur le langage publié `FichesDesPatients.identite`, contrôleurs `DossierMedicalDeBaseControleur` et `ExportFhirControleur`, advice) ; mêmes URL, rôles et table ; garde de patient et 400/403/404/409 ; couche d'erreur des statistiques médicales (403 avant 400) et des indicateurs Direction (403 avant 400 par le `@PreAuthorize` de classe) alignée ; code legacy mort retiré (voir ADR 0087) ; verrou d'architecture (port et adapters) ; RG-MED-049b à 049e, glossaire, `verifications-production.md` §52, `known-bugs.md` « Décisions DM8 », `seams.md` (FERMÉ, `seamAllowed` vide).

**Parité** : 38 scénarios du filet ; 26 inchangés, 12 écarts décidés (`*_nouveau_code`) : KB-MED-98 (dossier : `sans_garde_de_patient`, `ecritures_simultanees`), KB-MED-99 (`valeurs_et_bornes`, `refus`, `audit`), KB-MED-102 (FHIR : `patient_inconnu_ou_autre_centre`, `lignes_illisibles`), KB-MED-103 (stats : `periodes`, `csv`, `recherche`, `sans_donnees`), KB-MED-105 (Direction : `periodes`). Reproduits sans changement de golden : KB-MED-100, 101, 104.

**Preuve PostgreSQL 16 (cluster jetable)** : `backend/scripts/dossier-medical-dm8-pg16-rejeu.sh` : chaîne Flyway, 11 colonnes, unicité de `patient_id`, requêtes du §52 (dont les lignes illisibles de toutes les familles exportées), ordre FHIR des abords sans date (`a4,a1,a3,a2`), tests de contrat verts, code de sortie 0 (date et résultat du dernier rejeu : voir la fin de ce fichier).

**Reste à faire dans DM8** : rien hors conditions de mise en production. **Reliquat transféré à DM9** : voir le fichier de DM9.

**Conditions de mise en production (trois risques, arbitrage écrit AVANT la mise en production, au plus tard le 2026-12-15, ADR 0087 en tête)** : KB-MED-104 (DPO : fuite par différence des totaux Direction), KB-MED-101 (médecins et DPO : export FHIR tronqué en silence), KB-MED-102 (médecins : export FHIR qui omet en silence toute ligne illisible, toutes familles).

**Plan de bascule** : sans objet (refactoring sur place, ADR 0024/0072 : pas de palier ni de flag). Surveillance et rollback : `verifications-production.md` §52 (hausse attendue des 400 et 404, surveiller les 409 `DOSSIER_MEDICAL_ECRITURE_CONCURRENTE` et les WARN d'ignorance) ; rollback = revenir au commit précédent.

# DM8 — Dossier médical de base et export FHIR (reprise sur place)

Les consommateurs restants (Seance, Facturation, Planification, Pilotage) et le retrait de bc-medical relèvent de DM9. Données de santé : cloisonnement strict.
