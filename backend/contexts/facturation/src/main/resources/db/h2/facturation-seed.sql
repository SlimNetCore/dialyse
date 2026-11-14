-- Amorçage H2 (poste de développement et tests) des réglages de facturation (SEED2, ADR 0188). Extrait, sans changement de contenu, de
-- db/seed.sql. Les factures de démonstration (INSERT ... SELECT sur les séances facturées) restent dans db/seed.sql : elles lisent
-- seances, ce que TableDesSeancesTest interdit hors du contexte Séance.

MERGE INTO facturation_settings (center_id, tva_rate, code_format, regroupement_multi_forfait, updated_at, updated_by)
    KEY (center_id)
    VALUES ('11111111-1111-1111-1111-111111111111', 19.00, 'FACT-{YYYY}-{SEQ6}', TRUE, CURRENT_TIMESTAMP, 'seed-perf');

MERGE INTO facturation_settings (center_id, tva_rate, code_format, regroupement_multi_forfait, updated_at, updated_by)
    KEY (center_id)
    VALUES ('22222222-2222-2222-2222-222222222222', 18.00, 'FACT-{YYYY}-{SEQ6}', TRUE, CURRENT_TIMESTAMP, 'seed-perf');
