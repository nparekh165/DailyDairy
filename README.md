# Khodiyar Dairy Billing (offline Android app)

A fully offline dairy billing and milk-delivery app for a small Indian dairy: customers, houses/flats,
multiple products with effective-dated rates, fast daily delivery entry, monthly invoices with
finalization and revisions, payments ledger, PDF bills, WhatsApp/Sharesheet sharing, reports with
CSV/PDF export, and local backup/restore. English and Hindi UI.

* Kotlin · Jetpack Compose · Material 3 (custom "Fresh Dairy" design system) · Room/SQLite · MVVM + repositories
* **No INTERNET permission.** No backend, Firebase, cloud or remote API. All data stays on the device.

## Toolchain (versions pinned)

| Component | Version |
|---|---|
| Android Gradle Plugin | 8.7.3 (Gradle 8.11.1 wrapper) |
| Kotlin / KSP | 2.1.0 / 2.1.0-1.0.29 |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 (Android 8.0+) |
| Compose BOM | 2024.12.01 (Material 3 1.3.x) |
| Room | 2.6.1 (schemas exported to `app/schemas`) |
| Navigation / Lifecycle | 2.8.5 / 2.8.7 |
| DataStore | 1.1.1 (UI preferences + encrypted session token only) |
| kotlinx-serialization | 1.7.3 (backup format) |
| Tests | JUnit 4.13.2, Robolectric 4.14.1, room-testing |
| Font | Manrope (SIL Open Font License, bundled in `res/font`) |

JDK 17 is required. Internet is needed only the first time Gradle downloads dependencies.

## Build

```bash
./gradlew :app:assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk` (application id `com.khodiyar.dairybilling.debug`).

Run tests:

```bash
./gradlew :app:testDebugUnitTest
```

### Signed release APK

1. Create a keystore once (keep it and its passwords safe — you need the same key for every update):
   ```bash
   keytool -genkeypair -v -keystore khodiyar-release.jks -keyalg RSA -keysize 4096 -validity 10000 -alias khodiyar
   ```
2. Create `keystore.properties` in the project root (it is git-ignored):
   ```properties
   storeFile=../khodiyar-release.jks
   storePassword=...
   keyAlias=khodiyar
   keyPassword=...
   ```
3. Build: `./gradlew :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk` (minified, resources shrunk, signed).

Alternatively use Android Studio → *Build → Generate Signed Bundle / APK*.

## First run

1. The **setup screen** asks for the dairy name, contact details and the Admin's name, username and PIN/password.
   There is no default account or password.
2. Optionally create starter products (Milk, Curd, Paneer, Ghee) — **without rates**; set rates in *More → Products & rates*.
3. Add customers (code, name, house/flat, mobile, opening balance, usual product & daily quantity, optional route order).

## Multiple dairies

One phone can run several separate dairies (businesses): *More → Dairies → Add dairy*, or *Switch dairy* on the sign-in screen.
Each dairy has its **own database file** (`dairy_billing.db` for the first one, `dairy-<id>.db` for added ones), so
customers, products, rates, bills, payments, staff logins, audit log, backups and safety backups never mix.
Switching signs you out; you sign in to each dairy with that dairy's own login. The list of dairies is kept in
`files/dairies.json`; if that file is lost, the app finds the database files again on the next start.
A backup holds one dairy; restore it while the matching dairy is open.

## Install on a phone from GitHub

Each push to `main` publishes a release with the APK attached. On the phone (signed in to GitHub, since the repo is private) open
`https://github.com/<owner>/<repo>/releases/latest` and tap `khodiyar-dairy-billing.apk`.

## Daily use

* **Delivery tab** – pick the date (it stays selected for the session), product (and session if morning/evening is enabled),
  search by name/house/code/mobile, and type quantities or use −/+ steppers. Rows show the computed amount and an
  *unsaved* highlight until **Save all** writes them in one transaction. The row menu marks *No delivery* (customer didn't
  need milk) or *Missed* (dairy failed to deliver) – both distinct from a day with no entry. *Fill usual quantities* and
  *Copy previous day* prefill drafts for review. Tap a name to enter every product for that customer.
