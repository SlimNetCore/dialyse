-- =====================================================================================================================
--  JEU DE DONNÃ‰ES COMPLET â€” SOCIÃ‰TÃ‰ RENADIAL (17 centres) â€” PostgreSQL 16
-- ---------------------------------------------------------------------------------------------------------------------
--  Couvre les 77 tables du schÃ©ma de production (84.235.227.26, base Â« hemodialyse Â»).
--
--  Contenu gÃ©nÃ©rÃ© (paramÃ©trable dans _cfg) :
--    â€¢ 1 sociÃ©tÃ© RENADIAL + 17 centres (ROUIBA â€¦ TLEMCEN 2), calendrier (jours fÃ©riÃ©s, fermetures)
--    â€¢ RÃ©fÃ©rentiels par centre : caisses, agences, centres payeurs, mÃ©decins, salles, gÃ©nÃ©rateurs, crÃ©neaux,
--      transporteurs, catÃ©gories de transport, 5 forfaits, modÃ¨les de documents, paramÃ¨tres facturation/TVA/compta
--    â€¢ Comptes utilisateurs (SUPERADMIN, DIRECTION, ADMIN rÃ©seau + 6 comptes par centre)
--    â€¢ 30 patients par centre (510), chacun avec : assurÃ©, 1 attestation, 1 prise en charge, dossier mÃ©dical complet
--      (antÃ©cÃ©dents, allergie, sÃ©rologies, abords vasculaires, prescriptions, bilans mensuels, observations LOINC,
--      demandes d'examens, ordonnances, anÃ©mie EPO/fer, alertes d'observance, bilan prÃ©-greffe pour les Ã©ligibles)
--    â€¢ SÃ©ances sur 12 mois glissants + mois en cours (volets paramÃ©dical et mÃ©dical, bons de sortie, consommations)
--    â€¢ Facturation mensuelle des 12 mois clos (factures, lignes multi-forfaits, sÃ©quences, rÃ¨glements)
--    â€¢ Stock (fournisseurs, emplacements, articles, BC, BR, lots FEFO, mouvements, PMP)
--    â€¢ ComptabilitÃ© SCF (mapping, pÃ©riodes, Ã©critures VE/BQ + lignes), pilotage direction (instantanÃ©s, alertes),
--      journal d'audit, jetons de rafraÃ®chissement, enrÃ´lement MFA
--
--  Dates relatives Ã  la date d'exÃ©cution : pÃ©riode = 1er jour du mois courant âˆ’ 12 mois â†’ aujourd'hui.
--  Identifiants dÃ©terministes (md5) : deux exÃ©cutions (aprÃ¨s purge) produisent les mÃªmes UUID ; la sociÃ©tÃ© et le
--  centre ROUIBA reprennent les UUID existants en production.
--
--  Mot de passe de TOUS les comptes : valeur de _cfg.mot_de_passe (par dÃ©faut Â« Renadial@2026! Â») â†’ Ã€ CHANGER.
--
--  LICENCES : la table Â« license Â» n'est volontairement PAS alimentÃ©e. Une licence est un jeton RS256 signÃ© par la
--  clÃ© privÃ©e de l'autoritÃ© : elle ne peut pas Ãªtre fabriquÃ©e en SQL. AprÃ¨s chargement, se connecter en
--  Â« superadmin Â» et Ã©mettre une licence par centre (Ã©cran Licences) â€” les UUID de centres Ã©tant dÃ©terministes,
--  les licences restent valides aprÃ¨s une purge + rechargement si la table license est sauvegardÃ©e/restaurÃ©e.
--
--  PrÃ©-requis : base vide (exÃ©cuter 00_purge.sql), extension pgcrypto disponible (image postgres officielle).
--  ExÃ©cution :
--    psql -U hemo_user -d hemodialyse -v ON_ERROR_STOP=1 -f 01_dataset_renadial.sql
--    (Docker) docker exec -i hemodialyse-postgres-1 psql -U hemo_user -d hemodialyse -v ON_ERROR_STOP=1 < 01_dataset_renadial.sql
-- =====================================================================================================================

\set
ON_ERROR_STOP = on

CREATE
EXTENSION IF NOT EXISTS pgcrypto;

BEGIN;
SET LOCAL client_min_messages = warning;
SET LOCAL work_mem = '256MB';

DO
$$
BEGIN
        IF
EXISTS (SELECT 1 FROM societes) OR EXISTS (SELECT 1 FROM patients) OR EXISTS (SELECT 1 FROM app_user) THEN
            RAISE EXCEPTION 'Base non vide : exÃ©cuter d''abord 00_purge.sql';
END IF;
END
$$;

-- =====================================================================================================================
-- 0. PARAMÃˆTRES & FONCTIONS UTILITAIRES (temporaires, dÃ©truites en fin de session)
-- =====================================================================================================================
CREATE
TEMP TABLE _cfg ON COMMIT DROP
AS
SELECT 30::int                                                              AS nb_patients,  -- patients par centre (date_trunc('month', current_date) - INTERVAL '12 months')::date     AS d_debut,      -- dÃ©but historique date_trunc('month', current_date)::date                              AS d_mois,       -- mois courant (non facturÃ©) current_date AS d_fin, -- derniÃ¨re sÃ©ance (date_trunc('year', current_date) + INTERVAL '1 year - 1 day')::date AS d_fin_droits,
-- fin attestations / PEC 'Renadial@2026!'::text                                               AS mot_de_passe, 0.00::numeric(5, 2)                                                  AS tva,          -- soins exonÃ©rÃ©s de TVA '031404be-8303-44f7-b7b1-429fde1a7853'::uuid                         AS societe_id;

-- UUID dÃ©terministe
CREATE
OR REPLACE FUNCTION pg_temp.uid(k text) RETURNS uuid
    LANGUAGE sql IMMUTABLE AS
$$
SELECT md5('renadial:' || k) ::uuid $$;

