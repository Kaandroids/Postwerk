-- Resize embedding column from 768 to 3072 dimensions (gemini-embedding-001)
ALTER TABLE categories DROP COLUMN IF EXISTS embedding;
ALTER TABLE categories ADD COLUMN embedding vector(3072);
