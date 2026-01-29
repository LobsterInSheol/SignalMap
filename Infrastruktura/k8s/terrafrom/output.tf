output "aks_cluster_name" {
  value = azurerm_kubernetes_cluster.aks.name
}

output "aks_cluster_kube_config" {
  value     = azurerm_kubernetes_cluster.aks.kube_config_raw
  sensitive = true
}

output "aks_cluster_id" {
  value = azurerm_kubernetes_cluster.aks.id
}

output "vnet_id" {
  value = azurerm_virtual_network.aks_vnet.id
}

output "subnet_id" {
  value = azurerm_subnet.aks_vnet_subnet.id 
}