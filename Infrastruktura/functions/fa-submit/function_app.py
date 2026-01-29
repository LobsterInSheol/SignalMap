import json
import os
from datetime import datetime, timezone, timedelta
import uuid
import logging
import hmac, hashlib, base64, binascii
import psycopg2
import psycopg2.extras
from psycopg2 import errors
import jwt
import azure.functions as func
from azure.functions import HttpRequest, HttpResponse
from azure.identity import DefaultAzureCredential
from azure.servicebus import ServiceBusClient, ServiceBusMessage

log = logging.getLogger("fa")
log.setLevel(logging.INFO)

# konfiguracja poziomu autoryzacji funkcji
app = func.FunctionApp(http_auth_level=func.AuthLevel.FUNCTION)

def _iso_utc(dt: datetime) -> str:
    return dt.astimezone(timezone.utc).replace(microsecond=0).isoformat()

def require_client_jwt(req: HttpRequest):
    # weryfikacja obecności i poprawności tokena JWT w nagłówku Authorization
    auth = req.headers.get("Authorization", "")
    if not auth.startswith("Bearer "):
        return None, HttpResponse(json.dumps({"error": "jwt_missing"}), status_code=401, mimetype="application/json")

    token = auth.split(" ", 1)[1].strip()
    key = os.environ["JWT_SIGNING_KEY"]

    try:
        payload = jwt.decode(
            token,
            key,
            algorithms=["HS256"],
            audience="apim-inzynierka",
            issuer="fa-submit-broker",
            leeway=30 # margines czasu na synchronizację zegarów
        )
        return payload, None
    except jwt.ExpiredSignatureError:
        return None, HttpResponse(json.dumps({"error": "jwt_expired"}), status_code=401, mimetype="application/json")
    except Exception:
        return None, HttpResponse(json.dumps({"error": "jwt_invalid"}), status_code=401, mimetype="application/json")

@app.route(route="gettoken", methods=["GET"], auth_level=func.AuthLevel.FUNCTION)
def gettoken(req: HttpRequest) -> HttpResponse:
    sc_input = req.headers.get("X-Short-Code")
    
    if not sc_input:
        return HttpResponse(json.dumps({"error": "short_code_required"}), status_code=400, mimetype="application/json")

    # sprawdzenie czy użytkownik o podanym kodzie istnieje w bazie
    try:
        with _pg_conn() as cx:
            with cx.cursor() as cur:
                cur.execute("SELECT 1 FROM public.viewer WHERE short_code = %s", (sc_input.strip(),))
                exists = cur.fetchone()
                
        if not exists:
            return HttpResponse(json.dumps({"error": "invalid_short_code"}), status_code=403, mimetype="application/json")
            
    except Exception as e:
        return HttpResponse(json.dumps({"error": "database_error", "detail": str(e)}), status_code=500, mimetype="application/json")

    signing_key = os.environ["JWT_SIGNING_KEY"]
    now = datetime.now(timezone.utc)
    exp = now + timedelta(minutes=15)

    # budowa struktury tokena dla klienta
    payload = {
        "iss": "fa-submit-broker",
        "aud": "apim-inzynierka",
        "iat": int(now.timestamp()),
        "exp": int(exp.timestamp()),
        "sub": sc_input.strip() 
    }

    token = jwt.encode(payload, signing_key, algorithm="HS256")

    return HttpResponse(
        json.dumps({
            "access_token": token,
            "expires_in": 15 * 60,
            "expires_at": int(exp.timestamp()),
        }),
        status_code=200,
        mimetype="application/json",
        headers={"Cache-Control": "no-store"}
    )

