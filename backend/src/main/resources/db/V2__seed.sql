-- WeBox seed migration V2 (idempotent: safe on every restart).
-- Demo accounts (password hashes are PBKDF2-HMAC-SHA256, never plaintext):
--   admin@webox.com    / Admin@12345   (ADMIN)
--   employee@webox.com / Employee@123  (EMPLOYEE)

INSERT IGNORE INTO users (id, email, password_hash, full_name, role, created_at) VALUES
 (1, 'admin@webox.com',    'pbkdf2$120000$MCPMxl+lRYyeNxhzvmGI+Q==$IAYcjXgLWhJUtjuoKrnbBLS52uE7I4jeXnPE+OXdh/4=', 'Canteen Admin', 'ADMIN',    NOW(6)),
 (2, 'employee@webox.com', 'pbkdf2$120000$CtD2a3FhUyl4i9VesYU67w==$Ra91bO9AUE2YUJbkY+jUylBRB5BIKjpJsglZmzBVXjc=', 'Demo Employee', 'EMPLOYEE', NOW(6));

INSERT IGNORE INTO preferences (user_id, allergens, cuisines, spice_level, taste, budget_min, budget_max, recommend_enabled, updated_at) VALUES
 (2, 'Peanuts', 'Chinese,Light Meal', 'Mild', 'Balanced', 15.00, 35.00, 1, NOW(6));

-- 30 dishes (the PRD set plus extra catalogue items to exercise paging at scale).
INSERT IGNORE INTO dishes (id, name, description, price, category, protein, allergens, spice_level, image_url, active, created_at, updated_at) VALUES
 (1,'Kung Pao Chicken','Classic Sichuan dish, chicken stir-fried with peanuts and dried chili',22.00,'Chinese','Chicken','Peanuts','Medium','/images/image_01.jpg',1,NOW(6),NOW(6)),
 (2,'Caesar Salad','Fresh romaine lettuce with Parmesan and Caesar dressing',28.50,'Light Meal','None','Dairy,Egg','None','/images/image_02.jpg',1,NOW(6),NOW(6)),
 (3,'Salmon Sashimi Set','Fresh salmon sashimi with rice and miso soup',45.00,'Japanese','Fish','Fish','None','/images/image_03.jpg',1,NOW(6),NOW(6)),
 (4,'Tomato Pasta','Classic Italian tomato pasta with fresh basil',26.50,'Western','None','Gluten','None','/images/image_04.jpg',1,NOW(6),NOW(6)),
 (5,'Tom Yum Soup','Thai hot and sour shrimp soup with lemongrass, galangal, lime leaves',32.00,'Southeast Asian','Shrimp','Shellfish','Hot','/images/image_05.jpg',1,NOW(6),NOW(6)),
 (6,'Chicken Quinoa Bowl','Low-fat high-protein grilled chicken with quinoa, avocado, veggies',35.80,'Light Meal','Chicken','','None','/images/image_06.jpg',1,NOW(6),NOW(6)),
 (7,'Mapo Tofu','Sichuan classic, soft tofu with spicy minced meat',18.00,'Chinese','Tofu,Pork','Soy','Medium','/images/image_07.jpg',1,NOW(6),NOW(6)),
 (8,'Korean Bibimbap','Stone-pot mixed rice with veggies, fried egg, and chili sauce',30.00,'Korean','Egg','Egg,Soy','Mild','/images/image_08.jpg',1,NOW(6),NOW(6)),
 (9,'Classic Beef Burger','Angus beef patty with lettuce, tomato, onion',38.00,'Western','Beef','Gluten,Dairy','None','/images/image_09.jpg',1,NOW(6),NOW(6)),
 (10,'Grilled Chicken Sandwich','Chargrilled chicken breast with lettuce in a toasted bun',32.00,'Western','Chicken','Gluten,Dairy','None','/images/image_10.jpg',1,NOW(6),NOW(6)),
 (11,'Beef Pho','Vietnamese rice noodle soup with slow-cooked beef and herbs',34.00,'Southeast Asian','Beef','Fish','Mild','/images/image_11.jpg',1,NOW(6),NOW(6)),
 (12,'Vegetarian Ramen','Rich miso broth with seasonal vegetables and noodles',29.00,'Japanese','None','Gluten,Soy','Mild','/images/image_12.jpg',1,NOW(6),NOW(6)),
 (13,'Spicy Tofu Bowl','Wok-tossed tofu with chili oil and steamed rice',24.00,'Chinese','Tofu','Soy','Hot','/images/image_13.jpg',1,NOW(6),NOW(6)),
 (14,'Greek Yogurt Parfait','Creamy Greek yogurt with berries, granola and honey',18.00,'Light Meal','None','Dairy','None','/images/image_14.jpg',1,NOW(6),NOW(6)),
 (15,'Kimchi Fried Rice','Wok-fried rice with fermented kimchi and a sunny egg',27.00,'Korean','Egg','Egg,Soy','Medium','/images/image_15.jpg',1,NOW(6),NOW(6)),
 (16,'Shrimp Pad Thai','Stir-fried rice noodles with shrimp, crushed peanuts and lime',36.00,'Southeast Asian','Shrimp','Shellfish,Peanuts,Egg','Mild','/images/image_16.jpg',1,NOW(6),NOW(6)),
 (17,'Mushroom Risotto','Creamy arborio rice with wild mushrooms and Parmesan',39.00,'Western','None','Dairy','None','/images/image_17.jpg',1,NOW(6),NOW(6)),
 (18,'Teriyaki Salmon Bowl','Glazed salmon fillet over rice with steamed greens',42.00,'Japanese','Fish','Fish,Soy','None','/images/image_18.jpg',1,NOW(6),NOW(6)),
 (19,'Braised Pork Rice','Slow-braised pork belly with soy glaze over jasmine rice',25.00,'Chinese','Pork','Soy','None','/images/image_19.jpg',1,NOW(6),NOW(6)),
 (20,'Chicken Caesar Wrap','Romaine, grilled chicken and Caesar dressing in a tortilla',26.00,'Western','Chicken','Gluten,Dairy,Egg','None','/images/image_20.jpg',1,NOW(6),NOW(6)),
 (21,'Miso Glazed Eggplant','Roasted eggplant with sweet miso glaze and sesame',22.00,'Japanese','None','Soy','None','/images/image_01.jpg',1,NOW(6),NOW(6)),
 (22,'Thai Green Curry','Coconut green curry with chicken, basil and jasmine rice',33.00,'Southeast Asian','Chicken','Fish','Hot','/images/image_02.jpg',1,NOW(6),NOW(6)),
 (23,'Bulgogi Beef Bowl','Marinated grilled beef with rice and seasoned vegetables',38.00,'Korean','Beef','Soy','Mild','/images/image_03.jpg',1,NOW(6),NOW(6)),
 (24,'Quinoa Avocado Salad','Chilled quinoa, avocado, cherry tomato and lemon dressing',31.00,'Light Meal','None','','None','/images/image_04.jpg',1,NOW(6),NOW(6)),
 (25,'Sweet and Sour Pork','Crispy pork in a tangy pineapple sauce',28.00,'Chinese','Pork','Gluten,Soy','None','/images/image_05.jpg',1,NOW(6),NOW(6)),
 (26,'Smoked Salmon Bagel','Toasted bagel with cream cheese, smoked salmon and dill',35.00,'Western','Fish','Fish,Gluten,Dairy','None','/images/image_06.jpg',1,NOW(6),NOW(6)),
 (27,'Lentil Soup','Hearty lentil and vegetable soup with toasted sourdough',21.00,'Light Meal','None','','None','/images/image_07.jpg',1,NOW(6),NOW(6)),
 (28,'Szechuan Noodles','Chili sesame noodles with crushed peanuts and scallions',23.00,'Chinese','None','Gluten,Peanuts,Soy','Hot','/images/image_08.jpg',1,NOW(6),NOW(6)),
 (29,'Tuna Poke Bowl','Fresh tuna, edamame, cucumber and sushi rice',40.00,'Japanese','Fish','Fish,Soy','None','/images/image_09.jpg',1,NOW(6),NOW(6)),
 (30,'Roast Chicken Plate','Herb roasted chicken with mashed potato and gravy',36.00,'Western','Chicken','Dairy','None','/images/image_10.jpg',1,NOW(6),NOW(6));

