--liquibase formatted sql

--changeset food-delivery:catalog-001
CREATE TABLE restaurants (
    id UUID PRIMARY KEY, name VARCHAR(255) NOT NULL, address VARCHAR(500) NOT NULL,
    open BOOLEAN NOT NULL DEFAULT true, owner_user_id UUID NOT NULL
);
CREATE TABLE menu_items (
    id UUID PRIMARY KEY, restaurant_id UUID NOT NULL REFERENCES restaurants(id), section_id UUID,
    name VARCHAR(255) NOT NULL, description TEXT, price NUMERIC(19,2) NOT NULL,
    available BOOLEAN NOT NULL DEFAULT true, version INTEGER NOT NULL DEFAULT 0
);

--changeset food-delivery:catalog-002
INSERT INTO restaurants (id,name,address,open,owner_user_id) VALUES
 ('550e8400-e29b-41d4-a716-446655440001','Pizza Palace','123 Main St, Food City',true,'550e8400-e29b-41d4-a716-446655440010'),
 ('550e8400-e29b-41d4-a716-446655440002','Burger Barn','456 Oak Ave, Food City',true,'550e8400-e29b-41d4-a716-446655440011');
INSERT INTO menu_items (id,restaurant_id,name,description,price,available,version) VALUES
 ('550e8400-e29b-41d4-a716-446655440101','550e8400-e29b-41d4-a716-446655440001','Margherita Pizza','Fresh mozzarella, tomato sauce, basil',12.99,true,0),
 ('550e8400-e29b-41d4-a716-446655440102','550e8400-e29b-41d4-a716-446655440001','Pepperoni Pizza','Pepperoni, mozzarella, tomato sauce',14.99,true,0),
 ('550e8400-e29b-41d4-a716-446655440201','550e8400-e29b-41d4-a716-446655440002','Classic Burger','Beef patty, lettuce, tomato, onion',8.99,true,0),
 ('550e8400-e29b-41d4-a716-446655440202','550e8400-e29b-41d4-a716-446655440002','Cheese Fries','Crispy fries with melted cheese',5.99,true,0);
