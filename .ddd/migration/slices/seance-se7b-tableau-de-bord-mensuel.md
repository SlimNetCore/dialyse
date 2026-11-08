---
slice: seance-se7b-tableau-de-bord-mensuel
status: built
date: 2026-11-08
order: 7.2
context: seance
parent: seance-se7-lectures-publiees
depends_on: [seance-se7a-routes-residuelles]
sources: [.ddd/safety-net/seance-se7-lectures-publiees.md, .ddd/architecture/adr/0095-seance-se7b-reprise-sur-place-du-tableau-de-bord-mensuel-detail-nominatif-et-exports.md]
---

# SE7b : tableau de bord mensuel des séances (dashboard, détails, exports CSV/PDF/XLSX)

`GET /api/v1/seances/dashboard`, `/dashboard/details?kind=`, `/dashboard/export?format=` (ADMIN INFIRMIER MEDECIN SECRETAIRE). Reprise SUR PLACE dans `backend/contexts/seance` (ADR 0024 : pas de façade, pas de drapeau, pas de projection ; mêmes tables, mêmes URL). Le routage s'est fait par retrait des 3 handlers du legacy `SeanceRestController`, qui a disparu (plus aucune route).

## Fait
- Lot A (e366602d) : ADR 0095, domaine (`MoisDuTableauDeBord`, `SeancesAttendues`, `EffectifDuMois`, `FinDeLaPriseEnCharge`, `TrancheDAge`, `LigneDuDetail`, `FormatDExport`...), cas d'usage, ports, adapters JDBC / ACL / pilotage des absences / exports.
- Lot B (3de40eea) : `TableauDeBordDesSeancesControleur`, `ErreursDeSeanceAdvice` étendu (403 avant 400), retrait du legacy, verrous ArchUnit, goldens A4/PC1 toujours verts.
- Lot C : RG-SEA-059, annexe 16, glossaire (proposed), §60 des vérifications de production (SELECT seulement, rejoué sur PostgreSQL 16 jetable), bilan nominatif des résidus (ADR 0095).

## Parité
15/16 scénarios identiques au legacy (exports : mêmes octets). Écarts : 5 corrections KB-SEA-63 (500 devient 400 : `tableau/parametres`, mois 13, sans centre, année illisible, `kind` absent), approuvées une à une, À CONFIRMER métiers/DPO. KB-SEA-62, 64, 65, 66 reproduits et signalés ; KB-SEA-67, droits, libellés reproduits.

## Bascule
Aucun drapeau, aucun palier (ADR 0024). Rollback : revenir au commit précédent ; aucune donnée à restaurer.

## Reste à faire
Décisions écrites sur KB-SEA-62/64/65/66 et KB-SEA-63 avant mise en production ; résidus R1 à R7 de l'ADR 0095 (chacun avec revue au plus tard le 2026-12-15 et butoir propre).
