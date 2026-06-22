# German Law Compliance Audit (DSGVO / TTDSG / TMG)

**Date:** 2026-05-18
**Application:** Postwerk (SaaS — Email Automation Platform)
**Status:** Pre-Production Audit

---

## Already Implemented

| # | Area | Law | Status |
|---|------|-----|--------|
| 1 | Consent tracking (privacy, terms, marketing timestamps) | DSGVO Art.7 | Done |
| 2 | Account deletion (soft-delete + 30-day grace period) | DSGVO Art.17 | Done |
| 3 | Data export (`GET /users/me/export`) | DSGVO Art.20 | Done |
| 4 | Data retention (scheduled daily cleanup, configurable) | DSGVO Art.5(1)(e) | Done |
| 5 | Audit logging (all actions, IP, timestamps, 10-year retention) | DSGVO Art.30 | Done |
| 6 | Password hashing (BCrypt, never exposed in API) | DSGVO Art.32 | Done |
| 7 | IMAP/SMTP password encryption (AES-256-GCM at rest) | DSGVO Art.32 | Done |
| 8 | Cookie consent banner (essential/analytics/marketing) | TTDSG §25 | Done |
| 9 | Registration consent checkbox (AGB + Datenschutz links) | DSGVO Art.7 | Done |
| 10 | Legal pages routing (datenschutz, impressum, agb) | TMG §5 | Done |
| 11 | Legal links in footer, auth pages, register form | TMG §5 | Done |
| 12 | Rate limiting (Redis-backed, per-IP) | DSGVO Art.32 | Done |
| 13 | Marketing opt-in/out toggle (Settings page) | DSGVO Art.21 | Done |
| 14 | Cookie reset button (Settings page) | TTDSG §25 | Done |
| 15 | No external analytics or tracking scripts | TTDSG §25 | Done |
| 16 | Cost-based AI quota enforcement | — | Done |

---

## Missing Items — Priority Order

### CRITICAL (Must fix before production)

| # | Issue | Law | Description | Status |
|---|-------|-----|-------------|--------|
| C1 | **HTTPS/TLS not configured** | TTDSG §3, DSGVO Art.32 | Nginx listens HTTP only (port 80). All data (JWT, passwords, emails) transmitted in cleartext. Need domain + Let's Encrypt + Nginx SSL termination. | TODO |
| C2 | **Legal page content is placeholder** | TMG §5, DSGVO Art.13 | Impressum has fake address ("Beispielstrasse 12"), Privacy Policy is one paragraph, AGB is boilerplate. Real legal text from IT-Rechtsanwalt required. | TODO (Lawyer) |
| C3 | **No DPA with Google Gemini** | DSGVO Art.28 | Email content is sent to Google Gemini API for classification/extraction. No Data Processing Agreement (Auftragsverarbeitungsvertrag) exists. | TODO (Lawyer) |
| C4 | **No separate consent for AI processing** | DSGVO Art.6 | Users don't know their email data is sent to Gemini. Need explicit AI processing consent, revocable anytime. | TODO |
| C5 | **Security headers missing** | DSGVO Art.32 | No HSTS, X-Frame-Options, X-Content-Type-Options, CSP, Referrer-Policy headers. | TODO |

### HIGH (Should fix before launch)

| # | Issue | Law | Description | Status |
|---|-------|-----|-------------|--------|
| H1 | **Password reset doesn't send email** | — | Endpoint exists but only logs, no actual email sent. Users cannot recover accounts. | TODO |
| H2 | **No separate `privacyAccepted` field** | DSGVO Art.7 | Single checkbox covers both AGB + Datenschutz. Should be separate explicit consents. | TODO |
| H3 | **AI conversation no soft-delete** | DSGVO Art.17 | `ai_conversations` table has `deleted_at` column. Soft-delete + 90-day retention + hard-delete via DataRetentionService. | Done |
| H4 | **Privacy policy version check missing** | DSGVO Art.7 | `privacyVersion` stored but never checked on login. Users not prompted to re-consent when policy updates. | TODO |
| H5 | **Account deletion UI message misleading** | — | Says "dauerhaft geloscht" but actually soft-deletes for 30 days. Should mention grace period. | TODO |

