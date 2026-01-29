rg_name                    = "Inzynierka"
rg_location                = "northeurope"
k8s_cluster_name           = "inzynierkaaks"
k8s_node_pool_name         = "inzynierka"
k8s_node_default_pool_count = 2 ##  autoscaling działa
k8s_node_min_count         = 1
k8s_node_max_count         = 3
node_vm_size               = "Standard_B2s_v2"

vnet_name          = "k8s-vnet"
vnet_address       = ["172.16.0.0/16"]
vnet_subnet_name   = "k8s-subnet"
vnet_subnet_address = ["172.16.0.0/20"]
apiserver_subnet_name = "k8s-apiserver"
apiserver_subnet_address = ["172.16.4.0/28"]  

# fa-submit
submit_storage_name  = "inzynierka9476"
submit_plan_name     = "ASP-Inzynierka-b4e7"
submit_function_name = "fa-submit"

# fa-worker
worker_storage_name  = "inzynierka365"
worker_plan_name     = "ASP-faworkergroup8708-b84c"
worker_function_name = "fa-worker"

# Service Bus 
sb_namespace_name    = "sb-inz-telemetry" 

# Reszta konfiguracji service bus
function_app_sku       = "FC1"
python_version         = "3.10"
service_bus_sku        = "Basic"
service_bus_queue_name = "worker-queue"