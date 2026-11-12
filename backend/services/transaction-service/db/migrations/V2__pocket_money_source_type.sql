-- V2 (release v0.1.6): "Pocket money" as an income source type.
ALTER TABLE income_source DROP CONSTRAINT ck_income_source_type;
ALTER TABLE income_source ADD CONSTRAINT ck_income_source_type CHECK (type IN (
    'SALARY', 'BUSINESS', 'FREELANCE', 'INVESTMENT', 'RENTAL', 'PENSION', 'GIFT', 'POCKET_MONEY', 'OTHER'));
