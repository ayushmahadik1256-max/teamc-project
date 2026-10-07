# Team C — Metadata, Version & Restore Manager
**APNILEAP Initiative — Smart File Backup System**
RIT CSE Mini Project | Academic Year 2026-27

Team C is the **Authoritative System of Record** for the 4-team Smart File Backup platform. Teams A (Job Scheduler), B (Deduplication & Integrity Engine), and D (Control & Restore Dashboard) interact with persistent storage exclusively through Team C's versioned REST and SSE interfaces.

---

## 1. Architectural Role & Boundary

```
  ┌────────────────────────────────────────────────────────┐
  │         Team D - Backup Control & Dashboard UI          │
  └───────────────▲────────────────────────▲───────────────┘
                  │ HTTPS / JSON           │ SSE Live Stream
  ┌───────────────┴────────────────────────┴───────────────┐
  │     Team C - Metadata, Version & Restore Manager       │
  │              (Authoritative System of Record)          │
  └───────────────▲────────────────────────▲───────────────┘
                  │ Leases / Queue         │ Chunk / Dedup / Merkle
  ┌───────────────┴───────────────┐ ┌──────┴───────────────┐
  │  Team A - Job Scheduler Engine │ │ Team B - Dedup/Merkle│
  └───────────────────────────────┘ └──────────────────────┘
```

- **Atomic Transactions:** Team C commits `backup + file_version + chunk_references + audit_log` in a single ACID database transaction.
- **Two-Phase Compensation:** If Team B or the database fails after Team A grants a worker lease, Team C releases the lease and records `COMPENSATION_REQUIRED`.
- **Idempotency by Construction:** Every mutation requires an `Idempotency-Key` enforced via unique DB constraints and dual-layer retry handling.
- **Disaster Recovery (C4):** Built-in verification endpoint (`POST /internal/v1/recovery/verify`) proving compliance with RPO ≤ 5 minutes and RTO ≤ 30 minutes.

---

## 2. API Contract Matrix

| Method | Endpoint | Consumer | Purpose |
|---|---|---|---|
| `POST` | `/api/v1/users` | Admin / Team D | Register an institutional user (`EMPLOYEE`, `IT_ADMIN`, `AUDITOR`) |
| `GET` | `/api/v1/users` | Team D | List registered users |
| `GET` | `/api/v1/users/{userId}` | Team D | Get user profile and role |
| `POST` | `/api/v1/files` | Team D | Register file metadata with retention policy |
| `GET` | `/api/v1/files` | Team D | List registered files (filter by `userId`) |
| `GET` | `/api/v1/files/{fileId}` | Team D | Get file details & current version pointer |
| `GET` | `/api/v1/files/{fileId}/versions` | Teams B / D | Get immutable version history with Merkle root & `chunk_refs` |
| `PATCH` | `/internal/v1/files/{fileId}` | IT_ADMIN | Update retention policy or storage location |
| `POST` | `/api/v1/backups` | Team D | Trigger idempotent backup workflow |
| `GET` | `/api/v1/backups` | Team D | List backups (filter by `userId`, `fileId`) |
| `GET` | `/api/v1/backups/{backupId}` | Team D | Get backup status and verification details |
| `POST` | `/api/v1/backups/{backupId}/cancel` | Team D | Cancel backup and release worker lease |
| `POST` | `/internal/v1/restores/commit` | Orchestrator | Atomically commit a leased restore and update file pointer |
| `GET` | `/internal/v1/restores` | Team D | List restore operations |
| `GET` | `/internal/v1/restores/{restoreId}` | Team D | Get restore state |
| `POST` | `/internal/v1/restores/{id}/compensate`| Orchestrator | Trigger manual or automated compensation |
| `POST` | `/internal/v1/recovery/verify` | Auditor / Admin | Disaster recovery verification drill (RPO / RTO compliance) |
| `GET` | `/api/v1/reports/storage` | Auditor / Team D | Aggregated storage and deduplication report |
| `GET` | `/api/v1/audit` | Auditor / IT_ADMIN | Search immutable audit log by actor, action, correlation ID |
| `GET` | `/api/v1/stream/events` | Team D | Real-time Server-Sent Events stream for live dashboard freshness |
| `POST` | `/api/v1/demo/seed` | Test Console | One-click demo data seeder |

---

## 3. Quick Start & Testing

### Prerequisites
- Java 17+
- Maven 3.9+
- PostgreSQL 15+ (or Docker for Testcontainers)

### Run the Application
```bash
# Start PostgreSQL via Docker (optional, if running locally):
docker run --name teamc-db -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=teamc -p 5432:5432 -d postgres

# Start Spring Boot
mvn spring-boot:run
```
The service starts on **http://localhost:8083**.

### Open the Interactive Testing Frontend
Open your browser to:
**http://localhost:8083**

The embedded testing dashboard enables testing:
1. **Idempotency Guarantee:** Click "Test Idempotent Replay" to verify identical `backup_id` returned with 0 duplicate rows.
2. **State Machine Progression:** Follow visual stepper: `REQUESTED -> QUEUED -> CHUNKING -> DEDUPLICATING -> UPLOADING -> COMMITTED -> VERIFIED -> COMPLETED`.
3. **Retention Policy Expiry:** Test rejection of restore when version retention policy has expired (409 Conflict).
4. **Disaster Recovery Drill:** Run `POST /internal/v1/recovery/verify` to verify RPO ≤ 5 min and RTO ≤ 30 min.
5. **Live SSE Events:** Real-time event ticker from `/api/v1/stream/events`.
6. **Role Switcher:** Toggle `EMPLOYEE`, `IT_ADMIN`, `AUDITOR` to test role-based security on `/api/v1/audit` and `PATCH /files`.
