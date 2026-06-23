# ---------------------------------------------------------------------------
# APIs required for keyless CI/CD (WIF), private images (Artifact Registry),
# and internet-free SSH (IAP). secretmanager.googleapis.com is enabled in iam.tf.
# disable_on_destroy=false so a `terraform destroy` doesn't disable shared APIs.
# ---------------------------------------------------------------------------
locals {
  required_apis = [
    "iam.googleapis.com",
    "iamcredentials.googleapis.com", # SA impersonation / token minting for WIF
    "sts.googleapis.com",            # Security Token Service — exchanges the GitHub OIDC token
    "artifactregistry.googleapis.com",
    "iap.googleapis.com", # Identity-Aware Proxy TCP forwarding (SSH tunnel)
  ]
}

resource "google_project_service" "required" {
  for_each           = toset(local.required_apis)
  service            = each.value
  disable_on_destroy = false
}
