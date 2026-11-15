-- JEU DÉRIVÉ MULTI-CONTEXTES de démonstration (SEED2, ADR 0189), module demo-data : séances, volets, relevés, médicaments, prescriptions,
-- analyses, consommables, bons de sortie, factures et leurs liaisons, tous engendrés par INSERT ... SELECT à partir des patients, des
-- séances ou des factures. Ce module n'est PAS un contexte : il compose les contextes, donc ses lectures inter-contextes sont légitimes.
-- Extrait, sans changement de contenu, de l'ancien db/seed.sql (l'identité y reste, côté plateforme).
-- ORDRE (application.yml spring.sql.init.data-locations) : APRÈS organisation, comptabilité, facturation, patient-couverture et db/seed.sql
-- (identité) ; AVANT gmao, documents et stock (dont les lignes de sortie lisent bons_sortie, créé ici).
-- ============================================================================
-- PERF DATASET (multi-centres, 5 ans): patients, seances, factures, stock
-- ============================================================================

-- facturation_settings : contexts/facturation (facturation-seed.sql) ; tva_types : contexts/comptabilite (comptabilite-seed.sql) ; SEED2.

-- Patients, assurés, attestations de droit et prises en charge (y compris ceux générés par INSERT ... SELECT) : contexts/patient-couverture
-- (db/h2/patient-couverture-seed.sql, SEED2, chargé avant ce script).

-- 120 seances par patient synthetique (~19k seances sur 5 ans, 2 centres).
INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at)
SELECT CAST('810' || LPAD(CAST(p.v AS VARCHAR), 2, '0') || LPAD(CAST(s.v AS VARCHAR), 3, '0') || '-1000-0000-0000-' ||
            LPAD(CAST((p.v * 1000 + s.v) AS VARCHAR), 12, '0') AS UUID),
       CAST('7100' || LPAD(CAST(p.v AS VARCHAR), 4, '0') || '-1000-0000-0000-' ||
            LPAD(CAST(p.v AS VARCHAR), 12, '0') AS UUID),
       '11111111-1111-1111-1111-111111111111',
       DATEADD('DAY', ((s.v - 1) * 15) + MOD(p.v, 5), DATE '2022-01-01'),
       CASE WHEN s.v <= 96 THEN 'FACTUREE' WHEN MOD(s.v, 3) = 0 THEN 'SIGNEE' ELSE 'VALIDEE' END,
       CURRENT_TIMESTAMP
FROM SYSTEM_RANGE(1, 80) p(v)
         CROSS JOIN SYSTEM_RANGE(1, 120) s(v)
WHERE NOT EXISTS (SELECT 1
                  FROM seances sx
                  WHERE sx.id =
                        CAST('810' || LPAD(CAST(p.v AS VARCHAR), 2, '0') || LPAD(CAST(s.v AS VARCHAR), 3, '0') ||
                             '-1000-0000-0000-' || LPAD(CAST((p.v * 1000 + s.v) AS VARCHAR), 12, '0') AS UUID));

INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at)
SELECT CAST('820' || LPAD(CAST(p.v AS VARCHAR), 2, '0') || LPAD(CAST(s.v AS VARCHAR), 3, '0') || '-2000-0000-0000-' ||
            LPAD(CAST((p.v * 1000 + s.v) AS VARCHAR), 12, '0') AS UUID),
       CAST('7200' || LPAD(CAST(p.v AS VARCHAR), 4, '0') || '-2000-0000-0000-' ||
            LPAD(CAST(p.v AS VARCHAR), 12, '0') AS UUID),
       '22222222-2222-2222-2222-222222222222',
       DATEADD('DAY', ((s.v - 1) * 15) + MOD(p.v, 4), DATE '2022-01-03'),
       CASE WHEN s.v <= 96 THEN 'FACTUREE' WHEN MOD(s.v, 4) = 0 THEN 'SIGNEE' ELSE 'VALIDEE' END,
       CURRENT_TIMESTAMP
FROM SYSTEM_RANGE(1, 80) p(v)
         CROSS JOIN SYSTEM_RANGE(1, 120) s(v)
WHERE NOT EXISTS (SELECT 1
                  FROM seances sx
                  WHERE sx.id =
                        CAST('820' || LPAD(CAST(p.v AS VARCHAR), 2, '0') || LPAD(CAST(s.v AS VARCHAR), 3, '0') ||
                             '-2000-0000-0000-' || LPAD(CAST((p.v * 1000 + s.v) AS VARCHAR), 12, '0') AS UUID));

-- Seances de couverture pour tout patient sans historique de seance.
INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at)
SELECT CAST('83' || LPAD(CAST(pw.rn AS VARCHAR), 4, '0') || LPAD(CAST(n.v AS VARCHAR), 2, '0') ||
            '-3000-0000-0000-' || LPAD(CAST((pw.rn * 100 + n.v) AS VARCHAR), 12, '0') AS UUID),
       pw.patient_id,
       pw.center_id,
       DATEADD('DAY', (n.v - 1) * 15, DATE '2025-01-01'),
       CASE WHEN n.v <= 8 THEN 'FACTUREE' ELSE 'VALIDEE' END,
       CURRENT_TIMESTAMP
FROM (SELECT p.id                                           AS patient_id,
             p.center_id,
             ROW_NUMBER() OVER (ORDER BY p.center_id, p.id) AS rn
      FROM patients p
      WHERE NOT EXISTS (SELECT 1 FROM seances s WHERE s.patient_id = p.id AND s.center_id = p.center_id)) pw
         CROSS JOIN SYSTEM_RANGE(1, 12) n(v)
WHERE NOT EXISTS (SELECT 1
                  FROM seances sx
                  WHERE
                      sx.id = CAST('83' || LPAD(CAST(pw.rn AS VARCHAR), 4, '0') || LPAD(CAST(n.v AS VARCHAR), 2, '0') ||
                                   '-3000-0000-0000-' || LPAD(CAST((pw.rn * 100 + n.v) AS VARCHAR), 12, '0') AS UUID));

