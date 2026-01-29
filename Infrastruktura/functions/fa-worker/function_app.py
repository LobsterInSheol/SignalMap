import json, os, logging, traceback
from typing import List, Tuple, Any
from datetime import datetime, timezone
import azure.functions as func
import psycopg
import re

app = func.FunctionApp()
log = logging.getLogger("fa")
log.setLevel(logging.INFO)

PG_CONN = os.getenv("PG_CONN")
BATCH_SIZE = 500

# maskowanie poufnych danych w logach
def _mask(s: str | None, keep: int = 6) -> str:
    if not s: return "<EMPTY>"
    return s[:keep] + "… (len=" + str(len(s)) + ")"

# bezpieczne wyciąganie treści wiadomości z kolejki
def _safe_parse(msg: func.ServiceBusMessage) -> Tuple[dict | None, str | None, str]:
    try:
        body = msg.get_body()
        if isinstance(body, (bytes, bytearray)):
            body = body.decode("utf-8", errors="replace")
        obj = json.loads(body)
        return obj, None, body[:300]
    except Exception as e:
        prev = (str(body)[:300] if 'body' in locals() else "<no body>")
        return None, f"parse_error: {e}", prev

def _chunks(lst, n):
    for i in range(0, len(lst), n):
        yield lst[i:i+n]

def _iso_utc(dt: datetime) -> str:
    return dt.astimezone(timezone.utc).replace(microsecond=0).isoformat()

def _to_int_or_none(v):
    try:
        if v is None or v == "" or isinstance(v, bool): return None
        return int(v)
    except (TypeError, ValueError): return None

# oczyszczanie tekstu z nadmiarowych znaków i spacji
def _norm_text(s: str | None) -> str:
    if not s: return ""
    s = s.strip().lower()
    s = re.sub(r"[#._\-]+", " ", s)
    s = re.sub(r"\s+", " ", s)
    return s

# rozdzielanie nazwy operatora od nazwy w nawiasie (np. Roaming)
def _split_brand_paren(op_raw: str) -> tuple[str, str | None]:
    s = (op_raw or "").strip()
    m = re.match(r"^(.*?)\s*\((.*?)\)\s*$", s)
    if m: return m.group(1).strip(), m.group(2).strip()
    return s, None

# dopasowanie tekstu do jednego z głównych operatorów (MNO)
def _map_to_mno4(s_norm: str) -> str:
    if not s_norm: return "Unknown"
    if re.search(r"\bt\s*mobile\b", s_norm) or "tmobile" in s_norm: return "T-Mobile"
    if "orange" in s_norm: return "Orange"
    if re.search(r"\bplay\b", s_norm): return "Play"
    if re.search(r"\bplus\b", s_norm) or "plush" in s_norm: return "Plus"
    return "Unknown"

# logika rozpoznawania czyj to nadajnik (RAN) na podstawie nazwy operatora
def _map_operator_norm4_ran(op_raw: str | None) -> str:
    if not op_raw or not str(op_raw).strip(): return "Unknown"
    brand, paren = _split_brand_paren(str(op_raw))
    if paren:
        res = _map_to_mno4(_norm_text(paren))
        if res != "Unknown": return res
    b = _norm_text(brand)
    if "vectra" in b: return "Play"
    return _map_to_mno4(b)

# przygotowanie danych telemetrycznych do zapisu w bazie
def _normalize_row(raw: dict) -> Tuple[dict | None, str | None]:
    try:
        operator = (raw.get("operator") or "").strip()
        operator_norm4 = _map_operator_norm4_ran(operator)

        # elastyczne pobieranie typu sieci i sygnału
        network_type = str(raw.get("network_type") or raw.get("networkType") or "").strip()
        signal = raw.get("signal")

        pos = raw.get("position") or {}
        lat = raw.get("lat", pos.get("lat"))
        lon = raw.get("lon", pos.get("lon"))

        st = raw.get("send_time") or raw.get("sentTime")
        if st:
            try: st_dt = datetime.fromisoformat(str(st).replace("Z", "+00:00"))
            except: return None, "send_time must be ISO-8601"
        else: st_dt = datetime.now(timezone.utc)
        sendtime = _iso_utc(st_dt)

        if not operator or not network_type or isinstance(signal, bool):
            return None, "missing or invalid core fields"

        lat, lon = float(lat), float(lon)
        if not (-90.0 <= lat <= 90.0 and -180.0 <= lon <= 180.0):
            return None, "position out of range"

        return {
            "operator": operator,
            "operator_norm4": operator_norm4,
            "network_type": network_type,
            "signal": int(signal),
            "lat": lat, "lon": lon,
            "send_time": sendtime,
            "short_code": (raw.get("short_code") or raw.get("shortCode") or "").strip() or None,
            "rat": (raw.get("rat") or raw.get("networkType") or "").strip() or None,
            "nr_mode": (raw.get("nr_mode") or raw.get("nrMode") or "").strip() or None,
            "band": (raw.get("band") or "").strip() or None,
            "arfcn": _to_int_or_none(raw.get("arfcn")),
            "rsrp": _to_int_or_none(raw.get("rsrp")),
            "rsrq": _to_int_or_none(raw.get("rsrq")),
            "sinr": _to_int_or_none(raw.get("sinr")),
            "rssi": _to_int_or_none(raw.get("rssi")),
            "timing_advance": _to_int_or_none(raw.get("timing_advance") or raw.get("timingAdvance")),
            "pci": _to_int_or_none(raw.get("pci")),
            "eci": _to_int_or_none(raw.get("eci")),
            "nci": _to_int_or_none(raw.get("nci")),
            "cell_id": _to_int_or_none(raw.get("cell_id") or raw.get("cellId")),
            "enb": _to_int_or_none(raw.get("enb")),
            "sector_id": _to_int_or_none(raw.get("sector_id") or raw.get("sectorId")),
            "tac": _to_int_or_none(raw.get("tac")),
            "lac": _to_int_or_none(raw.get("lac")),
        }, None
    except Exception as e: return None, f"normalize_error: {e}"

