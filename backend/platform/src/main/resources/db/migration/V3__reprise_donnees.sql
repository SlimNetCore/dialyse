-- ============================================================
-- V3 : reprise des données d'un système existant
-- Un lot de reprise par centre ; correspondances « identifiant d'origine → identifiant cible » (reprise
-- rejouable, annulation des seules données créées) ; correspondances de valeurs saisies par le centre ;
-- comptes rendus de vérification / d'import. Même DDL que db/schema.sql (H2).
-- ============================================================

CREATE TABLE IF NOT EXISTS public.migration_batch
(
    id
    uuid
    NOT
    NULL
    PRIMARY
    KEY,
    center_id
    uuid
    NOT
    NULL,
    libelle
    character
    varying
(
    200
) NOT NULL,
    source_system character varying
(
    100
),
    date_debut_reprise date,
    status character varying
(
    20
) NOT NULL,
    created_by character varying
(
    100
),
    created_at timestamp with time zone NOT NULL,
    closed_at timestamp with time zone
                            );
CREATE INDEX IF NOT EXISTS idx_migration_batch_center ON public.migration_batch (center_id, status);

CREATE TABLE IF NOT EXISTS public.migration_entity_run
(
    id
    uuid
    NOT
    NULL
    PRIMARY
    KEY,
    batch_id
    uuid
    NOT
    NULL,
    center_id
    uuid
    NOT
    NULL,
    entity
    character
    varying
(
    40
) NOT NULL,
    file_name character varying
(
    255
),
    dry_run boolean NOT NULL,
    applied boolean NOT NULL,
    total_rows integer NOT NULL,
    created_count integer NOT NULL,
    updated_count integer NOT NULL,
    error_count integer NOT NULL,
    warning_count integer NOT NULL,
    report_json text,
    executed_by character varying
(
    100
),
    executed_at timestamp with time zone NOT NULL
                              );
CREATE INDEX IF NOT EXISTS idx_migration_run_batch ON public.migration_entity_run (batch_id, entity, executed_at);

CREATE TABLE IF NOT EXISTS public.migration_id_map
(
    id
    uuid
    NOT
    NULL
    PRIMARY
    KEY,
    center_id
    uuid
    NOT
    NULL,
    batch_id
    uuid
    NOT
    NULL,
    entity
    character
    varying
(
    40
) NOT NULL,
    legacy_id character varying
(
    150
) NOT NULL,
    target_id character varying
(
    150
) NOT NULL,
    operation character varying
(
    10
) NOT NULL,
    created_at timestamp with time zone NOT NULL,
                             CONSTRAINT uk_migration_id_map UNIQUE (center_id, entity, legacy_id)
    );
CREATE INDEX IF NOT EXISTS idx_migration_id_map_batch ON public.migration_id_map (batch_id);

CREATE TABLE IF NOT EXISTS public.migration_value_map
(
    id
    uuid
    NOT
    NULL
    PRIMARY
    KEY,
    center_id
    uuid
    NOT
    NULL,
    column_key
    character
    varying
(
    60
) NOT NULL,
    source_value character varying
(
    150
) NOT NULL,
    target_value character varying
(
    150
) NOT NULL,
    CONSTRAINT uk_migration_value_map UNIQUE
(
    center_id,
    column_key,
    source_value
)
    );

