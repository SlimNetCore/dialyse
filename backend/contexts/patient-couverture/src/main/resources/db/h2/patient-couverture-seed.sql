-- Amorçage H2 (poste de développement et tests) des données dont le Patient-Couverture est propriétaire (SEED2, ADR 0188) : caisses,
-- agences, centres payeurs, transporteurs, catégories de transport, forfaits, patients, assurés, attestations de droit, prises en charge.
-- Extrait, bloc à bloc, dans le même ordre relatif et sans changement de contenu, de db/seed.sql. Les INSERT ... SELECT RANDOM_UUID() de
-- fin de script lisent les patients déjà présents : l'ordre relatif de ce fichier est donc à conserver.
-- ORDRE : chargé APRÈS l'Organisation (centres, salles, médecins, créneaux, par valeur) et AVANT db/seed.sql (séances, qui lisent patients).

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

-- ═══ DONNÉES COMPLÈTES : DATE_NAISSANCE DES PATIENTS SUPPLÉMENTAIRES ═══

UPDATE patients
SET date_naissance = DATE '1972-05-15'
WHERE id = 'b3000001-0000-0000-0000-000000000001';
UPDATE patients
SET date_naissance = DATE '1985-09-22'
WHERE id = 'b3000001-0000-0000-0000-000000000002';
UPDATE patients
SET date_naissance = DATE '1968-03-07'
WHERE id = 'b3000001-0000-0000-0000-000000000003';
UPDATE patients
SET date_naissance = DATE '2000-11-18'
WHERE id = 'b3000001-0000-0000-0000-000000000004';
UPDATE patients
SET date_naissance = DATE '1975-07-30'
WHERE id = 'b3000001-0000-0000-0000-000000000005';
UPDATE patients
SET date_naissance = DATE '1991-01-14'
WHERE id = 'b3000001-0000-0000-0000-000000000006';
UPDATE patients
SET date_naissance = DATE '1970-04-20'
WHERE id = 'b3000002-0000-0000-0000-000000000001';
UPDATE patients
SET date_naissance = DATE '1983-08-05'
WHERE id = 'b3000002-0000-0000-0000-000000000002';
UPDATE patients
SET date_naissance = DATE '1966-12-01'
WHERE id = 'b3000002-0000-0000-0000-000000000003';
UPDATE patients
SET date_naissance = DATE '1979-06-17'
WHERE id = 'b3000002-0000-0000-0000-000000000004';
UPDATE patients
SET date_naissance = DATE '1988-10-25'
WHERE id = 'b3000002-0000-0000-0000-000000000005';
UPDATE patients
SET date_naissance = DATE '1995-03-09'
WHERE id = 'b3000002-0000-0000-0000-000000000006';

-- ═══ ATTESTATIONS MANQUANTES 2025 ═══

-- P-SL-0002 : NDIAYE Aminata (pas encore d'attestation)
MERGE INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin) KEY (id)
    VALUES ('a7000002-0000-0000-0000-000000000002', 'b2000001-0000-0000-0000-000000000002',
            '22222222-2222-2222-2222-222222222222', DATE '2025-03-01', DATE '2025-12-31');

-- ═══ ATTESTATIONS 2026 (renouvellement annuel) ═══

-- Centre 1 — ANNABA
MERGE INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin) KEY (id)
    VALUES ('a9000001-0000-0000-0000-000000000001', 'b1000001-0000-0000-0000-000000000001',
            '11111111-1111-1111-1111-111111111111', DATE '2026-01-01', DATE '2026-12-31');
MERGE INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin) KEY (id)
    VALUES ('a9000001-0000-0000-0000-000000000002', 'b1000001-0000-0000-0000-000000000002',
            '11111111-1111-1111-1111-111111111111', DATE '2026-01-01', DATE '2026-12-31');
MERGE INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin) KEY (id)
    VALUES ('a9000001-0000-0000-0000-000000000003', 'b3000001-0000-0000-0000-000000000001',
            '11111111-1111-1111-1111-111111111111', DATE '2026-01-01', DATE '2026-12-31');
MERGE INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin) KEY (id)
    VALUES ('a9000001-0000-0000-0000-000000000004', 'b3000001-0000-0000-0000-000000000002',
            '11111111-1111-1111-1111-111111111111', DATE '2026-01-01', DATE '2026-12-31');
