ALTER TABLE t_region
    ADD COLUMN province VARCHAR(50);

UPDATE t_region
SET province = '서울'
WHERE province IS NULL;

ALTER TABLE t_region
    ALTER COLUMN province SET NOT NULL;
