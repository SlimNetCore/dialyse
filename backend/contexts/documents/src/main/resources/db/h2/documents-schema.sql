-- DDL H2 (tests et poste de développement) des tables dont les Documents sont propriétaires (S-DDL3, ADR 0186).
-- Chargé par spring.sql.init.schema-locations (application.yml de la plateforme) ; en production le schéma vient de Flyway (V1).

-- ═══ Modèles de documents (Jasper) ═══

CREATE TABLE IF NOT EXISTS modele_document (
    id UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    libelle VARCHAR(255) NOT NULL,
    type_document VARCHAR(50) NOT NULL,
    chemin_jrxml VARCHAR(500) NOT NULL,
    format_impression VARCHAR(20) NOT NULL DEFAULT 'PDF',
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(center_id, code)
);

-- type_document : FICHE_PATIENT, ATTESTATION, PEC, FICHE_SIGNALETIQUE, CUSTOM
-- format_impression : PDF, EXCEL, HTML

-- DOC3 (ADR 0163) : versions de gabarit téléversées par les centres. Jusqu'ici créée par Hibernate (ddl-auto: update) à partir de
-- l'entité ModeleDocumentVersionJpaEntity ; ce DDL reproduit le schéma réel de production (Flyway V1__baseline_schema : mêmes colonnes,
-- mêmes types, clé primaire, contrainte uk_modele_version, index idx_modele_version_modele). Aucune clé étrangère vers modele_document
-- (absente en production). Une seule version active par modèle est tenue par le domaine, pas par la base.
CREATE TABLE IF NOT EXISTS modele_document_version (
    id UUID PRIMARY KEY,
    actif BOOLEAN NOT NULL,
    center_id UUID NOT NULL,
    commentaire VARCHAR(500),
    contenu TEXT NOT NULL,
    modele_id UUID NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    taille_octets INTEGER NOT NULL,
    uploaded_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    uploaded_by VARCHAR(100) NOT NULL,
    version INTEGER NOT NULL,
    CONSTRAINT uk_modele_version UNIQUE (modele_id, version)
);
CREATE INDEX IF NOT EXISTS idx_modele_version_modele ON modele_document_version (modele_id, center_id);
