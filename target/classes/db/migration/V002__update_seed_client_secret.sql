-- Update seeded super-user client secret to bcrypt hash for plain text: password
UPDATE public.client
SET client_secret = '$2y$10$jZu0u0/u0fQLrnl4d1n45.mKSWB9bQFCPQPz6FC0GGm7W.smJALN6'
WHERE client_id = '05d0c763-b23d-4cd5-b628-63fde9ab7227';

