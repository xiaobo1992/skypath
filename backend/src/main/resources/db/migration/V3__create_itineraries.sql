CREATE TABLE itineraries (
    id                      BIGSERIAL PRIMARY KEY,

    origin_code             VARCHAR(10)   NOT NULL REFERENCES airports(code),
    destination_code        VARCHAR(10)   NOT NULL REFERENCES airports(code),
    departure_date          DATE          NOT NULL,  -- local date at origin; what the search filters on

    stop_count              SMALLINT      NOT NULL CHECK (stop_count BETWEEN 0 AND 2),

    flight_1_id             BIGINT        NOT NULL REFERENCES flights(id),
    flight_2_id             BIGINT        REFERENCES flights(id),
    flight_3_id             BIGINT        REFERENCES flights(id),

    layover_1_minutes       INT,          -- layover between flight_1 arrival and flight_2 departure
    layover_2_minutes       INT,          -- layover between flight_2 arrival and flight_3 departure

    departure_time          TIMESTAMP     NOT NULL,  -- flight_1 departure, local origin time
    arrival_time            TIMESTAMP     NOT NULL,  -- last flight's arrival, local destination time
    total_duration_minutes  INT           NOT NULL,  -- timezone-aware total, also the sort key
    total_price             NUMERIC(10,2) NOT NULL,  -- sum of segment prices

    created_at              TIMESTAMP     NOT NULL DEFAULT now(),

    CONSTRAINT chk_stop_count_matches_flights CHECK (
        (stop_count = 0 AND flight_2_id IS NULL AND flight_3_id IS NULL) OR
        (stop_count = 1 AND flight_2_id IS NOT NULL AND flight_3_id IS NULL) OR
        (stop_count = 2 AND flight_2_id IS NOT NULL AND flight_3_id IS NOT NULL)
    )
);

CREATE INDEX idx_itineraries_search
    ON itineraries (origin_code, destination_code, departure_date, total_duration_minutes);

CREATE INDEX idx_itineraries_flight_1 ON itineraries (flight_1_id);
CREATE INDEX idx_itineraries_flight_2 ON itineraries (flight_2_id);
CREATE INDEX idx_itineraries_flight_3 ON itineraries (flight_3_id);
