# ---------------------------------------------------------------------------
# Workload Identity Federation — GitHub Actions authenticates to GCP with its
# short-lived OIDC token, impersonating the deployer SA below. NO long-lived
# service-account key is ever stored in GitHub. The provider only trusts tokens
# whose `repository` claim matches var.github_repo.
# ---------------------------------------------------------------------------

# Identity that GitHub Actions impersonates. Holds only deploy-time permissions.
resource "google_service_account" "github_deployer" {
  account_id   = "github-deployer"
  display_name = "GitHub Actions deployer (WIF, keyless)"
}

resource "google_iam_workload_identity_pool" "github" {
  workload_identity_pool_id = "github-pool"
  display_name              = "GitHub Actions"
  description               = "OIDC federation for GitHub Actions"
  depends_on                = [google_project_service.required]
}

resource "google_iam_workload_identity_pool_provider" "github" {
  workload_identity_pool_id          = google_iam_workload_identity_pool.github.workload_identity_pool_id
  workload_identity_pool_provider_id = "github-provider"
  display_name                       = "GitHub OIDC"

  attribute_mapping = {
    "google.subject"       = "assertion.sub"
    "attribute.repository" = "assertion.repository"
    "attribute.ref"        = "assertion.ref"
  }

  # SECURITY: only OIDC tokens from THIS repo are accepted by the provider.
  attribute_condition = "assertion.repository == '${var.github_repo}'"

  oidc {
    issuer_uri = "https://token.actions.githubusercontent.com"
  }
}

# Let any workflow run from var.github_repo impersonate the deployer SA.
resource "google_service_account_iam_member" "github_wif_user" {
  service_account_id = google_service_account.github_deployer.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.github.name}/attribute.repository/${var.github_repo}"
}

# Permissions the deployer needs:
#   - compute.instanceAdmin.v1 → `gcloud compute ssh` pushes an EPHEMERAL key to
#     instance metadata each run (no stored SSH key). Tighten to a custom role
#     (instances.get + instances.setMetadata) later if desired.
#   - iap.tunnelResourceAccessor → open the IAP TCP tunnel to port 22.
# (Artifact Registry push is granted at repo scope in artifact_registry.tf.)
resource "google_project_iam_member" "deployer_roles" {
  for_each = toset([
    "roles/compute.instanceAdmin.v1",
    "roles/iap.tunnelResourceAccessor",
  ])
  project = var.project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.github_deployer.email}"
}
