# Google provider. Authentication uses Application Default Credentials (ADC):
#   gcloud auth application-default login
# (This is SEPARATE from `gcloud auth login` — Terraform needs ADC.)
provider "google" {
  project = var.project_id
  region  = var.region
  zone    = var.zone
}
