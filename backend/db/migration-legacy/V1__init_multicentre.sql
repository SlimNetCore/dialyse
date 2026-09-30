CREATE TABLE IF NOT EXISTS centers (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS user_center_assignment (
    id UUID PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    center_id UUID NOT NULL,
    role_code VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_user_center_center
        FOREIGN KEY (center_id) REFERENCES centers (id) ON DELETE CASCADE,
    CONSTRAINT uk_user_center_role UNIQUE (user_id, center_id, role_code)
);

CREATE INDEX IF NOT EXISTS idx_user_center_assignment_center
    ON user_center_assignment (center_id);

