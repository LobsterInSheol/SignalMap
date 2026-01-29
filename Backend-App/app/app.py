# app.py - Backend Flask dla SignalMap
import os
import time
import threading
import signal
from datetime import datetime, timedelta
from typing import Optional
from flask import Flask, jsonify, request
from flask_cors import CORS
import psycopg2
from psycopg2.pool import SimpleConnectionPool

# Konfiguracja z zmiennych środowiskowych (CSI secrets z Key Vault)
HOST = os.getenv("PGHOST", "localhost")
DB = os.getenv("PGDATABASE", "postgres")
USER = os.getenv("PGUSER", "postgres")
PWD = os.getenv("PGPASSWORD", "")
PORT = int(os.getenv("PGPORT", "5432"))
SSLM = os.getenv("PGSSLMODE", "require")
POLL_INTERVAL = int(os.getenv("POLL_INTERVAL", "60"))

app = Flask(__name__)
# CORS wyłączony - frontend i backend w tej samej domenie
# CORS_ORIGIN = os.getenv("CORS_ORIGIN", "*")
# CORS(app, resources={r"/api/*": {"origins": CORS_ORIGIN}})

_last_ok: Optional[datetime] = None
_stop = threading.Event()
_pool: Optional[SimpleConnectionPool] = None


def get_conn():
    """Pobiera połączenie z puli"""
    assert _pool is not None, "DB pool not initialized"
    return _pool.getconn()


def put_conn(conn):
    """Zwraca połączenie do puli"""
    if _pool and conn:
        _pool.putconn(conn)


def poll_loop():
    """Wątek monitorujący połączenie z bazą"""
    global _last_ok
    while not _stop.is_set():
        try:
            conn = get_conn()
            with conn, conn.cursor() as cur:
                cur.execute("SELECT 1;")
                cur.fetchone()
            put_conn(conn)
            _last_ok = datetime.utcnow()
        except Exception as e:
            print(f"[POLL] error: {e}", flush=True)
        _stop.wait(POLL_INTERVAL)


@app.route("/healthz")
def healthz():
    """Liveness probe"""
    return jsonify(status="ok"), 200


@app.route("/ready")
def ready():
    """Readiness probe"""
    if _last_ok and _last_ok > datetime.utcnow() - timedelta(minutes=2):
        return jsonify(ready=True, last_ok=_last_ok.isoformat() + "Z"), 200
    return jsonify(ready=False, last_ok=_last_ok.isoformat() + "Z" if _last_ok else None), 503


@app.route("/api/telemetry")
def api_telemetry():
    """
    Endpoint zwracający pełne dane telemetryczne.
    Query params:
      - minutes (int, default 1440): dane z ostatnich X minut (domyślnie 24h)
      - limit (int, default 100000): max liczba rekordów
      - operator (str, optional): filtruj po operatorze
    """
    minutes = int(request.args.get("minutes", "1440"))
    limit = int(request.args.get("limit", "100000"))
    operator_filter = request.args.get("operator")

    sql = """
SELECT
  id,
  operator,
  network_type,  
  signal,
  ST_Y(position::geometry) AS latitude,
  ST_X(position::geometry) AS longitude,
  send_time,     
  rat,
  nr_mode,
  band,
  arfcn,
  rsrp,
  rsrq,
  sinr,
  rssi,
  timing_advance,
  pci,
  eci,
  nci,
  cell_id,
  enb,
  sector_id,
  tac,
  lac
FROM telemetry
WHERE send_time >= now() - interval %s
    """
    params = [f"{minutes} minutes"]

    if operator_filter:
        sql += " AND operator = %s"
        params.append(operator_filter)

    sql += " ORDER BY send_time DESC LIMIT %s"
    params.append(limit)

    items = []
    try:
        conn = get_conn()
        with conn, conn.cursor() as cur:
            cur.execute(sql, tuple(params))
            for r in cur.fetchall():
                items.append({
                    "id": r[0],
                    "operator": r[1],
                    "networkType": r[2],
                    "signal": int(r[3]),
                    "latitude": float(r[4]),
                    "longitude": float(r[5]),
                    "sendTime": r[6].isoformat(),
                    "position": [float(r[5]), float(r[4])],
                    "rat": r[7],
                    "nrMode": r[8],
                    "band": r[9],
                    "arfcn": r[10],
                    "rsrp": r[11],
                    "rsrq": r[12],
                    "sinr": r[13],
                    "rssi": r[14],
                    "timingAdvance": r[15],
                    "pci": r[16],
                    "eci": r[17],
                    "nci": r[18],
                    "cellId": r[19],
                    "enb": r[20],
                    "sectorId": r[21],
                    "tac": r[22],
                    "lac": r[23]
                })
        put_conn(conn)
    except Exception as e:
        print(f"[API ERROR] /api/telemetry: {e}", flush=True)
        return jsonify(error=str(e)), 500
    
    return jsonify(items=items)


