---
status: accepted (CLOT1, action m6/M4)
date: 2026-11-15
decision-makers: [utilisateur (directive CLOT1), architecte]
context-scope: organisation, stock, gmao
related: ADR 0192 ; revue globale 2026-11-15 m6 ; `.ddd/strategic/context-map.md`
---

# 0206 - Organisation dépend (pom) du langage publié de Stock et de GMAO

## Constat
`contexts/organisation/pom.xml` (l.82-94) déclare `contexts-stock` et `contexts-gmao`, alors qu'Organisation est un référentiel amont. Imports réels, tous dans `publishedlanguage` : `gmao.GenerateursDuCentre`, `gmao.EquipementsDeLaSalle`, `stock.CatalogueDArticles`, `stock.ArticleDuCatalogue`, `stock.VerrouDuCentreFourni`, `stock.CentresFournis` (services de lecture des référentiels, ACL `acl/gmao/UsageDeLaSalleParLaGmao`, `acl/stock/*`).

## Décision
Justifiée, pas de changement : Organisation expose des lectures de référentiel qui **agrègent** le catalogue d'articles et les générateurs (écrans de référentiels) et implémente côté Stock les ports fournis `CentresFournis` et `VerrouDuCentreFourni`. La relation est **Conformist** d'Organisation envers Stock et GMAO sur ces lectures, limitée aux packages publiés (règle ArchUnit `les_autres_contextes_seulement_par_leur_langage_publie`). Stock et GMAO ne dépendent pas d'Organisation par pom (pas de cycle), les implémentations inverses passent par des ports publiés.

À surveiller : toute importation hors `publishedlanguage` ou tout nouveau pom amont -> aval échoue la garde ; si ces lectures migrent vers les contextes aval, le pom se réduit (événement de retrait, pas de date).
