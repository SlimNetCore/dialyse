-- Paramétrage comptable sans développement : plan comptable par centre, compte client par payeur, modèles de pièces.
--
-- 1. comptes_comptables : plan comptable du centre (numéro, libellé, actif). Un centre sans ligne dispose du plan SCF
--    de départ, complété des comptes que son paramétrage utilise déjà : aucune reprise de données n'est nécessaire.
-- 2. comptes_payeurs : compte client propre à un payeur (centre_payeur) ; sans ligne, le compte client par défaut du
--    centre (colonne compte_client_autre) s'applique.
-- 3. modeles_piece + modeles_piece_lignes : types de pièces définis par le centre (journal, lignes débit/crédit).
-- 4. ecritures_comptables.modele_id : modèle d'une pièce saisie ; vide pour une écriture générée par le système.
-- 5. mapping_comptable : les comptes clients par type de payeur (CNAS, CASNOS, mutuelle) ne sont plus alimentés,
--    remplacés par le compte porté par chaque payeur ; leurs colonnes, conservées, deviennent facultatives.
--
-- Idempotent. Les mêmes tables sont créées par db/schema.sql pour H2 (développement).

CREATE TABLE IF NOT EXISTS comptes_comptables
(
    id
    UUID
    NOT
    NULL
    PRIMARY
    KEY,
    center_id
    UUID
    NOT
    NULL,
    numero
    VARCHAR
(
    20
) NOT NULL,
    libelle VARCHAR
(
    150
) NOT NULL,
    actif BOOLEAN NOT NULL,
    CONSTRAINT uq_compte_comptable_centre_numero UNIQUE
(
    center_id,
    numero
)
    );

CREATE TABLE IF NOT EXISTS comptes_payeurs
(
    center_id
    UUID
    NOT
    NULL,
    payeur_id
    UUID
    NOT
    NULL,
    compte
    VARCHAR
(
    20
) NOT NULL,
    CONSTRAINT pk_comptes_payeurs PRIMARY KEY
(
    center_id,
    payeur_id
)
    );

CREATE TABLE IF NOT EXISTS modeles_piece
(
    id
    UUID
    NOT
    NULL
    PRIMARY
    KEY,
    center_id
    UUID
    NOT
    NULL,
    code
    VARCHAR
(
    20
) NOT NULL,
    libelle VARCHAR
(
    100
) NOT NULL,
    journal_code VARCHAR
(
    10
) NOT NULL,
    actif BOOLEAN NOT NULL,
    CONSTRAINT uq_modele_piece_centre_code UNIQUE
(
    center_id,
    code
)
    );

CREATE TABLE IF NOT EXISTS modeles_piece_lignes
(
    modele_id
    UUID
    NOT
    NULL,
    center_id
    UUID
    NOT
    NULL,
    position_ligne
    INTEGER
    NOT
    NULL,
    sens
    VARCHAR
(
    6
) NOT NULL,
    compte VARCHAR
(
    20
) NOT NULL,
    libelle VARCHAR
(
    100
),
    CONSTRAINT pk_modeles_piece_lignes PRIMARY KEY
(
    modele_id,
    position_ligne
)
    );
CREATE INDEX IF NOT EXISTS idx_modeles_piece_lignes_compte ON modeles_piece_lignes (center_id, compte);

ALTER TABLE ecritures_comptables
    ADD COLUMN IF NOT EXISTS modele_id UUID;

CREATE INDEX IF NOT EXISTS idx_lignes_ecriture_compte ON lignes_ecriture (compte_scf);

ALTER TABLE mapping_comptable
    ALTER COLUMN compte_client_cnas DROP NOT NULL;
ALTER TABLE mapping_comptable
    ALTER COLUMN compte_client_casnos DROP NOT NULL;
ALTER TABLE mapping_comptable
    ALTER COLUMN compte_client_mutuelle DROP NOT NULL;
