---
status: done (rejoue sur le nouveau code le 2026-10-19)
date: 2026-10-19
slice: facturation-f1-referentiel
---

# Filet de caractérisation — F1 Référentiel de facturation

**Où** : `backend/platform/src/test/java/com/hemodialyse/backend/characterization/facturation/referentiel/`
(`TypesTvaCharacterizationIntegrationTest`, `ParametresFacturationCharacterizationIntegrationTest`, harnais
`ReferentielFacturationCharacterizationSupport`) ; goldens
`src/test/resources/characterization/facturation/referentiel/`.

**Point d'observation** : HTTP (MockMvc, contexte Spring complet, sécurité réelle + `RoleScopeFilter`, H2) et état SQL
de
`tva_types` et `facturation_settings`. Aucun mock du code caractérisé.

**Résultat sur le legacy (2026-10-19)** : 24 scénarios, 76 goldens, verts, stables au rejeu.

**Non-déterminisme maîtrisé** : identifiants générés et horodatages normalisés ; clés JSON triées ; **caches vidés avant
chaque scénario** (le cache `tva-types-list` survit d'un scénario à l'autre et n'est pas évincé par les insertions SQL
des
fixtures : sans cela, une liste lue plus tôt était resservie).

**Couverture** : lecture (vide, tri, pagination, autre centre, sans centre) ; création (nominale, complète, bornes du
taux,
décimales, dates, chevauchement, champs obligatoires, corps absent, autre centre) ; modification (réactivation,
identifiant
inconnu, autre centre) ; désactivation (idempotence, inconnu, autre centre) ; autorisations par profil ; table partagée
avec
`/comptabilite/regles-tva` ; paramètres (valeurs par défaut écrites par la lecture, modification, modèle, champs,
centres,
autorisations).

**Constats** : `.ddd/safety-net/known-bugs.md` § « Contexte Facturation — slice F1 », KB-FAC-01 à 12.

**Non couvert ici** : rejeu HTTP sur PostgreSQL 16 de `/facturation/settings` (KB-FAC-07, syntaxe `MERGE … KEY` refusée
par
`EXPLAIN` sur la base locale) ; à faire avec le cluster jetable (`backend/scripts/comptabilite-pg16-rejeu.sh` comme
modèle).

**Rejeu sur le nouveau code (2026-10-19)** : vert ; ecarts decides en goldens `_nouveau_code` (voir `known-bugs.md`,
Livraison F1).
