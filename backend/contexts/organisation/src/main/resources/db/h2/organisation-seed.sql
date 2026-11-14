-- Amorçage H2 (poste de développement et tests) des données dont l'Organisation est propriétaire (SEED2, ADR 0188) : sociétés, centres,
-- calendrier du centre, médecins, salles, positions/créneaux. Extrait, bloc à bloc et sans changement de contenu, de db/seed.sql.
-- Chargé par spring.sql.init.data-locations (application.yml de la plateforme), EN PREMIER : tout le reste du jeu référence ces centres
-- (app_user_center -> centers est une clé étrangère). Jamais rejoué en production (application-prod.yml : mode never).

-- Sociétés (une société chapeaute un ou plusieurs centres ; au moins un centre par société)
MERGE INTO societes (id, code, raison_sociale, nif, adresse, ville, wilaya, telephone, email, site_web, actif, created_at)
    KEY (id)
    VALUES ('51000001-0000-0000-0000-000000000001', 'GHNE', 'Groupe Hémodialyse Nord-Est', '099823000123456',
    '12 boulevard de la Révolution', 'Annaba', 'Annaba', '038 00 00 00', 'contact@ghne.example.dz',
    'https://www.ghne.example.dz', TRUE, CURRENT_TIMESTAMP);

MERGE INTO societes (id, code, raison_sociale, nif, adresse, ville, wilaya, telephone, email, site_web, actif, created_at)
    KEY (id)
    VALUES ('51000001-0000-0000-0000-000000000002', 'CLR', 'Clinique de Rouiba', '099816000654321',
    '3 rue des Frères Bouchama', 'Rouiba', 'Alger', '023 00 00 00', 'contact@clinique-rouiba.example.dz',
    'https://www.clinique-rouiba.example.dz', TRUE, CURRENT_TIMESTAMP);

-- Centers (chaque centre appartient à exactement une société)
MERGE INTO centers (id, code, name, societe_id, adresse, ville, wilaya, telephone, email, site_web, actif) KEY (id)
    VALUES ('11111111-1111-1111-1111-111111111111', 'CTR-DAKAR-01', 'ANNABA 1',
    '51000001-0000-0000-0000-000000000001', '12 boulevard de la Révolution', 'Annaba', 'Annaba',
    '038 00 00 01', 'annaba1@ghne.example.dz', 'https://www.ghne.example.dz/annaba-1', TRUE);

MERGE INTO centers (id, code, name, societe_id, adresse, ville, wilaya, telephone, email, site_web, actif) KEY (id)
    VALUES ('22222222-2222-2222-2222-222222222222', 'CTR-SAINTLOUIS-01', 'ROUIBA',
    '51000001-0000-0000-0000-000000000002', '3 rue des Frères Bouchama', 'Rouiba', 'Alger',
    '023 00 00 01', 'dialyse@clinique-rouiba.example.dz', 'https://www.clinique-rouiba.example.dz', TRUE);

-- Calendrier centre (jours fériés / fermetures exceptionnelles)
MERGE INTO center_holiday (id, center_id, day_date, label) KEY (id)
    VALUES ('91000001-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', DATE '2026-06-05',
            'Férié local');
MERGE INTO center_holiday (id, center_id, day_date, label) KEY (id)
    VALUES ('91000001-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222', DATE '2026-06-05',
            'Férié local');

MERGE INTO center_closure_day (id, center_id, day_date, reason) KEY (id)
    VALUES ('92000001-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', DATE '2026-06-20',
            'Maintenance clinique');
MERGE INTO center_closure_day (id, center_id, day_date, reason) KEY (id)
    VALUES ('92000001-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222', DATE '2026-06-21',
            'Fermeture exceptionnelle');

-- Medecin (replaced m -> e1 for valid hex)
MERGE INTO medecin (id, center_id, nom, prenom, specialite) KEY (id)
VALUES ('e1000001-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'NOURI', 'Ahmed', 'Néphrologue');
MERGE INTO medecin (id, center_id, nom, prenom, specialite) KEY (id)
VALUES ('e1000001-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'BENALI', 'Fatima', 'Néphrologue');
MERGE INTO medecin (id, center_id, nom, prenom, specialite) KEY (id)
VALUES ('e1000001-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'KACI', 'Mohamed', 'Médecin généraliste');

-- Salle (replaced s -> 50 for valid hex)
MERGE INTO salle (id, center_id, code, nom) KEY (id)
VALUES ('50000001-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'S01', 'Salle A');
MERGE INTO salle (id, center_id, code, nom) KEY (id)
VALUES ('50000001-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'S02', 'Salle B');
MERGE INTO salle (id, center_id, code, nom) KEY (id)
VALUES ('50000001-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'S03', 'Salle ISO');

-- Position / Créneau (replaced p -> d0 for valid hex)
MERGE INTO position_creneau (id, center_id, code, libelle) KEY (id)
VALUES ('d0000001-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'CR001', 'Matin 1 (06h-10h)');
MERGE INTO position_creneau (id, center_id, code, libelle) KEY (id)
VALUES ('d0000001-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'CR002', 'Matin 2 (10h-14h)');
MERGE INTO position_creneau (id, center_id, code, libelle) KEY (id)
VALUES ('d0000001-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'CR003', 'Après-midi (14h-18h)');
MERGE INTO position_creneau (id, center_id, code, libelle) KEY (id)
VALUES ('d0000001-0000-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111', 'CR004', 'Nuit (18h-22h)');

-- ═══ REFERENTIELS - CENTRE 2 ═══

MERGE INTO medecin (id, center_id, nom, prenom, specialite) KEY (id)
    VALUES ('e1000002-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'DIOP', 'Mamadou',
            'Néphrologue');
MERGE INTO medecin (id, center_id, nom, prenom, specialite) KEY (id)
    VALUES ('e1000002-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222', 'SOW', 'Aissatou',
            'Néphrologue');

MERGE INTO salle (id, center_id, code, nom) KEY (id)
    VALUES ('50000002-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'SL-S1', 'Salle Bleu');
MERGE INTO salle (id, center_id, code, nom) KEY (id)
    VALUES ('50000002-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222', 'SL-S2', 'Salle Vert');

MERGE INTO position_creneau (id, center_id, code, libelle) KEY (id)
    VALUES ('d0000002-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'SL-CR1',
            'Matin (07h-11h)');
MERGE INTO position_creneau (id, center_id, code, libelle) KEY (id)
    VALUES ('d0000002-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222', 'SL-CR2',
            'Après-midi (13h-17h)');