@app.route(route="submit", methods=["POST"])
def submit(req: HttpRequest) -> HttpResponse:
    try:
        _, err = require_client_jwt(req)
        if err: return err

        src = json.loads(req.get_body() or "{}")

        # pobieranie danych bez restrykcji co do formatu kluczy
        operator = str(src.get("operator", "")).strip()
        network_type = str(src.get("networkType") or src.get("network_type") or "").strip()
        signal = src.get("signal")
        pos = src.get("position") or {}
        lat = src.get("lat", pos.get("lat"))
        lon = src.get("lon", pos.get("lon"))
        senttime = src.get("sentTime") or src.get("send_time")
        idem = src.get("idempotencyKey") or str(uuid.uuid4())

        # walidacja wymaganych parametrów technicznych
        if not operator or not network_type or not isinstance(signal, int):
            return HttpResponse(json.dumps({"error": "invalid_telemetry_data"}), status_code=400, mimetype="application/json")

        # walidacja zakresu współrzędnych geograficznych
        try:
            lat, lon = float(lat), float(lon)
            if not (-90 <= lat <= 90 and -180 <= lon <= 180): raise ValueError()
        except:
            return HttpResponse(json.dumps({"error": "invalid_position"}), status_code=400, mimetype="application/json")

        # standaryzacja czasu do formatu ISO UTC
        try:
            sendtime_iso = _iso_utc(datetime.fromisoformat(senttime.replace("Z", "+00:00"))) if senttime else _iso_utc(datetime.now(timezone.utc))
        except:
            return HttpResponse(json.dumps({"error": "invalid_time_format"}), status_code=400, mimetype="application/json")

        ns = os.environ.get("SB_NAMESPACE")
        queue = os.environ.get("SB_QUEUE")

        # przygotowanie paczki danych do wysłania na kolejkę
        msg_body = dict(src)
        msg_body.update({
            "operator": operator,
            "network_type": network_type,
            "signal": signal,
            "lat": lat,
            "lon": lon,
            "send_time": sendtime_iso,
            "received_at": _iso_utc(datetime.now(timezone.utc))
        })

        # użycie Managed Identity do autoryzacji w Service Bus
        credential = DefaultAzureCredential()
        sb_client = ServiceBusClient(fully_qualified_namespace=ns, credential=credential)

        with sb_client:
            sender = sb_client.get_queue_sender(queue_name=queue)
            with sender:
                msg = ServiceBusMessage(json.dumps(msg_body))
                msg.message_id = str(idem) # ochrona przed duplikatami na poziomie kolejki
                sender.send_messages(msg)

        return HttpResponse(json.dumps({"status": "queued"}), status_code=202, mimetype="application/json")

    except Exception as e:
        return HttpResponse(json.dumps({"error": "enqueue_failed"}), status_code=500, mimetype="application/json")

@app.route(route="speedtest", methods=["POST"])
def speedtest_result(req: HttpRequest) -> HttpResponse:
    try:
        _, err = require_client_jwt(req)
        if err: return err

        src = json.loads(req.get_body() or "{}")
        pos = src.get("position") or {}
        
        # przygotowanie danych pomiarowych do zapisu
        msg_body = {
            "kind": "speedtest",
            "shortCode": src.get("shortCode"),
            "latencyMs": src.get("latencyMs"),
            "downloadMbps": src.get("downloadMbps"),
            "uploadMbps": src.get("uploadMbps"),
            "jitterMs": src.get("jitterMs"),
            "lat": src.get("lat", pos.get("lat")),
            "lon": src.get("lon", pos.get("lon")),
            "operator": src.get("operator"),
            "sentTime": src.get("sentTime"),
            "received_at": _iso_utc(datetime.now(timezone.utc)),
            "raw": src
        }

        credential = DefaultAzureCredential()
        sb_client = ServiceBusClient(fully_qualified_namespace=os.environ["SB_NAMESPACE"], credential=credential)

        with sb_client:
            sender = sb_client.get_queue_sender(queue_name=os.environ["SB_QUEUE"])
            with sender:
                msg = ServiceBusMessage(json.dumps(msg_body))
                msg.message_id = src.get("idempotencyKey") or str(uuid.uuid4())
                sender.send_messages(msg)

        return HttpResponse(json.dumps({"status": "queued"}), status_code=202, mimetype="application/json")
    except Exception as e:
        return HttpResponse(json.dumps({"error": "speedtest_enqueue_failed"}), status_code=500, mimetype="application/json")

