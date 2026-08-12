-- Seed data for local development (H2)
-- Centers
MERGE INTO centers (id, code, name) KEY (id)
    VALUES ('11111111-1111-1111-1111-111111111111', 'CTR-DAKAR-01', 'ANNABA 1');

MERGE INTO centers (id, code, name) KEY (id)
    VALUES ('22222222-2222-2222-2222-222222222222', 'CTR-SAINTLOUIS-01', 'ROUIBA');

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

-- Admin user assignment to both centers
MERGE INTO user_center_assignment (id, user_id, center_id, role_code) KEY (id)
VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'admin', '11111111-1111-1111-1111-111111111111', 'ADMIN');

MERGE INTO user_center_assignment (id, user_id, center_id, role_code) KEY (id)
VALUES ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'admin', '22222222-2222-2222-2222-222222222222', 'ADMIN');

MERGE INTO user_center_assignment (id, user_id, center_id, role_code) KEY (id)
VALUES ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'medecin', '11111111-1111-1111-1111-111111111111', 'MEDECIN');

MERGE INTO user_center_assignment (id, user_id, center_id, role_code) KEY (id)
    VALUES ('dddddddd-dddd-dddd-dddd-dddddddddddd', 'infirmier-annaba', '11111111-1111-1111-1111-111111111111',
            'INFIRMIER');

MERGE INTO user_center_assignment (id, user_id, center_id, role_code) KEY (id)
    VALUES ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 'infirmier-rouiba', '22222222-2222-2222-2222-222222222222',
            'INFIRMIER');

MERGE INTO user_center_assignment (id, user_id, center_id, role_code) KEY (id)
    VALUES ('ffffffff-ffff-ffff-ffff-ffffffffffff', 'secretaire-annaba', '11111111-1111-1111-1111-111111111111',
            'SECRETAIRE');

MERGE INTO user_center_assignment (id, user_id, center_id, role_code) KEY (id)
    VALUES ('12121212-1212-1212-1212-121212121212', 'secretaire-rouiba', '22222222-2222-2222-2222-222222222222',
            'SECRETAIRE');

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

-- Generateur (démo) — centre 1
MERGE INTO generateur (id, salle_id, center_id, numero, marque, modele, etat) KEY (id)
    VALUES ('a1000001-0000-0000-0000-000000000001', '50000001-0000-0000-0000-000000000001',
            '11111111-1111-1111-1111-111111111111', 'G01', 'Fresenius', '5008S', 'FONCTIONNEL');
MERGE INTO generateur (id, salle_id, center_id, numero, marque, modele, etat) KEY (id)
    VALUES ('a1000001-0000-0000-0000-000000000002', '50000001-0000-0000-0000-000000000002',
            '11111111-1111-1111-1111-111111111111', 'G02', 'B.Braun', 'Dialog+', 'EN_PANNE');
MERGE INTO generateur (id, salle_id, center_id, numero, marque, modele, etat) KEY (id)
    VALUES ('a1000001-0000-0000-0000-000000000003', '50000001-0000-0000-0000-000000000003',
            '11111111-1111-1111-1111-111111111111', 'G03', 'Nipro', 'SURDIAL-55plus', 'EN_REPARATION');

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

-- ═══ USER-ROLE ASSIGNMENTS ═══
MERGE INTO app_user_role (user_id, role_id) KEY (user_id, role_id)
VALUES ('b0b00001-0000-0000-0000-000000000001', 'a0a00001-0000-0000-0000-000000000001');
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

-- Modèles par défaut pour le centre 2 (multi-centre)
MERGE INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, format_impression, description) KEY (id)
VALUES ('d0d00002-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222',
        'FICHE_PATIENT', 'Fiche signalétique patient', 'FICHE_PATIENT',
        'reports/fiche_patient.jrxml', 'PDF', 'Fiche complète du patient avec ses informations personnelles et médicales');

MERGE INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, format_impression, description) KEY (id)
VALUES ('d0d00002-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222',
        'ATTESTATION', 'Attestation d''ouverture de droit', 'ATTESTATION',
        'reports/attestation.jrxml', 'PDF', 'Attestation d''ouverture de droit du patient');

MERGE INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, format_impression, description) KEY (id)
VALUES ('d0d00002-0000-0000-0000-000000000003', '22222222-2222-2222-2222-222222222222',
        'PEC', 'Prise en charge', 'PEC',
        'reports/prise_en_charge.jrxml', 'PDF', 'Document de prise en charge pour la caisse d''assurance');

MERGE INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, format_impression, description) KEY (id)
VALUES ('d0d00002-0000-0000-0000-000000000004', '22222222-2222-2222-2222-222222222222',
        'LISTE_PATIENTS', 'Liste des patients', 'LISTE_PATIENTS',
        'reports/liste_patients.jrxml', 'PDF', 'Liste complète des patients du centre');

MERGE INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, format_impression, description) KEY (id)
VALUES ('d0d00002-0000-0000-0000-000000000005', '22222222-2222-2222-2222-222222222222',
        'LISTE_PEC', 'Liste des prises en charge', 'LISTE_PEC',
        'reports/liste_pec.jrxml', 'PDF', 'Liste de toutes les prises en charge du centre');

MERGE INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, format_impression, description) KEY (id)
VALUES ('d0d00002-0000-0000-0000-000000000006', '22222222-2222-2222-2222-222222222222',
        'LISTE_ATTESTATIONS', 'Liste des attestations', 'LISTE_ATTESTATIONS',
        'reports/liste_attestations.jrxml', 'PDF', 'Liste de toutes les attestations du centre');

-- ═══ REFERENTIELS - CENTRE 2 ═══

MERGE INTO caisse_assurance (id, center_id, code, nom, type_caisse) KEY (id)
    VALUES ('c0000002-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'CNAS-SL',
            'CNAS Saint-Louis', 'STANDARD');
MERGE INTO caisse_assurance (id, center_id, code, nom, type_caisse) KEY (id)
    VALUES ('c0000002-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222', 'VAC-SL',
            'Caisse Vacancier Saint-Louis', 'VACANCIER');

MERGE INTO agence (id, center_id, caisse_id, code, nom) KEY (id)
    VALUES ('a0000002-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222',
            'c0000002-0000-0000-0000-000000000001', 'SL-01', 'Saint-Louis Ville');
MERGE INTO agence (id, center_id, caisse_id, code, nom) KEY (id)
    VALUES ('a0000002-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222',
            'c0000002-0000-0000-0000-000000000002', 'SL-VAC', 'Saint-Louis Vacanciers');

MERGE INTO centre_payeur (id, center_id, agence_id, code, nom, adresse) KEY (id)
    VALUES ('c1000002-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222',
            'a0000002-0000-0000-0000-000000000001', '22001', 'Centre Payeur SL 1', 'Avenue Principale, Saint-Louis');
MERGE INTO centre_payeur (id, center_id, agence_id, code, nom, adresse) KEY (id)
    VALUES ('c1000002-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222',
            'a0000002-0000-0000-0000-000000000001', '22002', 'Centre Payeur SL 2', 'Quartier Nord, Saint-Louis');
MERGE INTO centre_payeur (id, center_id, agence_id, code, nom, adresse) KEY (id)
    VALUES ('c1000002-0000-0000-0000-000000000003', '22222222-2222-2222-2222-222222222222',
            'a0000002-0000-0000-0000-000000000002', '22999', 'Centre Payeur Vacancier', 'Front de mer, Saint-Louis');

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

