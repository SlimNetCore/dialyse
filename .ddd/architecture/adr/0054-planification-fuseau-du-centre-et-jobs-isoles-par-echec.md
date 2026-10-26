---
status: proposed
date: 2026-10-26
decision-makers: [utilisateur (décisions 2, 3 et 5 du 2026-10-26), architecte]
context-scope: planification (slices P4, P7, P8)
research: [.ddd/research/spring-scheduling-fuseau.md]
related: ADR 0040 (détection de nuit des Absences), 0049, 0052 ; migration V46 ; B7/B9/B13, H9
---
# 0054 — Juger « aujourd'hui » au fuseau du centre et porter les crons comme jobs du contexte, isolés par échec

## Contexte et problème
Libération (`Clock.systemUTC`), alerte générateur (`ZoneOffset.UTC`) et crons sont en UTC (B9) : entre 00:00 et 01:00
à Alger, « aujourd'hui » est la veille. Un patient en erreur annule la libération de tout le centre (B7). L'ordre de nuit
(02:30 détection des absences → 04:00 libération) est implicite (H9).

## Options considérées
1. Garder UTC — contre : décalage d'un jour sur la règle J-1 (règle d'argent, tactique §11).
2. **Fuseau du centre (V46, défaut `Africa/Algiers`) via le port `FuseauxDesCentres`, comme les Absences** — pour :
   cohérence entre contextes, déjà en production.

## Décision
Option 2. Jobs dans `interfaces/jobs` du contexte : `LiberationDesPlacesEchues` `@Scheduled(cron = "0 0 4 * * *",
zone = "${app.planification.fuseau-par-defaut:Africa/Algiers}")`, `ControleDeLaPresence` `0 15 7 * * *`, puis
`ReplanificationNocturne` `0 30 2 * * *` (P8). Le cas d'usage calcule « aujourd'hui » **par centre** à son fuseau ;
`Horloge` reste en UTC (instants). Isolation : un centre en erreur n'arrête pas les autres, une **transaction par
patient** pour la libération (B7) ; incidents journalisés, jamais propagés au planificateur. Ordre 02:30 → 04:00 conservé
(décision utilisateur) et écrit dans les RG. Un seul serveur : pas de verrou distribué de job.

## Conséquences
+ B7, B9 corrigés ; même modèle que `ControleDeNuitDesAbsences`.
− Écarts décidés à tracer en goldens `*_nouveau_code` (libération 00:30 Alger).
⚠ Pool de planification à 1 thread : jobs en série ; un job long (replanification) retarde les suivants — surveiller
  la durée (30 s/phase × 3 × centres).

## Révision
Déploiement multi-instances (verrou de job à décider) ou changement de l'ordre de nuit.
