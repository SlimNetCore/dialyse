---
status: accepted (livrée le 2026-11-14)
date: 2026-11-14
decision-makers: [utilisateur, architecte]
context-scope: facturation (porteur), comptabilite (port publié consommé)
related: ADR 0024, 0027, 0036 ; KB-CPT-37 ; `inventaire-platform-restant-v2.md` ligne 19
---

# 0183 - LST : les deux écouteurs de comptabilisation quittent la plateforme, pour la Facturation (et non la Comptabilité)

Refactoring sur place (ADR 0024) : aucun changement de comportement.

## Constat : la destination demandée est impossible (cycle Maven)
Le placement visé était `comptabilite/infrastructure/acl/facturation`. Or `contexts-facturation` dépend de `contexts-comptabilite` (`contexts/facturation/pom.xml`, langage publié de la Comptabilité) : un écouteur de la Comptabilité devrait importer `facturation.publishedlanguage` (`FacturationValideeEvent`, `FactureReglementRecordedEvent`), donc `contexts-comptabilite` dépendrait de `contexts-facturation` : cycle. Inverser la dépendance Maven ou extraire les événements dans un module tiers est hors de cette petite slice.

## Décision
Les écouteurs vont dans la Facturation, dans `facturation/infrastructure/acl/comptabilite/` (dossier de ses ACL vers la Comptabilité, dépendance Maven déjà existante) :
- `FacturationValideeComptabiliteListener` devient `ComptabilisationDesVentesApresLaValidation` (politique P1) ;
- `ReglementEncaisseComptabiliteListener` devient `ComptabilisationDuReglementApresEncaissement` (politique P2).
Méthodes `onFacturationValidee` / `onReglementEncaisse`, `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)`, journalisation et rattrapage des `RuntimeException`, appel du port publié `ComptabilisationDesVentesEtReglements` : inchangés. Câblés par `@Bean` dans `FacturationContexteConfiguration` (le contexte n'utilise pas `@Component`). Le paquet `infrastructure/listener` disparaît de la plateforme.

## DomainServiceConfig
Aucun bean de `DomainServiceConfig` ne concernait ces écouteurs (ils étaient `@Component`) : rien à retirer. La cible « retirer les beans de DomainServiceConfig si possible » était sans objet pour LST.

## KB-CPT-37 (relu)
Décision de 2026-10-19 conservée telle quelle : comptabilisation après le commit de l'appelant, une source par transaction, facture et règlement toujours conservés, refus journalisé, source à régénérer (`/comptabilite/rebuild`). Les tests `p1_/p2_transaction_appelante_nouveau_code` et les goldens existants passent inchangés.

## Filet
Existant, vert avant et après : `VentesEtReglementsCharacterizationIntegrationTest` (événement => écriture, rejeu idempotent, transaction appelante, mois clos) et `EvenementEtComptabiliteCharacterizationIntegrationTest` (facturation et règlements). Seuls changements de tests : le type du consommateur injecté (`ComptabilisationDesVentesApresLaValidation`) et un commentaire. Aucun golden modifié.

## Gardes
Les deux anciens fichiers étaient dans `covered.json` (lignes 49-50) : entrées à retirer par l'utilisateur. `seamAllowed` = [].
