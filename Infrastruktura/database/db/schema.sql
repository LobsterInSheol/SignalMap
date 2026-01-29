
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_raster;
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;


CREATE TABLE IF NOT EXISTS public.viewer (
    id BIGSERIAL PRIMARY KEY,
    viewer_key BYTEA NOT NULL UNIQUE,
    short_code TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_viewer_short_code ON public.viewer(short_code);

CREATE TABLE IF NOT EXISTS public.bts (
    id BIGINT NOT NULL PRIMARY KEY,
    operator TEXT NOT NULL,
    voivodeship TEXT,
    town TEXT,
    location TEXT,
    network_type TEXT,
    band INTEGER,
    duplex TEXT,
    lac INTEGER,
    btsid TEXT,
    ecid BIGINT,
    enbi INTEGER,
    clid INTEGER,
    comments TEXT,
    lat NUMERIC(9,6),
    lon NUMERIC(9,6),
    updated_at DATE,
    rnc INTEGER,
    carrier TEXT,
    station_id BIGINT
);
CREATE INDEX IF NOT EXISTS bts_ecid_idx ON public.bts(ecid);
CREATE INDEX IF NOT EXISTS bts_operator_enbi_idx ON public.bts(operator, enbi);


CREATE TABLE IF NOT EXISTS public.telemetry (
    id BIGSERIAL PRIMARY KEY,
    operator TEXT NOT NULL,
    network_type TEXT NOT NULL,
    signal SMALLINT NOT NULL,
    position GEOGRAPHY(Point, 4326) NOT NULL,
    send_time TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    short_code TEXT NOT NULL,
    rat TEXT,
    nr_mode TEXT,
    band TEXT,
    arfcn INTEGER,
    rsrp INTEGER,
    rsrq INTEGER,
    sinr INTEGER,
    rssi INTEGER,
    timing_advance INTEGER,
    pci INTEGER,
    eci BIGINT,
    nci BIGINT,
    cell_id BIGINT,
    enb INTEGER,
    sector_id INTEGER,
    tac INTEGER,
    lac INTEGER,
    operator_norm4 TEXT,
    CONSTRAINT telemetry_short_code_fk FOREIGN KEY (short_code) REFERENCES public.viewer(short_code),
    CONSTRAINT chk_operator_norm4 CHECK (operator_norm4 = ANY (ARRAY['Orange'::text, 'Play'::text, 'Plus'::text, 'T-Mobile'::text, 'Unknown'::text])),
    CONSTRAINT telemetry_signal_check CHECK (signal >= -150 AND signal <= 0)
);

CREATE INDEX IF NOT EXISTS telemetry_position_gix ON public.telemetry USING GIST (position);
CREATE INDEX IF NOT EXISTS telemetry_short_code_send_time_idx ON public.telemetry(short_code, send_time);
CREATE INDEX IF NOT EXISTS telemetry_operator_enb_idx ON public.telemetry(operator, enb);

CREATE TABLE IF NOT EXISTS public.speed_test (
    id BIGSERIAL PRIMARY KEY,
    short_code TEXT NOT NULL,
    latency_ms BIGINT NOT NULL,
    jitter_ms BIGINT NOT NULL,
    download_mbps DOUBLE PRECISION NOT NULL,
    upload_mbps DOUBLE PRECISION NOT NULL,
    send_time TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ DEFAULT now() NOT NULL,
    position GEOGRAPHY(Point, 4326),
    operator TEXT,
    CONSTRAINT speed_test_short_code_fk FOREIGN KEY (short_code) REFERENCES public.viewer(short_code)
);
CREATE INDEX IF NOT EXISTS speed_test_position_gix ON public.speed_test USING GIST (position);
CREATE INDEX IF NOT EXISTS speed_test_short_code_send_time_idx ON public.speed_test(short_code, send_time);