variable "create_rg" {
  type        = bool
  default     = true
  description = "Czy Terraform ma stworzyć Resource Group (true), czy użyć istniejącej (false)"
}

variable "location" {
  type    = string
  default = "westeurope"
}

variable "resource_group_name" {
  type = string
}

variable "server_name" {
  type = string
}

variable "admin_login" {
  type    = string
  default = "pgadmin"
}

variable "admin_password" {
  type      = string
  sensitive = true
}

variable "database_name" {
  type = string
}

variable "firewall_rule_name" {
  type    = string
  default = "allow_my_ip"
}

