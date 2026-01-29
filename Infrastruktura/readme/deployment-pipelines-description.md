
# Pełen potok wdrożenia (Flux + Helm + Kustomize)

## 1) Commit ląduje w repo
Pushujesz zmiany (np. w `k8s/k8s_config/...`, chart backendu, HelmRelease, values).  
Zmiany dotyczą:
- **infra/apps** (manifesty Flux/Kustomize/HelmRelease/HelmChart),
- **samego chartu** (`k8s/k8s_config/apps/backend/charts/...`),
- **values w HelmRelease** (np. `image.tag`).

## 2) Source Controller odświeża Git
- `GitRepository/gitrepo` w `flux-system` co X minut pobiera najnowszy commit.  
- Kiedy znajdzie nowy, aktualizuje artefakt Gita.

## 3) Kustomization uruchamia reconciliację
```bash
flux-system/kustomize-infra → Applied revision: main@sha1:319254c5
flux-system/kustomize-apps  → Applied revision: main@sha1:319254c5
```
- Każdy `Kustomization` wskazuje ścieżkę w repo; gdy `GitRepository` ma nowy artefakt, Kustomization renderuje Kustomize i **aplikuje manifesty do klastra** (kubectl apply w wersji kontrolerowej).  
- To tu tworzone/aktualizowane są CRD/CR: HelmRelease, HelmChart, ConfigMapy, itp.
- W `MESSAGE` widać, jaki commit został zastosowany.

## 4) HelmChart (Source Controller) pakuje lokalny chart
Dotyczy backendu:
- HelmChart/flux-system/backend-backend ma:
  - spec.chart: ./k8s/k8s_config/apps/backend/charts
  - spec.sourceRef: GitRepository/gitrepo
  - reconcileStrategy: ChartVersion
- Efekt: przy zmianie `Chart.yaml: version`, Flux spakuje nowy artefakt `.tgz` (np. `backend-0.1.2.tgz`).  
  - Jeśli **nie podbijesz wersji chartu**, mimo zmian w templatach, przy tej strategii nowy pakiet **nie powstaje**.

Alternatywa: `reconcileStrategy: Revision` — każdy nowy commit w ścieżce chartu tworzy nowy pakiet.

## 5) HelmRelease (Helm Controller) renderuje i porównuje
`HelmRelease/backend`:
- Wskazuje na `HelmChart` + ma swoje `spec.values`.
- Gdy:
  - pojawi się nowy artefakt chartu (np. 0.1.1 → 0.1.2), **albo**
  - zmienią się values (np. `image.tag`, porty, HPA),
- Helm Controller robi **render (chart + values)** i porównuje z tym, co jest w klastrze.  
  - Jeśli jest **diff** → `helm upgrade` → nowa rewizja w `helm history`.  
  - Jeśli brak diffu → nic się nie dzieje.

## 6) Upgrade: zasoby w klastrze się zmieniają
- `Deployment` dostaje nowy template → `ReplicaSet` → nowe Pody.
- `Service/Endpoints` aktualizują się, gdy zmienia się `targetPort`/nazwy portów.
- `HPA` (jeśli włączony) działa dalej według metryk.
- helm -n backend history backend dostaje REVISION +1. U Ciebie np. z backend-0.1.0 (APP 1.0.0) → backend-0.1.1 (APP 1.0.1).
Przykład: `backend-0.1.0 (APP 1.0.0)` → `backend-0.1.1 (APP 1.0.1)`.

## 7) Status/Health i „drift correction”
Flux monitoruje stan (`Ready` na `Kustomization`, `HelmChart`, `HelmRelease`).  
Jeśli ktoś ręcznie zmieni manifesty w klastrze (drift), Flux przywróci deklaratywny stan z Gita.

---

## Kiedy dokładnie pojawia się nowa rewizja Helm?
- Przy realnym upgrade (czyli jest diff po renderze chart+values).  
- Wyzwalacze:
  - Bump wersji chartu w `Chart.yaml` (`ChartVersion` → nowy `.tgz`),
  - Zmiana `spec.values` w `HelmRelease` (np. `image.tag`, `service.targetPort`, `resources` itp.).
- Samo `flux reconcile` bez zmian → nie tworzy nowej rewizji.

---

## „Ścieżka dowodowa” — jak to prześledzić
### Commit / Kustomization
```bash
flux get sources git -A
flux get kustomization -A
kubectl -n flux-system get kustomization kustomize-apps -o yaml | sls Applied
```

### Chart (pakowanie)
```bash
kubectl -n flux-system get helmchart backend-backend -o jsonpath="{.status.artifact.revision}{'\n'}"
kubectl -n flux-system get helmchart backend-backend -o jsonpath="{.status.observedSourceArtifactRevision}{'\n'}"
```

### Release (Helm)
```bash
helm -n backend status backend | sls CHART:
helm -n backend history backend
kubectl -n backend get helmrelease backend -o yaml | sls lastAppliedRevision
```

### Zasoby w klastrze
```bash
kubectl -n backend get deploy,rs,pod,svc,endpoints
kubectl -n backend get svc backend -o jsonpath="{.spec.ports[0].port}:{.spec.ports[0].targetPort}{'\n'}"
kubectl -n backend get endpoints backend -o wide
```

---

## Dobre praktyki
- **Zmiany w chartcie**: zawsze podbij `Chart.yaml: version` (lub użyj `Revision`).
- **Spójność portów**: `containerPort` ↔ `service.targetPort` ↔ nazwy portów.
- **Historia Helm**: ustaw `spec.maxHistory` > 5, jeśli chcesz więcej rewizji.
- **Zależności**: jeśli masz CRDs → CR, użyj `dependsOn` w `Kustomization`.
- **Wymuszenie cyklu:**
  ```bash
  flux reconcile source git gitrepo -n flux-system --with-source
  flux reconcile kustomization kustomize-apps -n flux-system
  flux reconcile helmrelease backend -n backend --with-source
  ```
- **Automatyczne obrazy**: rozważ Flux Image Automation.

---

## Podsumowanie
**Commit → GitRepository → Kustomization → HelmChart → HelmRelease → Helm upgrade → Nowa rewizja Helm**  
Jeśli brak różnic (diff), nie powstaje nowa rewizja.
