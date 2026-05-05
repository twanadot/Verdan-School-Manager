# Verdan Skoleadministrasjon

> **Live demo:** [http://54.75.32.234](http://54.75.32.234) — Deployet på AWS (EC2 + RDS)

**Verdan** er et omfattende skoleadministrasjonssystem med en **React web-app** koblet til et **REST API** drevet av Javalin, med **MySQL** for persistens. Systemet støtter flere institusjoner med ulike utdanningsnivåer (universitet, fagskole, VGS, ungdomsskole).

## Nøkkelfunksjoner

* **Multi-institusjons støtte:** Administrer flere skoler/universiteter fra ett system med institusjonsisolering.
* **Institusjonsdeaktivering:** Super Admin kan deaktivere institusjoner. Ansatte blokkeres automatisk, mens elever beholder tilgang til opptaksportalen for overføring.
* **Rollebasert tilgangskontroll (RBAC):** Fire roller — Super Admin, Institusjonsadmin, Lærer og Elev — med distinkte grensesnitt og rettigheter.
* **Brukeradministrasjon:** Komplett system for å opprette, redigere og administrere brukere med batch-import og batch-overføring (Excel/CSV).
* **Programstyring:** Opprett studieprogram/klasser, administrer medlemskap, og håndter nivåopprykk.
* **Fagstyring:** Opprett og administrer fag med fagtildelinger for lærere og elever.
* **Karaktersystem:** Lærere registrerer karakterer; elever ser sin egen akademiske progresjon.
* **Fraværsregistrering:** Registrer og spor fravær per fag, med konfigurerbare fraværsgrenser og fraværsbarometer.
* **Rom- og bookingadministrasjon:** Administrer klasserom og planlegg timer med ukentlig kalendervisning og konfliktdeteksjon.
* **Opptaksportal:** Felles søknadsportal med opptaksperioder, krav, søknadsbehandling og gjenåpning med ny frist.
* **Elevportal:** Delt plattform med mappestruktur, filinnleveringer, kunngjøringer og kommentarer.
* **Sanntidschat:** WebSocket-basert meldingssystem med chattegrupper, fil-vedlegg og reaksjoner.
* **Rapportering:** Oversikt over uteksaminerte elever med arkivering og gjenoppretting (CSV-eksport).
* **REST API:** Full CRUD med JWT-autentisering, refresh-tokens og rate limiting.
* **API-dokumentasjon:** Interaktiv Swagger UI på `/swagger`.
* **CI/CD-pipeline:** Automatisert bygg, test (108 tester) og deploy via GitHub Actions til AWS.

## Teknologi

### Backend
| Teknologi | Versjon |
|---|---|
| **Språk** | Java 21 |
| **REST API** | Javalin 6.4 + Jackson 2.17 |
| **Autentisering** | JWT (java-jwt 4.4) + BCrypt |
| **Byggverktøy** | Maven |
| **Database** | MySQL 8.0 (via Hibernate ORM 6.5) |
| **Persistens** | Jakarta Persistence (JPA) / Hibernate |
| **WebSocket** | Javalin WebSocket (sanntidschat) |
| **Filhåndtering** | Apache POI (Excel/CSV-import) |
| **Logging** | SLF4J / Logback med MDC |
| **Testing** | JUnit 5, Mockito, JaCoCo (code coverage), Javalin TestTools |

### Frontend (Web)
| Teknologi | Versjon |
|---|---|
| **Rammeverk** | React 19 + TypeScript 5.9 |
| **Bundler** | Vite 8 |
| **Ruting** | React Router v7 |
| **Datahenting** | TanStack Query v5 |
| **Styling** | Tailwind CSS v4 (lyst/mørkt tema) |
| **HTTP-klient** | Axios med JWT-interceptors |
| **Ikoner** | Lucide React |
| **Skjemavalidering** | React Hook Form + Zod |
| **Varsler** | Sonner (toast-notifikasjoner) |
| **Kodekvalitet** | ESLint 10 + Prettier 3 |

### Infrastruktur
| Teknologi | Bruk |
|---|---|
| **Docker** | Multi-stage builds for API og frontend |
| **Docker Hub** | Container image-register for produksjons-images |
| **Nginx** | Reverse proxy for produksjons-frontend |
| **GitHub Actions** | CI/CD-pipeline (test, bygg, Docker Hub push, EC2 auto-deploy) |
| **AWS EC2** | Produksjonsserver (eu-west-1) |
| **AWS RDS** | MySQL-database i produksjon (eu-west-1) |

## Installasjon og oppsett

### Forutsetninger
* Java JDK 21+
* Node.js 22+ (for React-frontend)
* Docker og Docker Compose (for full stack)

> **Maven er ikke nødvendig** — prosjektet bruker Maven Wrapper (`./mvnw`) som laster ned riktig versjon automatisk.

### Miljøvariabler
Kopier `.env.example` til `.env` og fyll inn verdier:
```
DB_HOST=your-database-host.example.com
DB_USER=your-db-username
DB_PASS=your-db-password
JWT_SECRET=change-this-to-a-random-secret
```

### Kjøre med Docker (full stack)
Hele systemet (API + React-frontend) kan deployes med én kommando:
```bash
docker-compose up -d --build
```
* **React Web App:** `http://localhost:4000`
* **REST API:** `http://localhost:8081`
* **Swagger UI:** `http://localhost:8081/swagger`

> **Merk:** Databasen er konfigurert via miljøvariabler (AWS RDS i produksjon). For lokal utvikling kan du avkommentere `mysql-db`-tjenesten i `docker-compose.yml`.

### Kjøre lokalt (kommandolinje)

**1. Start database:**
Enten koble til en ekstern MySQL-database via `.env`, eller start en lokal MySQL via Docker:
```bash
# Avkommenter mysql-db i docker-compose.yml først
docker-compose up -d mysql-db
```

**2. Start REST API (i en egen terminal):**
```bash
./mvnw compile exec:java      # Linux/Mac
mvnw.cmd compile exec:java    # Windows
```

**3. Start React Web App (i en egen terminal):**
```bash
cd verdan-web
npm install
npm run dev
```
Åpne `http://localhost:4000` i nettleseren. Vite proxyer automatisk API-kall til backend.

### Kjøre tester
```bash
./mvnw test      # Linux/Mac
mvnw.cmd test    # Windows
```

## Innlogging (demodata)

Systemet kommer forhåndslastet med følgende brukere for testing:

| Rolle | Brukernavn | Passord |
| :--- | :--- | :--- |
| **Super Admin** | `admin` | `admin123` |
| **Institusjonsadmin** | `inst-admin` | `123456` |
| **Lærer** | `teacher` | `teacher123` |
| **Elev** | `student` | `student123` |

> I tillegg finnes det et stort sett med lærere og elever fordelt på 5 institusjoner. Alle har passord `password123`.

## REST API-endepunkter

Base URL: `http://localhost:8081` (Docker) / `http://localhost:8080` (lokal)

**Full interaktiv API-dokumentasjon:** [http://localhost:8081/swagger](http://localhost:8081/swagger)

| Metode | Endepunkt | Beskrivelse | Tilgang |
| :--- | :--- | :--- | :--- |
| POST | `/api/login` | Autentisering og JWT-token | Offentlig (rate limited) |
| POST | `/api/auth/refresh` | Forny utløpt access-token | Offentlig |
| GET | `/api/health` | Helsesjekk | Offentlig |
| GET | `/api/openapi.json` | OpenAPI 3.0-spesifikasjon | Offentlig |
| GET/POST/PUT/DELETE | `/api/users` | Brukeradministrasjon | ADMIN |
| POST | `/api/users/import` | Batch-import elever (CSV/Excel) | ADMIN |
| POST | `/api/users/transfer` | Batch-overføring elever (CSV/Excel) | ADMIN |
| GET/POST/PUT/DELETE | `/api/institutions` | Institusjonsadministrasjon | SUPER_ADMIN |
| GET/POST/PUT/DELETE | `/api/subjects` | Fagstyring | ADMIN (skriv) |
| GET/POST/PUT/DELETE | `/api/grades` | Karakterhåndtering | ADMIN/TEACHER |
| GET/POST/PUT/DELETE | `/api/attendance` | Fraværsregistrering | ADMIN/TEACHER |
| GET/POST/PUT/DELETE | `/api/rooms` | Romadministrasjon | ADMIN (skriv) |
| GET/POST/DELETE | `/api/bookings` | Booking-håndtering | ADMIN/TEACHER |
| GET/POST/PUT/DELETE | `/api/programs` | Programstyring | ADMIN |
| GET/POST/PUT/DELETE | `/api/admissions` | Opptaksadministrasjon | ADMIN |
| GET/POST | `/api/portal` | Elevportal (mapper, filer, innleveringer) | Alle |
| GET/POST | `/api/chat` | Sanntidschat | Alle |
| WS | `/ws/chat` | WebSocket for meldinger | Autentisert |
| POST | `/api/files/upload` | Filopplasting | Alle |

> Alle endepunkter (utenom offentlige) krever JWT access-token i `Authorization: Bearer <token>`-headeren.
> Access-tokens utløper etter 15 minutter. Bruk refresh-endepunktet for å fornye.

## Databasearkitektur

Applikasjonen bruker en relasjonell databasemodell administrert av Hibernate ORM. Entitetene inkluderer:

```mermaid
erDiagram
    Institution ||--o{ User : "tilhører"
    Institution ||--o{ Subject : "har"
    Institution ||--o{ Room : "har"
    Institution ||--o{ Program : "har"

    User ||--o{ Grade : "mottar (elev)"
    User ||--o{ Attendance : "har"
    User ||--o{ SubjectAssignment : "tildelt"
    User ||--o{ Booking : "oppretter"

    Subject ||--o{ Grade : "har"
    Subject ||--o{ SubjectAssignment : "har"

    Program ||--o{ ProgramMember : "har"
    Program ||--o{ AdmissionPeriod : "har"

    AdmissionPeriod ||--o{ Application : "har"
    AdmissionPeriod ||--o{ AdmissionRequirement : "har"

    Booking }o--o{ Room : "reserverer"

    ChatRoom ||--o{ ChatMessage : "inneholder"
    ChatRoom ||--o{ ChatMember : "har"
    ChatMessage ||--o{ ChatAttachment : "har"
    ChatMessage ||--o{ ChatReaction : "har"

    PortalFolder ||--o{ PortalFile : "inneholder"
    PortalFolder ||--o{ PortalSubmission : "har"
    PortalFolder ||--o{ PortalAnnouncement : "har"
    PortalSubmission ||--o{ PortalComment : "har"

    Institution {
        Integer id PK
        String name UK
        String location
        String level "UNIVERSITET, FAGSKOLE, VGS, UNGDOMSSKOLE"
    }

    User {
        Integer id PK
        String username UK
        String password
        String role "SUPER_ADMIN, INSTITUTION_ADMIN, TEACHER, STUDENT"
        String firstName
        String lastName
        String email UK
        String phone
        String gender
        LocalDate birthDate
        Integer institution_id FK
    }

    Subject {
        Integer id PK
        String code UK
        String name
        String level
        Integer institution_id FK
    }

    Room {
        Integer id PK
        String roomNumber UK
        String roomType
        Integer capacity
        Integer institution_id FK
    }

    Program {
        Integer id PK
        String name
        String description
        Boolean attendanceRequired
        Integer minAttendancePct
        String programType
        Integer institution_id FK
    }

    Grade {
        Integer id PK
        Integer student_id FK
        String subjectCode
        String value
        LocalDate dateGiven
        String teacherUsername
        Integer institution_id FK
    }

    Attendance {
        Integer id PK
        Integer student_id FK
        LocalDate dateOf
        String status
        String note
        String subjectCode
        Boolean excused
        Integer institution_id FK
    }

    Booking {
        Integer id PK
        LocalDateTime startDateTime
        LocalDateTime endDateTime
        String status
        String description
        String createdBy
        String subject
        Integer institution_id FK
    }
```

## Prosjektstruktur

Prosjektet følger en lagdelt arkitektur med tydelig separasjon:

```
┌───────────────────┐
│   React Web App   │
│   (verdan-web/)   │
└────────┬──────────┘
         │ HTTP / WebSocket
         ▼
┌──────────────────────────────────────────────┐
│              REST API (Javalin)              │
│  Controller → Service → DAO → Hibernate/DB  │
└──────────────────────────────────────────────┘
```

### Backend (Java) — `src/main/java/no/example/verdan/`
| Pakke | Beskrivelse |
|---|---|
| `api` | REST-controllere, WebSocket, middleware (auth, metrics) |
| `app` | Applikasjonskonfigurasjon og DataSeeder |
| `auth` | Autentiseringslogikk (BCrypt, session) |
| `dao` | Data Access Objects for databasetransaksjoner |
| `dto` | Data Transfer Objects for API request/response |
| `model` | JPA-entiteter som representerer databasetabeller |
| `security` | JWT-verktøy, inputvalidering, rate limiting |
| `service` | Forretningslogikk, validering og feilhåndtering |
| `util` | Hjelpeklasser (HibernateUtil) |

### Frontend (React) — `verdan-web/src/`
| Mappe | Beskrivelse |
|---|---|
| `api/` | Axios API-klient med JWT auto-refresh (15 moduler) |
| `auth/` | AuthProvider, beskyttede ruter, rolleguard |
| `components/` | Gjenbrukbare UI-komponenter (sidebar, dialogs, autocomplete) |
| `contexts/` | React contexts (tema, chat WebSocket) |
| `pages/` | Sidekomponenter for hver funksjonsmodul (13 sider) |
| `types/` | TypeScript-typer som matcher backend-DTOer |

---