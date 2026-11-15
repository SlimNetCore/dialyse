-- ═══════════════════════════════════════════════════════════════════
-- Purge du jeu de test RENADIAL (voir renadial-seed.sql). Supprime uniquement les données rattachées à la
-- société RENADIAL — n'affecte aucune autre société ni aucun autre centre.
-- ═══════════════════════════════════════════════════════════════════

DELETE
FROM alertes_observance
WHERE center_id IN (SELECT id FROM centers WHERE societe_id = (SELECT id FROM societes WHERE code = 'RENADIAL'));
DELETE
FROM administrations_anemie
WHERE center_id IN (SELECT id FROM centers WHERE societe_id = (SELECT id FROM societes WHERE code = 'RENADIAL'));
DELETE
FROM facture_reglements
WHERE center_id IN (SELECT id FROM centers WHERE societe_id = (SELECT id FROM societes WHERE code = 'RENADIAL'));
DELETE
FROM factures
WHERE center_id IN (SELECT id FROM centers WHERE societe_id = (SELECT id FROM societes WHERE code = 'RENADIAL'));
DELETE
FROM seances
WHERE center_id IN (SELECT id FROM centers WHERE societe_id = (SELECT id FROM societes WHERE code = 'RENADIAL'));
DELETE
FROM serologies_patient
WHERE center_id IN (SELECT id FROM centers WHERE societe_id = (SELECT id FROM societes WHERE code = 'RENADIAL'));
DELETE
FROM resultats_analyses
WHERE center_id IN (SELECT id FROM centers WHERE societe_id = (SELECT id FROM societes WHERE code = 'RENADIAL'));
DELETE
FROM bilans_pre_greffe
WHERE center_id IN (SELECT id FROM centers WHERE societe_id = (SELECT id FROM societes WHERE code = 'RENADIAL'));
DELETE
FROM patients
WHERE center_id IN (SELECT id FROM centers WHERE societe_id = (SELECT id FROM societes WHERE code = 'RENADIAL'));
DELETE
FROM lots
WHERE center_id IN (SELECT id FROM centers WHERE societe_id = (SELECT id FROM societes WHERE code = 'RENADIAL'));
DELETE
FROM articles
WHERE center_id IN (SELECT id FROM centers WHERE societe_id = (SELECT id FROM societes WHERE code = 'RENADIAL'));
DELETE
FROM centre_payeur
WHERE center_id IN (SELECT id FROM centers WHERE societe_id = (SELECT id FROM societes WHERE code = 'RENADIAL'));
DELETE
FROM agence
WHERE center_id IN (SELECT id FROM centers WHERE societe_id = (SELECT id FROM societes WHERE code = 'RENADIAL'));
DELETE
FROM caisse_assurance
WHERE center_id IN (SELECT id FROM centers WHERE societe_id = (SELECT id FROM societes WHERE code = 'RENADIAL'));
DELETE
FROM app_user_societe
WHERE societe_id = (SELECT id FROM societes WHERE code = 'RENADIAL');
DELETE
FROM centers
WHERE societe_id = (SELECT id FROM societes WHERE code = 'RENADIAL');
DELETE
FROM societes
WHERE code = 'RENADIAL';