MERGE INTO generateur (id, salle_id, center_id, numero, marque, modele, etat) KEY (id)
    VALUES ('a1000002-0000-0000-0000-000000000001', '50000002-0000-0000-0000-000000000001',
            '22222222-2222-2222-2222-222222222222', 'G10', 'Fresenius', '5008S', 'FONCTIONNEL');
MERGE INTO generateur (id, salle_id, center_id, numero, marque, modele, etat) KEY (id)
    VALUES ('a1000002-0000-0000-0000-000000000002', '50000002-0000-0000-0000-000000000002',
            '22222222-2222-2222-2222-222222222222', 'G11', 'B.Braun', 'Dialog+', 'REFORME');

MERGE INTO position_creneau (id, center_id, code, libelle) KEY (id)
    VALUES ('d0000002-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'SL-CR1',
            'Matin (07h-11h)');
MERGE INTO position_creneau (id, center_id, code, libelle) KEY (id)
    VALUES ('d0000002-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222', 'SL-CR2',
            'Après-midi (13h-17h)');

MERGE INTO transporteur (id, center_id, nom, telephone) KEY (id)
    VALUES ('e2000002-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'Ambulance SL',
            '033 900 11 22');
MERGE INTO transporteur (id, center_id, nom, telephone) KEY (id)
    VALUES ('e2000002-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222', 'VSL Delta',
            '033 900 33 44');

MERGE INTO categorie_transport (id, center_id, libelle) KEY (id)
    VALUES ('c2000002-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'Ambulance');
MERGE INTO categorie_transport (id, center_id, libelle) KEY (id)
    VALUES ('c2000002-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222', 'VSL');

MERGE INTO forfait (id, center_id, code, libelle, prix) KEY (id)
    VALUES ('f0000002-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'SL-F01',
            'Forfait Standard SL', 5200.00);
MERGE INTO forfait (id, center_id, code, libelle, prix) KEY (id)
    VALUES ('f0000002-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222', 'SL-F02',
            'Forfait Premium SL', 8800.00);

-- ═══ PATIENTS DE TEST (CENTRE 1 + CENTRE 2) ═══

MERGE INTO patients (
                     id, center_id, code_patient, civilite, nom, prenom, sexe, groupe_sanguin,
                     date_admission, date_naissance, lieu_naissance, situation_familiale,
                     profession1, adresse, tel_mobile, email, numero_assurance, type_patient,
                     etat_patient, qualite_assure, sous_kt, epo_enabled, fer_enabled,
                     centre_payeur_id, medecin_traitant_id, salle_id, position_id,
                     transporteur_aller_id, transporteur_retour_id, categorie_transport_id,
                     jour_lundi, jour_mercredi, jour_vendredi
    ) KEY (id)
    VALUES ('b1000001-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'P-ANN-0001', 'M', 'AMRANI',
            'Sami', 'M', 'O+',
            DATE '2025-01-12', DATE '1980-03-10', 'Annaba', 'Marié',
            'Comptable', 'Cité 120 logements, Annaba', '0550112233', 'sami.amrani@example.com', 'ASS-ANN-0001',
            'NON_VACANCIER',
            'PERMANENT', 'ASSURE_LUI_MEME', TRUE, TRUE, FALSE,
            'c1000001-0000-0000-0000-000000000001', 'e1000001-0000-0000-0000-000000000001',
            '50000001-0000-0000-0000-000000000001', 'd0000001-0000-0000-0000-000000000001',
            'e2000001-0000-0000-0000-000000000001', 'e2000001-0000-0000-0000-000000000001',
            'c2000001-0000-0000-0000-000000000001',
            TRUE, TRUE, TRUE);

MERGE INTO patients (
                     id, center_id, code_patient, civilite, nom, prenom, sexe, groupe_sanguin,
                     date_admission, date_naissance, lieu_naissance, situation_familiale,
                     profession1, adresse, tel_mobile, email, numero_assurance, type_patient,
                     etat_patient, qualite_assure, sous_kt, epo_enabled, fer_enabled,
                     centre_payeur_id, medecin_traitant_id, salle_id, position_id,
                     transporteur_aller_id, transporteur_retour_id, categorie_transport_id,
                     jour_mardi, jour_jeudi, jour_samedi
    ) KEY (id)
    VALUES ('b1000001-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'P-ANN-0002', 'Mme',
            'BELAID', 'Nadia', 'F', 'A+',
            DATE '2025-02-01', DATE '1990-07-21', 'El Bouni', 'Célibataire',
            'Enseignante', 'Rue des Jasmins, Annaba', '0661223344', 'nadia.belaid@example.com', 'ASS-ANN-0002',
            'NON_VACANCIER',
            'OCCASIONNEL', 'CONJOINT', FALSE, FALSE, TRUE,
            'c1000001-0000-0000-0000-000000000002', 'e1000001-0000-0000-0000-000000000002',
            '50000001-0000-0000-0000-000000000002', 'd0000001-0000-0000-0000-000000000002',
            'e2000001-0000-0000-0000-000000000002', 'e2000001-0000-0000-0000-000000000003',
            'c2000001-0000-0000-0000-000000000002',
            TRUE, TRUE, FALSE);

MERGE INTO patients (
                     id, center_id, code_patient, civilite, nom, prenom, sexe, groupe_sanguin,
                     date_admission, date_naissance, lieu_naissance, situation_familiale,
                     profession1, adresse, tel_mobile, email, numero_assurance, type_patient,
                     etat_patient, qualite_assure, sous_kt,
                     centre_payeur_id, medecin_traitant_id, salle_id, position_id,
                     transporteur_aller_id, transporteur_retour_id, categorie_transport_id,
                     jour_lundi, jour_mercredi
    ) KEY (id)
    VALUES ('b2000001-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'P-SL-0001', 'M', 'FALL',
            'Ibrahima', 'M', 'B+',
            DATE '2025-01-18', DATE '1978-11-02', 'Saint-Louis', 'Marié',
            'Fonctionnaire', 'Quartier Sud, Saint-Louis', '0778112233', 'ibrahima.fall@example.com', 'ASS-SL-0001',
            'NON_VACANCIER',
            'PERMANENT', 'ASSURE_LUI_MEME', FALSE,
            'c1000002-0000-0000-0000-000000000001', 'e1000002-0000-0000-0000-000000000001',
            '50000002-0000-0000-0000-000000000001', 'd0000002-0000-0000-0000-000000000001',
            'e2000002-0000-0000-0000-000000000001', 'e2000002-0000-0000-0000-000000000001',
            'c2000002-0000-0000-0000-000000000001',
            TRUE, TRUE);

MERGE INTO patients (
                     id, center_id, code_patient, civilite, nom, prenom, sexe, groupe_sanguin,
                     date_admission, date_naissance, lieu_naissance, situation_familiale,
                     profession1, adresse, tel_mobile, email, numero_assurance, type_patient,
                     etat_patient, qualite_assure, sous_kt,
                     centre_payeur_id, medecin_traitant_id, salle_id, position_id,
                     transporteur_aller_id, transporteur_retour_id, categorie_transport_id,
                     jour_mardi, jour_jeudi
    ) KEY (id)
    VALUES ('b2000001-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222', 'P-SL-0002', 'Mme',
            'NDIAYE', 'Aminata', 'F', 'O+',
            DATE '2025-03-05', DATE '1988-06-16', 'Dakar', 'Mariée',
            'Commerçante', 'Rue 11, Saint-Louis', '0778998877', 'aminata.ndiaye@example.com', 'ASS-SL-0002',
            'VACANCIER',
            'VACANCIER_ETRANGER', 'AUTRE', FALSE,
            'c1000002-0000-0000-0000-000000000003', 'e1000002-0000-0000-0000-000000000002',
            '50000002-0000-0000-0000-000000000002', 'd0000002-0000-0000-0000-000000000002',
            'e2000002-0000-0000-0000-000000000002', 'e2000002-0000-0000-0000-000000000002',
            'c2000002-0000-0000-0000-000000000002',
            TRUE, TRUE);

-- ═══ ASSURES DE TEST + HISTORIQUE ═══

MERGE INTO assure (numero_assurance, center_id, nom, prenom, sexe, date_naissance, tel_personnel, tel_mobile, adresse,
                   groupe_sanguin) KEY (numero_assurance)
    VALUES ('ASSURE-ANN-1001', '11111111-1111-1111-1111-111111111111', 'BELAID', 'Karim', 'M', DATE '1965-09-12',
            '038500001', '055500001', 'Annaba Centre', 'A+');
MERGE INTO assure (numero_assurance, center_id, nom, prenom, sexe, date_naissance, tel_personnel, tel_mobile, adresse,
                   groupe_sanguin) KEY (numero_assurance)
    VALUES ('ASSURE-SL-2001', '22222222-2222-2222-2222-222222222222', 'FALL', 'Mariama', 'F', DATE '1970-12-30',
            '033900001', '077700001', 'Saint-Louis Ville', 'B+');

MERGE INTO assure_patient (id, patient_id, numero_assurance, center_id, is_primary, date_affectation) KEY (id)
    VALUES ('c1000001-0000-0000-0000-000000000001', 'b1000001-0000-0000-0000-000000000002', 'ASSURE-ANN-1001',
            '11111111-1111-1111-1111-111111111111', TRUE,
            CURRENT_TIMESTAMP);
MERGE INTO assure_patient (id, patient_id, numero_assurance, center_id, is_primary, date_affectation) KEY (id)
    VALUES ('c2000001-0000-0000-0000-000000000001', 'b2000001-0000-0000-0000-000000000001', 'ASSURE-SL-2001',
            '22222222-2222-2222-2222-222222222222', TRUE,
            CURRENT_TIMESTAMP);

-- ═══ ATTESTATIONS / PEC DE TEST ═══

MERGE INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin) KEY (id)
    VALUES ('a7000001-0000-0000-0000-000000000001', 'b1000001-0000-0000-0000-000000000001',
            '11111111-1111-1111-1111-111111111111', DATE '2025-01-01', DATE '2025-12-31');
