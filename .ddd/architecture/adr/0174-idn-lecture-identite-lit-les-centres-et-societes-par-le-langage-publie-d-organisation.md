---
status: accepted (deux écarts corrigés sur décision de l'utilisateur du 2026-11-14 ; langage publié étendu)
date: 2026-11-14
decision-makers: [utilisateur, architecte]
context-scope: platform (Identité et accès, transitoire) + organisation (langage publié) + pilotage-direction (ACL)
related: ADR 0024, 0107, 0140 D7, 0157-0161, 0173 ; `.ddd/migration/reviews/idn-lecture-realisation.md` ; `TableDeLOrganisationTest`
---

# 0174 - IDN-lecture : Identité et accès lit centres et sociétés par le langage publié d'Organisation

Refactoring sur place (ADR 0024) : mêmes URLs, mêmes tables, mêmes JSON ; filet de 19 goldens (phase 1, commit afb554b7).

## Décision
1. Les six derniers lecteurs SQL de `centers`/`societes` hors Organisation passent par `AnnuaireDesCentres` / `AnnuaireDesSocietes` injectés : `AuthService`, `DirectionAccountService`, `SocieteAdminAccountService`, `UserRestController`, `DevSuperAdminBootstrapInitializer`, `SessionLegacy` (Pilotage). Les tables propres à Identité (`app_user*`, `auth_refresh_token`) restent lues par elle.
2. **Extension du langage publié** (option B de la phase 1, validée par l'utilisateur), tri côté Organisation, adapter JDBC et faux en mémoire passent le même contrat (`AnnuairesContrat`) :
   - `AnnuaireDesCentres.nomDuCentre(centre)` : nom d'un centre, vide si inconnu ;
   - `AnnuaireDesCentres.tousAvecNom()` : tous les centres (actifs ou non) avec leur nom, par nom croissant ;
   - `AnnuaireDesSocietes.societesAvecUnCentreActif()` : sociétés actives avec au moins un centre actif, par raison sociale croissante (troisième lecture, nécessaire pour l'annuaire public de connexion : la composer en Java aurait changé la collation du tri).
   Le reste est composé : centre actif et société d'un centre (`societeDuCentre` + `societe(id).centres`), centres actifs d'une société, existence d'une société (`societe(id).isPresent()`), société active (`societesActives().contains`).
3. **Écarts corrigés** (golden `_nouveau_code`, ancien golden archivé en `_ecarts-legacy/**/*_avant_idn_lecture.legacy.json`) : KB-IDN-1 (identifiant de société illisible : 500 -> 400) et KB-IDN-2 (403 avant 400 sur les comptes de société), par `DatesIllisiblesDeLaDirectionAdvice`. Les 16 autres goldens sont inchangés.
4. `TableDeLOrganisationTest` : les 6 entrées sont supprimées, critère d'avancement O4 = **0**, garde permanente (tout nouveau lecteur échoue). Aucun report de butoir (2027-03-31 sans objet). `seamAllowed` = [].

## Conséquences
- Les fakes d'`AnnuaireDesCentres` des modules Séance et Patient-couverture (tests) implémentent les deux nouvelles méthodes.
- Une lecture de plus par connexion (annuaire) ; volumétrie de centres faible, aucune requête ajoutée dans une boucle (sauf `GET /users/{id}` : un `nomDuCentre` par centre rattaché).