MERGE INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin) KEY (id)
    VALUES ('a9000001-0000-0000-0000-000000000005', 'b3000001-0000-0000-0000-000000000003',
            '11111111-1111-1111-1111-111111111111', DATE '2026-01-01', DATE '2026-12-31');
MERGE INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin) KEY (id)
    VALUES ('a9000001-0000-0000-0000-000000000006', 'b3000001-0000-0000-0000-000000000004',
            '11111111-1111-1111-1111-111111111111', DATE '2026-01-01', DATE '2026-09-30');
MERGE INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin) KEY (id)
    VALUES ('a9000001-0000-0000-0000-000000000007', 'b3000001-0000-0000-0000-000000000005',
            '11111111-1111-1111-1111-111111111111', DATE '2026-01-01', DATE '2026-12-31');
MERGE INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin) KEY (id)
    VALUES ('a9000001-0000-0000-0000-000000000008', 'b3000001-0000-0000-0000-000000000006',
            '11111111-1111-1111-1111-111111111111', DATE '2026-02-01', DATE '2026-12-31');

-- Centre 2 — ROUIBA
MERGE INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin) KEY (id)
    VALUES ('a9000002-0000-0000-0000-000000000001', 'b2000001-0000-0000-0000-000000000001',
            '22222222-2222-2222-2222-222222222222', DATE '2026-01-01', DATE '2026-12-31');
MERGE INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin) KEY (id)
    VALUES ('a9000002-0000-0000-0000-000000000002', 'b2000001-0000-0000-0000-000000000002',
            '22222222-2222-2222-2222-222222222222', DATE '2026-01-01', DATE '2026-12-31');
MERGE INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin) KEY (id)
    VALUES ('a9000002-0000-0000-0000-000000000003', 'b3000002-0000-0000-0000-000000000001',
            '22222222-2222-2222-2222-222222222222', DATE '2026-01-01', DATE '2026-12-31');
MERGE INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin) KEY (id)
    VALUES ('a9000002-0000-0000-0000-000000000004', 'b3000002-0000-0000-0000-000000000002',
            '22222222-2222-2222-2222-222222222222', DATE '2026-01-01', DATE '2026-12-31');
MERGE INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin) KEY (id)
    VALUES ('a9000002-0000-0000-0000-000000000005', 'b3000002-0000-0000-0000-000000000003',
            '22222222-2222-2222-2222-222222222222', DATE '2026-01-01', DATE '2026-12-31');
MERGE INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin) KEY (id)
    VALUES ('a9000002-0000-0000-0000-000000000006', 'b3000002-0000-0000-0000-000000000004',
            '22222222-2222-2222-2222-222222222222', DATE '2026-02-01', DATE '2026-08-31');
MERGE INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin) KEY (id)
    VALUES ('a9000002-0000-0000-0000-000000000007', 'b3000002-0000-0000-0000-000000000005',
            '22222222-2222-2222-2222-222222222222', DATE '2026-01-01', DATE '2026-12-31');
MERGE INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin) KEY (id)
    VALUES ('a9000002-0000-0000-0000-000000000008', 'b3000002-0000-0000-0000-000000000006',
            '22222222-2222-2222-2222-222222222222', DATE '2026-03-01', DATE '2026-12-31');

-- ═══ PEC MANQUANTES ═══

-- P-SL-0002 : NDIAYE Aminata (VACANCIER_ETRANGER) — en attente
MERGE INTO prise_en_charge (
                            id, patient_id, center_id,
                            date_debut_demande, date_fin_demande, forfait_demande_id,
                            statut
    ) KEY (id)
    VALUES ('b7000002-0000-0000-0000-000000000002', 'b2000001-0000-0000-0000-000000000002',
            '22222222-2222-2222-2222-222222222222',
            DATE '2025-03-01', DATE '2025-12-31', 'f0000002-0000-0000-0000-000000000002',
            'CREE');

-- P-ANN-0004 : MANSOURI Lina — validée
MERGE INTO prise_en_charge (
                            id, patient_id, center_id,
                            date_debut_demande, date_fin_demande, forfait_demande_id,
                            date_debut_effectif, date_fin_effectif, forfait_effectif_id,
                            statut
    ) KEY (id)
    VALUES ('b8000001-0000-0000-0000-000000000003', 'b3000001-0000-0000-0000-000000000002',
            '11111111-1111-1111-1111-111111111111',
            DATE '2025-01-10', DATE '2025-10-31', 'f0000001-0000-0000-0000-000000000002',
            DATE '2025-01-12', DATE '2025-10-31', 'f0000001-0000-0000-0000-000000000002',
            'VALIDEE');

