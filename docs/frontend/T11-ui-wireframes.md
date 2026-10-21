# T11 — UI Wireframes & Equipment Catalog Planning

> **Project:** Intelligent Laboratory Equipment Sharing and Resource Utilization Optimization Platform (Team B)<br>
> **Task:** T11 — UI Wireframes & Equipment Catalog Planning<br>
> **Status:** Completed (Planning & Wireframe Specification Baseline)<br>
> **Branch:** `kaif-khan`<br>
> **Target Date:** Academic Year 2026–2027 (Current Planning Baseline: October 2026)<br>
> **Scope Notice:** This document defines the frontend information architecture, wireframe layout specifications, role-based navigation, responsive behavior, accessibility guidelines, and equipment catalog design. In accordance with task rules, no backend Java files, existing React production files, or package dependencies have been modified. All sample records, counts, prices, and metrics used in this document are illustrative mock data for UI layout validation.

---

## 1. Product Goals & Target Users

### 1.1 Project Overview & Problem Statement
Universities and research institutions house specialized, high-value laboratory equipment (such as electron microscopes, NMR spectrometers, mass spectrometers, and X-ray diffractometers). In many academic institutions, these instruments are managed across separate departments using manual spreadsheets, paper sign-up sheets, or informal email chains.

This approach creates several operational problems:
1. **Low Cross-Department Visibility:** Researchers in one department may not know that suitable equipment exists and is idle in an adjacent department or faculty.
2. **Scheduling Conflicts:** Manual booking methods lack automated conflict checking, leading to overlapping reservations and wasted lab preparation time.
3. **Maintenance & Calibration Downtime:** Instruments may fall out of calibration or undergo unannounced maintenance without notifying scheduled users, resulting in invalid experimental runs.
4. **Lack of Usage Records:** Laboratory managers and department heads have limited visibility into actual equipment utilization hours, making it hard to plan maintenance windows or justify equipment sharing policies.

The platform provides a centralized, role-governed web application where scientific instruments can be discovered through a faceted catalog, reserved via an interactive availability calendar, maintained through structured service logs, and tracked through department utilization summaries.

---

### 1.2 Core Platform Objectives

The frontend architecture is designed to support five core functional objectives:

| Functional Area | Platform Goal | UI/UX Design Approach |
| :--- | :--- | :--- |
| **Equipment Discovery** | Centralize searchable instrument records across campus departments. | Multi-faceted catalog with keyword search, scientific technique filters, department tags, and live status badges. |
| **Conflict-Free Scheduling** | Prevent double-booking and provide clear reservation windows. | Visual time-grid calendar with hourly time blocks, instant conflict warnings, and a step-by-step booking modal. |
| **Maintenance & Calibration Tracking** | Prevent use of uncalibrated or out-of-service instruments. | Prominent operational badges (`Available`, `In Use`, `Under Maintenance`, `Calibrating`), calibration expiration dates, and technician service queues. |
| **Resource Utilization Visibility** | Provide usage transparency for laboratory operations. | Role-tailored dashboards displaying booking summaries, room occupancy hours, and equipment sharing summaries. |
| **Role-Based Access Control** | Restrict administrative and maintenance actions to authorized staff. | Dynamic navigation visibility and route guards aligned with the six backend user roles. |

---

### 1.3 Target User Roles

The platform enforces role-based access control (RBAC) across six user tiers defined in the backend `User.Role` enumeration:

```
+---------------------------------------------------------------------------------------------------+
|                                     PLATFORM USER HIERARCHY                                       |
+---------------------------------------------------------------------------------------------------+
| [System Administrator]         -> Manages user roles, system health, and audit logs               |
|   │                                                                                               |
|   └── [Institution Administrator] -> Views multi-department utilization and institutional policies|
|         │                                                                                         |
|         └── [Department Head]    -> Reviews department equipment usage and sharing summaries      |
|               │                                                                                   |
|               └── [Lab Manager] -> Manages lab assets, approves booking requests, schedules service|
|                     │                                                                             |
|                     ├── [Lab Technician] -> Logs calibrations, repairs, and daily equipment checks|
|                     │                                                                             |
|                     └── [Researcher / Student] -> Searches catalog, checks availability, books slots|
+---------------------------------------------------------------------------------------------------+
```

#### 1. Researcher / Student (`RESEARCHER_STUDENT`)
* **Profile:** Graduate students, postdocs, undergraduate researchers, and project investigators.
* **Core Tasks:** Search the equipment catalog; review technical specifications and prerequisite safety requirements; check calendar availability; submit reservation requests; track booking approval status; report equipment issues.
* **Common Devices:** Desktop laptops for browsing and planning; mobile devices or lab tablets for checking reservation status on-site.

#### 2. Lab Technician (`LAB_TECHNICIAN`)
* **Profile:** Equipment engineers, laboratory specialists, and technical staff responsible for instrument upkeep.
* **Core Tasks:** Perform and document periodic calibrations; log preventative maintenance; set equipment status to `Under Maintenance` or `Calibrating`; inspect physical logbooks and check-in records.
* **Common Devices:** Lab-bench tablets and shared lab workstations located next to physical equipment.

#### 3. Lab Manager (`LAB_MANAGER`)
* **Profile:** Facility directors and lab managers overseeing daily lab operations.
* **Core Tasks:** Review and approve or decline incoming booking requests; onboard new equipment records into the catalog; configure operating hours and safety guidelines; schedule maintenance downtime.
* **Common Devices:** Desktop workstation for queue triage and administrative tasks.

#### 4. Department Head (`DEPARTMENT_HEAD`)
* **Profile:** Academic Department Chairs and Research Directors.
* **Core Tasks:** Review department equipment utilization reports; monitor inter-department sharing hours; evaluate high-demand bottleneck equipment; review internal recharge summaries.
* **Common Devices:** Desktop and laptop browsers during department meetings and quarterly reviews.

