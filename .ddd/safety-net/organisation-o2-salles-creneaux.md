---
slice: organisation-o2-salles-creneaux
status: characterized
date: 2026-10-29
sources: [.ddd/migration/slices/organisation-o2-salles-creneaux.md, ADR 0063]
---

# Filet de caractérisation — O2 Salles et créneaux (positions de créneau)

## Point d'observation

HTTP (MockMvc sur le contexte Spring complet, sécurité réelle + `CenterAccessGuard`, H2 du profil `test`) et état SQL
des tables `salle`, `position_creneau`. Aucun mock du code caractérisé, aucun seam. Les salles et les créneaux sont des
référentiels administrables génériques (`/api/v1/admin/referentials/{salles,creneaux}`), gérés par
`ReferentialAdminRestController` + `ReferentialAdminApplicationService` + `ReferentialAdminTables`.

## Tests existants couvrant la slice

- `backend/platform/src/test/java/com/hemodialyse/backend/infrastructure/web/rest/ReferentialAdminIntegrationTest`
  (cloisonnement par centre sur `/salles`, création/lecture/suppression, refus inter-centre, rôles, cache par centre) ;
- `backend/bc-referential/src/test/java/.../ReferentialAdminDomainServiceTest` (unicité par centre, mise à jour à
  l'import, validation) ;
- `backend/platform/src/test/java/.../ReferentialAdminRestControllerTest` si présent.

## Scénarios verrouillés (multi-centre)

- Liste `/salles` et `/creneaux` filtrée par `centerId` de session ; un ADMIN ne voit que son centre.
- Création dans un centre A n'apparaît pas dans le centre B (`center_id` partie intégrante de la ligne).
- Suppression d'une salle/créneau d'un autre centre → `REFERENTIAL_NOT_FOUND` (422), aucune fuite.
- Paramètre `centerId` autre que la session → 403.
- Cache référentiel invalidé par centre uniquement (écriture centre A n'invalide pas le cache centre B).
- Pagination `page`/`size` avec enveloppe `{items,total,page,size}`.

## Stabilité

Suite exécutée sur H2 (profil `test`). Isolation centre vérifiée par
`ReferentialAdminIntegrationTest.anAdminOnlySeesAndChangesItsOwnCenter`.

## Points d'attention

- Les données de démo (seed) peuvent pré-peupler des salles/créneaux ; les tests nettoient par `center_id`.
- Les consommateurs (Planification, Séance, GMAO) lisent via `SalleVueJdbcAdapter` et `ReferentialAdminTables` ; retrait
  différé des adapters legacy (P9).