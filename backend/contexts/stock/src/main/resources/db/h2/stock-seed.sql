-- Amorçage H2 (poste de développement et tests) du jeu de démonstration du Stock (SEED2, ADR 0188) : articles, réceptions, lots,
-- mouvements d'entrée, lignes de bons de sortie, mouvements de sortie, recalcul de l'état de stock. Extrait, bloc à bloc et sans
-- changement de contenu, de db/seed.sql. Ce script ne lit QUE des tables du Stock.
-- ORDRE : chargé APRÈS db/seed.sql, qui crée les bons de sortie (bons_sortie) : ceux-là lisent les séances facturées et restent donc dans
-- seed.sql (une lecture de seances hors du contexte Séance n'est pas permise par TableDesSeancesTest) ; les lignes de ces bons, les
-- mouvements de sortie et le recalcul en dépendent, d'où leur place ici et non plus haut.

-- Articles stock: 18 par centre.
INSERT INTO articles (id, center_id, code, libelle, unite, stock_quantity, seuil_alerte, pmp_courant, gere_par_lot,
                      active, created_at)
SELECT CAST('3100' || LPAD(CAST(v AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST(v AS VARCHAR), 12, '0') AS UUID),
       '11111111-1111-1111-1111-111111111111',
       'ART-ANN-' || LPAD(CAST(v AS VARCHAR), 3, '0'),
       'Article Annaba ' || v,
       CASE WHEN MOD(v, 3) = 0 THEN 'L' WHEN MOD(v, 3) = 1 THEN 'UNITE' ELSE 'ML' END,
       0,
       80 + (v * 5),
       0,
       TRUE,
       TRUE,
       CURRENT_TIMESTAMP
FROM SYSTEM_RANGE(1, 18) n(v)
WHERE NOT EXISTS (SELECT 1
                  FROM articles a
                  WHERE a.id = CAST('3100' || LPAD(CAST(v AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
                                    LPAD(CAST(v AS VARCHAR), 12, '0') AS UUID));

INSERT INTO articles (id, center_id, code, libelle, unite, stock_quantity, seuil_alerte, pmp_courant, gere_par_lot,
                      active, created_at)
SELECT CAST('3200' || LPAD(CAST(v AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST(v AS VARCHAR), 12, '0') AS UUID),
       '22222222-2222-2222-2222-222222222222',
       'ART-SL-' || LPAD(CAST(v AS VARCHAR), 3, '0'),
       'Article Saint-Louis ' || v,
       CASE WHEN MOD(v, 3) = 0 THEN 'L' WHEN MOD(v, 3) = 1 THEN 'UNITE' ELSE 'ML' END,
       0,
       90 + (v * 5),
       0,
       TRUE,
       TRUE,
       CURRENT_TIMESTAMP
FROM SYSTEM_RANGE(1, 18) n(v)
WHERE NOT EXISTS (SELECT 1
                  FROM articles a
                  WHERE a.id = CAST('3200' || LPAD(CAST(v AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
                                    LPAD(CAST(v AS VARCHAR), 12, '0') AS UUID));

-- Receptions mensuelles sur 5 ans et lignes associees.
INSERT INTO bons_reception (id, center_id, reference, bon_commande_id, fournisseur_id, date_reception, statut,
                            created_by, created_at)
SELECT CAST('4100' || LPAD(CAST(v AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST(v AS VARCHAR), 12, '0') AS UUID),
       '11111111-1111-1111-1111-111111111111',
       'BR-ANN-PF-' || LPAD(CAST(v AS VARCHAR), 4, '0'),
       NULL,
       NULL,
       DATEADD('DAY', ((v - 1) * 30), DATE '2022-01-05'),
       'RECU',
       'seed-perf',
       CURRENT_TIMESTAMP
FROM SYSTEM_RANGE(1, 60) r(v)
WHERE NOT EXISTS (SELECT 1
                  FROM bons_reception br
                  WHERE br.id = CAST('4100' || LPAD(CAST(v AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
                                     LPAD(CAST(v AS VARCHAR), 12, '0') AS UUID));

INSERT INTO bons_reception (id, center_id, reference, bon_commande_id, fournisseur_id, date_reception, statut,
                            created_by, created_at)
SELECT CAST('4200' || LPAD(CAST(v AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST(v AS VARCHAR), 12, '0') AS UUID),
       '22222222-2222-2222-2222-222222222222',
       'BR-SL-PF-' || LPAD(CAST(v AS VARCHAR), 4, '0'),
       NULL,
       NULL,
       DATEADD('DAY', ((v - 1) * 30), DATE '2022-01-07'),
       'RECU',
       'seed-perf',
       CURRENT_TIMESTAMP
FROM SYSTEM_RANGE(1, 60) r(v)
WHERE NOT EXISTS (SELECT 1
                  FROM bons_reception br
                  WHERE br.id = CAST('4200' || LPAD(CAST(v AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
                                     LPAD(CAST(v AS VARCHAR), 12, '0') AS UUID));

INSERT INTO bons_reception_lignes (id, bon_reception_id, article_id, quantite, prix_unitaire, numero_lot,
                                   date_peremption, emplacement_id, lot_id)
SELECT CAST('5100' || LPAD(CAST((r.v * 10 + l.v) AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST((r.v * 10 + l.v) AS VARCHAR), 12, '0') AS UUID),
       CAST('4100' || LPAD(CAST(r.v AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST(r.v AS VARCHAR), 12, '0') AS UUID),
       CAST('3100' || LPAD(CAST((((r.v + l.v) % 18) + 1) AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST((((r.v + l.v) % 18) + 1) AS VARCHAR), 12, '0') AS UUID),
       120 + (l.v * 15),
       350 + MOD(r.v, 20) + (l.v * 4),
       'LOT-ANN-' || LPAD(CAST(r.v AS VARCHAR), 3, '0') || '-' || l.v,
       DATEADD('DAY', (r.v * 20), DATE '2026-12-31'),
       NULL,
       CAST('6100' || LPAD(CAST((r.v * 10 + l.v) AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST((r.v * 10 + l.v) AS VARCHAR), 12, '0') AS UUID)
FROM SYSTEM_RANGE(1, 60) r(v)
         CROSS JOIN SYSTEM_RANGE(1, 3) l(v)
WHERE NOT EXISTS (SELECT 1
                  FROM bons_reception_lignes brl
                  WHERE brl.id = CAST('5100' || LPAD(CAST((r.v * 10 + l.v) AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
                                      LPAD(CAST((r.v * 10 + l.v) AS VARCHAR), 12, '0') AS UUID));

INSERT INTO bons_reception_lignes (id, bon_reception_id, article_id, quantite, prix_unitaire, numero_lot,
                                   date_peremption, emplacement_id, lot_id)
SELECT CAST('5200' || LPAD(CAST((r.v * 10 + l.v) AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST((r.v * 10 + l.v) AS VARCHAR), 12, '0') AS UUID),
       CAST('4200' || LPAD(CAST(r.v AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST(r.v AS VARCHAR), 12, '0') AS UUID),
       CAST('3200' || LPAD(CAST((((r.v + l.v) % 18) + 1) AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST((((r.v + l.v) % 18) + 1) AS VARCHAR), 12, '0') AS UUID),
       140 + (l.v * 12),
       360 + MOD(r.v, 25) + (l.v * 5),
       'LOT-SL-' || LPAD(CAST(r.v AS VARCHAR), 3, '0') || '-' || l.v,
       DATEADD('DAY', (r.v * 21), DATE '2026-12-31'),
       NULL,
       CAST('6200' || LPAD(CAST((r.v * 10 + l.v) AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST((r.v * 10 + l.v) AS VARCHAR), 12, '0') AS UUID)
FROM SYSTEM_RANGE(1, 60) r(v)
         CROSS JOIN SYSTEM_RANGE(1, 3) l(v)
WHERE NOT EXISTS (SELECT 1
                  FROM bons_reception_lignes brl
                  WHERE brl.id = CAST('5200' || LPAD(CAST((r.v * 10 + l.v) AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
                                      LPAD(CAST((r.v * 10 + l.v) AS VARCHAR), 12, '0') AS UUID));

INSERT INTO lots (id, center_id, article_id, bon_reception_id, emplacement_id, numero_lot, date_peremption,
                  quantite_initiale, quantite_restante, pmp, created_at)
SELECT brl.lot_id,
       br.center_id,
       brl.article_id,
       br.id,
       NULL,
       brl.numero_lot,
       brl.date_peremption,
       brl.quantite,
       brl.quantite,
       brl.prix_unitaire,
       CURRENT_TIMESTAMP
FROM bons_reception_lignes brl
         JOIN bons_reception br ON br.id = brl.bon_reception_id
WHERE (br.reference LIKE 'BR-ANN-PF-%' OR br.reference LIKE 'BR-SL-PF-%')
  AND NOT EXISTS (SELECT 1 FROM lots l WHERE l.id = brl.lot_id);

INSERT INTO stock_movements (id, center_id, article_id, seance_id, lot_id, mouvement_type, quantite, prix_unitaire,
                             pmp_apres, created_by, created_at)
SELECT CAST('7100' || SUBSTRING(CAST(brl.id AS VARCHAR), 1, 4) || '-0000-0000-0000-' ||
            SUBSTRING(CAST(brl.id AS VARCHAR), 25, 12) AS UUID),
       br.center_id,
       brl.article_id,
       NULL,
       brl.lot_id,
       'ENTREE',
       brl.quantite,
       brl.prix_unitaire,
       brl.prix_unitaire,
       'seed-perf',
       -- date du mouvement = date de la pièce, 00:00 UTC (comme l'application) : sinon toutes les entrées de démo
       -- tombent le jour du démarrage et faussent PMP, valorisation et comptabilisation du stock de ce jour-là
       CAST(CAST(br.date_reception AS VARCHAR) || ' 00:00:00+00:00' AS TIMESTAMP WITH TIME ZONE)
FROM bons_reception_lignes brl
         JOIN bons_reception br ON br.id = brl.bon_reception_id
WHERE (br.reference LIKE 'BR-ANN-PF-%' OR br.reference LIKE 'BR-SL-PF-%')
  AND NOT EXISTS (SELECT 1
                  FROM stock_movements sm
                  WHERE sm.id = CAST('7100' || SUBSTRING(CAST(brl.id AS VARCHAR), 1, 4) || '-0000-0000-0000-' ||
                                     SUBSTRING(CAST(brl.id AS VARCHAR), 25, 12) AS UUID));

INSERT INTO bons_sortie_lignes (id, bon_sortie_id, article_id, lot_id, quantite, pmp_applique)
SELECT CAST('9101' || LPAD(CAST(z.rn AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST(z.rn AS VARCHAR), 12, '0') AS UUID),
       z.id,
       CAST('3100' || LPAD(CAST(MOD(z.rn, 18) + 1 AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST(MOD(z.rn, 18) + 1 AS VARCHAR), 12, '0') AS UUID),
       CAST('6100' || LPAD(CAST(MOD(z.rn, 180) + 1 AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST(MOD(z.rn, 180) + 1 AS VARCHAR), 12, '0') AS UUID),
       2 + MOD(z.rn, 4),
       380 + MOD(z.rn, 30)
FROM (SELECT bs.id, ROW_NUMBER() OVER (ORDER BY bs.date_sortie, bs.id) AS rn
      FROM bons_sortie bs
      WHERE bs.reference LIKE 'BS-ANN-PF-%') z
WHERE NOT EXISTS (SELECT 1
                  FROM bons_sortie_lignes l
                  WHERE l.id = CAST('9101' || LPAD(CAST(z.rn AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
                                    LPAD(CAST(z.rn AS VARCHAR), 12, '0') AS UUID));

INSERT INTO bons_sortie_lignes (id, bon_sortie_id, article_id, lot_id, quantite, pmp_applique)
SELECT CAST('9201' || LPAD(CAST(z.rn AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST(z.rn AS VARCHAR), 12, '0') AS UUID),
       z.id,
       CAST('3200' || LPAD(CAST(MOD(z.rn, 18) + 1 AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST(MOD(z.rn, 18) + 1 AS VARCHAR), 12, '0') AS UUID),
       CAST('6200' || LPAD(CAST(MOD(z.rn, 180) + 1 AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST(MOD(z.rn, 180) + 1 AS VARCHAR), 12, '0') AS UUID),
       2 + MOD(z.rn, 5),
       390 + MOD(z.rn, 35)
FROM (SELECT bs.id, ROW_NUMBER() OVER (ORDER BY bs.date_sortie, bs.id) AS rn
      FROM bons_sortie bs
      WHERE bs.reference LIKE 'BS-SL-PF-%') z
WHERE NOT EXISTS (SELECT 1
                  FROM bons_sortie_lignes l
                  WHERE l.id = CAST('9201' || LPAD(CAST(z.rn AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
                                    LPAD(CAST(z.rn AS VARCHAR), 12, '0') AS UUID));

INSERT INTO stock_movements (id, center_id, article_id, seance_id, lot_id, mouvement_type, quantite, prix_unitaire,
                             pmp_apres, created_by, created_at)
SELECT CAST('a101' || LPAD(CAST(z.rn AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST(z.rn AS VARCHAR), 12, '0') AS UUID),
       bs.center_id,
       z.article_id,
       bs.seance_id,
       z.lot_id,
       'SORTIE',
       z.quantite,
       z.pmp_applique,
       z.pmp_applique,
       'seed-perf',
       -- date du mouvement = date du bon de sortie (jour de la séance), 00:00 UTC, comme l'application
       CAST(CAST(bs.date_sortie AS VARCHAR) || ' 00:00:00+00:00' AS TIMESTAMP WITH TIME ZONE)
FROM (SELECT l.id,
             l.bon_sortie_id,
             l.article_id,
             l.lot_id,
             l.quantite,
             l.pmp_applique,
             ROW_NUMBER() OVER (ORDER BY l.id) AS rn
      FROM bons_sortie_lignes l
      WHERE l.id IN (
          SELECT id FROM bons_sortie_lignes WHERE CAST(id AS VARCHAR) LIKE '9101%' OR CAST(id AS VARCHAR) LIKE '9201%'
          )) z
         JOIN bons_sortie bs ON bs.id = z.bon_sortie_id
WHERE NOT EXISTS (SELECT 1
                  FROM stock_movements sm
                  WHERE sm.id = CAST('a101' || LPAD(CAST(z.rn AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
                                     LPAD(CAST(z.rn AS VARCHAR), 12, '0') AS UUID));

-- Recalcule un etat de stock coherent (entrees - sorties) sur 5 ans.
UPDATE lots l
SET quantite_restante = l.quantite_initiale
    - COALESCE((
                   SELECT SUM(m.quantite)
                   FROM stock_movements m
                   WHERE m.lot_id = l.id
                     AND m.mouvement_type = 'SORTIE'
                   ), 0)
WHERE l.numero_lot LIKE 'LOT-ANN-%'
   OR l.numero_lot LIKE 'LOT-SL-%';

UPDATE articles a
SET stock_quantity = COALESCE((
                                  SELECT SUM(CASE WHEN m.mouvement_type = 'ENTREE' THEN m.quantite ELSE -m.quantite END)
                                  FROM stock_movements m
                                  WHERE m.article_id = a.id
                                  ), 0),
    pmp_courant    = (
        SELECT AVG(m.prix_unitaire)
        FROM stock_movements m
        WHERE m.article_id = a.id
          AND m.mouvement_type = 'ENTREE'
        )
WHERE a.code LIKE 'ART-ANN-%'
   OR a.code LIKE 'ART-SL-%';
