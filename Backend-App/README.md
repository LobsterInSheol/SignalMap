# Aplikacja do nawiązaywania połączenia z bazą 

Nową wersję apki trzeba wrzucać na azure acr, konieczny jest dobry dockerfile oraz uruchomienie pipeline z /azure-pipelines, wtedy aby wersja aplikacji wdrożona została na klaster nalezy zmienić w
\Infrastruktura\k8s\k8s_config\apps\backend\helmrelease.yaml zmienić tag na ten z nowej wersji backendu, wtedy flux zobaczy zmiene i uruchmi helmrelease z nowym tagiem.