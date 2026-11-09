-- Add route_id column to benefits_master table
ALTER TABLE benefits_master ADD COLUMN IF NOT EXISTS route_id INTEGER;

-- Add foreign key constraint
ALTER TABLE benefits_master
    ADD CONSTRAINT fk_benefits_master_ai_route
    FOREIGN KEY (route_id) REFERENCES ai_route(id)
    ON DELETE SET NULL;

-- Create index for route_id lookup
CREATE INDEX IF NOT EXISTS idx_benefits_master_route_id ON benefits_master(route_id);