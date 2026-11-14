-- Amorçage H2 (poste de développement et tests) des générateurs de dialyse de démonstration (SEED2, ADR 0188) : le GMAO est propriétaire
-- de gmao_equipements (table créée par Hibernate, ddl-auto: update). Extrait, bloc à bloc et sans changement de contenu, de db/seed.sql.
-- Chargé par spring.sql.init.data-locations (application.yml de la plateforme) APRÈS l'Organisation (salle_id, centre_id).

-- Générateurs de dialyse (démo) — centre 1 — module GMAO v2 : Equipement (type GENERATEUR_DIALYSE)
-- est la source de vérité unique.
MERGE INTO gmao_equipements
    (id, code, designation, type, fabricant, modele, date_installation, centre_id, statut,
    salle_id, date_creation, cree_par) KEY (id)
    VALUES ('a1000001-0000-0000-0000-000000000001', 'G01', 'Générateur G01', 'GENERATEUR_DIALYSE',
    'Fresenius', '5008S', CURRENT_TIMESTAMP, '11111111-1111-1111-1111-111111111111', 'EN_SERVICE',
    '50000001-0000-0000-0000-000000000001', CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000000');
MERGE INTO gmao_equipements
    (id, code, designation, type, fabricant, modele, date_installation, centre_id, statut,
    salle_id, date_creation, cree_par) KEY (id)
    VALUES ('a1000001-0000-0000-0000-000000000002', 'G02', 'Générateur G02', 'GENERATEUR_DIALYSE',
    'B.Braun', 'Dialog+', CURRENT_TIMESTAMP, '11111111-1111-1111-1111-111111111111', 'HORS_SERVICE',
    '50000001-0000-0000-0000-000000000002', CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000000');
MERGE INTO gmao_equipements
    (id, code, designation, type, fabricant, modele, date_installation, centre_id, statut,
    salle_id, date_creation, cree_par) KEY (id)
    VALUES ('a1000001-0000-0000-0000-000000000003', 'G03', 'Générateur G03', 'GENERATEUR_DIALYSE',
    'Nipro', 'SURDIAL-55plus', CURRENT_TIMESTAMP, '11111111-1111-1111-1111-111111111111', 'EN_MAINTENANCE',
    '50000001-0000-0000-0000-000000000003', CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000000');

MERGE INTO gmao_equipements
    (id, code, designation, type, fabricant, modele, date_installation, centre_id, statut,
    salle_id, date_creation, cree_par) KEY (id)
    VALUES ('a1000002-0000-0000-0000-000000000001', 'G10', 'Générateur G10', 'GENERATEUR_DIALYSE',
    'Fresenius', '5008S', CURRENT_TIMESTAMP, '22222222-2222-2222-2222-222222222222', 'EN_SERVICE',
    '50000002-0000-0000-0000-000000000001', CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000000');
MERGE INTO gmao_equipements
    (id, code, designation, type, fabricant, modele, date_installation, centre_id, statut,
    salle_id, date_creation, cree_par) KEY (id)
    VALUES ('a1000002-0000-0000-0000-000000000002', 'G11', 'Générateur G11', 'GENERATEUR_DIALYSE',
    'B.Braun', 'Dialog+', CURRENT_TIMESTAMP, '22222222-2222-2222-2222-222222222222', 'REFORME',
    '50000002-0000-0000-0000-000000000002', CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000000');
