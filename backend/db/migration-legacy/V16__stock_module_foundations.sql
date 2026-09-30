-- V16: Stock module foundations
-- app_settings: auto-incremented counters for BL/BR/BS references (per center)
-- Note: stock domain tables (fournisseurs, emplacements, lots, bons_*) are created
-- by Hibernate (spring.jpa.hibernate.ddl-auto=update) with indexes declared on the
-- JPA entities. Flyway only handles pure-SQL settings and role seeding here.

CREATE TABLE IF NOT EXISTS app_settings
(
    center_id        UUID        NOT NULL,
    cle              VARCHAR(50) NOT NULL,
    prefixe          VARCHAR(20) NOT NULL,
    dernier_compteur BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (center_id, cle)
);

-- Seed sequence counters (BL / BR / BS) for the two demo centers
INSERT INTO app_settings (center_id, cle, prefixe, dernier_compteur)
SELECT '11111111-1111-1111-1111-111111111111', 'SEQ_BL', 'BL-', 0
WHERE NOT EXISTS (SELECT 1
                  FROM app_settings
                  WHERE center_id = '11111111-1111-1111-1111-111111111111' AND cle = 'SEQ_BL');
INSERT INTO app_settings (center_id, cle, prefixe, dernier_compteur)
SELECT '11111111-1111-1111-1111-111111111111', 'SEQ_BR', 'BR-', 0
WHERE NOT EXISTS (SELECT 1
                  FROM app_settings
                  WHERE center_id = '11111111-1111-1111-1111-111111111111' AND cle = 'SEQ_BR');
INSERT INTO app_settings (center_id, cle, prefixe, dernier_compteur)
SELECT '11111111-1111-1111-1111-111111111111', 'SEQ_BS', 'BS-', 0
WHERE NOT EXISTS (SELECT 1
                  FROM app_settings
                  WHERE center_id = '11111111-1111-1111-1111-111111111111' AND cle = 'SEQ_BS');

INSERT INTO app_settings (center_id, cle, prefixe, dernier_compteur)
SELECT '22222222-2222-2222-2222-222222222222', 'SEQ_BL', 'BL-', 0
WHERE NOT EXISTS (SELECT 1
                  FROM app_settings
                  WHERE center_id = '22222222-2222-2222-2222-222222222222' AND cle = 'SEQ_BL');
INSERT INTO app_settings (center_id, cle, prefixe, dernier_compteur)
SELECT '22222222-2222-2222-2222-222222222222', 'SEQ_BR', 'BR-', 0
WHERE NOT EXISTS (SELECT 1
                  FROM app_settings
                  WHERE center_id = '22222222-2222-2222-2222-222222222222' AND cle = 'SEQ_BR');
INSERT INTO app_settings (center_id, cle, prefixe, dernier_compteur)
SELECT '22222222-2222-2222-2222-222222222222', 'SEQ_BS', 'BS-', 0
WHERE NOT EXISTS (SELECT 1
                  FROM app_settings
                  WHERE center_id = '22222222-2222-2222-2222-222222222222' AND cle = 'SEQ_BS');

-- Grant PHARMACIEN role to the admin user in both centers (testable role)
INSERT INTO user_center_assignment (id, user_id, center_id, role_code)
SELECT 'dddddddd-dddd-dddd-dddd-dddddddddddd', 'admin', '11111111-1111-1111-1111-111111111111', 'PHARMACIEN'
WHERE NOT EXISTS (SELECT 1
                  FROM user_center_assignment
                  WHERE user_id = 'admin'
                    AND center_id = '11111111-1111-1111-1111-111111111111'
                    AND role_code = 'PHARMACIEN');
INSERT INTO user_center_assignment (id, user_id, center_id, role_code)
SELECT 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 'admin', '22222222-2222-2222-2222-222222222222', 'PHARMACIEN'
WHERE NOT EXISTS (SELECT 1
                  FROM user_center_assignment
                  WHERE user_id = 'admin'
                    AND center_id = '22222222-2222-2222-2222-222222222222'
                    AND role_code = 'PHARMACIEN');

