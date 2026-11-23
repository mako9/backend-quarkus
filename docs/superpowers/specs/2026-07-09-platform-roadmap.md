# ComShare Platform — Roadmap & Gap Analysis

**Date:** 2026-07-09  
**Scope:** All three repos — backend-quarkus, frontend-nextjs, frontend_flutter

---

## What's Already Built

| Area | Status |
|------|--------|
| Auth (Keycloak OIDC) | All three stacks |
| User profile CRUD | Backend + both frontends |
| Community lifecycle (create, join, approve, manage) | Backend + both frontends |
| Item CRUD + image upload | Backend + both frontends |
| Item booking flow (PENDING → CONFIRMED/DECLINED/CANCELLED) | Backend + both frontends |
| Booking conflict detection (CONFIRMED bookings only) | Backend |
| Notifications (in-app DB rows + email) | Backend + both frontends |
| Unread notification badge + polling | Both frontends |
| i18n (EN + DE) | Next.js |

---

## Sub-Project Roadmap

### A — Geo-Proximity *(core platform differentiator)*

The `latitude`, `longitude`, and `radius` columns exist on `Community` but community
discovery returns all unjoined communities with no distance filter. The "nearby groups only"
constraint — the platform's key differentiator — does not exist yet.

**Missing pieces:**
- Geocode user address → lat/lon stored on `User`
- Community search filtered by distance (Haversine or PostGIS)
- Optional: map view of nearby communities (Leaflet on web, flutter_map on mobile)

---

### B — Complete Booking Experience *(short gaps in existing feature)*

Backend booking logic is fully implemented. Gaps are entirely in the frontend layer.

**Missing pieces:**
- Owner-side "incoming booking requests" view — Flutter `BookingCubit.lenderBookings` field is declared but never populated; no dedicated screen/tab
- Next.js `/bookings/:uuid` — `isOwner` is hardcoded `false` so approve/decline are unreachable in the web UI
- No visual availability calendar on item detail (users book without seeing what's already booked)
- No return/handover confirmation step — booking lifecycle ends at CONFIRMED; no RETURNED state

---

### C — Item Discovery & Search

**Missing pieces:**
- Search by name or category
- Filter by availability window
- Pagination controls (backend paginates; frontend ignores `Page<T>`)
- Browseable item index page (Next.js `/items` currently redirects to `/my-area`)

---

### D — User Trust & Reputation

**Missing pieces:**
- Public user profile pages (web)
- Item rating after booking completes
- User rating/reputation score

---

### E — Production Infrastructure

**Missing pieces:**
- Object storage (S3 or compatible) — images currently stored on local filesystem
- Environment config — backend URL hardcoded as `http://127.0.0.1:8080/api` in Next.js `next.config.js`
- Mobile push notifications (FCM/APNs) — currently only polling + email
- Real-time notification updates (SSE or WebSocket) — currently 30 s polling

---

### F — Community Features

**Missing pieces:**
- Invite link/code system — currently only open join requests with admin approval
- Community announcement/message board

---

## Known UI Bugs / Incomplete Wiring

| Location | Issue |
|----------|-------|
| Next.js `/bookings/:uuid` | `isOwner` hardcoded `false` — approve/decline unreachable |
| Next.js `/about`, `/contact`, `/settings` | Stub pages — no content |
| Next.js `/items` (index) | Redirects to `/my-area` instead of showing item catalog |
| Flutter `BookingCubit.lenderBookings` | Field declared, never populated |
| Flutter `SettingScreen` → Security Settings | Stub — no action on tap |
| Flutter `NotificationService.markRead(uuid)` | Implemented in service, unused in UI |