#### 5. Institution Administrator (`INSTITUTION_ADMINISTRATOR`)
* **Profile:** Campus research office administrators and facilities directors.
* **Core Tasks:** View campus-wide equipment utilization summaries; evaluate cross-school sharing balance; review safety certification compliance across facilities.
* **Common Devices:** Desktop monitors for multi-facility overview tables and reports.

#### 6. System Administrator (`SYSTEM_ADMINISTRATOR`)
* **Profile:** Central IT administrators maintaining the platform deployment.
* **Core Tasks:** Manage user accounts and assign roles; monitor API and database health; inspect security audit logs; manage global announcements.
* **Common Devices:** Desktop dual-monitor workstation.

---

### 1.4 Architectural Baseline: Confirmed Requirements vs. Provisional Design Assumptions

To ensure clear coordination with the backend engineering team, this table distinguishes verified implementation specifications from provisional design decisions made during T11 planning:

| Dimension | Confirmed Project Requirements (Source Code & Backend Contracts) | Provisional Frontend Planning Assumptions (T11 Scope) |
| :--- | :--- | :--- |
| **Technology Stack** | React.js (Vite frontend), Java Spring Boot 3.x, and PostgreSQL relational database. | Client-side routing with React Router; standard institutional layout styled using Vanilla CSS tokens. |
| **User Roles & RBAC** | Exactly six official roles defined in `User.java`: `RESEARCHER_STUDENT`, `LAB_TECHNICIAN`, `LAB_MANAGER`, `DEPARTMENT_HEAD`, `INSTITUTION_ADMINISTRATOR`, `SYSTEM_ADMINISTRATOR`. | Dynamic sidebar navigation visibility driven by current role; wireframe gallery provides an interactive role switcher for layout preview. |
| **Backend Route Security** | Explicit role mappings in `SecurityConfig.java`: Public auth, multi-role GET equipment, restricted POST/PUT equipment, maintenance restricted to tech/mgr/admin, admin endpoints restricted to system admin. | Proposed frontend routes mirror the backend security boundaries and display permission-denied states (HTTP 403) when unauthorized. |
| **Core Modules** | Backend package structure establishes: `auth`, `user`, `equipment`, `booking`, `maintenance`, `sharing`, `utilization`, `notification`. | Frontend route map and dashboard widgets correspond directly to each established module. |
| **Equipment & Booking Models** | Backend entity classes exist (`Equipment.java`, `Booking.java`, etc.) but currently contain minimal skeleton fields. | Proposed equipment fields (name, category, specs, location, calibration dates, sharing rules) and booking states are provisional design models for layout validation. |
| **Sample Data Status** | The PostgreSQL database contains no production equipment or booking records yet. | All mock equipment names (e.g., JEOL TEM, Bruker NMR), sample users (e.g., Dr. Elena Vance), hourly rates, and metrics are **illustrative mock data only**. |
| **Authentication Flow** | `withCredentials: true` is configured in frontend `src/services/api.js`; CSRF is disabled in backend `SecurityConfig.java`. | Token storage mechanisms (HTTP-only cookies vs. Authorization headers) remain provisional pending backend team agreement. |

---

## 2. Information Architecture & Proposed Frontend Route Map

### 2.1 Information Architecture (IA) Tree

```
Platform Root (/)
│
├── Public & Authentication Area
│   ├── / (Landing Page: Platform Overview, Search, Featured Categories)
│   ├── /login (Login Form with Email/Password & Google Workspace SSO)
│   ├── /register (Account Registration with Department & Role Selection)
│   ├── /forgot-password (Password Recovery Request Form)
│   └── /reset-password (Token Validation & New Password Entry)
│
├── Discovery & Resource Area (All Authenticated Roles)
│   ├── /catalog (Faceted Equipment Catalog: Grid & Table Views)
│   ├── /catalog/:equipmentId (Technical Specifications & Calibration Status)
│   └── /catalog/:equipmentId/book (Time-Slot Booking Form & Safety Check)
│
├── Scheduling & Operational Area (Role-Governed)
│   ├── /bookings (My Bookings & Reservation Status Tracker)
│   ├── /calendar (Multi-Resource Master Availability Calendar)
│   ├── /equipment/new (Add Equipment: Tech, Manager, Admin)
│   └── /equipment/:equipmentId/edit (Edit Equipment: Manager, Admin)
│
├── Maintenance & Service Area (Technicians, Managers, Admins)
│   ├── /maintenance (Preventative Maintenance Queue & Work Orders)
│   └── /calibration (Calibration Verification Logs & Certificate Records)
│
├── Analytics & Reports Area (Managers, Department Heads, Admins)
│   ├── /analytics (Equipment Utilization & Sharing Overview)
│   └── /reports/export (Audit & Resource Allocation Summary)
│
├── System Administration Area (System Administrator)
│   ├── /admin/users (User Provisioning & Role Management)
│   ├── /admin/audit-logs (Security & Operational Event Logs)
│   └── /admin/health (Backend Service & Database Connection Status)
│
└── Role-Specific Dashboard Hub
    ├── /dashboard/researcher (Personal Bookings, Quota Status, Re-book Links)
    ├── /dashboard/technician (Calibration Deadlines, Open Work Orders)
    ├── /dashboard/lab-manager (Booking Approvals Queue, Facility Occupancy)
    ├── /dashboard/department-head (Department Utilization, Sharing Breakdown)
    ├── /dashboard/institution-admin (Campus Facility Overview, Equipment Status)
    └── /dashboard/system-admin (User Accounts, Microservices Health)
```

