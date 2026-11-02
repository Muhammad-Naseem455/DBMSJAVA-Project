# 💊 MediCircle – Unused Medicine Redistribution Network

[![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0%2B-4479A1?style=for-the-badge&logo=mysql&logoColor=white)](https://www.mysql.com/)
[![HTML5/CSS3](https://img.shields.io/badge/Frontend-HTML5%20%2F%20CSS3%20%2F%20JS-E34F26?style=for-the-badge&logo=html5&logoColor=white)](https://developer.mozilla.org/en-US/)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg?style=for-the-badge)](LICENSE)

> A full-stack, dual-interface (Web + CLI) healthcare redistribution system built for **2nd Year B.E. CSE** integrating core **Object-Oriented Programming (Java)** and **Database Management Systems (DBMS)** concepts.

---

## 📌 Problem Statement
Millions of rupees worth of unexpired, sealed medicines are discarded annually by households and medical representatives, while charitable clinics and verified NGOs face severe shortages of basic life-saving drugs. 

Traditional donation systems fail because:
1. Donated medicines sit idle in warehouses and expire before dispatch.
2. Transit times between distant cities cause drugs to spoil.

## 💡 The Solution: MediCircle
MediCircle implements a **Smart Expiry-First Hyperlocal Priority Matching Algorithm**:
1. **Tier 1 (City Proximity):** Prioritizes donors located in the same city to minimize logistics and transit delays.
2. **Tier 2 (FEFO - First Expired, First Out):** Ranks medicines nearing expiration higher so they are claimed and administered before spoiling.
3. **Multi-tier Safety:** Database triggers reject donations with `< 30 days` shelf life and prevent donors from reserving their own medicines (conflict-of-interest check).

---

## 🏗️ Architecture & Concepts

```
┌────────────────────────────────────────────────────────┐
│                   Frontend (Web / CLI)                 │
│       • index.html (Responsive SPA, Role Portals)      │
│       • ConsoleApp.java (Interactive Terminal Shell)   │
└───────────────────────────┬────────────────────────────┘
                            │ HTTP REST / Standard I/O
┌───────────────────────────▼────────────────────────────┐
│                  Java Backend (JDK 17+)                │
│       • WebServer.java (Built-in HttpServer)           │
│       • MediService.java (ACID Transactions, Locks)    │
│       • Models.java (Polymorphism, Inheritance)        │
└───────────────────────────┬────────────────────────────┘
                            │ JDBC Connection / Dual-Mode
┌───────────────────────────▼────────────────────────────┐
│                    DBMS Layer (MySQL)                  │
│       • Tables (users, medicines, requests, audit_log) │
│       • Triggers (trg_med_check, trg_prevent_self_res) │
│       • Views (v_available, v_impact)                  │
│       • Stored Procedure (mark_expired)                │
└────────────────────────────────────────────────────────┘
```

### Syllabus Mapping (Java OOP + DBMS)

| Subject | Academic Concept | Implementation in MediCircle |
| :--- | :--- | :--- |
| **Java OOP** | **Abstraction** | `abstract class Person` defines base template with abstract `getRole()`. |
| **Java OOP** | **Inheritance** | `Donor` and `Ngo` extend `Person`. |
| **Java OOP** | **Polymorphism** | Dynamic method dispatch in `describe()` and `getRole()`. |
| **Java OOP** | **Encapsulation** | Private variables with strictly guarded accessors. |
| **Java OOP** | **Interfaces** | `Medicine implements Comparable<Medicine>` for natural sorting. |
| **Java OOP** | **Custom Exceptions** | `MediException` handles domain rule violations. |
| **DBMS** | **Relational Schema** | Normalized 3NF tables with PK, FK (`ON DELETE CASCADE`), `CHECK`, `UNIQUE`. |
| **DBMS** | **Triggers** | `trg_med_check` (enforces 30-day safety) & `trg_prevent_self_reserve` (anti-hoarding). |
| **DBMS** | **Concurrency & ACID** | `SELECT ... FOR UPDATE` pessimistic row locking prevents double-booking. |
| **DBMS** | **Views & Procedures** | `v_available`, `v_impact`, and `mark_expired()` procedure. |

---

## 📂 Repository Structure

```text
MediCircle/
├── src/
│   └── medicircle/
│       ├── Models.java         # OOP Core Classes (Person, Donor, Ngo, Medicine)
│       ├── MediService.java    # Business Logic & JDBC Access Layer
│       ├── ConsoleApp.java     # Interactive Command Shell Application
│       └── WebServer.java      # Zero-framework Built-in Web Server (Port 8080)
├── web/
│   └── index.html              # Responsive Web UI (Login/Register & Role Portals)
├── schema.sql                  # Full MySQL DDL (Tables, Triggers, Views, Seed data)
├── run_console.bat             # 1-Click launcher for Terminal App
├── start_website.bat           # 1-Click launcher for Web Application
├── .gitignore                  # Git ignore rules
├── LICENSE                     # MIT License
└── README.md                   # Project Documentation
```

---

## 🚀 Quick Start Guide

### Prerequisites
- **Java JDK 17+** installed (`java -version`, `javac -version`)
- *(Optional)* **MySQL 8.0+** — *Note: The system automatically falls back to an embedded relational store if MySQL is offline, running 100% out of the box!*

### Running on Windows
1. Clone the repository:
   ```bash
   git clone https://github.com/YOUR_USERNAME/MediCircle.git
   cd MediCircle
   ```

2. Compile:
   ```cmd
   javac -d out src/medicircle/*.java
   ```

3. Launch:
   - **To run Web App:** Double-click `start_website.bat` or run:
     ```cmd
     java -cp out medicircle.WebServer
     ```
     Open `http://localhost:8080` in your browser.
   - **To run Terminal App:** Double-click `run_console.bat` or run:
     ```cmd
     java -cp out medicircle.ConsoleApp
     ```

---

## 👥 Demo Credentials
Pre-seeded for instant viva demonstration:

| Role | Name | Email / ID | City |
| :--- | :--- | :--- | :--- |
| **Donor** | Ravi Kumar | `ravi@mail.com` (ID: 1) | Chennai |
| **Donor** | Priya S | `priya@mail.com` (ID: 2) | Bengaluru |
| **NGO** | Helping Hands NGO | `hh@ngo.org` (ID: 3) | Chennai |
| **NGO** | CareFirst Trust | `cf@ngo.org` (ID: 4) | Bengaluru |

---

## 📄 License
This project is licensed under the [MIT License](LICENSE).
