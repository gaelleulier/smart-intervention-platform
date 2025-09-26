-- Passer la PK 'id' de INTEGER (serial) -> BIGINT, sans toucher la sequence existante
ALTER TABLE demo
  ALTER COLUMN id TYPE BIGINT;
