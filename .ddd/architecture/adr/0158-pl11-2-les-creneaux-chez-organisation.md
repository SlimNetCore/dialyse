---
status: accepted
date: 2026-11-13
sources: pl11-analyse.md (4, 6), pl11-decisions.md (D3, D4, D10, D11, D12), pl11-0-filet.md, ADR 0024, 0111, 0157
---

# ADR 0158 - PL11-2 : les créneaux (position_creneau) chez l'Organisation

## Contexte
Refactoring sur place (ADR 0024) : mêmes URL, rôles, codes, table. L'administration générique `/api/v1/admin/referentials/creneaux` écrivait `position_creneau` par SQL dynamique en plateforme, avec un contrôle d'usage limité aux patients (KB-PL11-1).

## Décision
1. **Cas d'usage dans l'Organisation** : `GererLesCreneaux` (langage publié `GestionDesCreneaux`) : créer, modifier, supprimer ou désactiver, lister, importer. `SaisieDeCreneau` (VO : nettoyage, obligatoire, 50/255 caractères) et `Creneau.verifierLUnicite` (code, casse et espaces ignorés, par centre) portent les règles ; mêmes anomalies, codes et messages qu'avant (goldens PL11-0 identiques).
2. **Descripteur publié** : `DescripteursDeLOrganisation.CRENEAU` ; la façade le reçoit par le SPI de l'ADR 0157 (`CreneauxParOrganisation#descripteurs`, ACL `acl/organisation`). `ReferentialKind.CRENEAU` disparaît ; la façade ne décrit plus que salles et médecins.
3. **D3, usage de tous les propriétaires** : l'Organisation publie le port `UsageDuCreneau` ; chaque propriétaire d'une référence l'implémente chez lui et l'enregistre comme bean : Patient et couverture (`patients.position_id`, `mouvement_patient.position_id`), Séance (`seances.creneau_id`), Planification (`infirmier_affectation`, `infirmier_remplacement`, `planification_calendrier_case`, `deplacement_temporaire`). Le sens est imposé par Maven : ces trois contextes dépendent déjà d'Organisation, un ACL côté Organisation créerait un cycle. Le compte reste par identifiant seul (KB-PL11-2 reproduit : non décidé).
4. **D4, désactivation logique** : un créneau référencé est désactivé (`actif=false`, migration V58, 204) au lieu de supprimé ; non référencé : suppression physique comme avant. Les créneaux désactivés ne sont plus listés ni proposés (administration, `/referentials/positions`, lecture de la Planification : `SallesEtCreneauxDuCentreJdbc`, KB-PL11-14) mais restent lus par les libellés des historiques.
5. **D10** : si un propriétaire échoue à compter, la suppression est refusée (422 `REFERENTIAL_USAGE_UNVERIFIABLE`) et le créneau est conservé.
6. **Réactivation (calquée sur D6)** : créer ou réimporter le code d'un créneau désactivé le réactive (libellé mis à jour) au lieu de créer un doublon. Question ouverte à confirmer par les métiers avant le 2026-12-15.
7. **Content-Type (hérité de PL11-1)** : `consumes` retiré des POST/PUT ; l'intercepteur `AutorisationAvantLeCorpsDesReferentiels` exige le JSON (415) seulement pour un ADMIN ; pour tout autre rôle le 403 est produit par le chemin normal. Un secrétaire sans en-tête reçoit de nouveau 403.

## Butoirs 2027-03-31 (D12 : aucun report)
Aucune entrée de butoir n'est soldée par PL11-2 (pl11-analyse.md 6) : `ReferentialAdminJdbcAdapter`/`ReferentialAdminTables` restent listés pour salles et médecins (PL11-3/4, retrait PL11-6). La garde anti-retour de l'ADR 0157 est étendue à `CRENEAU` et interdit `position_creneau` dans l'adaptateur et la table de correspondance.

## Conséquences
- Écarts de goldens relus un par un : `pl11-0-creneaux/refus-d-usage-par-dependance_nouveau_code` (422 -> 204 : désactivation), `audit_nouveau_code` (suppression refusée -> 204) ; archives `_ecarts-legacy/pl11-0-creneaux/*_avant_pl11-2.legacy.json`. rf3 `refus-de-forme` : trois lignes « secrétaire sans corps » 415 -> 403 (identiques aux archives `_avant_pl11-1`).
- `PatientMigrator` et `findAllForMatching` apparient les créneaux par `GestionDesCreneaux#proposes` en attendant PL11-5.
- Pas de routage Strangler, flag ni projection.
