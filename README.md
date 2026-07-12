# QI Tracker — Spring Boot + PostgreSQL

A Quality Improvement project tracker: projects with objectives/baseline/success criteria,
indicators grouped into process areas, numerator/denominator-based indicator formulas,
a live dashboard with project-stage and on-target snapshot, role-based access
(creator vs assigned member vs viewer), email password reset, and a scheduled
PDF report emailed to a project's assigned members.

## Stack

- **Backend**: Spring Boot 3 (Java 21), packaged as a **WAR** deployable to an external Tomcat
  (or runnable standalone via the embedded servlet container).
- **Database**: PostgreSQL, schema managed with **Flyway** (`src/main/resources/db/migration`).
- **Auth**: Spring Security, session-cookie based. Passwords hashed with BCrypt.
- **PDF generation**: openhtmltopdf (HTML → PDF), for the scheduled report emails.
- **Mail**: Spring Mail (JavaMailSender) via any SMTP server.
- **Frontend**: plain HTML/CSS/JS, served as static resources from the same app (no separate build step).

## 1. Prerequisites

- JDK 21
- Maven 3.9+
- PostgreSQL 14+ running locally (or reachable over the network)
- An SMTP account/relay (Gmail app password, SendGrid, your org's mail relay, etc.) — only
  needed for password-reset emails and the scheduled PDF reports. The app runs fine without
  mail configured; those two features will just log an error instead of sending.

## 2. Create the database

```sql
CREATE DATABASE qitracker;
CREATE USER qitracker WITH PASSWORD 'qitracker';
GRANT ALL PRIVILEGES ON DATABASE qitracker TO qitracker;
```

(Use your own username/password — see the environment variables below.)

Flyway creates all tables automatically the first time the app starts. You don't need to run
any SQL by hand beyond creating the empty database.

## 3. Configure

All configuration is in `src/main/resources/application.properties`, driven by environment
variables so you don't have to edit the file for a normal deploy. Set what you need:

| Variable | Purpose | Default |
|---|---|---|
| `DB_URL` | JDBC URL | `jdbc:postgresql://localhost:5432/qitracker` |
| `DB_USER` / `DB_PASSWORD` | DB credentials | `qitracker` / `qitracker` |
| `SMTP_HOST` / `SMTP_PORT` | Mail server | `smtp.gmail.com` / `587` |
| `SMTP_USER` / `SMTP_PASSWORD` | Mail credentials | *(empty)* |
| `MAIL_FROM` | From address on outgoing mail | `qi-tracker@example.org` |
| `SIGNUP_CODE` | Code required to self-register — **change this** | `qi-team-2026` |
| `APP_BASE_URL` | Used to build links in emails; set to how your team reaches the server, e.g. `http://192.168.1.42:8080` | `http://localhost:8080` |
| `REPORT_CRON` | Cron schedule for the report-check job | `0 0 7 * * *` (daily at 07:00) |
| `PORT` | Port when run standalone | `8080` |

Example (Linux/macOS):
```bash
export DB_URL=jdbc:postgresql://localhost:5432/qitracker
export DB_USER=qitracker
export DB_PASSWORD=your-real-password
export SMTP_HOST=smtp.gmail.com
export SMTP_USER=you@gmail.com
export SMTP_PASSWORD=your-app-password
export MAIL_FROM=you@gmail.com
export SIGNUP_CODE=change-me-before-sharing
export APP_BASE_URL=http://192.168.1.42:8080
```

## 4. Build

```bash
mvn clean package
```

This produces `target/qi-tracker.war`. It also runs against your configured Postgres at
build/startup time to apply Flyway migrations — **make sure the DB is reachable and the env
vars above are set before you run this** (or at least before you first start the app).

## 5a. Run standalone (simplest for localhost)

```bash
java -jar target/qi-tracker.war
```

The app starts its own embedded Tomcat on port 8080 (or `$PORT`). Visit `http://localhost:8080`.
Teammates on the same network reach it at `http://<your-machine's-LAN-IP>:8080` — find your LAN
IP with `ipconfig` (Windows) or `ifconfig`/`ip addr` (macOS/Linux), then set `APP_BASE_URL`
accordingly so emailed links work.

## 5b. Deploy to an external Tomcat

1. Drop `target/qi-tracker.war` into Tomcat's `webapps/` folder (rename to `ROOT.war` to serve
   it at your domain root instead of `/qi-tracker`).
2. Set the environment variables above wherever Tomcat picks up its environment (e.g.
   `setenv.sh` in Tomcat's `bin/` folder, or your OS-level service environment).
3. Start Tomcat. It will unpack the WAR and Spring Boot's `SpringBootServletInitializer`
   wires the app into Tomcat's own servlet lifecycle — no embedded server runs in this mode.

## 6. First login

The first account anyone registers becomes an **ADMIN** automatically (admins can act as
creator on any project — useful for support/recovery). Everyone after that registers as a
regular member. Registration requires the `SIGNUP_CODE` — share it with your team out of band
and change it from the default before you do.

## How access control works

- **Anyone signed in** can create a project (becoming its creator) and view the **Dashboard**
  of any project in the system.
- **The creator** of a project is the only one who can: edit the project's details, add/edit/
  delete process areas, add/edit/delete indicators and their numerator/denominator data
  elements, delete the project, assign/remove members, and trigger "send report now".
- **Assigned members** (added via the Members panel) can log/edit/delete entries and use the
  Manage view, but cannot touch project/indicator/process-area structure.
- Entries can be edited or deleted by whoever logged them, or by the project's creator.
- Everyone assigned to a project (creator + members) receives its scheduled PDF report email.

## Scheduled reports

A job runs on the `REPORT_CRON` schedule (daily at 07:00 by default) and checks every project:
if the time since its last report matches or exceeds its reporting frequency (daily/weekly/
monthly/quarterly), it generates a PDF snapshot and emails it to the creator and all assigned
members, then records the send time. The creator can also trigger it immediately from the
project's header ("Send report now") without waiting for the schedule.

## Notes / things worth knowing

- Editing an indicator's data elements preserves history for elements you keep (even if
  renamed or flipped between add/subtract) — only elements you actually remove lose their
  historical per-entry values. The entry's total stays intact either way.
- Session-based login means restarting the app signs everyone out; they just sign back in.
- I haven't been able to compile or run this in the environment I built it in (no Maven, no
  network access to fetch dependencies, no live Postgres) — I've reviewed it carefully for
  consistency (matching field names, repository method signatures Spring Data can derive,
  balanced braces/tags, etc.) but `mvn clean package` on your end is the first real compile.
  If something doesn't build, send me the error and I'll fix it.
