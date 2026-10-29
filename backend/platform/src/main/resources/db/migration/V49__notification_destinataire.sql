-- Migration V49 : alertes personnelles (revue P8, M2).
--
-- target_user_id : compte destinataire d'une alerte personnelle (le roulement ou le remplacement d'un infirmier concerne CET
-- infirmier, pas tous ceux du centre). Vide pour les alertes visées par rôle seulement. Une alerte personnelle reste aussi visible
-- des rôles listés dans target_roles (l'ADMIN du centre).
-- Aussi appliqué au démarrage par db/schema.sql (idempotent).
ALTER TABLE IF EXISTS notification_evenement ADD COLUMN IF NOT EXISTS target_user_id VARCHAR (64);
