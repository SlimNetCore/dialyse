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
