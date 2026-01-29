from flask import Flask, request, jsonify
import requests
import os
from azure.identity import DefaultAzureCredential

app = Flask(__name__)

FUNC_BASE = os.getenv("FUNC_BASE_URL", "").rstrip("/")
AUDIENCE  = os.getenv("FUNC_AUDIENCE", "")
SCOPE     = f"{AUDIENCE}/.default"

if not FUNC_BASE or not AUDIENCE:
    raise RuntimeError("Brakuje env: FUNC_BASE_URL i/lub FUNC_AUDIENCE")

cred = DefaultAzureCredential()

@app.route("/health")
def health():
    return {"status": "ok"}


@app.route("/debug-host")
def debug_host():
    try:
        token = cred.get_token(SCOPE).token
    except Exception as e:
        return jsonify({"stage": "get_token", "ok": False, "error": str(e)}), 500


    url = f"{FUNC_BASE}/api/odbior"      
    try:
        r = requests.post(
            url,
            json={"ping": "debug"},
            headers={
                "Authorization": f"Bearer {token}",
                "Content-Type": "application/json",
            },
            timeout=15,
        )
    except Exception as e:
        return jsonify({
            "stage": "request",
            "ok": False,
            "error": str(e),
            "url": url,
            "func_base_env": os.environ.get("FUNC_BASE_URL"),
        }), 500

    body_preview = (r.text or "")[:1000]
    return jsonify({
        "stage": "done",
        "ok": r.ok,
        "status_code": r.status_code,
        "response_headers": dict(r.headers),
        "body_preview": body_preview,
        "used_url": url,
        "func_base_env": os.environ.get("FUNC_BASE_URL"),
    }), (200 if r.ok else r.status_code)

@app.route("/token")
def token():
    try:
        t = cred.get_token(SCOPE).token
        return jsonify({"token": t[:40] + "...", "scope": SCOPE})
    except Exception as e:
        return jsonify({"error": str(e)}), 500

if __name__ == "__main__":
    app.run(debug=True, host="0.0.0.0", port=8000)