MERGE INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin) KEY (id)
    VALUES ('a7000001-0000-0000-0000-000000000002', 'b1000001-0000-0000-0000-000000000002',
            '11111111-1111-1111-1111-111111111111', DATE '2025-02-01', DATE '2025-11-30');
MERGE INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin) KEY (id)
    VALUES ('a7000002-0000-0000-0000-000000000001', 'b2000001-0000-0000-0000-000000000001',
            '22222222-2222-2222-2222-222222222222', DATE '2025-01-15', DATE '2025-10-31');

MERGE INTO prise_en_charge (
                            id, patient_id, center_id,
                            date_debut_demande, date_fin_demande, forfait_demande_id,
                            date_debut_effectif, date_fin_effectif, forfait_effectif_id,
                            statut
    ) KEY (id)
    VALUES ('b7000001-0000-0000-0000-000000000001', 'b1000001-0000-0000-0000-000000000001',
            '11111111-1111-1111-1111-111111111111',
            DATE '2025-01-01', DATE '2025-06-30', 'f0000001-0000-0000-0000-000000000001',
            DATE '2025-01-05', DATE '2025-06-30', 'f0000001-0000-0000-0000-000000000001',
            'VALIDEE');

MERGE INTO prise_en_charge (
                            id, patient_id, center_id,
                            date_debut_demande, date_fin_demande, forfait_demande_id,
                            statut
    ) KEY (id)
    VALUES ('b7000001-0000-0000-0000-000000000002', 'b1000001-0000-0000-0000-000000000002',
            '11111111-1111-1111-1111-111111111111',
            DATE '2025-03-01', DATE '2025-09-30', 'f0000001-0000-0000-0000-000000000002',
            'CREE');

MERGE INTO prise_en_charge (
                            id, patient_id, center_id,
                            date_debut_demande, date_fin_demande, forfait_demande_id,
                            date_debut_effectif, date_fin_effectif, forfait_effectif_id,
                            statut
    ) KEY (id)
    VALUES ('b7000002-0000-0000-0000-000000000001', 'b2000001-0000-0000-0000-000000000001',
            '22222222-2222-2222-2222-222222222222',
            DATE '2025-02-01', DATE '2025-08-31', 'f0000002-0000-0000-0000-000000000001',
            DATE '2025-02-03', DATE '2025-08-31', 'f0000002-0000-0000-0000-000000000001',
            'VALIDEE');

-- ═══ LOT PATIENTS SUPPLEMENTAIRE (PAGINATION/RECHERCHE) ═══

