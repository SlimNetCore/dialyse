-- Amorçage H2 (poste de développement et tests) des modèles de documents (SEED2, ADR 0188) : les Documents sont propriétaires de
-- modele_document. Extrait, bloc à bloc et sans changement de contenu, de db/seed.sql. Chargé par spring.sql.init.data-locations
-- (application.yml de la plateforme) APRÈS l'Organisation (center_id).

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

MERGE INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, format_impression,
                            description) KEY (id)
    VALUES ('d0d00001-0000-0000-0000-000000000007', '11111111-1111-1111-1111-111111111111',
            'SYNTHESE_FACTURATION_MENSUELLE', 'Synthese mensuelle facturation', 'SYNTHESE_FACTURATION_MENSUELLE',
            'reports/synthese_mensuelle_facturation.jrxml', 'PDF',
            'Tableau croise facturation par forfait/caisse avec repartition graphique');

MERGE INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, format_impression,
                            description) KEY (id)
    VALUES ('d0d00001-0000-0000-0000-000000000008', '11111111-1111-1111-1111-111111111111',
            'ORDONNANCE', 'Ordonnance médicamenteuse', 'ORDONNANCE',
            'reports/ordonnance.jrxml', 'PDF', 'Ordonnance signée par le médecin, imprimable pour le patient');

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

MERGE INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, format_impression,
                            description) KEY (id)
    VALUES ('d0d00002-0000-0000-0000-000000000007', '22222222-2222-2222-2222-222222222222',
            'SYNTHESE_FACTURATION_MENSUELLE', 'Synthese mensuelle facturation', 'SYNTHESE_FACTURATION_MENSUELLE',
            'reports/synthese_mensuelle_facturation.jrxml', 'PDF',
            'Tableau croise facturation par forfait/caisse avec repartition graphique');

MERGE INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, format_impression,
                            description) KEY (id)
    VALUES ('d0d00002-0000-0000-0000-000000000008', '22222222-2222-2222-2222-222222222222',
            'ORDONNANCE', 'Ordonnance médicamenteuse', 'ORDONNANCE',
            'reports/ordonnance.jrxml', 'PDF', 'Ordonnance signée par le médecin, imprimable pour le patient');
