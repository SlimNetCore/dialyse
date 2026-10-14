---
status: approved
date: 2026-10-09
sources:
  - .ddd/discovery/domain-vision.md
  - .ddd/discovery/event-storming.md (timeline, hotspots, réponses utilisateur du 2026-10-09)
  - .ddd/discovery/glossary.md
  - .ddd/discovery/legacy-analysis.md
validation: classification proposée par l'architecte ; à valider au gate `strategic`
---

# Sous-domaines (problem space)

## Classification

| # | Sous-domaine | Type | Complexité | Volatilité | Valeur métier | Risque | Investissement | Justification (signaux) |
|---|---|---|---|---|---|---|---|---|
| SD1 | Réalisation de la séance de dialyse | **Core** | haute | haute | très haute | haut | DDD tactique complet, meilleurs développeurs, interne | Unité de valeur (vision l.15-18) ; co-changement maximal (`bc-seance ↔ platform` 982) ; cycle `CREE → VALIDEE → SIGNEE → FACTUREE` + régularisation + dérogation (ES #21-29) |
| SD2 | Planification des places et de la présence infirmière | **Core** | haute | haute | haute | moyen | Modèle riche + solveur derrière un port (Timefold) | Placement 0-100, optimisation, replanification nocturne, remplaçants (ES #11-20) ; cluster C1 cohésion 0.92 ; investissement algorithmique dédié |
| SD3 | Suivi et valorisation des absences de patients | **Core** (à confirmer, sinon supporting) | moyenne | moyenne | haute (« ne perdre aucune séance ») | haut (argent perdu) | Modèle riche, cycle de vie explicite, clôture mensuelle à créer | Détection nocturne, qualification, rattrapage, valorisation TTC → HT (ES #30-35) ; règles RG-ABS nombreuses ; clôture mensuelle demandée (réponse Q5) |
| SD4 | Admission du patient et couverture (assuré, PEC, attestation) | Supporting | moyenne | basse | haute (conditionne la facturation) | moyen | Modèle léger + règles, interne | Règles spécifiques locales (PEC à forfait, attestations) mais non différenciantes ; 4 micro-modules (`bc-patient/assure/insurance/pec`) |
| SD5 | Facturation et règlements | Supporting | moyenne | moyenne | très haute (trésorerie) | **haut** (non testé, argent) | Modèle riche ciblé sur l'émission et les clôtures | Spécifique (caisses, agences, centres payeurs, forfait PEC) ; 3 tests pour 33 fichiers ; clôture mensuelle à créer (Q5) |
| SD6 | Comptabilité générale (SCF) | Supporting | moyenne | basse | haute | haut (non testé) | Modèle de règles de génération + export ; pourrait être remplacé par un progiciel en aval | Plan SCF, journaux, périodes ; consommateur d'événements ; logiciel comptable externe reçoit l'export |
| SD7 | Stock pharmacie et catalogue d'articles | Supporting | moyenne | basse | moyenne | moyen | Modèle de règles (FEFO, PMP, inventaire) | Règles standard de gestion de stock ; traçabilité des lots requise |
| SD8 | Dossier médical et parcours greffe | Supporting (candidat core pour le parcours greffe) | haute | moyenne | haute (clinique) | moyen (47 fichiers non testés) | Modèle par agrégat (ordonnance, examen, bilan pré-greffe…) | ≥ 10 sous-ensembles cohérents ; peu d'interactions avec la facturation ; parcours greffe possiblement différenciant |
| SD9 | Maintenance des équipements (GMAO) | Supporting (proche du générique) | moyenne | basse | moyenne | bas | Modèle léger ; option « acheter » à réévaluer | Isolé (Ca 2 / Ce 1), cohésion 0.8 ; problème couvert par le marché, mais lien générateur ↔ planning spécifique |
| SD10 | Organisation (société, centres, référentiel salles/créneaux) | Supporting | basse | basse | moyenne | moyen (hub `CenterId` 321 importeurs) | CRUD + règles de transfert de centre | Dimension transverse d'isolement par centre (RG-TRV-002) |
| SD11 | Pilotage de direction (tableaux de bord, instantanés, alertes) | Supporting | moyenne | moyenne | haute (client société) | bas | Modèle de lecture (projections), pas de règles d'écriture | Agrège stock/absences/capacité ; instantané mensuel figé (RG-DIR-090) |
| SD12 | Identité et accès (comptes utilisateurs, MFA) | Generic | basse | basse | — | moyen (sécurité) | Adopter (Spring Security / IdP) derrière un port | Problème résolu par le marché |
| SD13 | Notifications | Generic | basse | basse | — | bas | Composant technique abonné aux événements | Catalogue RG-NOT, purge |
| SD14 | Journal d'audit | Generic | basse | basse | conformité | moyen | Composant transverse asynchrone | Traçabilité des écritures/dossier médical |
| SD15 | Licence logicielle | Generic | basse | basse | commerciale | bas | Client du serveur de licences | Contrôle nocturne 03:15 |
| — | Import / reprise de données (`bc-migration`) | Hors domaine (outillage) | — | — | — | — | Outil ponctuel, hors contextes | Hypothèse Q12 legacy-analysis |

## Core Domain Chart (complexité × différenciation)

| | Différenciation basse | Différenciation moyenne | Différenciation haute |
|---|---|---|---|
| **Complexité haute** | — | SD8 Dossier médical | **SD1 Séance**, **SD2 Planification** |
| **Complexité moyenne** | SD9 GMAO, SD7 Stock | SD5 Facturation, SD6 Comptabilité, SD11 Direction | **SD3 Absences** |
| **Complexité basse** | SD12-15 génériques | SD4 Patient & couverture, SD10 Organisation | — |

## Investissement recommandé
- Core (SD1, SD2, SD3) : DDD tactique complet, tests de caractérisation prioritaires, aucun prestataire.
- Supporting à risque financier (SD5, SD6) : priorité au filet de sécurité (non testés) avant toute extraction.
- Generic : adapter derrière un port, ne pas modéliser.

## Questions ouvertes
1. Les absences de patients (SD3) sont-elles un différenciateur reconnu par la direction (core) ou une obligation de gestion (supporting) ? Hypothèse : core, car la perte de séances est le principal levier financier.
2. Le parcours de préparation à la greffe (SD8) est-il un argument commercial ? Hypothèse : non, supporting.
3. La GMAO pourrait-elle être remplacée par un logiciel du marché ? Hypothèse : non à court terme (lien générateur/planning).