MERGE INTO patients (
                     id, center_id, code_patient, nom, prenom, sexe, date_admission, numero_assurance, type_patient,
                     etat_patient, qualite_assure, centre_payeur_id, medecin_traitant_id, salle_id, position_id,
                     transporteur_aller_id, transporteur_retour_id, categorie_transport_id
    ) KEY (id)
    VALUES ('b3000001-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'P-ANN-0003', 'CHERIF',
            'Anis', 'M', DATE '2025-01-03', 'ASS-ANN-0003', 'NON_VACANCIER', 'PERMANENT', 'ASSURE_LUI_MEME',
            'c1000001-0000-0000-0000-000000000001', 'e1000001-0000-0000-0000-000000000001',
            '50000001-0000-0000-0000-000000000001', 'd0000001-0000-0000-0000-000000000001',
            'e2000001-0000-0000-0000-000000000001', 'e2000001-0000-0000-0000-000000000001',
            'c2000001-0000-0000-0000-000000000001'),
           ('b3000001-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'P-ANN-0004', 'MANSOURI',
            'Lina', 'F', DATE '2025-01-08', 'ASS-ANN-0004', 'NON_VACANCIER', 'OCCASIONNEL', 'CONJOINT',
            'c1000001-0000-0000-0000-000000000002', 'e1000001-0000-0000-0000-000000000002',
            '50000001-0000-0000-0000-000000000002', 'd0000001-0000-0000-0000-000000000002',
            'e2000001-0000-0000-0000-000000000002', 'e2000001-0000-0000-0000-000000000003',
            'c2000001-0000-0000-0000-000000000002'),
           ('b3000001-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'P-ANN-0005', 'HADDAD',
            'Yacine', 'M', DATE '2025-01-13', 'ASS-ANN-0005', 'NON_VACANCIER', 'PERMANENT', 'ASSURE_LUI_MEME',
            'c1000001-0000-0000-0000-000000000003', 'e1000001-0000-0000-0000-000000000003',
            '50000001-0000-0000-0000-000000000003', 'd0000001-0000-0000-0000-000000000003',
            'e2000001-0000-0000-0000-000000000003', 'e2000001-0000-0000-0000-000000000003',
            'c2000001-0000-0000-0000-000000000003'),
           ('b3000001-0000-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111', 'P-ANN-0006', 'ZIANI',
            'Nour', 'F', DATE '2025-01-19', 'ASS-ANN-0006', 'NON_VACANCIER', 'OCCASIONNEL', 'ENFANT',
            'c1000001-0000-0000-0000-000000000001', 'e1000001-0000-0000-0000-000000000002',
            '50000001-0000-0000-0000-000000000001', 'd0000001-0000-0000-0000-000000000004',
            'e2000001-0000-0000-0000-000000000002', 'e2000001-0000-0000-0000-000000000001',
            'c2000001-0000-0000-0000-000000000001'),
           ('b3000001-0000-0000-0000-000000000005', '11111111-1111-1111-1111-111111111111', 'P-ANN-0007', 'KERROUCHE',
            'Sofiane', 'M', DATE '2025-02-02', 'ASS-ANN-0007', 'NON_VACANCIER', 'PERMANENT', 'ASSURE_LUI_MEME',
            'c1000001-0000-0000-0000-000000000002', 'e1000001-0000-0000-0000-000000000001',
            '50000001-0000-0000-0000-000000000002', 'd0000001-0000-0000-0000-000000000001',
            'e2000001-0000-0000-0000-000000000001', 'e2000001-0000-0000-0000-000000000003',
            'c2000001-0000-0000-0000-000000000004'),
           ('b3000001-0000-0000-0000-000000000006', '11111111-1111-1111-1111-111111111111', 'P-ANN-0008', 'TOUATI',
            'Imene', 'F', DATE '2025-02-11', 'ASS-ANN-0008', 'NON_VACANCIER', 'OCCASIONNEL', 'AUTRE',
            'c1000001-0000-0000-0000-000000000003', 'e1000001-0000-0000-0000-000000000003',
            '50000001-0000-0000-0000-000000000003', 'd0000001-0000-0000-0000-000000000002',
            'e2000001-0000-0000-0000-000000000003', 'e2000001-0000-0000-0000-000000000002',
            'c2000001-0000-0000-0000-000000000003'),
           ('b3000002-0000-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'P-SL-0003', 'GUEYE',
            'Moussa', 'M', DATE '2025-01-10', 'ASS-SL-0003', 'NON_VACANCIER', 'PERMANENT', 'ASSURE_LUI_MEME',
            'c1000002-0000-0000-0000-000000000001', 'e1000002-0000-0000-0000-000000000001',
            '50000002-0000-0000-0000-000000000001', 'd0000002-0000-0000-0000-000000000001',
            'e2000002-0000-0000-0000-000000000001', 'e2000002-0000-0000-0000-000000000001',
            'c2000002-0000-0000-0000-000000000001'),
           ('b3000002-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222', 'P-SL-0004', 'BA', 'Astou',
            'F', DATE '2025-01-16', 'ASS-SL-0004', 'NON_VACANCIER', 'OCCASIONNEL', 'CONJOINT',
            'c1000002-0000-0000-0000-000000000002', 'e1000002-0000-0000-0000-000000000002',
            '50000002-0000-0000-0000-000000000002', 'd0000002-0000-0000-0000-000000000002',
            'e2000002-0000-0000-0000-000000000002', 'e2000002-0000-0000-0000-000000000002',
            'c2000002-0000-0000-0000-000000000002'),
           ('b3000002-0000-0000-0000-000000000003', '22222222-2222-2222-2222-222222222222', 'P-SL-0005', 'NDAO',
            'Cheikh', 'M', DATE '2025-02-01', 'ASS-SL-0005', 'NON_VACANCIER', 'PERMANENT', 'ASSURE_LUI_MEME',
            'c1000002-0000-0000-0000-000000000001', 'e1000002-0000-0000-0000-000000000001',
            '50000002-0000-0000-0000-000000000001', 'd0000002-0000-0000-0000-000000000002',
            'e2000002-0000-0000-0000-000000000001', 'e2000002-0000-0000-0000-000000000002',
            'c2000002-0000-0000-0000-000000000001'),
           ('b3000002-0000-0000-0000-000000000004', '22222222-2222-2222-2222-222222222222', 'P-SL-0006', 'THIAM',
            'Sokhna', 'F', DATE '2025-02-14', 'ASS-SL-0006', 'VACANCIER', 'VACANCIER_LOCAL', 'AUTRE',
            'c1000002-0000-0000-0000-000000000003', 'e1000002-0000-0000-0000-000000000002',
            '50000002-0000-0000-0000-000000000002', 'd0000002-0000-0000-0000-000000000001',
            'e2000002-0000-0000-0000-000000000002', 'e2000002-0000-0000-0000-000000000002',
            'c2000002-0000-0000-0000-000000000002'),
           ('b3000002-0000-0000-0000-000000000005', '22222222-2222-2222-2222-222222222222', 'P-SL-0007', 'SECK',
            'Ilyass', 'M', DATE '2025-03-02', 'ASS-SL-0007', 'NON_VACANCIER', 'PERMANENT', 'ASSURE_LUI_MEME',
            'c1000002-0000-0000-0000-000000000002', 'e1000002-0000-0000-0000-000000000001',
            '50000002-0000-0000-0000-000000000001', 'd0000002-0000-0000-0000-000000000001',
            'e2000002-0000-0000-0000-000000000001', 'e2000002-0000-0000-0000-000000000001',
            'c2000002-0000-0000-0000-000000000001'),
           ('b3000002-0000-0000-0000-000000000006', '22222222-2222-2222-2222-222222222222', 'P-SL-0008', 'SARR', 'Awa',
            'F', DATE '2025-03-12', 'ASS-SL-0008', 'NON_VACANCIER', 'OCCASIONNEL', 'ENFANT',
            'c1000002-0000-0000-0000-000000000001', 'e1000002-0000-0000-0000-000000000002',
            '50000002-0000-0000-0000-000000000002', 'd0000002-0000-0000-0000-000000000002',
            'e2000002-0000-0000-0000-000000000002', 'e2000002-0000-0000-0000-000000000001',
            'c2000002-0000-0000-0000-000000000002');

-- Attestations et PEC additionnelles
MERGE INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin) KEY (id)
    VALUES ('a8000001-0000-0000-0000-000000000001', 'b3000001-0000-0000-0000-000000000001',
            '11111111-1111-1111-1111-111111111111', DATE '2025-01-01', DATE '2025-09-30');
MERGE INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin) KEY (id)
    VALUES ('a8000001-0000-0000-0000-000000000002', 'b3000001-0000-0000-0000-000000000005',
            '11111111-1111-1111-1111-111111111111', DATE '2025-02-01', DATE '2025-12-31');
MERGE INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin) KEY (id)
    VALUES ('a8000002-0000-0000-0000-000000000001', 'b3000002-0000-0000-0000-000000000001',
            '22222222-2222-2222-2222-222222222222', DATE '2025-01-15', DATE '2025-11-30');
MERGE INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin) KEY (id)
    VALUES ('a8000001-0000-0000-0000-000000000003', 'b3000001-0000-0000-0000-000000000002',
            '11111111-1111-1111-1111-111111111111', DATE '2025-01-08', DATE '2025-10-31');
MERGE INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin) KEY (id)
    VALUES ('a8000001-0000-0000-0000-000000000004', 'b3000001-0000-0000-0000-000000000003',
            '11111111-1111-1111-1111-111111111111', DATE '2025-01-13', DATE '2025-12-31');
MERGE INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin) KEY (id)
    VALUES ('a8000001-0000-0000-0000-000000000005', 'b3000001-0000-0000-0000-000000000004',
            '11111111-1111-1111-1111-111111111111', DATE '2025-01-19', DATE '2025-09-30');
