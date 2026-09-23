--liquibase formatted sql

--changeset food-delivery:cart-001
CREATE TABLE carts (id UUID PRIMARY KEY, restaurant_id UUID NOT NULL, customer_id UUID NOT NULL);
CREATE TABLE cart_items (
 id UUID PRIMARY KEY, cart_id UUID NOT NULL REFERENCES carts(id), menu_item_id UUID NOT NULL,
 name VARCHAR(255) NOT NULL, unit_price NUMERIC(19,2) NOT NULL, quantity INTEGER NOT NULL
);
CREATE TABLE checkout_requests (
 id UUID PRIMARY KEY, customer_id UUID NOT NULL, cart_id UUID NOT NULL UNIQUE
);
