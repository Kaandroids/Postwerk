ALTER TABLE templates ADD COLUMN parameter_set_id UUID REFERENCES parameter_sets(id) ON DELETE SET NULL;
CREATE INDEX idx_templates_parameter_set_id ON templates(parameter_set_id);
