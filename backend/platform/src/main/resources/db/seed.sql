-- Seed data for local development (H2) : IDENTITÉ seulement (SEED2, ADR 0188 et 0189) : rôles, utilisateurs, rattachements aux centres.
-- Aucun contexte ne possède l'identité (ADR 0140 D7). Le reste du jeu est dans les contextes (db/h2/<contexte>-seed.sql) et dans le module
-- de données de démonstration (demo-data : jeu dérivé multi-contextes, RENADIAL). Ordre : voir spring.sql.init.data-locations (application.yml).
-- ═══ ROLES ═══
MERGE INTO app_role (id, code, name, description) KEY (id)
VALUES ('a0a00001-0000-0000-0000-000000000001', 'ADMIN', 'Administrateur', 'Accès complet');
MERGE INTO app_role (id, code, name, description) KEY (id)
VALUES ('a0a00001-0000-0000-0000-000000000002', 'MEDECIN', 'Médecin', 'Accès dossier médical et séances');
MERGE INTO app_role (id, code, name, description) KEY (id)
VALUES ('a0a00001-0000-0000-0000-000000000003', 'INFIRMIER', 'Infirmier', 'Accès aux séances et soins');
MERGE INTO app_role (id, code, name, description) KEY (id)
VALUES ('a0a00001-0000-0000-0000-000000000004', 'SECRETAIRE', 'Secrétaire', 'Gestion administrative');
MERGE INTO app_role (id, code, name, description) KEY (id)
    VALUES ('a0a00001-0000-0000-0000-000000000006', 'DIRECTION', 'Direction',
    'Consultation des tableaux de bord consolidés de la société (agrégats anonymes, lecture seule)');

-- ═══ USERS (passwords set by SeedPasswordInitializer at runtime) ═══
MERGE INTO app_user (id, username, password_hash, email, full_name, active) KEY (id)
VALUES ('b0b00001-0000-0000-0000-000000000001', 'admin', 'placeholder', 'admin@hemodialyse.dz', 'Administrateur Système', TRUE);
MERGE INTO app_user (id, username, password_hash, email, full_name, active) KEY (id)
VALUES ('b0b00001-0000-0000-0000-000000000002', 'medecin', 'placeholder', 'medecin@hemodialyse.dz', 'Dr. Nouri Ahmed', TRUE);
MERGE INTO app_user (id, username, password_hash, email, full_name, active) KEY (id)
    VALUES ('b0b00001-0000-0000-0000-000000000003', 'infirmier-annaba', 'placeholder',
            'infirmier.annaba@hemodialyse.dz', 'Infirmier Annaba', TRUE);
MERGE INTO app_user (id, username, password_hash, email, full_name, active) KEY (id)
    VALUES ('b0b00001-0000-0000-0000-000000000004', 'infirmier-rouiba', 'placeholder',
            'infirmier.rouiba@hemodialyse.dz', 'Infirmier Rouiba', TRUE);
MERGE INTO app_user (id, username, password_hash, email, full_name, active) KEY (id)
    VALUES ('b0b00001-0000-0000-0000-000000000005', 'secretaire-annaba', 'placeholder',
            'secretaire.annaba@hemodialyse.dz', 'Secretaire Annaba', TRUE);
MERGE INTO app_user (id, username, password_hash, email, full_name, active) KEY (id)
    VALUES ('b0b00001-0000-0000-0000-000000000006', 'secretaire-rouiba', 'placeholder',
            'secretaire.rouiba@hemodialyse.dz', 'Secretaire Rouiba', TRUE);

-- Propriétaire de l'application (jeu de démonstration uniquement) : sans centre ni société, il gère sociétés,
-- centres et licences. Mot de passe posé par SeedPasswordInitializer. En production, ne pas utiliser ce jeu de
-- démonstration : le propriétaire est créé par l'installation initiale (écran /setup).
MERGE INTO app_role (id, code, name, description) KEY (id)
    VALUES ('a0a00001-0000-0000-0000-000000000005', 'SUPERADMIN', 'Propriétaire de l''application',
    'Éditeur : sociétés, centres, licences');
