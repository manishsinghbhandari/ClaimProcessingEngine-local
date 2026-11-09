-- Create ai_route table for configurable AI endpoints per intervention code
CREATE TABLE IF NOT EXISTS ai_route (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    api_endpoint VARCHAR(500) NOT NULL,
    is_default BOOLEAN DEFAULT FALSE,
    created_by VARCHAR(255),
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    last_modified_by VARCHAR(255),
    last_modified_date TIMESTAMP
);

-- Create index on is_default for quick lookup of default route
CREATE INDEX IF NOT EXISTS idx_ai_route_is_default ON ai_route(is_default);