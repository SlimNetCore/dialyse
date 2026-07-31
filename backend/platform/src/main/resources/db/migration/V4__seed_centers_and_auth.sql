INSERT INTO centers (id, code, name)
SELECT '11111111-1111-1111-1111-111111111111', 'CTR-DAKAR-01', 'ANNABA 1'
WHERE NOT EXISTS (SELECT 1 FROM centers WHERE id = '11111111-1111-1111-1111-111111111111');

INSERT INTO centers (id, code, name)
SELECT '22222222-2222-2222-2222-222222222222', 'CTR-SAINTLOUIS-01', 'ROUIBA'
WHERE NOT EXISTS (SELECT 1 FROM centers WHERE id = '22222222-2222-2222-2222-222222222222');

INSERT INTO user_center_assignment (id, user_id, center_id, role_code)
SELECT 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'admin', '11111111-1111-1111-1111-111111111111', 'ADMIN'
WHERE NOT EXISTS (
    SELECT 1 FROM user_center_assignment
    WHERE user_id = 'admin' AND center_id = '11111111-1111-1111-1111-111111111111' AND role_code = 'ADMIN'
);

INSERT INTO user_center_assignment (id, user_id, center_id, role_code)
SELECT 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'admin', '22222222-2222-2222-2222-222222222222', 'ADMIN'
WHERE NOT EXISTS (
    SELECT 1 FROM user_center_assignment
    WHERE user_id = 'admin' AND center_id = '22222222-2222-2222-2222-222222222222' AND role_code = 'ADMIN'
);

INSERT INTO user_center_assignment (id, user_id, center_id, role_code)
SELECT 'cccccccc-cccc-cccc-cccc-cccccccccccc', 'medecin', '11111111-1111-1111-1111-111111111111', 'MEDECIN'
WHERE NOT EXISTS (
    SELECT 1 FROM user_center_assignment
    WHERE user_id = 'medecin' AND center_id = '11111111-1111-1111-1111-111111111111' AND role_code = 'MEDECIN'
);