MERGE INTO app_user (id, username, password_hash, email, full_name, active) KEY (id)
    VALUES ('b0b00001-0000-0000-0000-000000000009', 'superadmin', 'placeholder',
    'superadmin@hemodialyse.dz', 'Propriétaire de l''application', TRUE);
MERGE INTO app_user_role (user_id, role_id) KEY (user_id, role_id)
    VALUES ('b0b00001-0000-0000-0000-000000000009', 'a0a00001-0000-0000-0000-000000000005');

-- Droit particulier : décision de réforme des équipements (GMAO). À attribuer aux personnes habilitées.
MERGE INTO app_role (id, code, name, description) KEY (id)
    VALUES ('a0a00001-0000-0000-0000-000000000007', 'GMAO_REFORME', 'Habilité à réformer',
    'Décide de la réforme des équipements (GMAO) — droit particulier distinct de l''administration');

-- Droit particulier : rectification (réouverture tracée) d'une intervention GMAO terminée.
MERGE INTO app_role (id, code, name, description) KEY (id)
    VALUES ('a0a00001-0000-0000-0000-000000000008', 'GMAO_RECTIFICATION', 'Habilité à rectifier les interventions',
    'Rouvre une intervention GMAO terminée pour correction (motif obligatoire, tracé) — droit particulier');

-- ═══ USER-ROLE ASSIGNMENTS ═══
MERGE INTO app_user_role (user_id, role_id) KEY (user_id, role_id)
VALUES ('b0b00001-0000-0000-0000-000000000001', 'a0a00001-0000-0000-0000-000000000001');
MERGE INTO app_user_role (user_id, role_id) KEY (user_id, role_id)
    VALUES ('b0b00001-0000-0000-0000-000000000001', 'a0a00001-0000-0000-0000-000000000007');
MERGE INTO app_user_role (user_id, role_id) KEY (user_id, role_id)
    VALUES ('b0b00001-0000-0000-0000-000000000001', 'a0a00001-0000-0000-0000-000000000008');
MERGE INTO app_user_role (user_id, role_id) KEY (user_id, role_id)
VALUES ('b0b00001-0000-0000-0000-000000000002', 'a0a00001-0000-0000-0000-000000000002');
MERGE INTO app_user_role (user_id, role_id) KEY (user_id, role_id)
    VALUES ('b0b00001-0000-0000-0000-000000000003', 'a0a00001-0000-0000-0000-000000000003');
MERGE INTO app_user_role (user_id, role_id) KEY (user_id, role_id)
    VALUES ('b0b00001-0000-0000-0000-000000000004', 'a0a00001-0000-0000-0000-000000000003');
MERGE INTO app_user_role (user_id, role_id) KEY (user_id, role_id)
    VALUES ('b0b00001-0000-0000-0000-000000000005', 'a0a00001-0000-0000-0000-000000000004');
MERGE INTO app_user_role (user_id, role_id) KEY (user_id, role_id)
    VALUES ('b0b00001-0000-0000-0000-000000000006', 'a0a00001-0000-0000-0000-000000000004');

-- ═══ USER-CENTER ASSIGNMENTS ═══
MERGE INTO app_user_center (user_id, center_id) KEY (user_id, center_id)
VALUES ('b0b00001-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111');
MERGE INTO app_user_center (user_id, center_id) KEY (user_id, center_id)
VALUES ('b0b00001-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222');
MERGE INTO app_user_center (user_id, center_id) KEY (user_id, center_id)
VALUES ('b0b00001-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111');
MERGE INTO app_user_center (user_id, center_id) KEY (user_id, center_id)
    VALUES ('b0b00001-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111');
MERGE INTO app_user_center (user_id, center_id) KEY (user_id, center_id)
    VALUES ('b0b00001-0000-0000-0000-000000000004', '22222222-2222-2222-2222-222222222222');
MERGE INTO app_user_center (user_id, center_id) KEY (user_id, center_id)
    VALUES ('b0b00001-0000-0000-0000-000000000005', '11111111-1111-1111-1111-111111111111');
MERGE INTO app_user_center (user_id, center_id) KEY (user_id, center_id)
    VALUES ('b0b00001-0000-0000-0000-000000000006', '22222222-2222-2222-2222-222222222222');