MERGE INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin) KEY (id)
    VALUES ('a8000001-0000-0000-0000-000000000006', 'b3000001-0000-0000-0000-000000000006',
            '11111111-1111-1111-1111-111111111111', DATE '2025-02-11', DATE '2025-12-31');
MERGE INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin) KEY (id)
    VALUES ('a8000002-0000-0000-0000-000000000002', 'b3000002-0000-0000-0000-000000000002',
            '22222222-2222-2222-2222-222222222222', DATE '2025-01-16', DATE '2025-10-31');
MERGE INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin) KEY (id)
    VALUES ('a8000002-0000-0000-0000-000000000003', 'b3000002-0000-0000-0000-000000000003',
            '22222222-2222-2222-2222-222222222222', DATE '2025-02-01', DATE '2025-11-30');
MERGE INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin) KEY (id)
    VALUES ('a8000002-0000-0000-0000-000000000004', 'b3000002-0000-0000-0000-000000000004',
            '22222222-2222-2222-2222-222222222222', DATE '2025-02-14', DATE '2025-08-31');
MERGE INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin) KEY (id)
    VALUES ('a8000002-0000-0000-0000-000000000005', 'b3000002-0000-0000-0000-000000000005',
            '22222222-2222-2222-2222-222222222222', DATE '2025-03-02', DATE '2025-12-31');
MERGE INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin) KEY (id)
    VALUES ('a8000002-0000-0000-0000-000000000006', 'b3000002-0000-0000-0000-000000000006',
            '22222222-2222-2222-2222-222222222222', DATE '2025-03-12', DATE '2025-10-31');

MERGE INTO prise_en_charge (
                            id, patient_id, center_id, date_debut_demande, date_fin_demande, forfait_demande_id,
                            date_debut_effectif, date_fin_effectif, forfait_effectif_id, statut
    ) KEY (id)
    VALUES ('b8000001-0000-0000-0000-000000000001', 'b3000001-0000-0000-0000-000000000001',
            '11111111-1111-1111-1111-111111111111',
            DATE '2025-01-05', DATE '2025-07-31', 'f0000001-0000-0000-0000-000000000001',
            DATE '2025-01-07', DATE '2025-07-31', 'f0000001-0000-0000-0000-000000000001', 'VALIDEE');
MERGE INTO prise_en_charge (
                            id, patient_id, center_id, date_debut_demande, date_fin_demande, forfait_demande_id, statut
    ) KEY (id)
    VALUES ('b8000001-0000-0000-0000-000000000002', 'b3000001-0000-0000-0000-000000000006',
            '11111111-1111-1111-1111-111111111111',
            DATE '2025-03-01', DATE '2025-10-31', 'f0000001-0000-0000-0000-000000000003', 'CREE');
MERGE INTO prise_en_charge (
                            id, patient_id, center_id, date_debut_demande, date_fin_demande, forfait_demande_id,
                            date_debut_effectif, date_fin_effectif, forfait_effectif_id, statut
    ) KEY (id)
    VALUES ('b8000002-0000-0000-0000-000000000001', 'b3000002-0000-0000-0000-000000000001',
            '22222222-2222-2222-2222-222222222222',
            DATE '2025-02-01', DATE '2025-09-30', 'f0000002-0000-0000-0000-000000000001',
            DATE '2025-02-02', DATE '2025-09-30', 'f0000002-0000-0000-0000-000000000001', 'VALIDEE');

-- ============================================================================
-- PERF DATASET (multi-centres, 5 ans): patients, seances, factures, stock
-- ============================================================================

MERGE INTO facturation_settings (center_id, tva_rate, code_format, regroupement_multi_forfait, updated_at, updated_by)
    KEY (center_id)
    VALUES ('11111111-1111-1111-1111-111111111111', 19.00, 'FACT-{YYYY}-{SEQ6}', TRUE, CURRENT_TIMESTAMP, 'seed-perf');

MERGE INTO facturation_settings (center_id, tva_rate, code_format, regroupement_multi_forfait, updated_at, updated_by)
    KEY (center_id)
    VALUES ('22222222-2222-2222-2222-222222222222', 18.00, 'FACT-{YYYY}-{SEQ6}', TRUE, CURRENT_TIMESTAMP, 'seed-perf');

-- 80 patients synthetiques par centre.
INSERT INTO patients (id, center_id, code_patient, civilite, nom, prenom, sexe,
                      date_admission, date_naissance, lieu_naissance, situation_familiale,
                      profession1, adresse, tel_mobile, email, numero_assurance,
                      type_patient, etat_patient, qualite_assure,
                      sous_kt, epo_enabled, fer_enabled,
                      centre_payeur_id, medecin_traitant_id, salle_id, position_id,
                      transporteur_aller_id, transporteur_retour_id, categorie_transport_id,
                      jour_lundi, jour_mardi, jour_mercredi, jour_jeudi, jour_vendredi, jour_samedi)
SELECT CAST('7100' || LPAD(CAST(v AS VARCHAR), 4, '0') || '-1000-0000-0000-' ||
            LPAD(CAST(v AS VARCHAR), 12, '0') AS UUID),
       '11111111-1111-1111-1111-111111111111',
       'P-ANN-PF-' || LPAD(CAST(v AS VARCHAR), 4, '0'),
       CASE WHEN MOD(v, 2) = 0 THEN 'Mme' ELSE 'M' END,
       'NOMANN' || LPAD(CAST(v AS VARCHAR), 4, '0'),
       'PrenomAnn' || LPAD(CAST(v AS VARCHAR), 4, '0'),
       CASE WHEN MOD(v, 2) = 0 THEN 'F' ELSE 'M' END,
       DATEADD('DAY', MOD(v * 11, 1100), DATE '2021-01-01'),
       DATEADD('DAY', (v * 95), DATE '1960-01-01'),
       'Annaba',
       CASE WHEN MOD(v, 3) = 0 THEN 'Célibataire' ELSE 'Marié' END,
       'Profession' || MOD(v, 12),
       'Quartier Annaba ' || v,
       '0550' || LPAD(CAST(v AS VARCHAR), 6, '0'),
       'perf.ann.' || LPAD(CAST(v AS VARCHAR), 4, '0') || '@hemodialyse.dz',
       'ASS-PERF-ANN-' || LPAD(CAST(v AS VARCHAR), 4, '0'),
       CASE WHEN MOD(v, 9) = 0 THEN 'VACANCIER' ELSE 'NON_VACANCIER' END,
       CASE WHEN MOD(v, 5) = 0 THEN 'OCCASIONNEL' ELSE 'PERMANENT' END,
       CASE WHEN MOD(v, 4) = 0 THEN 'CONJOINT' ELSE 'ASSURE_LUI_MEME' END,
       MOD(v, 2) = 0,
       MOD(v, 3) = 0,
       MOD(v, 4) = 0,
       CASE
           WHEN MOD(v, 3) = 0 THEN 'c1000001-0000-0000-0000-000000000002'
           WHEN MOD(v, 3) = 1 THEN 'c1000001-0000-0000-0000-000000000001'
           ELSE 'c1000001-0000-0000-0000-000000000003' END,
       CASE
           WHEN MOD(v, 3) = 0 THEN 'e1000001-0000-0000-0000-000000000003'
           WHEN MOD(v, 3) = 1 THEN 'e1000001-0000-0000-0000-000000000001'
           ELSE 'e1000001-0000-0000-0000-000000000002' END,
       CASE
           WHEN MOD(v, 3) = 0 THEN '50000001-0000-0000-0000-000000000003'
           WHEN MOD(v, 3) = 1 THEN '50000001-0000-0000-0000-000000000001'
           ELSE '50000001-0000-0000-0000-000000000002' END,
       CASE
           WHEN MOD(v, 4) = 0 THEN 'd0000001-0000-0000-0000-000000000004'
           WHEN MOD(v, 4) = 1 THEN 'd0000001-0000-0000-0000-000000000001'
           WHEN MOD(v, 4) = 2 THEN 'd0000001-0000-0000-0000-000000000002'
           ELSE 'd0000001-0000-0000-0000-000000000003' END,
       CASE
           WHEN MOD(v, 3) = 0 THEN 'e2000001-0000-0000-0000-000000000003'
           WHEN MOD(v, 3) = 1 THEN 'e2000001-0000-0000-0000-000000000001'
           ELSE 'e2000001-0000-0000-0000-000000000002' END,
       CASE
           WHEN MOD(v, 3) = 0 THEN 'e2000001-0000-0000-0000-000000000002'
           WHEN MOD(v, 3) = 1 THEN 'e2000001-0000-0000-0000-000000000001'
           ELSE 'e2000001-0000-0000-0000-000000000003' END,
       CASE
           WHEN MOD(v, 4) = 0 THEN 'c2000001-0000-0000-0000-000000000004'
           WHEN MOD(v, 4) = 1 THEN 'c2000001-0000-0000-0000-000000000001'
           WHEN MOD(v, 4) = 2 THEN 'c2000001-0000-0000-0000-000000000002'
           ELSE 'c2000001-0000-0000-0000-000000000003' END,
       MOD(v, 2) = 0,
       MOD(v, 2) = 1,
       MOD(v, 3) = 0,
       MOD(v, 3) <> 0,
       MOD(v, 4) <> 0,
       MOD(v, 5) = 0
