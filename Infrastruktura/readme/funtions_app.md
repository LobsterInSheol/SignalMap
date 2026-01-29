## Mobile App -> Azure Function ->  Azure Flexiable Postresql server

### Mobile App
Macie tam klasę AzureClient.kt. Kod ten posiada endpoint do Azure Function. Trzeba tam poprawić w jaki sposób będzie obłusgiwany klucz do autoryzacji z Function.

### Azure Function

Ustawiony jest jak HTTP Trigger. Czyli kod zaimplementowany tam będzie uruchamiany za każdym razem gdy telefony będą wysyłać dane. Przetwarza ona Json do formatu PostGis i zapisuje do naszej bazy
Połączenie z bazę natępuje przez wpisaną zmienną w "environment variables" tam jest psql connect wpisany, kod pobiera tą zmienną i loguje się do bazy.

Aby móc obługiwać Azure Function należy:
- Zainstaluj rozszerzenia Azure Functions i Azure Account.
- Zaloguj się (Azure: Sign In).
- Pobrać azure func [link](https://learn.microsoft.com/en-us/azure/azure-functions/functions-run-local?tabs=windows%2Cisolated-process%2Cnode-v4%2Cpython-v2%2Chttp-trigger%2Ccontainer-apps&pivots=programming-language-python) i azure cli (choć to powinno być już po instalacji agenta)
- crtl+shift+p - pozwala na interakcje z wtyczkami jak wpiszecie function to macie mozliwości wtyczki azure function, tam jest create app (już zrobione)
- w cli można zrobić publish kodu do Function (wtedy implementuje kod ddo Azure, też to już zrobione)
!!!! Wg mnie lepiej napisać pipeline który pozwoli na automatyczne wdrażanie kodu do Function przy kazdym nowym commicie na branchu main projektu.

### Azure Flexiable Postresql server

Teraz tak ustwiony że jest public dostęp w sensie że widoczny, trzeba to poprawić, implementuje się przez pipeline terraform.yml (już zrobione więc uruchamiamy tylko jak np zmienimy strukturę bazy)
