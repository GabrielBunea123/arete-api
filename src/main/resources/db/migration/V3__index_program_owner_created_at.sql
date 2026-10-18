-- Default program list path: one owner's programs ordered by creation time
CREATE INDEX idx_program_owner_id_created_at ON program(owner_id, created_at);
