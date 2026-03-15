-- Seed data for local development (H2)
-- Centers
MERGE INTO centers (id, code, name) KEY (id)
VALUES ('11111111-1111-1111-1111-111111111111', 'CTR-DAKAR-01', 'Centre Dakar Principal');

MERGE INTO centers (id, code, name) KEY (id)
VALUES ('22222222-2222-2222-2222-222222222222', 'CTR-SAINTLOUIS-01', 'Centre Saint-Louis');

-- Admin user assignment to both centers
MERGE INTO user_center_assignment (id, user_id, center_id, role_code) KEY (id)
VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'admin', '11111111-1111-1111-1111-111111111111', 'ADMIN');

MERGE INTO user_center_assignment (id, user_id, center_id, role_code) KEY (id)
VALUES ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'admin', '22222222-2222-2222-2222-222222222222', 'ADMIN');

-- ═══ REFERENTIAL SEED DATA ═══

-- Caisse d'assurance
MERGE INTO caisse_assurance (id, center_id, code, nom, type_caisse) KEY (id)
VALUES ('c0000001-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'CNAS', 'CNAS', 'STANDARD');
MERGE INTO caisse_assurance (id, center_id, code, nom, type_caisse) KEY (id)
VALUES ('c0000001-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'CASNOS', 'CASNOS', 'STANDARD');
MERGE INTO caisse_assurance (id, center_id, code, nom, type_caisse) KEY (id)
VALUES ('c0000001-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'VACANCIER', 'Caisse Vacancier', 'VACANCIER');

-- Agence
MERGE INTO agence (id, center_id, caisse_id, code, nom) KEY (id)
VALUES ('a0000001-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'c0000001-0000-0000-0000-000000000001', '23', 'Annaba');
MERGE INTO agence (id, center_id, caisse_id, code, nom) KEY (id)
VALUES ('a0000001-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'c0000001-0000-0000-0000-000000000001', '16', 'Alger');
MERGE INTO agence (id, center_id, caisse_id, code, nom) KEY (id)
VALUES ('a0000001-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'c0000001-0000-0000-0000-000000000002', '23', 'Annaba CASNOS');

-- Centre payeur
MERGE INTO centre_payeur (id, center_id, agence_id, code, nom, adresse) KEY (id)
VALUES ('cp000001-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'a0000001-0000-0000-0000-000000000001', '12308', 'Sidi Salem', 'Rue Principale, Sidi Salem, Annaba');
MERGE INTO centre_payeur (id, center_id, agence_id, code, nom, adresse) KEY (id)
VALUES ('cp000001-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'a0000001-0000-0000-0000-000000000001', '12309', 'El Bouni', 'Boulevard 1er Nov, El Bouni');
MERGE INTO centre_payeur (id, center_id, agence_id, code, nom, adresse) KEY (id)
VALUES ('cp000001-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'a0000001-0000-0000-0000-000000000002', '16001', 'Bab El Oued', 'Rue de la Liberté, Alger');

-- Medecin
MERGE INTO medecin (id, center_id, nom, prenom, specialite) KEY (id)
VALUES ('m0000001-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'NOURI', 'Ahmed', 'Néphrologue');
MERGE INTO medecin (id, center_id, nom, prenom, specialite) KEY (id)
VALUES ('m0000001-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'BENALI', 'Fatima', 'Néphrologue');
MERGE INTO medecin (id, center_id, nom, prenom, specialite) KEY (id)
VALUES ('m0000001-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'KACI', 'Mohamed', 'Médecin généraliste');

-- Salle
MERGE INTO salle (id, center_id, code, nom) KEY (id)
VALUES ('s0000001-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'S01', 'Salle A');
MERGE INTO salle (id, center_id, code, nom) KEY (id)
VALUES ('s0000001-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'S02', 'Salle B');
MERGE INTO salle (id, center_id, code, nom) KEY (id)
VALUES ('s0000001-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'S03', 'Salle ISO');

-- Position / Créneau
MERGE INTO position_creneau (id, center_id, code, libelle) KEY (id)
VALUES ('p0000001-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'CR001', 'Matin 1 (06h-10h)');
MERGE INTO position_creneau (id, center_id, code, libelle) KEY (id)
VALUES ('p0000001-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'CR002', 'Matin 2 (10h-14h)');
MERGE INTO position_creneau (id, center_id, code, libelle) KEY (id)
VALUES ('p0000001-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'CR003', 'Après-midi (14h-18h)');
MERGE INTO position_creneau (id, center_id, code, libelle) KEY (id)
VALUES ('p0000001-0000-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111', 'CR004', 'Nuit (18h-22h)');

-- Transporteur
MERGE INTO transporteur (id, center_id, nom, telephone) KEY (id)
VALUES ('t0000001-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Ambulance Annaba', '038 84 00 00');
MERGE INTO transporteur (id, center_id, nom, telephone) KEY (id)
VALUES ('t0000001-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'Transport Médical Plus', '038 85 11 22');
MERGE INTO transporteur (id, center_id, nom, telephone) KEY (id)
VALUES ('t0000001-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'VSL Sud', '038 86 33 44');

-- Categorie transport
MERGE INTO categorie_transport (id, center_id, libelle) KEY (id)
VALUES ('ct000001-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Ambulance sanitaire');
MERGE INTO categorie_transport (id, center_id, libelle) KEY (id)
VALUES ('ct000001-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'VSL');
MERGE INTO categorie_transport (id, center_id, libelle) KEY (id)
VALUES ('ct000001-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'Taxi médical');
MERGE INTO categorie_transport (id, center_id, libelle) KEY (id)
VALUES ('ct000001-0000-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111', 'Personnel');