FROM SYSTEM_RANGE(1, 80) n(v)
WHERE NOT EXISTS (SELECT 1
                  FROM patients p
                  WHERE p.id = CAST('7100' || LPAD(CAST(v AS VARCHAR), 4, '0') || '-1000-0000-0000-' ||
                                    LPAD(CAST(v AS VARCHAR), 12, '0') AS UUID));

INSERT INTO patients (id, center_id, code_patient, civilite, nom, prenom, sexe,
                      date_admission, date_naissance, lieu_naissance, situation_familiale,
                      profession1, adresse, tel_mobile, email, numero_assurance,
                      type_patient, etat_patient, qualite_assure,
                      sous_kt, epo_enabled, fer_enabled,
                      centre_payeur_id, medecin_traitant_id, salle_id, position_id,
                      transporteur_aller_id, transporteur_retour_id, categorie_transport_id,
                      jour_lundi, jour_mardi, jour_mercredi, jour_jeudi, jour_vendredi, jour_samedi)
SELECT CAST('7200' || LPAD(CAST(v AS VARCHAR), 4, '0') || '-2000-0000-0000-' ||
            LPAD(CAST(v AS VARCHAR), 12, '0') AS UUID),
       '22222222-2222-2222-2222-222222222222',
       'P-SL-PF-' || LPAD(CAST(v AS VARCHAR), 4, '0'),
       CASE WHEN MOD(v, 2) = 0 THEN 'Mme' ELSE 'M' END,
       'NOMSL' || LPAD(CAST(v AS VARCHAR), 4, '0'),
       'PrenomSl' || LPAD(CAST(v AS VARCHAR), 4, '0'),
       CASE WHEN MOD(v, 2) = 0 THEN 'F' ELSE 'M' END,
       DATEADD('DAY', MOD(v * 13, 1100), DATE '2021-02-01'),
       DATEADD('DAY', (v * 90), DATE '1962-01-01'),
       'Saint-Louis',
       CASE WHEN MOD(v, 3) = 0 THEN 'Célibataire' ELSE 'Marié' END,
       'Profession' || MOD(v, 10),
       'Quartier Saint-Louis ' || v,
       '0777' || LPAD(CAST(v AS VARCHAR), 6, '0'),
       'perf.sl.' || LPAD(CAST(v AS VARCHAR), 4, '0') || '@hemodialyse.dz',
       'ASS-PERF-SL-' || LPAD(CAST(v AS VARCHAR), 4, '0'),
       CASE WHEN MOD(v, 8) = 0 THEN 'VACANCIER' ELSE 'NON_VACANCIER' END,
       CASE WHEN MOD(v, 5) = 0 THEN 'OCCASIONNEL' ELSE 'PERMANENT' END,
       CASE WHEN MOD(v, 4) = 0 THEN 'ENFANT' ELSE 'ASSURE_LUI_MEME' END,
       MOD(v, 2) = 0,
       MOD(v, 3) = 0,
       MOD(v, 4) = 0,
       CASE
           WHEN MOD(v, 3) = 0 THEN 'c1000002-0000-0000-0000-000000000003'
           WHEN MOD(v, 3) = 1 THEN 'c1000002-0000-0000-0000-000000000001'
           ELSE 'c1000002-0000-0000-0000-000000000002' END,
       CASE
           WHEN MOD(v, 2) = 0 THEN 'e1000002-0000-0000-0000-000000000002'
           ELSE 'e1000002-0000-0000-0000-000000000001' END,
       CASE
           WHEN MOD(v, 2) = 0 THEN '50000002-0000-0000-0000-000000000002'
           ELSE '50000002-0000-0000-0000-000000000001' END,
       CASE
           WHEN MOD(v, 2) = 0 THEN 'd0000002-0000-0000-0000-000000000002'
           ELSE 'd0000002-0000-0000-0000-000000000001' END,
       CASE
           WHEN MOD(v, 2) = 0 THEN 'e2000002-0000-0000-0000-000000000002'
           ELSE 'e2000002-0000-0000-0000-000000000001' END,
       CASE
           WHEN MOD(v, 2) = 0 THEN 'e2000002-0000-0000-0000-000000000001'
           ELSE 'e2000002-0000-0000-0000-000000000002' END,
       CASE
           WHEN MOD(v, 2) = 0 THEN 'c2000002-0000-0000-0000-000000000002'
           ELSE 'c2000002-0000-0000-0000-000000000001' END,
       MOD(v, 2) = 0,
       MOD(v, 2) = 1,
       MOD(v, 3) = 0,
       MOD(v, 3) <> 0,
       MOD(v, 4) <> 0,
       MOD(v, 5) = 0
FROM SYSTEM_RANGE(1, 80) n(v)
WHERE NOT EXISTS (SELECT 1
                  FROM patients p
                  WHERE p.id = CAST('7200' || LPAD(CAST(v AS VARCHAR), 4, '0') || '-2000-0000-0000-' ||
                                    LPAD(CAST(v AS VARCHAR), 12, '0') AS UUID));

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

-- Articles stock: 18 par centre.
INSERT INTO articles (id, center_id, code, libelle, unite, stock_quantity, seuil_alerte, pmp_courant, gere_par_lot,
                      active, created_at)
