# ---------------------------------------------------------------------------
# Static external IP — postwerk.io A-record (DNS-only in Cloudflare) points here.
# Free while attached to a running VM.
# ---------------------------------------------------------------------------
resource "google_compute_address" "static_ip" {
  name   = "${var.instance_name}-ip"
  region = var.region
}

# ---------------------------------------------------------------------------
# Firewall — ONLY 80 / 443 / 22 are exposed.
# Postgres (5432) and Redis (6379) stay bound to 127.0.0.1 in docker-compose,
# so they are never reachable from the internet.
# ---------------------------------------------------------------------------
resource "google_compute_firewall" "allow_http" {
  name          = "${var.instance_name}-allow-http"
  network       = var.network
  direction     = "INGRESS"
  source_ranges = ["0.0.0.0/0"]
  target_tags   = ["http-server"]

  allow {
    protocol = "tcp"
    ports    = ["80"] # Caddy: ACME HTTP-01 challenge + HTTP->HTTPS redirect
  }
}

resource "google_compute_firewall" "allow_https" {
  name          = "${var.instance_name}-allow-https"
  network       = var.network
  direction     = "INGRESS"
  source_ranges = ["0.0.0.0/0"]
  target_tags   = ["https-server"]

  allow {
    protocol = "tcp"
    ports    = ["443"]
  }
}

resource "google_compute_firewall" "allow_ssh" {
  name          = "${var.instance_name}-allow-ssh"
  network       = var.network
  direction     = "INGRESS"
  source_ranges = var.ssh_source_ranges
  target_tags   = ["ssh"]

  allow {
    protocol = "tcp"
    ports    = ["22"]
  }
}

# ---------------------------------------------------------------------------
# The VM — runs the existing docker-compose stack (Postgres+pgvector, Redis,
# backend, frontend, Caddy). App state lives in Docker volumes on this disk;
# back it up via nightly pg_dump -> GCS.
# ---------------------------------------------------------------------------
resource "google_compute_instance" "vm" {
  name         = var.instance_name
  machine_type = var.machine_type
  zone         = var.zone
  tags         = ["http-server", "https-server", "ssh"]

  # Allow Terraform to stop/start the VM when an in-place update needs it
  # (e.g. attaching the service account below). Safe — app state is in volumes.
  allow_stopping_for_update = true

  boot_disk {
    initialize_params {
      image = var.boot_disk_image
      size  = var.boot_disk_size_gb
      type  = "pd-balanced"
    }
  }

  network_interface {
    network = var.network
    access_config {
      nat_ip = google_compute_address.static_ip.address
    }
  }

  # Dedicated identity for the VM. cloud-platform scope + IAM (see iam.tf)
  # lets the box read secrets from Secret Manager with NO key file.
  service_account {
    email  = google_service_account.vm.email
    scopes = ["cloud-platform"]
  }

  lifecycle {
    # Flip to `true` in production so a stray `terraform destroy` can't wipe
    # the box holding your Postgres volumes.
    prevent_destroy = false
  }
}
