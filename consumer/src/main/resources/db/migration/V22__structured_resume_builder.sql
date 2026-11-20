ALTER TABLE resumes
    ADD COLUMN IF NOT EXISTS resume_data_json TEXT;

ALTER TABLE resume_optimizations
    ADD COLUMN IF NOT EXISTS language VARCHAR(8);

ALTER TABLE resume_optimizations
    ADD COLUMN IF NOT EXISTS document_type VARCHAR(32);

ALTER TABLE resume_optimizations
    ADD COLUMN IF NOT EXISTS resume_data_json TEXT;
