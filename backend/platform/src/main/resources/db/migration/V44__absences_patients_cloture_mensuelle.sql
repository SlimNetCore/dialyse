-- Migration V44 : clôture mensuelle des absences de patients (slice A6, ADR 0042, RG-ABS-070 à 078).
--
-- ADDITIVE : deux tables nouvelles, aucun DROP, aucun DELETE, aucune donnée existante modifiée, aucune ligne insérée :
-- AUCUN mois n'est clos au départ (un mois sans ligne est ouvert).
--  * mois_absences         : l'état d'un mois d'absences d'un centre (une ligne par centre et par mois, créée au premier
--                            verrouillage ou à la première clôture). La ligne est verrouillée (SELECT ... FOR UPDATE) par la
--                            clôture, la réouverture et chaque écriture d'absence du mois : elles se sérialisent.
--  * mois_absences_journal : journal APPEND-ONLY des clôtures et réouvertures (jamais d'UPDATE ni de DELETE par l'application).
-- Portable PostgreSQL / H2. Contrôle de production : .ddd/migration/verifications-production.md (SELECT seulement).

CREATE TABLE IF NOT EXISTS mois_absences
(
    center_id
    UUID
    NOT
    NULL,
    annee
    INTEGER
    NOT
    NULL,
    mois
    INTEGER
    NOT
    NULL,
    statut
    VARCHAR
(
    10
) NOT NULL DEFAULT 'OUVERT',
    cloture_par UUID,
    cloture_le TIMESTAMP WITH TIME ZONE,
    rouvert_par UUID,
    rouvert_le TIMESTAMP WITH TIME ZONE,
                             motif_reouverture VARCHAR (500),
    PRIMARY KEY
(
    center_id,
    annee,
    mois
),
    CONSTRAINT ck_mois_absences_mois CHECK
(
    mois
    BETWEEN
    1
    AND
    12
),
    CONSTRAINT ck_mois_absences_statut CHECK
(
    statut
    IN
(
    'OUVERT',
    'CLOTURE'
))
    );

CREATE TABLE IF NOT EXISTS mois_absences_journal
(
    id
    BIGINT
    GENERATED
    ALWAYS AS
    IDENTITY
    PRIMARY
    KEY,
    center_id
    UUID
    NOT
    NULL,
    annee
    INTEGER
    NOT
    NULL,
    mois
    INTEGER
    NOT
    NULL,
    type_acte
    VARCHAR
(
    12
) NOT NULL,
    effectue_par UUID NOT NULL,
    effectue_le TIMESTAMP WITH TIME ZONE NOT NULL,
                              motif VARCHAR (500),
    a_qualifier_restantes BIGINT,
    en_retard BIGINT,
    CONSTRAINT ck_mois_absences_journal_type CHECK
(
    type_acte
    IN
(
    'CLOTURE',
    'REOUVERTURE'
))
    );

CREATE INDEX IF NOT EXISTS idx_mois_absences_journal_mois
    ON mois_absences_journal (center_id, annee, mois, id);
