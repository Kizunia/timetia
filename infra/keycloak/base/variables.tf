variable "realm_name" {
  type    = string
  default = "kibootia"
}

variable "public_base_url" {
  description = "Public origin of the Next.js server, used for redirect and post-logout URIs."
  type        = string
}

variable "web_client_secret" {
  description = "Secret of the confidential Next.js client. Comes from the generated .env, never from this repository."
  type        = string
  sensitive   = true
}

variable "admin_client_secret" {
  description = "Secret of the administration service-account client."
  type        = string
  sensitive   = true
}

variable "api_audience" {
  description = "Audience the Spring resource server requires in access tokens."
  type        = string
  default     = "kibootia-api"
}

variable "ssl_required" {
  type    = string
  default = "external"
}

variable "smtp" {
  description = "Outgoing mail settings for Keycloak. Null leaves Keycloak without a mail server."
  type = object({
    host = string
    port = number
    from = string
  })
  default = null
}
