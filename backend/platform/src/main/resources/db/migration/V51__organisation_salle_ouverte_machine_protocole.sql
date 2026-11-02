-- Migration V51 : schéma du contexte Organisation absent du chemin Flyway (production PostgreSQL).
--
-- Jusqu'ici la colonne salle.ouverte et les tables machine / protocole n'existaient que dans db/schema.sql (H2) : le profil de
-- production n'exécute que Flyway, donc SallesJdbc, MachinesJdbc et ProtocolesJdbc échouaient en SQL.
--
-- ADDITIVE et IDEMPOTENTE : aucune donnée existante modifiée ni supprimée. Les salles existantes restent ouvertes (DEFAULT TRUE,
-- comme avant l'introduction de l'ouverture/fermeture). Les tables nouvelles sont vides. Portable PostgreSQL 16 / H2.

ALTER TABLE salle
    ADD COLUMN IF NOT EXISTS ouverte BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE IF NOT EXISTS machine
(
    id        UUID PRIMARY KEY,
    center_id UUID         NOT NULL,
    code      VARCHAR(100) NOT NULL,
    libelle   VARCHAR(255) NOT NULL,
    actif     BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS protocole
(
    id        UUID PRIMARY KEY,
    center_id UUID         NOT NULL,
    code      VARCHAR(100) NOT NULL,
    libelle   VARCHAR(255) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_machine_center ON machine (center_id);
CREATE INDEX IF NOT EXISTS idx_protocole_center ON protocole (center_id);
