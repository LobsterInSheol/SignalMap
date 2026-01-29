resource "azurerm_postgresql_flexible_server" "pg" {
  name                   = var.server_name
  resource_group_name    = local.rg_name
  location               = local.rg_location

  administrator_login    = var.admin_login
  administrator_password = var.admin_password

  sku_name               = "B_Standard_B1ms"
  version                = "16"
  storage_mb             = 32768
  backup_retention_days  = 7
  zone                   = "1"

  public_network_access_enabled = true
  auto_grow_enabled             = false

}


resource "azurerm_postgresql_flexible_server_database" "db" {
  name      = var.database_name
  server_id = azurerm_postgresql_flexible_server.pg.id
  collation = "en_US.utf8"
  charset   = "UTF8"
}

resource "azurerm_postgresql_flexible_server_firewall_rule" "azure_services" {
  name             = var.firewall_rule_name
  server_id        = azurerm_postgresql_flexible_server.pg.id
  start_ip_address = "0.0.0.0"
  end_ip_address   = "0.0.0.0"
}


