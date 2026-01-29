resource "azurerm_kubernetes_cluster" "aks" {
    name = var.k8s_cluster_name
    location = var.rg_location
    resource_group_name = var.rg_name

    dns_prefix = var.k8s_cluster_name

    default_node_pool {
        name       = var.k8s_node_pool_name
        node_count = var.k8s_node_default_pool_count
        vm_size    = var.node_vm_size
        min_count  = var.k8s_node_min_count
        max_count  = var.k8s_node_max_count
        auto_scaling_enabled = true
        type = "VirtualMachineScaleSets"
        os_disk_size_gb = 60
        max_pods = var.azure_kubernetes_cluster_node_pods_number
        
        vnet_subnet_id = azurerm_subnet.aks_vnet_subnet.id
    }

    identity {
        type = "SystemAssigned"
    }

    private_cluster_enabled = false
   
    
    network_profile {
        network_plugin = "azure"
        load_balancer_sku = "standard"
        outbound_type = "loadBalancer"
    }

    role_based_access_control_enabled = true

    tags = {
        Environment = "Production"
    }
}