-- P-ANN-0005 : HADDAD Yacine — validée
MERGE INTO prise_en_charge (
                            id, patient_id, center_id,
                            date_debut_demande, date_fin_demande, forfait_demande_id,
                            date_debut_effectif, date_fin_effectif, forfait_effectif_id,
                            statut
    ) KEY (id)
    VALUES ('b8000001-0000-0000-0000-000000000004', 'b3000001-0000-0000-0000-000000000003',
            '11111111-1111-1111-1111-111111111111',
            DATE '2025-01-15', DATE '2025-12-31', 'f0000001-0000-0000-0000-000000000001',
            DATE '2025-01-18', DATE '2025-12-31', 'f0000001-0000-0000-0000-000000000001',
            'VALIDEE');

-- P-ANN-0006 : ZIANI Nour — en cours de création
MERGE INTO prise_en_charge (
                            id, patient_id, center_id,
                            date_debut_demande, date_fin_demande, forfait_demande_id,
                            statut
    ) KEY (id)
    VALUES ('b8000001-0000-0000-0000-000000000005', 'b3000001-0000-0000-0000-000000000004',
            '11111111-1111-1111-1111-111111111111',
            DATE '2025-01-20', DATE '2025-09-30', 'f0000001-0000-0000-0000-000000000003',
            'CREE');

-- P-ANN-0007 : KERROUCHE Sofiane — validée
MERGE INTO prise_en_charge (
                            id, patient_id, center_id,
                            date_debut_demande, date_fin_demande, forfait_demande_id,
                            date_debut_effectif, date_fin_effectif, forfait_effectif_id,
                            statut
    ) KEY (id)
    VALUES ('b8000001-0000-0000-0000-000000000006', 'b3000001-0000-0000-0000-000000000005',
            '11111111-1111-1111-1111-111111111111',
            DATE '2025-02-05', DATE '2025-12-31', 'f0000001-0000-0000-0000-000000000002',
            DATE '2025-02-08', DATE '2025-12-31', 'f0000001-0000-0000-0000-000000000002',
            'VALIDEE');

-- P-SL-0004 : BA Astou — validée
MERGE INTO prise_en_charge (
                            id, patient_id, center_id,
                            date_debut_demande, date_fin_demande, forfait_demande_id,
                            date_debut_effectif, date_fin_effectif, forfait_effectif_id,
                            statut
    ) KEY (id)
    VALUES ('b8000002-0000-0000-0000-000000000002', 'b3000002-0000-0000-0000-000000000002',
            '22222222-2222-2222-2222-222222222222',
            DATE '2025-01-18', DATE '2025-10-31', 'f0000002-0000-0000-0000-000000000001',
            DATE '2025-01-20', DATE '2025-10-31', 'f0000002-0000-0000-0000-000000000001',
            'VALIDEE');

-- P-SL-0005 : NDAO Cheikh — validée
MERGE INTO prise_en_charge (
                            id, patient_id, center_id,
                            date_debut_demande, date_fin_demande, forfait_demande_id,
                            date_debut_effectif, date_fin_effectif, forfait_effectif_id,
                            statut
    ) KEY (id)
    VALUES ('b8000002-0000-0000-0000-000000000003', 'b3000002-0000-0000-0000-000000000003',
            '22222222-2222-2222-2222-222222222222',
            DATE '2025-02-03', DATE '2025-11-30', 'f0000002-0000-0000-0000-000000000002',
            DATE '2025-02-05', DATE '2025-11-30', 'f0000002-0000-0000-0000-000000000002',
            'VALIDEE');

-- P-SL-0006 : THIAM Sokhna — en cours de création
MERGE INTO prise_en_charge (
                            id, patient_id, center_id,
                            date_debut_demande, date_fin_demande, forfait_demande_id,
                            statut
    ) KEY (id)
    VALUES ('b8000002-0000-0000-0000-000000000004', 'b3000002-0000-0000-0000-000000000004',
            '22222222-2222-2222-2222-222222222222',
            DATE '2025-02-15', DATE '2025-08-31', 'f0000002-0000-0000-0000-000000000001',
            'CREE');