---

### 2.2 Proposed Frontend Route Map Table

The table below lists the proposed frontend routes, their access requirements, and the corresponding backend security matches from `SecurityConfig.java`:

| Proposed Route | View Component | Minimum Role Required | Backend Security Match | Purpose & Key Features |
| :--- | :--- | :--- | :--- | :--- |
| `/` | `LandingView` | Public | Unauthenticated | Platform overview, search box, sample statistics, category cards. |
| `/login` | `LoginView` | Public | `/api/auth/**` (PermitAll) | Email and password sign-in, Google SSO button, forgot password link. |
| `/register` | `RegisterView` | Public | `/api/auth/**` (PermitAll) | User registration with department affiliation and role request. |
| `/forgot-password` | `ForgotPasswordView` | Public | `/api/auth/**` (PermitAll) | Request a time-limited password reset link by email. |
| `/reset-password` | `ResetPasswordView` | Public | `/api/auth/**` (PermitAll) | Reset password using a validated security token. |
| `/catalog` | `EquipmentCatalogView` | All Authenticated Roles | `GET /api/equipment/**` | Search, faceted filter sidebar, grid/table view toggle, status badges. |
| `/catalog/:id` | `EquipmentDetailView` | All Authenticated Roles | `GET /api/equipment/**` | Technical specifications, calibration date, availability calendar strip. |
| `/catalog/:id/book` | `BookingWizardView` | All Authenticated Roles | `POST /api/bookings/**` | Reservation slot selector, project grant code input, safety checklist. |
| `/bookings` | `BookingsManagementView` | All Authenticated Roles | `/api/bookings/**` | List of user reservations, status badges (Pending, Approved, Cancelled). |
| `/calendar` | `MasterCalendarView` | All Authenticated Roles | `GET /api/bookings/**` | Multi-resource Day/Week/Month calendar with slot conflict indicators. |
| `/equipment/new` | `AddEquipmentView` | Lab Tech, Lab Mgr, Admins | `POST /api/equipment/**` | Form for registering equipment name, model, serial, category, and specs. |
| `/equipment/:id/edit`| `EditEquipmentView` | Lab Mgr, Admins | `PUT /api/equipment/**` | Update instrument specifications, room location, or operational status. |
| `/maintenance` | `MaintenanceHubView` | Lab Tech, Lab Mgr, Admins | `/api/maintenance/**` | Service work orders, repair logs, scheduled downtime notices. |
| `/calibration` | `CalibrationLogView` | Lab Tech, Lab Mgr, Admins | `/api/maintenance/**` | Calibration validity tracker, test logs, certificate file references. |
| `/analytics` | `AnalyticsDashboardView`| Lab Mgr, Dept Head, Admins | `/api/analytics/**` | Equipment booking hours, utilization percentages, sharing metrics. |
| `/admin/users` | `UserAdminView` | System Admin | `/api/admin/**` | Search users, assign roles, activate or lock accounts. |
| `/dashboard` | `DashboardRouter` | All Authenticated Roles | Dynamic by JWT Role | Redirects the authenticated user to their role-specific dashboard. |

---

## 3. Navigation Structure & Role-Based Menu Visibility

### 3.1 Global Application Shell Anatomy

The interface follows a standard institutional web application layout with a top header, collapsible sidebar, content viewport, and accessible footer:

```
+---------------------------------------------------------------------------------------------------+
|  [LOGO] Intelligent Lab Platform   | Search Catalog... [Q] | [Notifications] [Role Badge] [Profile v]|
+-----------------------+---------------------------------------------------------------------------+
| SIDEBAR NAVIGATION    | BREADCRUMBS: Home > Equipment Catalog > JEOL JEM-2100F TEM                |
| --------------------- |---------------------------------------------------------------------------|
| [Dashboard]           | MAIN CONTENT VIEWPORT                                                     |
| [Equipment Catalog]   |                                                                           |
| [Master Calendar]     |  [Filter Bar: Category | Department | Status | Calibration]               |
| [My Bookings]         |                                                                           |
|                       |  +--------------------+  +--------------------+  +--------------------+   |
| -- ROLE SECTIONS --   |  | Equipment Card 1   |  | Equipment Card 2   |  | Equipment Card 3   |   |
| [Approvals Queue]     |  | TEM-2100F HR-TEM   |  | Bruker 600MHz NMR  |  | Agilent LC-MS 6490 |   |
| [Maintenance Hub]     |  | [Available] [Dept] |  | [In-Use] [Campus]  |  | [Maint] [Off-line] |   |
| [Utilization Stats]   |  +--------------------+  +--------------------+  +--------------------+   |
| [System Admin]        |                                                                           |
+-----------------------+---------------------------------------------------------------------------+
| FOOTER: (c) 2026 Lab Resource Platform | Terms | Privacy | Lab Safety SOPs | v1.0.0-wireframe     |
+---------------------------------------------------------------------------------------------------+
```

### 3.2 Dynamic Role-Based Menu Visibility Matrix

The menu visibility matrix specifies which navigation links are rendered for each role:

