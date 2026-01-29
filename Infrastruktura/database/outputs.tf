output "resource_group_name" {
  value = local.rg_name
}

output "resource_group_location" {
  value = local.rg_location
}
output "postgres_fqdn" {
value = azurerm_postgresql_flexible_server.pg.fqdn
}

output "postgres_admin_login" {
value = azurerm_postgresql_flexible_server.pg.administrator_login
}

output "postgres_db" {
value = azurerm_postgresql_flexible_server_database.db.name
}