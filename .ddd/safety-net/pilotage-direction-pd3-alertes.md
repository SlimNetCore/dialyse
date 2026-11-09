# Filet de non-régression — PD3 alertes direction

> **OBSOLÈTE (gel du module parallèle, 2026-11-02, ADR 0075).** Types `SOUS_EFFECTIF`…, R2 « une alerte par centre et par jour », acquittement, événement `AlerteDirectionGeneree` : rien de cela n'existe dans le legacy (codes réels `KTV_CONFORMITE_BASSE`, `HB_HORS_CIBLE`, `OBSERVANCE_EN_RETARD`, `STOCK_SOUS_SEUIL`, `LOTS_PERIMES`, `LOTS_PEREMPTION_PROCHE` ; identité (centre, code) sans jour ; pas d'acquittement). Filet réel et règle R2 caractérisée : `.ddd/safety-net/pilotage-direction.md` (4 goldens `pilotagedirection/pd3/alertes/*`, KB-DIR-04).

Slice : `pilotage-direction-pd3-alertes`. Contexte : `pilotage-direction`.
Date : 2026-11-01.

## Point d'observation

- Legacy : `DirectionAlertPolicy` + `DirectionAlertHistoryServiceTest` (platform).
- Nouveau : `AlertesDirectionControleur` (`/api/v1/pilotage/alertes`).

## Tests existants couvrant la slice

- `DirectionAlertHistoryServiceTest`, `DirectionAlertPolicyTest` : historique et politiques, isolation.
- Nouveaux : `AlerteDirectionTest`, `AlerteDirectionServiceTest`,
  `AlertesDirectionJdbcIntegrationTest`, `AlertesDirectionControleurIntegrationTest`.

## Scénarios verrouillés (multi-centre + R2)

1. Alerte unique par centre et jour (R2 : `ALERTE_DEJA_EXISTANTE_POUR_JOUR` sinon).
2. Types : SOUS_EFFECTIF, SUR_CAPACITE, RETARD_VALIDATION, GREFFE_EN_ATTENTE.
3. Acquittement : une seule fois (`ALERTE_DEJA_ACQUITTEE` sinon).
4. Liste paginée par centre ; isolation inter-centres.
5. Événement publié `AlerteDirectionGeneree` (consommé par les notifications direction).

## Points d'attention

- Consommation d'événements publiés (AbsenceDetectee, etc.) : les seuils sont alimentés
  par les langages publiés, jamais par SQL direct (garde PD5).
