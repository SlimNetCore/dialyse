# ADR-001 - Single database multi-centre

- Statut: Accepted
- Date: 2026-03-14

## Contexte
Le produit doit gerer plusieurs centres d hemodialyse dans une seule base PostgreSQL.
La priorite est de simplifier exploitation/deploiement tout en garantissant l isolation des donnees.

## Decision
Adopter un modele single database multi-centre avec isolation logique obligatoire par `center_id`.

## Consequences
### Positives
- Socle simple a operer (une base).
- Mutualisation des ressources et maintenance reduite.
- Reporting transverse possible avec controles stricts.

### Negatives
- Risque de fuite cross-centre si oubli de filtre.
- Besoin de discipline forte en code, tests et revues.

## Garde-fous
- `center_id` obligatoire sur tables metier sensibles.
- Verification center access dans chaque use case.
- Tests automatiques d isolation.
- Audit des refus cross-centre.
- Option RLS PostgreSQL pour defense en profondeur.

