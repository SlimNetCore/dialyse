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




