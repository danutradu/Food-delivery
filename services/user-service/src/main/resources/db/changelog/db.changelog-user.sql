--liquibase formatted sql

--changeset food-delivery:user-001
CREATE TABLE users_profile (
    user_id UUID PRIMARY KEY,
    username VARCHAR(255),
    email VARCHAR(255)
);
