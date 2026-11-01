---
slice: organisation-o3-referentiels-parametrage
status: characterized
date: 2026-10-29
sources: [.ddd/migration/slices/organisation-o3-referentiels-parametrage.md, ADR 0063]
---

# Filet de caractérisation — O3 Référentiels de paramétrage (médecins, machines, protocoles)

## Point d'observation

HTTP (MockMvc sur le contexte Spring complet, sécurité réelle + `CenterAccessGuard`, H2 du profil `test`) et état SQL
des tables référentielles (`medecin`, et équipements GMAO de type `GENERATEUR_DIALYSE` pour les machines). Aucun mock du
code caractérisé. Les médecins sont un référentiel administrable générique (`/api/v1/admin/referentials/medecins`),
géré par `ReferentialAdminRestController` + `ReferentialAdminApplicationService` + `ReferentialAdminTables`. Les
machines (générateurs de dialyse) sont désormais gérées comme des équipements GMAO via `/gmao/equipements` (type
`GENERATEUR_DIALYSE`). Les protocoles dialyse sont portés par les forfaits (`/api/v1/admin/referentials/forfaits`) et
les prescriptions médicales (contexte médical). Aucune migration de données.

## Tests existants couvrant la slice

- `backend/platform/src/test/java/com/hemodialyse/backend/infrastructure/web/rest/ReferentialAdminIntegrationTest`
  (cloisonnement par centre sur `/medecins`, création/lecture/suppression, refus inter-centre, rôles, cache par
  centre) ;
- `backend/bc-referential/src/test/java/.../ReferentialAdminDomainServiceTest` (unicité par centre, mise à jour à
  l'import, validation) ;
- `backend/platform/src/test/java/.../ReferentialAdminRestControllerTest` si présent ;
- `backend/platform/src/test/java/.../StockReferentialIntegrationTest` / équipements GMAO pour les machines
  (générateurs).

## Scénarios verrouillés (multi-centre)

- Liste `/medecins` filtrée par `centerId` de session ; un ADMIN ne voit que son centre.
- Création d'un médecin dans un centre A n'apparaît pas dans le centre B (`center_id` partie intégrante de la ligne).
- Suppression d'un médecin d'un autre centre → `REFERENTIAL_NOT_FOUND` (422), aucune fuite.
- Paramètre `centerId` autre que la session → 403.
- Cache référentiel invalidé par centre uniquement (écriture centre A n'invalide pas le cache centre B).
- Pagination `page`/`size` avec enveloppe `{items,total,page,size}`.
- Machines (générateurs) : lecture via `findGenerateurs`/`findGenerateursBySalle` filtrée par `centerId` ; un centre ne
  voit que ses propres générateurs.
- Protocoles/forfaits : liste et CRUD filtrés par `centerId` (référentiel administrable).

## Stabilité

Suite exécutée sur H2 (profil `test`). Isolation centre vérifiée par
`ReferentialAdminIntegrationTest.anAdminOnlySeesAndChangesItsOwnCenter`.

## Points d'attention

- Les données de démo (seed) peuvent pré-peupler des médecins ; les tests nettoient par `center_id`.
- Les consommateurs (Séance, Planification, GMAO) lisent les médecins via `ReferentialRepositoryPort.findMedecins` et
  les
  machines via `findGenerateurs`/équipements GMAO ; retrait différé des adapters legacy (P9).
- Les protocoles ne sont pas un référentiel plat autonome : ils se matérialisent via les forfaits et les prescriptions
  médicales ; tout changement doit préserver cette dépendance.
- Le cache référentiel doit toujours inclure le `centerId` dans sa clé (règle multi-centre §2).