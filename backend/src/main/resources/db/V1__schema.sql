-- WeBox schema migration V1 (idempotent, MySQL 8 standalone).
-- Amounts are stored as DECIMAL(10,2); the API transports integer cents end-to-end.

CREATE TABLE IF NOT EXISTS users (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  email VARCHAR(200) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  full_name VARCHAR(100) NOT NULL DEFAULT '',
  role VARCHAR(20) NOT NULL,
  created_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS refresh_tokens (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  token_hash CHAR(64) NOT NULL UNIQUE,
  expires_at DATETIME(6) NOT NULL,
  revoked TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  INDEX idx_rt_user (user_id),
  CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS preferences (
  user_id BIGINT NOT NULL PRIMARY KEY,
  allergens VARCHAR(255) NOT NULL DEFAULT '',
  cuisines VARCHAR(255) NOT NULL DEFAULT '',
  spice_level VARCHAR(20) NOT NULL DEFAULT 'None',
  taste VARCHAR(20) NOT NULL DEFAULT 'Balanced',
  budget_min DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  budget_max DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  recommend_enabled TINYINT(1) NOT NULL DEFAULT 0,
  updated_at DATETIME(6) NULL,
  CONSTRAINT fk_pref_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS dishes (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  description VARCHAR(500) NOT NULL DEFAULT '',
  price DECIMAL(10,2) NOT NULL,
  category VARCHAR(40) NOT NULL,
  protein VARCHAR(80) NOT NULL DEFAULT '',
  allergens VARCHAR(255) NOT NULL DEFAULT '',
  spice_level VARCHAR(20) NOT NULL DEFAULT 'None',
  image_url VARCHAR(300) NOT NULL DEFAULT '',
  active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  INDEX idx_dish_category (category),
  INDEX idx_dish_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS option_groups (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  dish_id BIGINT NOT NULL,
  name VARCHAR(80) NOT NULL,
  required TINYINT(1) NOT NULL DEFAULT 0,
  max_select INT NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  INDEX idx_og_dish (dish_id),
  CONSTRAINT fk_og_dish FOREIGN KEY (dish_id) REFERENCES dishes(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS option_items (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  group_id BIGINT NOT NULL,
  name VARCHAR(80) NOT NULL,
  extra_price DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  sort_order INT NOT NULL DEFAULT 0,
  INDEX idx_oi_group (group_id),
  CONSTRAINT fk_oi_group FOREIGN KEY (group_id) REFERENCES option_groups(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS daily_menu (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  menu_date DATE NOT NULL,
  dish_id BIGINT NOT NULL,
  total_qty INT NOT NULL,
  remaining_qty INT NOT NULL,
  version INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_menu_date_dish (menu_date, dish_id),
  INDEX idx_menu_date (menu_date),
  CONSTRAINT fk_menu_dish FOREIGN KEY (dish_id) REFERENCES dishes(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS orders (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_no VARCHAR(40) NOT NULL UNIQUE,
  user_id BIGINT NOT NULL,
  delivery_date DATE NOT NULL,
  meal_period VARCHAR(10) NOT NULL,
  address VARCHAR(200) NOT NULL,
  total_amount DECIMAL(10,2) NOT NULL,
  status VARCHAR(20) NOT NULL,
  idempotency_key VARCHAR(100) NULL UNIQUE,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  INDEX idx_orders_user (user_id, created_at),
  INDEX idx_orders_date_status (delivery_date, status),
  INDEX idx_orders_rule (user_id, delivery_date, meal_period, status),
  CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS order_items (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  dish_id BIGINT NOT NULL,
  dish_name VARCHAR(120) NOT NULL,
  unit_price DECIMAL(10,2) NOT NULL,
  quantity INT NOT NULL,
  options_json TEXT NULL,
  subtotal DECIMAL(10,2) NOT NULL,
  INDEX idx_order_items_order (order_id),
  CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
