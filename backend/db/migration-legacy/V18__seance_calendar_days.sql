CREATE TABLE IF NOT EXISTS center_holiday
(
    id        UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    day_date  DATE NOT NULL,
    label     VARCHAR(255),
    UNIQUE (center_id, day_date)
);

CREATE TABLE IF NOT EXISTS center_closure_day
(
    id        UUID PRIMARY KEY,
    center_id UUID NOT NULL,
    day_date  DATE NOT NULL,
    reason    VARCHAR(255),
    UNIQUE (center_id, day_date)
);

CREATE INDEX IF NOT EXISTS idx_center_holiday_center_date ON center_holiday (center_id, day_date);
CREATE INDEX IF NOT EXISTS idx_center_closure_center_date ON center_closure_day (center_id, day_date);

