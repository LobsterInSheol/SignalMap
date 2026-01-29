```bash
C:\Users\Jakub\Desktop\Inzynierka> kubectl -n frontend exec -it deploy/frontend -- sh -lc `  'curl -iS --max-time 10 "http://backend.backend.svc/healthz"'kubectl -n frontend exec -it deploy/frontend -- sh -lc `  'curl -iS --max-time 10 "http://backend.backend.svc/api/telemetry/kepler?minutes=5256000&limit=10"'
HTTP/1.1 200 OK
Server: Werkzeug/3.1.3 Python/3.12.3
Date: Sun, 19 Oct 2025 18:26:42 GMT
Content-Type: application/json
Content-Length: 16
Connection: close

{"status":"ok"}
PS C:\Users\Jakub\Desktop\Inzynierka> kubectl -n frontend exec -it deploy/frontend -- sh -lc `  'curl -iS --max-time 10 "http://backend.backend.svc/api/telemetry/kepler?minutes=5256000&limit=10"'
PS C:\Users\Jakub\Desktop\Inzynierka> kubectl -n frontend exec -it frontend-849667bb8-wgfrn -- sh -lc "wget -qO- 'http://backend.backend.svc/api/telemetry/kepler?minutes=5256000&limit=10' | head -c 10000; echo"
{"fields":[{"name":"id","type":"integer"},{"name":"operator","type":"string"},{"name":"networkType","type":"string"},{"name":"signal","type":"integer"},{"name":"latitude","type":"real"},{"name":"longitude","type":"real"},{"name":"sendTime","type":"timestamp"}],"rows":[[85,"T-Mobile","LTE",-75,52.2297,21.0122,"2025-10-14T16:24:51.494384+00:00"],[84,"Plus","LTE",-98,51.6449933,17.1660292,"2025-09-18T15:26:18+00:00"],[83,"T-Mobile","LTE",-59,37.4219983,-122.084,"2025-09-18T15:23:29+00:00"],[82,"T-Mobile","LTE",-44,37.4219983,-122.084,"2025-09-18T15:22:58+00:00"],[81,"T-Mobile","LTE",-116,37.4219983,-122.084,"2025-09-18T15:22:27+00:00"]]}

PS C:\Users\Jakub\Desktop\Inzynierka> kubectl -n frontend exec -it deploy/frontend -- sh -lc `  "wget -qO- 'http://backend.backend.svc/api/telemetry/kepler?minutes=5256000&limit=10' | head -c 400; echo"
{"fields":[{"name":"id","type":"integer"},{"name":"operator","type":"string"},{"name":"networkType","type":"string"},{"name":"signal","type":"integer"},{"name":"latitude","type":"real"},{"name":"longitude","type":"real"},{"name":"sendTime","type":"timestamp"}],"rows":[[85,"T-Mobile","LTE",-75,52.2297,21.0122,"2025-10-14T16:24:51.494384+00:00"],[84,"Plus","LTE",-98,51.6449933,17.1660292,"2025-09-18
PS C:\Users\Jakub\Desktop\Inzynierka> kubectl -n frontend exec -it deploy/frontend -- sh -lc `  'curl -iS --max-time 10 "http://127.0.0.1/api/telemetry/kepler?minutes=5256000&limit=10"'
PS C:\Users\Jakub\Desktop\Inzynierka> kubectl -n frontend exec -it deploy/frontend -- sh -lc `  "wget -qO- 'http://backend.backend.svc/api/telemetry/kepler?minutes=5256000&limit=10' | head -c 400; echo"
{"fields":[{"name":"id","type":"integer"},{"name":"operator","type":"string"},{"name":"networkType","type":"string"},{"name":"signal","type":"integer"},{"name":"latitude","type":"real"},{"name":"longitude","type":"real"},{"name":"sendTime","type":"timestamp"}],"rows":[[85,"T-Mobile","LTE",-75,52.2297,21.0122,"2025-10-14T16:24:51.494384+00:00"],[84,"Plus","LTE",-98,51.6449933,17.1660292,"2025-09-18
PS C:\Users\Jakub\Desktop\Inzynierka> kubectl -n frontend exec -it deploy/frontend -- sh -lc `  "wget -qO- 'http://127.0.0.1/api/telemetry/kepler?minutes=5256000&limit=10' | head -c 400; echo"          
{"fields":[{"name":"id","type":"integer"},{"name":"operator","type":"string"},{"name":"networkType","type":"string"},{"name":"signal","type":"integer"},{"name":"latitude","type":"real"},{"name":"longitude","type":"real"},{"name":"sendTime","type":"timestamp"}],"rows":[[85,"T-Mobile","LTE",-75,52.2297,21.0122,"2025-10-14T16:24:51.494384+00:00"],[84,"Plus","LTE",-98,51.6449933,17.1660292,"2025-09-18
PS C:\Users\Jakub\Desktop\Inzynierka>
