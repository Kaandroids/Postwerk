-- Allow implicit cast from varchar (character varying) to vector
-- V26 only added text→vector but Hibernate sends varchar, not text
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_cast
        WHERE castsource = 'character varying'::regtype AND casttarget = 'vector'::regtype
    ) THEN
        CREATE CAST (character varying AS vector) WITH INOUT AS IMPLICIT;
    END IF;
END $$;
