-- ═══════════════════════════════════════════════════════════════════
-- Traçabilité pharmaceutique EPO/Fer — prescription structurée (article + fréquence).
-- NOTE : Flyway n'est pas actif sur ce projet (voir AGENTS.md / db/schema.sql) ; ce script
-- documente le schéma pour la parité PostgreSQL. Le schéma réel en dev/test est généré par
-- Hibernate (ddl-auto: update) à partir des @Entity.
-- ═══════════════════════════════════════════════════════════════════

ALTER TABLE prescriptions_medicales
    ADD COLUMN IF NOT EXISTS epo_article_id UUID,
    ADD COLUMN IF NOT EXISTS epo_frequence_valeur INTEGER,
    ADD COLUMN IF NOT EXISTS epo_frequence_unite VARCHAR(10),
    ADD COLUMN IF NOT EXISTS fer_article_id UUID,
    ADD COLUMN IF NOT EXISTS fer_frequence_valeur INTEGER,
    ADD COLUMN IF NOT EXISTS fer_frequence_unite VARCHAR(10);

ALTER TABLE prescriptions_medicales
    ADD CONSTRAINT IF NOT EXISTS chk_epo_frequence_unite
        CHECK (epo_frequence_unite IS NULL OR epo_frequence_unite IN ('HEURE', 'JOUR', 'SEMAINE', 'MOIS', 'ANNEE'));

ALTER TABLE prescriptions_medicales
    ADD CONSTRAINT IF NOT EXISTS chk_fer_frequence_unite
        CHECK (fer_frequence_unite IS NULL OR fer_frequence_unite IN ('HEURE', 'JOUR', 'SEMAINE', 'MOIS', 'ANNEE'));

-- Anciennes colonnes texte libre, remplacées par les colonnes structurées ci-dessus.
-- Laissées en place (nullable, non lues par le code) : pas de perte de données de démo.
-- ALTER TABLE prescriptions_medicales DROP COLUMN epo_molecule;
-- ALTER TABLE prescriptions_medicales DROP COLUMN epo_frequence;
-- ALTER TABLE prescriptions_medicales DROP COLUMN fer_molecule;
-- ALTER TABLE prescriptions_medicales DROP COLUMN fer_frequence;
