data "azurerm_resource_group" "rg" {
  name = var.rg_name
}

# fa-submit
resource "azurerm_storage_account" "st_submit" {
  name                     = var.submit_storage_name 
  resource_group_name      = data.azurerm_resource_group.rg.name
  location                 = data.azurerm_resource_group.rg.location
  account_tier             = "Standard"
  account_replication_type = "LRS"
}

resource "azurerm_service_plan" "plan_submit" {
  name                = var.submit_plan_name 
  resource_group_name = data.azurerm_resource_group.rg.name
  location            = data.azurerm_resource_group.rg.location
  os_type             = "Linux"
  sku_name            = var.function_app_sku
}

resource "azurerm_linux_function_app" "fa_submit" {
  name                = var.submit_function_name  
  resource_group_name = data.azurerm_resource_group.rg.name
  location            = data.azurerm_resource_group.rg.location

  storage_account_name       = azurerm_storage_account.st_submit.name # nazwa konta storage
  storage_account_access_key = azurerm_storage_account.st_submit.primary_access_key # klucz dostępu do konta storage
  service_plan_id            = azurerm_service_plan.plan_submit.id # ID planu usługi

  site_config {
    application_stack {
      python_version = var.python_version
    }
    cors {
      allowed_origins = ["*"] # pozwala na żądania z dowolnej domeny
    }
  }

  app_settings = {
    "FUNCTIONS_WORKER_RUNTIME"       = "python" # ustawienie dla Pythona 
    "ENABLE_ORYX_BUILD"              = "true" # włącza Oryx do budowania aplikacji, pip requirements.txt, biblioteki
    "SCM_DO_BUILD_DURING_DEPLOYMENT" = "true" # buduje aplikację podczas wdrażania na azure
  }
}

# fa-worker

resource "azurerm_storage_account" "st_worker" {
  name                     = var.worker_storage_name 
  resource_group_name      = data.azurerm_resource_group.rg.name
  location                 = data.azurerm_resource_group.rg.location
  account_tier             = "Standard"
  account_replication_type = "LRS"
}

resource "azurerm_service_plan" "plan_worker" {
  name                = var.worker_plan_name  
  resource_group_name = data.azurerm_resource_group.rg.name
  location            = data.azurerm_resource_group.rg.location
  os_type             = "Linux"
  sku_name            = var.function_app_sku
}

resource "azurerm_linux_function_app" "fa_worker" {
  name                = var.worker_function_name
  resource_group_name = data.azurerm_resource_group.rg.name
  location            = data.azurerm_resource_group.rg.location

  storage_account_name       = azurerm_storage_account.st_worker.name
  storage_account_access_key = azurerm_storage_account.st_worker.primary_access_key
  service_plan_id            = azurerm_service_plan.plan_worker.id

  site_config {
    application_stack {
      python_version = var.python_version
    }
  }

  app_settings = {
    "FUNCTIONS_WORKER_RUNTIME" = "python"
    "ServiceBusConnection"     = azurerm_service_bus_namespace.sb.default_primary_connection_string # połączenie do Service Bus
  }
}

# SERVICE BUS

resource "azurerm_service_bus_namespace" "sb" {
  name                = var.sb_namespace_name
  location            = data.azurerm_resource_group.rg.location
  resource_group_name = data.azurerm_resource_group.rg.name
  sku                 = var.service_bus_sku
}

resource "azurerm_service_bus_queue" "sb_queue" {
  name         = var.service_bus_queue_name 
  namespace_id = azurerm_service_bus_namespace.sb.id # ID przestrzeni nazw Service Bus
}