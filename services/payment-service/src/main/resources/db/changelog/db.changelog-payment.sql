--liquibase formatted sql

--changeset food-delivery:payment-001
CREATE TABLE payments (
 id UUID PRIMARY KEY, order_id UUID NOT NULL UNIQUE, amount NUMERIC(19,2) NOT NULL,
 status VARCHAR(255) NOT NULL DEFAULT 'PENDING', created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
 updated_at TIMESTAMP WITH TIME ZONE, authorization_code VARCHAR(255), failure_reason TEXT
);