| Navigation Menu Item | Target Route | Researcher / Student | Lab Technician | Lab Manager | Department Head | Institution Admin | System Admin |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **Dashboard** | `/dashboard` | Yes (Student) | Yes (Tech) | Yes (Mgr) | Yes (Dept) | Yes (Inst) | Yes (Sys) |
| **Equipment Catalog** | `/catalog` | Yes | Yes | Yes | Yes | Yes | Yes |
| **Master Calendar** | `/calendar` | Yes (View) | Yes (View) | Yes (Manage) | Yes (View) | Yes (View) | Yes (View) |
| **My Bookings** | `/bookings` | Yes | Yes | Yes | Yes | Yes | Yes |
| **Booking Approvals** | `/bookings/approvals` | No | No | **Yes** | **Yes** (Dept) | **Yes** (Campus) | Yes (Audit) |
| **Add Equipment** | `/equipment/new` | No | **Yes** | **Yes** | No | **Yes** | **Yes** |
| **Maintenance Hub** | `/maintenance` | No | **Yes** | **Yes** | No | **Yes** | **Yes** |
| **Calibration Queue**| `/calibration` | No | **Yes** | **Yes** | No | **Yes** | **Yes** |
| **Utilization KPIs** | `/analytics` | No | No | **Yes** (Lab) | **Yes** (Dept) | **Yes** (Campus) | **Yes** (All) |
| **User Administration**| `/admin/users` | No | No | No | No | No | **Yes** |
| **System Health** | `/admin/health` | No | No | No | No | No | **Yes** |

---

## 4. Low-Fidelity Layout Specifications

### 4.1 Home / Landing Page (`/`)
* **Purpose:** Public portal introducing the platform, core equipment categories, and search entry point.
* **Key Wireframe Elements:**
  1. **Top Bar:** Institution branding, navigation links (`Catalog`, `Core Facilities`, `Safety Guidelines`), and Auth buttons (`Sign In`, `Register Affiliation`).
  2. **Hero Section:** Headline ("Discover, Book & Share Scientific Equipment Across Departments"), search input ("Search by instrument name, technique, or department..."), and primary CTA button (`Search Catalog`).
  3. **Illustrative Metric Ticker (Wireframe Mock Data):** 4 sample summary cards:
     - `142+` Registered Instruments *(Mock placeholder)*
     - `18` Core Laboratory Bays *(Mock placeholder)*
     - `89.4%` On-Time Calibration Rate *(Mock placeholder)*
     - `14,200 hrs` Logged Research Usage *(Mock placeholder)*
  4. **Category Discovery Grid:** 6 category cards (Electron Microscopy, Spectroscopy, Mass Spectrometry, X-Ray Diffraction, Mechanical Testing, Chromatography).
  5. **Footer:** Institutional copyright notice, safety policy links, and wireframe disclaimer.

### 4.2 Authentication Suite
#### 4.2.1 Login Screen (`/login`)
* **Layout:** Centered two-panel card with an institutional summary on the left and login form on the right.
* **Form Elements:**
  - Institutional Email input (`name@university.edu`).
  - Password input with toggle visibility button.
  - "Remember this lab workstation" checkbox.
  - "Forgot password?" link.
  - Primary button: `Sign In to Workspace`.
  - Secondary OAuth button: `Sign in with Google Workspace`.
  - Security note: "Authorized university research personnel only."

#### 4.2.2 Registration Screen (`/register`)
* **Layout:** Card layout with department affiliation and role request inputs.
* **Form Elements:**
  - Full Name input.
  - Institutional Email input (validates domain).
  - Institution / University dropdown.
  - Academic Department dropdown (e.g., Materials Science, Chemistry, Bioengineering).
  - Primary Role Selection (Researcher/Student, Lab Technician, Lab Manager).
  - Password and Confirm Password inputs with complexity hints.
  - Safety guidelines acknowledgement checkbox.
  - Primary button: `Submit Registration Request`.

#### 4.2.3 Password Recovery Screens (`/forgot-password` & `/reset-password`)
* **Forgot Password View:** Email input, explanation of receiving a 15-minute reset token, and `Send Recovery Link` button.
* **Reset Password View:** Token display field (validated from URL parameter), new password input, confirm password input, and `Update Password & Sign In` button.

---

### 4.3 Equipment Catalog (`/catalog`)
* **Layout Structure:** Two-column layout with a left filter sidebar (280px wide) and right catalog results area.
* **Faceted Filter Sidebar (Left):**
  - **Keyword Search:** Input matching instrument name, model, serial, or keywords.
  - **Category Facet:** Checkbox list with counts (e.g., Electron Microscopy, Spectroscopy, Mass Spectrometry).
  - **Operational Status Facet:** `Available for Booking`, `Currently In-Use`, `Under Maintenance`, `Calibrating`.
  - **Department Facet:** Materials Science, Chemistry, Bioengineering, Physics.
  - **Calibration Validity:** `Certified Valid`, `Due within 14 Days`, `Overdue / Lockdown`.
  - **Action:** `Reset All Filters` button.
* **Catalog Results Toolbar (Right):**
  - Results count indicator (e.g., "Showing 6 of 48 Matched Instruments").
  - Sort dropdown: `Availability (Soonest First)`, `Name (A-Z)`, `Highest Utilization`.
  - View toggle: `[Grid Cards]` vs. `[Dense Table]`.
* **Grid Card Elements:**
  - Thumbnail placeholder with category tag.
  - Equipment name, manufacturer, model, and asset tag (e.g., `JEOL JEM-2100F TEM`, `EQ-2024-MS-004`).
  - Facility location (Building, Room).
  - Operational status badge and calibration validity chip.
  - Hourly grant recharge rate (e.g., `$45 / hr`).
  - Action buttons: `View Details` and `Book Time Slot`.
* **Dense Table Columns:**
  - Asset Code | Equipment Name & Model | Category | Facility Location | Operational Status | Calibration Expiry | Sharing Policy | Actions.

---

### 4.4 Equipment Details (`/catalog/:equipmentId`)
* **Layout:** Two-column view (left: technical specifications and maintenance log; right: sticky reservation widget).
* **Left Section (Specifications & History):**
  1. **Header:** Instrument schematic placeholder, title, manufacturer, serial number, and status badge.
  2. **Technical Specifications Table:** Operating voltage/parameters, resolution, detector types, supported specimen dimensions.
  3. **Safety & Training Prerequisites:** Required training certifications (e.g., "Radiation Safety Level 2").
  4. **Calibration & Preventative Service Log:** Historical table showing date, service type, technician name, and certificate link.