-- Regle stricte: aucune seance sans attestation valide + PEC valide (patient non facturable => zero seance).
DELETE
FROM seances s
WHERE NOT EXISTS (
    SELECT 1
    FROM attestation_droit a
    WHERE a.patient_id = s.patient_id
  AND a.center_id = s.center_id
  AND s.date_seance >= a.date_debut
  AND (s.date_seance <= a.date_fin
   OR a.date_fin IS NULL)
    )
   OR NOT EXISTS (
    SELECT 1
    FROM prise_en_charge p
    WHERE p.patient_id = s.patient_id
  AND p.center_id = s.center_id
  AND p.statut = 'VALIDEE'
  AND COALESCE(p.forfait_effectif_id
    , p.forfait_demande_id) IS NOT NULL
  AND s.date_seance >= COALESCE(p.date_debut_effectif
    , p.date_debut_demande)
  AND (s.date_seance <= COALESCE(p.date_fin_effectif
    , p.date_fin_demande)
   OR COALESCE(p.date_fin_effectif
    , p.date_fin_demande) IS NULL)
    );

-- Donnees paramedicales completes par seance: poids, tension, UF et parametres associes.
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS poids_sec_cible_kg DECIMAL(5, 2);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS surcharge_hydrique_kg DECIMAL(5, 2);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS ta_systolique_avant SMALLINT;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS ta_diastolique_avant SMALLINT;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS fc_avant SMALLINT;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS temperature_avant DECIMAL(4, 1);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS etat_general_score SMALLINT;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS oedemes BOOLEAN DEFAULT FALSE;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS oedemes_localisation VARCHAR(255);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS dyspnee BOOLEAN DEFAULT FALSE;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS qb_ml_min SMALLINT;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS qd_ml_min SMALLINT;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS uf_cible_ml INTEGER;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS duree_prevue_min SMALLINT;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS type_dialyseur VARCHAR(100);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS type_bain VARCHAR(30);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS conductivite DECIMAL(4, 2);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS temperature_bain DECIMAL(4, 1);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS anticoag_type VARCHAR(20);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS anticoag_dose_initiale DECIMAL(8, 2);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS anticoag_dose_horaire DECIMAL(8, 2);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS nb_rincages SMALLINT;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS volume_rincage_ml INTEGER;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS heure_arret_heparine VARCHAR(10);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS abord_type VARCHAR(20);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS abord_cote VARCHAR(10);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS aiguille_calibre VARCHAR(20);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS ordre_ponction VARCHAR(20);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS aspect_site VARCHAR(30);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS incident_ponction BOOLEAN DEFAULT FALSE;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS incident_ponction_detail VARCHAR(500);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS ta_systolique_apres SMALLINT;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS ta_diastolique_apres SMALLINT;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS fc_apres SMALLINT;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS kt_v_realise DECIMAL(4, 2);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS uf_reelle_ml INTEGER;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS incident_hypotension BOOLEAN DEFAULT FALSE;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS incident_crampes BOOLEAN DEFAULT FALSE;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS incident_cephalees BOOLEAN DEFAULT FALSE;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS incident_frissons BOOLEAN DEFAULT FALSE;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS incident_nausees BOOLEAN DEFAULT FALSE;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS incident_thrombose BOOLEAN DEFAULT FALSE;
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS incident_autre VARCHAR(500);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS signature_infirmier_id VARCHAR(100);
ALTER TABLE volet_paramedical
    ADD COLUMN IF NOT EXISTS signature_at TIMESTAMP WITH TIME ZONE;

INSERT INTO volet_paramedical (id,
                               seance_id,
                               center_id,
                               poids_avant_kg,
                               poids_apres_kg,
                               poids_sec_cible_kg,
                               surcharge_hydrique_kg,
                               ta_avant,
                               ta_apres,
                               ta_systolique_avant,
                               ta_diastolique_avant,
                               ta_systolique_apres,
                               ta_diastolique_apres,
                               fc_avant,
                               fc_apres,
                               temperature_avant,
                               etat_general_score,
                               oedemes,
                               oedemes_localisation,
                               dyspnee,
                               duree_minutes,
                               duree_prevue_min,
                               debit_sang_ml_min,
                               qb_ml_min,
                               qd_ml_min,
                               ultrafiltration_ml,
                               uf_cible_ml,
                               uf_reelle_ml,
                               type_dialysat,
                               type_bain,
                               type_dialyseur,
                               conductivite,
                               temperature_bain,
                               anticoagulant,
                               anticoag_type,
                               anticoag_dose_initiale,
                               anticoag_dose_horaire,
                               nb_rincages,
                               volume_rincage_ml,
                               heure_arret_heparine,
                               abord_type,
                               abord_cote,
                               aiguille_calibre,
                               ordre_ponction,
                               aspect_site,
                               incident_ponction,
                               incident_ponction_detail,
                               kt_v_realise,
                               incident_hypotension,
                               incident_crampes,
                               incident_cephalees,
                               incident_frissons,
                               incident_nausees,
                               incident_thrombose,
                               incident_autre,
                               incidents,
                               signature_infirmier_id,
                               signature_at,
                               created_at,
                               updated_at)
