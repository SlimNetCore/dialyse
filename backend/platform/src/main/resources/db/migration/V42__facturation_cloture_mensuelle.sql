-- Migration V42 : clôture mensuelle de facturation (slice F7, ADR 0037, RG-FAC-040 à 045).
--
-- ADDITIVE : deux tables nouvelles, aucun DROP, aucun DELETE, aucune donnée existante modifiée, aucune ligne insérée :
-- AUCUN mois n'est clos au départ (un mois sans ligne est ouvert).
--  * periodes_facturation         : l'état d'un mois d'un centre (une ligne par centre et par mois, créée au premier verrouillage
--                                   ou à la première clôture). La ligne est verrouillée (SELECT ... FOR UPDATE) par la clôture, la
--                                   réouverture, l'émission des factures et les modifications de la simulation : elles se sérialisent.
--  * periodes_facturation_journal : journal APPEND-ONLY des clôtures et réouvertures (jamais d'UPDATE ni de DELETE par l'application).
-- Portable PostgreSQL / H2. Contrôle de production : .ddd/migration/verifications-production.md (point 23).

CREATE TABLE IF NOT EXISTS periodes_facturation
(
    center_id         UUID                     NOT NULL,
    annee             INTEGER                  NOT NULL,
    mois              INTEGER                  NOT NULL,
    statut            VARCHAR(10)              NOT NULL DEFAULT 'OUVERT',
    cloturee_par      UUID,
    cloturee_le       TIMESTAMP WITH TIME ZONE,
    rouverte_par      UUID,
    rouverte_le       TIMESTAMP WITH TIME ZONE,
    motif_reouverture VARCHAR(500),
    PRIMARY KEY (center_id, annee, mois),
    CONSTRAINT ck_periodes_facturation_mois CHECK (mois BETWEEN 1 AND 12),
    CONSTRAINT ck_periodes_facturation_statut CHECK (statut IN ('OUVERT', 'CLOTURE'))
);

CREATE TABLE IF NOT EXISTS periodes_facturation_journal
(
    id                            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    center_id                     UUID                     NOT NULL,
    annee                         INTEGER                  NOT NULL,
    mois                          INTEGER                  NOT NULL,
    type_acte                     VARCHAR(12)              NOT NULL,
    effectue_par                  UUID                     NOT NULL,
    effectue_le                   TIMESTAMP WITH TIME ZONE NOT NULL,
    motif                         VARCHAR(500),
    seances_eligibles_restantes   INTEGER,
    CONSTRAINT ck_periodes_facturation_journal_type CHECK (type_acte IN ('CLOTURE', 'REOUVERTURE'))
);

CREATE INDEX IF NOT EXISTS idx_periodes_facturation_journal_mois
    ON periodes_facturation_journal (center_id, annee, mois, id);
