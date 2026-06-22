-- Assign PRO plan to k.kara@duahotel.de
UPDATE users SET plan_id = (SELECT id FROM plans WHERE name = 'PRO')
  WHERE email = 'k.kara@duahotel.de' AND deleted_at IS NULL;