-- P-SL-0007 : SECK Ilyass — validée
MERGE INTO prise_en_charge (
                            id, patient_id, center_id,
                            date_debut_demande, date_fin_demande, forfait_demande_id,
                            date_debut_effectif, date_fin_effectif, forfait_effectif_id,
                            statut
    ) KEY (id)
    VALUES ('b8000002-0000-0000-0000-000000000005', 'b3000002-0000-0000-0000-000000000005',
            '22222222-2222-2222-2222-222222222222',
            DATE '2025-03-05', DATE '2025-12-31', 'f0000002-0000-0000-0000-000000000001',
            DATE '2025-03-07', DATE '2025-12-31', 'f0000002-0000-0000-0000-000000000001',
            'VALIDEE');

-- P-SL-0008 : SARR Awa — en cours de création
MERGE INTO prise_en_charge (
                            id, patient_id, center_id,
                            date_debut_demande, date_fin_demande, forfait_demande_id,
                            statut
    ) KEY (id)
    VALUES ('b8000002-0000-0000-0000-000000000006', 'b3000002-0000-0000-0000-000000000006',
            '22222222-2222-2222-2222-222222222222',
            DATE '2025-03-15', DATE '2025-12-31', 'f0000002-0000-0000-0000-000000000002',
            'CREE');

-- ═══ PEC 2026 (renouvellement) ═══

-- Centre 1 — ANNABA
MERGE INTO prise_en_charge (
                            id, patient_id, center_id,
                            date_debut_demande, date_fin_demande, forfait_demande_id,
                            date_debut_effectif, date_fin_effectif, forfait_effectif_id,
                            statut
    ) KEY (id)
    VALUES ('c9000001-0000-0000-0000-000000000001', 'b1000001-0000-0000-0000-000000000001',
            '11111111-1111-1111-1111-111111111111',
            DATE '2026-01-01', DATE '2026-12-31', 'f0000001-0000-0000-0000-000000000001',
            DATE '2026-01-03', DATE '2026-12-31', 'f0000001-0000-0000-0000-000000000001',
            'VALIDEE');
MERGE INTO prise_en_charge (
                            id, patient_id, center_id,
                            date_debut_demande, date_fin_demande, forfait_demande_id,
                            date_debut_effectif, date_fin_effectif, forfait_effectif_id,
                            statut
    ) KEY (id)
    VALUES ('c9000001-0000-0000-0000-000000000002', 'b1000001-0000-0000-0000-000000000002',
            '11111111-1111-1111-1111-111111111111',
            DATE '2026-01-01', DATE '2026-12-31', 'f0000001-0000-0000-0000-000000000002',
            DATE '2026-01-05', DATE '2026-12-31', 'f0000001-0000-0000-0000-000000000002',
            'VALIDEE');
MERGE INTO prise_en_charge (
                            id, patient_id, center_id,
                            date_debut_demande, date_fin_demande, forfait_demande_id,
                            date_debut_effectif, date_fin_effectif, forfait_effectif_id,
                            statut
    ) KEY (id)
    VALUES ('c9000001-0000-0000-0000-000000000003', 'b3000001-0000-0000-0000-000000000001',
            '11111111-1111-1111-1111-111111111111',
            DATE '2026-01-01', DATE '2026-12-31', 'f0000001-0000-0000-0000-000000000001',
            DATE '2026-01-04', DATE '2026-12-31', 'f0000001-0000-0000-0000-000000000001',
            'VALIDEE');
MERGE INTO prise_en_charge (
                            id, patient_id, center_id,
                            date_debut_demande, date_fin_demande, forfait_demande_id,
                            date_debut_effectif, date_fin_effectif, forfait_effectif_id,
                            statut
    ) KEY (id)
    VALUES ('c9000001-0000-0000-0000-000000000004', 'b3000001-0000-0000-0000-000000000002',
            '11111111-1111-1111-1111-111111111111',
            DATE '2026-01-01', DATE '2026-12-31', 'f0000001-0000-0000-0000-000000000002',
            DATE '2026-01-06', DATE '2026-12-31', 'f0000001-0000-0000-0000-000000000002',
            'VALIDEE');
