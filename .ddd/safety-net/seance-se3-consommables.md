---
slice: seance-se3-consommables
status: characterized
date: 2026-11-01
sources: [.ddd/migration/slices/seance-se3-consommables.md, .ddd/tactical/seance.md, ADR 0045, 0046, 0062]
---

# Filet de caractérisation — SE3 Consommables déclarés

## Point d'observation

HTTP (MockMvc sur le contexte Spring complet, sécurité réelle + `CenterAccessGuard`/`RoleScopeFilter`,
H2 du profil `test`) et état SQL des tables `seance_consommables` / `stock_movements` / `articles`.
Aucun mock du code caractérisé. Les opérations `DeclarerConsommables`, `ListerConsommablesDeSeance`,
`addConsommableSeance`, `removeConsommableSeance`, `updateConsommableSeance` passent par
`SeanceRestController`, `SeanceApplicationService` et `SeanceDomainService`, avec isolation multi-centre
par `center_id` et coordination avec le Stock via le langage publié `ConsommationsDeSeance`.

## Tests existants couvrant la slice

- `backend/platform/src/test/java/.../infrastructure/web/rest/SeanceRestControllerTest` (ajout/retrait/modification de
  consommable, rôles, multi-centre) ;
- `backend/platform/src/test/java/.../infrastructure/web/rest/SeanceScanValidationControllerTest` (validation directe
  avec consommables, multi-centre) ;
- `backend/platform/src/test/java/.../infrastructure/web/rest/SeanceSuppressionIntegrationTest` (restitution du stock à
  la suppression de séance) ;
- `backend/bc-seance/src/test/java/.../domain/seance/service/SeanceDomainServiceTest` (logique domaine : ajout sur
  séance validée uniquement, quantité, article inactif/inconnu, multi-centre) ;
-
`backend/platform/src/test/java/.../characterization/stock/referentiel/ArticlesDeLaSeanceCharacterizationIntegrationTest`
(article inconnu, d'un autre centre, inactif).

## Scénarios verrouillés (multi-centre)

- Ajout d'un consommable sur une séance déjà `VALIDEE` : seule cette ligne sort du stock (FEFO), les consommables
  déclarés à la validation sont déjà sortis ; idempotence du statut uniquement (RG-SEA-024).
- Refus d'ajout sur une séance `CREEE` ou `FACTUREE` ; refus pour quantité nulle/négative ; refus pour article
  inconnu, d'un autre centre ou inactif (400, KB-STK-56).
- Retrait d'un consommable d'une séance (`ADMIN`, `INFIRMIER`) : la quantité est mise à zéro sur le bon de sortie
  de la séance et le stock est restitué ; refus si la séance est verrouillée pour facturation.
- Modification de la quantité d'un consommable : mise à jour de la ligne unique du bon de sortie ; refus si la
  séance est verrouillée pour facturation.
- Validation d'une séance : les consommables déclarés sont sortis du stock automatiquement, chaque ligne génère
  une sortie FEFO (RG-SEA-021) ; un article inactif provoque un refus (400, KB-STK-57).
- Suppression d'une séance : les consommables et administrations EPO/fer sortis du stock pour la séance sont
  restitués (lots selon le mouvement d'origine).
- Isolation inter-centres : une séance du centre A n'est ni modifiable ni consultable depuis le centre B ;
  `centerId` systématiquement propagé dans `SeanceUseCase`/`SeanceRepositoryPort` et dans les clés de cache.
- Raccourcis de consommables du centre : au plus 12 articles, sans doublon, articles actifs et du même centre ;
  mise en cache par centre et purgée à chaque modification (RG-SEA-027).

## Stabilité

Suite exécutée sur H2 (profil `test`). Isolation centre vérifiée par `SeanceRestControllerTest`,
`SeanceDomainServiceTest` et `ArticlesDeLaSeanceCharacterizationIntegrationTest`. Aucun mock du code
caractérisé. Exécution répétée sans flakiness détectée.

## Points d'attention

- `addConsommableSeance` n'est autorisé que sur une séance déjà validée ; l'ajout sur une séance créée est refusé.
- Valider à nouveau une séance déjà validée avec une liste de consommables retraite ces consommables (nouvelle
  sortie) : l'idempotence ne porte que sur le statut (RG-SEA-024).
- Le stock décide des lots (FEFO) ; la Séance ne lit plus les lots directement (langage publié `ConsommationsDeSeance`).
- Compatibilité H2 (dev) / PostgreSQL (prod) : aucun SQL spécifique à un moteur.
- Les règles de gestion associées : RG-SEA-021, RG-SEA-022, RG-SEA-024, RG-SEA-027 (voir
  `docs/reference/regles-de-gestion/07-seances.md`).
