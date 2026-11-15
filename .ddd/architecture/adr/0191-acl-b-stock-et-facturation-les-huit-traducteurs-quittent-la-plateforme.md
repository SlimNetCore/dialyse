---
status: accepted (livré le 2026-11-15, slice ACL (b), décision D8)
date: 2026-11-15
decision-makers: [utilisateur, architecte]
context-scope: platform (infrastructure/acl/stock, infrastructure/acl/facturation), organisation, seance, stock
related: ADR 0008, 0024, 0096, 0097, 0108, 0185, 0190 ; `acl-b-realisation.md` ; `inventaire-platform-restant-v2.md` ligne 20
---

# 0191 - ACL (b) Stock et Facturation : les huit traducteurs quittent la plateforme

## Contexte
Mesure des poms : `organisation -> stock` ; `seance -> stock, organisation, absences-patients -> facturation` ; `facturation -> comptabilite, documents-api, patient-couverture, shared-kernel` (aucun chemin vers `seance`, `organisation` ni `stock`). Aucun des huit traducteurs ne fermait donc de cycle Maven réel s'il était hébergé par le consommateur de la direction de dépendance existante (fournisseur d'un port « fourni », ADR 0108, 0185, 0190).

## Décision
| Traducteur | Cible | Cycle ? |
|---|---|---|
| `CentresDuStockParOrganisation` (+ test) | `organisation/.../infrastructure/acl/stock` | non : `organisation -> stock` ; implémente `stock.publishedlanguage.CentresFournis` |
| `VerrouDuCentreDuStockParOrganisation` | idem | non ; `VerrouDuCentreFourni` |
| `LiaisonDesSeancesParLaSeance`, `SeancesDeFacturationParLaSeance`, `SeancesDesFacturesParLaSeance`, `SeancesFacturablesParLaSeance` | `seance/.../infrastructure/acl/facturation` | non : `seance -> absences-patients -> facturation` (dépendance directe `contexts-facturation` déclarée dans le pom de la Séance, aucun cycle : la Facturation ne dépend pas de la Séance) |
| `StatutDesSeancesParLaSeance`, `PerimetreDesSeancesParLaSeance` (+ test) | `seance/.../infrastructure/acl/stock` | non : `seance -> stock` |

Les deux ports du Stock (`StatutDesSeances`, `PerimetreDesSeances`) sont des ports de DOMAINE ; la règle ArchUnit `la_seance_n_utilise_le_stock_que_par_son_langage_publie` interdit à la Séance d'y toucher. Sur le modèle de `CentresFournis`, le Stock publie donc deux ports fournis (`StatutDesSeancesFourni`, `PerimetreDesSeancesFourni`, types primitifs/JDK) et les adapte à son domaine par deux classes minces (`stock/.../infrastructure/acl/seance/StatutDesSeancesFournies`, `PerimetreDesSeancesFournis`). La règle ArchUnit n'est pas assouplie. La logique (lecture de la demi-journée, KB-SEA-68/69) reste dans le traducteur de la Séance, test table-driven inchangé en substance.

Câblage : `@Bean` dans `OrganisationConfiguration`, `SeanceConfiguration`, `StockContexteConfiguration` ; plus de `@Component`. Aucun changement de comportement.

## Gardes datées
Aucune entrée F-R1..F-R7, M-R1 ni butoir 2027-03-31 n'était rattachée à ces fichiers (aucun SQL, aucune liste `TableDe*Test` ne les nomme) : rien à supprimer, aucune date reportée ni créée. La garde `TableDesSeancesTest` (4 traducteurs de la Facturation sans SQL sur les séances) est repointée sur le nouveau dossier.

## Cycles réels conservés
Aucun dans ce périmètre.

## Conséquences
`platform/.../infrastructure/acl/stock` et `.../facturation` n'existent plus. ACL de production en plateforme : 27 -> 19 ; sous-dossiers 9 -> 7. Reste (slice (c)) : organisation 4, patientcouverture 4, absences 2, gmao 2, dossiermedical 1, reprise 1, seance 1.
