# Filet de non-régression — PD4 export synthèse

Slice : `pilotage-direction-pd4-export-synthese`. Contexte : `pilotage-direction`.
Date : 2026-11-01.

## Point d'observation

- Legacy : `DirectionReportIntegrationTest`, `DocumentRestController*` (platform, reporting).
- Nouveau : `ExportsSyntheseControleur` (`/api/v1/pilotage/exports-synthese`).

## Tests existants couvrant la slice

- `DirectionReportIntegrationTest` : synthèse direction par centre.
- Nouveaux : `ExportSyntheseTest`, `ExportSyntheseServiceTest`,
  `ExportsSyntheseJdbcIntegrationTest`, `ExportsSyntheseControleurIntegrationTest`.

## Scénarios verrouillés (multi-centre)

1. Génération : contenu assemblé depuis les read models du contexte (tableau de bord + alertes + instantanés), jamais
   par SQL cross-context.
2. Téléchargement par id + `centreId` : isolation inter-centres.
3. Formats : PDF, HTML (énumération fermée).

## Points d'attention (dette documentée)

- Le rendu physique PDF/HTML via `ModeleDocumentPrinter` (§11.1) reste assuré par la
  plateforme reporting jusqu'au retrait complet du legacy : l'export du contexte produit
  le contenu de synthèse ; la déclaration du modèle de document (`ModeleDocumentCatalog`
    + `.jrxml`) sera faite au branchement de l'impression.
- Aucune table dédiée legacy ; table neuve `exports_synthese` (tracking).