@app.route("/api/bts")
def api_bts():
    """
    Endpoint zwracający stacje bazowe BTS.
    Query params:
      - operator (str, optional): filtruj po operatorze (siec_id)
      - limit (int, default 1000): max liczba rekordów
    """
    limit = int(request.args.get("limit", "1000"))
    operator_filter = request.args.get("operator")
    
    sql = """
SELECT
    id,
    operator,           
    voivodeship,        
    town,               
    location,           
    network_type,       
    band,               
    duplex,
    lac,
    btsid,
    ecid,
    enbi,
    clid,
    comments,           
    lat,
    lon,
    updated_at,         
    station_id,
    rnc,
    carrier
  FROM bts              
  WHERE lat IS NOT NULL AND lon IS NOT NULL
    """
    params = []
    
    if operator_filter:
        sql += " AND operator = %s"
        params.append(operator_filter)
    
    sql += " LIMIT %s"
    params.append(limit)
    
    items = []
    try:
        conn = get_conn()
        with conn, conn.cursor() as cur:
            cur.execute(sql, tuple(params))
            for r in cur.fetchall():
                items.append({
                    "id": r[0],
                    "siecId": r[1],          
                    "wojewodztwoId": r[2],   
                    "miejscowosc": r[3],     
                    "lokalizacja": r[4],      
                    "standard": r[5],        
                    "pasmo": r[6],            
                    "duplex": r[7],
                    "lac": r[8],              
                    "btsid": r[9],
                    "ecid": r[10],
                    "enbi": r[11],
                    "clid": r[12],
                    "uwagi": r[13],           
                    "lat": float(r[14]) if r[14] else None,
                    "lon": float(r[15]) if r[15] else None,
                    "aktualizacja": r[16].isoformat() if r[16] else None,  
                    "stationId": r[17],
                    "rnc": r[18],
                    "carrier": r[19]
                })
        put_conn(conn)
    except Exception as e:
        print(f"[API ERROR] /api/bts: {e}", flush=True)
        return jsonify(error=str(e)), 500
    
    return jsonify(items=items)


@app.route("/api/speedtest")
def api_speedtest():
    """
    Endpoint zwracający dane speedtestów z tabeli speed_test.
    Query params:
      - limit (int, default 5000): max liczba rekordów
      - minutes (int, optional): dane z ostatnich X minut
      - operator (str, optional): filtruj po operatorze
      - short_code (str, optional): filtruj po short_code (identyfikator urządzenia)
      - start_date (str, optional): początek zakresu czasowego (ISO format)
      - end_date (str, optional): koniec zakresu czasowego (ISO format)
    """
    limit = int(request.args.get("limit", "5000"))
    minutes = request.args.get("minutes")
    operator_filter = request.args.get("operator")
    short_code_filter = request.args.get("short_code")
    start_date = request.args.get("start_date")
    end_date = request.args.get("end_date")

    sql = """
SELECT
  id,
  short_code,
  operator,
  download_mbps,
  upload_mbps,
  latency_ms,
  jitter_ms,
  ST_Y(position::geometry) AS latitude,
  ST_X(position::geometry) AS longitude,
  send_time        
FROM speed_test    
WHERE position IS NOT NULL
    """
    params = []

    # Filtr czasu - domyslnie pobiera ostatnie 30 dni jeśli nie podano nic
    if start_date:
        sql += " AND send_time >= %s"
        params.append(start_date)
    elif minutes:
        sql += " AND send_time >= now() - interval %s"
        params.append(f"{minutes} minutes")
    else:
        # domyslnie pobiera ostatnie 30 dni
        sql += " AND send_time >= now() - interval '30 days'"

    if end_date:
        sql += " AND send_time <= %s"
        params.append(end_date)

    # Filtr operatora
    if operator_filter:
        sql += " AND operator = %s"
        params.append(operator_filter)

    # Filtr short_code (device identifier z Android app)
    if short_code_filter:
        sql += " AND short_code = %s"
        params.append(short_code_filter)

    sql += " ORDER BY send_time DESC LIMIT %s"
    params.append(limit)

    items = []
    try:
        conn = get_conn()
        with conn, conn.cursor() as cur:
            cur.execute(sql, tuple(params))
            rows = cur.fetchall()
            print(f"[API] Znaleziono {len(rows)} speedtestów", flush=True)
            
            for r in rows:
                items.append({
                    "id": r[0],
                    "operator": r[2] if r[2] else "Nieznany",
                    "downloadSpeed": float(r[3]) if r[3] else 0,
                    "uploadSpeed": float(r[4]) if r[4] else 0,
                    "ping": float(r[5]) if r[5] else 0,
                    "jitter": float(r[6]) if r[6] else None,
                    "latitude": float(r[7]) if r[7] else None,
                    "longitude": float(r[8]) if r[8] else None,
                    "timestamp": r[9].isoformat() if r[9] else None,
                    "position": [float(r[8]), float(r[7])] if r[7] and r[8] else None
                })
        put_conn(conn)
        
        print(f"[API] Zwracam {len(items)} speedtestów", flush=True)
        
    except Exception as e:
        print(f"[API ERROR] /api/speedtest: {e}", flush=True)
        return jsonify(error=str(e)), 500
    
    return jsonify(items=items)


