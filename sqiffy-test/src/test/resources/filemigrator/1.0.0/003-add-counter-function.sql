--liquibase formatted sql
--changeset test:3 splitStatements:false endDelimiter:;

-- multi-statement script with a dollar-quoted body; a naive ";" splitter would break it

CREATE TABLE counters (
    name  varchar(64) NOT NULL,
    total int         NOT NULL DEFAULT 0,
    CONSTRAINT pk_counters PRIMARY KEY (name)
);

CREATE OR REPLACE FUNCTION bump_counter(counter_name text) RETURNS int AS
$$
DECLARE
    new_total int;
BEGIN
    INSERT INTO counters(name, total) VALUES (counter_name, 1)
    ON CONFLICT (name) DO UPDATE SET total = counters.total + 1
    RETURNING total INTO new_total;
    RETURN new_total;
END;
$$ LANGUAGE plpgsql;
