--liquibase formatted sql
--changeset test:1 splitStatements:false endDelimiter:;

CREATE TABLE users (
    id   serial      NOT NULL,
    name varchar(64) NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id)
);