* **Right Sticky Sidebar (Booking Widget):**
  1. Hourly grant recharge rate.
  2. Next available reservation slot.
  3. Date picker and time-window selector.
  4. Project grant billing code input.
  5. Action buttons: `Proceed to Booking Wizard`, `Download SOP Guide (.PDF)`.
  6. Privileged actions (visible to Lab Managers): `Edit Asset`, `Flag Maintenance`.

---

### 4.5 Add / Edit Equipment Form (`/equipment/new` & `/:id/edit`)
* **Layout:** Multi-step tabbed form for onboarding or updating equipment records.
* **Tabs:**
  1. **Core Information:** Commercial name, manufacturer, model number, internal serial tag, department, room/bay.
  2. **Technical & Safety:** Category selection, power/vacuum requirements, safety prerequisites, max continuous session limit.
  3. **Sharing Policies:** Access tier (Open Campus, Department Only, Supervisor Approval Required), hourly internal recharge rate.
  4. **Calibration Interval:** Calibration frequency (monthly, quarterly, annual), last certified date, assigned technician.
* **Footer Actions:** `Save Draft`, `Cancel`, `Publish Equipment to Catalog`.

---

### 4.6 Booking & Availability Calendar (`/calendar`)
* **Layout & Controls:**
  - View switcher: `Week`, `Day`, `Month`.
  - Navigation controls: `< Previous`, `Today`, `Next >`, current date range title.
  - Multi-resource selector: Dropdown to filter by specific equipment or room bay.
  - Weekly Time Grid (08:00 to 18:00):
    - Confirmed reservations (Blue block with requester name and time).
    - Pending manager review (Amber block).
    - Maintenance downtime (Striped gray block).
    - Open available slots (Clickable block).
* **Reservation Modal (Triggered by clicking an open slot):**
  - Selected equipment, bay location, and date/time window.
  - Project grant account code input.
  - Specimen safety and chemical compatibility checkboxes.
  - Conflict check indicator banner ("No conflicts detected for this window").
  - Submit button: `Confirm & Submit Booking Request`.

---

### 4.7 Six Role Dashboards

#### 4.7.1 Researcher / Student Dashboard (`/dashboard/researcher`)
* **Header:** Researcher name and affiliated department.
* **Illustrative Metric Cards:** Active reservations count, hours reserved this month, pending approval count, safety certification status.
* **Key Sections:**
  1. **Upcoming Reserved Sessions:** List of confirmed bookings with countdown, room number, door access passcode, and reschedule button.
  2. **Booking Approval Status Tracker:** Visual step progress for pending bookings (`Submitted -> Lab Manager Review -> Confirmed`).
  3. **Frequently Used Equipment:** Quick-book shortcuts for previous instruments.

#### 4.7.2 Lab Technician Dashboard (`/dashboard/technician`)
* **Header:** Technician operations command console.
* **Illustrative Metric Cards:** Instruments due for calibration, active repair work orders, operational equipment count, daily check-in count.
* **Key Sections:**
  1. **Calibration Action Queue:** Prioritized table of instruments approaching calibration deadlines with days remaining and `Log Calibration` action.
  2. **Active Work Orders:** List of reported defects with severity badges and `Start Repair` button.
  3. **Daily Equipment Check-In Log:** Log of physical instrument access.

#### 4.7.3 Lab Manager Dashboard (`/dashboard/lab-manager`)
* **Header:** Facility operations hub and lab name.
* **Illustrative Metric Cards:** Pending booking approvals, weekly facility utilization rate, total managed instruments, monthly recharge recovery total.
* **Key Sections:**
  1. **Booking Approval Queue:** Table of pending requests with requester name, requested slot, grant code, and inline `Approve` / `Decline` buttons.
  2. **Weekly Facility Occupancy:** Occupancy heatmap across managed laboratory bays.
  3. **Maintenance Downtime Planner:** Scheduled preventative maintenance windows.

#### 4.7.4 Department Head Dashboard (`/dashboard/department-head`)
* **Header:** Departmental resource overview and department name.
* **Illustrative Metric Cards:** Average department equipment utilization, inter-department sharing hours, active research groups count, high-demand equipment count.
* **Key Sections:**
  1. **Equipment Demand & Bottleneck Summary:** Utilization progress bars identifying heavily booked instruments.
  2. **Inter-Department Sharing Summary:** Breakdown of equipment hours shared with other academic departments.
  3. **Grant Allocation Summary:** Total hours utilized grouped by research cluster.

#### 4.7.5 Institution Administrator Dashboard (`/dashboard/institution-admin`)
* **Header:** Campus scientific asset governance overview.
* **Illustrative Metric Cards:** Total monitored instruments, campus-wide average utilization rate, active core facilities count, cross-sharing hours.
* **Key Sections:**
  1. **Core Facilities Comparison:** Table comparing total instruments, utilization rate, and compliance index across campus facilities.
  2. **Inter-Faculty Sharing Flow:** Summary of equipment usage between engineering, natural sciences, and biomedical schools.
  3. **Safety & Audit Compliance:** Overview of certification rates across facilities.

#### 4.7.6 System Administrator Dashboard (`/dashboard/system-admin`)
* **Header:** System infrastructure and user administration console.
* **Illustrative Metric Cards:** Registered user accounts, backend API gateway status, database connection latency, open security alerts.
* **Key Sections:**
  1. **User Identity & Role Management:** User table with search, role dropdown selector, active status toggle, and audit link.
  2. **Backend Service Health Monitor:** Status cards for Auth Service, Equipment Service, Booking Service, and PostgreSQL database.
  3. **Security Audit Log Feed:** Chronological log of recent login events and role changes.

