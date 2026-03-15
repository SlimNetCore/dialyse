-- ═══ Real User & Role Management Tables ═══

CREATE TABLE IF NOT EXISTS app_role (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS app_user (
    id UUID PRIMARY KEY,
    username VARCHAR(80) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(150),
    full_name VARCHAR(150),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS app_user_role (
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES app_role(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS app_user_center (
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    center_id UUID NOT NULL REFERENCES centers(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, center_id)
);

CREATE INDEX IF NOT EXISTS ix_app_user_username ON app_user(username);
CREATE INDEX IF NOT EXISTS ix_app_user_center_user ON app_user_center(user_id);
CREATE INDEX IF NOT EXISTS ix_app_user_center_center ON app_user_center(center_id);

-- ═══ Seed default roles ═══
MERGE INTO app_role (id, code, name, description) KEY (id)
VALUES ('r0000001-0000-0000-0000-000000000001', 'ADMIN', 'Administrateur', 'Accès complet à toutes les fonctionnalités');
MERGE INTO app_role (id, code, name, description) KEY (id)
VALUES ('r0000001-0000-0000-0000-000000000002', 'MEDECIN', 'Médecin', 'Accès au dossier médical et aux séances');
MERGE INTO app_role (id, code, name, description) KEY (id)
VALUES ('r0000001-0000-0000-0000-000000000003', 'INFIRMIER', 'Infirmier', 'Accès aux séances et soins');
MERGE INTO app_role (id, code, name, description) KEY (id)
VALUES ('r0000001-0000-0000-0000-000000000004', 'SECRETAIRE', 'Secrétaire', 'Gestion administrative des patients');

-- ═══ Seed default users (BCrypt hash of 'admin123' and 'medecin123') ═══
-- admin123 => $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
-- medecin123 => $2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36S6iqYi.H.XlQQXl0tD9Te
MERGE INTO app_user (id, username, password_hash, email, full_name, active) KEY (id)
VALUES ('u0000001-0000-0000-0000-000000000001', 'admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'admin@hemodialyse.dz', 'Administrateur Système', TRUE);
MERGE INTO app_user (id, username, password_hash, email, full_name, active) KEY (id)
VALUES ('u0000001-0000-0000-0000-000000000002', 'medecin', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36S6iqYi.H.XlQQXl0tD9Te', 'medecin@hemodialyse.dz', 'Dr. Nouri Ahmed', TRUE);

-- Assign roles
MERGE INTO app_user_role (user_id, role_id) KEY (user_id, role_id)
VALUES ('u0000001-0000-0000-0000-000000000001', 'r0000001-0000-0000-0000-000000000001');
MERGE INTO app_user_role (user_id, role_id) KEY (user_id, role_id)
VALUES ('u0000001-0000-0000-0000-000000000002', 'r0000001-0000-0000-0000-000000000002');

-- Assign centers
MERGE INTO app_user_center (user_id, center_id) KEY (user_id, center_id)
VALUES ('u0000001-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111');
MERGE INTO app_user_center (user_id, center_id) KEY (user_id, center_id)
VALUES ('u0000001-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222');
MERGE INTO app_user_center (user_id, center_id) KEY (user_id, center_id)
VALUES ('u0000001-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111');

