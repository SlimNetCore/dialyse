---
status: accepted
date: 2026-10-15
decision-makers: [utilisateur]
context-scope: slice:comptabilite-c3-ecritures
research: []
---

# 0026 — Persister la date d'export C3 dans `updated_at`

## Contexte et problème

La décision métier C3 (2026-10-14, KB-CPT-25) demande d'exclure les écritures déjà exportées et de dater leur export.
La table `ecritures_comptables` ne possède pas de colonne dédiée à l'export, et la roadmap interdit une migration pour
les slices qui réutilisent le schéma. Une date doit toutefois rester disponible après redémarrage.

## Options considérées

1. Réutiliser `updated_at` au passage à `EXPORTEE` — aucune migration ; la ligne est ensuite immuable.
2. Ajouter `exportee_le` — nom explicite, mais migration additive et compatibilité des codepaths legacy requise.
3. Ne persister aucune date — contredit la décision métier.

## Décision

Proposition : écrire l'instant de transition dans `updated_at` et le projeter en `exporteeLe` pour les écritures
`EXPORTEE`. Ne pas reconstruire une date d'export historique pour les écritures déjà exportées avant C3.

## Conséquences

+ Respect de la demande sans migration ni changement de propriétaire des tables.

- `updated_at` perd sa sémantique générique sur cette transition, mais l'écriture étant immuable après export, il
  devient stable.
  ⚠ Les écritures déjà exportées n'ont pas de date d'export déductible ; ne pas présenter leur `updated_at` antérieur
  comme fiable.

## Révision

À confirmer lors de la revue des goldens C3 avec la comptabilité/importateur Sage ; si un audit exige un horodatage
explicite,
remplacer par une migration additive dédiée avant C3 `built`.