---

## 5. Equipment Catalog Layout, Filtering, Search & Status Conventions

### 5.1 Search & Filtering Specifications
1. **Search Matching:** Keyword input evaluates instrument commercial name, manufacturer, model number, internal serial code, and scientific modality (e.g., TEM, NMR, LC-MS).
2. **Category Hierarchy:**
   - Level 1: Analytical Imaging, Spectroscopy, Separation Science, Surface Analysis, Mechanical Prototyping.
   - Level 2: Transmission Electron Microscopy, Scanning Electron Microscopy, NMR Spectrometry, Mass Spectrometry, HPLC.
3. **Filter Dimensions:**
   - Facility Location (Campus, Building, Room).
   - Operational Status (`Available`, `In Use`, `Under Maintenance`, `Calibrating`).
   - Department Affiliation (Materials Science, Chemistry, Bioengineering, Physics).
   - Calibration Validity (`Certified Valid`, `Due within 14 Days`, `Overdue / Lockdown`).

### 5.2 Status Indicator Conventions & Color Semantics

| Status | Badge Style | Semantic Meaning | Permitted User Action |
| :--- | :--- | :--- | :--- |
| **Available** | Green pill (`#047857`) with checkmark | Instrument is operational, certified, and idle. | Immediate booking allowed. |
| **In Use / Busy** | Blue pill (`#1d4ed8`) with clock | Instrument is running an active reservation. | View schedule to book future slot. |
| **Pending Approval** | Amber pill (`#b45309`) with timer | Booking requested, awaiting Lab Manager review. | View queue position or cancel request. |
| **Under Maintenance**| Red/Yellow pill (`#b91c1c`) with wrench | Instrument is undergoing repair or servicing. | Bookings disabled until restored. |
| **Calibrating** | Purple pill (`#6d28d9`) with gauge | Instrument undergoing calibration check. | Bookings disabled until certification. |
| **Decommissioned** | Slate Gray pill (`#475569`) with icon | Asset is archived or permanently retired. | View-only historical reference. |

### 5.3 Role-Dependent Action Buttons Matrix

| Action Button | Researcher / Student | Lab Technician | Lab Manager | Department Head | Institution Admin | System Admin |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **"Book Time Slot"** | **Visible & Primary** | Visible | Visible | Visible | Visible | Visible |
| **"View Schedule / Calendar"** | **Visible** | **Visible** | **Visible** | **Visible** | **Visible** | **Visible** |
| **"Log Maintenance"** | Hidden | **Visible & Primary** | **Visible** | Hidden | Visible | Visible |
| **"Perform Calibration"** | Hidden | **Visible & Primary** | **Visible** | Hidden | Hidden | Visible |
| **"Edit Asset Specs"** | Hidden | Hidden | **Visible & Primary**| Hidden | **Visible** | **Visible** |
| **"Take Offline (Emergency)"**| Hidden | **Visible (Warning)** | **Visible (Warning)**| Hidden | **Visible** | **Visible** |
| **"View Utilization Analytics"**| Hidden | Hidden | **Visible** | **Visible & Primary**| **Visible & Primary**| **Visible** |

---

## 6. Important UI States & Error Handling

```
+--------------------------------------------------------------------------------------------------+
|                                  UI STATE DEFINITIONS & LAYOUTS                                  |
+--------------------------------------------------------------------------------------------------+
| 1. SKELETON LOADING     | Animated gray placeholders mirroring card and table geometries.         |
| 2. EMPTY RESULTS        | Search icon with "No equipment matched your filters" and Clear button.  |
| 3. API SERVER ERROR     | Alert box with error details, retry button, and support contact link.   |
| 4. NO AVAILABILITY      | "Fully booked for selected window" with next open date indicator.       |
| 5. PENDING APPROVAL     | Warning banner with reviewer name and option to withdraw request.       |
| 6. PERMISSION DENIED    | HTTP 403 box showing required role and a link back to dashboard.        |
+--------------------------------------------------------------------------------------------------+
```

### 6.1 Loading State (Skeleton Screens)
* Uses pulsing gray geometric bars matching the card and table layouts.
* Prevents layout shift (CLS = 0) while catalog data or calendar events are fetched from the backend.

### 6.2 Empty Results State (`Catalog` & `Bookings`)
* Displays an empty search icon.
* Heading: "No equipment found matching your criteria".
* Helper text: "Try broadening your filters or searching for general instrument categories."
* Action: `Clear All Filters` button.

### 6.3 API Error State (Network Disruption / HTTP 500)
* Prominent alert banner with a red outline:
  - Title: "Unable to Synchronize with Laboratory Services".
  - Description: "Failed to connect to the backend service. Please verify your connection."
  - Action: `Retry Request` button and `Report Issue` link.

### 6.4 No Availability State
* Displayed when all slots for an instrument are occupied during a chosen date range.
* Features:
  - Text: "Fully Booked for Selected Window".
  - Action: `Jump to Next Available Opening` button.

### 6.5 Booking Pending Approval State
* Banner displayed on user dashboard and reservation details:
  - Status: "Awaiting Lab Manager Review".
  - Details: Shows reviewer name and typical review window.
  - Action: `Withdraw Request` button with confirmation.

### 6.6 Permission Denied State (HTTP 403)
* Displayed when a user navigates to a restricted route (such as a student accessing `/admin/users`).
* Features:
  - Security lock icon.
  - Title: "Access Restricted by Role Policy (HTTP 403)".
  - Description: Identifies the user's current role and the required role for that view.
  - Action: `Return to Personal Dashboard` primary button.

---

