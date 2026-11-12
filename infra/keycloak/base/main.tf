# Shared base of the Timetia realm (ADR 0045). Overlays add environment-specific content: demo users locally,
# nothing in production. Authorization roles live in Timetia's database, not here (ADR 0025).

resource "keycloak_realm" "timetia" {
  realm        = var.realm_name
  enabled      = true
  display_name = "Timetia"

  ssl_required = var.ssl_required

  # People arrive by invitation, and later by import: nobody can create their own account (Timetia ADR 0005).
  registration_allowed     = false
  login_with_email_allowed = true
  duplicate_emails_allowed = false
  # Timetia enforces email verification itself through the email_verified claim (ADR 0041).
  verify_email = false

  # Short-lived access tokens; refresh tokens rotate and tolerate one reuse for concurrent requests.
  access_token_lifespan    = "5m"
  sso_session_idle_timeout = "30m"
  sso_session_max_lifespan = "10h"
  revoke_refresh_token     = true
  refresh_token_max_reuse  = 1

  password_policy = "length(12) and notUsername and notEmail"

  security_defenses {
    brute_force_detection {
      permanent_lockout                = false
      max_login_failures               = 5
      wait_increment_seconds           = 60
      max_failure_wait_seconds         = 900
      failure_reset_time_seconds       = 900
      quick_login_check_milli_seconds  = 1000
      minimum_quick_login_wait_seconds = 60
    }
    headers {
      x_frame_options           = "DENY"
      content_security_policy   = "frame-src 'self'; frame-ancestors 'self'; object-src 'none';"
      x_content_type_options    = "nosniff"
      x_robots_tag              = "none"
      x_xss_protection          = "1; mode=block"
      strict_transport_security = "max-age=31536000; includeSubDomains"
    }
  }

  dynamic "smtp_server" {
    for_each = var.smtp == null ? [] : [var.smtp]
    content {
      host = smtp_server.value.host
      port = smtp_server.value.port
      from = smtp_server.value.from
    }
  }
}

# The Next.js server: confidential client, Authorization Code with PKCE. No web origins, so no CORS (ADR 0008).
# Who a person is, as far as Timetia is concerned, is their email address: the application copies it from the token into the
# profile at every request, and an import gives roles to the profile that holds an address. So a person must not be able to
# change their own address or username, or they could take the address of someone about to be imported (or the bootstrap
# address) and receive that person's roles. Only an administrator, which here means the application's service account and
# the realm's operators, edits them. Names stay editable by the person, because the invitation asks for them.
# Everything else is Keycloak's default user profile, written out because this resource replaces the whole definition.
resource "keycloak_realm_user_profile" "timetia" {
  realm_id = keycloak_realm.timetia.id

  attribute {
    name         = "username"
    display_name = "$${username}"

    permissions {
      view = ["admin", "user"]
      edit = ["admin"]
    }

    validator {
      name   = "length"
      config = { min = "3", max = "255" }
    }
    validator {
      name = "username-prohibited-characters"
    }
    validator {
      name = "up-username-not-idn-homograph"
    }
  }

  attribute {
    name               = "email"
    display_name       = "$${email}"
    required_for_roles = ["user"]

    permissions {
      view = ["admin", "user"]
      edit = ["admin"]
    }

    validator {
      name = "email"
    }
    validator {
      name   = "length"
      config = { max = "255" }
    }
  }

  attribute {
    name               = "firstName"
    display_name       = "$${firstName}"
    required_for_roles = ["user"]

    permissions {
      view = ["admin", "user"]
      edit = ["admin", "user"]
    }

    validator {
      name   = "length"
      config = { max = "255" }
    }
    validator {
      name = "person-name-prohibited-characters"
    }
  }

  attribute {
    name               = "lastName"
    display_name       = "$${lastName}"
    required_for_roles = ["user"]

    permissions {
      view = ["admin", "user"]
      edit = ["admin", "user"]
    }

    validator {
      name   = "length"
      config = { max = "255" }
    }
    validator {
      name = "person-name-prohibited-characters"
    }
  }

  group {
    name                = "user-metadata"
    display_header      = "User metadata"
    display_description = "Attributes, which refer to user metadata"
  }
}

resource "keycloak_openid_client" "web" {
  realm_id  = keycloak_realm.timetia.id
  client_id = "timetia-web"
  name      = "Timetia web (Next.js)"
  enabled   = true

  access_type                  = "CONFIDENTIAL"
  client_secret                = var.web_client_secret
  standard_flow_enabled        = true
  implicit_flow_enabled        = false
  direct_access_grants_enabled = false
  service_accounts_enabled     = false
  pkce_code_challenge_method   = "S256"

  root_url                        = var.public_base_url
  # The callback, and the front door that the links in Keycloak's invitation and verification emails return to.
  valid_redirect_uris             = ["${var.public_base_url}/auth/callback", "${var.public_base_url}/"]
  valid_post_logout_redirect_uris = [var.public_base_url]
  web_origins                     = []
}

# Access tokens issued to the web client carry the audience the Spring resource server requires.
resource "keycloak_openid_audience_protocol_mapper" "api_audience" {
  realm_id  = keycloak_realm.timetia.id
  client_id = keycloak_openid_client.web.id
  name      = "timetia-api-audience"

  included_custom_audience = var.api_audience
  add_to_access_token      = true
  add_to_id_token          = false
}

# Least-privilege administration client for the user lifecycle (ADR 0025). Realm-management roles only, never
# realm-admin. Its tokens carry no timetia-api audience, and the API rejects service-account tokens anyway.
resource "keycloak_openid_client" "admin" {
  realm_id  = keycloak_realm.timetia.id
  client_id = "timetia-admin"
  name      = "Timetia administration (service account)"
  enabled   = true

  access_type                  = "CONFIDENTIAL"
  client_secret                = var.admin_client_secret
  standard_flow_enabled        = false
  implicit_flow_enabled        = false
  direct_access_grants_enabled = false
  service_accounts_enabled     = true
}

# Keycloak 26.8 accepts an Admin API call only when the token's audience is the realm's admin API address. The built-in
# kc-admin-api scope adds it; a client created here does not have it unless it is made a default scope. The list is the
# default set plus that scope; the resource owns the whole set.
resource "keycloak_openid_client_default_scopes" "admin" {
  realm_id  = keycloak_realm.timetia.id
  client_id = keycloak_openid_client.admin.id
  default_scopes = [
    "profile",
    "email",
    "roles",
    "web-origins",
    "acr",
    "basic",
    "kc-admin-api",
  ]
}

data "keycloak_openid_client" "realm_management" {
  realm_id  = keycloak_realm.timetia.id
  client_id = "realm-management"
}

resource "keycloak_openid_client_service_account_role" "admin" {
  for_each                = toset(["view-users", "query-users", "manage-users"])
  realm_id                = keycloak_realm.timetia.id
  service_account_user_id = keycloak_openid_client.admin.service_account_user_id
  client_id               = data.keycloak_openid_client.realm_management.id
  role                    = each.value
}
