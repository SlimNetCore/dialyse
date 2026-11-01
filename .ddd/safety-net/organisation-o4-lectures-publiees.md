---
slice: organisation-o4-lectures-publiees
status: characterized
date: 2026-10-29
sources: [.ddd/migration/slices/organisation-o4-lectures-publiees.md, ADR 0063, .ddd/tactical/organisation.md]
---

# Filet de caractérisation — O4 Lectures publiées et retrait du legacy Organisation

## Point d'observation

HTTP (MockMvc sur le contexte Spring complet, sécurité réelle + `CenterAccessGuard` / `DirectionAccessGuard`, H2 du
profil `test`) et état SQL des tables d'Organisation (`societes`, `centres`, `salle`, `position_creneau`, `medecin`,
équipements GMAO de type `GENERATEUR_DIALYSE`). Aucun mock du code caractérisé. Les lectures publiées de
`organisation.v1` sont consommées par Planification, Stock, Séance, Facturation, GMAO, Pilotage et Direction ; le filet
verrouille l'absence de SQL direct sur les tables d'Organisation hors du contexte Organisation (modèle
`TableDuStockTest`), ainsi que l'isolation multi-centre / multi-société.

## Tests existants couvrant la slice

- `backend/platform/src/test/java/com/hemodialyse/backend/infrastructure/web/rest/ReferentialAdminIntegrationTest`
  (cloisonnement par centre sur `/salles`, `/creneaux`, `/medecins`, création/lecture/suppression, refus inter-centre,
  rôles, cache par centre) ;
- `backend/bc-referential/src/test/java/.../ReferentialAdminDomainServiceTest` (unicité par centre, mise à jour à
  l'import, validation) ;
- `backend/platform/src/test/java/.../SocieteRestController` / `CentreRestController` (gestion des sociétés et centres,
  rattachement centre↔société, statut actif) ;
- `backend/platform/src/test/java/.../Direction*QueryService` (lectures consolidées par société, isolation entre
  sociétés) ;
- `backend/platform/src/test/java/.../PlanningDesPlacesLegacy` et adaptateurs ACL Planification (traduction des types
  legacy vers le langage publié, sans SQL direct sur `salle`/`position_creneau` hors contexte).

## Scénarios verrouillés (multi-centre / multi-société)

- Liste `/salles`, `/creneaux`, `/medecins` filtrée par `centerId` de session ; un ADMIN ne voit que son centre.
- Création dans un centre A n'apparaît pas dans le centre B (`center_id` partie intégrante de la ligne).
- Suppression d'une salle/créneau/médecin d'un autre centre → `REFERENTIAL_NOT_FOUND` (422), aucune fuite.
- Paramètre `centerId` autre que la session → 403.
- Cache référentiel invalidé par centre uniquement (écriture centre A n'invalide pas le cache centre B).
- Pagination `page`/`size` avec enveloppe `{items,total,page,size}`.
- Machines (générateurs) : lecture via `findGenerateurs`/`findGenerateursBySalle` filtrée par `centerId` ; un centre ne
  voit que ses propres générateurs.
- Sociétés : une direction ne voit que sa société (`societe_id` du jeton, revérifié en base) ; aucun accès croisé entre
  sociétés.
- Centres : rattachement à une société unique ; un centre inactif n'est pas publié aux consommateurs.
- Aucun SQL direct sur `societes`, `centres`, `salle`, `position_creneau`, `medecin` hors du contexte Organisation
  (garde `TableDuStockTest` étendue) ; les consommateurs passent par `organisation.v1` (ports publiés).

## Stabilité

Suite exécutée sur H2 (profil `test`). Isolation centre/société vérifiée par
`ReferentialAdminIntegrationTest.anAdminOnlySeesAndChangesItsOwnCenter` et les tests Direction. Aucun mock du code
caractérisé.

## Points d'attention

- Les données de démo (seed) peuvent pré-peupler des salles/créneaux/médecins ; les tests nettoient par `center_id`.
- Les consommateurs (Planification, Séance, GMAO, Stock, Facturation, Pilotage) lisent via le langage publié
  `organisation.v1` (`SocieteRepositoryPort`, `CentreRepositoryPort`, `SalleVuePort`, `ReferentialRepositoryPort`) ; le
  retrait de `bc-referential` pour l'organisation est différé à la cutover.
- Le retrait effectif du legacy Organisation suppose que tous les SQL hors contexte aient été remplacés par les ports
  publiés ; la garde « pas de SQL sur les tables d'Organisation hors contexte » doit rester verte.
- Le cache référentiel doit toujours inclure le `centerId` dans sa clé (règle multi-centre §2).
- Compatibilité H2 (dev) / PostgreSQL (prod) : aucun SQL spécifique à un moteur.