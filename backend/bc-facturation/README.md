# bc-facturation

Bounded context `facturation` (domaine pur) pour la plateforme Hemodialyse.

## Rôles

- calcul d'aperçu de facturation (en mémoire, sans persistance)
- validation atomique (création factures + passage des séances en `FACTUREE`)
- gestion des paramètres (TVA, code, regroupement multi-forfait)
- agrégations dashboard facturation

## Contrats d'entrée

- `FacturationUseCase`

## Ports sortants

- `SeanceFacturationPort`
- `FactureRepositoryPort`
- `FacturationSettingsRepositoryPort`

## Notes

Le module ne dépend ni de Spring, ni de JPA, conformément à l'architecture hexagonale.

