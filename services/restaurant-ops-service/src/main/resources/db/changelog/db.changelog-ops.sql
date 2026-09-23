--liquibase formatted sql

--changeset food-delivery:ops-001
CREATE TABLE kitchen_tickets (
 id UUID PRIMARY KEY, order_id UUID NOT NULL, restaurant_id UUID NOT NULL, customer_id UUID NOT NULL,
 status VARCHAR(255) NOT NULL DEFAULT 'PENDING', special_instructions TEXT,
 estimated_prep_time_minutes INTEGER NOT NULL DEFAULT 0,
 received_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE,
 accepted_at TIMESTAMP WITH TIME ZONE, started_at TIMESTAMP WITH TIME ZONE, ready_at TIMESTAMP WITH TIME ZONE
);