SELECT CAST('91' || LPAD(CAST(o.rn AS VARCHAR), 6, '0') || '-' ||
            CASE WHEN o.center_id = '11111111-1111-1111-1111-111111111111' THEN '1000' ELSE '2000' END ||
            '-0000-0000-' || LPAD(CAST((o.rn * 17) AS VARCHAR), 12, '0') AS UUID),
       o.seance_id,
       o.center_id,
       o.poids_avant,
       o.poids_apres,
       o.poids_sec,
       o.surcharge,
       CAST(o.ta_sys_avant AS VARCHAR) || '/' || CAST(o.ta_dia_avant AS VARCHAR),
       CAST(o.ta_sys_apres AS VARCHAR) || '/' || CAST(o.ta_dia_apres AS VARCHAR),
       o.ta_sys_avant,
       o.ta_dia_avant,
       o.ta_sys_apres,
       o.ta_dia_apres,
       o.fc_avant,
       o.fc_apres,
       o.temperature_avant,
       o.etat_general_score,
       o.oedemes,
       o.oedemes_localisation,
       o.dyspnee,
       o.duree,
       o.duree,
       o.qb,
       o.qb,
       o.qd,
       CAST(o.uf_cible AS DECIMAL(10, 3)),
       o.uf_cible,
       o.uf_reelle,
       'Bicarbonate',
       CASE WHEN MOD(o.rn, 3) = 0 THEN 'Bicarbonate 34' ELSE 'Bicarbonate 32' END,
       CASE WHEN MOD(o.rn, 2) = 0 THEN 'FX80' ELSE 'FX60' END,
       CAST(13.6 + MOD(o.rn, 7) * 0.1 AS DECIMAL(4, 2)),
       CAST(36.5 + MOD(o.rn, 4) * 0.1 AS DECIMAL(4, 1)),
       CASE WHEN MOD(o.rn, 2) = 0 THEN 'Heparine' ELSE 'Enoxaparine' END,
       CASE WHEN MOD(o.rn, 2) = 0 THEN 'HEPARINE' ELSE 'HBPM' END,
       CAST(1600 + MOD(o.rn, 8) * 120 AS DECIMAL(8, 2)),
       CAST(500 + MOD(o.rn, 6) * 60 AS DECIMAL(8, 2)),
       2 + MOD(o.rn, 2),
       300 + MOD(o.rn, 3) * 100,
       CASE WHEN MOD(o.rn, 3) = 0 THEN '02:30' WHEN MOD(o.rn, 3) = 1 THEN '02:45' ELSE '03:00' END,
       CASE WHEN MOD(o.rn, 5) = 0 THEN 'CATHETER' ELSE 'FAV' END,
       CASE WHEN MOD(o.rn, 2) = 0 THEN 'GAUCHE' ELSE 'DROITE' END,
       CASE WHEN MOD(o.rn, 2) = 0 THEN '16G' ELSE '15G' END,
       CASE WHEN MOD(o.rn, 2) = 0 THEN 'ARTERE_VEINE' ELSE 'VEINE_ARTERE' END,
       CASE WHEN MOD(o.rn, 9) = 0 THEN 'HEMATOME' ELSE 'NORMAL' END,
       MOD(o.rn, 9) = 0,
       CASE WHEN MOD(o.rn, 9) = 0 THEN 'Ponction difficile avec petit hematome local' ELSE NULL END,
       CAST(1.10 + MOD(o.rn, 9) * 0.04 AS DECIMAL(4, 2)),
       MOD(o.rn, 15) = 0,
       MOD(o.rn, 18) = 0,
       MOD(o.rn, 22) = 0,
       MOD(o.rn, 27) = 0,
       MOD(o.rn, 31) = 0,
       MOD(o.rn, 35) = 0,
       CASE WHEN MOD(o.rn, 40) = 0 THEN 'Lombalgie transitoire' ELSE NULL END,
       CASE WHEN MOD(o.rn, 18) = 0 THEN 'Crampes moderees en fin de seance' ELSE 'Bonne tolerance globale' END,
       CASE WHEN MOD(o.rn, 2) = 0 THEN 'infirmier-annaba' ELSE 'infirmier-rouiba' END,
       DATEADD('MINUTE', 10, CAST(o.date_seance AS TIMESTAMP)),
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM (SELECT s.id                                                                                              AS seance_id,
             s.center_id,
             s.date_seance,
             ROW_NUMBER() OVER (ORDER BY s.center_id, s.patient_id, s.date_seance, s.id)                       AS rn,
             CAST(58 + MOD(ROW_NUMBER() OVER (ORDER BY s.center_id, s.patient_id, s.date_seance, s.id), 34)
                 + MOD(DAY_OF_MONTH(s.date_seance), 5) *
                   0.2 AS DECIMAL(5, 2))                                                                       AS poids_avant,
             CAST(56.5 + MOD(ROW_NUMBER() OVER (ORDER BY s.center_id, s.patient_id, s.date_seance, s.id), 34)
                 + MOD(DAY_OF_MONTH(s.date_seance), 5) *
                   0.2 AS DECIMAL(5, 2))                                                                       AS poids_apres,
             CAST(55.8 + MOD(ROW_NUMBER() OVER (ORDER BY s.center_id, s.patient_id, s.date_seance, s.id), 34)
                 *
                         1.0 AS DECIMAL(5, 2))                                                                 AS poids_sec,
             CAST(1.2 + MOD(ROW_NUMBER() OVER (ORDER BY s.center_id, s.patient_id, s.date_seance, s.id), 8) *
                        0.15 AS DECIMAL(5, 2))                                                                 AS surcharge,
             118 + MOD(ROW_NUMBER() OVER (ORDER BY s.center_id, s.patient_id, s.date_seance, s.id),
                       32)                                                                                     AS ta_sys_avant,
             68 + MOD(ROW_NUMBER() OVER (ORDER BY s.center_id, s.patient_id, s.date_seance, s.id),
                      18)                                                                                      AS ta_dia_avant,
             112 + MOD(ROW_NUMBER() OVER (ORDER BY s.center_id, s.patient_id, s.date_seance, s.id),
                       30)                                                                                     AS ta_sys_apres,
             66 + MOD(ROW_NUMBER() OVER (ORDER BY s.center_id, s.patient_id, s.date_seance, s.id),
                      16)                                                                                      AS ta_dia_apres,
             72 + MOD(ROW_NUMBER() OVER (ORDER BY s.center_id, s.patient_id, s.date_seance, s.id),
                      14)                                                                                      AS fc_avant,
             70 + MOD(ROW_NUMBER() OVER (ORDER BY s.center_id, s.patient_id, s.date_seance, s.id),
                      12)                                                                                      AS fc_apres,
             CAST(36.4 + MOD(ROW_NUMBER() OVER (ORDER BY s.center_id, s.patient_id, s.date_seance, s.id), 6) *
                         0.1 AS DECIMAL(4, 1))                                                                 AS temperature_avant,
             1 + MOD(ROW_NUMBER() OVER (ORDER BY s.center_id, s.patient_id, s.date_seance, s.id),
                     5)                                                                                        AS etat_general_score,
             MOD(ROW_NUMBER() OVER (ORDER BY s.center_id, s.patient_id, s.date_seance, s.id), 11) =
             0                                                                                                 AS oedemes,
             CASE
                 WHEN MOD(ROW_NUMBER() OVER (ORDER BY s.center_id, s.patient_id, s.date_seance, s.id), 11) = 0
                     THEN 'Malléoles'
                 ELSE NULL END                                                                                 AS oedemes_localisation,
             MOD(ROW_NUMBER() OVER (ORDER BY s.center_id, s.patient_id, s.date_seance, s.id), 17) =
             0                                                                                                 AS dyspnee,
             230 + MOD(ROW_NUMBER() OVER (ORDER BY s.center_id, s.patient_id, s.date_seance, s.id), 4) * 10    AS qb,
             500 + MOD(ROW_NUMBER() OVER (ORDER BY s.center_id, s.patient_id, s.date_seance, s.id), 5) * 20    AS qd,
             220 + MOD(ROW_NUMBER() OVER (ORDER BY s.center_id, s.patient_id, s.date_seance, s.id), 3) * 10    AS duree,
             1800 + MOD(ROW_NUMBER() OVER (ORDER BY s.center_id, s.patient_id, s.date_seance, s.id), 12) *
                    120                                                                                        AS uf_cible,
             1700 + MOD(ROW_NUMBER() OVER (ORDER BY s.center_id, s.patient_id, s.date_seance, s.id), 12) *
                    110                                                                                        AS uf_reelle
      FROM seances s) o