## 7. Responsive Behavior & Multi-Device Breakpoints

### 7.1 Breakpoints

| Device Class | Breakpoint Range | Grid Columns | Navigation Pattern | Catalog Layout |
| :--- | :--- | :---: | :--- | :--- |
| **Desktop / Workstation** | `>= 1200px` | 12 cols | Persistent sidebar (260px) and top bar | 3-column card grid or full table |
| **Tablet / Bench Station** | `768px - 1199px`| 8 cols | Collapsible sidebar or top horizontal tabs | 2-column card grid or responsive table |
| **Mobile Device** | `< 768px` | 4 cols | Top bar with hamburger off-canvas drawer | 1-column card stack; table scrolls horizontally |

### 7.2 Mobile Adaptation Rules
1. **Catalog Filters on Mobile:** Filters collapse into an off-canvas drawer or modal triggered by a `Filters` button to preserve vertical screen space.
2. **Horizontal Overflow Prevention:** Tables use contained horizontal scrolling (`overflow-x: auto`) with visual scroll indicators. The root container enforces `overflow-x: hidden` to prevent body scroll breaking.
3. **Touch Targets:** All buttons, form controls, and calendar slot hit areas maintain a minimum target size of 44px by 44px for touchscreen use.
4. **Calendar on Mobile:** The master calendar switches from a 5-day grid to a single-day agenda view with a horizontal date slider.

---

## 8. Accessibility Requirements, Reusable Component Inventory & Major User Flows

### 8.1 Accessibility (WCAG 2.1 AA) Compliance Strategy
* **Contrast Ratios:** Text maintains a contrast ratio of at least 4.5:1 against light backgrounds. Primary buttons and active badges maintain at least 3.0:1.
* **Keyboard Navigation:** All interactive elements support Tab navigation with visible `:focus-visible` outline rings. Modals implement focus traps and close on `Escape`.
* **Semantic HTML & ARIA:**
  - Semantic elements used throughout (`<header>`, `<main>`, `<section>`, `<nav>`, `<aside>`, `<table>`).
  - Form inputs are paired with `<label for="...">`.
  - Dynamic status changes use `role="status"` and `aria-live="polite"`.

---

### 8.2 Reusable Component Inventory

```
+--------------------------------------------------------------------------------------------------+
|                                   REUSABLE COMPONENT INVENTORY                                   |
+--------------------------------------------------------------------------------------------------+
| ATOMS:                                                                                           |
|  - Button (Primary, Secondary, Danger, Small)                                                    |
|  - StatusBadge (Available, Busy, Maintenance, Calibration, Pending)                              |
|  - FormInput, FormSelect, FormCheckbox, FormRadio                                                |
|  - IconPlaceholder (Search, Calendar, Wrench, Lock, Checkmark)                                   |
|                                                                                                  |
| MOLECULES:                                                                                       |
|  - SearchBar (Input + Clear button + Submit trigger)                                             |
|  - FilterGroup (Facet heading + Checkbox list + Count pill)                                      |
|  - MetricStatCard (Metric value + Label + Subtext)                                               |
|  - BreadcrumbTrail (Hierarchical path navigation links)                                          |
|                                                                                                  |
| ORGANISMS:                                                                                       |
|  - AppHeader (Logo, Global Search, Role Badge, User Menu)                                        |
|  - AppSidebar (Role-governed hierarchical navigation links)                                      |
|  - EquipmentCard (Thumbnail, Title, Specs, Status badge, Action buttons)                         |
|  - EquipmentDataTable (Dense metadata table with sortable columns and inline actions)             |
|  - MasterCalendar (Time-grid scheduler with colored reservation blocks)                          |
|  - BookingModal (Slot summary, grant code input, safety checklist, submit CTA)                   |
+--------------------------------------------------------------------------------------------------+
```

---

### 8.3 Major User Flows

#### Flow 1: Equipment Discovery & Reservation (Researcher)
```
[Researcher Home / Catalog] ──> [Filter by Category & Available Status]
       │
       ▼
[Select Instrument (e.g. JEOL TEM)] ──> [Review Specs & Safety Rules]
       │
       ▼
[Click "Book Time Slot"] ──> [Select Open Slot in Calendar Grid]
       │
       ▼
[Enter Project Grant Code & Acknowledge SOPs] ──> [Submit Booking Request]
       │
       ▼
[Receive Pending Confirmation] ──> [View Status on Researcher Dashboard]
```

#### Flow 2: Booking Approval (Lab Manager)
```
[Lab Manager Dashboard] ──> [Open Booking Approvals Queue]
       │
       ▼
[Review Requester, Time Window, Grant Code & Safety Clearance]
       │
       ├──> [Approve Request] ──> [Slot Locked on Master Calendar] ──> [User Notified]
       │
       └──> [Decline Request] ──> [Enter Rejection Reason] ──> [User Notified]
```

#### Flow 3: Maintenance & Calibration Logging (Lab Technician)
```
[Lab Technician Dashboard] ──> [Open Calibration Queue]
       │
       ▼
[Select Instrument Due for Calibration] ──> [Click "Log Calibration"]
       │
       ▼
[Set Status to "Calibrating" (Blocks User Bookings)]
       │
       ▼
[Perform Calibration & Record Offset Results]
       │
       ▼
[Record Next Due Date & Attach Certificate Reference]
       │
       ▼
[Submit Record] ──> [Status Restores to "Available"]
```

#### Flow 4: Department Resource Review (Department Head)
```
[Department Head Dashboard] ──> [Open Department Utilization Overview]
       │
       ▼
[Review Equipment Utilization Bars & High-Demand Bottlenecks]
       │
       ▼
[Inspect Inter-Department Sharing Balance Sheet]
       │
       ▼
[Export Summary Report for Planning Review]
```

