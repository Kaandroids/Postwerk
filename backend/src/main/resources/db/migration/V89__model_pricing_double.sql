-- The ModelPricing entity maps its rate fields as Java `double` (Hibernate FLOAT8), but V88 created
-- them as NUMERIC — which fails Hibernate's ddl-auto=validate (expects float(53)). Convert the columns
-- to double precision so the schema matches the entity. (V88 is already applied, so this is a forward
-- migration rather than an edit to V88.)
ALTER TABLE model_pricing
    ALTER COLUMN input_per_million TYPE double precision USING input_per_million::double precision,
    ALTER COLUMN output_per_million TYPE double precision USING output_per_million::double precision;
