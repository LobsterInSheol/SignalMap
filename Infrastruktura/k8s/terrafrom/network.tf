resource "azurerm_virtual_network" "aks_vnet" {
  name                = var.vnet_name
  address_space       = var.vnet_address
  location            = var.rg_location
  resource_group_name = var.rg_name
  tags = { Environment = "Production" }
}

resource "azurerm_subnet" "aks_vnet_subnet" {
  name                 = var.vnet_subnet_name
  resource_group_name  = var.rg_name
  virtual_network_name = azurerm_virtual_network.aks_vnet.name
  address_prefixes     = var.vnet_subnet_address
  
}

