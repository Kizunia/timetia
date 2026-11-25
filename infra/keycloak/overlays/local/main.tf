# Local overlay: the shared base plus demo data. Never applied to production.

variable "keycloak_url" {
  type = string
}

variable "keycloak_admin_password" {
  type      = string
  sensitive = true
}

variable "public_base_url" {
  type    = string
  default = "http://localhost:3470"
}

variable "web_client_secret" {
  type      = string
  sensitive = true
}

variable "admin_client_secret" {
  type      = string
  sensitive = true
}

variable "demo_user_password" {
  type      = string
  sensitive = true
}

variable "demo_admin_password" {
  type      = string
  sensitive = true
}

terraform {
  required_providers {
    keycloak = {
      source  = "keycloak/keycloak"
      version = "5.10.0"
    }
  }
}

# The bootstrap administrator lives in the master realm; the Compose health check gates this step.
provider "keycloak" {
  client_id = "admin-cli"
  username  = "admin"
  password  = var.keycloak_admin_password
  url       = var.keycloak_url
  realm     = "master"
}

module "base" {
  source = "../../base"

  public_base_url     = var.public_base_url
  web_client_secret   = var.web_client_secret
  admin_client_secret = var.admin_client_secret
  ssl_required        = "none"
  smtp = {
    host = "mailpit"
    port = 1025
    from = "timetia@localhost"
  }
}

# The local administrator. The backend makes this address the first administrator when it signs in with a verified email
# address, through the same bootstrap setting production uses (KIBOOTIA_BOOTSTRAP_ADMIN_EMAIL, ADR 0025).
resource "keycloak_user" "demo_admin" {
  realm_id       = module.base.realm_id
  username       = "demo-admin"
  enabled        = true
  email          = "demo-admin@timetia.localhost"
  email_verified = true
  first_name     = "Demo"
  last_name      = "Administrator"

  initial_password {
    value     = var.demo_admin_password
    temporary = false
  }
}

resource "keycloak_user" "demo" {
  realm_id       = module.base.realm_id
  username       = "demo"
  enabled        = true
  email          = "demo@timetia.localhost"
  email_verified = true
  first_name     = "Demo"
  last_name      = "User"

  initial_password {
    value     = var.demo_user_password
    temporary = false
  }
}
