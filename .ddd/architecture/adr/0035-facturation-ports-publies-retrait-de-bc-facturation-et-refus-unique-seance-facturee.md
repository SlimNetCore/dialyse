---
status: accepted
date: 2026-10-21
deciders: utilisateur (décisions F6 du 2026-10-21) ; amende l'ADR 0024 (les contextes se parlent par leur langage publié)
---

# ADR 0035 — Facturation : ports publiés pour les derniers consommateurs, retrait de `bc-facturation`, refus unique d'une séance facturée

## Contexte

`backend/bc-facturation` ne contenait plus de classe depuis F5. Restaient des lecteurs et écrivains des tables `factures`,
`facture_lignes` et `facture_reglements` hors du contexte (Absences, Migration, Comptabilité) et trois classes de `platform` qui
importaient `application`/`domain` du contexte (exemptions de la règle ArchUnit).

## Décisions

1. **Retrait du module** (pom racine, `platform`, `Dockerfile`, scripts PIT, dossier). Aucune classe à migrer.
2. **Ports publiés dans `contexts/facturation/publishedlanguage`** : `FacturationDuPatient` (Absences), `RepriseDesSoldes`
   (Migration : le SQL de reprise et d'annulation est déplacé tel quel dans `RepriseDesSoldesJdbc`, comportement identique),
   `SyntheseDeFacturation` + `SyntheseMensuelle`, `FormatDImpression`, `BarresDeRepartition`, `ImpressionDeLaSynthese`
   (impression), `DeclarationDesAbsences` (besoin de la Facturation, fourni par le module Absences). Les ACL de `platform`
   (`OpeningBalanceJdbcAdapter`, `MigrationRollbackJdbcAdapter`, `AbsenceDonneesJdbcAdapter`, `AbsencesDesPatientsLegacy`)
   traduisent. Plus aucune exemption dans `FrontieresDesContextesTest`.
3. **Sens de la dépendance Facturation / Comptabilité.** La Facturation dépend déjà de la Comptabilité (`ParametrageFiscal`) ;
   l'inverse créerait un cycle Maven. Les ports de lecture que la Comptabilité consomme (`FacturesEmises`, `ReglementsEncaisses`)
   sont donc **publiés par la Comptabilité** (son besoin, en types simples) et **fournis par la Facturation**
   (`infrastructure/acl/comptabilite`, SQL de lecture seule). La Comptabilité les traduit en ses ports de domaine par une ACL
   (`infrastructure/acl/facturation`). La requête « factures sans écriture » (qui lisait `ecritures_comptables`) devient un
   calcul du cas d'usage avec `EcritureComptableRepository.parSource`.
4. **KB-FAC-67 (écart sur l'argent).** Les factures de reprise (`numero_facture` commençant par `REPRISE-`) et leurs règlements de reprise (auteur `REPRISE`)
   sont exclus du rebuild comptable et de la comptabilisation automatique ; la reprise des soldes d'ouverture est comptabilisée à
   part (les soldes figurent déjà dans la comptabilité de l'ancien logiciel). Écritures déjà générées : extourne à décider avec le
   comptable (`verifications-production.md` point 22), non exécutée.
5. **KB-FAC-70 (écart sur le contrat d'erreur).** Toute modification refusée parce que la séance est `FACTUREE` répond 422
   `SEANCE_FACTUREE_NON_MODIFIABLE` (`SeanceFactureeNonModifiableException`, noyau partagé ; un seul handler HTTP). Code choisi par
   symétrie avec `SEANCE_FACTUREE_NON_SUPPRIMABLE`. Conditions de refus et définition de « séance facturée » inchangées (KB-FAC-69).
6. **Reproduits** : KB-FAC-68 (compte rendu de reprise), KB-FAC-69 (trois définitions), KB-FAC-71 (`saisi_par` = principal).

## Conséquences

- Les tests de caractérisation F6 passent sur le nouveau code ; 14 goldens remplacés par des goldens `*_nouveau_code` (3 pour la
  reprise, 11 pour les refus), diff relu avant promotion.
- Le frontend doit gérer le nouveau code d'erreur (fiche F6).
- Stock (`SeanceBillingStatusJdbcAdapter.isBilled`) et blocage de suppression d'un forfait (`ReferentialAdminTables`) lisent
  encore une table de la Séance / une table de la Facturation sans passer par un port : hors F6, à reprendre avec le contexte Séance.
