-- Migration V48 : reprise des calculs d'optimisation orphelins, instance par instance (revue P8, B1).
--
-- instance_id : jeton de l'instance du serveur qui a lancé le calcul (seule elle le fait avancer) ;
-- battement_le : dernier signe de vie de cette instance (toutes les 10 s). Un calcul EN_COURS sans battement depuis 60 s est orphelin.
-- Les calculs EN_COURS d'avant la migration n'ont ni l'un ni l'autre : ils sont repris comme orphelins.
-- Aussi appliqué au démarrage par db/schema.sql (idempotent).
ALTER TABLE IF EXISTS planification_optimisation ADD COLUMN IF NOT EXISTS instance_id VARCHAR (64);
ALTER TABLE IF EXISTS planification_optimisation ADD COLUMN IF NOT EXISTS battement_le TIMESTAMP;
