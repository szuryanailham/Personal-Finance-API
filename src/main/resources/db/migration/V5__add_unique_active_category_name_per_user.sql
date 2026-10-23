CREATE UNIQUE INDEX uq_categories_user_name_type_active
    ON categories (user_id, LOWER(name), type)
    WHERE is_deleted = FALSE;
