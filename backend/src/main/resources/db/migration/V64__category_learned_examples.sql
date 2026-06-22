-- Supervised mode (#3c): corrections from the approval inbox teach a category. The email text of a
-- misclassified message is appended here (newline-separated) and folded into the category's
-- embedding, so classification improves from real human feedback over time.
ALTER TABLE categories
    ADD COLUMN learned_examples TEXT;
