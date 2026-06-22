# Postwerk — Infrastructure (Terraform)

Provisions the beta infrastructure on Google Cloud:

- 1× Compute Engine VM (`e2-medium`, Ubuntu 24.04 LTS, 30 GB) in `europe-west3` (Frankfurt)
- 1× reserved static external IP (for the `postwerk.io` A-record)
- Firewall rules: **only** 80 / 443 / 22 (Postgres & Redis stay bound to `127.0.0.1`)

State is stored remotely in a GCS bucket (versioned, lockable).

---

## Prerequisites

1. **Terraform** ≥ 1.6 — https://developer.hashicorp.com/terraform/install
   ```
   terraform -version
   ```
2. **gcloud** authenticated, and **Application Default Credentials** for Terraform:
   ```
   gcloud auth login
   gcloud auth application-default login
   ```
   > `gcloud auth login` authenticates the CLI; Terraform needs the **ADC** login too. Both required.

---

## One-time bootstrap — create the state bucket

The GCS backend bucket must exist before `terraform init`. Bucket names are
**globally unique** — if this name is taken, pick another and update `backend.tf`.

```
gcloud storage buckets create gs://postwerk-tfstate --project=postwerk --location=europe-west3 --uniform-bucket-level-access
gcloud storage buckets update gs://postwerk-tfstate --versioning
```

---

## Usage

```
cd terraform
cp terraform.tfvars.example terraform.tfvars   # then edit if needed

terraform init      # downloads provider, connects to GCS backend
terraform plan       # preview — review what will be created
terraform apply      # type "yes" to create the resources (BILLABLE)
```

After apply, Terraform prints the outputs:

```
terraform output static_ip      # -> point postwerk.io A-record (DNS-only) here
terraform output ssh_command    # -> command to SSH into the VM
```

---

## Day-2

- **Change a setting** (e.g. machine_type): edit `terraform.tfvars`, then `terraform plan` + `terraform apply`.
- **Tighten SSH**: set `ssh_source_ranges = ["<your-ip>/32"]` in `terraform.tfvars`.
- **Tear down everything**: `terraform destroy` (set `prevent_destroy = true` in `main.tf` once live to guard the data disk).

## Notes

- The VM is a **pet, not cattle**: the valuable state (Postgres volumes, `.env`)
  lives on the box, NOT in Terraform. Terraform manages the shell (VM/IP/firewall).
  Protect data with nightly `pg_dump` → GCS, not with Terraform.
- Commit `.terraform.lock.hcl`. Never commit `*.tfstate` or `terraform.tfvars`.
- If apply fails with "no default network", create one first:
  ```
  gcloud compute networks create default --subnet-mode=auto --project=postwerk
  ```