MERGE INTO prise_en_charge (
                            id, patient_id, center_id,
                            date_debut_demande, date_fin_demande, forfait_demande_id,
                            date_debut_effectif, date_fin_effectif, forfait_effectif_id,
                            statut
    ) KEY (id)
    VALUES ('c9000001-0000-0000-0000-000000000005', 'b3000001-0000-0000-0000-000000000003',
            '11111111-1111-1111-1111-111111111111',
            DATE '2026-01-01', DATE '2026-12-31', 'f0000001-0000-0000-0000-000000000001',
            DATE '2026-01-05', DATE '2026-12-31', 'f0000001-0000-0000-0000-000000000001',
            'VALIDEE');
MERGE INTO prise_en_charge (
                            id, patient_id, center_id,
                            date_debut_demande, date_fin_demande, forfait_demande_id,
                            statut
    ) KEY (id)
    VALUES ('c9000001-0000-0000-0000-000000000006', 'b3000001-0000-0000-0000-000000000004',
            '11111111-1111-1111-1111-111111111111',
            DATE '2026-01-01', DATE '2026-09-30', 'f0000001-0000-0000-0000-000000000003',
            'CREE');
MERGE INTO prise_en_charge (
                            id, patient_id, center_id,
                            date_debut_demande, date_fin_demande, forfait_demande_id,
                            date_debut_effectif, date_fin_effectif, forfait_effectif_id,
                            statut
    ) KEY (id)
    VALUES ('c9000001-0000-0000-0000-000000000007', 'b3000001-0000-0000-0000-000000000005',
            '11111111-1111-1111-1111-111111111111',
            DATE '2026-01-01', DATE '2026-12-31', 'f0000001-0000-0000-0000-000000000002',
            DATE '2026-01-07', DATE '2026-12-31', 'f0000001-0000-0000-0000-000000000002',
            'VALIDEE');
MERGE INTO prise_en_charge (
                            id, patient_id, center_id,
                            date_debut_demande, date_fin_demande, forfait_demande_id,
                            statut
    ) KEY (id)
    VALUES ('c9000001-0000-0000-0000-000000000008', 'b3000001-0000-0000-0000-000000000006',
            '11111111-1111-1111-1111-111111111111',
            DATE '2026-02-01', DATE '2026-12-31', 'f0000001-0000-0000-0000-000000000003',
            'CREE');

-- Centre 2 — ROUIBA
MERGE INTO prise_en_charge (
                            id, patient_id, center_id,
                            date_debut_demande, date_fin_demande, forfait_demande_id,
                            date_debut_effectif, date_fin_effectif, forfait_effectif_id,
                            statut
    ) KEY (id)
    VALUES ('c9000002-0000-0000-0000-000000000001', 'b2000001-0000-0000-0000-000000000001',
            '22222222-2222-2222-2222-222222222222',
            DATE '2026-01-01', DATE '2026-12-31', 'f0000002-0000-0000-0000-000000000001',
            DATE '2026-01-03', DATE '2026-12-31', 'f0000002-0000-0000-0000-000000000001',
            'VALIDEE');
MERGE INTO prise_en_charge (
                            id, patient_id, center_id,
                            date_debut_demande, date_fin_demande, forfait_demande_id,
                            statut
    ) KEY (id)
    VALUES ('c9000002-0000-0000-0000-000000000002', 'b2000001-0000-0000-0000-000000000002',
            '22222222-2222-2222-2222-222222222222',
            DATE '2026-01-01', DATE '2026-12-31', 'f0000002-0000-0000-0000-000000000002',
            'CREE');
MERGE INTO prise_en_charge (
                            id, patient_id, center_id,
                            date_debut_demande, date_fin_demande, forfait_demande_id,
                            date_debut_effectif, date_fin_effectif, forfait_effectif_id,
                            statut
    ) KEY (id)
    VALUES ('c9000002-0000-0000-0000-000000000003', 'b3000002-0000-0000-0000-000000000001',
            '22222222-2222-2222-2222-222222222222',
            DATE '2026-01-01', DATE '2026-12-31', 'f0000002-0000-0000-0000-000000000001',
            DATE '2026-01-04', DATE '2026-12-31', 'f0000002-0000-0000-0000-000000000001',
            'VALIDEE');
MERGE INTO prise_en_charge (
                            id, patient_id, center_id,
                            date_debut_demande, date_fin_demande, forfait_demande_id,
                            date_debut_effectif, date_fin_effectif, forfait_effectif_id,
                            statut
    ) KEY (id)
    VALUES ('c9000002-0000-0000-0000-000000000004', 'b3000002-0000-0000-0000-000000000002',
            '22222222-2222-2222-2222-222222222222',
            DATE '2026-01-01', DATE '2026-12-31', 'f0000002-0000-0000-0000-000000000001',
            DATE '2026-01-06', DATE '2026-12-31', 'f0000002-0000-0000-0000-000000000001',
            'VALIDEE');