* **Billing tab** – month overview with Draft / Finalized / Needs revision / Paid / Part paid / Outstanding states,
  issues (days without entries, invoices that differ from current records), *Finalize all*, *Lock month*/*Reopen*.
* **Invoice** – on-screen bill laid out like the PDF; *PDF & share* renders the actual generated PDF pages, then
  **WhatsApp** (contact picker with the PDF attached), **Share…** (Android Sharesheet), **Save to device** (you pick the
  folder) or **WhatsApp chat** (opens the customer's chat with the bill text; attach the PDF there). Opening a share
  target does not mean the bill was sent; you complete sending in that app.
* **Payments** – record cash/UPI/bank/other payments, partial or full, with a duplicate-submission guard; share a receipt PDF;
  Admin can reverse with a reason (kept in history).
* **Reports** – sales (date range, product filter), monthly, outstanding receivables, customer statement, customer list;
  every report exports to CSV and PDF.
* **Backup & restore** (Admin) – see below.

## Billing rules (single source of truth: `domain/billing/BillingEngine.kt`)

* Money is integer **paise** (`Long`); quantities are integer **milli-units** (`Long`, 1.5 L = 1500). No floating point.
* Line amount = quantity × applied rate, rounded **HALF_UP to the paisa once per delivery entry**. Totals are exact sums.
* Each delivery stores its applied rate. Rate changes (effective-dated, future rates allowed) never alter recorded entries or
  issued invoices. Optionally, a back-dated rate can re-price entries in *unlocked* months (audited).
* **Opening balance** – owed when the customer was added (negative = advance), dated before all entries.
* **Charges (month)** – DELIVERED entries dated in the calendar month. NO_DELIVERY / MISSED are never billed.
* **Adjustments** – explicitly recorded DEBIT (+) / CREDIT (−) items dated in the month.
* **Payments (month)** – active (non-reversed) payments dated in the month.
* **Previous balance** = opening + charges + adjustments − payments dated before the month.
* **Closing balance** = previous + charges + adjustments − payments (positive = due, negative = advance carried forward).
* Every entry belongs to exactly one month by its date, so nothing is double counted. Payments settle the oldest dues first
  (FIFO): a finalized invoice is *Paid* once later payments/credits cover its closing balance.
* Invoice numbers: `PREFIX-YYYY-MM-NNNN` (prefix configurable, default `KD`). Numbers are never reused; one live
  invoice per customer-month (enforced by a unique index). Finalized invoices store a full snapshot (customer details,
  line items, totals). Corrections after finalization produce a **revision** `…-R1` and the old invoice is kept as
  *Superseded*. Locked months reject any delivery/payment/adjustment dated inside them; reopening needs Admin PIN + reason.

## Security

* Local accounts only (Admin and Staff on **this device**; another phone needs its own accounts and data).
* PINs/passwords are stored as salted **PBKDF2-HMAC-SHA256** (120k iterations) verifiers, compared in constant time.
  5 wrong attempts lock the account with exponential back-off.
* Permissions are checked inside repositories (not only by hiding buttons). Staff always may record today's deliveries; the
  Admin can grant payments, past-entry edits, reports, billing view, customer management, rate changes and finalization.
  User management, settings, restore, backup, locking and payment reversal are Admin-only.
* Destructive actions (finalize, lock/reopen, restore, payment reversal, PIN reset) require re-entering the PIN.
* Sessions live in memory; a session token encrypted with an AES-GCM key in the **Android Keystore** lets the app resume
  within the auto-lock window after process death. Auto sign-out after configurable inactivity.
* Android cloud backup/device-transfer is disabled so business data never leaves through the OS.
* An audit log records setup, logins, customer/product/rate changes, delivery edits/deletes, payments, reversals,
  adjustments, invoices, PDFs, locks, settings, backups and restores.

**Limitations:** data is stored in the app's private SQLite database. Anyone with a rooted/compromised or unlocked device,
or with a backup file, may read it; the database itself is not encrypted. Backups contain PIN *hashes* (not PINs) so that
accounts work after a restore — keep backup files private. Use a phone screen lock.

## Backup & restore

* Backup = versioned ZIP with `manifest.json` (format, schema version, app version, counts, SHA-256 of the data) and
  `data.json` (every table, read inside one transaction for a consistent snapshot). You choose where to save it (SAF).
* Restore validates the format, schema compatibility, checksum, record counts, unique keys and every foreign-key
  reference **before** touching the database, then writes a safety backup of the current data (and re-reads it to prove it
  is valid), then replaces all tables in one transaction. You are signed out afterwards.
* A backup kept only on this phone is lost with the phone — copy it to a PC, pen drive or another phone regularly.

## Database

Room entities: `UserEntity`, `CustomerEntity`, `ProductEntity`, `ProductRateEntity`, `DeliveryEntryEntity`,
`PaymentEntity`, `BillingAdjustmentEntity`, `MonthlyBillEntity` (invoices), `InvoiceLineEntity`, `BillingPeriodEntity`
(locks), `AuditLogEntity`, `AppSettingsEntity`. Foreign keys use RESTRICT (history cannot be orphaned; customers and products
are deactivated, never deleted). Indexes cover name/code/house/mobile, delivery date, customer+date, product+date,
customer+month, payment date, unique delivery key (customer, product, date, session), unique rate (product, date) and payment
idempotency keys. Payment allocation is computed (FIFO) rather than stored, so a reversal can never leave stale allocations.

Schema history: v1 initial; v2 adds `customers.routeOrder` (`Migrations.MIGRATION_1_2`). No destructive fallback is configured.

## Project layout

```
app/src/main/java/com/khodiyar/dairybilling/
  domain/        money, quantity, dates, billing engine (pure Kotlin, unit-tested)
  data/db        Room entities, DAOs, database, migrations
  data/repo      repositories (validation, permissions, transactions, audit)
  security/      PBKDF2 hasher, permissions, session + Keystore cipher
  pdf/           platform-independent layout engine + Android PdfDocument renderer
  share/         FileProvider / Sharesheet / WhatsApp helpers
  backup/        ZIP backup & restore
  export/        CSV writer
  ui/            theme (design tokens), components, screens + ViewModels, navigation
```