SELECT CAST('3100' || LPAD(CAST(v AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST(v AS VARCHAR), 12, '0') AS UUID),
       '11111111-1111-1111-1111-111111111111',
       'ART-ANN-' || LPAD(CAST(v AS VARCHAR), 3, '0'),
       'Article Annaba ' || v,
       CASE WHEN MOD(v, 3) = 0 THEN 'L' WHEN MOD(v, 3) = 1 THEN 'UNITE' ELSE 'ML' END,
       0,
       80 + (v * 5),
       0,
       TRUE,
       TRUE,
       CURRENT_TIMESTAMP
FROM SYSTEM_RANGE(1, 18) n(v)
WHERE NOT EXISTS (SELECT 1
                  FROM articles a
                  WHERE a.id = CAST('3100' || LPAD(CAST(v AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
                                    LPAD(CAST(v AS VARCHAR), 12, '0') AS UUID));

INSERT INTO articles (id, center_id, code, libelle, unite, stock_quantity, seuil_alerte, pmp_courant, gere_par_lot,
                      active, created_at)
SELECT CAST('3200' || LPAD(CAST(v AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST(v AS VARCHAR), 12, '0') AS UUID),
       '22222222-2222-2222-2222-222222222222',
       'ART-SL-' || LPAD(CAST(v AS VARCHAR), 3, '0'),
       'Article Saint-Louis ' || v,
       CASE WHEN MOD(v, 3) = 0 THEN 'L' WHEN MOD(v, 3) = 1 THEN 'UNITE' ELSE 'ML' END,
       0,
       90 + (v * 5),
       0,
       TRUE,
       TRUE,
       CURRENT_TIMESTAMP
FROM SYSTEM_RANGE(1, 18) n(v)
WHERE NOT EXISTS (SELECT 1
                  FROM articles a
                  WHERE a.id = CAST('3200' || LPAD(CAST(v AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
                                    LPAD(CAST(v AS VARCHAR), 12, '0') AS UUID));

-- Receptions mensuelles sur 5 ans et lignes associees.
INSERT INTO bons_reception (id, center_id, reference, bon_commande_id, fournisseur_id, date_reception, statut,
                            created_by, created_at)
SELECT CAST('4100' || LPAD(CAST(v AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST(v AS VARCHAR), 12, '0') AS UUID),
       '11111111-1111-1111-1111-111111111111',
       'BR-ANN-PF-' || LPAD(CAST(v AS VARCHAR), 4, '0'),
       NULL,
       NULL,
       DATEADD('DAY', ((v - 1) * 30), DATE '2022-01-05'),
       'RECU',
       'seed-perf',
       CURRENT_TIMESTAMP
FROM SYSTEM_RANGE(1, 60) r(v)
WHERE NOT EXISTS (SELECT 1
                  FROM bons_reception br
                  WHERE br.id = CAST('4100' || LPAD(CAST(v AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
                                     LPAD(CAST(v AS VARCHAR), 12, '0') AS UUID));

INSERT INTO bons_reception (id, center_id, reference, bon_commande_id, fournisseur_id, date_reception, statut,
                            created_by, created_at)
SELECT CAST('4200' || LPAD(CAST(v AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST(v AS VARCHAR), 12, '0') AS UUID),
       '22222222-2222-2222-2222-222222222222',
       'BR-SL-PF-' || LPAD(CAST(v AS VARCHAR), 4, '0'),
       NULL,
       NULL,
       DATEADD('DAY', ((v - 1) * 30), DATE '2022-01-07'),
       'RECU',
       'seed-perf',
       CURRENT_TIMESTAMP
FROM SYSTEM_RANGE(1, 60) r(v)
WHERE NOT EXISTS (SELECT 1
                  FROM bons_reception br
                  WHERE br.id = CAST('4200' || LPAD(CAST(v AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
                                     LPAD(CAST(v AS VARCHAR), 12, '0') AS UUID));

INSERT INTO bons_reception_lignes (id, bon_reception_id, article_id, quantite, prix_unitaire, numero_lot,
                                   date_peremption, emplacement_id, lot_id)
SELECT CAST('5100' || LPAD(CAST((r.v * 10 + l.v) AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST((r.v * 10 + l.v) AS VARCHAR), 12, '0') AS UUID),
       CAST('4100' || LPAD(CAST(r.v AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST(r.v AS VARCHAR), 12, '0') AS UUID),
       CAST('3100' || LPAD(CAST((((r.v + l.v) % 18) + 1) AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST((((r.v + l.v) % 18) + 1) AS VARCHAR), 12, '0') AS UUID),
       120 + (l.v * 15),
       350 + MOD(r.v, 20) + (l.v * 4),
       'LOT-ANN-' || LPAD(CAST(r.v AS VARCHAR), 3, '0') || '-' || l.v,
       DATEADD('DAY', (r.v * 20), DATE '2026-12-31'),
       NULL,
       CAST('6100' || LPAD(CAST((r.v * 10 + l.v) AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST((r.v * 10 + l.v) AS VARCHAR), 12, '0') AS UUID)
FROM SYSTEM_RANGE(1, 60) r(v)
         CROSS JOIN SYSTEM_RANGE(1, 3) l(v)
WHERE NOT EXISTS (SELECT 1
                  FROM bons_reception_lignes brl
                  WHERE brl.id = CAST('5100' || LPAD(CAST((r.v * 10 + l.v) AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
                                      LPAD(CAST((r.v * 10 + l.v) AS VARCHAR), 12, '0') AS UUID));

INSERT INTO bons_reception_lignes (id, bon_reception_id, article_id, quantite, prix_unitaire, numero_lot,
                                   date_peremption, emplacement_id, lot_id)
SELECT CAST('5200' || LPAD(CAST((r.v * 10 + l.v) AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST((r.v * 10 + l.v) AS VARCHAR), 12, '0') AS UUID),
       CAST('4200' || LPAD(CAST(r.v AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST(r.v AS VARCHAR), 12, '0') AS UUID),
       CAST('3200' || LPAD(CAST((((r.v + l.v) % 18) + 1) AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST((((r.v + l.v) % 18) + 1) AS VARCHAR), 12, '0') AS UUID),
       140 + (l.v * 12),
       360 + MOD(r.v, 25) + (l.v * 5),
       'LOT-SL-' || LPAD(CAST(r.v AS VARCHAR), 3, '0') || '-' || l.v,
       DATEADD('DAY', (r.v * 21), DATE '2026-12-31'),
       NULL,
       CAST('6200' || LPAD(CAST((r.v * 10 + l.v) AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST((r.v * 10 + l.v) AS VARCHAR), 12, '0') AS UUID)
FROM SYSTEM_RANGE(1, 60) r(v)
         CROSS JOIN SYSTEM_RANGE(1, 3) l(v)
WHERE NOT EXISTS (SELECT 1
                  FROM bons_reception_lignes brl
                  WHERE brl.id = CAST('5200' || LPAD(CAST((r.v * 10 + l.v) AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
                                      LPAD(CAST((r.v * 10 + l.v) AS VARCHAR), 12, '0') AS UUID));

INSERT INTO lots (id, center_id, article_id, bon_reception_id, emplacement_id, numero_lot, date_peremption,
                  quantite_initiale, quantite_restante, pmp, created_at)
SELECT brl.lot_id,
       br.center_id,
       brl.article_id,
       br.id,
       NULL,
       brl.numero_lot,
       brl.date_peremption,
       brl.quantite,
       brl.quantite,
       brl.prix_unitaire,
       CURRENT_TIMESTAMP
FROM bons_reception_lignes brl
         JOIN bons_reception br ON br.id = brl.bon_reception_id
WHERE (br.reference LIKE 'BR-ANN-PF-%' OR br.reference LIKE 'BR-SL-PF-%')
  AND NOT EXISTS (SELECT 1 FROM lots l WHERE l.id = brl.lot_id);

INSERT INTO stock_movements (id, center_id, article_id, seance_id, lot_id, mouvement_type, quantite, prix_unitaire,
                             pmp_apres, created_by, created_at)
SELECT CAST('7100' || SUBSTRING(CAST(brl.id AS VARCHAR), 1, 4) || '-0000-0000-0000-' ||
            SUBSTRING(CAST(brl.id AS VARCHAR), 25, 12) AS UUID),
       br.center_id,
       brl.article_id,
       NULL,
       brl.lot_id,
       'ENTREE',
       brl.quantite,
       brl.prix_unitaire,
       brl.prix_unitaire,
       'seed-perf',
       CURRENT_TIMESTAMP
FROM bons_reception_lignes brl
         JOIN bons_reception br ON br.id = brl.bon_reception_id
WHERE (br.reference LIKE 'BR-ANN-PF-%' OR br.reference LIKE 'BR-SL-PF-%')
  AND NOT EXISTS (SELECT 1
                  FROM stock_movements sm
                  WHERE sm.id = CAST('7100' || SUBSTRING(CAST(brl.id AS VARCHAR), 1, 4) || '-0000-0000-0000-' ||
                                     SUBSTRING(CAST(brl.id AS VARCHAR), 25, 12) AS UUID));

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

INSERT INTO bons_sortie_lignes (id, bon_sortie_id, article_id, lot_id, quantite, pmp_applique)
SELECT CAST('9101' || LPAD(CAST(z.rn AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST(z.rn AS VARCHAR), 12, '0') AS UUID),
       z.id,
       CAST('3100' || LPAD(CAST(MOD(z.rn, 18) + 1 AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST(MOD(z.rn, 18) + 1 AS VARCHAR), 12, '0') AS UUID),
       CAST('6100' || LPAD(CAST(MOD(z.rn, 180) + 1 AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST(MOD(z.rn, 180) + 1 AS VARCHAR), 12, '0') AS UUID),
       2 + MOD(z.rn, 4),
       380 + MOD(z.rn, 30)
FROM (SELECT bs.id, ROW_NUMBER() OVER (ORDER BY bs.date_sortie, bs.id) AS rn
      FROM bons_sortie bs
      WHERE bs.reference LIKE 'BS-ANN-PF-%') z
WHERE NOT EXISTS (SELECT 1
                  FROM bons_sortie_lignes l
                  WHERE l.id = CAST('9101' || LPAD(CAST(z.rn AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
                                    LPAD(CAST(z.rn AS VARCHAR), 12, '0') AS UUID));

INSERT INTO bons_sortie_lignes (id, bon_sortie_id, article_id, lot_id, quantite, pmp_applique)
SELECT CAST('9201' || LPAD(CAST(z.rn AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST(z.rn AS VARCHAR), 12, '0') AS UUID),
       z.id,
       CAST('3200' || LPAD(CAST(MOD(z.rn, 18) + 1 AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST(MOD(z.rn, 18) + 1 AS VARCHAR), 12, '0') AS UUID),
       CAST('6200' || LPAD(CAST(MOD(z.rn, 180) + 1 AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST(MOD(z.rn, 180) + 1 AS VARCHAR), 12, '0') AS UUID),
       2 + MOD(z.rn, 5),
       390 + MOD(z.rn, 35)
FROM (SELECT bs.id, ROW_NUMBER() OVER (ORDER BY bs.date_sortie, bs.id) AS rn
      FROM bons_sortie bs
      WHERE bs.reference LIKE 'BS-SL-PF-%') z
WHERE NOT EXISTS (SELECT 1
                  FROM bons_sortie_lignes l
                  WHERE l.id = CAST('9201' || LPAD(CAST(z.rn AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
                                    LPAD(CAST(z.rn AS VARCHAR), 12, '0') AS UUID));

INSERT INTO stock_movements (id, center_id, article_id, seance_id, lot_id, mouvement_type, quantite, prix_unitaire,
                             pmp_apres, created_by, created_at)
SELECT CAST('a101' || LPAD(CAST(z.rn AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
            LPAD(CAST(z.rn AS VARCHAR), 12, '0') AS UUID),
       bs.center_id,
       z.article_id,
       bs.seance_id,
       z.lot_id,
       'SORTIE',
       z.quantite,
       z.pmp_applique,
       z.pmp_applique,
       'seed-perf',
       CURRENT_TIMESTAMP
FROM (SELECT l.id,
             l.bon_sortie_id,
             l.article_id,
             l.lot_id,
             l.quantite,
             l.pmp_applique,
             ROW_NUMBER() OVER (ORDER BY l.id) AS rn
      FROM bons_sortie_lignes l
      WHERE l.id IN (
          SELECT id FROM bons_sortie_lignes WHERE CAST(id AS VARCHAR) LIKE '9101%' OR CAST(id AS VARCHAR) LIKE '9201%'
          )) z
         JOIN bons_sortie bs ON bs.id = z.bon_sortie_id
WHERE NOT EXISTS (SELECT 1
                  FROM stock_movements sm
                  WHERE sm.id = CAST('a101' || LPAD(CAST(z.rn AS VARCHAR), 4, '0') || '-0000-0000-0000-' ||
                                     LPAD(CAST(z.rn AS VARCHAR), 12, '0') AS UUID));

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

-- Recalcule un etat de stock coherent (entrees - sorties) sur 5 ans.
UPDATE lots l
SET quantite_restante = l.quantite_initiale
    - COALESCE((
                   SELECT SUM(m.quantite)
                   FROM stock_movements m
                   WHERE m.lot_id = l.id
                     AND m.mouvement_type = 'SORTIE'
                   ), 0)
WHERE l.numero_lot LIKE 'LOT-ANN-%'
   OR l.numero_lot LIKE 'LOT-SL-%';

UPDATE articles a
SET stock_quantity = COALESCE((
                                  SELECT SUM(CASE WHEN m.mouvement_type = 'ENTREE' THEN m.quantite ELSE -m.quantite END)
                                  FROM stock_movements m
                                  WHERE m.article_id = a.id
                                  ), 0),
    pmp_courant    = (
        SELECT AVG(m.prix_unitaire)
        FROM stock_movements m
        WHERE m.article_id = a.id
          AND m.mouvement_type = 'ENTREE'
        )
WHERE a.code LIKE 'ART-ANN-%'
   OR a.code LIKE 'ART-SL-%';

-- Synchronise les compteurs sequence pour eviter collisions de references.
MERGE INTO app_settings (center_id, cle, prefixe, dernier_compteur)
    KEY (center_id, cle)
    VALUES ('11111111-1111-1111-1111-111111111111', 'SEQ_BR', 'BR-', 6000);
MERGE INTO app_settings (center_id, cle, prefixe, dernier_compteur)
    KEY (center_id, cle)
    VALUES ('11111111-1111-1111-1111-111111111111', 'SEQ_BS', 'BS-', 6000);
MERGE INTO app_settings (center_id, cle, prefixe, dernier_compteur)
    KEY (center_id, cle)
    VALUES ('22222222-2222-2222-2222-222222222222', 'SEQ_BR', 'BR-', 6000);
MERGE INTO app_settings (center_id, cle, prefixe, dernier_compteur)
    KEY (center_id, cle)
    VALUES ('22222222-2222-2222-2222-222222222222', 'SEQ_BS', 'BS-', 6000);