#### Flow 5: User Onboarding & Role Assignment (System Administrator)
```
[User Submits Registration Form] ──> [System Admin Dashboard: User Management]
       │
       ▼
[Verify University Email & Affiliation] ──> [Assign Verified Role (e.g. LAB_TECHNICIAN)]
       │
       ▼
[Save User Record] ──> [Account Activated]
```

---

## 9. Navigation / Role Matrix

This matrix defines access permissions across all platform views for the six user roles:
* **F (Full Access):** Can view, create, edit, delete, and perform all actions.
* **A (Action / Operational):** Can view records and perform role-specific operational actions.
* **R (Read Only):** Can view records and status without modification rights.
* **- (Access Denied):** Route is hidden from navigation and returns HTTP 403 if accessed directly.

| Screen / Feature Route | Researcher / Student | Lab Technician | Lab Manager | Department Head | Institution Admin | System Admin |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **Landing Page (`/`)** | R | R | R | R | R | R |
| **Authentication (`/login`, `/register`)** | F | F | F | F | F | F |
| **Equipment Catalog (`/catalog`)** | R | R | R | R | R | R |
| **Equipment Detail (`/catalog/:id`)** | R | R | R | R | R | R |
| **Create Booking (`/catalog/:id/book`)** | **A** | **A** | **A** | **A** | **A** | **A** |
| **My Bookings (`/bookings`)** | F (Own) | F (Own) | F (Own) | F (Own) | F (Own) | F (All) |
| **Booking Approvals Queue** | - | - | **A (Lab)** | **A (Dept)** | **A (Campus)** | **F (Override)**|
| **Add Equipment (`/equipment/new`)** | - | **A** | **F** | - | **F** | **F** |
| **Edit Equipment (`/equipment/:id/edit`)**| - | - | **F** | - | **F** | **F** |
| **Maintenance Hub (`/maintenance`)** | - | **F** | **F** | - | R | **F** |
| **Calibration Queue (`/calibration`)** | - | **F** | **F** | - | R | **F** |
| **Department Analytics (`/analytics`)** | - | - | R (Lab) | **F (Dept)** | **F (Campus)** | **F (All)** |
| **User Administration (`/admin/users`)** | - | - | - | - | - | **F** |
| **System Health (`/admin/health`)** | - | - | - | - | - | **F** |
| **Role Dashboard: Researcher** | **F** | - | - | - | - | R (Preview) |
| **Role Dashboard: Technician** | - | **F** | - | - | - | R (Preview) |
| **Role Dashboard: Lab Manager** | - | - | **F** | - | - | R (Preview) |
| **Role Dashboard: Department Head** | - | - | - | **F** | - | R (Preview) |
| **Role Dashboard: Institution Admin** | - | - | - | - | **F** | R (Preview) |
| **Role Dashboard: System Admin** | - | - | - | - | - | **F** |

---

## 10. Architectural Traceability & Unresolved Backend / API Questions

To preserve documentation integrity and adhere to the project rules, the technical items below are documented for coordination with the backend team:

### 10.1 Frontend Design Assumptions
1. **JWT Authentication:** The frontend assumes standard JWT tokens or HTTP-only cookies (`withCredentials: true` in `src/services/api.js`). The decoded user context is assumed to provide `userId`, `email`, `fullName`, `role`, `departmentId`, and `institutionId`.
2. **Role Representation:** The user role is received as a single string matching `User.Role` in `User.java`.
3. **Equipment Schema:** The catalog wireframe assumes equipment records will include fields for `id`, `name`, `modelNumber`, `serialNumber`, `category`, `description`, `departmentId`, `building`, `roomNumber`, `operationalStatus`, `hourlyRechargeRate`, `lastCalibrationDate`, and `nextCalibrationDue`.
4. **Booking Model:** Bookings require an equipment reference, start time, end time, user reference, project grant billing code, and approval status (`PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`, `COMPLETED`).

### 10.2 Open Questions for the Backend Team

1. **Authentication Token Delivery & CSRF:**
   - *Question:* Will authentication use HTTP-only cookies (requiring CSRF tokens for mutating requests) or `Authorization: Bearer <token>` stored in frontend memory?
   - *Context:* `SecurityConfig.java` has `.csrf(csrf -> csrf.disable())` and `withCredentials: true` is set in `api.js`. Need confirmation before implementing React auth interceptors in subsequent tasks.

2. **Equipment & Booking Pagination Contract:**
   - *Question:* Will catalog endpoints return Spring Data `Page<Equipment>` (`{ content: [...], totalElements: 48, totalPages: 5, pageable: { pageNumber: 0 } }`) or an unpaged list?
   - *Impact:* Determines whether the catalog uses numbered pagination controls or infinite scrolling.

3. **Booking Conflict Resolution:**
   - *Question:* How will overlapping booking requests be arbitrated? Will the PostgreSQL database enforce exclusion constraints on `[start_time, end_time]` intervals, or is conflict resolution handled in application logic during Lab Manager approval?

4. **Multi-Role User Contexts:**
   - *Question:* Can a user (such as a Principal Investigator) hold both `RESEARCHER_STUDENT` and `LAB_MANAGER` roles? If so, does the backend support role switching in a single session?

5. **Notification Push Mechanism:**
   - *Question:* Will real-time booking approval notices and maintenance alerts be delivered via WebSockets (STOMP), Server-Sent Events (SSE), or short-interval polling against `GET /api/notifications`?

6. **File Storage for SOPs and Certificates:**
   - *Question:* Where will uploaded equipment manuals, safety SOPs, and calibration certificates be stored (local file system, cloud object storage, or database byte arrays)?

---
*End of Planning Specification Document T11-ui-wireframes.md*