-- Pseudo-alÃ©atoire dÃ©terministe dans [0,1[
CREATE
OR REPLACE FUNCTION pg_temp.rnd(k text) RETURNS double precision
    LANGUAGE sql IMMUTABLE AS
$$
SELECT (('x' || substr(md5(k), 1, 12))::bit(48)::bigint)::double precision / 281474976710656.0 $$;

CREATE
OR REPLACE FUNCTION pg_temp.rng(k text, lo numeric, hi numeric) RETURNS numeric
    LANGUAGE sql IMMUTABLE AS
$$
SELECT lo + (hi - lo) * pg_temp.rnd(k) ::numeric $$;

CREATE
OR REPLACE FUNCTION pg_temp.pick(arr text[], k text) RETURNS text
    LANGUAGE sql IMMUTABLE AS
$$
SELECT arr[1 + floor(pg_temp.rnd(k) * array_length(arr, 1))::int] $$;

-- Horodatage Africa/Algiers : date + heure dÃ©cimale (ex. 14.5 = 14h30)
CREATE
OR REPLACE FUNCTION pg_temp.ts(d date, h numeric) RETURNS timestamptz
    LANGUAGE sql IMMUTABLE AS
$$
SELECT (d + make_interval(mins = > round(h * 60)::int)) AT TIME ZONE 'Africa/Algiers' $$;

CREATE
OR REPLACE FUNCTION pg_temp.nom(k text) RETURNS text
    LANGUAGE sql IMMUTABLE AS
$$
SELECT pg_temp.pick(ARRAY['BENALI', 'BOUZID', 'BELKACEM', 'HADDAD', 'MANSOURI', 'BOUDIAF', 'CHERIF', 'KHELIFI',
                    'RAHMANI',
                    'SAADI', 'BRAHIMI', 'HAMIDI', 'MEZIANE', 'DJEBBAR', 'BENAISSA', 'ZERROUKI', 'OUALI', 'BENSALAH',
                    'AMRANI', 'KACI', 'TALEB',
                    'MEBARKI', 'LAOUAR', 'GUERFI', 'BOUKHARI', 'SLIMANI', 'FERHAT', 'AIT AHMED', 'CHAOUCH', 'BENYOUCEF',
                    'TOUATI', 'HADJADJ',
                    'BELAID', 'LOUNES', 'AOUADI', 'BOUAZZA', 'MEDJDOUB', 'BEKKOUCHE', 'KHERBACHE', 'SMAILI', 'ZITOUNI',
                    'BENMOUSSA', 'GHERBI',
                    'REBAI', 'SAIDI', 'YAHIAOUI', 'LARBI', 'BOUCHAMA', 'NAILI', 'OUARET'], 'nom' || k)
           $$;

CREATE
OR REPLACE FUNCTION pg_temp.prenom(sexe text, k text) RETURNS text
    LANGUAGE sql IMMUTABLE AS
$$
SELECT CASE
           WHEN sexe = 'M' THEN pg_temp.pick(ARRAY['Mohamed', 'Ahmed', 'Abdelkader', 'Karim', 'Rachid', 'Yacine',
                                             'Sofiane',
                                             'Nabil', 'Samir', 'Mourad', 'Djamel', 'Hocine', 'Omar', 'Brahim',
                                             'Mustapha', 'Farid', 'Kamel', 'Said', 'Amine', 'Bilal',
                                             'Walid', 'Toufik', 'Nassim', 'Hamza', 'Riad', 'Larbi', 'Messaoud', 'Aissa',
                                             'Lakhdar', 'Abderrahmane', 'Youcef',
                                             'Redouane', 'Slimane', 'Ali', 'Salah'], 'pre' || k)
           ELSE pg_temp.pick(ARRAY['Fatima', 'Khadija', 'Amina', 'Nadia', 'Samia', 'Souad', 'Houria', 'Zohra', 'Malika',
                             'Yamina',
                             'Naima', 'Leila', 'Karima', 'Meriem', 'Sarah', 'Imane', 'Fatiha', 'Djamila', 'Rachida',
                             'Hassiba', 'Nawel', 'Lynda',
                             'Sabrina', 'Aicha', 'Kheira', 'Ouarda', 'Hayat', 'Salima', 'Dalila', 'Farida', 'Baya',
                             'Zineb', 'Chahrazed', 'Nesrine',
                             'Wassila'], 'pre' || k) END
           $$;

CREATE
OR REPLACE FUNCTION pg_temp.mob(k text) RETURNS text
    LANGUAGE sql IMMUTABLE AS
$$
SELECT
    pg_temp.pick(ARRAY['0550', '0551', '0552', '0661', '0662', '0665', '0770', '0771', '0696', '0697', '0795', '0798'],
                 'mob' || k)
        || lpad(floor(pg_temp.rnd('mobn' || k) * 1000000)::int::text, 6, '0')
        $$;

CREATE
OR REPLACE FUNCTION pg_temp.fixe(prefixe text, k text) RETURNS text
    LANGUAGE sql IMMUTABLE AS
$$
SELECT prefixe || ' ' || lpad((10 + floor(pg_temp.rnd('f1' || k) * 89)):: int ::text, 2, '0')
           || ' ' || lpad(floor(pg_temp.rnd('f2' || k) * 100)::int::text, 2, '0')
           || ' ' || lpad(floor(pg_temp.rnd('f3' || k) * 100)::int::text, 2, '0')
           $$;

-- =====================================================================================================================
-- 1. SOCIÃ‰TÃ‰, CENTRES, CALENDRIER
-- =====================================================================================================================
CREATE
TEMP TABLE _c ON COMMIT DROP
AS
SELECT v.idx,
       v.nom,
       v.s,
       lower(v.s)                                                  AS slug,
       v.ville,
       v.wilaya,
       v.wcode,
       v.tel,
       v.adresse,
       replace(v.nom, ' ', '') || '-' || lpad(v.idx::text, 4, '0') AS code,
       CASE
           WHEN v.s = 'ROU' THEN 'b26119d0-6fc7-48fd-96a6-fb4d2ffe33a7'::uuid
           ELSE pg_temp.uid('centre:' || v.s) END                  AS id
FROM (VALUES (1, 'ROUIBA', 'ROU', 'Rouiba', 'Alger', '16', '023', 'Route nationale nÂ° 5, zone urbaine, Rouiba'),
             (2, 'DEB', 'DEB', 'Dar El Beida', 'Alger', '16', '023', 'CitÃ© du 5 Juillet, lot nÂ° 12, Dar El Beida'),
             (3, 'BAINEM', 'BAI', 'Hammamet', 'Alger', '16', '023', 'Route de BaÃ¯nem, Hammamet'),
             (4, 'ANNABA 1', 'AN1', 'Annaba', 'Annaba', '23', '038', 'Boulevard du 1er Novembre 1954, Annaba'),
             (5, 'ANNABA 2', 'AN2', 'El Bouni', 'Annaba', '23', '038', 'CitÃ© Sidi Salem, El Bouni'),
             (6, 'SKIKDA', 'SKI', 'Skikda', 'Skikda', '21', '038', 'Avenue Didouche Mourad, Skikda'),
             (7, 'CONSTANTINE', 'CST', 'Constantine', 'Constantine', '25', '031', 'CitÃ© Daksi Abdeslam, Constantine'),
             (8, 'EL KHROUB', 'KHR', 'El Khroub', 'Constantine', '25', '031', 'CitÃ© des 1600 logements, El Khroub'),
             (9, 'GUELMA', 'GUE', 'Guelma', 'Guelma', '24', '037', 'Rue Souidani Boudjemaa, Guelma'),
             (10, 'BATNA', 'BAT', 'Batna', 'Batna', '05', '033', 'Route de Biskra, Batna'),
             (11, 'KHENCHELA', 'KHE', 'Khenchela', 'Khenchela', '40', '032', 'CitÃ© El Nasr, Khenchela'),
             (12, 'SIDI BEL ABBES 1', 'SB1', 'Sidi Bel AbbÃ¨s', 'Sidi Bel AbbÃ¨s', '22', '048',
              'Boulevard de la RÃ©publique, Sidi Bel AbbÃ¨s'),
             (13, 'SIDI BEL ABBES 2', 'SB2', 'Sidi Bel AbbÃ¨s', 'Sidi Bel AbbÃ¨s', '22', '048',
              'CitÃ© Sidi Djillali, Sidi Bel AbbÃ¨s'),
             (14, 'ESSEDIKIA', 'ESS', 'Oran', 'Oran', '31', '041', 'CitÃ© Essedikia, Oran'),
             (15, 'HAI ESSALAM', 'HAI', 'Oran', 'Oran', '31', '041', 'HaÃ¯ Essalam, Oran'),
             (16, 'TLEMCEN 1', 'TL1', 'Tlemcen', 'Tlemcen', '13', '043', 'Boulevard Colonel Lotfi, Tlemcen'),
             (17, 'TLEMCEN 2', 'TL2', 'Mansourah', 'Tlemcen', '13', '043', 'Route de Mansourah, Tlemcen'))
         AS v(idx, nom, s, ville, wilaya, wcode, tel, adresse);

INSERT INTO societes (id, actif, adresse, code, created_at, email, logo, logo_content_type, nif, nis, pied_page,
                      raison_sociale, rc, site_web, telephone, ville, wilaya)
SELECT cfg.societe_id,
       TRUE,
       'Zone industrielle, lot nÂ° 45, Rouiba',
       'RE-0001',
       pg_temp.ts(cfg.d_debut - 400, 9),
       'contact@renadial.dz',
       NULL,
       NULL,
       '001216098765432',
       '001216012345678',
       'RENADIAL SPA â€” RÃ©seau national de centres d''hÃ©modialyse â€” Capital social 250 000 000 DA â€” '
           || 'RC 16/00-0987654 B 12 â€” NIF 001216098765432',
       'RENADIAL',
       '16/00-0987654 B 12',
       'https://www.renadial.dz',
       '023 86 10 00',
       'Rouiba',
       'Alger'
FROM _cfg cfg;

INSERT INTO centers (id, actif, adresse, code, email, name, site_web, societe_id, telephone, ville, wilaya)
SELECT c.id,
       TRUE,
       c.adresse,
       c.code,
       c.slug || '@renadial.dz',
       c.nom,
       'https://www.renadial.dz/centres/' || c.slug,
       cfg.societe_id,
       pg_temp.fixe(c.tel, 'ctr' || c.s),
       c.ville,
       c.wilaya
FROM _c c
         CROSS JOIN _cfg cfg;

-- Jours fÃ©riÃ©s algÃ©riens (civils fixes + religieux)
CREATE
TEMP TABLE _hol ON COMMIT DROP
AS
SELECT DISTINCT
ON (h.d) h.d, h.label
FROM (SELECT make_date(y, f.m, f.dd) AS d, f.lbl AS label
    FROM generate_series(extract (YEAR FROM (SELECT d_debut FROM _cfg)):: int, extract (YEAR FROM current_date):: int + 1) AS y
    CROSS JOIN (VALUES (1, 1, 'Jour de l''An'), (1, 12, 'Yennayer â€” Nouvel An amazigh'), (5, 1, 'FÃªte du Travail'), (7, 5, 'FÃªte de l''IndÃ©pendance et de la Jeunesse'), (11, 1, 'Anniversaire du dÃ©clenchement de la RÃ©volution')) AS f(m, dd, lbl)
    UNION ALL
    SELECT v.d:: date, v.lbl
    FROM (VALUES ('2025-03-31', 'AÃ¯d el-Fitr'), ('2025-06-06', 'AÃ¯d el-Adha'), ('2025-06-26', 'Awal Moharram'), ('2025-07-06', 'Achoura'), ('2025-09-04', 'Mawlid Ennabaoui'), ('2026-03-20', 'AÃ¯d el-Fitr'), ('2026-05-27', 'AÃ¯d el-Adha'), ('2026-06-16', 'Awal Moharram'), ('2026-06-25', 'Achoura'), ('2026-08-25', 'Mawlid Ennabaoui'), ('2027-03-10', 'AÃ¯d el-Fitr'), ('2027-05-16', 'AÃ¯d el-Adha'), ('2027-06-06', 'Awal Moharram'), ('2027-06-15', 'Achoura'), ('2027-08-14', 'Mawlid Ennabaoui')) AS v(d, lbl)) h
WHERE h.d BETWEEN (SELECT d_debut - 31 FROM _cfg) AND (SELECT d_fin_droits FROM _cfg)
ORDER BY h.d, h.label;

INSERT INTO center_holiday (id, center_id, day_date, label)
SELECT pg_temp.uid('hol:' || c.s || ':' || h.d), c.id, h.d, h.label
FROM _c c
         CROSS JOIN _hol h;

-- Une fermeture exceptionnelle par centre (maintenance du traitement d'eau), jamais un vendredi
CREATE
TEMP TABLE _clo ON COMMIT DROP
AS
SELECT c.id                                                                                    AS center_id,
       c.s,
       CASE WHEN extract(DOW FROM x.d) = 5 THEN x.d + 1 ELSE x.d END                           AS d,
       pg_temp.pick(ARRAY['Maintenance annuelle de la station de traitement d''eau',
                    'DÃ©sinfection thermique et chimique de la boucle d''eau osmosÃ©e',
                    'Travaux Ã©lectriques â€” remplacement du groupe Ã©lectrogÃ¨ne'], 'clo' || c.s) AS reason
FROM _c c
         CROSS JOIN LATERAL (SELECT (SELECT d_debut FROM _cfg) + 60 + c.idx * 11 AS d) x;

INSERT INTO center_closure_day (id, center_id, day_date, reason)
SELECT pg_temp.uid('clo:' || s), center_id, d, reason
FROM _clo;

-- =====================================================================================================================
-- 2. RÃ”LES, UTILISATEURS, AFFECTATIONS
-- =====================================================================================================================
INSERT INTO app_role (id, code, name, description)
VALUES ('a0a00001-0000-0000-0000-000000000001', 'ADMIN', 'Administrateur', 'AccÃ¨s complet au(x) centre(s)'),
       ('a0a00001-0000-0000-0000-000000000002', 'MEDECIN', 'MÃ©decin',
        'Dossier mÃ©dical, prescriptions et signature des sÃ©ances'),
       ('a0a00001-0000-0000-0000-000000000003', 'INFIRMIER', 'Infirmier', 'SÃ©ances, volet paramÃ©dical et soins'),
       ('a0a00001-0000-0000-0000-000000000004', 'SECRETAIRE', 'SecrÃ©taire',
        'Gestion administrative : patients, attestations, PEC, facturation'),
       ('a0a00001-0000-0000-0000-000000000005', 'SUPERADMIN', 'PropriÃ©taire de l''application',
        'Ã‰diteur : sociÃ©tÃ©s, centres, licences'),
       ('a0a00001-0000-0000-0000-000000000006', 'DIRECTION', 'Direction',
        'Consultation des tableaux de bord consolidÃ©s de la sociÃ©tÃ© (agrÃ©gats anonymes, lecture seule)');

CREATE
TEMP TABLE _u ON COMMIT DROP
AS
SELECT pg_temp.uid('user:' || x.username) AS id, x.*
FROM (SELECT 'superadmin'::text AS username, 'PropriÃ©taire'::text AS prenom, 'APPLICATION'::text AS nom, 'SUPERADMIN'::text AS role_code, NULL::uuid AS center_id, NULL::text AS s, 0 AS rang
      UNION ALL
      SELECT 'direction.renadial', 'Direction GÃ©nÃ©rale', 'RENADIAL', 'DIRECTION', NULL, NULL, 0
      UNION ALL
      SELECT 'admin.renadial', 'Administrateur RÃ©seau', 'RENADIAL', 'ADMIN', NULL, NULL, 0
      UNION ALL
      SELECT r.prefix || '.' || c.slug,
             CASE
                 WHEN r.role_code = 'ADMIN' THEN 'Administrateur'
                 ELSE pg_temp.prenom(r.sx, 'u' || c.s || r.prefix) END,
             CASE WHEN r.role_code = 'ADMIN' THEN c.nom ELSE pg_temp.nom('u' || c.s || r.prefix) END,
             r.role_code,
             c.id,
             c.s,
             r.rang
      FROM _c c
               CROSS JOIN (VALUES ('admin', 'ADMIN', 'M', 1),
                                  ('medecin1', 'MEDECIN', 'M', 1),
                                  ('medecin2', 'MEDECIN', 'F', 2),
                                  ('infirmier1', 'INFIRMIER', 'F', 1),
                                  ('infirmier2', 'INFIRMIER', 'M', 2),
                                  ('secretaire', 'SECRETAIRE', 'F', 1)) AS r(prefix, role_code, sx, rang)) x;

INSERT INTO app_user (id, active, created_at, email, full_name, password_hash, username)
SELECT u.id,
       TRUE,
       pg_temp.ts(cfg.d_debut - 30, 9),
       u.username || '@renadial.dz',
       CASE WHEN u.role_code = 'MEDECIN' THEN 'Dr ' ELSE '' END || u.prenom || ' ' || u.nom,
       h.hash,
       u.username
FROM _u u
         CROSS JOIN _cfg cfg
         CROSS JOIN (SELECT crypt(mot_de_passe, gen_salt('bf', 10)) AS hash FROM _cfg) h;

INSERT INTO app_user_role (user_id, role_id)
SELECT u.id, r.id
FROM _u u
         JOIN app_role r ON r.code = u.role_code;

INSERT INTO app_user_center (user_id, center_id)
SELECT u.id, u.center_id
FROM _u u
WHERE u.center_id IS NOT NULL
UNION ALL
SELECT u.id, c.id
FROM _u u
         CROSS JOIN _c c
WHERE u.username = 'admin.renadial';

INSERT INTO app_user_societe (user_id, societe_id)
SELECT u.id, cfg.societe_id
FROM _u u
         CROSS JOIN _cfg cfg
WHERE u.role_code = 'DIRECTION';

INSERT INTO user_center_assignment (id, center_id, created_at, role_code, user_id)
SELECT pg_temp.uid('uca:' || u.username || ':' || u.center_id),
       u.center_id,
       pg_temp.ts(cfg.d_debut - 30, 9),
       u.role_code,
       u.username
FROM _u u
         CROSS JOIN _cfg cfg
WHERE u.center_id IS NOT NULL
UNION ALL
SELECT pg_temp.uid('uca:' || u.username || ':' || c.id),
       c.id,
       pg_temp.ts(cfg.d_debut - 30, 9),
       'ADMIN',
       u.username
FROM _u u
         CROSS JOIN _c c
         CROSS JOIN _cfg cfg
WHERE u.username = 'admin.renadial';

-- MFA : enrÃ´lement initiÃ© (non activÃ©) pour les comptes sans centre ; codes de secours dÃ©jÃ  consommÃ©s
INSERT INTO app_user_mfa (user_id, secret_cipher, enabled, last_step, failed_attempts, locked_until, created_at)
SELECT u.id, 'PENDING-ENROLLMENT', FALSE, 0, 0, NULL, pg_temp.ts(cfg.d_fin - 20, 10)
FROM _u u
         CROSS JOIN _cfg cfg
WHERE u.role_code IN ('SUPERADMIN', 'DIRECTION');

INSERT INTO app_user_mfa_recovery (id, user_id, code_hash, used)
SELECT pg_temp.uid('mfarec:' || u.id || ':' || g),
       u.id,
       encode(digest('recovery-' || u.username || '-' || g, 'sha256'), 'hex'),
       TRUE
FROM _u u
         CROSS JOIN generate_series(1, 4) g
WHERE u.role_code IN ('SUPERADMIN', 'DIRECTION');

-- Jetons de rafraÃ®chissement historiques (rÃ©voquÃ©s / expirÃ©s)
INSERT INTO auth_refresh_token (id, token_hash, user_id, center_id, societe_id, expires_at, revoked, created_at,
                                revoked_at)
SELECT pg_temp.uid('rt:' || u.id),
       encode(digest('refresh-' || u.id::text, 'sha256'), 'hex'),
       u.id,
       u.center_id,
       CASE WHEN u.role_code = 'DIRECTION' OR u.center_id IS NOT NULL THEN cfg.societe_id END,
       pg_temp.ts(cfg.d_fin - 3, 8),
       TRUE,
       pg_temp.ts(cfg.d_fin - 10, 8),
       pg_temp.ts(cfg.d_fin - 10, 17)
FROM _u u
         CROSS JOIN _cfg cfg;

-- =====================================================================================================================
-- 3. RÃ‰FÃ‰RENTIELS PAR CENTRE
-- =====================================================================================================================
CREATE
TEMP TABLE _cai ON COMMIT DROP
AS
SELECT c.id                                           AS center_id,
       c.s,
       c.wcode,
       c.ville,
       v.code,
       v.nom,
       v.type_caisse,
       v.ord,
       pg_temp.uid('caisse:' || c.s || ':' || v.code) AS id,
       pg_temp.uid('agence:' || c.s || ':' || v.code) AS agence_id
FROM _c c
         CROSS JOIN (VALUES ('CNAS', 'CNAS â€” Caisse Nationale des Assurances Sociales des travailleurs salariÃ©s',
                             'STANDARD', 1),
                            ('CASNOS', 'CASNOS â€” Caisse Nationale de SÃ©curitÃ© Sociale des Non-SalariÃ©s',
                             'STANDARD', 2),
                            ('MGPTT', 'Mutuelle GÃ©nÃ©rale des Postes et TÃ©lÃ©communications', 'STANDARD', 3),
                            ('VAC', 'Caisse patients vacanciers (hors wilaya)', 'VACANCIER',
                             4)) AS v(code, nom, type_caisse, ord);

INSERT INTO caisse_assurance (id, center_id, code, nom, type_caisse)
SELECT id, center_id, code, nom, type_caisse
FROM _cai;

INSERT INTO agence (id, caisse_id, center_id, code, nom)
SELECT agence_id,
       id,
       center_id,
       wcode || lpad(ord::text, 2, '0'),
       CASE code
           WHEN 'CNAS' THEN 'Agence CNAS de ' || ville
           WHEN 'CASNOS' THEN 'Agence CASNOS de ' || ville
           WHEN 'MGPTT' THEN 'DÃ©lÃ©gation MGPTT de ' || ville
           ELSE 'Guichet vacanciers ' || ville END
FROM _cai;

CREATE
TEMP TABLE _cp ON COMMIT DROP
AS
SELECT a.center_id,
       a.s,
       a.code                                                 AS caisse_code,
       a.agence_id,
       a.wcode,
       a.ville,
       k,
       pg_temp.uid('cp:' || a.s || ':' || a.code || ':' || k) AS id
FROM _cai a
         CROSS JOIN LATERAL generate_series(1, CASE WHEN a.code = 'CNAS' THEN 2 ELSE 1 END) AS k;

INSERT INTO centre_payeur (id, adresse, agence_id, center_id, code, nom)
SELECT id,
       CASE k WHEN 1 THEN 'Centre-ville, ' ELSE 'CitÃ© administrative, ' END || ville,
       agence_id,
       center_id,
       wcode ||
       lpad((CASE caisse_code WHEN 'CNAS' THEN 100 WHEN 'CASNOS' THEN 200 WHEN 'MGPTT' THEN 300 ELSE 900 END + k)::text,
            3, '0'),
       'Centre payeur ' || caisse_code || ' ' || ville || CASE WHEN k = 2 THEN ' Est' ELSE '' END
FROM _cp;

-- MÃ©decins : les 2 nÃ©phrologues correspondent aux comptes medecin1 / medecin2, + 1 gÃ©nÃ©raliste
CREATE
TEMP TABLE _med ON COMMIT DROP
AS
SELECT u.center_id
     , u.s
     , u.rang
     , u.nom
     , u.prenom
     , 'NÃ©phrologue'::text AS specialite, pg_temp.uid('med:' || u.s || ':' || u.rang) AS id
     , u.id AS user_id
     , u.username
FROM _u u
WHERE u.role_code = 'MEDECIN'
UNION ALL
SELECT c.id,
       c.s,
       3,
       pg_temp.nom('gen' || c.s),
       pg_temp.prenom('M', 'gen' || c.s),
       'MÃ©decin gÃ©nÃ©raliste',
       pg_temp.uid('med:' || c.s || ':3'),
       NULL,
       NULL
FROM _c c;

INSERT INTO medecin (id, center_id, nom, prenom, specialite)
SELECT id, center_id, nom, prenom, specialite
FROM _med;

INSERT INTO salle (id, center_id, code, nom)
SELECT pg_temp.uid('salle:' || c.s || ':' || v.no), c.id, 'S' || v.no, v.nom
FROM _c c
         CROSS JOIN (VALUES (1, 'Salle A'), (2, 'Salle B'), (3, 'Salle d''isolement (VHB / VHC)')) AS v(no, nom);

CREATE
TEMP TABLE _gen ON COMMIT DROP
AS
SELECT c.id                                                   AS center_id,
       c.s,
       sl.no                                                  AS salle_no,
       g,
       pg_temp.uid('gen:' || c.s || ':' || sl.no || ':' || g) AS id,
       'G' || sl.no || lpad(g::text, 2, '0')                  AS numero
FROM _c c
         CROSS JOIN (VALUES (1, 6), (2, 6), (3, 3)) AS sl(no, nb)
         CROSS JOIN LATERAL generate_series(1, sl.nb) AS g;

INSERT INTO generateur (id, salle_id, center_id, numero, marque, modele, etat)
SELECT g.id,
       pg_temp.uid('salle:' || g.s || ':' || g.salle_no),
       g.center_id,
       g.numero,
       CASE WHEN g.g % 3 = 0 THEN 'B. Braun' WHEN g.g % 3 = 1 THEN 'Fresenius Medical Care' ELSE 'Nipro' END,
       CASE WHEN g.g % 3 = 0 THEN 'Dialog+' WHEN g.g % 3 = 1 THEN '5008S CorDiax' ELSE 'Surdial 55 Plus' END,
       CASE
           WHEN g.salle_no = 1 AND g.g = 6 THEN 'EN_REPARATION'
           WHEN g.salle_no = 2 AND g.g = 6 THEN 'EN_PANNE'
           WHEN g.salle_no = 3 AND g.g = 3 THEN 'REFORME'
           ELSE 'FONCTIONNEL' END
FROM _gen g;

INSERT INTO position_creneau (id, center_id, code, libelle)
SELECT pg_temp.uid('pos:' || c.s || ':' || v.no), c.id, 'CR' || v.no, v.lib
FROM _c c
         CROSS JOIN (VALUES (1, 'Matin (06h30 â€“ 10h30)'),
                            (2, 'Mi-journÃ©e (11h00 â€“ 15h00)'),
                            (3, 'AprÃ¨s-midi (15h30 â€“ 19h30)')) AS v(no, lib);

INSERT INTO transporteur (id, center_id, nom, telephone)
SELECT pg_temp.uid('trp:' || c.s || ':' || v.no),
       c.id,
       replace(v.nom, '#', c.ville),
       pg_temp.mob('trp' || c.s || v.no)
FROM _c c
         CROSS JOIN (VALUES (1, 'Ambulances #'),
                            (2, 'Transport sanitaire El Amel'),
                            (3, 'Taxi mÃ©dical #')) AS v(no, nom);

INSERT INTO categorie_transport (id, center_id, libelle)
SELECT pg_temp.uid('cat:' || c.s || ':' || v.no), c.id, v.lib
FROM _c c
         CROSS JOIN (VALUES (1, 'Ambulance'),
                            (2, 'VÃ©hicule sanitaire lÃ©ger (VSL)'),
                            (3, 'Taxi conventionnÃ©'),
                            (4, 'Moyens personnels')) AS v(no, lib);

-- 5 forfaits par centre
CREATE
TEMP TABLE _fo ON COMMIT DROP
AS
SELECT c.id AS center_id,
       c.s,
       v.code,
       v.libelle,
       v.prix::numeric(12, 2) AS prix, pg_temp.uid('forfait:' || c.s || ':' || v.code) AS id
FROM _c c
         CROSS JOIN (VALUES ('HD-CONV', 'HÃ©modialyse conventionnelle', 5600),
                            ('HD-EPO', 'HÃ©modialyse avec traitement de l''anÃ©mie (EPO)', 7200),
                            ('HDF-OL', 'HÃ©modiafiltration en ligne', 7800),
                            ('HD-KT', 'HÃ©modialyse sur cathÃ©ter veineux central', 6200),
                            ('HD-VAC', 'HÃ©modialyse patient vacancier', 9000)) AS v(code, libelle, prix);

INSERT INTO forfait (id, center_id, code, libelle, prix)
SELECT id, center_id, code, libelle, prix
FROM _fo;

-- ModÃ¨les de documents (Jasper) + version archivÃ©e
CREATE
TEMP TABLE _md ON COMMIT DROP
AS
SELECT c.id AS center_id, c.s, v.*, pg_temp.uid('modele:' || c.s || ':' || v.code) AS id
FROM _c c
         CROSS JOIN (VALUES ('FICHE_PATIENT', 'Fiche signalÃ©tique patient', 'reports/fiche_patient.jrxml',
                             'Fiche complÃ¨te du patient avec ses informations personnelles et mÃ©dicales'),
                            ('ATTESTATION', 'Attestation d''ouverture de droit', 'reports/attestation.jrxml',
                             'Attestation d''ouverture de droit du patient'),
                            ('PEC', 'Prise en charge', 'reports/prise_en_charge.jrxml',
                             'Document de prise en charge pour la caisse d''assurance'),
                            ('LISTE_PATIENTS', 'Liste des patients', 'reports/liste_patients.jrxml',
                             'Liste complÃ¨te des patients du centre'),
                            ('LISTE_PEC', 'Liste des prises en charge', 'reports/liste_pec.jrxml',
                             'Liste de toutes les prises en charge du centre'),
                            ('LISTE_ATTESTATIONS', 'Liste des attestations', 'reports/liste_attestations.jrxml',
                             'Liste de toutes les attestations du centre'),
                            ('SYNTHESE_FACTURATION_MENSUELLE', 'SynthÃ¨se mensuelle facturation',
                             'reports/synthese_mensuelle_facturation.jrxml',
                             'Tableau croisÃ© facturation par forfait/caisse avec rÃ©partition graphique'),
                            ('ORDONNANCE', 'Ordonnance mÃ©dicamenteuse', 'reports/ordonnance.jrxml',
                             'Ordonnance signÃ©e par le mÃ©decin, imprimable pour le patient')) AS v(code, libelle, chemin, description);

INSERT INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, format_impression, description,
                             active, created_at)
SELECT m.id,
       m.center_id,
       m.code,
       m.libelle,
       m.code,
       m.chemin,
       'PDF',
       m.description,
       TRUE,
       pg_temp.ts(cfg.d_debut - 30, 9)
FROM _md m
         CROSS JOIN _cfg cfg;

-- Version 1 archivÃ©e (inactive) : le rendu utilise le modÃ¨le JRXML embarquÃ© tant qu'aucune version n'est active.
INSERT INTO modele_document_version (id, actif, center_id, commentaire, contenu, modele_id, sha256, taille_octets,
                                     uploaded_at, uploaded_by, version)
SELECT pg_temp.uid('mdv:' || m.id),
       FALSE,
       m.center_id,
       'Version archivÃ©e (jeu de donnÃ©es) â€” ne pas rÃ©activer',
       x.contenu,
       m.id,
       encode(digest(x.contenu, 'sha256'), 'hex'),
       octet_length(x.contenu),
       pg_temp.ts(cfg.d_debut + 15, 10),
       'admin.' || lower(m.s),
       1
FROM _md m
         CROSS JOIN _cfg cfg
         CROSS JOIN LATERAL (SELECT '<!-- Archive ' || m.code || ' v1 â€” ' || m.chemin
                                        || ' : contenu non exploitable, conservÃ© pour l''historique. -->' AS contenu) x
WHERE m.code IN ('FICHE_PATIENT', 'ORDONNANCE', 'ATTESTATION');

-- ParamÃ©trage facturation / TVA / comptabilitÃ©
INSERT INTO facturation_settings (center_id, tva_rate, code_format, regroupement_multi_forfait, updated_at, updated_by)
SELECT c.id, cfg.tva, c.s || '-FAC-{YYYY}-{SEQ5}', TRUE, pg_temp.ts(cfg.d_debut - 20, 10), 'admin.' || c.slug
FROM _c c
         CROSS JOIN _cfg cfg;

INSERT INTO tva_types (id, center_id, libelle, taux, type_prestation, exonere, date_debut_validite, date_fin_validite,
                       texte_reference, actif, created_at, created_by)
SELECT pg_temp.uid('tva:' || c.s || ':' || v.no),
       c.id,
       v.libelle,
       v.taux,
       'HEMODIALYSE',
       v.exonere,
       v.debut::date, v.fin::date, v.ref,
       v.actif,
       pg_temp.ts(cfg.d_debut - 20, 10),
       'admin.' || c.slug
FROM _c c
         CROSS JOIN _cfg cfg
         CROSS JOIN (VALUES (1, 'ExonÃ©ration â€” prestations de soins (hÃ©modialyse)', 0.00, TRUE, '2020-01-01', NULL,
                             'Code des taxes sur le chiffre d''affaires â€” exonÃ©ration des actes mÃ©dicaux', TRUE),
                            (2, 'TVA taux normal 19 % (historique)', 19.00, FALSE, '2017-01-01', '2019-12-31',
                             'Loi de finances 2017 â€” taux normal',
                             FALSE)) AS v(no, libelle, taux, exonere, debut, fin, ref, actif);

INSERT INTO mapping_comptable (id, center_id, compte_banque, compte_caisse, compte_client_autre, compte_client_casnos,
                               compte_client_cnas, compte_client_mutuelle, compte_client_patient, compte_tva_collectee,
                               compte_ventes, updated_at, updated_by)
SELECT pg_temp.uid('map:' || c.s),
       c.id,
       '512',
       '530',
       '411500',
       '411300',
       '411200',
       '411400',
       '411100',
       '44571',
       '706',
       pg_temp.ts(cfg.d_debut - 20, 10),
       'admin.' || c.slug
FROM _c c
         CROSS JOIN _cfg cfg;

-- =====================================================================================================================
-- 4. PATIENTS & ASSURÃ‰S
-- =====================================================================================================================
CREATE
TEMP TABLE _p ON COMMIT DROP
AS
WITH b AS (SELECT c.id AS center_id, c.s, c.slug, c.idx AS cidx, c.nom AS centre_nom, c.ville, c.wilaya, c.wcode, c.tel,
                  g.n, pg_temp.uid('pat:' || c.s || ':' || g.n) AS id, c.s || ':' || g.n AS k,
                  cfg.nb_patients, cfg.d_debut, cfg.d_mois, cfg.d_fin, cfg.d_fin_droits
           FROM _c c
                    CROSS JOIN _cfg cfg
                    CROSS JOIN LATERAL generate_series(1, cfg.nb_patients) AS g(n)),
     b2 AS (SELECT b.*,
                   CASE WHEN pg_temp.rnd('sexe' || k) < 0.56 THEN 'M' ELSE 'F' END AS sexe,
                   (n = nb_patients)                                              AS is_vac,
                   (n = nb_patients - 3)                                          AS is_dcd,
                   (n IN (nb_patients - 1, nb_patients - 2))                      AS is_new,
                   (n % 15 = 7)                                                   AS is_vhb,
                   (n % 15 = 11)                                                  AS is_vhc,
                   (pg_temp.rnd('kt' || k) < 0.12)                                AS is_kt,
                   (pg_temp.rnd('epo' || k) < 0.70)                               AS is_epo,
                   (pg_temp.rnd('fer' || k) < 0.50)                               AS is_fer,
                   (pg_temp.rnd('vacc' || k) < 0.60)                              AS is_vaccine,
                   DATE '1946-01-01' + floor(pg_temp.rnd('dn' || k) * 19500)::int AS date_naissance,
                   n % 2                                                          AS grp,
                   1 + (n / 4) % 3                                                AS shift,
                   1 + (n % 4) / 2                                                AS salle_base,
                   1 + (n / 3) % 2                                                AS med_rang,
                   pg_temp.nom('p' || k)                                          AS nom
            FROM b),
     b3 AS (SELECT b2.*,
                   pg_temp.prenom(sexe, 'p' || k)                                           AS prenom,
                   CASE WHEN is_vhb OR is_vhc THEN 3 ELSE salle_base END                    AS salle_no,
                   CASE
                       WHEN is_vac THEN (d_debut + INTERVAL '9 months')::date + 3
                       WHEN is_new THEN d_debut + 100 + floor(pg_temp.rnd('adm' || k) * 120)::int
                       ELSE d_debut - 45 - floor(pg_temp.rnd('adm' || k) * 2900)::int END  AS date_admission,
                   CASE
                       WHEN is_vac THEN 'HD-VAC'
                       WHEN is_kt THEN 'HD-KT'
                       WHEN n % 4 = 0 THEN 'HDF-OL'
                       WHEN is_epo THEN 'HD-EPO'
                       ELSE 'HD-CONV' END                                                   AS forfait_code,
                   CASE
                       WHEN is_vac THEN 'VAC'
                       WHEN pg_temp.rnd('cai' || k) < 0.70 THEN 'CNAS'
                       WHEN pg_temp.rnd('cai' || k) < 0.90 THEN 'CASNOS'
                       ELSE 'MGPTT' END                                                     AS caisse_code,
                   CASE
                       WHEN pg_temp.rnd('ac' || k) < 0.80 THEN 'HNF'
                       WHEN pg_temp.rnd('ac' || k) < 0.95 THEN 'HBPM'
                       ELSE 'CITRATE' END                                                   AS anticoag,
                   round(pg_temp.rng('ps' || k, 52, 88), 1)                                 AS poids_sec,
                   pg_temp.pick(ARRAY ['O+','O+','O+','A+','A+','B+','B+','AB+','O-','A-','B-','AB-'], 'gs' || k) AS groupe_sanguin,
                   pg_temp.pick(ARRAY ['NÃ©phropathie diabÃ©tique','NÃ©phropathie diabÃ©tique',
                       'NÃ©phroangiosclÃ©rose hypertensive','GlomÃ©rulonÃ©phrite chronique',
                       'Polykystose rÃ©nale autosomique dominante','NÃ©phropathie interstitielle chronique',
                       'Uropathie malformative','NÃ©phropathie indÃ©terminÃ©e'], 'nep' || k) AS nephropathie,
                   CASE
                       WHEN date_naissance > DATE '1992-01-01' THEN 'ENFANT'
                       WHEN pg_temp.rnd('qa' || k) < 0.65 THEN 'ASSURE_LUI_MEME'
                       WHEN pg_temp.rnd('qa' || k) < 0.88 OR date_naissance >= DATE '1965-01-01' THEN 'CONJOINT'
                       ELSE 'ASCENDANT' END                                                 AS qualite_assure,
                   CASE
                       WHEN sexe = 'M' THEN pg_temp.pick(ARRAY ['MariÃ©','MariÃ©','MariÃ©','CÃ©libataire','Veuf','DivorcÃ©'], 'sf' || k)
                       ELSE pg_temp.pick(ARRAY ['MariÃ©e','MariÃ©e','MariÃ©e','CÃ©libataire','Veuve','DivorcÃ©e'], 'sf' || k) END AS situation_familiale
            FROM b2),
     b4 AS (SELECT b3.*,
                   CASE WHEN is_vac THEN date_admission ELSE greatest(date_admission, d_debut) END AS d_start,
                   CASE
                       WHEN is_vac THEN least(date_admission + 27, d_fin)
                       WHEN is_dcd THEN (d_debut + INTERVAL '8 months')::date + 11
                       ELSE d_fin END                                                              AS d_end,
                   CASE
                       WHEN qualite_assure = 'ASSURE_LUI_MEME' THEN sexe
                       WHEN qualite_assure = 'CONJOINT' THEN CASE sexe WHEN 'M' THEN 'F' ELSE 'M' END
                       WHEN qualite_assure = 'ENFANT' THEN 'M'
                       ELSE CASE WHEN pg_temp.rnd('asx' || k) < 0.6 THEN 'M' ELSE 'F' END END      AS assure_sexe
            FROM b3)
SELECT b4.*,
       CASE WHEN qualite_assure = 'CONJOINT' THEN pg_temp.nom('as' || k) ELSE nom END                           AS assure_nom,
       CASE
           WHEN qualite_assure = 'ASSURE_LUI_MEME' THEN prenom
           ELSE pg_temp.prenom(assure_sexe, 'as' || k) END                                                      AS assure_prenom,
       CASE qualite_assure
           WHEN 'ASSURE_LUI_MEME' THEN date_naissance
           WHEN 'CONJOINT' THEN date_naissance + (floor(pg_temp.rnd('adn' || k) * 2400)::int - 1200)
           WHEN 'ENFANT' THEN date_naissance - 9500 - floor(pg_temp.rnd('adn' || k) * 2500)::int
           ELSE date_naissance + 9500 + floor(pg_temp.rnd('adn' || k) * 2500)::int
END             AS assure_date_naissance,
       CASE WHEN is_vac THEN 'VACANCIER_LOCAL' WHEN is_dcd THEN 'DECEDE' ELSE 'PERMANENT'
END         AS etat_patient,
       CASE WHEN is_vac THEN 'VACANCIER' ELSE 'NON_VACANCIER'
END                                   AS type_patient,
       CASE WHEN grp = 0 THEN ARRAY [6,1,3] ELSE ARRAY [0,2,4]
END                                   AS jours,
       CASE WHEN caisse_code = 'CNAS' THEN 1 + floor(pg_temp.rnd('cp' || k) * 2)::int ELSE 1
END     AS cp_no,
       'PAT-' || s || lpad(n::text, 5, '0')                                                         AS code_patient
FROM b4;

ALTER TABLE _p
    ADD COLUMN numero_assurance text,
    ADD COLUMN gen_no           int,
    ADD COLUMN date_dialyse     date,
    ADD COLUMN presc1_date      date,
    ADD COLUMN presc2_date      date,
    ADD COLUMN has_presc2       boolean;

UPDATE _p
SET numero_assurance = lpad((extract(YEAR FROM assure_date_naissance)::int % 100)::text, 2, '0') || wcode
    || lpad(cidx::text, 2, '0') || lpad(n::text, 4, '0')
    || lpad(floor(pg_temp.rnd('nss' || k) * 100)::int::text, 2, '0'),
    date_dialyse     = date_admission - CASE WHEN pg_temp.rnd('dd' || k) < 0.3 THEN floor(pg_temp.rnd('dd2' || k) * 700)::int ELSE 0 END,
    presc1_date      = CASE WHEN date_admission < d_debut THEN d_debut - 12 ELSE date_admission
END,
    presc2_date      = (d_debut + INTERVAL '6 months')::date + 3;

UPDATE _p
SET has_presc2 = (d_start < presc2_date AND d_end > presc2_date + 30);

UPDATE _p
SET gen_no = x.r FROM (SELECT id, row_number() OVER (PARTITION BY center_id, salle_no, grp, shift ORDER BY n) AS r FROM _p) x
WHERE x.id = _p.id;

UPDATE _p
SET gen_no = CASE WHEN salle_no = 3 THEN 1 + (gen_no - 1) % 2 ELSE 1 + (gen_no - 1) % 5 END;

CREATE UNIQUE INDEX ON _p (id);
ANALYZE
_p;

INSERT INTO assure (numero_assurance, adresse, center_id, created_at, date_naissance, groupe_sanguin, nom, prenom, sexe,
                    tel_bureau, tel_mobile, tel_personnel)
SELECT p.numero_assurance,
       'CitÃ© ' ||
       pg_temp.pick(ARRAY['500 logements', 'El Djorf', 'AADL', '1er Mai', 'El Amel', 'des Oliviers', '20 AoÃ»t'],
                    'adr' || p.k)
           || ', nÂ° ' || (1 + floor(pg_temp.rnd('adrn' || p.k) * 120))::int || ', ' || p.ville, p.center_id,
       pg_temp.ts(p.date_admission, 9),
       p.assure_date_naissance,
       CASE
           WHEN p.qualite_assure = 'ASSURE_LUI_MEME' THEN p.groupe_sanguin
           ELSE pg_temp.pick(ARRAY['O+', 'A+', 'B+', 'AB+', 'O-', 'A-'], 'ags' || p.k) END,
       p.assure_nom,
       p.assure_prenom,
       p.assure_sexe,
       pg_temp.fixe(p.tel, 'atb' || p.k),
       CASE WHEN p.qualite_assure = 'ASSURE_LUI_MEME' THEN pg_temp.mob(p.k) ELSE pg_temp.mob('as' || p.k) END,
       pg_temp.fixe(p.tel, 'atp' || p.k)
FROM _p p;

INSERT INTO patients (id, adresse, assure_adresse, assure_date_naissance, assure_groupe_sanguin, assure_history_json,
                      assure_nom, assure_numero_assurance, assure_prenom, assure_sexe, assure_tel_bureau,
                      assure_tel_mobile, assure_tel_personnel, categorie_transport_id, center_id, centre_payeur_id,
                      civilite, code_patient, created_at, date_admission, date_evenement_etat, date_naissance, email,
                      en_sommeil, epo_date, epo_enabled, etat_patient, fer_date, fer_enabled, generateur_id,
                      groupe_sanguin, jour_dimanche, jour_jeudi, jour_lundi, jour_mardi, jour_mercredi, jour_samedi,
                      jour_vendredi, lieu_naissance, medecin_traitant_id, nom, nombre_enfants, numero_assurance,
                      observation, photo_base64, pieces_jointes_json, position_id, prenom, profession1, qualite_assure,
                      salle_id, sexe, situation_familiale, sous_kt, tel_bureau, tel_mobile, tel_personnel,
                      transporteur_aller_id, transporteur_retour_id, type_patient)
SELECT p.id,
       a.adresse,
       a.adresse,
       p.assure_date_naissance,
       a.groupe_sanguin,
       '[]',
       p.assure_nom,
       p.numero_assurance,
       p.assure_prenom,
       p.assure_sexe,
       a.tel_bureau,
       a.tel_mobile,
       a.tel_personnel,
       pg_temp.uid('cat:' || p.s || ':' ||
                   CASE WHEN p.is_kt THEN 1 ELSE 1 + floor(pg_temp.rnd('ct' || p.k) * 4)::int END),
       p.center_id,
       pg_temp.uid('cp:' || p.s || ':' || p.caisse_code || ':' || p.cp_no),
       CASE
           WHEN p.sexe = 'M' THEN 'M'
           WHEN p.situation_familiale = 'CÃ©libataire' AND p.date_naissance > DATE '1990-01-01' THEN 'Mlle'
           ELSE 'Mme' END,
       p.code_patient,
       pg_temp.ts(p.date_admission, 10),
       p.date_admission,
       CASE WHEN p.is_dcd THEN p.d_end + 1 ELSE p.date_admission END,
       p.date_naissance,
       lower(regexp_replace(p.prenom || '.' || p.nom, '[^A-Za-z.]', '', 'g')) || p.n || '@gmail.com',
       p.is_dcd,
       CASE WHEN p.is_epo THEN greatest(p.date_admission, p.d_debut - 200) END,
       p.is_epo,
       p.etat_patient,
       CASE WHEN p.is_fer THEN greatest(p.date_admission, p.d_debut - 150) END,
       p.is_fer,
       pg_temp.uid('gen:' || p.s || ':' || p.salle_no || ':' || p.gen_no),
       p.groupe_sanguin,
       0 = ANY (p.jours),
       4 = ANY (p.jours),
       1 = ANY (p.jours),
       2 = ANY (p.jours),
       3 = ANY (p.jours),
       6 = ANY (p.jours),
       FALSE,
       CASE WHEN pg_temp.rnd('ln' || p.k) < 0.7 THEN p.ville ELSE p.wilaya END,
       pg_temp.uid('med:' || p.s || ':' || p.med_rang),
       p.nom,
       CASE WHEN p.situation_familiale = 'CÃ©libataire' THEN 0 ELSE floor(pg_temp.rnd('enf' || p.k) * 6)::int END,
       p.numero_assurance,
       'HÃ©modialysÃ© chronique (' || p.nephropathie || '). Prise en charge au centre ' || p.centre_nom
           || ' depuis le ' || to_char(p.date_admission, 'DD/MM/YYYY')
           || CASE WHEN p.is_vac THEN ' â€” sÃ©jour vacancier de 4 semaines.'
                   WHEN p.is_dcd THEN ' â€” patient dÃ©cÃ©dÃ© le ' || to_char(p.d_end + 1, 'DD/MM/YYYY') || '.'
                   ELSE '.'
END,
       NULL,
       '[]',
       pg_temp.uid('pos:' || p.s || ':' || p.shift),
       p.prenom,
       pg_temp.pick(ARRAY ['RetraitÃ©(e)','Enseignant(e)','CommerÃ§ant(e)','Agriculteur','Fonctionnaire','Sans profession',
           'Chauffeur','Artisan','Ouvrier','EmployÃ©(e) administratif(ve)','Comptable','Technicien'], 'pro' || p.k),
       p.qualite_assure,
       pg_temp.uid('salle:' || p.s || ':' || p.salle_no),
       p.sexe,
       p.situation_familiale,
       p.is_kt,
       pg_temp.fixe(p.tel, 'tb' || p.k),
       pg_temp.mob(p.k),
       pg_temp.fixe(p.tel, 'tp' || p.k),
       pg_temp.uid('trp:' || p.s || ':' || (1 + floor(pg_temp.rnd('ta' || p.k) * 3))::int),
       pg_temp.uid('trp:' || p.s || ':' || (1 + floor(pg_temp.rnd('tr' || p.k) * 3))::int),
       p.type_patient
FROM _p p
         JOIN assure a ON a.numero_assurance = p.numero_assurance;

INSERT INTO assure_patient (id, center_id, date_affectation, date_debut_affectation, date_fin_affectation, is_primary,
                            numero_assurance, patient_id)
SELECT pg_temp.uid('ap:' || p.id),
       p.center_id,
       pg_temp.ts(p.date_admission, 10),
       p.date_admission,
       CASE WHEN p.is_dcd THEN p.d_end + 1 WHEN p.is_vac THEN p.d_end END,
       TRUE,
       p.numero_assurance,
       p.id
FROM _p p;

-- Une attestation de droits et une prise en charge validÃ©e par patient (couvrant toute la pÃ©riode de soins)
INSERT INTO attestation_droit (id, center_id, created_at, date_debut, date_fin, patient_id)
SELECT pg_temp.uid('att:' || p.id),
       p.center_id,
       pg_temp.ts(p.d_start - 10, 9),
       p.d_start,
       CASE WHEN p.is_vac THEN p.d_end ELSE p.d_fin_droits END,
       p.id
FROM _p p;

INSERT INTO prise_en_charge (id, center_id, created_at, date_debut_demande, date_debut_effectif, date_fin_demande,
                             date_fin_effectif, forfait_demande_id, forfait_effectif_id, patient_id, statut)
SELECT pg_temp.uid('pec:' || p.id),
       p.center_id,
       pg_temp.ts(p.d_start - 8, 11),
       p.d_start,
       p.d_start,
       CASE WHEN p.is_vac THEN p.d_end ELSE p.d_fin_droits END,
       CASE WHEN p.is_vac THEN p.d_end ELSE p.d_fin_droits END,
       pg_temp.uid('forfait:' || p.s || ':' || p.forfait_code),
       pg_temp.uid('forfait:' || p.s || ':' || p.forfait_code),
       p.id,
       'VALIDEE'
FROM _p p;

-- =====================================================================================================================
-- 5. SÃ‰ANCES (12 mois + mois courant)
-- =====================================================================================================================
CREATE
TEMP TABLE _s ON COMMIT DROP
AS
WITH d AS (SELECT p.id AS patient_id, p.center_id, p.s, p.slug, p.k, p.n, p.shift, p.salle_no, p.gen_no, p.is_kt,
                  p.is_epo, p.is_fer, p.anticoag, p.forfait_code, p.poids_sec, p.med_rang, p.is_vac, p.code_patient,
                  g::date AS date_seance
           FROM _p p
                    CROSS JOIN LATERAL generate_series(p.d_start::timestamp, p.d_end::timestamp, INTERVAL '1 day') AS g
           WHERE extract(DOW FROM g)::int = ANY (p.jours)
             AND pg_temp.rnd('abs' || p.k || g::date) >= 0.03
             AND NOT EXISTS (SELECT 1 FROM _hol h WHERE h.d = g::date)
             AND NOT EXISTS (SELECT 1 FROM _clo x WHERE x.center_id = p.center_id AND x.d = g::date)),
     e AS (SELECT d.*,
                  pg_temp.uid('seance:' || d.patient_id || ':' || d.date_seance)        AS id,
                  date_trunc('month', d.date_seance)::date                              AS mois,
                  CASE
                      WHEN d.date_seance < cfg.d_mois THEN 'FACTUREE'
                      WHEN d.date_seance <= cfg.d_fin - 3 THEN 'SIGNEE'
                      WHEN d.date_seance < cfg.d_fin THEN 'VALIDEE'
                      ELSE 'CREE' END                                                   AS statut,
                  (NOT d.is_vac AND pg_temp.rnd('ovr' || d.k || d.date_seance) < 0.01) AS is_override,
                  6.5 + (d.shift - 1) * 4.5                                             AS h_debut
           FROM d
                    CROSS JOIN _cfg cfg)
SELECT e.*,
       CASE
           WHEN e.is_override THEN CASE WHEN e.forfait_code = 'HDF-OL' THEN 'HD-CONV' ELSE 'HDF-OL' END
           ELSE e.forfait_code END                                         AS forfait_eff,
       pg_temp.uid('user:infirmier' || (1 + e.shift % 2) || '.' || e.slug) AS inf_id,
       'infirmier' || (1 + e.shift % 2) || '.' || e.slug                   AS inf_username,
       pg_temp.uid('user:medecin' || e.med_rang || '.' || e.slug)          AS med_user_id,
       'medecin' || e.med_rang || '.' || e.slug                            AS med_username
FROM e;

CREATE UNIQUE INDEX ON _s (id);
CREATE INDEX ON _s (patient_id, mois);
ANALYZE
_s;

INSERT INTO seances (id, center_id, created_at, date_seance, facture_id, forfait_override_code, forfait_override_id,
                     forfait_override_nom, forfait_override_prix, forfait_override_updated_at,
                     forfait_override_updated_by, patient_id, signed_infirmier_at, signed_infirmier_by,
                     signed_medecin_at, signed_medecin_by, statut, validated_at)
SELECT s.id,
       s.center_id,
       pg_temp.ts(s.date_seance - 2, 14),
       s.date_seance,
       NULL,
       CASE WHEN s.is_override THEN f.code END,
       CASE WHEN s.is_override THEN f.id END,
       CASE WHEN s.is_override THEN f.libelle END,
       CASE WHEN s.is_override THEN f.prix END,
       CASE WHEN s.is_override THEN pg_temp.ts(s.date_seance, s.h_debut + 0.5) END,
       CASE WHEN s.is_override THEN s.med_username END,
       s.patient_id,
       CASE WHEN s.statut <> 'CREE' THEN pg_temp.ts(s.date_seance, s.h_debut + 4.25) END,
       CASE WHEN s.statut <> 'CREE' THEN s.inf_id::text END,
       CASE WHEN s.statut IN ('SIGNEE', 'FACTUREE') THEN pg_temp.ts(s.date_seance, 19.5) END,
       CASE WHEN s.statut IN ('SIGNEE', 'FACTUREE') THEN s.med_user_id::text END,
       s.statut,
       CASE WHEN s.statut <> 'CREE' THEN pg_temp.ts(s.date_seance, s.h_debut + 4.25) END
FROM _s s
         JOIN _fo f ON f.center_id = s.center_id AND f.code = s.forfait_eff;

-- Volet paramÃ©dical (toutes les sÃ©ances rÃ©alisÃ©es)
CREATE
TEMP TABLE _vp ON COMMIT DROP
AS
SELECT s.id                                                       AS seance_id,
       s.center_id,
       s.date_seance,
       s.h_debut,
       s.anticoag,
       s.is_kt,
       s.poids_sec,
       s.forfait_eff,
       s.id::text AS kk, pg_temp.rnd('inc' || s.id) AS r_inc,
       round(1.2 + 2.6 * pg_temp.rnd('gain' || s.id)::numeric, 1) AS gain,
       round(0.4 * pg_temp.rnd('res' || s.id)::numeric, 1)        AS residu,
       125 + floor(pg_temp.rnd('tas' || s.id) * 40)::int                          AS tas, 70 + floor(pg_temp.rnd('tad' || s.id) * 20) ::int                           AS tad
FROM _s s
WHERE s.statut <> 'CREE';

INSERT INTO volet_paramedical (id, anticoagulant, center_id, created_at, debit_sang_ml_min, duree_minutes, incidents,
                               poids_apres_kg, poids_avant_kg, seance_id, ta_apres, ta_avant, type_dialysat,
                               ultrafiltration_ml, updated_at)
SELECT pg_temp.uid('vp:' || v.seance_id),
       CASE v.anticoag
           WHEN 'HNF' THEN 'HNF 2 500 UI en bolus puis 1 000 UI/h'
           WHEN 'HBPM' THEN 'Ã‰noxaparine 4 000 UI en dÃ©but de sÃ©ance'
           ELSE 'Sans hÃ©parine â€” verrou citrate 4 %' END,
       v.center_id,
       pg_temp.ts(v.date_seance, v.h_debut),
       CASE WHEN v.is_kt THEN 250 + 10 * floor(pg_temp.rnd('qb' || v.kk) * 6)::int
            ELSE 280 + 10 * floor(pg_temp.rnd('qb' || v.kk) * 8)::int
END,
       CASE WHEN v.r_inc >= 0.91 AND v.r_inc < 0.96 OR v.r_inc >= 0.98 THEN 210 ELSE 240
END,
       CASE
           WHEN v.r_inc < 0.85 THEN 'Aucun incident'
           WHEN v.r_inc < 0.91 THEN 'Crampes musculaires en fin de sÃ©ance, cÃ©dant sous sÃ©rum salÃ© isotonique'
           WHEN v.r_inc < 0.96 THEN 'Hypotension artÃ©rielle per-dialytique (TA 90/55), UF rÃ©duite, sÃ©ance Ã©courtÃ©e'
           WHEN v.r_inc < 0.98 THEN 'CÃ©phalÃ©es en cours de sÃ©ance, paracÃ©tamol 1 g'
           ELSE 'Coagulation partielle du circuit, restitution anticipÃ©e'
END,
       v.poids_sec + v.residu,
       v.poids_sec + v.gain,
       v.seance_id,
       (v.tas - 5 - floor(pg_temp.rnd('tas2' || v.kk) * 15)::int) || '/' || (v.tad - floor(pg_temp.rnd('tad2' || v.kk) * 8)::int),
       v.tas || '/' || v.tad,
       CASE WHEN v.forfait_eff = 'HDF-OL' THEN 'Bicarbonate ultrapur (HDF en ligne) â€” K+ 2 mmol/l, Ca++ 1,5 mmol/l'
            ELSE 'Bicarbonate â€” K+ 2 mmol/l, Ca++ 1,5 mmol/l'
END,
       (v.gain - v.residu) * 1000,
       pg_temp.ts(v.date_seance, v.h_debut + 4.25)
FROM _vp v;

INSERT INTO volet_medical (id, ajustements_therapeutiques, center_id, conclusion_medicale, created_at, examen_clinique,
                           prescription, resultats_biologiques, seance_id, tolerance_seance, updated_at)
SELECT pg_temp.uid('vm:' || v.seance_id),
       CASE
           WHEN v.r_inc >= 0.85 AND v.r_inc < 0.96
               THEN 'RÃ©Ã©valuation du poids sec (+0,5 kg) ; UF horaire limitÃ©e Ã  800 ml/h'
           ELSE 'Poids sec maintenu Ã  ' || v.poids_sec || ' kg ; pas de modification thÃ©rapeutique' END,
       v.center_id,
       CASE
           WHEN v.r_inc < 0.85 THEN 'SÃ©ance conforme Ã  la prescription, objectifs atteints'
           ELSE 'SÃ©ance rÃ©alisÃ©e avec incident maÃ®trisÃ© â€” surveillance renforcÃ©e Ã  la prochaine sÃ©ance' END,
       pg_temp.ts(v.date_seance, v.h_debut + 0.5),
       pg_temp.pick(
               ARRAY['Patient conscient, eupnÃ©ique, apyrÃ©tique. Auscultation cardio-pulmonaire normale. Pas d''Å“dÃ¨mes des membres infÃ©rieurs.',
               'Bon Ã©tat gÃ©nÃ©ral. Abord vasculaire fonctionnel, thrill perÃ§u, pas de signe inflammatoire.',
               'Examen clinique sans particularitÃ©. Discrets Å“dÃ¨mes des chevilles. Abord vasculaire sans anomalie.',
               'Patient asthÃ©nique, conjonctives lÃ©gÃ¨rement pÃ¢les. Auscultation normale. Abord vasculaire fonctionnel.'],
               'ec' || v.kk),
       'Poursuite du traitement habituel ; dialyse 3 Ã— 4 h/semaine',
       'Cf. bilan mensuel du ' || to_char(date_trunc('month', v.date_seance), 'MM/YYYY'),
       v.seance_id,
       CASE
           WHEN v.r_inc < 0.85 THEN 'Bonne tolÃ©rance clinique et hÃ©modynamique'
           WHEN v.r_inc < 0.91 THEN 'TolÃ©rance correcte â€” crampes de fin de sÃ©ance'
           WHEN v.r_inc < 0.96 THEN 'TolÃ©rance mÃ©diocre â€” Ã©pisode hypotensif'
           ELSE 'TolÃ©rance correcte malgrÃ© incident technique' END,
       pg_temp.ts(v.date_seance, 19.5)
FROM _vp v;

-- =====================================================================================================================
-- 6. DOSSIER MÃ‰DICAL
-- =====================================================================================================================
INSERT INTO dossier_medical_patient (id, center_id, conclusion_medicale, created_at, date_mise_en_dialyse,
                                     hepatite_b_statut, hepatite_c_statut, nephropathie_initiale, observation_globale,
                                     patient_id, updated_at)
SELECT pg_temp.uid('dmp:' || p.id),
       p.center_id,
       CASE
           WHEN p.is_dcd THEN 'DÃ©cÃ¨s survenu le ' || to_char(p.d_end + 1, 'DD/MM/YYYY') ||
                              ' (arrÃªt cardio-respiratoire Ã  domicile).'
           ELSE 'Patient stable en hÃ©modialyse chronique, dialyse adÃ©quate (Kt/V > 1,2), anÃ©mie contrÃ´lÃ©e. '
               || 'Poursuite de la prise en charge actuelle.' END,
       pg_temp.ts(p.date_admission, 11),
       p.date_dialyse,
       CASE WHEN p.is_vhb THEN 'PORTEUR' WHEN p.is_vaccine THEN 'VACCINE' ELSE 'NEGATIF' END,
       CASE WHEN p.is_vhc THEN 'POSITIF' ELSE 'NEGATIF' END,
       p.nephropathie,
       'Insuffisance rÃ©nale chronique terminale sur ' || lower(p.nephropathie) || '. Mise en dialyse le '
           || to_char(p.date_dialyse, 'DD/MM/YYYY') || ' sur '
           || CASE WHEN p.is_kt THEN 'cathÃ©ter tunnelisÃ© jugulaire' ELSE 'fistule artÃ©rio-veineuse' END
           || '. Poids sec : ' || p.poids_sec || ' kg. Groupe sanguin ' || p.groupe_sanguin || '.',
       p.id,
       pg_temp.ts(least(p.d_end, p.d_fin), 18)
FROM _p p;

INSERT INTO antecedents_medicaux (id, center_id, created_at, date_debut, date_fin, diagnostic_code,
                                  diagnostic_code_display, diagnostic_code_system, libelle_libre, note, patient_id,
                                  severite, statut_clinique, type_antecedent, updated_at)
SELECT pg_temp.uid('ant:' || p.id || ':' || a.no),
       p.center_id,
       pg_temp.ts(p.date_admission, 11),
       a.debut,
       a.fin,
       a.code,
       a.display,
       CASE WHEN a.code IS NULL THEN NULL ELSE 'CIM10' END,
       a.libre,
       a.note,
       p.id,
       a.severite,
       a.statut,
       a.type_ant,
       pg_temp.ts(p.date_admission, 11)
FROM _p p
         CROSS JOIN LATERAL (VALUES (1, 'MEDICAL', 'I10', 'Hypertension essentielle (primitive)', NULL::text,
                                     'HTA traitÃ©e par inhibiteur calcique', 'ModÃ©rÃ©e', 'ACTIF',
                                     p.date_dialyse - 2500 - floor(pg_temp.rnd('a1' || p.k) * 2000)::int, NULL::date),
                                    (2, 'COMORBIDITE',
                                     CASE
                                         WHEN p.nephropathie = 'NÃ©phropathie diabÃ©tique' THEN 'E11.2'
                                         ELSE pg_temp.pick(ARRAY['I25.1', 'E78.5', 'I48', 'J44.9'], 'a2' || p.k) END,
                                     CASE
                                         WHEN p.nephropathie = 'NÃ©phropathie diabÃ©tique'
                                             THEN 'DiabÃ¨te de type 2 avec complications rÃ©nales'
                                         ELSE CASE pg_temp.pick(ARRAY['I25.1', 'E78.5', 'I48', 'J44.9'], 'a2' || p.k)
                                                  WHEN 'I25.1' THEN 'Cardiopathie athÃ©rosclÃ©reuse'
                                                  WHEN 'E78.5' THEN 'HyperlipidÃ©mie, sans prÃ©cision'
                                                  WHEN 'I48' THEN 'Fibrillation et flutter auriculaires'
                                                  ELSE 'Bronchopneumopathie chronique obstructive' END END,
                                     NULL, 'Suivi spÃ©cialisÃ© semestriel', 'SÃ©vÃ¨re', 'ACTIF',
                                     p.date_dialyse - 1500 - floor(pg_temp.rnd('a3' || p.k) * 2000)::int, NULL),
                                    (3, 'CHIRURGICAL', NULL, NULL,
                                     CASE
                                         WHEN p.is_kt THEN 'Pose de cathÃ©ter tunnelisÃ© jugulaire interne droit'
                                         ELSE 'CrÃ©ation d''une fistule artÃ©rio-veineuse radio-cÃ©phalique' END,
                                     'Intervention sans complication', 'LÃ©gÃ¨re', 'RESOLU',
                                     p.date_dialyse - 60, p.date_dialyse - 30),
                                    (4, 'FAMILIAL', 'Z82.4', 'AntÃ©cÃ©dents familiaux de cardiopathie ischÃ©mique',
                                     NULL, 'PÃ¨re suivi pour coronaropathie', NULL, 'ACTIF',
                                     p.date_naissance, NULL)) AS a(no, type_ant, code, display, libre, note, severite,
                                                                   statut, debut, fin);

CREATE
TEMP TABLE _alg ON COMMIT DROP
AS
SELECT *
FROM (VALUES (1, 'MEDICAMENT', 'J01CA04', 'Amoxicilline', 'ATC', 'ALLERGIE', 'HAUTE',
              'Urticaire gÃ©nÃ©ralisÃ©e, Å“dÃ¨me de Quincke'),
             (2, 'MEDICAMENT', 'N02BA01', 'Acide acÃ©tylsalicylique', 'ATC', 'INTOLERANCE', 'BASSE', 'Ã‰pigastralgies'),
             (3, 'MEDICAMENT', 'V08AB', 'Produits de contraste iodÃ©s', 'ATC', 'ALLERGIE', 'HAUTE',
              'RÃ©action anaphylactoÃ¯de'),
             (4, 'ENVIRONNEMENT', 'LATEX', 'Latex', 'LOCAL', 'ALLERGIE', 'BASSE', 'EczÃ©ma de contact'),
             (5, 'ALIMENT', 'ARACHIDE', 'Arachide', 'LOCAL', 'ALLERGIE', 'HAUTE', 'Prurit, Å“dÃ¨me labial'),
             (6, 'ENVIRONNEMENT', 'POLLEN', 'Pollens de graminÃ©es', 'LOCAL', 'ALLERGIE', 'BASSE',
              'Rhinite saisonniÃ¨re'),
             (7, 'MEDICAMENT', 'J01EE01', 'SulfamÃ©thoxazole-trimÃ©thoprime', 'ATC', 'ALLERGIE', 'HAUTE',
              'Ã‰ruption cutanÃ©e maculo-papuleuse'),
             (8, 'BIOLOGIQUE', 'VENIN', 'Venin d''hymÃ©noptÃ¨res', 'LOCAL', 'ALLERGIE', 'HAUTE',
              'Å’dÃ¨me local Ã©tendu'))
         AS t(no, categorie, code, display, systeme, type_reaction, criticite, manifestations);

INSERT INTO allergies_patient (id, categorie, center_id, created_at, criticite, date_constatation, manifestations,
                               patient_id, statut_verification, substance_code, substance_code_display,
                               substance_code_system, type_reaction, updated_at)
SELECT pg_temp.uid('alg:' || p.id),
       a.categorie,
       p.center_id,
       pg_temp.ts(p.date_admission, 11),
       a.criticite,
       p.date_admission - 400 - floor(pg_temp.rnd('ald' || p.k) * 3000)::int, a.manifestations,
       p.id,
       CASE WHEN pg_temp.rnd('alv' || p.k) < 0.8 THEN 'CONFIRMEE' ELSE 'SUSPECTEE' END,
       a.code,
       a.display,
       a.systeme,
       a.type_reaction,
       pg_temp.ts(p.date_admission, 11)
FROM _p p
         JOIN _alg a ON a.no = 1 + floor(pg_temp.rnd('alg' || p.k) * 8)::int;

-- SÃ©rologies : 2 campagnes semestrielles Ã— 6 marqueurs
CREATE
TEMP TABLE _sero ON COMMIT DROP
AS
SELECT p.id                                                AS patient_id,
       p.center_id,
       p.k,
       p.ville,
       p.is_vhb,
       p.is_vhc,
       p.is_vaccine,
       c.camp,
       p.d_start + CASE c.camp WHEN 1 THEN 20 ELSE 200 END AS d,
       m.marqueur
FROM _p p
         CROSS JOIN (VALUES (1), (2)) AS c(camp)
         CROSS JOIN (VALUES ('AG_HBS'), ('AC_HBS'), ('AC_HBC'), ('AC_VHC'), ('VIH_AC'), ('TPHA')) AS m(marqueur)
WHERE p.d_start + CASE c.camp WHEN 1 THEN 20 ELSE 200 END <= least(p.d_end, p.d_fin);

INSERT INTO serologies_patient (id, center_id, conduite_a_tenir, created_at, date_prelevement, date_prochain_controle,
                                laboratoire, marqueur, patient_id, resultat, titre, unite, updated_at)
SELECT pg_temp.uid('sero:' || s.patient_id || ':' || s.camp || ':' || s.marqueur),
       s.center_id,
       CASE
           WHEN (s.marqueur IN ('AG_HBS', 'AC_HBC') AND s.is_vhb) OR (s.marqueur = 'AC_VHC' AND s.is_vhc)
               THEN 'Dialyse en salle d''isolement sur gÃ©nÃ©rateur dÃ©diÃ© ; avis hÃ©patologique'
           WHEN s.marqueur = 'AC_HBS' AND NOT s.is_vaccine AND NOT s.is_vhb
               THEN 'Programme de vaccination anti-VHB renforcÃ© (4 doubles doses)'
           ELSE 'ContrÃ´le semestriel' END,
       pg_temp.ts(s.d, 12),
       s.d,
       (s.d + INTERVAL '6 months')::date, 'Laboratoire d''analyses mÃ©dicales de ' || s.ville,
       s.marqueur,
       s.patient_id,
       CASE
           WHEN s.marqueur IN ('AG_HBS', 'AC_HBC') AND s.is_vhb THEN 'POSITIF'
           WHEN s.marqueur = 'AC_VHC' AND s.is_vhc THEN 'POSITIF'
           WHEN s.marqueur = 'AC_HBS' AND s.is_vaccine THEN 'POSITIF'
           ELSE 'NEGATIF' END,
       CASE
           WHEN s.marqueur = 'AC_HBS' THEN
               CASE
                   WHEN s.is_vaccine THEN round(pg_temp.rng('ti' || s.k || s.camp, 15, 480), 0)
                   ELSE round(pg_temp.rng('ti' || s.k || s.camp, 0, 9), 1) END END,
       CASE WHEN s.marqueur = 'AC_HBS' THEN 'mUI/ml' END,
       pg_temp.ts(s.d + 3, 12)
FROM _sero s;

-- Abords vasculaires : abord actuel + cathÃ©ter provisoire historique
INSERT INTO abords_vasculaires (id, actif, center_id, complications, cote, created_at, date_creation, date_fin,
                                localisation, patient_id, type_abord)
SELECT pg_temp.uid('abord:' || p.id || ':1'),
       TRUE,
       p.center_id,
       CASE
           WHEN pg_temp.rnd('cpl' || p.k) < 0.8 THEN 'Aucune'
           ELSE 'StÃ©nose veineuse traitÃ©e par angioplastie transluminale' END,
       CASE WHEN p.is_kt THEN 'DROIT' WHEN pg_temp.rnd('cote' || p.k) < 0.8 THEN 'GAUCHE' ELSE 'DROIT' END,
       pg_temp.ts(p.date_admission, 11),
       CASE WHEN p.is_kt THEN p.date_dialyse ELSE p.date_dialyse - 60 END,
       NULL,
       CASE
           WHEN p.is_kt THEN 'Veine jugulaire interne'
           WHEN pg_temp.rnd('fav' || p.k) < 0.08 THEN 'Pontage prothÃ©tique humÃ©ro-axillaire (PTFE)'
           WHEN pg_temp.rnd('loc' || p.k) < 0.6 THEN 'Radio-cÃ©phalique Ã  l''avant-bras'
           ELSE 'HumÃ©ro-cÃ©phalique au pli du coude' END,
       p.id,
       CASE WHEN p.is_kt THEN 'KT_TUNNELISE' WHEN pg_temp.rnd('fav' || p.k) < 0.08 THEN 'PTFE' ELSE 'FAV' END
FROM _p p
UNION ALL
SELECT pg_temp.uid('abord:' || p.id || ':0'),
       FALSE,
       p.center_id,
       'RetirÃ© aprÃ¨s maturation de la fistule',
       'DROIT',
       pg_temp.ts(p.date_admission, 11),
       p.date_dialyse,
       p.date_dialyse + 50,
       'Veine fÃ©morale droite',
       p.id,
       'KT_AIGU'
FROM _p p
WHERE NOT p.is_kt
  AND pg_temp.rnd('kta' || p.k) < 0.5;

-- Prescriptions mÃ©dicales (initiale + rÃ©vision semestrielle)
CREATE
TEMP TABLE _pr ON COMMIT DROP
AS
SELECT p.*, x.no, x.d AS date_prescription, pg_temp.uid('presc:' || p.id || ':' || x.no) AS presc_id
FROM _p p
         CROSS JOIN LATERAL (VALUES (1, p.presc1_date), (2, p.presc2_date)) AS x(no, d)
WHERE x.no = 1
   OR p.has_presc2;

INSERT INTO prescriptions_medicales (id, anticoag_type_prescrit, center_id, created_at, date_prescription,
                                     duree_cible_min, epo_article_id, epo_dose_ui, epo_frequence_unite,
                                     epo_frequence_valeur, epo_voie, fer_article_id, fer_dose_mg, fer_frequence_unite,
                                     fer_frequence_valeur, fer_voie, medecin_id, patient_id, qb_cible, qd_cible,
                                     type_dialyseur_prescrit, uf_max_ml, updated_at)
SELECT r.presc_id,
       CASE r.anticoag WHEN 'CITRATE' THEN 'CITRATE' ELSE r.anticoag END,
       r.center_id,
       pg_temp.ts(r.date_prescription, 11),
       r.date_prescription,
       240,
       CASE WHEN r.is_epo THEN pg_temp.uid('art:' || r.s || ':EPO-4000') END,
       CASE WHEN r.is_epo THEN CASE WHEN r.no = 1 THEN 4000 ELSE 4000 END END,
       CASE WHEN r.is_epo THEN 'SEMAINE' END,
       CASE WHEN r.is_epo THEN 1 END,
       CASE WHEN r.is_epo THEN CASE WHEN r.forfait_code = 'HDF-OL' THEN 'IV' ELSE 'SC' END END,
       CASE WHEN r.is_fer THEN pg_temp.uid('art:' || r.s || ':FER-100') END,
       CASE WHEN r.is_fer THEN 100 END,
       CASE WHEN r.is_fer THEN 'MOIS' END,
       CASE WHEN r.is_fer THEN 1 END,
       CASE WHEN r.is_fer THEN 'IV' END,
       pg_temp.uid('med:' || r.s || ':' || r.med_rang),
       r.id,
       CASE WHEN r.is_kt THEN 280 ELSE 320 END,
       500,
       CASE
           WHEN r.forfait_code IN ('HDF-OL', 'HD-EPO') THEN 'FX80 haute permÃ©abilitÃ© 1,8 mÂ²'
           ELSE 'F8 HPS basse permÃ©abilitÃ© 1,8 mÂ²' END,
       CASE WHEN r.no = 1 THEN 3500 ELSE 3200 END,
       pg_temp.ts(r.date_prescription, 11)
FROM _pr r;

-- Administrations anÃ©mie : EPO hebdomadaire + fer mensuel (1re sÃ©ance rÃ©alisÃ©e de la semaine / du mois)
CREATE
TEMP TABLE _adm ON COMMIT DROP
AS
WITH e AS (SELECT s.*, 'EPO'::text AS tt,
                  row_number() OVER (PARTITION BY s.patient_id, date_trunc('week', s.date_seance) ORDER BY s.date_seance) AS r
           FROM _s s
           WHERE s.is_epo
             AND s.statut <> 'CREE'),
     f AS (SELECT s.*, 'FER_INJECTABLE'::text AS tt,
                  row_number() OVER (PARTITION BY s.patient_id, s.mois ORDER BY s.date_seance) AS r
           FROM _s s
           WHERE s.is_fer
             AND s.statut <> 'CREE')
SELECT x.*,
       pg_temp.uid('adm:' || x.id || ':' || x.tt)  AS adm_id,
       pg_temp.rnd('nadm' || x.id || x.tt) >= 0.05 AS administree
FROM (SELECT * FROM e WHERE r = 1 UNION ALL SELECT * FROM f WHERE r = 1) x;

INSERT INTO administrations_anemie (id, administre_par, administree, article_id, center_id, created_at,
                                    date_administration, dose, molecule, motif_non_administration, patient_id,
                                    prescription_medicale_id, quantite_article, seance_id, type_traitement,
                                    unite_dose, voie)
SELECT a.adm_id,
       CASE WHEN a.administree THEN a.inf_username END,
       a.administree,
       pg_temp.uid('art:' || a.s || CASE a.tt WHEN 'EPO' THEN ':EPO-4000' ELSE ':FER-100' END),
       a.center_id,
       pg_temp.ts(a.date_seance, a.h_debut + 3.75),
       a.date_seance,
       CASE a.tt WHEN 'EPO' THEN 4000 ELSE 100 END,
       CASE a.tt WHEN 'EPO' THEN 'Ã‰poÃ©tine alfa' ELSE 'Fer saccharose' END,
       CASE
           WHEN NOT a.administree THEN pg_temp.pick(ARRAY['Patient hypertendu en dÃ©but de sÃ©ance (TA > 180/100)',
                                                    'HÃ©moglobine > 12 g/dl â€” injection suspendue',
                                                    'Refus du patient',
                                                    'Rupture de stock ponctuelle'], 'mot' || a.adm_id) END,
       a.patient_id,
       CASE WHEN pr2.presc_id IS NOT NULL THEN pr2.presc_id ELSE pg_temp.uid('presc:' || a.patient_id || ':1') END,
       CASE WHEN a.administree THEN 1 ELSE 0 END,
       a.id,
       a.tt,
       CASE a.tt WHEN 'EPO' THEN 'UI' ELSE 'mg' END,
       CASE WHEN a.tt = 'EPO' AND a.forfait_code <> 'HDF-OL' THEN 'SC' ELSE 'IV' END
FROM _adm a
         LEFT JOIN _pr pr2 ON pr2.id = a.patient_id AND pr2.no = 2 AND a.date_seance >= pr2.date_prescription;

INSERT INTO alertes_observance (id, center_id, created_at, doses_administrees, doses_attendues, message, patient_id,
                                periode_debut, periode_fin, resolved_at, type_alerte, type_traitement)
SELECT pg_temp.uid('alo:' || a.adm_id),
       a.center_id,
       pg_temp.ts(a.date_seance, 20),
       0,
       1,
       CASE a.tt
           WHEN 'EPO' THEN 'Dose hebdomadaire d''EPO non administrÃ©e'
           ELSE 'Injection mensuelle de fer non administrÃ©e' END,
       a.patient_id,
       CASE a.tt WHEN 'EPO' THEN date_trunc('week', a.date_seance)::date ELSE a.mois END,
       CASE a.tt WHEN 'EPO' THEN date_trunc('week', a.date_seance)::date + 6
                 ELSE (a.mois + INTERVAL '1 month - 1 day')::date
END,
       CASE WHEN a.date_seance + 7 < cfg.d_fin THEN pg_temp.ts(a.date_seance + 7, 10)
END,
       'RETARD_CONSTATE',
       a.tt
FROM _adm a
         CROSS JOIN _cfg cfg
WHERE NOT a.administree
UNION ALL
SELECT pg_temp.uid('alo:rappel:' || p.id),
       p.center_id,
       pg_temp.ts(cfg.d_mois, 7),
       0,
       1,
       'Injection de fer mensuelle Ã  planifier avant la fin du mois',
       p.id,
       cfg.d_mois,
       (cfg.d_mois + INTERVAL '1 month - 1 day')::date, NULL,
       'RAPPEL_ECHEANCE',
       'FER_INJECTABLE'
FROM _p p
         CROSS JOIN _cfg cfg
WHERE p.is_fer
  AND p.n % 5 = 0
  AND p.d_end >= cfg.d_mois;

-- Bilans biologiques mensuels (1re sÃ©ance rÃ©alisÃ©e du mois)
CREATE
TEMP TABLE _res ON COMMIT DROP
AS
WITH f AS (SELECT DISTINCT ON (s.patient_id, s.mois) s.patient_id, s.center_id, s.s, s.k, s.mois, s.date_seance AS d,
                                                      s.poids_sec, s.med_rang
           FROM _s s
           WHERE s.statut <> 'CREE'
           ORDER BY s.patient_id, s.mois, s.date_seance),
     v AS (SELECT f.*, f.k || f.mois AS rk FROM f)
SELECT v.*,
       pg_temp.uid('res:' || v.patient_id || ':' || v.mois) AS id,
       ((extract(YEAR FROM v.mois) * 12 + extract(MONTH FROM v.mois))::int % 3 = 0) AS trimestriel,
       round(pg_temp.rng('hb' || v.rk, 9.2, 12.8), 1)                           AS hb,
       round(pg_temp.rng('fe' || v.rk, 180, 780), 0)                            AS ferritine,
       round(pg_temp.rng('cs' || v.rk, 18, 42), 1)                              AS cstf,
       round(pg_temp.rng('pth' || v.rk, 120, 650), 0)                           AS pth,
       round(pg_temp.rng('ph' || v.rk, 3.2, 6.8), 1)                            AS phosphore,
       round(pg_temp.rng('ca' || v.rk, 8.2, 10.2), 1)                           AS calcium,
       round(pg_temp.rng('al' || v.rk, 3.3, 4.4), 1)                            AS albumine,
       round(pg_temp.rng('pr' || v.rk, 6.0, 7.8), 1)                            AS proteines,
       round(pg_temp.rng('crp' || v.rk, 1, 18), 1)                              AS crp,
       round(pg_temp.rng('up' || v.rk, 110, 190), 0)                            AS uree_pre,
       round(pg_temp.rng('ur' || v.rk, 0.25, 0.35), 3)                          AS ratio_r,
       round(pg_temp.rng('cr' || v.rk, 6.5, 12.5), 1)                           AS creatinine,
       floor(pg_temp.rng('pl' || v.rk, 140000, 360000))::int                    AS plaquettes,
       round(pg_temp.rng('ee' || v.rk, 5, 25), 1)                               AS epo_endo
FROM v;

INSERT INTO resultats_analyses (id, albumine_g_dl, calcium_mg_dl, center_id, created_at, creatinine_mg_dl, crp_mg_l,
                                cstf_pct, date_prelevement, epo_endogene_mui_ml, ferritine_ng_ml, hb_g_dl, ht_pct,
                                kt_v_mensuel, patient_id, phosphore_mg_dl, plaquettes, proteines_g_dl, pth_pg_ml,
                                updated_at, uree_post_mg_dl, uree_pre_mg_dl)
SELECT r.id,
       r.albumine,
       r.calcium,
       r.center_id,
       pg_temp.ts(r.d + 1, 14),
       r.creatinine,
       r.crp,
       r.cstf,
       r.d,
       r.epo_endo,
       r.ferritine,
       r.hb,
       round(r.hb * 3, 1),
       round((-ln((r.ratio_r - 0.032):: double precision) +
              (4 - 3.5 * r.ratio_r::double precision) * 2.5 / r.poids_sec::double precision):: numeric, 2),
       r.patient_id,
       r.phosphore,
       r.plaquettes,
       r.proteines,
       r.pth,
       pg_temp.ts(r.d + 1, 14),
       round(r.uree_pre * r.ratio_r, 0),
       r.uree_pre
FROM _res r;

-- Demandes d'examens : bilan trimestriel (biologie) + Ã©cho-Doppler, ECG, radiographie
CREATE
TEMP TABLE _dem ON COMMIT DROP
AS
SELECT pg_temp.uid('dem:' || r.patient_id || ':' || r.mois) AS id,
       r.patient_id,
       r.center_id,
       r.s,
       r.med_rang,
       r.d - 3                                              AS d,
       'BIOLOGIE'::text AS categorie, CASE
                                          WHEN r.mois = cfg.d_mois THEN 'RESULTAT_DISPONIBLE'
                                          ELSE 'VALIDE' END AS statut,
       'Bilan trimestriel de surveillance du patient hÃ©modialysÃ©'::text AS motif, CASE
                                                                                      WHEN r.hb < 10
                                                                                          THEN 'AnÃ©mie sous la cible KDIGO (Hb < 10 g/dl) : majoration de l''EPO Ã  discuter.'
                                                                                      WHEN r.phosphore > 5.5
                                                                                          THEN 'HyperphosphorÃ©mie : renforcer les chÃ©lateurs et les conseils diÃ©tÃ©tiques.'
                                                                                      ELSE 'Bilan dans les cibles thÃ©rapeutiques.' END AS conclusion,
       FALSE                                                AS urgent,
       'BIO'::text AS kind
FROM _res r
         CROSS JOIN _cfg cfg
WHERE r.trimestriel
UNION ALL
SELECT pg_temp.uid('dem:' || p.id || ':' || x.kind),
       p.id,
       p.center_id,
       p.s,
       p.med_rang,
       x.d,
       x.categorie,
       x.statut,
       x.motif,
       x.conclusion,
       x.urgent,
       x.kind
FROM _p p
         CROSS JOIN _cfg cfg
         CROSS JOIN LATERAL (VALUES ('ECHO', p.d_start + 40, 'IMAGERIE', 'VALIDE',
                                     'Ã‰cho-Doppler de l''abord vasculaire (contrÃ´le de dÃ©bit)',
                                     'DÃ©bit de fistule satisfaisant (â‰ˆ 900 ml/min), pas de stÃ©nose significative.',
                                     FALSE),
                                    ('ECG', p.d_start + 150, 'FONCTIONNEL', 'VALIDE',
                                     'Ã‰lectrocardiogramme de repos â€” bilan cardiovasculaire annuel',
                                     'Rythme sinusal rÃ©gulier, pas de trouble de la repolarisation.', FALSE),
                                    ('RXT', cfg.d_fin - 2, 'IMAGERIE', 'DEMANDE',
                                     'Radiographie thoracique de face â€” recherche de surcharge', NULL, TRUE))
    AS x(kind, d, categorie, statut, motif, conclusion, urgent)
WHERE x.d <= least(p.d_end, cfg.d_fin);

INSERT INTO demandes_examen (id, categorie, center_id, conclusion, created_at, date_demande, motif, patient_id,
                             prescripteur_id, statut, updated_at, urgent)
SELECT d.id,
       d.categorie,
       d.center_id,
       d.conclusion,
       pg_temp.ts(d.d, 10),
       d.d,
       d.motif,
       d.patient_id,
       pg_temp.uid('med:' || d.s || ':' || d.med_rang)::text, d.statut,
       pg_temp.ts(d.d + 4, 16),
       d.urgent
FROM _dem d;

CREATE
TEMP TABLE _analyte ON COMMIT DROP
AS
SELECT *
FROM (VALUES (1, '718-7', 'HÃ©moglobine [Masse/Volume] Sang', 'g/dL'),
             (2, '2777-1', 'Phosphate [Masse/Volume] SÃ©rum ou Plasma', 'mg/dL'),
             (3, '17861-6', 'Calcium [Masse/Volume] SÃ©rum ou Plasma', 'mg/dL'),
             (4, '1751-7', 'Albumine [Masse/Volume] SÃ©rum ou Plasma', 'g/dL'),
             (5, '2276-4', 'Ferritine [Masse/Volume] SÃ©rum ou Plasma', 'ng/mL'),
             (6, '2731-8', 'Parathormone intacte [Masse/Volume] SÃ©rum ou Plasma',
              'pg/mL')) AS t(no, code, display, unite);

INSERT INTO lignes_demande_examen (id, analyte_code, analyte_code_display, analyte_code_system, commentaire, demande_id,
                                   libelle)
SELECT pg_temp.uid('ldem:' || d.id || ':' || a.no), a.code, a.display, 'LOINC', NULL, d.id, a.display
FROM _dem d
         CROSS JOIN _analyte a
WHERE d.kind = 'BIO'
UNION ALL
SELECT pg_temp.uid('ldem:' || d.id || ':1'),
       NULL,
       NULL,
       NULL,
       CASE d.kind WHEN 'RXT' THEN 'ClichÃ© debout, inspiration profonde' ELSE NULL END,
       d.id,
       d.motif
FROM _dem d
WHERE d.kind <> 'BIO';

INSERT INTO observations_biologiques (id, analyte_code, analyte_code_display, analyte_code_system, center_id,
                                      created_at, date_prelevement, demande_examen_id, patient_id, source, statut,
                                      unite, updated_at, valeur_num, valeur_texte)
SELECT pg_temp.uid('obs:' || r.id || ':' || a.no),
       a.code,
       a.display,
       'LOINC',
       r.center_id,
       pg_temp.ts(r.d + 1, 14),
       r.d,
       CASE WHEN r.trimestriel THEN pg_temp.uid('dem:' || r.patient_id || ':' || r.mois) END,
       r.patient_id,
       'DERIVEE_BILAN',
       'FINAL',
       a.unite,
       pg_temp.ts(r.d + 1, 14),
       CASE a.no
           WHEN 1 THEN r.hb
           WHEN 2 THEN r.phosphore
           WHEN 3 THEN r.calcium
           WHEN 4 THEN r.albumine
           WHEN 5 THEN r.ferritine
           ELSE r.pth END,
       NULL
FROM _res r
         CROSS JOIN _analyte a;

-- Ordonnances trimestrielles
CREATE
TEMP TABLE _ord ON COMMIT DROP
AS
SELECT x.*,
       pg_temp.uid('ord:' || x.patient_id || ':' || x.d) AS id,
       row_number()                                         OVER (PARTITION BY x.center_id ORDER BY x.d, x.code_patient)                            AS seq, row_number() OVER (PARTITION BY x.patient_id ORDER BY x.d DESC)                                      AS rang_inv
FROM (SELECT p.id AS patient_id, p.center_id, p.s, p.k, p.med_rang, p.code_patient, g::date AS d
      FROM _p p
               CROSS JOIN _cfg cfg
               CROSS JOIN LATERAL generate_series((p.d_start + 7)::timestamp, least(p.d_end, cfg.d_fin)::timestamp,
                                                  INTERVAL '3 months') AS g) x;

INSERT INTO ordonnances (id, center_id, created_at, date_prescription, medecin_id, numero, patient_id, signed_at,
                         statut, updated_at)
SELECT o.id,
       o.center_id,
       pg_temp.ts(o.d, 11),
       o.d,
       pg_temp.uid('med:' || o.s || ':' || o.med_rang)::text, 'ORD-' || lpad(o.seq::text, 5, '0'),
       o.patient_id,
       pg_temp.ts(o.d, 11.5),
       CASE WHEN o.rang_inv = 1 THEN 'SIGNEE' ELSE 'IMPRIMEE' END,
       pg_temp.ts(o.d, 11.5)
FROM _ord o;

INSERT INTO lignes_ordonnance (id, duree_jours, instructions, libelle, medicament_code, medicament_code_display,
                               medicament_code_system, ordonnance_id, posologie, quantite, voie)
SELECT pg_temp.uid('lord:' || o.id || ':' || m.no),
       m.duree,
       m.instr,
       m.libelle,
       m.code,
       m.dci,
       'ATC',
       o.id,
       m.poso,
       m.qte,
       'Orale'
FROM _ord o
         CROSS JOIN LATERAL (VALUES (1, 'A12AA04', 'Carbonate de calcium', 'Carbonate de calcium 500 mg, comprimÃ©',
                                     '1 comprimÃ© 3 fois par jour au milieu des repas', 270, 90,
                                     'ChÃ©lateur du phosphore', TRUE),
                                    (2, 'A11CC03', 'Alfacalcidol', 'Alfacalcidol 0,25 Âµg, capsule molle',
                                     '1 capsule par jour', 90, 90, 'Adapter selon la PTH', TRUE),
                                    (3, 'B03BB01', 'Acide folique', 'Acide folique 5 mg, comprimÃ©',
                                     '1 comprimÃ© par jour aprÃ¨s la sÃ©ance', 90, 90, NULL, TRUE),
                                    (4, 'C08CA01', 'Amlodipine', 'Amlodipine 5 mg, comprimÃ©',
                                     '1 comprimÃ© le soir (sauf jours de dialyse)', 60, 90, 'Adapter selon la TA',
                                     TRUE),
                                    (5, 'V03AE02', 'SÃ©vÃ©lamer', 'SÃ©vÃ©lamer carbonate 800 mg, comprimÃ©',
                                     '2 comprimÃ©s 3 fois par jour pendant les repas', 540, 90,
                                     'Si phosphore > 5,5 mg/dl',
                                     pg_temp.rnd('sev' || o.k) < 0.4),
                                    (6, 'A02BC01', 'OmÃ©prazole', 'OmÃ©prazole 20 mg, gÃ©lule gastro-rÃ©sistante',
                                     '1 gÃ©lule le matin Ã  jeun', 90, 90, NULL, pg_temp.rnd('ome' || o.k) < 0.3))
    AS m(no, code, dci, libelle, poso, qte, duree, instr, inclus)
WHERE m.inclus;

-- Greffe rÃ©nale : bilan prÃ©-greffe pour les patients Ã©ligibles (< 55 ans)
CREATE
TEMP TABLE _gr ON COMMIT DROP
AS
SELECT p.*,
       p.d_debut + 20 + floor(pg_temp.rnd('gbd' || p.k) * 60)::int AS d_bilan, CASE
                                                                                   WHEN pg_temp.rnd('gst' || p.k) < 0.35
                                                                                       THEN 'BILAN_EN_COURS'
                                                                                   WHEN pg_temp.rnd('gst' || p.k) < 0.60
                                                                                       THEN 'ELIGIBLE'
                                                                                   WHEN pg_temp.rnd('gst' || p.k) < 0.85
                                                                                       THEN 'INSCRIT_LISTE_ATTENTE'
                                                                                   ELSE 'CONTRE_INDICATION_TEMPORAIRE' END AS statut_bilan
FROM _p p
WHERE NOT p.is_vac
  AND NOT p.is_dcd
  AND p.date_naissance > p.d_debut - INTERVAL '55 years'
  AND pg_temp.rnd('gr' || p.k)
    < 0.45;

INSERT INTO bilans_pre_greffe (id, center_id, conclusion_nephrologue, contre_indications, created_at, date_debut_bilan,
                               date_greffe, date_inscription_liste_attente, groupe_sanguin_confirme, patient_id,
                               pra_classe_i, pra_classe_ii, statut, typage_hla, updated_at)
SELECT pg_temp.uid('bpg:' || g.id),
       g.center_id,
       CASE g.statut_bilan
           WHEN 'BILAN_EN_COURS' THEN 'Bilan prÃ©-transplantation en cours de rÃ©alisation.'
           WHEN 'CONTRE_INDICATION_TEMPORAIRE' THEN 'Contre-indication temporaire : rÃ©Ã©valuation Ã  6 mois.'
           ELSE 'Patient apte Ã  la transplantation rÃ©nale.' END,
       CASE
           WHEN g.statut_bilan = 'CONTRE_INDICATION_TEMPORAIRE'
               THEN 'Coronaropathie Ã  explorer (coronarographie programmÃ©e)' END,
       pg_temp.ts(g.d_bilan, 10),
       g.d_bilan,
       NULL,
       CASE WHEN g.statut_bilan = 'INSCRIT_LISTE_ATTENTE' THEN g.d_bilan + 150 END,
       g.groupe_sanguin,
       g.id,
       round(pg_temp.rng('pra1' || g.k, 0, 40), 0),
       round(pg_temp.rng('pra2' || g.k, 0, 25), 0),
       g.statut_bilan,
       'A*' || pg_temp.pick(ARRAY['01', '02', '03', '24', '30'], 'ha' || g.k) || ', A*' ||
       pg_temp.pick(ARRAY['02', '11', '26', '68'], 'hb' || g.k)
           || ' ; B*' || pg_temp.pick(ARRAY['07', '35', '44', '51'], 'hc' || g.k) || ', B*' ||
       pg_temp.pick(ARRAY['08', '18', '50', '52'], 'hd' || g.k)
           || ' ; DRB1*' || pg_temp.pick(ARRAY['03', '04', '07', '11'], 'he' || g.k) || ', DRB1*' ||
       pg_temp.pick(ARRAY['13', '15', '01'], 'hf' || g.k),
       pg_temp.ts(g.d_bilan + 150, 10)
FROM _gr g;

INSERT INTO etapes_bilan_pre_greffe (id, categorie, center_id, created_at, date_expiration, date_realisation,
                                     demande_examen_id, libelle, patient_id, resultat, serologie_id, statut, updated_at)
SELECT pg_temp.uid('etg:' || g.id || ':' || e.no),
       e.categorie,
       g.center_id,
       pg_temp.ts(g.d_bilan, 10),
       CASE WHEN x.statut = 'FAIT' THEN (g.d_bilan + e.no * 15 + INTERVAL '1 year')::date END,
       CASE WHEN x.statut = 'FAIT' THEN g.d_bilan + e.no * 15
END,
       NULL, e.libelle, g.id,
       CASE WHEN x.statut = 'FAIT' THEN e.resultat
END,
       CASE WHEN e.categorie = 'VIROLOGIQUE' THEN pg_temp.uid('sero:' || g.id || ':1:AG_HBS')
END,
       x.statut, pg_temp.ts(g.d_bilan + e.no * 15, 16)
FROM _gr g
         CROSS JOIN (VALUES (1, 'CARDIOLOGIQUE', 'Ã‰chocardiographie et Ã©preuve d''effort', 'FEVG 60 %, Ã©preuve d''effort nÃ©gative'),
                            (2, 'PNEUMOLOGIQUE', 'Radiographie thoracique et EFR', 'Pas d''anomalie parenchymateuse'),
                            (3, 'VIROLOGIQUE', 'SÃ©rologies virales prÃ©-greffe (VHB, VHC, VIH, CMV, EBV)', 'Statut viral compatible'),
                            (4, 'DENTAIRE', 'Panoramique dentaire et soins', 'Pas de foyer infectieux'),
                            (5, 'UROLOGIQUE', 'Ã‰chographie vÃ©sico-rÃ©nale', 'Vessie de capacitÃ© normale'),
                            (6, 'IMMUNOLOGIQUE', 'Typage HLA et recherche d''anticorps anti-HLA', 'Typage rÃ©alisÃ©, PRA faible'))
             AS e(no, categorie, libelle, resultat)
         CROSS JOIN LATERAL (SELECT CASE
                                        WHEN g.statut_bilan <> 'BILAN_EN_COURS' THEN 'FAIT'
                                        WHEN e.no <= 3 THEN 'FAIT'
                                        WHEN e.no = 4 THEN 'PLANIFIE'
                                        WHEN e.no = 5 THEN 'A_FAIRE'
                                        ELSE 'NON_APPLICABLE' END AS statut) x;

INSERT INTO decisions_rcp (id, avis, bilan_id, compte_rendu, date_reunion, prochaine_date_revue)
SELECT pg_temp.uid('rcp:' || g.id),
       CASE g.statut_bilan WHEN 'CONTRE_INDICATION_TEMPORAIRE' THEN 'AJOURNE' ELSE 'FAVORABLE' END,
       pg_temp.uid('bpg:' || g.id),
       CASE g.statut_bilan
           WHEN 'CONTRE_INDICATION_TEMPORAIRE'
               THEN 'RCP greffe : dossier ajournÃ© dans l''attente de l''exploration coronarienne.'
           ELSE 'RCP greffe : avis favorable Ã  l''inscription sur liste d''attente / greffe Ã  donneur vivant.' END,
       g.d_bilan + 120,
       (g.d_bilan + 120 + INTERVAL '6 months') ::date
FROM _gr g
WHERE g.statut_bilan <> 'BILAN_EN_COURS';

INSERT INTO donneurs_vivants (id, bilan_realise, center_id, contre_indications, created_at, crossmatch_resultat,
                              date_crossmatch, date_decision, date_naissance, decision_finale, groupe_sanguin,
                              lien_parente, nom, patient_id, prenom, statut_bilan, telephone, typage_hla, updated_at)
SELECT pg_temp.uid('don:' || g.id),
       'Bilan donneur : fonction rÃ©nale, imagerie, bilan cardiovasculaire et psychologique',
       g.center_id,
       CASE WHEN x.statut = 'INCOMPATIBLE' THEN 'Crossmatch lymphocytaire positif' END,
       pg_temp.ts(g.d_bilan + 30, 10),
       CASE
           WHEN x.statut IN ('COMPATIBLE', 'RETENU') THEN 'NEGATIF'
           WHEN x.statut = 'INCOMPATIBLE' THEN 'POSITIF'
           ELSE 'NON_FAIT' END,
       CASE WHEN x.statut IN ('COMPATIBLE', 'RETENU', 'INCOMPATIBLE') THEN g.d_bilan + 90 END,
       CASE WHEN x.statut IN ('COMPATIBLE', 'RETENU', 'INCOMPATIBLE') THEN g.d_bilan + 110 END,
       g.date_naissance + CASE x.lien WHEN 'PARENT' THEN -9500 WHEN 'ENFANT' THEN 9000 ELSE 800 END,
       CASE x.statut
           WHEN 'RETENU' THEN 'Donneur retenu â€” programmation de la transplantation'
           WHEN 'COMPATIBLE' THEN 'Donneur compatible â€” dÃ©cision RCP en attente'
           WHEN 'INCOMPATIBLE' THEN 'Donneur rÃ©cusÃ© (incompatibilitÃ© immunologique)'
           ELSE 'Ã‰valuation en cours' END,
       pg_temp.pick(ARRAY['O+', 'A+', 'B+', 'AB+', 'O-'], 'dgs' || g.k),
       x.lien,
       CASE WHEN x.lien = 'CONJOINT' THEN pg_temp.nom('don' || g.k) ELSE g.nom END,
       g.id,
       pg_temp.prenom(CASE WHEN pg_temp.rnd('dsx' || g.k) < 0.5 THEN 'M' ELSE 'F' END, 'don' || g.k),
       x.statut,
       pg_temp.mob('don' || g.k),
       'A*02, A*24 ; B*35, B*51 ; DRB1*11, DRB1*13',
       pg_temp.ts(g.d_bilan + 110, 16)
FROM _gr g
         CROSS JOIN LATERAL (SELECT pg_temp.pick(ARRAY['FRERE_SOEUR', 'FRERE_SOEUR', 'PARENT', 'CONJOINT', 'ENFANT',
                                                 'AUTRE_FAMILLE'], 'dl' || g.k) AS lien,
                                    pg_temp.pick(ARRAY['CANDIDAT', 'BILAN_EN_COURS', 'COMPATIBLE', 'INCOMPATIBLE',
                                                 'RETENU'], 'dst' || g.k)       AS statut) x
WHERE pg_temp.rnd('dv' || g.k) < 0.5;

-- =====================================================================================================================
-- 7. STOCK
-- =====================================================================================================================
CREATE
TEMP TABLE _art ON COMMIT DROP
AS
SELECT *
FROM (VALUES ('DLZ-HF', 'Dialyseur haute permÃ©abilitÃ© FX80 (1,8 mÂ²)', 'UnitÃ©', 2850, 'FMC', 'MAG', NULL, 24),
             ('DLZ-LF', 'Dialyseur basse permÃ©abilitÃ© F8 HPS (1,8 mÂ²)', 'UnitÃ©', 1950, 'FMC', 'MAG', NULL, 24),
             ('LIG-AV', 'Ligne artÃ©rio-veineuse universelle', 'UnitÃ©', 850, 'FMC', 'MAG', NULL, 36),
             ('AIG-15G', 'Aiguille Ã  fistule 15G', 'UnitÃ©', 120, 'NIP', 'MAG', NULL, 36),
             ('KIT-KT', 'Kit de branchement / dÃ©branchement cathÃ©ter', 'Kit', 650, 'NIP', 'MAG', NULL, 24),
             ('CONC-ACD', 'ConcentrÃ© acide pour hÃ©modialyse 5 L', 'Bidon', 1100, 'HYD', 'MAG', NULL, 18),
             ('BIC-CART', 'Cartouche de bicarbonate 650 g', 'Cartouche', 780, 'FMC', 'MAG', NULL, 18),
             ('HNF-5000', 'HÃ©parine sodique 5 000 UI/ml â€” flacon 5 ml', 'Flacon', 420, 'BIO', 'PHA', NULL, 24),
             ('HBPM-4000', 'Ã‰noxaparine 4 000 UI/0,4 ml â€” seringue', 'Seringue', 560, 'BIO', 'PHA', NULL, 24),
             ('SER-1L', 'Chlorure de sodium 0,9 % â€” poche 1 L', 'Poche', 160, 'HYD', 'MAG', NULL, 24),
             ('CMP-STE', 'Compresses stÃ©riles 10 Ã— 10 cm â€” sachet de 10', 'Sachet', 45, 'HYD', 'MAG', NULL, 60),
             ('EPO-4000', 'Ã‰poÃ©tine alfa 4 000 UI â€” seringue prÃ©remplie', 'Seringue', 3200, 'BIO', 'FRI', 'EPO',
              12),
             ('FER-100', 'Fer saccharose 100 mg/5 ml â€” ampoule', 'Ampoule', 950, 'BIO', 'PHA', 'FER_INJECTABLE', 24))
         AS t(code, libelle, unite, prix, fournisseur, emplacement, anemie, peremption_mois);

CREATE
TEMP TABLE _fr ON COMMIT DROP
AS
SELECT c.id                                         AS center_id,
       c.s,
       c.tel,
       v.code,
       v.raison_sociale,
       v.contact,
       pg_temp.uid('four:' || c.s || ':' || v.code) AS id
FROM _c c
         CROSS JOIN (VALUES ('FMC', 'Fresenius Medical Care AlgÃ©rie SPA', 'Service clients â€” M. Hamdi'),
                            ('NIP', 'Nipro Medical AlgÃ©rie SARL', 'DÃ©lÃ©guÃ©e commerciale â€” Mme Rahal'),
                            ('HYD', 'Hydra Pharm SPA', 'Service commandes hÃ´pitaux'),
                            ('BIO', 'Biopharm Distribution SPA',
                             'Responsable grands comptes â€” M. Ziani')) AS v(code, raison_sociale, contact);

INSERT INTO fournisseurs (id, actif, center_id, code, contact, email, raison_sociale, telephone)
SELECT id,
       TRUE,
       center_id,
       code,
       contact,
       lower(code) || '.commandes@fournisseur.dz',
       raison_sociale,
       pg_temp.fixe(tel, 'four' || s || code)
FROM _fr;

INSERT INTO emplacements (id, actif, center_id, code, libelle)
SELECT pg_temp.uid('emp:' || c.s || ':' || v.code), TRUE, c.id, v.code, v.libelle
FROM _c c
         CROSS JOIN (VALUES ('MAG', 'Magasin central'),
                            ('PHA', 'Pharmacie'),
                            ('FRI', 'RÃ©frigÃ©rateur pharmacie (2â€“8 Â°C)'),
                            ('RES', 'RÃ©serve salle de dialyse')) AS v(code, libelle);

-- Consommations : matÃ©riel par sÃ©ance rÃ©alisÃ©e + injections EPO / fer administrÃ©es
CREATE
TEMP TABLE _cons ON COMMIT DROP
AS
SELECT s.id AS seance_id,
       s.center_id,
       s.s,
       s.mois,
       s.date_seance,
       s.h_debut,
       s.inf_username,
       a.art,
       a.q::numeric AS q
FROM _s s
         CROSS JOIN LATERAL (VALUES (CASE WHEN s.forfait_eff IN ('HDF-OL', 'HD-EPO') THEN 'DLZ-HF' ELSE 'DLZ-LF' END,
                                     1),
                                    ('LIG-AV', 1),
                                    (CASE WHEN s.is_kt THEN 'KIT-KT' ELSE 'AIG-15G' END,
                                     CASE WHEN s.is_kt THEN 1 ELSE 2 END),
                                    ('CONC-ACD', 1),
                                    ('BIC-CART', 1),
                                    (CASE s.anticoag WHEN 'HNF' THEN 'HNF-5000' WHEN 'HBPM' THEN 'HBPM-4000' END, 1),
                                    ('SER-1L', 1),
                                    ('CMP-STE', 1)) AS a(art, q)
WHERE s.statut <> 'CREE'
  AND a.art IS NOT NULL
UNION ALL
SELECT a.id,
       a.center_id,
       a.s,
       a.mois,
       a.date_seance,
       a.h_debut,
       a.inf_username,
       CASE a.tt WHEN 'EPO' THEN 'EPO-4000' ELSE 'FER-100' END,
       1
FROM _adm a
WHERE a.administree;

CREATE INDEX ON _cons (center_id, art, mois);
ANALYZE
_cons;

-- Lots : une rÃ©ception par mois et par article (consommation du mois + 10 % + stock de sÃ©curitÃ©)
CREATE
TEMP TABLE _lot ON COMMIT DROP
AS
WITH m AS (SELECT g::date AS mois, row_number() OVER (ORDER BY g) - 1 AS mi
           FROM _cfg cfg
                    CROSS JOIN LATERAL generate_series(cfg.d_debut::timestamp, cfg.d_mois::timestamp, INTERVAL '1 month') g),
     c AS (SELECT center_id, art, mois, sum(q) AS conso FROM _cons GROUP BY center_id, art, mois)
SELECT ce.id                                 AS center_id,
       ce.s,
       ce.slug,
       a.code                                AS art,
       a.libelle,
       a.fournisseur,
       a.emplacement,
       a.peremption_mois,
       m.mois,
       m.mi,
       coalesce(c.conso, 0)                  AS conso,
       ceil(coalesce(c.conso, 0) * 1.10) + 5 AS qte_init,
       CASE WHEN a.code = 'CMP-STE' AND extract(MONTH FROM m.mois)::int % 3 = 0 THEN -2 ELSE 0 END AS ajust,
       round(a.prix * (1 + 0.015 * m.mi), 2)                                       AS prix,
       pg_temp.uid('lot:' || ce.s || ':' || a.code || ':' || m.mois)               AS id,
       pg_temp.uid('br:' || ce.s || ':' || m.mois || ':' || a.fournisseur)         AS br_id,
       pg_temp.uid('bc:' || ce.s || ':' || m.mois || ':' || a.fournisseur)         AS bc_id,
       'L' || to_char(m.mois, 'YYMM') || '-' || a.code                             AS numero_lot,
       (m.mois + make_interval(months => a.peremption_mois))::date                 AS peremption
FROM _c ce
    CROSS JOIN _art a
    CROSS JOIN m
    LEFT JOIN c
ON c.center_id = ce.id AND c.art = a.code AND c.mois = m.mois;

CREATE INDEX ON _lot (center_id, art, mois);
ANALYZE
_lot;

INSERT INTO articles (id, active, center_id, code, created_at, gere_par_lot, libelle, pmp_courant, seuil_alerte,
                      stock_quantity, type_traitement_anemie, unite)
SELECT pg_temp.uid('art:' || ce.s || ':' || a.code),
       TRUE,
       ce.id,
       a.code,
       pg_temp.ts(cfg.d_debut - 20, 9),
       TRUE,
       a.libelle,
       coalesce(round(sum((l.qte_init - l.conso + l.ajust) * l.prix) / nullif(sum(l.qte_init - l.conso + l.ajust), 0),
                      4), a.prix),
       greatest(5, ceil(avg(l.conso) * 0.4)),
       sum(l.qte_init - l.conso + l.ajust),
       a.anemie,
       a.unite
FROM _c ce
         CROSS JOIN _art a
         CROSS JOIN _cfg cfg
         JOIN _lot l ON l.center_id = ce.id AND l.art = a.code
GROUP BY ce.id, ce.s, a.code, a.libelle, a.prix, a.anemie, a.unite, cfg.d_debut;

-- Bons de commande (reÃ§us) + commandes en attente (validÃ©es / brouillon)
CREATE
TEMP TABLE _bc ON COMMIT DROP
AS
SELECT x.*, row_number() OVER (PARTITION BY x.center_id ORDER BY x.mois, x.fournisseur, x.statut) AS seq
FROM (SELECT DISTINCT l.center_id,
                      l.s,
                      l.slug,
                      l.mois,
                      l.fournisseur,
                      l.bc_id AS id,
                      l.br_id,
                      'RECU'::text AS statut
      FROM _lot l
      UNION ALL
      SELECT c.id,
             c.s,
             c.slug,
             (cfg.d_mois + INTERVAL '1 month')::date, f.fournisseur,
             pg_temp.uid('bc:' || c.s || ':next:' || f.fournisseur),
             NULL,
             CASE WHEN f.fournisseur = 'BIO' THEN 'BROUILLON' ELSE 'VALIDE' END
      FROM _c c
               CROSS JOIN _cfg cfg
               CROSS JOIN (SELECT DISTINCT fournisseur FROM _art) f) x;

INSERT INTO bons_commande (id, center_id, created_at, created_by, fournisseur_id, reference, statut)
SELECT b.id,
       b.center_id,
       pg_temp.ts(least(b.mois - 6, cfg.d_fin), 10),
       'admin.' || b.slug,
       pg_temp.uid('four:' || b.s || ':' || b.fournisseur),
       'BC-' || lpad(b.seq::text, 5, '0'),
       b.statut
FROM _bc b
         CROSS JOIN _cfg cfg;

INSERT INTO bons_commande_lignes (id, article_id, bon_commande_id, prix_unitaire, quantite)
SELECT pg_temp.uid('bcl:' || l.bc_id || ':' || l.art),
       pg_temp.uid('art:' || l.s || ':' || l.art),
       l.bc_id,
       l.prix,
       l.qte_init
FROM _lot l
UNION ALL
SELECT pg_temp.uid('bcl:' || b.id || ':' || l.art),
       pg_temp.uid('art:' || l.s || ':' || l.art),
       b.id,
       l.prix,
       l.qte_init
FROM _bc b
         CROSS JOIN _cfg cfg
         JOIN _lot l ON l.center_id = b.center_id AND l.fournisseur = b.fournisseur AND l.mois = cfg.d_mois
WHERE b.statut <> 'RECU';

INSERT INTO bons_reception (id, bon_commande_id, center_id, created_at, created_by, date_reception, fournisseur_id,
                            reference, statut)
SELECT b.br_id,
       b.id,
       b.center_id,
       pg_temp.ts(b.mois - 2, 11),
       'admin.' || b.slug,
       b.mois - 2,
       pg_temp.uid('four:' || b.s || ':' || b.fournisseur),
       'BR-' || lpad((row_number() OVER (PARTITION BY b.center_id ORDER BY b.mois, b.fournisseur))::text, 5, '0'),
       'VALIDE'
FROM _bc b
WHERE b.statut = 'RECU';

INSERT INTO bons_reception_lignes (id, article_id, bon_reception_id, date_peremption, emplacement_id, lot_id,
                                   numero_lot, prix_unitaire, quantite)
SELECT pg_temp.uid('brl:' || l.id),
       pg_temp.uid('art:' || l.s || ':' || l.art),
       l.br_id,
       l.peremption,
       pg_temp.uid('emp:' || l.s || ':' || l.emplacement),
       l.id,
       l.numero_lot,
       l.prix,
       l.qte_init
FROM _lot l;

INSERT INTO lots (id, article_id, bon_reception_id, center_id, created_at, date_peremption, emplacement_id, numero_lot,
                  pmp, quantite_initiale, quantite_restante)
SELECT l.id,
       pg_temp.uid('art:' || l.s || ':' || l.art),
       l.br_id,
       l.center_id,
       pg_temp.ts(l.mois - 2, 11),
       l.peremption,
       pg_temp.uid('emp:' || l.s || ':' || l.emplacement),
       l.numero_lot,
       l.prix,
       l.qte_init,
       l.qte_init - l.conso + l.ajust
FROM _lot l;

-- Bons de sortie : un par sÃ©ance rÃ©alisÃ©e
INSERT INTO bons_sortie (id, center_id, created_at, created_by, date_sortie, patient_id, poste, reference, seance_id)
SELECT pg_temp.uid('bs:' || s.id),
       s.center_id,
       pg_temp.ts(s.date_seance, s.h_debut + 4.3),
       s.inf_username,
       s.date_seance,
       s.patient_id,
       'G' || s.salle_no || lpad(s.gen_no::text, 2, '0'),
       'BS-' || lpad((row_number() OVER (PARTITION BY s.center_id ORDER BY s.date_seance, s.h_debut, s.code_patient))::text, 5, '0'),
       s.id
FROM _s s
WHERE s.statut <> 'CREE';

INSERT INTO bons_sortie_lignes (id, article_id, bon_sortie_id, lot_id, pmp_applique, quantite)
SELECT pg_temp.uid('bsl:' || c.seance_id || ':' || c.art),
       pg_temp.uid('art:' || c.s || ':' || c.art),
       pg_temp.uid('bs:' || c.seance_id),
       l.id,
       l.prix,
       c.q
FROM _cons c
         JOIN _lot l ON l.center_id = c.center_id AND l.art = c.art AND l.mois = c.mois;

-- Mouvements de stock : entrÃ©es (rÃ©ceptions), sorties (sÃ©ances), ajustements d'inventaire
INSERT INTO stock_movements (id, article_id, center_id, created_at, created_by, lot_id, mouvement_type, pmp_apres,
                             prix_unitaire, quantite, seance_id)
SELECT pg_temp.uid('mv:in:' || l.id),
       pg_temp.uid('art:' || l.s || ':' || l.art),
       l.center_id,
       pg_temp.ts(l.mois - 2, 11),
       'admin.' || l.slug,
       l.id,
       'ENTREE',
       l.prix,
       l.prix,
       l.qte_init,
       NULL
FROM _lot l
UNION ALL
SELECT pg_temp.uid('mv:out:' || c.seance_id || ':' || c.art),
       pg_temp.uid('art:' || c.s || ':' || c.art),
       c.center_id,
       pg_temp.ts(c.date_seance, c.h_debut + 4.3),
       c.inf_username,
       l.id,
       'SORTIE',
       l.prix,
       l.prix,
       c.q,
       c.seance_id
FROM _cons c
         JOIN _lot l ON l.center_id = c.center_id AND l.art = c.art AND l.mois = c.mois
UNION ALL
SELECT pg_temp.uid('mv:adj:' || l.id),
       pg_temp.uid('art:' || l.s || ':' || l.art),
       l.center_id,
       pg_temp.ts((l.mois + INTERVAL '1 month - 1 day'):: date, 17),
       'admin.' || l.slug,
       l.id,
       'AJUSTEMENT',
       l.prix,
       NULL,
       l.ajust,
       NULL
FROM _lot l
WHERE l.ajust <> 0;

-- =====================================================================================================================
-- 8. FACTURATION (12 mois clos) & RÃˆGLEMENTS
-- =====================================================================================================================
CREATE
TEMP TABLE _fl ON COMMIT DROP
AS
SELECT s.patient_id,
       s.center_id,
       s.s,
       s.mois,
       f.code,
       f.libelle,
       f.prix,
       f.id     AS forfait_id,
       count(*) AS nb
FROM _s s
         JOIN _fo f ON f.center_id = s.center_id AND f.code = s.forfait_eff
WHERE s.statut = 'FACTUREE'
GROUP BY s.patient_id, s.center_id, s.s, s.mois, f.code, f.libelle, f.prix, f.id;

CREATE
TEMP TABLE _f ON COMMIT DROP
AS
WITH a AS (SELECT fl.patient_id, fl.center_id, fl.s, fl.mois,
                  pg_temp.uid('fac:' || fl.patient_id || ':' || fl.mois)                AS id,
                  sum(fl.nb * fl.prix)                                                  AS total_ht,
                  least((fl.mois + INTERVAL '1 month')::date + 4, cfg.d_fin)            AS date_facturation,
                  cfg.tva
           FROM _fl fl
                    CROSS JOIN _cfg cfg
           GROUP BY fl.patient_id, fl.center_id, fl.s, fl.mois, cfg.d_fin, cfg.tva)
SELECT a.*,
       p.code_patient,
       p.nom,
       p.prenom,
       p.etat_patient,
       p.numero_assurance,
       p.caisse_code,
       p.cp_no,
       p.k,
       p.slug,
       round(a.total_ht * a.tva / 100, 2) AS total_tva,
       row_number()                          OVER (PARTITION BY a.center_id, extract(YEAR FROM a.date_facturation)
                          ORDER BY a.date_facturation, p.code_patient)                           AS seq
FROM a
         JOIN _p p ON p.id = a.patient_id;

INSERT INTO factures (id, center_id, patient_id, numero_facture, patient_code, patient_full_name,
                      patient_status_snapshot, numero_immatriculation_snapshot, centre_payeur_id_snapshot,
                      agence_id_snapshot, period_start, period_end, date_facturation, tva_rate, total_ht, total_tva,
                      total_ttc, created_at)
SELECT f.id,
       f.center_id,
       f.patient_id,
       f.s || '-FAC-' || extract(YEAR FROM f.date_facturation)::int || '-' || lpad(f.seq::text, 5, '0'), f.code_patient,
       f.nom || ' ' || f.prenom,
       f.etat_patient,
       f.numero_assurance,
       pg_temp.uid('cp:' || f.s || ':' || f.caisse_code || ':' || f.cp_no),
       pg_temp.uid('agence:' || f.s || ':' || f.caisse_code),
       f.mois,
       (f.mois + INTERVAL '1 month - 1 day')::date, f.date_facturation,
       f.tva,
       f.total_ht,
       f.total_tva,
       f.total_ht + f.total_tva,
       pg_temp.ts(f.date_facturation, 9)
FROM _f f;

INSERT INTO facture_lignes (id, facture_id, center_id, forfait_id, forfait_label, unit_price_ht, seance_count, line_ht)
SELECT pg_temp.uid('fl:' || fl.patient_id || ':' || fl.mois || ':' || fl.code),
       pg_temp.uid('fac:' || fl.patient_id || ':' || fl.mois),
       fl.center_id,
       fl.forfait_id,
       fl.libelle,
       fl.prix,
       fl.nb,
       fl.nb * fl.prix
FROM _fl fl;

UPDATE seances se
SET facture_id = pg_temp.uid('fac:' || se.patient_id || ':' || date_trunc('month', se.date_seance)::date)
WHERE se.statut = 'FACTUREE';

INSERT INTO facture_sequence (center_id, seq_year, seq_value)
SELECT center_id, extract(YEAR FROM date_facturation)::int, max(seq)
FROM _f
GROUP BY center_id, extract(YEAR FROM date_facturation)::int;

CREATE
TEMP TABLE _r ON COMMIT DROP
AS
SELECT f.*,
       x.taux,
       pg_temp.uid('reg:' || f.id)                                                              AS reg_id,
       round((f.total_ht + f.total_tva) * x.taux, 2)                                            AS montant,
       least(f.date_facturation + 40 + floor(pg_temp.rnd('dreg' || f.id) * 35)::int, cfg.d_fin) AS date_reglement
FROM _f f
         CROSS JOIN _cfg cfg
         CROSS JOIN LATERAL (SELECT CASE
                                        WHEN f.mois <= (cfg.d_mois - INTERVAL '3 months')::
    date
    AND pg_temp.rnd('imp' || f.id) >= 0.04 THEN 1.0
    WHEN f.mois = (cfg.d_mois - INTERVAL '2 months'):: date
    AND pg_temp.rnd('imp' || f.id)
   < 0.6 THEN 0.5
    ELSE 0
END AS taux) x
WHERE x.taux > 0;

INSERT INTO facture_reglements (id, facture_id, center_id, montant, date_reglement, saisi_par, created_at,
                                code_reglement)
SELECT r.reg_id,
       r.id,
       r.center_id,
       r.montant,
       r.date_reglement,
       'secretaire.' || r.slug,
       pg_temp.ts(r.date_reglement, 10),
       'REG-' || upper(substr(r.reg_id::text, 1, 8))
FROM _r r;

-- =====================================================================================================================
-- 9. COMPTABILITÃ‰ (SCF)
-- =====================================================================================================================
INSERT INTO periodes_comptables (id, annee, center_id, cloturee, cloturee_at, cloturee_by, mois)
SELECT pg_temp.uid('per:' || c.s || ':' || g::date),
       extract(YEAR FROM g)::int, c.id,
       g::date <= (cfg.d_mois - INTERVAL '3 months')::date, CASE WHEN g::date <= (cfg.d_mois - INTERVAL '3 months')::date THEN pg_temp.ts((g + INTERVAL '1 month')::date + 10, 16) END,
       CASE WHEN g::date <= (cfg.d_mois - INTERVAL '3 months')::date THEN 'admin.' || c.slug
END,
       extract(MONTH FROM g)::int
FROM _c c
         CROSS JOIN _cfg cfg
         CROSS JOIN LATERAL generate_series(cfg.d_debut::timestamp, cfg.d_mois::timestamp, INTERVAL '1 month') g;

CREATE
TEMP TABLE _e ON COMMIT DROP
AS
SELECT 'VE'::text AS journal, f.id AS source_id,
       f.center_id,
       f.s,
       f.slug,
       f.patient_id,
       f.date_facturation       AS d,
       'Facture ' || f.s || '-FAC-' || extract(YEAR FROM f.date_facturation)::int || '-' || lpad(f.seq::text, 5, '0')
           || ' â€” ' || f.nom || ' ' || f.prenom AS libelle, f.total_ht,
       f.total_tva,
       f.total_ht + f.total_tva AS ttc,
       f.caisse_code
FROM _f f
UNION ALL
SELECT 'BQ',
       r.reg_id,
       r.center_id,
       r.s,
       r.slug,
       r.patient_id,
       r.date_reglement,
       'RÃ¨glement REG-' || upper(substr(r.reg_id::text, 1, 8)) || ' â€” ' || r.nom || ' ' || r.prenom,
       r.montant,
       0,
       r.montant,
       r.caisse_code
FROM _r r;

ALTER TABLE _e
    ADD COLUMN id uuid,
    ADD COLUMN numero text,
    ADD COLUMN compte_client text,
    ADD COLUMN statut text;

UPDATE _e
SET id            = pg_temp.uid('ecr:' || journal || ':' || source_id),
    compte_client = CASE caisse_code
                        WHEN 'CNAS' THEN '411200'
                        WHEN 'CASNOS' THEN '411300'
                        WHEN 'MGPTT' THEN '411400'
                        ELSE '411500' END,
    statut        = CASE
                        WHEN d < (SELECT (d_mois - INTERVAL '2 months') ::date FROM _cfg) THEN 'EXPORTEE'
                        ELSE 'VALIDEE' END;

UPDATE _e
SET numero = x.numero FROM (SELECT id,
             journal || '-' || extract(YEAR FROM d)::int || '-'
                 || lpad((row_number() OVER (PARTITION BY center_id, journal, extract(YEAR FROM d) ORDER BY d, id))::text, 6, '0') AS numero
      FROM _e) x
WHERE x.id = _e.id;

INSERT INTO ecritures_comptables (id, center_id, created_at, date_ecriture, date_piece, journal_code, libelle,
                                  numero_piece, source_id, statut, updated_at)
SELECT e.id,
       e.center_id,
       pg_temp.ts(e.d, 18),
       e.d,
       e.d,
       e.journal, left (e.libelle, 255), e.numero, e.source_id, e.statut, pg_temp.ts(e.d, 18)
FROM _e e;

INSERT INTO lignes_ecriture (id, axes_analytiques, compte_scf, libelle_ligne, montant_credit, montant_debit, tiers_id,
                             ecriture_id)
SELECT pg_temp.uid('le:' || e.id || ':' || l.no),
       'CENTRE:' || e.s || ',CAISSE:' || e.caisse_code,
       l.compte, left (l.libelle, 255), l.credit, l.debit, l.tiers, e.id
FROM _e e
    CROSS JOIN LATERAL (VALUES (1, CASE WHEN e.journal = 'VE' THEN e.compte_client ELSE '512' END, CASE WHEN e.journal = 'VE' THEN 'Client ' || e.caisse_code ELSE 'Encaissement banque' END, 0:: numeric, e.ttc, CASE WHEN e.journal = 'VE' THEN e.patient_id END), (2, CASE WHEN e.journal = 'VE' THEN '706' ELSE e.compte_client END, CASE WHEN e.journal = 'VE' THEN 'Prestations d''hÃ©modialyse' ELSE 'Client ' || e.caisse_code END, e.total_ht, 0:: numeric, CASE WHEN e.journal = 'BQ' THEN e.patient_id END), (3, '44571', 'TVA collectÃ©e', e.total_tva, 0:: numeric, NULL))
    AS l(no, compte, libelle, credit, debit, tiers)
WHERE l.no
    < 3
   OR e.total_tva
    > 0;

-- =====================================================================================================================
-- 10. SÃ‰QUENCES APPLICATIVES
-- =====================================================================================================================
INSERT INTO app_settings (center_id, cle, dernier_compteur, prefixe)
SELECT c.id,
       v.cle,
       CASE v.cle
           WHEN 'SEQ_BC' THEN (SELECT count(*) FROM bons_commande b WHERE b.center_id = c.id)
           WHEN 'SEQ_BR' THEN (SELECT count(*) FROM bons_reception b WHERE b.center_id = c.id)
           WHEN 'SEQ_BS' THEN (SELECT count(*) FROM bons_sortie b WHERE b.center_id = c.id)
           WHEN 'SEQ_ORD' THEN (SELECT count(*) FROM ordonnances o WHERE o.center_id = c.id)
           ELSE 0 END,
       v.prefixe
FROM _c c
         CROSS JOIN (VALUES ('SEQ_BC', 'BC-'),
                            ('SEQ_BR', 'BR-'),
                            ('SEQ_BS', 'BS-'),
                            ('SEQ_BL', 'BL-'),
                            ('SEQ_ORD', 'ORD-')) AS v(cle, prefixe);

-- =====================================================================================================================
-- 11. PILOTAGE DIRECTION : alertes & instantanÃ©s mensuels
-- =====================================================================================================================
INSERT INTO direction_alert_history (id, societe_id, center_id, centre_nom, code, severity, valeur, first_seen_at,
                                     resolved_at)
SELECT pg_temp.uid('dah:' || c.s || ':' || v.code),
       cfg.societe_id,
       c.id,
       c.nom,
       v.code,
       v.severity,
       v.valeur,
       pg_temp.ts(cfg.d_debut + v.j + c.idx, 6),
       CASE
           WHEN v.code = 'HB_HORS_CIBLE' AND c.idx % 3 = 0 THEN NULL
           ELSE pg_temp.ts(cfg.d_debut + v.j + c.idx + 12, 6) END
FROM _c c
         CROSS JOIN _cfg cfg
         CROSS JOIN (VALUES ('STOCK_SOUS_SEUIL', 'CRITICAL', 2::numeric, 40),
                            ('LOTS_PEREMPTION_PROCHE', 'WARNING', 3::numeric, 120),
                            ('HB_HORS_CIBLE', 'WARNING', 58.5::numeric, 250),
                            ('KTV_CONFORMITE_BASSE', 'WARNING', 72.0::numeric, 300)) AS v(code, severity, valeur, j)
WHERE v.code <> 'KTV_CONFORMITE_BASSE'
   OR c.idx % 4 = 0;

WITH m AS (SELECT g::date AS mois
           FROM _cfg cfg
                    CROSS JOIN LATERAL generate_series(cfg.d_debut::timestamp, (cfg.d_mois - INTERVAL '1 month')::timestamp,
                                                       INTERVAL '1 month') g), st AS (
SELECT s.center_id, s.mois, count (*) AS seances, count (DISTINCT s.patient_id) AS patients, count (DISTINCT s.patient_id) FILTER (WHERE s.is_kt) AS sous_kt
FROM _s s
WHERE s.statut <> 'CREE'
GROUP BY s.center_id, s.mois),
    ft AS (
SELECT f.center_id, f.mois, count (*) AS factures, sum (f.total_ht) AS ca_ht, sum (f.total_ht + f.total_tva) AS ca_ttc, coalesce (sum (r.montant), 0) AS encaisse
FROM _f f
    LEFT JOIN _r r
ON r.id = f.id
GROUP BY f.center_id, f.mois),
    rs AS (
SELECT r.center_id, r.mois, count (*) AS evalues, round(100.0 * count (*) FILTER (WHERE r.hb BETWEEN 10 AND 12) / count (*), 1) AS hb_ok, round(100.0 * count (*) FILTER (WHERE r.hb < 10) / count (*), 1) AS hb_bas, round(100.0 * count (*) FILTER (WHERE r.hb > 12) / count (*), 1) AS hb_haut, round(100.0 * count (*) FILTER (WHERE r.phosphore BETWEEN 3.5 AND 5.5) / count (*), 1) AS ph_ok, round(100.0 * count (*) FILTER (WHERE r.phosphore < 3.5) / count (*), 1) AS ph_bas, round(100.0 * count (*) FILTER (WHERE r.phosphore > 5.5) / count (*), 1) AS ph_haut, round(100.0 * count (*) FILTER (WHERE r.pth BETWEEN 130 AND 585) / count (*), 1) AS pth_ok, round(100.0 * count (*) FILTER (WHERE r.pth < 130) / count (*), 1) AS pth_bas, round(100.0 * count (*) FILTER (WHERE r.pth > 585) / count (*), 1) AS pth_haut, round(100.0 * count (*) FILTER (WHERE r.albumine >= 3.5) / count (*), 1) AS alb_ok, round(100.0 * count (*) FILTER (WHERE r.albumine < 3.5) / count (*), 1) AS alb_bas, round(100.0 * count (*) FILTER (WHERE r.ratio_r <= 0.31) / count (*), 1) AS ktv_ok
FROM _res r
GROUP BY r.center_id, r.mois),
    cs AS (
SELECT m.mois, c.id AS center_id, c.nom, coalesce (st.patients, 0) AS patients, coalesce (st.sous_kt, 0) AS sous_kt, coalesce (st.seances, 0) AS seances, coalesce (ft.factures, 0) AS factures, coalesce (ft.ca_ht, 0) AS ca_ht, coalesce (ft.ca_ttc, 0) AS ca_ttc, coalesce (ft.encaisse, 0) AS encaisse, rs.evalues, rs.hb_ok, rs.hb_bas, rs.hb_haut, rs.ph_ok, rs.ph_bas, rs.ph_haut, rs.pth_ok, rs.pth_bas, rs.pth_haut, rs.alb_ok, rs.alb_bas, rs.ktv_ok
FROM m
    CROSS JOIN _c c
    LEFT JOIN st
ON st.center_id = c.id AND st.mois = m.mois
    LEFT JOIN ft ON ft.center_id = c.id AND ft.mois = m.mois
    LEFT JOIN rs ON rs.center_id = c.id AND rs.mois = m.mois),
    js AS (
SELECT cs.*, jsonb_build_object('centerId', cs.center_id, 'nom', cs.nom, 'actif', TRUE, 'patients', cs.patients, 'patientsSousKt', cs.sous_kt, 'seances', cs.seances, 'factures', cs.factures, 'caHt', cs.ca_ht, 'caTtc', cs.ca_ttc, 'encaisse', cs.encaisse, 'resteARecouvrer', cs.ca_ttc - cs.encaisse, 'tauxEncaissement', CASE WHEN cs.ca_ttc > 0 THEN round(100 * cs.encaisse / cs.ca_ttc, 1) END) AS stats, jsonb_build_object('centerId', cs.center_id, 'nom', cs.nom, 'actif', TRUE, 'clinique', jsonb_build_object(
    'ktV', jsonb_build_object('evalues', cs.evalues, 'pctDansCible', cs.ktv_ok, 'pctSousCible', 100 - cs.ktv_ok, 'pctAuDessus', 0), 'hemoglobine', jsonb_build_object('evalues', cs.evalues, 'pctDansCible', cs.hb_ok, 'pctSousCible', cs.hb_bas, 'pctAuDessus', cs.hb_haut), 'phosphore', jsonb_build_object('evalues', cs.evalues, 'pctDansCible', cs.ph_ok, 'pctSousCible', cs.ph_bas, 'pctAuDessus', cs.ph_haut), 'pth', jsonb_build_object('evalues', cs.evalues, 'pctDansCible', cs.pth_ok, 'pctSousCible', cs.pth_bas, 'pctAuDessus', cs.pth_haut), 'albumine', jsonb_build_object('evalues', cs.evalues, 'pctDansCible', cs.alb_ok, 'pctSousCible', cs.alb_bas, 'pctAuDessus', 0), 'vhbPositifs', NULL, 'vhcPositifs', NULL, 'vihPositifs', 0, 'patientsObservanceEnRetard', NULL, 'greffeListeAttente', NULL, 'greffeBilanEnCours', NULL, 'greffesPeriode', 0), 'stock', jsonb_build_object('articlesActifs', 13, 'articlesSousSeuil', 0, 'lotsPerimes', 0, 'lotsPeremptionProche', 0, 'valeurStock', NULL)) AS ind
FROM cs)
INSERT
INTO direction_snapshot (id, societe_id, mois, payload, generated_at)
SELECT pg_temp.uid('snap:' || js.mois),
       cfg.societe_id,
       to_char(js.mois, 'YYYY-MM'),
       jsonb_build_object(
               'overview', jsonb_build_object(
               'societeId', cfg.societe_id, 'societeNom', 'RENADIAL',
               'from', js.mois, 'to', (js.mois + INTERVAL '1 month - 1 day'):: date,
               'generatedAt', pg_temp.ts((js.mois + INTERVAL '1 month'):: date + 1, 2), 'seuilAnonymat', 5,
               'centres', jsonb_agg(js.stats ORDER BY js.nom),
               'totaux', jsonb_build_object('centerId', NULL, 'nom', 'Total sociÃ©tÃ©', 'actif', TRUE,
                                            'patients', sum(js.patients), 'patientsSousKt', sum(js.sous_kt),
                                            'seances', sum(js.seances), 'factures', sum(js.factures),
                                            'caHt', sum(js.ca_ht), 'caTtc', sum(js.ca_ttc),
                                            'encaisse', sum(js.encaisse),
                                            'resteARecouvrer', sum(js.ca_ttc) - sum(js.encaisse),
                                            'tauxEncaissement', CASE
                                                                    WHEN sum(js.ca_ttc) > 0
                                                                        THEN round(100 * sum(js.encaisse) / sum(js.ca_ttc), 1) END),
               'mensuel', jsonb_agg(jsonb_build_object('mois', to_char(js.mois, 'YYYY-MM'), 'centerId', js.center_id,
                                                       'seances', js.seances, 'caHt', js.ca_ht, 'caTtc',
                                                       js.ca_ttc) ORDER BY js.nom),
               'periodePrecedente', NULL),
               'indicators', jsonb_build_object(
                       'societeId', cfg.societe_id, 'from', js.mois, 'to',
                       (js.mois + INTERVAL '1 month - 1 day'):: date,
                       'generatedAt', pg_temp.ts((js.mois + INTERVAL '1 month'):: date + 1, 2), 'seuilAnonymat', 5,
                       'centres', jsonb_agg(js.ind ORDER BY js.nom),
                       'totaux', NULL, 'alertes', '[]'::jsonb),
               'breakdown', jsonb_build_object(
                       'societeId', cfg.societe_id, 'from', js.mois, 'to',
                       (js.mois + INTERVAL '1 month - 1 day'):: date,
                       'generatedAt', pg_temp.ts((js.mois + INTERVAL '1 month'):: date + 1, 2), 'seuilAnonymat', 5,
                       'sexe', '[]'::jsonb, 'ages', '[]'::jsonb, 'caisses', '[]'::jsonb, 'caisseTotaux', '[]'::jsonb,
                       'anemie', '[]'::jsonb)
       )::text, pg_temp.ts((js.mois + INTERVAL '1 month'):: date + 1, 2)
FROM js
         CROSS JOIN _cfg cfg
GROUP BY js.mois, cfg.societe_id;

-- =====================================================================================================================
-- 12. JOURNAL D'AUDIT (Ã©chantillon reprÃ©sentatif de l'activitÃ©)
-- =====================================================================================================================
INSERT INTO audit_log (id, occurred_at, user_id, username, roles, center_id, societe_id, action_code, entity_type,
                       entity_id, libelle, http_method, route_template, status_code, duration_ms, ip_address)
SELECT pg_temp.uid('audit:' || c.s || ':' || g::date || ':' || a.no),
       pg_temp.ts(g::date + a.j, a.h),
       u.id,
       u.username,
       'ROLE_' || u.role_code,
       c.id,
       cfg.societe_id,
       a.action_code,
       a.entity_type,
       NULL,
       a.verbe || ' : ' || replace(a.route, '/api/v1/', ''),
       a.methode,
       a.route,
       a.status,
       40 + floor(pg_temp.rnd('dur' || c.s || g || a.no) * 400)::int, '10.' || c.idx || '.0.' || (10 + a.no)
FROM _c c
         CROSS JOIN _cfg cfg
         CROSS JOIN LATERAL generate_series(cfg.d_debut::timestamp, cfg.d_mois::timestamp, INTERVAL '1 month') g
         CROSS JOIN (VALUES (1, 'secretaire', 'AUTH_CREATION', 'auth', '/api/v1/auth/login', 'POST', 'CrÃ©ation', 200, 1, 7.9),
                            (2, 'infirmier1', 'SEANCES_CREATION', 'seances', '/api/v1/seances', 'POST', 'CrÃ©ation', 201, 2, 6.4),
                            (3, 'infirmier1', 'SEANCES_MODIFICATION', 'seances', '/api/v1/seances/{id}/valider', 'PUT', 'Modification', 200, 2, 10.8),
                            (4, 'medecin1', 'DOSSIER_MEDICAL_CONSULTATION', 'dossier-medical',
                             '/api/v1/patients/{patientId}/dossier-medical', 'GET', 'Consultation', 200, 3, 9.2),
                            (5, 'medecin1', 'SEANCES_MODIFICATION', 'seances', '/api/v1/seances/{id}/signer', 'PUT', 'Modification', 200, 3, 19.4),
                            (6, 'secretaire', 'FACTURES_CREATION', 'factures', '/api/v1/factures/generation', 'POST', 'CrÃ©ation', 201, 4, 9.5),
                            (7, 'admin', 'STOCK_CREATION', 'stock', '/api/v1/stock/bons-reception', 'POST', 'CrÃ©ation', 201, 5, 11.0))
             AS a(no, prefix, action_code, entity_type, route, methode, verbe, status, j, h)
         JOIN _u u
ON u.username = a.prefix || '.' || c.slug
WHERE g:: date + a.j <= cfg.d_fin;

COMMIT;

-- =====================================================================================================================
-- CONTRÃ”LES
-- =====================================================================================================================
ANALYZE;

SELECT 'centres' AS indicateur, count(*) ::text AS valeur
FROM centers
UNION ALL
SELECT 'patients', count(*) ::text
FROM patients
UNION ALL
SELECT 'sÃ©ances', count(*) ::text
FROM seances
UNION ALL
SELECT 'sÃ©ances facturÃ©es', count(*) ::text
FROM seances
WHERE statut = 'FACTUREE'
UNION ALL
SELECT 'factures', count(*) ::text
FROM factures
UNION ALL
SELECT 'CA TTC facturÃ© (DA)', to_char(sum(total_ttc), 'FM999G999G999G990D00')
FROM factures
UNION ALL
SELECT 'rÃ¨glements', count(*) ::text
FROM facture_reglements
UNION ALL
SELECT 'mouvements de stock', count(*) ::text
FROM stock_movements
UNION ALL
SELECT 'lots Ã  stock nÃ©gatif (doit Ãªtre 0)', count(*) ::text
FROM lots
WHERE quantite_restante < 0
UNION ALL
SELECT 'sÃ©ances sans attestation/PEC (doit Ãªtre 0)', count(*) ::text
FROM seances s
WHERE NOT EXISTS (SELECT 1
                  FROM attestation_droit a
                  WHERE a.patient_id = s.patient_id
                    AND s.date_seance BETWEEN a.date_debut AND a.date_fin)
   OR NOT EXISTS (SELECT 1
                  FROM prise_en_charge p
                  WHERE p.patient_id = s.patient_id
                    AND p.statut = 'VALIDEE'
                    AND s.date_seance BETWEEN p.date_debut_effectif AND p.date_fin_effectif)
UNION ALL
SELECT 'tables vides (hors license)', coalesce(string_agg(relname, ', '), 'aucune')
FROM pg_stat_user_tables
WHERE schemaname = 'public'
  AND n_live_tup = 0
  AND relname <> 'license';



