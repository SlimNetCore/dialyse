-- Amorçage H2 des compteurs de numérotation des pièces du stock (S-DDL2b, ADR 0186) : ces quatre MERGE étaient les dernières
-- instructions de db/seed.sql ; ils sont rejoués juste après lui (application.yml, application-demo-renadial.yml).
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

-- SEED2 (ADR 0188) : types de TVA de démonstration (tva_types : propriétaire Comptabilité, ADR 0187). Extraits de db/seed.sql sans changement.
-- Types TVA versionnés (source de vérité partagée facturation + comptabilité)
MERGE INTO tva_types (id, center_id, libelle, taux, type_prestation, exonere, date_debut_validite, date_fin_validite,
                      texte_reference, actif, created_by)
    KEY (id)
    VALUES ('aa000001-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111',
            'TVA Hémodialyse 19%', 19.00, 'HEMODIALYSE', FALSE,
            '2020-01-01', NULL, 'Article 138 du CGI', TRUE, 'seed');

MERGE INTO tva_types (id, center_id, libelle, taux, type_prestation, exonere, date_debut_validite, date_fin_validite,
                      texte_reference, actif, created_by)
    KEY (id)
    VALUES ('aa000002-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222',
            'TVA Hémodialyse 18%', 18.00, 'HEMODIALYSE', FALSE,
            '2020-01-01', NULL, 'Article 138 du CGI', TRUE, 'seed');
