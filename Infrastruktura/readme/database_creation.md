### Działanie pipeline - database

- Sprawdzenia istnienia instancji PostgreSQL w danej grupie zasobów.
- Jeśli serwer **nie istnieje**:
  - Wdrożenie infrastruktury poprzez **Terraform**.
  - Ustawienie rozszerzenia `azure.extensions` z wartością `postgis,postgis_raster,postgis_topology`.
- W każdym przypadku:
  - Tymczasowe dodanie reguły firewall dla IP agenta.
  - Instalacja rozszerzenia CLI `rdbms-connect`, wymaganej do uruchomienia SQL-a.
  - Wykonanie skryptu `schema.sql` na bazie danych.
  - Usunięcie reguły firewall (cleanup).

### **Terraform**

Definicje infrastruktury do utworzenia:

- Azure Database for PostgreSQL (Flexible Server)
- Grupy zasobów, parametry bazy, loginy, hasła, zmienne środowiskowe
- Obsługa OIDC z Azure DevOps

### **SQL (`schema.sql`)**

- Tworzenie rozszerzeń PostGIS:
  - `postgis`
  - `postgis_raster`
  - `postgis_topology`
- Tworzenie tabeli `telemetry` ze wsparciem geolokalizacji
- Tworzenie indeksów przestrzennych i czasowych

---

## Struktura tabeli `telemetry`

| Kolumna       | Typ                 | Opis                                         |
|---------------|----------------------|----------------------------------------------|
| `id`          | `BIGSERIAL`          | Klucz główny                                 |
| `operator`    | `TEXT`               | Operator sieci                               |
| `networkType` | `TEXT`               | Typ sieci (LTE, 5G...)                       |
| `signal`      | `SMALLINT`           | Siła sygnału w dBm (zakres: -150 do 0)       |
| `position`    | `GEOGRAPHY(POINT)`   | Lokalizacja w formacie WGS84 (EPSG:4326)     |
| `sendTime`    | `TIMESTAMPTZ`        | Czas pomiaru (z uwzględnieniem strefy czas.) |

### Indeksy:

- `idxTelemetryPosition` — przestrzenny indeks GIST na kolumnie `position`
- `idxTelemetryTime` — indeks czasowy malejący na `sendTime`

---

## Szczegółowy przebieg pipeline

### 1. **TerraformInstaller**

Instaluje wersję Terraform wskazaną w zmiennej `TF_VERSION`.

### 2. **Cache Terraform Plugins**

Używa cache’a dla `tf-plugin-cache`, żeby przyspieszyć kolejne uruchomienia pipeline.

### 3. **Check if Postgres exists**

Skrypt PowerShell sprawdzający, czy istnieje instancja serwera PostgreSQL (`az postgres flexible-server list` z filtrem po nazwie). Ustawia zmienną pipeline `PG_EXISTS` na `true` lub `false`.

### 4. **Terraform init/plan/apply**

Tylko jeśli `PG_EXISTS == false`:
- Ustawia potrzebne zmienne środowiskowe (OIDC + `TF_VAR_admin_password`)
- Inicjuje Terraform (`terraform init`)
- Tworzy plan (`terraform plan -out=tfplan`)
- Wdraża infrastrukturę (`terraform apply tfplan`)

### 5. **Włączenie rozszerzeń PostGIS**

Ustawienie parametru `azure.extensions` dla bazy Postgres przy pomocy:

```bash
az postgres flexible-server parameter set \
  --name azure.extensions \
  --value postgis,postgis_raster,postgis_topology
```

### Zmienne
Bezpieczeństwo
- Wrażliwe dane (np. DBPASSWORD) przechowywane jako sekrety pipeline
- Ograniczony dostęp do bazy danych przez tymczasową regułę IP
- Usuwanie reguł firewall po zakończeniu
- Weryfikacja stanu serwera przed wykonaniem skryptu