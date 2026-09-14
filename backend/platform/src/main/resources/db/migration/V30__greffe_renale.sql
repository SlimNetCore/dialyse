-- ═══════════════════════════════════════════════════════════════════
-- Dossier de préparation à la greffe rénale (receveur + donneur vivant).
-- NOTE : Flyway n'est pas actif sur ce projet (voir AGENTS.md / db/schema.sql) ; ce script
-- documente le schéma pour la parité PostgreSQL. Le schéma réel en dev/test est généré par
-- Hibernate (ddl-auto: update) à partir des @Entity.
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS bilans_pre_greffe
(
    id                             UUID PRIMARY KEY,
    patient_id                     UUID                     NOT NULL,
    center_id                      UUID                     NOT NULL,
    statut                         VARCHAR(30)              NOT NULL,
    date_debut_bilan               DATE,
    date_inscription_liste_attente DATE,
    date_greffe                    DATE,
    groupe_sanguin_confirme        VARCHAR(5),
    typage_hla                     VARCHAR(500),
    pra_classe_i                   NUMERIC(5, 2),
    pra_classe_ii                  NUMERIC(5, 2),
    contre_indications             TEXT,
    conclusion_nephrologue         TEXT,
    created_at                     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_bilans_pre_greffe_patient ON bilans_pre_greffe (patient_id, center_id);

CREATE TABLE IF NOT EXISTS decisions_rcp
(
    id                   UUID PRIMARY KEY,
    bilan_id             UUID        NOT NULL,
    date_reunion         DATE        NOT NULL,
    avis                 VARCHAR(15) NOT NULL,
    compte_rendu         TEXT,
    prochaine_date_revue DATE
);

CREATE INDEX IF NOT EXISTS idx_decisions_rcp_bilan ON decisions_rcp (bilan_id);

CREATE TABLE IF NOT EXISTS etapes_bilan_pre_greffe
(
    id                UUID PRIMARY KEY,
    patient_id        UUID                     NOT NULL,
    center_id         UUID                     NOT NULL,
    categorie         VARCHAR(20)              NOT NULL,
    libelle           VARCHAR(255)             NOT NULL,
    statut            VARCHAR(20)              NOT NULL,
    date_realisation  DATE,
    resultat          TEXT,
    date_expiration   DATE,
    demande_examen_id UUID,
    serologie_id      UUID,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_etapes_bilan_pre_greffe_patient ON etapes_bilan_pre_greffe (patient_id, center_id);

CREATE TABLE IF NOT EXISTS donneurs_vivants
(
    id                  UUID PRIMARY KEY,
    patient_id          UUID                     NOT NULL,
    center_id           UUID                     NOT NULL,
    nom                 VARCHAR(100)             NOT NULL,
    prenom              VARCHAR(100),
    date_naissance      DATE,
    lien_parente        VARCHAR(20)              NOT NULL,
    telephone           VARCHAR(30),
    groupe_sanguin      VARCHAR(5),
    typage_hla          VARCHAR(500),
    statut_bilan        VARCHAR(20)              NOT NULL,
    crossmatch_resultat VARCHAR(15)              NOT NULL,
    date_crossmatch     DATE,
    bilan_realise       TEXT,
    contre_indications  TEXT,
    decision_finale     TEXT,
    date_decision       DATE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_donneurs_vivants_patient ON donneurs_vivants (patient_id, center_id);
