--liquibase formatted sql

--changeset food-delivery:delivery-001
CREATE TABLE couriers (id UUID PRIMARY KEY, user_id UUID NOT NULL UNIQUE, vehicle VARCHAR(100), status VARCHAR(255) NOT NULL DEFAULT 'AVAILABLE');
CREATE TABLE deliveries (id UUID PRIMARY KEY, order_id UUID NOT NULL UNIQUE, restaurant_id UUID NOT NULL, customer_id UUID NOT NULL, current_assignment_id UUID, ready_for_pickup BOOLEAN NOT NULL DEFAULT false, status VARCHAR(255) NOT NULL DEFAULT 'PENDING_ASSIGNMENT', created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(), updated_at TIMESTAMP WITH TIME ZONE);
CREATE TABLE courier_assignments (id UUID PRIMARY KEY, delivery_id UUID NOT NULL REFERENCES deliveries(id), courier_id UUID NOT NULL REFERENCES couriers(id), status VARCHAR(255) NOT NULL, offered_at TIMESTAMP WITH TIME ZONE NOT NULL, expires_at TIMESTAMP WITH TIME ZONE NOT NULL, responded_at TIMESTAMP WITH TIME ZONE, completed_at TIMESTAMP WITH TIME ZONE);
CREATE TABLE cancelled_delivery_orders (order_id UUID PRIMARY KEY, cancelled_at TIMESTAMP WITH TIME ZONE NOT NULL, reason TEXT);
CREATE TABLE ready_delivery_orders (order_id UUID PRIMARY KEY, ready_at TIMESTAMP WITH TIME ZONE);
CREATE INDEX idx_courier_assignments_delivery ON courier_assignments(delivery_id);
CREATE INDEX idx_courier_assignments_courier_status ON courier_assignments(courier_id, status);
CREATE UNIQUE INDEX idx_courier_assignments_active_delivery ON courier_assignments(delivery_id) WHERE status IN ('OFFERED', 'ACCEPTED');
CREATE UNIQUE INDEX idx_courier_assignments_active_courier ON courier_assignments(courier_id) WHERE status IN ('OFFERED', 'ACCEPTED');

--changeset food-delivery:delivery-002
INSERT INTO couriers (id,user_id,vehicle,status) VALUES ('550e8400-e29b-41d4-a716-446655440020','550e8400-e29b-41d4-a716-446655440020','bicycle','AVAILABLE'), ('550e8400-e29b-41d4-a716-446655440022','550e8400-e29b-41d4-a716-446655440022','scooter','AVAILABLE');
