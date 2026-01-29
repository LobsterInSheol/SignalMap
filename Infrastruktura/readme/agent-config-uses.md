# Azure Pipelines — README / Tutorial

## 1. Co to są Azure Pipelines?

Azure Pipelines to narzędzie CI/CD w Azure DevOps, które:

- Automatyzuje budowanie, testowanie, wdrażanie kodu 
- Działa przez plik `azure-pipelines.yml`  
- Wykonuje Twoje skrypty krok po kroku (np. Terraform, Python, Bash itd.)

---

## 2. Jak wygląda pipeline YAML?

```yaml
trigger:
- main

pool:
  name: 'Win' # taki dodałem do agent pool 

steps:
- task: TerraformInstaller@1
  inputs:
    terraformVersion: '1.6.6'

- script: terraform init
  workingDirectory: '$(Build.SourcesDirectory)/database'
```

---

## 3. Jak uruchamiasz pipeline?

### Automatycznie:
Wystarczy commit do gałęzi, np. `main`, jeśli masz `trigger: - main`

### Ręcznie:
1. Azure DevOps → Pipelines → Twój pipeline  
2. Kliknij **Run pipeline**  
3. Wybierz gałąź i parametry (jak dodam/dodacie)

---

## 4. Autoryzacja do zasobów Azure

### Service Connection — łączy pipeline z Azure

#### Opcja 2: OIDC 
- Brak sekretów, login federacyjny
- Ustawiasz Workload Identity Federation
- Dodanie poziomu uprawnienia do Azure (już zrobione)

---

## 5. Agent Pool — co to agent?

Agent = maszyna wykonująca job pipeline'a

### Microsoft-hosted 
- Automatycznie przydzielany przez Microsoft
- Resetowany po każdym jobie

###  B) Self-hosted agent 
- Twoja maszyna (PC/VM)
- Działa szybciej, ale musisz go skonfigurować

My mamy self-hosted
---

## 🛠️ 6. Instalacja Self-hosted Agenta (Windows)

1. Azure DevOps → Project Settings → Agent Pools → New Agent (tu każdy sobie konfiguruje swoje pool name oraz agnet name, te same nazwy używa podczas konfigu agneta)
2. Pobierz paczkę agenta (link 2)   
3. Rozpakuj i uruchom w PowerShellu:

#### LINKI:
- [Windows Agent Documentation](https://learn.microsoft.com/en-us/azure/devops/pipelines/agents/windows-agent?view=azure-devops&tabs=IP-V4)
- [Agent Releases on GitHub](https://github.com/microsoft/azure-pipelines-agent/releases)
- [Medium Guide on Self-hosted Agents](https://vijayasimhabr.medium.com/configure-self-hosted-agents-for-azure-pipelines-devops-933cb58c795a)


```powershell/cmd
(ścieżka przykładowa)
cd C:\agent
.\config.cmd
```

Wprowadź: (link 3 lub chat wam pomoże)
- URL organizacji DevOps
- PAT token (PAT chyba Jakub musi podesłać)
- Pool name
- Nazwa agenta



4. Uruchom agenta (to robicie już później za każdym razem):

```powershell/cmd
.\run.cmd
```

Po pierszej autoryzacji powinno was nie pytać drugi raz o PAT i login, przy uruchomieniu pipeline i kolejnych jobów pojawi wam się: 
```
C:\agent>run.cmd
Scanning for tool capabilities.
Connecting to the server.
2025-09-01 21:03:08Z: Listening for Jobs
2025-09-01 21:03:34Z: Running job: Job
2025-09-01 21:03:44Z: Job Job completed with result: Failed
2025-09-01 21:06:08Z: Running job: Job
2025-09-01 21:06:27Z: Job Job completed with result: Failed
2025-09-01 21:09:11Z: Running job: Job
2025-09-01 21:16:58Z: Job Job completed with result: Canceled
2025-09-01 21:17:11Z: Running job: Job
2025-09-01 21:17:53Z: Job Job completed with result: Failed
2025-09-01 21:27:16Z: Running job: Job
2025-09-01 21:28:17Z: Job Job completed with result: Succeeded
```

5. Błędy
- Wydaje mi się, że macie uprawnienia do wykonywania pipeline ale to do sprawdzenia.