# Filet de non-régression — PD3 alertes direction

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