WHERE NOT EXISTS (SELECT 1 FROM volet_paramedical vp WHERE vp.seance_id = o.seance_id);

-- Releves per-seance (2 points de surveillance par seance).
CREATE TABLE IF NOT EXISTS releves_per_seance
(
    id                   UUID PRIMARY KEY,
    volet_paramedical_id UUID                     NOT NULL,
    center_id            UUID                     NOT NULL,
    heure_releve         VARCHAR(10)              NOT NULL,
    ta_systolique        SMALLINT,
    ta_diastolique       SMALLINT,
    fc                   SMALLINT,
    pression_veineuse    SMALLINT,
    pression_arterielle  SMALLINT,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO releves_per_seance (id,
                                volet_paramedical_id,
                                center_id,
                                heure_releve,
                                ta_systolique,
                                ta_diastolique,
                                fc,
                                pression_veineuse,
                                pression_arterielle,
                                created_at)
SELECT CAST('92' || LPAD(CAST(b.rn AS VARCHAR), 6, '0') ||
            '-3' || LPAD(CAST(t.v AS VARCHAR), 3, '0') ||
            '-0000-0000-' || LPAD(CAST((b.rn * 10 + t.v) AS VARCHAR), 12, '0') AS UUID),
       b.volet_id,
       b.center_id,
       CASE WHEN t.v = 1 THEN '01:30' ELSE '03:00' END,
       b.ta_sys_avant - CASE WHEN t.v = 1 THEN 0 ELSE 6 END,
       b.ta_dia_avant - CASE WHEN t.v = 1 THEN 0 ELSE 4 END,
       b.fc_avant - CASE WHEN t.v = 1 THEN 0 ELSE 3 END,
       140 + MOD(b.rn + t.v, 40),
       -160 - MOD(b.rn + t.v, 45),
       CURRENT_TIMESTAMP
FROM (SELECT vp.id                                                   AS volet_id,
             vp.center_id,
             ROW_NUMBER() OVER (ORDER BY vp.center_id, vp.seance_id) AS rn,
             COALESCE(vp.ta_systolique_avant, 120)                   AS ta_sys_avant,
             COALESCE(vp.ta_diastolique_avant, 70)                   AS ta_dia_avant,
             COALESCE(vp.fc_avant, 75)                               AS fc_avant
      FROM volet_paramedical vp) b
         CROSS JOIN SYSTEM_RANGE(1, 2) t(v)
WHERE NOT EXISTS (SELECT 1
                  FROM releves_per_seance r
                  WHERE r.volet_paramedical_id = b.volet_id
                    AND r.heure_releve = CASE WHEN t.v = 1 THEN '01:30' ELSE '03:00' END);

-- Medicaments injectes en fin de seance.
CREATE TABLE IF NOT EXISTS medicaments_seance
(
    id                   UUID PRIMARY KEY,
    volet_paramedical_id UUID                     NOT NULL,
    center_id            UUID                     NOT NULL,
    nom_medicament       VARCHAR(255)             NOT NULL,
    dose                 VARCHAR(100),
    voie                 VARCHAR(50),
    heure_injection      VARCHAR(10),
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO medicaments_seance (id,
                                volet_paramedical_id,
                                center_id,
                                nom_medicament,
                                dose,
                                voie,
                                heure_injection,
                                created_at)
SELECT CAST('93' || LPAD(CAST(m.rn AS VARCHAR), 6, '0') ||
            '-3000-0000-0000-' || LPAD(CAST(m.rn AS VARCHAR), 12, '0') AS UUID),
       m.volet_id,
       m.center_id,
       CASE WHEN MOD(m.rn, 3) = 0 THEN 'EPO alfa' WHEN MOD(m.rn, 3) = 1 THEN 'Fer saccharose' ELSE 'Vitamine D' END,
       CASE WHEN MOD(m.rn, 3) = 0 THEN '4000 UI' WHEN MOD(m.rn, 3) = 1 THEN '100 mg' ELSE '1 ampoule' END,
       CASE WHEN MOD(m.rn, 2) = 0 THEN 'IV' ELSE 'SC' END,
       CASE WHEN MOD(m.rn, 2) = 0 THEN '03:15' ELSE '03:25' END,
       CURRENT_TIMESTAMP
FROM (SELECT vp.id                                                   AS volet_id,
             vp.center_id,
             ROW_NUMBER() OVER (ORDER BY vp.center_id, vp.seance_id) AS rn
      FROM volet_paramedical vp) m
WHERE NOT EXISTS (SELECT 1 FROM medicaments_seance ms WHERE ms.volet_paramedical_id = m.volet_id);

-- Prescriptions medicales periodiques pour tous les patients.
CREATE TABLE IF NOT EXISTS prescriptions_medicales
(
    id                      UUID PRIMARY KEY,
    patient_id              UUID                     NOT NULL,
    center_id               UUID                     NOT NULL,
    date_prescription       DATE                     NOT NULL,
    medecin_id              VARCHAR(100),
    qb_cible                SMALLINT,
    qd_cible                SMALLINT,
    uf_max_ml               INTEGER,
    duree_cible_min         SMALLINT,
    type_dialyseur_prescrit VARCHAR(100),
    anticoag_type_prescrit  VARCHAR(20),
    epo_article_id       UUID,
    epo_dose_ui             INTEGER,
    epo_voie                VARCHAR(10),
    epo_frequence_valeur INTEGER,
    epo_frequence_unite  VARCHAR(10),
    fer_article_id       UUID,
    fer_dose_mg             INTEGER,
    fer_voie                VARCHAR(10),
    fer_frequence_valeur INTEGER,
    fer_frequence_unite  VARCHAR(10),
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE
);

INSERT INTO prescriptions_medicales (id,
                                     patient_id,
                                     center_id,
                                     date_prescription,
                                     medecin_id,
                                     qb_cible,
                                     qd_cible,
                                     uf_max_ml,
                                     duree_cible_min,
                                     type_dialyseur_prescrit,
                                     anticoag_type_prescrit,
                                     epo_article_id,
                                     epo_dose_ui,
                                     epo_voie,
                                     epo_frequence_valeur,
                                     epo_frequence_unite,
                                     fer_article_id,
                                     fer_dose_mg,
                                     fer_voie,
                                     fer_frequence_valeur,
                                     fer_frequence_unite,
                                     created_at,
                                     updated_at)
SELECT CAST('94' || LPAD(CAST(pp.rn AS VARCHAR), 6, '0') ||
            '-4' || LPAD(CAST(m.v AS VARCHAR), 3, '0') ||
            '-0000-0000-' || LPAD(CAST((pp.rn * 100 + m.v) AS VARCHAR), 12, '0') AS UUID),
       pp.patient_id,
       pp.center_id,
       DATEADD('MONTH', m.v - 1, DATE '2025-01-01'),
       CASE
           WHEN pp.center_id = '11111111-1111-1111-1111-111111111111'
               THEN 'e1000001-0000-0000-0000-000000000001'
           ELSE 'e1000002-0000-0000-0000-000000000001' END,
       220 + MOD(pp.rn + m.v, 4) * 10,
       500 + MOD(pp.rn + m.v, 5) * 20,
       2200 + MOD(pp.rn + m.v, 8) * 150,
       240,
       CASE WHEN MOD(pp.rn + m.v, 2) = 0 THEN 'FX80' ELSE 'FX60' END,
       CASE WHEN MOD(pp.rn + m.v, 2) = 0 THEN 'HEPARINE' ELSE 'HBPM' END,
       NULL,
       4000 + MOD(pp.rn + m.v, 4) * 1000,
       'SC',
       CASE WHEN MOD(pp.rn + m.v, 2) = 0 THEN 3 ELSE 2 END,
       'SEMAINE',
       NULL,
       100 + MOD(pp.rn + m.v, 3) * 50,
       'IV',
       CASE WHEN MOD(pp.rn + m.v, 2) = 0 THEN 1 ELSE 2 END,
       CASE WHEN MOD(pp.rn + m.v, 2) = 0 THEN 'SEMAINE' ELSE 'MOIS' END,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM (SELECT p.id                                           AS patient_id,
             p.center_id,
             ROW_NUMBER() OVER (ORDER BY p.center_id, p.id) AS rn
      FROM patients p) pp
         CROSS JOIN SYSTEM_RANGE(1, 12) m(v)
WHERE NOT EXISTS (SELECT 1
                  FROM prescriptions_medicales pm
                  WHERE pm.id = CAST('94' || LPAD(CAST(pp.rn AS VARCHAR), 6, '0') ||
                                     '-4' || LPAD(CAST(m.v AS VARCHAR), 3, '0') ||
                                     '-0000-0000-' || LPAD(CAST((pp.rn * 100 + m.v) AS VARCHAR), 12, '0') AS UUID));

-- Autres traitements associes aux prescriptions.
CREATE TABLE IF NOT EXISTS autres_traitements_prescription
(
    id              UUID PRIMARY KEY,
    prescription_id UUID                     NOT NULL,
    nom_medicament  VARCHAR(255)             NOT NULL,
    dose            VARCHAR(100),
    voie            VARCHAR(50),
    frequence       VARCHAR(50),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO autres_traitements_prescription (id,
                                             prescription_id,
                                             nom_medicament,
                                             dose,
                                             voie,
                                             frequence,
                                             created_at)
SELECT CAST('97' || LPAD(CAST(pt.rn AS VARCHAR), 6, '0') ||
            '-7000-0000-0000-' || LPAD(CAST(pt.rn AS VARCHAR), 12, '0') AS UUID),
       pt.prescription_id,
       CASE WHEN MOD(pt.rn, 2) = 0 THEN 'Calcium carbonate' ELSE 'Vitamine B complex' END,
       CASE WHEN MOD(pt.rn, 2) = 0 THEN '500 mg' ELSE '1 cp' END,
       'PO',
       CASE WHEN MOD(pt.rn, 2) = 0 THEN '2x/jour' ELSE '1x/jour' END,
       CURRENT_TIMESTAMP
FROM (SELECT pm.id                                                                                 AS prescription_id,
             ROW_NUMBER() OVER (ORDER BY pm.center_id, pm.patient_id, pm.date_prescription, pm.id) AS rn
      FROM prescriptions_medicales pm
      WHERE MOD(MONTH(pm.date_prescription), 2) = 0) pt
WHERE NOT EXISTS (SELECT 1 FROM autres_traitements_prescription atp WHERE atp.prescription_id = pt.prescription_id);

-- Resultats d'analyses periodiques pour tous les patients.
CREATE TABLE IF NOT EXISTS resultats_analyses
(
    id                  UUID PRIMARY KEY,
    patient_id          UUID                     NOT NULL,
    center_id           UUID                     NOT NULL,
    date_prelevement    DATE                     NOT NULL,
    hb_g_dl             DECIMAL(4, 1),
    ht_pct              DECIMAL(5, 2),
    plaquettes          INTEGER,
    ferritine_ng_ml     DECIMAL(8, 1),
    cstf_pct            DECIMAL(5, 2),
    epo_endogene_mui_ml DECIMAL(8, 2),
    uree_pre_mg_dl      DECIMAL(7, 2),
    uree_post_mg_dl     DECIMAL(7, 2),
    creatinine_mg_dl    DECIMAL(7, 2),
    kt_v_mensuel        DECIMAL(4, 2),
    phosphore_mg_dl     DECIMAL(6, 2),
    calcium_mg_dl       DECIMAL(6, 2),
    pth_pg_ml           DECIMAL(8, 1),
    albumine_g_dl       DECIMAL(4, 1),
    proteines_g_dl      DECIMAL(4, 1),
    crp_mg_l            DECIMAL(7, 2),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE
);

INSERT INTO resultats_analyses (id,
                                patient_id,
                                center_id,
                                date_prelevement,
                                hb_g_dl,
                                ht_pct,
                                plaquettes,
                                ferritine_ng_ml,
                                cstf_pct,
                                epo_endogene_mui_ml,
                                uree_pre_mg_dl,
                                uree_post_mg_dl,
                                creatinine_mg_dl,
                                kt_v_mensuel,
                                phosphore_mg_dl,
                                calcium_mg_dl,
                                pth_pg_ml,
                                albumine_g_dl,
                                proteines_g_dl,
                                crp_mg_l,
                                created_at,
                                updated_at)
SELECT CAST('95' || LPAD(CAST(pa.rn AS VARCHAR), 6, '0') ||
            '-5' || LPAD(CAST(m.v AS VARCHAR), 3, '0') ||
            '-0000-0000-' || LPAD(CAST((pa.rn * 100 + m.v) AS VARCHAR), 12, '0') AS UUID),
       pa.patient_id,
       pa.center_id,
       DATEADD('MONTH', m.v - 1, DATE '2024-01-15'),
       CAST(9.8 + MOD(pa.rn + m.v, 10) * 0.25 AS DECIMAL(4, 1)),
       CAST(30 + MOD(pa.rn + m.v, 8) * 1.4 AS DECIMAL(5, 2)),
       170000 + MOD(pa.rn + m.v, 12) * 8000,
       CAST(180 + MOD(pa.rn + m.v, 14) * 22 AS DECIMAL(8, 1)),
       CAST(19 + MOD(pa.rn + m.v, 8) * 1.7 AS DECIMAL(5, 2)),
       CAST(8 + MOD(pa.rn + m.v, 6) * 0.9 AS DECIMAL(8, 2)),
       CAST(120 + MOD(pa.rn + m.v, 12) * 4 AS DECIMAL(7, 2)),
       CAST(42 + MOD(pa.rn + m.v, 10) * 2 AS DECIMAL(7, 2)),
       CAST(8.5 + MOD(pa.rn + m.v, 9) * 0.4 AS DECIMAL(7, 2)),
       CAST(1.05 + MOD(pa.rn + m.v, 9) * 0.05 AS DECIMAL(4, 2)),
       CAST(4.1 + MOD(pa.rn + m.v, 7) * 0.2 AS DECIMAL(6, 2)),
       CAST(8.2 + MOD(pa.rn + m.v, 6) * 0.25 AS DECIMAL(6, 2)),
       CAST(180 + MOD(pa.rn + m.v, 10) * 20 AS DECIMAL(8, 1)),
       CAST(3.4 + MOD(pa.rn + m.v, 5) * 0.15 AS DECIMAL(4, 1)),
       CAST(6.0 + MOD(pa.rn + m.v, 4) * 0.20 AS DECIMAL(4, 1)),
       CAST(2.0 + MOD(pa.rn + m.v, 8) * 0.35 AS DECIMAL(7, 2)),
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM (SELECT p.id                                           AS patient_id,
             p.center_id,
             ROW_NUMBER() OVER (ORDER BY p.center_id, p.id) AS rn
      FROM patients p) pa
         CROSS JOIN SYSTEM_RANGE(1, 24) m(v)
WHERE NOT EXISTS (SELECT 1
                  FROM resultats_analyses ra
                  WHERE ra.id = CAST('95' || LPAD(CAST(pa.rn AS VARCHAR), 6, '0') ||
                                     '-5' || LPAD(CAST(m.v AS VARCHAR), 3, '0') ||
                                     '-0000-0000-' || LPAD(CAST((pa.rn * 100 + m.v) AS VARCHAR), 12, '0') AS UUID));

-- Articles stock : contexts/stock (db/h2/stock-seed.sql, SEED2).

-- Consommables par seance (1 ligne par volet paramedical).
CREATE TABLE IF NOT EXISTS articles_consommes_seance
(
    id                   UUID PRIMARY KEY,
    volet_paramedical_id UUID                     NOT NULL,
    center_id            UUID                     NOT NULL,
    article_id           UUID                     NOT NULL,
    quantite             DECIMAL(10, 3)           NOT NULL,
    lot                  VARCHAR(100),
    stock_mouvement_id   UUID,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO articles_consommes_seance (id,
                                       volet_paramedical_id,
                                       center_id,
                                       article_id,
                                       quantite,
                                       lot,
                                       stock_mouvement_id,
                                       created_at)
SELECT CAST('96' || LPAD(CAST(c.rn AS VARCHAR), 6, '0') ||
            '-6000-0000-0000-' || LPAD(CAST(c.rn AS VARCHAR), 12, '0') AS UUID),
       c.volet_id,
       c.center_id,
       c.article_id,
       CAST(1 + MOD(c.rn, 4) * 0.5 AS DECIMAL(10, 3)),
       CASE
           WHEN c.center_id = '11111111-1111-1111-1111-111111111111'
               THEN 'LOT-ANN-SEANCE-' || LPAD(CAST(MOD(c.rn, 500) + 1 AS VARCHAR), 4, '0')
           ELSE 'LOT-SL-SEANCE-' || LPAD(CAST(MOD(c.rn, 500) + 1 AS VARCHAR), 4, '0') END,
       NULL,
       CURRENT_TIMESTAMP
FROM (SELECT vp.id                                                   AS volet_id,
             vp.center_id,
             ROW_NUMBER() OVER (ORDER BY vp.center_id, vp.seance_id) AS rn,
             CASE
                 WHEN vp.center_id = '11111111-1111-1111-1111-111111111111'
                     THEN CAST('3100' || LPAD(
                         CAST((MOD(ROW_NUMBER() OVER (ORDER BY vp.center_id, vp.seance_id), 18) + 1) AS VARCHAR), 4,
                         '0') ||
                               '-0000-0000-0000-' ||
                               LPAD(
                                       CAST((MOD(ROW_NUMBER() OVER (ORDER BY vp.center_id, vp.seance_id), 18) + 1) AS VARCHAR),
                                       12, '0') AS UUID)
                 ELSE CAST('3200' ||
                           LPAD(CAST((MOD(ROW_NUMBER() OVER (ORDER BY vp.center_id, vp.seance_id), 18) + 1) AS VARCHAR),
                                4, '0') ||
                           '-0000-0000-0000-' ||
                           LPAD(CAST((MOD(ROW_NUMBER() OVER (ORDER BY vp.center_id, vp.seance_id), 18) + 1) AS VARCHAR),
                                12, '0') AS UUID)
                 END                                                 AS article_id
      FROM volet_paramedical vp) c
WHERE NOT EXISTS (SELECT 1 FROM articles_consommes_seance acs WHERE acs.volet_paramedical_id = c.volet_id);

-- Réceptions, lots et mouvements d'entrée : contexts/stock (db/h2/stock-seed.sql, SEED2).

-- Sorties liees aux seances facturees (2 500 par centre).
INSERT INTO bons_sortie (id, center_id, reference, seance_id, patient_id, poste, date_sortie, created_by, created_at)
SELECT CAST('8101' || LPAD(CAST(z.rn AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST(z.rn AS VARCHAR), 12, '0') AS UUID),
       z.center_id,
       'BS-ANN-PF-' || LPAD(CAST(z.rn AS VARCHAR), 5, '0'),
       z.id,
       z.patient_id,
       'POSTE-' || MOD(z.rn, 24),
       z.date_seance,
       'seed-perf',
       CURRENT_TIMESTAMP
FROM (SELECT s.id,
             s.center_id,
             s.patient_id,
             s.date_seance,
             ROW_NUMBER() OVER (ORDER BY s.date_seance, s.id) AS rn
      FROM seances s
      WHERE s.center_id = '11111111-1111-1111-1111-111111111111'
        AND s.statut = 'FACTUREE') z
WHERE z.rn <= 2500
  AND NOT EXISTS (SELECT 1
                  FROM bons_sortie bs
                  WHERE bs.id = CAST('8101' || LPAD(CAST(z.rn AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
                                     LPAD(CAST(z.rn AS VARCHAR), 12, '0') AS UUID));

INSERT INTO bons_sortie (id, center_id, reference, seance_id, patient_id, poste, date_sortie, created_by, created_at)
SELECT CAST('8201' || LPAD(CAST(z.rn AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST(z.rn AS VARCHAR), 12, '0') AS UUID),
       z.center_id,
       'BS-SL-PF-' || LPAD(CAST(z.rn AS VARCHAR), 5, '0'),
       z.id,
       z.patient_id,
       'POSTE-' || MOD(z.rn, 24),
       z.date_seance,
       'seed-perf',
       CURRENT_TIMESTAMP
FROM (SELECT s.id,
             s.center_id,
             s.patient_id,
             s.date_seance,
             ROW_NUMBER() OVER (ORDER BY s.date_seance, s.id) AS rn
      FROM seances s
      WHERE s.center_id = '22222222-2222-2222-2222-222222222222'
        AND s.statut = 'FACTUREE') z
WHERE z.rn <= 2500
  AND NOT EXISTS (SELECT 1
                  FROM bons_sortie bs
                  WHERE bs.id = CAST('8201' || LPAD(CAST(z.rn AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
                                     LPAD(CAST(z.rn AS VARCHAR), 12, '0') AS UUID));

-- Les lignes de ces bons de sortie et les mouvements de sortie sont dans contexts/stock (db/h2/stock-seed.sql, SEED2, chargé après ce script).

-- Factures mensuelles basees sur les seances facturees.
INSERT INTO factures (id, center_id, patient_id, numero_facture,
                      patient_code, patient_full_name, patient_status_snapshot,
                      numero_immatriculation_snapshot, centre_payeur_id_snapshot, agence_id_snapshot,
                      period_start, period_end, date_facturation,
                      tva_rate, total_ht, total_tva, total_ttc, created_at)
SELECT CAST(
               CASE WHEN q.center_id = '11111111-1111-1111-1111-111111111111' THEN 'b101' ELSE 'b201' END
                   || LPAD(CAST(q.rn AS VARCHAR), 4, '0')
                   || '-0000-0000-0000-'
                   || LPAD(CAST(q.rn AS VARCHAR), 12, '0')
           AS UUID
       ),
       q.center_id,
       q.patient_id,
       'AUTO-' || CAST(q.yyyy AS VARCHAR) || '-' || LPAD(CAST(q.mm AS VARCHAR), 2, '0') || '-'
           || CASE WHEN q.center_id = '11111111-1111-1111-1111-111111111111' THEN 'ANN-' ELSE 'SL-' END
           || LPAD(CAST(q.rn AS VARCHAR), 6, '0'),
       q.code_patient,
       q.patient_full_name,
       q.etat_patient,
       q.numero_assurance,
       q.centre_payeur_id,
       NULL,
       CAST(CAST(q.yyyy AS VARCHAR) || '-' || LPAD(CAST(q.mm AS VARCHAR), 2, '0') || '-01' AS DATE),
       CAST(CAST(q.yyyy AS VARCHAR) || '-' || LPAD(CAST(q.mm AS VARCHAR), 2, '0') || '-28' AS DATE),
       q.max_date,
       CASE WHEN q.center_id = '11111111-1111-1111-1111-111111111111' THEN 19.00 ELSE 18.00 END,
       CAST(q.seance_count * CASE
                                 WHEN q.center_id = '11111111-1111-1111-1111-111111111111' THEN 5000.00
                                 ELSE 5200.00 END AS DECIMAL(14, 2)),
       CAST((q.seance_count *
             CASE WHEN q.center_id = '11111111-1111-1111-1111-111111111111' THEN 5000.00 ELSE 5200.00 END)
                * (CASE WHEN q.center_id = '11111111-1111-1111-1111-111111111111' THEN 19.00 ELSE 18.00 END) /
            100 AS DECIMAL(14, 2)),
       CAST((q.seance_count *
             CASE WHEN q.center_id = '11111111-1111-1111-1111-111111111111' THEN 5000.00 ELSE 5200.00 END)
           * (1 + (CASE WHEN q.center_id = '11111111-1111-1111-1111-111111111111' THEN 19.00 ELSE 18.00 END) /
                  100) AS DECIMAL(14, 2)),
       CURRENT_TIMESTAMP
FROM (
         WITH billed AS (
             SELECT
                 s.center_id,
                 s.patient_id,
                 EXTRACT(YEAR FROM s.date_seance) AS yyyy,
                 EXTRACT(MONTH FROM s.date_seance) AS mm,
                 COUNT(*) AS seance_count,
                 MIN(s.date_seance) AS min_date,
                 MAX(s.date_seance) AS max_date
             FROM seances s
             WHERE s.statut = 'FACTUREE'
               AND s.date_seance >= DATE '2022-01-01'
             GROUP BY s.center_id, s.patient_id, EXTRACT(YEAR FROM s.date_seance), EXTRACT(MONTH FROM s.date_seance)
         ), ranked AS (
             SELECT
                 b.*,
                 ROW_NUMBER() OVER (PARTITION BY b.center_id ORDER BY b.yyyy, b.mm, b.patient_id) AS rn
             FROM billed b
         )
         SELECT r.*, p.code_patient, (p.nom || ' ' || p.prenom) AS patient_full_name, p.etat_patient, p.numero_assurance, p.centre_payeur_id
         FROM ranked r
                  JOIN patients p ON p.id = r.patient_id AND p.center_id = r.center_id
         ) q
WHERE q.yyyy >= 2022
  AND NOT EXISTS (SELECT 1
                  FROM factures f
                  WHERE f.center_id = q.center_id
                    AND f.numero_facture =
                        'AUTO-' || CAST(q.yyyy AS VARCHAR) || '-' || LPAD(CAST(q.mm AS VARCHAR), 2, '0') || '-'
                            || CASE WHEN q.center_id = '11111111-1111-1111-1111-111111111111' THEN 'ANN-' ELSE 'SL-' END
                            || LPAD(CAST(q.rn AS VARCHAR), 6, '0'));

INSERT INTO facture_lignes (id, facture_id, center_id, forfait_id, forfait_label, unit_price_ht, seance_count, line_ht)
SELECT CAST(
               CASE WHEN f.center_id = '11111111-1111-1111-1111-111111111111' THEN 'c101' ELSE 'c201' END
                   || LPAD(CAST(ROW_NUMBER() OVER (ORDER BY f.id) AS VARCHAR), 4, '0')
                   || '-0000-0000-0000-'
                   || LPAD(CAST(ROW_NUMBER() OVER (ORDER BY f.id) AS VARCHAR), 12, '0')
           AS UUID
       ),
       f.id,
       f.center_id,
       CASE
           WHEN f.center_id = '11111111-1111-1111-1111-111111111111' THEN 'f0000001-0000-0000-0000-000000000001'
           ELSE 'f0000002-0000-0000-0000-000000000001' END,
       CASE
           WHEN f.center_id = '11111111-1111-1111-1111-111111111111' THEN 'Forfait Standard'
           ELSE 'Forfait Standard SL' END,
       CASE WHEN f.center_id = '11111111-1111-1111-1111-111111111111' THEN 5000.00 ELSE 5200.00 END,
       CAST(ROUND(f.total_ht /
                  (CASE WHEN f.center_id = '11111111-1111-1111-1111-111111111111' THEN 5000.00 ELSE 5200.00 END),
                  0) AS INTEGER),
       f.total_ht
FROM factures f
WHERE f.numero_facture LIKE 'AUTO-%'
  AND NOT EXISTS (SELECT 1 FROM facture_lignes fl WHERE fl.facture_id = f.id);

UPDATE seances s
SET facture_id = (
    SELECT f.id
    FROM factures f
    WHERE f.center_id = s.center_id
      AND f.patient_id = s.patient_id
      AND EXTRACT(YEAR FROM f.period_start) = EXTRACT(YEAR FROM s.date_seance)
      AND EXTRACT(MONTH FROM f.period_start) = EXTRACT(MONTH FROM s.date_seance)
      AND f.numero_facture LIKE 'AUTO-%'
        FETCH FIRST 1 ROW ONLY
    )
WHERE s.statut = 'FACTUREE'
  AND s.facture_id IS NULL;

-- Le recalcul de l'état de stock (UPDATE lots / articles) est dans contexts/stock (db/h2/stock-seed.sql, SEED2).

-- Les compteurs de numérotation des pièces (app_settings) sont amorcés par contexts/comptabilite (db/h2/comptabilite-seed.sql, S-DDL2b).