# przygotowanie danych speedtestu do zapisu
def _normalize_speedtest(raw: dict) -> Tuple[dict | None, str | None]:
    try:
        short_code = (raw.get("shortCode") or raw.get("short_code") or "").strip() or None
        sent = raw.get("sentTime") or raw.get("send_time")
        pos = raw.get("position") or {}
        lat = raw.get("lat", pos.get("lat"))
        lon = raw.get("lon", pos.get("lon"))

        if sent:
            try: dt = datetime.fromisoformat(str(sent).replace("Z", "+00:00"))
            except: return None, "sentTime must be ISO-8601"
        else: dt = datetime.now(timezone.utc)

        return {
            "short_code": short_code,
            "latency_ms": _to_int_or_none(raw.get("latencyMs")),
            "jitter_ms": _to_int_or_none(raw.get("jitterMs")),
            "download_mbps": raw.get("downloadMbps"),
            "upload_mbps": raw.get("uploadMbps"),
            "send_time": _iso_utc(dt),
            "received_at": _iso_utc(datetime.now(timezone.utc)),
            "lat": float(lat) if lat is not None else None,
            "lon": float(lon) if lon is not None else None,
            "operator": (raw.get("operator") or "").strip() or None,
        }, None
    except Exception as e: return None, f"normalize_speedtest_error: {e}"

@app.service_bus_queue_trigger(
    arg_name="messages",
    queue_name="%SB_QUEUE%",
    connection="ServiceBus",
    cardinality="many",
)
def worker(messages: List[func.ServiceBusMessage]):
    tel_ready, speed_ready = [], []
    
    for m in messages:
        obj, err, _ = _safe_parse(m)
        if err: continue

        # rozpoznawanie typu wiadomości po polu 'kind'
        if obj.get("kind") == "speedtest":
            norm, nerr = _normalize_speedtest(obj)
            if not nerr: speed_ready.append(norm)
        else:
            norm, nerr = _normalize_row(obj)
            if not nerr: tel_ready.append(norm)

    if not tel_ready and not speed_ready: return

    # szablony SQL z obsługą typu Point (geografia)
    sql_tel = """INSERT INTO telemetry (operator, operator_norm4, network_type, signal, position, send_time, short_code, rat, nr_mode, band, arfcn, rsrp, rsrq, sinr, rssi, timing_advance, pci, eci, nci, cell_id, enb, sector_id, tac, lac) 
                 VALUES ({})"""
    
    # konwersja lat/lon na natywny punkt geograficzny PostGIS
    tel_row_sql = "(%s,%s,%s,%s, ST_SetSRID(ST_MakePoint(%s,%s),4326)::geography, %s::timestamptz, %s,%s,%s,%s,%s, %s,%s,%s,%s,%s, %s,%s,%s,%s,%s, %s,%s,%s)"

    sql_speed = """INSERT INTO speed_test (short_code, latency_ms, jitter_ms, download_mbps, upload_mbps, send_time, position, operator) 
                   VALUES ({})"""
    
    speed_row_sql = "(%s,%s,%s,%s,%s, %s::timestamptz, CASE WHEN %s IS NOT NULL THEN ST_SetSRID(ST_MakePoint(%s,%s),4326)::geography ELSE NULL END, %s)"

    try:
        # otwarcie połączenia i transakcji do bazy danych
        with psycopg.connect(PG_CONN, autocommit=False) as conn:
            with conn.cursor() as cur:
                # masowy zapis danych telemetrycznych
                for chunk in _chunks(tel_ready, BATCH_SIZE):
                    vals = []
                    for r in chunk:
                        vals.extend([r["operator"], r["operator_norm4"], r["network_type"], r["signal"], r["lon"], r["lat"], r["send_time"], r["short_code"], r["rat"], r["nr_mode"], r["band"], r["arfcn"], r["rsrp"], r["rsrq"], r["sinr"], r["rssi"], r["timing_advance"], r["pci"], r["eci"], r["nci"], r["cell_id"], r["enb"], r["sector_id"], r["tac"], r["lac"]])
                    cur.execute(sql_tel.format(",".join([tel_row_sql] * len(chunk))), vals)

                # masowy zapis wyników speedtestów
                for chunk in _chunks(speed_ready, BATCH_SIZE):
                    vals = []
                    for r in chunk:
                        vals.extend([r["short_code"], r["latency_ms"], r["jitter_ms"], r["download_mbps"], r["upload_mbps"], r["send_time"], r["lon"], r["lon"], r["lat"], r["operator"]])
                    cur.execute(sql_speed.format(",".join([speed_row_sql] * len(chunk))), vals)
            
            # zatwierdzenie wszystkich zmian w jednej transakcji
            conn.commit()
    except Exception as e:
        log.error("[DB] BŁĄD ZAPISU: %s", str(e))
        raise