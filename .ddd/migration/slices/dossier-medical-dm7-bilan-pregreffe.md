---
slice: dossier-medical-dm7-bilan-pregreffe
status: built
date: 2026-11-07
order: 7
context: dossier-medical
sources: [.ddd/tactical/dossier-medical.md, ADR 0007, 0065]
---

> **REMPLACÉ le 2026-11-07 (revue DM8)** : ce fichier est l'ancienne fiche de planification. La slice livrée est `dossier-medical-dm7-pre-greffe` (état `built`, ADR 0086, `.ddd/migration/slices/dossier-medical-dm7-pre-greffe.md`) ; la clé d'état `dossier-medical-dm7-bilan-pregreffe` est alignée sur `built` pour ne pas laisser croire que DM7 reste à faire. Les bandeaux ci-dessous sont historiques.

> Statut vrai au 2026-11-02 : `planned` (module gelé, `backend/contexts/dossier-medical/GEL.md`, ADR 0072). Tables parallèles `bilans_pregreffe` / `etapes_bilan_pregreffe` face aux tables réelles `bilans_pre_greffe` / `etapes_bilan_pre_greffe` ; sans transaction ni filet. Reprise : sur place.

> **Découpage réel (2026-11-06, ADR 0080)** : `planned`, aucun filet DM7. Périmètre élargi (trou d'inventaire ACCEPTÉ) : DM7 porte aussi les **donneurs vivants** (`greffe/donneurs`, table `donneurs_vivants`), les décisions RCP, `greffe/kdigo` et `greffe/export-pdf`. Tables réelles : `bilans_pre_greffe`, `etapes_bilan_pre_greffe`, `donneurs_vivants`. KDIGO ne lit pas les constantes.

# DM7 — Bilan pré-greffe (isolé, extractible)

Agrégats `BilanPreGreffe`, `EtapeBilanPreGreffe`. Isolé et extractible (ADR 0007). Événement `BilanPreGreffeComplete`.

## Cas d'usage

`DemarrerBilan`, `ValiderEtape`, `CompleterBilan`.

## Entrées legacy

`bc-medical` (bilan greffe).

## Données

Tables bilan ; aucune migration. Port publié pour Patient & couverture (greffe).