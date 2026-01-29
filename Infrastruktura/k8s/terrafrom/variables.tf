variable "rg_name" {
  type = string
  description = "resource group name"
}

variable "rg_location" {
  type    = string
  default = "northeurope"
  description = "default location for all resources"
}
variable "k8s_cluster_name" {
  type = string
  default = "inzynierka-aks"
  description = "kubernetes cluster name"
}
variable "k8s_node_min_count" {
  type    = number
  default = 1
  description = "number of nodes in the k8s cluster"
}
variable "k8s_node_max_count" {
  type    = number
  default = 3
  description = "max number of nodes in the k8s cluster"
}

variable "k8s_node_default_pool_count" {
  type    = number
  default = 2
  description = "default number of nodes in the k8s cluster"
  
}

variable "k8s_node_pool_name" {
  type    = string
  default = "inzynierka"
  description = "name of the node pool"
  
}

variable "node_vm_size" {
  type    = string
  default = "Standard_DS2_v2"
  description = "size of the VM for each node"
}

variable "vnet_name" {
  type    = string
  default = "k8s-vnet"
  description = "name of the virtual network"  
}

variable "vnet_address" {
  type    = list(string)
  default = ["172.16.0.0/16"]
  description = "address space for the virtual network"
}

variable "vnet_subnet_name" {
  type    = string
  default = "k8s-subnet"
  description = "name of the subnet"
}

variable "vnet_subnet_address" {
  type    = list(string)
  default = ["172.16.0.0/22"]
}

variable "apiserver_subnet_name" {
  type    = string
  default = "apiserver-subnet"
  description = "name of the apiserver subnet"
}

variable "apiserver_subnet_address" {
  type    = list(string)
  default = ["172.168.4.0/28"]
  description = "address space for the apiserver subnet"
}

variable "azure_kubernetes_cluster_node_pods_number" {
  type    = number
  default = 60
  description = "number of pods per node in the AKS cluster"
}

variable "function_app_sku" {
  type    = string
  default = "FC1"
  description = "SKU for the Function App Service Plan"
  
}

variable "python_version" {
  type = string
  default = "3.10"
  description = "Python version in Functions Apps"

}

variable "service_bus_sku" {
  type = string
  default = "Basic"
  description = "SKU for the Service Bus Namespace"
}

variable "service_bus_queue_name" {
  type = string
  default = "worker-queue"
  description = "Name of the Service Bus Queue"
}
variable "submit_storage_name" { type = string }
variable "submit_plan_name" { type = string }
variable "submit_function_name" { type = string }
variable "worker_storage_name" { type = string }
variable "worker_plan_name" { type = string }
variable "worker_function_name" { type = string }
variable "sb_namespace_name" { type = string }