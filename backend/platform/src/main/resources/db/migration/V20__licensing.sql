-- V20: Commercial licensing module
-- One license per center, signed (RS256) and verified with a keypair the vendor
-- alone controls (private key never deployed to a client instance). The license
-- key itself is stored as an opaque signed string — it cannot be forged or edited
-- by direct database access without invalidating the signature.

CREATE TABLE IF NOT EXISTS license
(
    id                   UUID PRIMARY KEY,
    center_id            UUID                     NOT NULL REFERENCES centers (id),
    license_key          TEXT                     NOT NULL,
    jti                  VARCHAR(64)              NOT NULL UNIQUE,
    type                 VARCHAR(20)              NOT NULL DEFAULT 'STANDARD',
    max_users            INT                      NOT NULL DEFAULT 5,
    valid_from           TIMESTAMP WITH TIME ZONE NOT NULL,
    valid_until          TIMESTAMP WITH TIME ZONE NOT NULL,
    status               VARCHAR(20)              NOT NULL DEFAULT 'ACTIVE',
    activated_at         TIMESTAMP WITH TIME ZONE,
    last_online_check_at TIMESTAMP WITH TIME ZONE,
    revoked_reason       VARCHAR(255),
    created_by           UUID,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Only one active/non-revoked license history entry matters at a time per center,
-- but we keep history (revoked/expired rows) rather than overwriting, for audit.
CREATE INDEX IF NOT EXISTS idx_license_center_id ON license (center_id);
CREATE INDEX IF NOT EXISTS idx_license_status ON license (status);

-- New cross-center role reserved for the vendor's own account(s). A user with this
-- role is exempt from the per-center `app_user_center` access check (see AuthService)
-- and is the only role authorized to manage /api/v1/licenses.
INSERT INTO app_role (id, code, name, description)
SELECT 'a0a00001-0000-0000-0000-000000000005',
       'SUPERADMIN',
       'Propriétaire de l''application',
       'Gestion des licences — réservé à l''éditeur, aucun accès métier centre'
WHERE NOT EXISTS (SELECT 1 FROM app_role WHERE code = 'SUPERADMIN');
