--liquibase formatted sql

--changeset food-delivery:auth-001
CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);
CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true
);
CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id),
    role_id BIGINT NOT NULL REFERENCES roles(id),
    PRIMARY KEY (user_id, role_id)
);

--changeset food-delivery:auth-002
INSERT INTO roles (name) VALUES ('CUSTOMER'), ('RESTAURANT_OWNER'), ('COURIER'), ('ADMIN');
INSERT INTO users (id, username, email, password_hash, enabled) VALUES
 ('550e8400-e29b-41d4-a716-446655440000', 'admin', 'admin@fooddelivery.com', '$2a$10$LQK9xp2e0ZlgzkfU48bfzuZaHwm4IfUXTOGrMdsEuA/c7CUeOFOxK', true),
 ('550e8400-e29b-41d4-a716-446655440010', 'owner1', 'owner1@fooddelivery.com', '$2a$10$LQK9xp2e0ZlgzkfU48bfzuZaHwm4IfUXTOGrMdsEuA/c7CUeOFOxK', true),
 ('550e8400-e29b-41d4-a716-446655440011', 'owner2', 'owner2@fooddelivery.com', '$2a$10$LQK9xp2e0ZlgzkfU48bfzuZaHwm4IfUXTOGrMdsEuA/c7CUeOFOxK', true),
 ('550e8400-e29b-41d4-a716-446655440020', 'courier1', 'courier1@fooddelivery.com', '$2a$10$LQK9xp2e0ZlgzkfU48bfzuZaHwm4IfUXTOGrMdsEuA/c7CUeOFOxK', true),
 ('550e8400-e29b-41d4-a716-446655440022', 'courier2', 'courier2@fooddelivery.com', '$2a$10$LQK9xp2e0ZlgzkfU48bfzuZaHwm4IfUXTOGrMdsEuA/c7CUeOFOxK', true),
 ('550e8400-e29b-41d4-a716-446655440021', 'customer1', 'customer1@fooddelivery.com', '$2a$10$LQK9xp2e0ZlgzkfU48bfzuZaHwm4IfUXTOGrMdsEuA/c7CUeOFOxK', true);
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u CROSS JOIN roles r
WHERE (u.username = 'admin' AND r.name = 'ADMIN')
   OR (u.username IN ('owner1','owner2') AND r.name = 'RESTAURANT_OWNER')
   OR (u.username IN ('courier1','courier2') AND r.name = 'COURIER')
   OR (u.username = 'customer1' AND r.name = 'CUSTOMER');
