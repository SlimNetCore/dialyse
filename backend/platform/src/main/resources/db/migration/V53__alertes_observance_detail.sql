-- Migration V53 : colonnes de détail des alertes d'observance (KB-MED-33, ADR 0081, constat du rejeu PostgreSQL 16 de DM2).
--
-- L'entité JPA AlerteObservanceJpaEntity et l'adapter AlertesJdbc du Dossier médical lisent et écrivent quatre colonnes que la chaîne
-- Flyway V1..V52 ne créait pas (elles n'existaient qu'avec ddl-auto: update) : la prescription au moment de l'alerte (unité, dose
-- prescrite, valeur et unité de la fréquence). Sans elles, la liste des alertes, leur résolution et le contrôle de nuit 06:30 échouent
-- sur une base issue de Flyway.
--
-- ADDITIVE et IDEMPOTENTE : quatre colonnes NULLABLES sans valeur par défaut, mêmes types que l'entité JPA ; aucune donnée n'est lue ni
-- modifiée (les alertes existantes gardent des détails nuls, que l'API rend déjà nuls). Sans effet sur une base qui les a déjà
-- (développement). Portable PostgreSQL 16 / H2. Décision déléguée, À CONFIRMER avec le DBA et les métiers.

ALTER TABLE alertes_observance
    ADD COLUMN IF NOT EXISTS unite_dose VARCHAR(10);
ALTER TABLE alertes_observance
    ADD COLUMN IF NOT EXISTS dose_prescrite INTEGER;
ALTER TABLE alertes_observance
    ADD COLUMN IF NOT EXISTS frequence_valeur INTEGER;
ALTER TABLE alertes_observance
    ADD COLUMN IF NOT EXISTS frequence_unite VARCHAR(10);