-- Customization groups. required=1 means the employee must pick exactly one.
INSERT IGNORE INTO option_groups (id, dish_id, name, required, max_select, sort_order) VALUES
 (1,2,'Add-ons',0,3,1),
 (2,4,'Pasta Type',1,1,1),
 (3,4,'Add-ons',0,2,2),
 (4,6,'Base',1,1,1),
 (5,8,'Add-ons',0,3,1),
 (6,9,'Bun',1,1,1),
 (7,9,'Sauce',1,1,2),
 (8,9,'Add-ons',0,3,3);

INSERT IGNORE INTO option_items (id, group_id, name, extra_price, sort_order) VALUES
 (1,1,'Grilled Chicken',6.00,1),(2,1,'Bacon',5.00,2),(3,1,'Avocado',4.00,3),
 (4,2,'Spaghetti',0.00,1),(5,2,'Fusilli',0.00,2),(6,2,'Penne',0.00,3),
 (7,3,'Bacon',5.00,1),(8,3,'Cheese',3.00,2),
 (9,4,'Quinoa',0.00,1),(10,4,'Brown Rice',0.00,2),(11,4,'Mixed Grains',0.00,3),
 (12,5,'Cheese',3.00,1),(13,5,'Fried Egg',2.00,2),(14,5,'Beef Slices',8.00,3),
 (15,6,'Plain',0.00,1),(16,6,'Whole Wheat',0.00,2),
 (17,7,'Ketchup',0.00,1),(18,7,'Mustard',0.00,2),(19,7,'Mayo',0.00,3),(20,7,'BBQ',0.00,4),
 (21,8,'Cheese',3.00,1),(22,8,'Bacon',5.00,2),(23,8,'Fried Egg',2.00,3);

-- Today AND tomorrow are orderable out of the box (demo uses today).
INSERT IGNORE INTO daily_menu (menu_date, dish_id, total_qty, remaining_qty, version)
 SELECT CURDATE(), id, 12, 12, 0 FROM dishes WHERE active = 1;
INSERT IGNORE INTO daily_menu (menu_date, dish_id, total_qty, remaining_qty, version)
 SELECT DATE_ADD(CURDATE(), INTERVAL 1 DAY), id, 15, 15, 0 FROM dishes WHERE active = 1;

-- Deterministic low-stock / sold-out examples for the demo (only before any sale).
UPDATE daily_menu SET total_qty = 3, remaining_qty = 3
 WHERE menu_date = CURDATE() AND dish_id IN (4, 9) AND total_qty = 12 AND remaining_qty = 12;
UPDATE daily_menu SET total_qty = 6, remaining_qty = 0
 WHERE menu_date = CURDATE() AND dish_id = 5 AND total_qty = 12 AND remaining_qty = 12;
