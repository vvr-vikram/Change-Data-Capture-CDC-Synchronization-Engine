-- Create Domain Tables
CREATE TABLE customers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    phone VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    price DECIMAL(10, 2) NOT NULL,
    sku VARCHAR(50) UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT REFERENCES customers(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE inventory (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT REFERENCES products(id) ON DELETE CASCADE,
    quantity INT NOT NULL,
    location VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT REFERENCES orders(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL,
    payment_method VARCHAR(50) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    transaction_id VARCHAR(100) UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create CDC Tracking & Status Tables
CREATE TABLE cdc_sync_status (
    event_id VARCHAR(100) PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL, -- PENDING, PROCESSED, FAILED
    error_message TEXT,
    retry_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE cdc_audit_log (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    op VARCHAR(10) NOT NULL, -- CREATE, UPDATE, DELETE
    payload TEXT,
    status VARCHAR(50) NOT NULL,
    error_message TEXT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE failed_events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL,
    topic VARCHAR(100) NOT NULL,
    partition_id INT NOT NULL,
    offset_val BIGINT NOT NULL,
    payload TEXT NOT NULL,
    error_message TEXT,
    failed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved BOOLEAN DEFAULT FALSE
);

-- Create User Table for JWT Authentication
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL -- ADMIN, OPERATOR
);

-- Seed Domain Data with Indian Names
INSERT INTO customers (name, email, phone) VALUES
('Vikram Singh', 'vikram@example.com', '9876543210'),
('Bala Krishnan', 'bala@example.com', '9876543211'),
('Magesh Kumar', 'magesh@example.com', '9876543212'),
('Ananya Rao', 'ananya@example.com', '9876543213');

INSERT INTO products (name, description, price, sku) VALUES
('Laptop', 'High performance developer laptop', 75000.00, 'LAP-001'),
('Smartphone', 'Flagship mobile phone', 45000.00, 'MOB-001'),
('Headphones', 'Noise cancelling wireless headphones', 12000.00, 'AUD-001');

INSERT INTO inventory (product_id, quantity, location) VALUES
(1, 50, 'Warehouse-Mumbai'),
(2, 100, 'Warehouse-Bangalore'),
(3, 200, 'Warehouse-Chennai');

INSERT INTO orders (customer_id, status, total_amount) VALUES
(1, 'PENDING', 75000.00),
(2, 'COMPLETED', 45000.00);

INSERT INTO payments (order_id, status, payment_method, amount, transaction_id) VALUES
(2, 'SUCCESS', 'CREDIT_CARD', 45000.00, 'TXN-998877');
