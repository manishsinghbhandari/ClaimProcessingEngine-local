-- Create user_county table for assigning users to specific counties
CREATE TABLE IF NOT EXISTS user_county (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    admin_level_one_id BIGINT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(255),
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_by VARCHAR(255),
    last_modified_date TIMESTAMP,
    
    -- Foreign key constraints
    CONSTRAINT fk_user_county_username FOREIGN KEY (username) REFERENCES users(username) ON DELETE CASCADE,
    CONSTRAINT fk_user_county_admin_level FOREIGN KEY (admin_level_one_id) REFERENCES admin_level_ones(id) ON DELETE CASCADE,
    
    -- Unique constraint to prevent duplicate active user-county mappings
    CONSTRAINT uk_user_county UNIQUE (username, admin_level_one_id, is_active)
);

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_user_county_username
    ON user_county(username);

CREATE INDEX IF NOT EXISTS idx_user_county_admin_level_one_id
    ON user_county(admin_level_one_id);

CREATE INDEX IF NOT EXISTS idx_user_county_is_active
    ON user_county(is_active);

CREATE INDEX IF NOT EXISTS idx_user_county_username_active
    ON user_county(username, is_active);