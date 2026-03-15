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

MERGE INTO user_center_assignment (id, user_id, center_id, role_code) KEY (id)
VALUES ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'medecin', '11111111-1111-1111-1111-111111111111', 'MEDECIN');

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

-- Centre payeur (replaced cp -> c1 for valid hex UUID)
MERGE INTO centre_payeur (id, center_id, agence_id, code, nom, adresse) KEY (id)
VALUES ('c1000001-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'a0000001-0000-0000-0000-000000000001', '12308', 'Sidi Salem', 'Rue Principale, Sidi Salem, Annaba');
MERGE INTO centre_payeur (id, center_id, agence_id, code, nom, adresse) KEY (id)
VALUES ('c1000001-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'a0000001-0000-0000-0000-000000000001', '12309', 'El Bouni', 'Boulevard 1er Nov, El Bouni');
MERGE INTO centre_payeur (id, center_id, agence_id, code, nom, adresse) KEY (id)
VALUES ('c1000001-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'a0000001-0000-0000-0000-000000000002', '16001', 'Bab El Oued', 'Rue de la Liberté, Alger');

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

-- Transporteur (replaced t -> e2 for valid hex)
MERGE INTO transporteur (id, center_id, nom, telephone) KEY (id)
VALUES ('e2000001-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Ambulance Annaba', '038 84 00 00');
MERGE INTO transporteur (id, center_id, nom, telephone) KEY (id)
VALUES ('e2000001-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'Transport Médical Plus', '038 85 11 22');
MERGE INTO transporteur (id, center_id, nom, telephone) KEY (id)
VALUES ('e2000001-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'VSL Sud', '038 86 33 44');

-- Categorie transport (replaced ct -> c2 for valid hex)
MERGE INTO categorie_transport (id, center_id, libelle) KEY (id)
VALUES ('c2000001-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Ambulance sanitaire');
MERGE INTO categorie_transport (id, center_id, libelle) KEY (id)
VALUES ('c2000001-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'VSL');
MERGE INTO categorie_transport (id, center_id, libelle) KEY (id)
VALUES ('c2000001-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'Taxi médical');
MERGE INTO categorie_transport (id, center_id, libelle) KEY (id)
VALUES ('c2000001-0000-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111', 'Personnel');

-- Forfaits (replaced f -> f0 for valid hex)
MERGE INTO forfait (id, center_id, code, libelle, prix) KEY (id)
VALUES ('f0000001-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'F01', 'Forfait Standard', 5000.00);
MERGE INTO forfait (id, center_id, code, libelle, prix) KEY (id)
VALUES ('f0000001-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'F02', 'Forfait Complet', 8000.00);
MERGE INTO forfait (id, center_id, code, libelle, prix) KEY (id)
VALUES ('f0000001-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'F03', 'Forfait Minimum', 3500.00);

-- ═══ ROLES ═══
MERGE INTO app_role (id, code, name, description) KEY (id)
VALUES ('a0a00001-0000-0000-0000-000000000001', 'ADMIN', 'Administrateur', 'Accès complet');
MERGE INTO app_role (id, code, name, description) KEY (id)
VALUES ('a0a00001-0000-0000-0000-000000000002', 'MEDECIN', 'Médecin', 'Accès dossier médical et séances');
MERGE INTO app_role (id, code, name, description) KEY (id)
VALUES ('a0a00001-0000-0000-0000-000000000003', 'INFIRMIER', 'Infirmier', 'Accès aux séances et soins');
MERGE INTO app_role (id, code, name, description) KEY (id)
VALUES ('a0a00001-0000-0000-0000-000000000004', 'SECRETAIRE', 'Secrétaire', 'Gestion administrative');

-- ═══ USERS (passwords set by SeedPasswordInitializer at runtime) ═══
MERGE INTO app_user (id, username, password_hash, email, full_name, active) KEY (id)
VALUES ('b0b00001-0000-0000-0000-000000000001', 'admin', 'placeholder', 'admin@hemodialyse.dz', 'Administrateur Système', TRUE);
MERGE INTO app_user (id, username, password_hash, email, full_name, active) KEY (id)
VALUES ('b0b00001-0000-0000-0000-000000000002', 'medecin', 'placeholder', 'medecin@hemodialyse.dz', 'Dr. Nouri Ahmed', TRUE);

-- ═══ USER-ROLE ASSIGNMENTS ═══
MERGE INTO app_user_role (user_id, role_id) KEY (user_id, role_id)
VALUES ('b0b00001-0000-0000-0000-000000000001', 'a0a00001-0000-0000-0000-000000000001');
MERGE INTO app_user_role (user_id, role_id) KEY (user_id, role_id)
VALUES ('b0b00001-0000-0000-0000-000000000002', 'a0a00001-0000-0000-0000-000000000002');

-- ═══ USER-CENTER ASSIGNMENTS ═══
MERGE INTO app_user_center (user_id, center_id) KEY (user_id, center_id)
VALUES ('b0b00001-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111');
MERGE INTO app_user_center (user_id, center_id) KEY (user_id, center_id)
VALUES ('b0b00001-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222');
MERGE INTO app_user_center (user_id, center_id) KEY (user_id, center_id)
VALUES ('b0b00001-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111');

-- ═══ MODELES DE DOCUMENTS (Jasper) ═══
MERGE INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, format_impression, description) KEY (id)
VALUES ('d0d00001-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111',
        'FICHE_PATIENT', 'Fiche signalétique patient', 'FICHE_PATIENT',
        'reports/fiche_patient.jrxml', 'PDF', 'Fiche complète du patient avec ses informations personnelles et médicales');

MERGE INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, format_impression, description) KEY (id)
VALUES ('d0d00001-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111',
        'ATTESTATION', 'Attestation d''ouverture de droit', 'ATTESTATION',
        'reports/attestation.jrxml', 'PDF', 'Attestation d''ouverture de droit du patient');

MERGE INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, format_impression, description) KEY (id)
VALUES ('d0d00001-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111',
        'PEC', 'Prise en charge', 'PEC',
        'reports/prise_en_charge.jrxml', 'PDF', 'Document de prise en charge pour la caisse d''assurance');

MERGE INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, format_impression, description) KEY (id)
VALUES ('d0d00001-0000-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111',
        'LISTE_PATIENTS', 'Liste des patients', 'LISTE_PATIENTS',
        'reports/liste_patients.jrxml', 'PDF', 'Liste complète des patients du centre');

MERGE INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, format_impression, description) KEY (id)
VALUES ('d0d00001-0000-0000-0000-000000000005', '11111111-1111-1111-1111-111111111111',
        'LISTE_PEC', 'Liste des prises en charge', 'LISTE_PEC',
        'reports/liste_pec.jrxml', 'PDF', 'Liste de toutes les prises en charge du centre');

MERGE INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, format_impression, description) KEY (id)
VALUES ('d0d00001-0000-0000-0000-000000000006', '11111111-1111-1111-1111-111111111111',
        'LISTE_ATTESTATIONS', 'Liste des attestations', 'LISTE_ATTESTATIONS',
        'reports/liste_attestations.jrxml', 'PDF', 'Liste de toutes les attestations du centre');
