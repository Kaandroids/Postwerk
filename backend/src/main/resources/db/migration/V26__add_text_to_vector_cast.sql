-- Allow implicit cast from text/varchar to vector (needed for Hibernate JPA string converter)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_cast
        WHERE castsource = 'text'::regtype AND casttarget = 'vector'::regtype
    ) THEN
        CREATE CAST (text AS vector) WITH INOUT AS IMPLICIT;
    END IF;
END $$;
