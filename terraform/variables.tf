# All tunables in one place. Override via terraform.tfvars (see *.example).

variable "project_id" {
  description = "GCP project ID"
  type        = string
  default     = "postwerk"
}

variable "region" {
  description = "GCP region (Frankfurt for DSGVO/data residency)"
  type        = string
  default     = "europe-west3"
}

variable "zone" {
  description = "GCP zone"
  type        = string
  default     = "europe-west3-a"
}

variable "instance_name" {
  description = "Name of the Compute Engine VM"
  type        = string
  default     = "postwerk-beta"
}

variable "machine_type" {
  description = <<-EOT
    VM size. e2-medium (2 vCPU / 4 GB) is the realistic minimum:
    docker-compose limits sum to ~2.75 GB (Postgres 1G + Redis 512M + backend 1G +
    frontend/Caddy ~256M) plus OS/Docker overhead. e2-small (2 GB) will likely OOM.
  EOT
  type        = string
  default     = "e2-medium"
}

variable "boot_disk_size_gb" {
  description = "Boot disk size. 10 GB default is too small once Docker images + Postgres data + backups land."
  type        = number
  default     = 30
}

variable "boot_disk_image" {
  description = "OS image (project/family)"
  type        = string
  default     = "ubuntu-os-cloud/ubuntu-2404-lts-amd64"
}

variable "network" {
  description = "VPC network name. Most projects have an auto-mode 'default' network."
  type        = string
  default     = "default"
}

variable "ssh_source_ranges" {
  description = <<-EOT
    CIDRs allowed to reach SSH (port 22) DIRECTLY (bypassing IAP). IAP traffic is
    allowed separately via the allow_ssh_iap rule. Once IAP is verified working,
    set this to [] (or your-ip/32 as break-glass) so SSH is not internet-exposed.
  EOT
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "github_repo" {
  description = "owner/repo permitted to impersonate the deployer SA via WIF."
  type        = string
  default     = "Kaandroids/Postwerk"
}

variable "ar_repo_id" {
  description = "Artifact Registry Docker repository ID."
  type        = string
  default     = "postwerk"
}
