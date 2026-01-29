CREATE ROLE pgmetrics LOGIN PASSWORD '{hasło}';

GRANT pg_monitor TO pgmetrics;
GRANT pg_read_all_stats TO pgmetrics;
GRANT pg_read_all_settings TO pgmetrics;

GRANT CONNECT ON DATABASE inzdb TO pgmetrics;

CREATE EXTENSION IF NOT EXISTS pg_stat_statements;