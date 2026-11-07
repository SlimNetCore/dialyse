---
slice: dossier-medical-dm2-prescriptions
status: planned
date: 2026-10-29
order: 2
context: dossier-medical
sources: [.ddd/tactical/dossier-medical.md, ADR 0065]
---

> Statut vrai au 2026-11-02 : `planned` (module gelé, `backend/contexts/dossier-medical/GEL.md`, ADR 0072). Aucun filet (ordonnances et prescriptions legacy non caractérisées), `OrdonnanceRestController` et `ordonnance.jrxml` non modélisés. Reprise : filet d'abord, puis refactoring sur place.

> **Découpage réel (2026-11-06, `.ddd/safety-net/dossier-medical-inventaire.md`, ADR 0080)** : statut `planned`, aucun filet DM2. Périmètre élargi (trou d'inventaire ACCEPTÉ) : en plus des ordonnances et prescriptions, DM2 porte l'**anémie et l'observance** (`suivi-anemie`, `observance-anemie`, `alertes-observance` + `resoudre`, `administrations-anemie` GET/POST, tables `administrations_anemie`, `alertes_observance`) et le **planificateur `ObservancePrescriptionScheduler` (cron 06:30). Tables réelles : `ordonnances`, `lignes_ordonnance`, `prescriptions_medicales`. Le module `contexts/dossier-medical` ne porte aujourd'hui que DM1 ; le contenu gelé d'avant est dans l'historique git (542c7571).

> **État au 2026-11-07 (ADR 0081) : `built`** (`./mvnw -o test -fae` complet vert : 2700 tests, 0 échec, 12 ignorés ; `check-arch.ts` 0 violation ; `seamAllowed` vide ; rejeu PostgreSQL 16 vert). Détail ci-dessous. **Fait** : (1) ordonnances (domaine `Ordonnance`, 5 cas d'usage, JDBC, `OrdonnancesControleur`) ; (2) prescriptions (`PrescriptionMedicale`, `ContenuDePrescription`, 4 cas d'usage, JDBC, ACL Stock `CatalogueDesArticlesDuStock`, port publié `SignalementDesPrescriptions` + ACL plateforme, `PrescriptionsControleur`) ; (3) impression : garde MEDECIN/ADMIN + centre dans `DocumentRestController.print` (KB-MED-18). Preuves : module (domaine, application avec fakes, contrat fake/H2) vert ; contrat PostgreSQL 16 jetable rejoué vert (`dossier-medical-dm2-pg16-rejeu.sh`) ; filet DM2 : ordonnances 15/15 (7 goldens `_nouveau_code`), prescriptions 15/15 (12), impression 4/4 (1), le reste inchangé ; legacy archivé dans `dm2/_ecarts-legacy`. Décisions : KB-MED-17 (404), 18 (garde), 19 (404), 22 (400) corrigés ; 16 (ordre déterministe, pas de rang) ; **20 REPRODUIT (trou de séquence conservé, à confirmer)**. `verifications-production.md` §46 écrit. (4) anémie / observance (`AnemieControleur` : `suivi-anemie`, `observance-anemie`, alertes + résoudre, administrations ; `AdministrationDeTraitement`, `AlerteDObservance`, `MesureDObservance`, `PeriodeDObservance`, `EvaluationDeCible` (cibles KDIGO COPIÉES, retrait avec le legacy) ; ACL Stock `SortiesDeStockDuStock`, port publié `NotificationsDeLAnemie` + ACL plateforme ; KB-MED-26 corrigé, 19 et 22 corrigés, 27 à 29 reproduits) et (5) planificateur 06:30 déplacé (`ControleDeNuitDeLObservance` + `ControlerLObservance`, même cron, `horlogeDeLObservance` = jour de la JVM reproduit). Écarts : 9 goldens `_nouveau_code` pour l'anémie (suivi 3, administrations 5, alertes 1), planificateur 12/12 inchangé. Documentation fonctionnelle et maquette régénérées. **Reste ouvert (questions, pas de code)** : confirmations métiers/DPO listées dans l'ADR 0081 ; 500 → 405 des routes inexistantes (gestionnaire global) ; contrats PostgreSQL des ports `Administrations` et `Alertes` non écrits (couverts par le filet H2 et par la vérification des colonnes du script de rejeu). Pas de façade ni de flag (refactoring sur place, ADR 0024).

# DM2 — Prescriptions (ordonnances)

Agrégat `Prescription`. Transition de statut d'ordonnance. Événement `PrescriptionSignee`.

## Cas d'usage

`CreerPrescription`, `SignerPrescription`, `ConsulterPrescriptions`.

## Entrées legacy

`bc-medical` (ordonnance).

## Données

Tables prescriptions ; aucune migration.