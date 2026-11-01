# Filet de non-régression — PD5 lectures publiées et retrait legacy

Slice : `pilotage-direction-pd5-lectures-publiees`. Contexte : `pilotage-direction`.
Date : 2026-11-01.

## Point d'observation

- Garde d'architecture : `LectureSeuleDuPilotageTest` (platform, 2 tests : périmètre R4,
  détection prouvée par test négatif).
- Tables possédées : `tableaux_de_bord`, `instantanes_mensuels`, `alertes_direction`,
  `exports_synthese`.

## Périmètre de la garde

- Scanne `contexts/pilotage-direction/src/main` (hors `/entity/`, `/migration/`) et échoue
  sur tout SQL (`FROM`/`JOIN`/`INTO`/`UPDATE`/`TABLE`) visant une table hors des 4 possédées.
- Garantit R4 au niveau code : aucune écriture ni lecture cross-context en SQL direct ;
  la consommation inter-contextes passe par les langages publiés.

## Scénarios verrouillés

1. Aucun SQL hors des 4 tables dans le contexte.
2. Test négatif : une lecture `FROM seances` dans un bac temporaire est détectée.

## Points d'attention (dette restante)

- `platform/application/direction` reste en service le temps du retrait : les endpoints
  legacy cohabitent avec `/api/v1/pilotage/**` (strangler, pas de coupure).
- Rendu PDF/HTML via `ModeleDocumentPrinter` (voir PD4) : à brancher au retrait.
- Verrou de clôture Facturation pour PD2 : ACL à brancher au retrait du legacy.
