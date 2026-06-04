--liquibase formatted sql
--changeset test:2 splitStatements:false endDelimiter:;

ALTER TABLE users ADD COLUMN email varchar(128);
