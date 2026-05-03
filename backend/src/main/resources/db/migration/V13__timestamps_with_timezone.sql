-- V13: standardize technical timestamps to timezone-aware columns

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'assure' AND column_name = 'created_at'
    ) THEN
        ALTER TABLE assure
            ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE
            USING created_at AT TIME ZONE 'UTC';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'assure_patient' AND column_name = 'date_affectation'
    ) THEN
        ALTER TABLE assure_patient
            ALTER COLUMN date_affectation TYPE TIMESTAMP WITH TIME ZONE
            USING date_affectation AT TIME ZONE 'UTC';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'app_user' AND column_name = 'created_at'
    ) THEN
        ALTER TABLE app_user
            ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE
            USING created_at AT TIME ZONE 'UTC';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'auth_refresh_token' AND column_name = 'expires_at'
    ) THEN
        ALTER TABLE auth_refresh_token
            ALTER COLUMN expires_at TYPE TIMESTAMP WITH TIME ZONE
            USING expires_at AT TIME ZONE 'UTC';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'auth_refresh_token' AND column_name = 'created_at'
    ) THEN
        ALTER TABLE auth_refresh_token
            ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE
            USING created_at AT TIME ZONE 'UTC';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'auth_refresh_token' AND column_name = 'revoked_at'
    ) THEN
        ALTER TABLE auth_refresh_token
            ALTER COLUMN revoked_at TYPE TIMESTAMP WITH TIME ZONE
            USING revoked_at AT TIME ZONE 'UTC';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'modele_document' AND column_name = 'created_at'
    ) THEN
        ALTER TABLE modele_document
            ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE
            USING created_at AT TIME ZONE 'UTC';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'report_template' AND column_name = 'created_at'
    ) THEN
        ALTER TABLE report_template
            ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE
            USING created_at AT TIME ZONE 'UTC';
    END IF;
END
$$;