@app.route(route="speedtest/upload", methods=["POST"], auth_level=func.AuthLevel.FUNCTION)
def speedtest_upload(req: HttpRequest) -> HttpResponse:
    try:
        # punkt końcowy służący tylko do mierzenia prędkości wysyłania (odbiera i odrzuca body)
        max_bytes = int(os.getenv("SPEEDTEST_MAX_UPLOAD_BYTES", "20971520"))
        body = req.get_body()
        n = len(body)

        if n == 0: return HttpResponse("Empty body", status_code=400)
        if n > max_bytes: return HttpResponse("Payload too large", status_code=413)

        return HttpResponse(str(n), status_code=200, headers={"X-Bytes-Received": str(n)})
    except Exception as e:
        return HttpResponse("Error", status_code=500)

@app.route(route="speedtest/ping", methods=["GET"], auth_level=func.AuthLevel.FUNCTION)
def speedtest_ping(req: func.HttpRequest) -> func.HttpResponse:
    # szybka odpowiedź dla testu opóźnień
    return func.HttpResponse("OK", status_code=200, headers={"Cache-Control": "no-store"})

def _pg_conn():
    # tworzenie połączenia z bazą Postgres (obsługuje URL lub parametry pojedyncze)
    url = os.getenv("PG_CONN")
    if url: return psycopg2.connect(url, connect_timeout=10)
    return psycopg2.connect(
        host=os.environ["PGHOST"],
        user=os.environ["PGUSER"],
        password=os.environ["PGPASSWORD"],
        dbname=os.environ["PGDATABASE"],
        port=int(os.getenv("PGPORT", "5432")),
        sslmode="require"
    )

def viewer_key_from_android_id(android_id: str) -> bytes:
    # generowanie unikalnego klucza urządzenia przy użyciu HMAC i pieprzu
    aid = android_id.strip().lower().encode("utf-8")
    return hmac.new(binascii.unhexlify(os.environ["PEPPER_HEX"]), aid, hashlib.sha256).digest()

def short_code_4x3(vk: bytes) -> str:
    # tworzenie czytelnego kodu XXXX-XXXX-XXXX z sumą kontrolną
    raw12 = base64.b32encode(vk[:8]).decode("ascii").rstrip("=")[:12]
    data11 = raw12[:11]
    s = sum("ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".index(c) for c in data11) % 32
    return f"{data11[0:4]}-{data11[4:8]}-{data11[8:11]}{'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'[s]}"

@app.route(route="register", methods=["POST"], auth_level=func.AuthLevel.FUNCTION)
def register(req: HttpRequest) -> HttpResponse:
    try:
        body = json.loads(req.get_body() or "{}")
        android_id = str(body.get("androidId", "")).strip().lower()

        # walidacja formatu technicznego ANDROID_ID (16 znaków hex)
        if len(android_id) != 16 or not all(c in "0123456789abcdef" for c in android_id):
            return HttpResponse(json.dumps({"error": "invalid_android_id"}), status_code=400, mimetype="application/json")

        vk = viewer_key_from_android_id(android_id)
        sc = short_code_4x3(vk)

        with _pg_conn() as cx:
            with cx.cursor(cursor_factory=psycopg2.extras.DictCursor) as cur:
                # sprawdzenie czy klucz już istnieje (idempotencja)
                cur.execute("SELECT short_code FROM public.viewer WHERE viewer_key = %s", (psycopg2.Binary(vk),))
                row = cur.fetchone()
                if row: return HttpResponse(json.dumps({"shortCode": row["short_code"]}), status_code=200)

                # próba rejestracji nowego urządzenia
                try:
                    cur.execute("INSERT INTO public.viewer(viewer_key, short_code) VALUES (%s, %s) RETURNING short_code", (psycopg2.Binary(vk), sc))
                    return HttpResponse(json.dumps({"shortCode": cur.fetchone()["short_code"]}), status_code=201)
                except errors.UniqueViolation:
                    cx.rollback()
                    cur.execute("SELECT short_code FROM public.viewer WHERE viewer_key = %s", (psycopg2.Binary(vk),))
                    return HttpResponse(json.dumps({"shortCode": cur.fetchone()["short_code"]}), status_code=200)

    except Exception as ex:
        return HttpResponse(json.dumps({"error": "register_failed"}), status_code=500)