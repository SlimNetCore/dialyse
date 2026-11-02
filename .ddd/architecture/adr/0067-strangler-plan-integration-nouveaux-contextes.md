---
status: partially superseded by ADR 0072 for dossier-medical, and to be reviewed for seance, patient-couverture and pilotage-direction (2026-11-02 : vérifications indépendantes : modules non branchés, nouvelles tables et URLs contraires à ADR 0024)
date: 2026-10-29
deciders: utilisateur
---

# ADR 0067 — Intégration au plan Strangler Fig des contextes nouvellement architecturés

## Contexte

Cinq contextes sont désormais architecturés (ADR 0062–0066) : `seance`, `organisation`, `patient-couverture`,
`dossier-medical`, `pilotage-direction`. Le plan Strangler Fig (roadmap) devait les intégrer comme slices candidats,
sans toucher aux slices déjà verified/built.

## Décision

1. Les nouveaux contextes sont ajoutés comme sections dédiées au roadmap (§1 septies à §1 undecies), avec slices
   candidats marqués `planned` dans `.ddd/state.json`.
2. Ordre d'extraction recommandé : `organisation` (fondation multi-centre, OHS) → `patient-couverture` (PEC requis par
   Seance) → `seance` → `dossier-medical` → `pilotage-direction` (consommateur final, aucune écriture).
3. Chaque slice suit la recette §3 du roadmap (filet de caractérisation, domaine pur TDD, adaptateurs sur tables
   existantes, retrait legacy, documentation règles de gestion, state `built`, `/ddd-verify`).
4. Multi-centre strict (`centerId`), hexagonal, pas de régression : bugs legacy reproduits (ADR 0019) sauf écart décidé.
5. Les dépendances inter-contextes passent uniquement par les langages publiés (ADR 0010, 0024) ; pas de SQL
   cross-context.
6. `FrontieresDesContextesTest` gagne une règle par contexte (vide tant que le module est squelette).

## Conséquences

- 29 slices candidats ajoutés (`planned`), à détailler par `/ddd-strangle-plan` puis `/ddd-slice`.
- Aucune slice existante modifiée (sauf cohérence roadmap).