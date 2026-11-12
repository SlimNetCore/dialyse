---
status: built (build complet en cours de validation au moment de l'ecriture)
date: 2026-11-12
sources: pl10a-filet.md, pl9-realisation.md, ADR 0148, ADR 0150, known-bugs.md (KB-PL10a-1..5)
---

# PL10a - realisation

## Fait
Pilotage (`backend/contexts/pilotage-direction`) : `InstantanesDeLaDirectionControleur` (3 routes, `produces` JSON => 406, `SessionDeLaDirection`) ; `MasqueDesTotauxFiges` (effectifs de centre 1 a 4 masques, KB-5) ; `InstantanesMensuelsPublies` (422 contenu vide/non objet/non fini, KB-2) ; `ListerLesInstantanes` + `MoisDInstantane.estLisible` (KB-4) ; port `InstantanesMensuels.enregistrer(instantane, genereLe)` + JDBC (horloge du Pilotage) ; tests `InstantanesMensuelsPubliesTest` (+11 cas), fake `InstantanesEnMemoire`.
Platform : `DirectionSnapshotRestController` supprime (git rm) ; `DatesIllisiblesDeLaDirectionAdvice` (404/406 etendus a `/snapshots`) ; `AuditActionResolver` (DIRECTION_INSTANTANE_CONSULTATION) ; `FrontieresDesContextesTest` (+2 regles, retour du controleur interdit) ; `covered.json`.
Goldens (relus un par un depuis .received, jamais approve=true) : PL10a x6 en `*_nouveau_code.approved.json` (anciens dans `_ecarts-legacy/*_avant_pl10a.legacy.json`), PL9 `horodatages_et_fuseau` (heure d'Alger de l'horloge du Pilotage) ; `seuil_d_anonymat_a_la_capture` inchange. PD2 : rendu de `generated_at` avec secondes (golden inchange).
Docs : ADR 0150, KB-PL10a-* CORRIGE, RG-DIR-115, annexe, covered.json. Glossaire inchange (aucun terme nouveau).

## Points a savoir
- `DirectionAccessGuard` reste en platform (Dashboard + WebSocket encore clients).
- Tableaux de butoir (TableDuPilotageTest, garde 2026-12-15) : aucune entree devenue inutile, aucune date repoussee.
- Question ouverte : mois invalide du chemin = 422 MOIS_INVALIDE conserve (la decision disait 400) ; pas de CHECK en base.
- Pas de commit.
