---
status: accepted
date: 2026-11-12
decision-makers: [architecte]
context-scope: facturation, patient-couverture, platform
related: ADR 0024, 0127 (RF3b), 0128 (RF3c-J1), 0147 (PL8) ; `.ddd/migration/reviews/pl8b-realisation.md` ; `.ddd/safety-net/known-bugs.md` KB-FAC-65, KB-RF3-07
---

# 0149 - PL8b : la Facturation lit la chaîne des payeurs publiée (solde de RF3c-J1)

Refactoring sur place (ADR 0024) : mêmes tables, URL, rôles, codes et JSON. Retour arrière = revert du commit.

## Décisions
1. `LectureDuTableauDeBordJdbc`, `SituationsDesFacturesJdbc` et `SyntheseFinanciereDesCentresJdbc` ne joignent plus `agence` / `caisse_assurance` / `centre_payeur` : elles lisent les identités figées sur la facture (`centre_payeur_id_snapshot`, `agence_id_snapshot`, ou le payeur courant du patient) et résolvent la chaîne en mémoire par `PayeursDuCentre`.
2. Situations des factures : chaîne bornée au centre (annuaire inchangé) ; le tri `date_facturation DESC, numero_facture DESC` reste fait par la base ; filtres caisse / agence / centre payeur, total et page (LIMIT / OFFSET) sont appliqués en mémoire APRÈS ce tri (mêmes résultats, volume = les factures de la période d'un centre, déjà lues en entier par `toutes`).
3. KB-FAC-65 / KB-RF3-07 (caisse non bornée au centre : tableau de bord et CA par caisse) REPRODUITS, sans décision utilisateur : extension minimale `PayeursDuCentre.caissesDesAgences(agences)`, seule lecture publiée non bornée au centre, documentée comme telle ; à supprimer le jour où la direction tranche. Le CA par caisse garde l'agence bornée au centre de la facture (KB-SEA-84).
4. Tri des caisses du tableau de bord : par ordre des caractères en Java (précédent PL8), stable, au lieu de l'ordre de collation de la base. Identique sous H2 ; sous PostgreSQL 16 la collation peut ordonner autrement les codes mêlant casse ou accents (contrat PG16 écrit, non exécuté ici).
5. `TableDesPayeursTest` : tableau d'exceptions, raisons datées et butoir 2027-03-31 supprimés. Règle permanente : aucun code de production hors patient-couverture (Java et gabarits `.jrxml`) ne nomme `caisse_assurance`, `agence`, `centre_payeur`.

## Conséquences
- Plus de jointure vers l'annuaire en dehors de patient-couverture ; RF3c-J1 soldé.
- Tests : contrat JDBC des situations (chaîne, filtres, pagination, chaîne rompue hors centre), tableau de bord (caisse d'un autre centre, tri), contrat de l'annuaire (`caissesDesAgences`) sur le double en mémoire et sur les tables.
