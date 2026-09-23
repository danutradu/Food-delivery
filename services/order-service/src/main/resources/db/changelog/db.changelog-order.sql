--liquibase formatted sql

--changeset food-delivery:order-001
CREATE TABLE orders (
 id UUID PRIMARY KEY, cart_id UUID NOT NULL, customer_id UUID NOT NULL, restaurant_id UUID NOT NULL,
 total NUMERIC(19,2) NOT NULL, status VARCHAR(255) NOT NULL DEFAULT 'PENDING',
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(), updated_at TIMESTAMP WITH TIME ZONE
);
CREATE TABLE order_items (
 id UUID PRIMARY KEY, order_id UUID NOT NULL REFERENCES orders(id), menu_item_id UUID NOT NULL,
 name VARCHAR(255) NOT NULL, unit_price NUMERIC(19,2) NOT NULL, quantity INTEGER NOT NULL
);
