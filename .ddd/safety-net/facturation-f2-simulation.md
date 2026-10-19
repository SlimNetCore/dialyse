---
status: done
date: 2026-10-19
slice: facturation-f2-simulation
---

# Filet de caractérisation — F2 Simulation de facturation

**Où** : `backend/platform/src/test/java/com/hemodialyse/backend/characterization/facturation/simulation/`
(`ApercuCharacterizationIntegrationTest`, `ExclusionEtForfaitCharacterizationIntegrationTest`, harnais
`SimulationCharacterizationSupport`) ; goldens `src/test/resources/characterization/facturation/simulation/`.

**Point d'observation** : HTTP (MockMvc, contexte Spring complet, sécurité réelle + `RoleScopeFilter`, H2) et état SQL
des séances (statut, facture, colonnes `forfait_override_*`). Aucun mock du code caractérisé.

**Non-déterminisme maîtrisé** : la simulation date la facturation du jour ; le centre A a donc un modèle sans année
(`SIM-{SEQ}`), une séquence posée pour l'année en cours (41) et un type de TVA ouvert depuis 2000 (9 %) ; identifiants
connus remplacés par des alias (`<P_EFFECTIF>`, `<FORFAIT_HD>`, `<S_nom>`), autres UUID et horodatages normalisés.

**Couverture** : éligibilité (statuts, casse, facturée, bornes, autre centre) ; forfait appliqué (surcharge, PEC
effective, demandée, échue, absente) ; regroupement vrai/faux ; lignes, totaux, TVA (avec et sans type actif) ;
numérotation prévisionnelle ; période (mois, dates, mois prioritaire, refus) ; exclusion (nominale, deuxième fois,
refus, sans période, autre centre) ; changement de forfait (nominal, refus) ; autorisations par profil.

**Constats** : `.ddd/safety-net/known-bugs.md` § « slice F2 », KB-FAC-13 à 21.

**Non couvert ici** : la validation (`/validate`, slice F3) ; le rejeu PostgreSQL 16 (la simulation ne fait que des
lectures et deux `UPDATE` standard).