@app.route("/api/telemetry-with-bts")
def api_telemetry_with_bts():
    minutes = int(request.args.get("minutes", "1440"))
    limit = int(request.args.get("limit", "100000"))
    short_code_filter = request.args.get("short_code")
    start_date = request.args.get("start_date")
    end_date = request.args.get("end_date")

    items = []
    enb_list = set()
    umts_pairs = set()   # (rnc, btsid_from_cell)
    gsm_btsids = set()
    conn = None

    try:
        conn = get_conn()

        # ========================================
        # KROK 1: Pobieranie danych telemetrycznych
        # ========================================
        sql_telemetry = """
SELECT
  id, operator, network_type, signal,
  ST_Y(position::geometry) AS latitude,
  ST_X(position::geometry) AS longitude,
  send_time, rat, nr_mode, band, arfcn, rsrp, rsrq, sinr, rssi, timing_advance,
  pci, eci, nci, cell_id, enb, sector_id, tac, lac
FROM telemetry
WHERE 1=1
        """
        params = []

        # Filtr czasu: start_date > minutes (fallback)
        if start_date:
            sql_telemetry += " AND send_time >= %s"
            params.append(start_date)
        else:
            sql_telemetry += " AND send_time >= now() - interval %s"
            params.append(f"{minutes} minutes")

        if end_date:
            sql_telemetry += " AND send_time <= %s"
            params.append(end_date)

        # Filtr short_code (device identifier)
        if short_code_filter:
            sql_telemetry += " AND short_code = %s"
            params.append(short_code_filter)

        sql_telemetry += " ORDER BY send_time DESC LIMIT %s"
        params.append(limit)
        
        print(f"[SQL] Wykonuję zapytanie telemetry z {len(params)} parametrami", flush=True)
        
        with conn.cursor() as cur:
            cur.execute(sql_telemetry, tuple(params))
            rows = cur.fetchall()
            print(f"[SQL] Pobrano {len(rows)} rekordów telemetrii", flush=True)
            
            for r in rows:
                item = {
                    "id": r[0],
                    "operator": r[1],
                    "networkType": r[2],
                    "signal": int(r[3]),
                    "latitude": float(r[4]),
                    "longitude": float(r[5]),
                    "sendTime": r[6].isoformat(),
                    "position": [float(r[5]), float(r[4])],
                    "rat": r[7],
                    "nrMode": r[8],
                    "band": r[9],
                    "arfcn": r[10],
                    "rsrp": r[11],
                    "rsrq": r[12],
                    "sinr": r[13],
                    "rssi": r[14],
                    "timingAdvance": r[15],
                    "pci": r[16],
                    "eci": r[17],
                    "nci": r[18],
                    "cellId": r[19],
                    "enb": r[20],
                    "sectorId": r[21],
                    "tac": r[22],
                    "lac": r[23]
                }
                items.append(item)

                nt = (item["networkType"] or "").lower()
                cell_id_val = item["cellId"]

                if item["enb"]:  # LTE/4G
                    enb_list.add(item["enb"])

                # UMTS/3G: cell_id = RNC * 65536 + CID; btsid = CID without last digit
                if any(k in nt for k in ["3g"]):
                    if cell_id_val is not None:
                        try:
                            rnc_val = int(cell_id_val) // 65536
                            cid_val = int(cell_id_val) - rnc_val * 65536
                            btsid_val = cid_val // 10  # drop last digit
                            umts_pairs.add((rnc_val, btsid_val))
                        except Exception:
                            pass

                # GSM/2G: btsid = cell_id without last digit
                if any(k in nt for k in ["gsm", "2g"]):
                    if cell_id_val is not None:
                        try:
                            btsid_val = int(cell_id_val) // 10
                            gsm_btsids.add(btsid_val)
                            print(f"[DEBUG] GSM: cell_id={cell_id_val} -> btsid={btsid_val}, lac={item.get('lac')}", flush=True)
                        except Exception as e:
                            print(f"[DEBUG] GSM error: {e}", flush=True)
        
        print(f"[DEBUG] Zebrano: enb={len(enb_list)}, umts_pairs={len(umts_pairs)}, gsm_btsids={len(gsm_btsids)}", flush=True)
        print(f"[DEBUG] gsm_btsids={list(gsm_btsids)[:10]}", flush=True)
        
        # ========================================
        # KROK 2: Pobieranie BTS dla znalezionych enb
        # ========================================
        bts_by_enb = {}
        bts_by_umts = {}  # key: (rnc, btsid)
        bts_by_gsm = {}   # key: btsid

        where_parts = []
        bts_params = []

        if enb_list:
            enb_list_tuple = tuple(enb_list)
            placeholders_enb = ','.join(['%s'] * len(enb_list_tuple))
            where_parts.append(f"enbi IN ({placeholders_enb})")
            bts_params.extend(enb_list_tuple)

        if umts_pairs:
            pair_clauses = []
            for rnc_val, btsid_val in umts_pairs:
                pair_clauses.append("(rnc = %s AND btsid = %s)")
                bts_params.extend([rnc_val, str(btsid_val)])
            where_parts.append('(' + ' OR '.join(pair_clauses) + ')')

        if gsm_btsids:
            gsm_tuple = tuple(str(btsid) for btsid in gsm_btsids)
            placeholders_gsm = ','.join(['%s'] * len(gsm_tuple))
            where_parts.append(f"btsid IN ({placeholders_gsm})")
            bts_params.extend(gsm_tuple)

        if where_parts:
            sql_bts = f"""
SELECT
  id, operator, voivodeship, town, location,
  network_type, band, duplex, btsid, enbi, comments,
  lat, lon, updated_at, station_id, rnc, carrier, lac
FROM bts
WHERE ({' OR '.join(where_parts)})
  AND lat IS NOT NULL
  AND lon IS NOT NULL
            """
            
            print(f"[SQL] Wykonuję zapytanie BTS dla filtrów: enb={len(enb_list)}, umts_pairs={len(umts_pairs)}, gsm={len(gsm_btsids)}", flush=True)
            
            with conn.cursor() as cur:
                cur.execute(sql_bts, tuple(bts_params))
                rows = cur.fetchall()
                print(f"[SQL] Pobrano {len(rows)} stacji BTS", flush=True)
                
                for r in rows:
                    entry = {
                        "id": r[0],
                        "siecId": r[1],
                        "wojewodztwoId": r[2],
                        "miejscowosc": r[3],
                        "lokalizacja": r[4],
                        "standard": r[5],
                        "pasmo": r[6],
                        "duplex": r[7],
                        "btsid": r[8],
                        "enbi": r[9],
                        "uwagi": r[10],
                        "lat": float(r[11]) if r[11] else None,
                        "lon": float(r[12]) if r[12] else None,
                        "aktualizacja": r[13].isoformat() if r[13] else None,
                        "stationId": r[14],
                        "rnc": r[15],
                        "carrier": r[16],
                        "lac": r[17]
                    }

                    if entry["enbi"] is not None:
                        bts_by_enb.setdefault(entry["enbi"], []).append(entry)

                    if entry["rnc"] is not None and entry["btsid"] is not None:
                        bts_by_umts.setdefault((entry["rnc"], entry["btsid"]), []).append(entry)

                    if entry["btsid"] is not None:
                        bts_by_gsm.setdefault(entry["btsid"], []).append(entry)
        
        print(f"[DEBUG] bts_by_gsm keys: {list(bts_by_gsm.keys())[:10]}", flush=True)
        print(f"[DEBUG] bts_by_enb={len(bts_by_enb)}, bts_by_umts={len(bts_by_umts)}, bts_by_gsm={len(bts_by_gsm)}", flush=True)
        
        # ========================================
        # KROK 3: Dopasowanie BTS do pomiarów
        # ========================================
        for item in items:
            nt = (item["networkType"] or "").lower()

            def pick_best(candidates):
                best = None
                best_dist = float('inf')
                for bts in candidates:
                    if item["lac"] and bts.get("lac") and item["lac"] != bts["lac"]:
                        continue
                    if bts.get("lat") is not None and bts.get("lon") is not None:
                        dist = ((item["latitude"] - bts["lat"])**2 + (item["longitude"] - bts["lon"])**2)**0.5
                        if dist < 0.15 and dist < best_dist:
                            best_dist = dist
                            best = bts
                return best

            # LTE/4G: dopasowanie po eNB
            if item["enb"] and item["enb"] in bts_by_enb:
                cand = bts_by_enb[item["enb"]]
                best = pick_best(cand)
                if best:
                    item["relatedBts"] = best
                continue

            # UMTS/3G: cell_id -> RNC + CID, btsid = CID bez ostatniej cyfry
            if any(k in nt for k in ["3g"]):
                cell_val = item.get("cellId")
                if cell_val is not None:
                    try:
                        rnc_val = int(cell_val) // 65536
                        cid_val = int(cell_val) - rnc_val * 65536
                        btsid_val = cid_val // 10
                        key = (rnc_val, str(btsid_val))
                        if key in bts_by_umts:
                            best = pick_best(bts_by_umts[key])
                            if best:
                                item["relatedBts"] = best
                                continue
                    except Exception:
                        pass

            # GSM/2G: btsid = cell_id bez ostatniej cyfry
            if any(k in nt for k in ["gsm", "2g"]):
                cell_val = item.get("cellId")
                if cell_val is not None:
                    try:
                        btsid_val = int(cell_val) // 10
                        # Klucz w słowniku używa str(btsid), więc tutaj też musi być string
                        if str(btsid_val) in bts_by_gsm:
                            best = pick_best(bts_by_gsm[str(btsid_val)])
                            if best:
                                item["relatedBts"] = best
                                continue
                    except Exception:
                        pass
        
        put_conn(conn)
        
        matched_count = sum(1 for item in items if "relatedBts" in item)
        unmatched_count = len(items) - matched_count

        unique_bts_ids = set()
        for lst in bts_by_enb.values():
            for e in lst:
                unique_bts_ids.add(e["id"])
        for lst in bts_by_umts.values():
            for e in lst:
                unique_bts_ids.add(e["id"])
        for lst in bts_by_gsm.values():
            for e in lst:
                unique_bts_ids.add(e["id"])

        print(f"[API] Dopasowane: {matched_count}, Bez BTS: {unmatched_count}")
        print(f"[API] Zwrócono {len(items)} pomiarów, {len(unique_bts_ids)} unikalnych BTS, {matched_count} dopasowań", flush=True)
        
    except Exception as e:
        print(f"[API ERROR] /api/telemetry-with-bts: {e}", flush=True)
        import traceback
        traceback.print_exc()
        return jsonify(error=str(e)), 500
    
    return jsonify(items=items)


