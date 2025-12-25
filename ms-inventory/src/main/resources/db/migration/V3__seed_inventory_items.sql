INSERT INTO inventory_items (product_id, on_hand, reserved, updated_at) VALUES
('P-0001', 120, 0, now()),
('P-0002',  80, 0, now()),
('P-0003',  50, 0, now()),
('P-0004',  35, 0, now()),
('P-0005', 200, 0, now()),
('P-0006',  15, 0, now()),
('P-0007',  60, 0, now()),
('P-0008',  90, 0, now()),
('P-0009',  25, 0, now()),
('P-0010',  10, 0, now()),
('P-0011', 300, 0, now()),
('P-0012',  40, 0, now()),
('P-0013',  75, 0, now()),
('P-0014',  55, 0, now()),
('P-0015',  20, 0, now()),
('P-0016', 150, 0, now()),
('P-0017',   8, 0, now()),
('P-0018', 110, 0, now()),
('P-0019',  65, 0, now()),
('P-0020',  95, 0, now())
ON CONFLICT (product_id)
DO UPDATE SET
    on_hand = EXCLUDED.on_hand,
    reserved = EXCLUDED.reserved,
    updated_at = EXCLUDED.updated_at;
