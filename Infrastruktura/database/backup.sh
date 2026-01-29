#!/bin/bash

# informacje ogólne
RG="Inzynierka"
SOURCE_SERVER="inz-postgres"
LOCATION="northeurope"

# nowa nazwa serwera z timestampem
TIMESTAMP=$(date +%s)
TARGET_SERVER="pg-recovery-$TIMESTAMP"

echo "przywracanie serwera"
echo "Cel: Przywrócenie '$SOURCE_SERVER' jako '$TARGET_SERVER'"

# 
echo "KROK 1: Sprawdzanie źródła"

# Sprawdzamy czy serwer istnieje
EXISTS=$(az postgres flexible-server show --name $SOURCE_SERVER --resource-group $RG --query id --output tsv 2>/dev/null)

if [ -n "$EXISTS" ]; then
    echo ">> Znaleziono aktywny serwer."
    SOURCE_PARAM=$SOURCE_SERVER
else
    echo ">> Serwer nie istnieje. Szukam w Activity Log (5 dni)"
    
    DELETED_ID=$(az monitor activity-log list \
        --resource-group $RG \
        --offset 5d \
        --status Succeeded \
        --namespace "Microsoft.DBforPostgreSQL" \
        --query "[?contains(resourceId, '/flexibleServers/$SOURCE_SERVER') && operationName.value == 'Microsoft.DBforPostgreSQL/flexibleServers/delete'].resourceId | [0]" \
        --output tsv)

    if [ -n "$DELETED_ID" ]; then
        echo ">> Znaleziono ID usuniętego serwera."
        SOURCE_PARAM=$DELETED_ID
    else
        echo ">> BŁĄD: Nie znaleziono serwera."
        exit 1
    fi
fi

# przywracanie serwera
echo "KROK 2: Tworzenie nowego serwera: $TARGET_SERVER"

# restore bazy
az postgres flexible-server restore \
    --resource-group $RG \
    --name $TARGET_SERVER \
    --source-server $SOURCE_PARAM \
    --location $LOCATION \
    --output none

echo "Czekam na status 'Ready'"
az postgres flexible-server wait --name $TARGET_SERVER --resource-group $RG --created

echo " Serwer został utworzony."
echo " Nazwa: $TARGET_SERVER"
