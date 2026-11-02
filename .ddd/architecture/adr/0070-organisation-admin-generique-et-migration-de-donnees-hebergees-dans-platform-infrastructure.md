---
status: proposed
date: 2026-11-03
decision-makers: [utilisateur (à valider), architecte]
context-scope: organisation
related: ADR 0024, 0063 ; `.ddd/migration/slices/organisation-o5-retrait-bc-referential.md` ; `.ddd/verify/verify-organisation.md` (constat « ADR manquant »)
---

# 0070 - Organisation : l'administration générique des référentiels et la migration de données sont hébergées dans `platform/infrastructure`

## Contexte

Le retrait de `bc-referential` (commit b365a63a) a supprimé le domaine `organisation` et `referential` de ce module, mais le code de
`referential/admin` (CRUD générique, import CSV/Excel, modèles d'import) et de `domain/migration` (migration de données) a été
relocalisé tel quel dans `platform/src/main/java/com/hemodialyse/backend/infrastructure/{referential/admin,migration}`. Aucun ADR ne
l'actait. L'ancien module `bc-migration`, en amont de `platform`, consommait `referential/admin` : le déplacer dans `platform` cassait la
compilation, d'où la relocalisation conjointe.

Ces routes restent servies sous leurs URL historiques : `/api/v1/admin/referentials/**` (ADMIN de son centre ou SUPERADMIN, garde de
centre) et `/api/v1/admin/migration/**`. Elles écrivent directement dans les tables `salle`, `position_creneau`, `medecin` et les
référentiels de facturation (forfait, caisse, agence, centre payeur, transporteur), qui sont (pour les trois premières) des tables du
contexte Organisation.

## Options

1. **Rester dans `platform/infrastructure`** comme outillage technique d'administration (décision actuelle de fait).
2. Porter l'administration des salles, créneaux et médecins dans le contexte Organisation (use cases dédiés), laisser le reste (forfaits,
   caisses...) dans leurs contextes propriétaires ; l'import CSV reste un adapter.
3. Retirer l'admin générique et la migration avec le dernier consommateur legacy.

## Décision proposée

Option 1 à court terme, avec une dette explicite : l'admin générique contourne les invariants d'Organisation (écrit en SQL dans ses tables
sans passer par les use cases) et la désactivation logique des médecins (migration V52) n'y est pas appliquée (la suppression d'un médecin y
reste physique, avec contrôle d'usage). Chemin de sortie : option 2, par type de référentiel, quand O4 (lectures publiées) sera faite.

Garde-fous en attendant : le filet rétroactif caractérise ces routes
(`AdministrationDesReferentielsCharacterizationIntegrationTest`, matrice de droits et gardes de centre) ; `TableDeLOrganisationTest` liste
les lecteurs SQL directs.

## Conséquences

- Le contexte Organisation n'est pas le seul écrivain de ses tables tant que l'option 2 n'est pas faite (violation connue de « un seul
  propriétaire en écriture »).
- À valider par l'utilisateur ; non appliqué au-delà de l'existant.

## Questions ouvertes

- Faut-il aligner la suppression d'un médecin de l'admin générique sur la désactivation logique (V52) ?
- L'outillage de migration de données doit-il être retiré ?