MERGE INTO prise_en_charge (
                            id, patient_id, center_id,
                            date_debut_demande, date_fin_demande, forfait_demande_id,
                            date_debut_effectif, date_fin_effectif, forfait_effectif_id,
                            statut
    ) KEY (id)
    VALUES ('c9000002-0000-0000-0000-000000000005', 'b3000002-0000-0000-0000-000000000003',
            '22222222-2222-2222-2222-222222222222',
            DATE '2026-01-01', DATE '2026-12-31', 'f0000002-0000-0000-0000-000000000002',
            DATE '2026-01-05', DATE '2026-12-31', 'f0000002-0000-0000-0000-000000000002',
            'VALIDEE');
MERGE INTO prise_en_charge (
                            id, patient_id, center_id,
                            date_debut_demande, date_fin_demande, forfait_demande_id,
                            statut
    ) KEY (id)
    VALUES ('c9000002-0000-0000-0000-000000000006', 'b3000002-0000-0000-0000-000000000004',
            '22222222-2222-2222-2222-222222222222',
            DATE '2026-02-01', DATE '2026-08-31', 'f0000002-0000-0000-0000-000000000001',
            'CREE');
MERGE INTO prise_en_charge (
                            id, patient_id, center_id,
                            date_debut_demande, date_fin_demande, forfait_demande_id,
                            date_debut_effectif, date_fin_effectif, forfait_effectif_id,
                            statut
    ) KEY (id)
    VALUES ('c9000002-0000-0000-0000-000000000007', 'b3000002-0000-0000-0000-000000000005',
            '22222222-2222-2222-2222-222222222222',
            DATE '2026-01-01', DATE '2026-12-31', 'f0000002-0000-0000-0000-000000000001',
            DATE '2026-01-07', DATE '2026-12-31', 'f0000002-0000-0000-0000-000000000001',
            'VALIDEE');
MERGE INTO prise_en_charge (
                            id, patient_id, center_id,
                            date_debut_demande, date_fin_demande, forfait_demande_id,
                            statut
    ) KEY (id)
    VALUES ('c9000002-0000-0000-0000-000000000008', 'b3000002-0000-0000-0000-000000000006',
            '22222222-2222-2222-2222-222222222222',
            DATE '2026-03-01', DATE '2026-12-31', 'f0000002-0000-0000-0000-000000000002',
            'CREE');

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

-- Conformite metier: tout patient doit avoir une attestation et une PEC valide (forfait inclus).
INSERT INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin)
SELECT RANDOM_UUID(), p.id, p.center_id, DATE '2024-01-01', DATE '2030-12-31'
FROM patients p
WHERE NOT EXISTS (SELECT 1
                  FROM attestation_droit a
                  WHERE a.patient_id = p.id
                    AND a.center_id = p.center_id);

INSERT INTO prise_en_charge (id, patient_id, center_id,
                             date_debut_demande, date_fin_demande, forfait_demande_id,
                             date_debut_effectif, date_fin_effectif, forfait_effectif_id,
                             statut)
SELECT RANDOM_UUID(),
       p.id,
       p.center_id,
       DATE '2024-01-01',
       DATE '2030-12-31',
       CASE
           WHEN p.center_id = '11111111-1111-1111-1111-111111111111' THEN 'f0000001-0000-0000-0000-000000000001'
           ELSE 'f0000002-0000-0000-0000-000000000001'
           END,
       DATE '2024-01-01',
       DATE '2030-12-31',
       CASE
           WHEN p.center_id = '11111111-1111-1111-1111-111111111111' THEN 'f0000001-0000-0000-0000-000000000001'
           ELSE 'f0000002-0000-0000-0000-000000000001'
           END,
       'VALIDEE'
FROM patients p
WHERE NOT EXISTS (SELECT 1
                  FROM prise_en_charge pec
                  WHERE pec.patient_id = p.id
                    AND pec.center_id = p.center_id
                    AND pec.statut = 'VALIDEE'
                    AND COALESCE(pec.forfait_effectif_id, pec.forfait_demande_id) IS NOT NULL);