def handle_term(signum, frame):
    """Obsługa SIGTERM/SIGINT dla graceful shutdown"""
    print("[SHUTDOWN] Otrzymano sygnał, zatrzymuję...", flush=True)
    _stop.set()


def main():
    global _last_ok, _pool
    
    print(f"[STARTUP] Łączenie z bazą: {USER}@{HOST}:{PORT}/{DB}", flush=True)
    
    # Inicjalizacja connection pool
    try:
        _pool = SimpleConnectionPool(
            minconn=2,
            maxconn=10,
            host=HOST,
            dbname=DB,
            user=USER,
            password=PWD,
            port=PORT,
            sslmode=SSLM,
            connect_timeout=10
        )
        print("[STARTUP] Connection pool utworzony", flush=True)
    except Exception as e:
        print(f"[STARTUP] BŁĄD: Nie można utworzyć connection pool: {e}", flush=True)
        raise

    # Test połączenia przy starcie
    for attempt in range(10):
        try:
            conn = get_conn()
            with conn, conn.cursor() as cur:
                cur.execute("SELECT 1;")
                cur.fetchone()
            put_conn(conn)
            _last_ok = datetime.utcnow()
            print("[STARTUP] Test połączenia OK", flush=True)
            break
        except Exception as e:
            print(f"[STARTUP] Próba {attempt + 1}/10 - błąd połączenia: {e}", flush=True)
            time.sleep(3)
    else:
        print("[STARTUP] OSTRZEŻENIE: Nie udało się połączyć z bazą po 10 próbach", flush=True)

    # Uruchom wątek monitorujący
    t = threading.Thread(target=poll_loop, daemon=True)
    t.start()
    print("[STARTUP] Wątek monitorujący uruchomiony", flush=True)

    # Obsługa sygnałów
    signal.signal(signal.SIGTERM, handle_term)
    signal.signal(signal.SIGINT, handle_term)

    # Start Flask
    print("[STARTUP] Uruchamiam Flask na porcie 8080", flush=True)
    app.run(host="0.0.0.0", port=8080, threaded=True)


if __name__ == "__main__":
    main()