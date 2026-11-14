---
status: realise (verrou seul : le perimetre etait deja livre ; comportement inchange)
date: 2026-11-14
sources: ADR 0173, 0099, 0134, 0169-0172, `inventaire-platform-restant-v2.md` ligne 11, `rp3a-realisation.md`, `rp3b-realisation.md`
---

# RP3c - realisation

| Etape | Resultat |
|---|---|
| Inventaire (Grep) | Aucune requete sur une table d'un autre contexte dans `infrastructure/migration/**`, `persistence/adapter/migration/**`, `MigrationRestController`, `MigrationDtos`, `MigrationApplicationService`. Restent seulement les 4 tables `migration_*` (3 adapters) |
| Seance historique | deja par `SeancesDeLaReprisePubliees` (SE7e, ADR 0099), golden SE7e existant |
| Soldes d'ouverture | proprietaire Facturation ; `OpeningBalanceJdbcAdapter` = traducteur vers `RepriseDesSoldes` (F6) ; golden `soldes_ouverture_controles_et_remplacement` existant |
| Port nouveau | aucun (doublon) |
| Livre | test `LaRepriseDeDonneesNeTouchePlusLesTablesDesContextesTest` (2 tests) : verrou permanent sans liste blanche ni butoir |
| Butoirs 2027-06-30 | aucune entree de la reprise ne subsistait ; ceux de `TableDesSeancesTest` (schema, seed, renadial, cahier jrxml) relevent de S-DDL2/3, SEED2..n, DOC5, non touches, non reportes |
| Parite | aucun golden touche : aucun `_nouveau_code`, aucune archive, aucune KB corrigee |

Fichiers de production modifies : aucun. `covered.json`/guard-legacy : sans objet. `seamAllowed` = []. `.ddd/state.json` non touche.
Filet complementaire non ecrit : l'import des seances n'a pas de golden « par nature » au niveau RP3 (formats limites, doublons, rejeu, autre centre, roles) ; seul le golden SE7e le couvre. A ouvrir si le metier veut le meme niveau que RP3a/RP3b.
Verification : `LaRepriseDeDonnees...`, `FrontieresDesContextesTest`, `TableDesSeancesTest` verts (108 tests) ; trois passes et build complet : voir message de livraison.
