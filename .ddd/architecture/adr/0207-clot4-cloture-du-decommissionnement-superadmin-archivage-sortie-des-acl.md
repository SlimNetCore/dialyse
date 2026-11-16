---
status: accepted (CLOT4)
date: 2026-11-16
decision-makers: [utilisateur (décisions reçues en chat le 2026-11-16), architecte]
context-scope: platform (sécurité, reprise, db/migration), tous contextes (ACL)
related: ADR 0161, 0184, 0204, 0205 (précisé : V60 autorisée) ; KB-S6a-6 ; revue globale 2026-11-16 §6
---

# 0207 - Clôture du décommissionnement : SUPERADMIN, archivage, sortie des ACL

## Contexte
La revue globale finale (2026-11-16, §6) liste trois restes : code SUPERADMIN inatteignable dans la reprise, quatre tables mortes, dix ACL. L'utilisateur a tranché les trois.

## Décisions
1. **SUPERADMIN dans la reprise : code inatteignable supprimé, refus maintenu.** `RoleScopeFilter` refuse le SUPERADMIN (seul ou avec ADMIN) en 403 `ROLE_SCOPE` sur `/api/v1/admin/migration` et `/api/v1/admin/referentials` avant tout contrôleur (KB-S6a-6). Retirés : `SUPERADMIN` du `@PreAuthorize` de `MigrationRestController` (devient `hasRole('ADMIN')`) et la branche « accès transverse » de `CenterAccessGuard` (seuls appelants : ces deux contrôleurs). Aucune réponse HTTP ne change (golden `droits_et_garde_de_centre` inchangé). Usages légitimes du SUPERADMIN conservés ailleurs (supervision, comptes direction/admin de société, rôles, setup, licences, audit, ACL `SessionLegacy`).
2. **Archivage, jamais de suppression.** Flyway **V60** renomme `user_center_assignment`, `gmao_alertes_maintenance`, `gmao_historique_maintenance`, `stock_parametres` en `archive_<nom>` (`ALTER TABLE IF EXISTS … RENAME TO …`, syntaxe valable PostgreSQL 16 et H2). Données, index et contraintes intacts ; retour arrière par RENAME inverse. Vérifié par recherche : aucun code vivant (main) ne lit ces tables ; les schémas H2 des contextes n'en portent plus (ADR 0184). La chaîne reste unique dans platform (ADR 0205 précisé : V1-V60).
3. **Sortie des 10 ACL à l'événement**, sans date : « fin de la reprise de production ET retrait de la façade ADR 0161 » (ACL `organisation/{Creneaux,Medecins,Salles,Referentiels}`, `patientcouverture/{Transporteurs,Forfaits,Payeurs,Referentiels}`, `reprise/AppariementParLesContextes`, `gmao/GenerateursDeRepriseGmao`).

## Garde-fou production
**V60 ne doit jamais être jouée en production sans sauvegarde préalable** (dump complet). Contrôles avant/après : `verifications-production.md` § 89. Une suppression définitive des `archive_*` exige un nouvel accord écrit.

## Conséquences
+ code d'autorisation de la reprise sans branche morte ; schéma de production allégé sans perte.
- les `archive_*` occupent de la place jusqu'à une décision de suppression.
