-- Migration V37 : une seule écriture comptable par source et par centre (R6, RG-CPT-001/002/012/014/022).
--
-- ADDITIVE : aucun DROP, aucun DELETE, aucune donnée modifiée. Jusqu'ici l'idempotence n'était tenue que par le verrou
-- applicatif « verrouillerSource » (revue DDD 2026-10-19, constat minor) ; cet index unique la garantit aussi en base.
-- Une écriture sans source (source_id NULL) n'est pas concernée : PostgreSQL tient les NULL pour distincts.
--
-- Sûreté du déploiement : si des doublons (center_id, source_id) existent déjà, l'index n'est PAS créé (il échouerait) ;
-- la migration se termine avec un WARNING qui donne leur nombre, sans bloquer le démarrage. Les doublons se contrôlent par
-- la requête de .ddd/migration/verifications-production.md (§ « Unicité des écritures par source ») ; une fois traités
-- (décision métier : extourne, jamais de suppression), une migration ultérieure crée l'index.
-- L'index non unique idx_ecriture_center_source (V32) est conservé.

DO
$$
DECLARE
doublons BIGINT;
BEGIN
SELECT COUNT(*)
INTO doublons
FROM (SELECT center_id, source_id
      FROM ecritures_comptables
      WHERE source_id IS NOT NULL
      GROUP BY center_id, source_id
      HAVING COUNT(*) > 1) d;

IF
doublons = 0 THEN
CREATE UNIQUE INDEX IF NOT EXISTS uq_ecriture_center_source ON ecritures_comptables (center_id, source_id);
ELSE
        RAISE WARNING 'V37 : % source(s) portent plusieurs écritures comptables ; index uq_ecriture_center_source non créé (voir verifications-production.md)', doublons;
END IF;
END
$$;

