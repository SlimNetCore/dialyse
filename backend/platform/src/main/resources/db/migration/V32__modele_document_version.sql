-- ═══════════════════════════════════════════════════════════════════
-- Versions personnalisées des modèles d'impression (JRXML téléversés par un ADMIN de centre).
-- NOTE : Flyway n'est pas actif sur ce projet (voir AGENTS.md / db/schema.sql) ; ce script
-- documente le schéma pour la parité PostgreSQL. Le schéma réel en dev/test est généré par
-- Hibernate (ddl-auto: update) à partir de l'@Entity ModeleDocumentVersionJpaEntity.
--
-- Un modèle sans version active utilise le modèle d'origine livré avec l'application.
-- Le contenu n'est accepté qu'après validation stricte (JrxmlSecurityValidator) : requête SQL
-- identique à l'origine, expressions restreintes, éléments en liste blanche.
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS modele_document_version
(
    id
    UUID
    PRIMARY
    KEY,
    modele_id
    UUID
    NOT
    NULL,
    center_id
    UUID
    NOT
    NULL,
    version
    INTEGER
    NOT
    NULL,
    contenu
    TEXT
    NOT
    NULL,
    sha256
    VARCHAR
(
    64
) NOT NULL,
    taille_octets INTEGER NOT NULL,
    commentaire VARCHAR
(
    500
),
    uploaded_by VARCHAR
(
    100
) NOT NULL,
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL,
                              actif BOOLEAN NOT NULL,
                              CONSTRAINT uk_modele_version UNIQUE (modele_id, version)
    );

CREATE INDEX IF NOT EXISTS idx_modele_version_modele ON modele_document_version (modele_id, center_id);