### MEDIUM (Fix soon after launch)

| # | Issue | Law | Description | Status |
|---|-------|-----|-------------|--------|
| M1 | **Cookie consent not granular** | TTDSG §25 | Only "Accept all" / "Essential only" buttons. No per-category checkboxes. | TODO |
| M2 | **IP addresses pseudonymized after 90 days** | TTDSG §3 | Scheduled job anonymizes IPs in audit_log and users.last_login_ip after 90 days (last octet → 0). | Done |
| M3 | **`lastLoginIp` exposed in profile API** | DSGVO | `/users/me` returns raw IP. Should be admin-only or removed from response. | TODO |
| M4 | **Email bodies stored unencrypted** | DSGVO Art.32 | Email content in plaintext in PostgreSQL. Should document or encrypt. | TODO (Document) |
| M5 | **No breach notification procedure** | DSGVO Art.33/34 | No documented 72-hour notification process for data breaches. | TODO (Document) |
| M6 | **Data export missing email body** | DSGVO Art.20 | Export has snippet but not full email body/attachments. | TODO |
| M7 | **No age verification** | — | Registration has no 18+ check. | TODO |
| M8 | **`usageAnalytics` defaults to `true`** | TTDSG §15 | Should default to `false` (opt-in, not opt-out). | TODO |

---

## Action Plan

### 1. Legal (Lawyer Required)
- [ ] Write real Datenschutzerklarung (Google Gemini, data retention periods, user rights, third-party services)
- [ ] Write real AGB (liability, payment, termination, applicable law)
- [ ] Write real Impressum (company details, Handelsregister, USt-IdNr)
- [ ] Prepare DPA/AVV with Google for Gemini API
- [ ] Document breach notification procedure

### 2. Infrastructure (Domain + DevOps)
- [ ] Purchase domain, configure DNS A record
- [ ] Install Certbot on server, generate Let's Encrypt certificate
- [ ] Update Nginx config: listen 443 SSL, redirect 80 -> 443, add HSTS
- [ ] Add security headers (X-Frame-Options, CSP, etc.) to Nginx or Spring Security

### 3. Technical (Code Changes)
- [x] AI conversation soft-delete (add `deleted_at` column, migration, entity update)
- [x] IP pseudonymization (scheduled job, anonymize IPs older than 90 days)
- [ ] AI processing consent mechanism (separate consent field, UI toggle, check before Gemini call)
- [ ] Privacy policy versioning (check on login, prompt re-consent)
- [ ] Separate privacy consent checkbox on registration
- [ ] Password reset email integration
- [ ] Cookie consent granular UI (per-category checkboxes)
- [ ] Remove `lastLoginIp` from profile API response
- [ ] Fix `usageAnalytics` default to `false`
- [ ] Update account deletion message to mention 30-day grace period

---

## Reference: Applicable Laws

| Law | Full Name | Scope |
|-----|-----------|-------|
| **DSGVO** | Datenschutz-Grundverordnung (EU 2016/679) | Personal data processing, user rights, consent, security |
| **TTDSG** | Telekommunikation-Telemedien-Datenschutz-Gesetz | Cookies, tracking, telecommunications privacy |
| **TMG** | Telemediengesetz | Impressum, provider identification, liability |
| **GoBD** | Grundsatze ordnungsgemasser Buchfuhrung | Accounting records retention (10 years) |

---

## Compliance Score

| Area | Score | Notes |
|------|-------|-------|
| DSGVO (technical) | 80% | Strong infrastructure, consent + deletion + export done |
| DSGVO (legal/docs) | 30% | Placeholder legal texts, no DPA, no breach procedure |
| TTDSG | 50% | Cookie consent exists but no HTTPS, IP retention too long |
| TMG | 40% | Legal pages routed but content is placeholder |
| **Overall** | **55%** | Good technical base, needs legal docs + HTTPS + AI consent |
