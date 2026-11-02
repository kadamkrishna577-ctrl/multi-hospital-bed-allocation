# 🏥 Multi-Hospital Critical Care and Dynamic Bed Allocation System

**Course:** CS1207 Object-Oriented Programming Lab  
**Team:** Group 15  

---

## 📖 What is this Project About?

In many hospitals, staff still use phone calls or simple spreadsheets to find open ICU beds and medical machines. During an emergency, this causes dangerous delays and mistakes:
* A critical patient arrives, and all beds are full, so nobody knows which stable patient can be safely moved.
* Two nurses at different desks accidentally give the same open bed to two different patients at the same time.
* A patient is put into a bed before the cleaning staff have finished sanitizing it.

Our **Multi-Hospital Critical Care and Dynamic Bed Allocation System** is a Java desktop software connected to a MySQL database that solves these problems automatically. 

### What Our Software Does:
1. **Finds the Best Bed Automatically:** When a nurse registers a patient, the system checks all free beds and picks the one that has the right machines (like a ventilator or oxygen tank) for that patient.
2. **Handles Emergency Bed Swaps:** If a patient in life-threatening danger (`LEVEL_1`) arrives and the ward is full, the system finds the healthiest stable patient in that ward, moves them to the very front of a waiting list, and gives their bed to the emergency patient in under 2 seconds.
3. **Stops Double-Booking:** Even if 50 nurses click to book the last free bed at the exact same second, our code locks the bed so only the first nurse gets it and the others see a "Bed Taken" message.
4. **Tracks Medical Machines & Repairs:** Doctors can attach or remove machines from any bed. The system counts how many hours each machine runs and automatically locks it for repair when it reaches its safe limit.
5. **Reserves Beds for Ambulances:** Paramedics on the road can hold a bed 15 minutes before they arrive (`RESERVED_EN_ROUTE`). If that hospital suddenly fills up, the system automatically sends the ambulance to the nearest partner hospital with an open bed.
6. **Blocks Beds Near Contagious Patients:** If a patient has a contagious virus, the system automatically locks the empty beds next to them so weaker patients aren't placed nearby.
7. **Updates Meals and Bills Automatically:** Whenever a patient moves to a new room or gets a new machine, their kitchen food order follows them (or switches to IV fluids in the ICU) and their hourly bill updates by itself.
8. **Saves a Permanent History Diary:** Every bed swap, room change, and priority change is saved with the time and user ID in a database table that nobody can edit or delete.

---

## 🛠️ Tools & OOP Concepts We Used

* **Java (JDK 21+):** Written in pure Object-Oriented Java (no external frameworks like Spring or Hibernate).
* **Java Swing:** Used to build the desktop screens and instant pop-up alerts for hospital staff.
* **MySQL & JDBC:** Saves all data permanently. If an error happens in the middle of moving a patient, the database automatically undoes the unfinished move (**ACID Transactions**) so no patient data is lost.

### Simple Explanation of Our 5 Design Patterns:
* **Decorator Pattern (Adding Machines to Beds):** Instead of creating a new bed every time a patient needs a machine, we "wrap" an existing bed object with machine add-ons (`VentilatorDecorator`, `OxygenCylinderDecorator`, etc.). This automatically adds the machine's cost to the bed's hourly price.
* **Strategy Pattern (Choosing How to Search):** Lets the nurse choose *how* the computer searches for a bed—either finding the **closest bed** or the **best-equipped bed**.
* **State Pattern (Bed Status Rules):** Controls what status a bed is in (`VACANT`, `OCCUPIED`, `RESERVED_EN_ROUTE`, `REQUIRES_SANITIZATION`, `RESTRICTED_BUFFER`). For example, if a bed is waiting to be cleaned, the code blocks anyone from booking it.
* **Observer Pattern (Automatic Updates):** Works like a notification bell. Whenever a patient changes beds, the Billing, Kitchen, and Pop-up Alert modules are notified automatically.
* **DAO Pattern (Database Helpers):** All SQL database commands are kept neatly inside `dao/` classes instead of mixing them with screen or business code.

---

## 👥 How the Work is Divided (4 Team Members)

To make sure all 4 team members can work on GitHub at the same time without overwriting each other's files, each person owns specific folders and use cases:

---

### 👤 1. Saksham — Hospital Setup, Patient Registration & Bed Search
* **Use Cases Owned:**
  * **U1:** Register Patient & Find a Bed
  * **U8:** Manage Hospital Rooms & Save History Diary
* **What Saksham Builds:**
  * **Hospital Setup:** Creates the classes for `Hospital`, `Ward`, `Room`, and `Patient`. Allows the Admin to add/remove rooms and set hourly bed prices.
  * **Bed Search Engine:** Writes the **Strategy Pattern** (`ClosestBedStrategy` and `BestEquippedBedStrategy`) so nurses can find the best matching free bed for a patient.
  * **Login & History Diary:** Builds the login screen (checking if the user is an Admin, Nurse, Doctor, Paramedic, or Cleaning Staff) and `AuditDAO` to save permanent history logs.
* **Folders Owned:** `config/`, `model/hierarchy/`, `service/strategy/`, `ui/admin/`

---

### 👤 2. Krishna — Medical Machines, Emergency Bed Swaps & Waiting List
* **Use Cases Owned:**
  * **U2:** Emergency Bed Swap (Bump Stable Patient for a Level 1 Emergency)
  * **U3:** Add Medical Machines & Track Repair Hours
* **What Krishna Builds:**
  * **Machine Add-ons (Decorator Pattern):** Builds `BaseBed` and the machine wrappers (`VentilatorDecorator`, `OxygenCylinderDecorator`, `DialysisDecorator`, `CardiacMonitorDecorator`) so staff can attach or remove machines from beds at any time.
  * **Emergency Swap Engine:** Writes `EmergencyPreemptionService`. When a `LEVEL_1` patient needs a bed in a full ward, it locks the bed, moves the most stable lower-priority patient to the front of the `WaitlistManager`, gives the bed to the emergency patient, and undoes everything safely if any database error happens.
  * **Machine Hour Tracker:** Runs a background timer (`EquipmentMaintenanceService`) that counts how many hours each machine has been used and locks it when it needs repair.
* **Folders Owned:** `model/bed/`, `service/preemption/`, `ui/clinical/`

---

### 👤 3. Jatin — Contagious Isolation Zones, Kitchen Meals & Patient Billing
* **Use Cases Owned:**
  * **U4:** Lock Neighboring Beds for Contagious Viruses
  * **U7:** Update Kitchen Food & Hourly Patient Bill
* **What Jatin Builds:**
  * **Contagious Safety Zones:** Builds `WardTopologyGraph` and `IsolationContainmentService`. When a contagious patient is admitted, it finds the beds next to them (or sharing air ducts) and locks them as `RESTRICTED_BUFFER` so weaker patients aren't placed nearby.
  * **Automatic Billing & Kitchen Updates (Observer Pattern):** Builds `BillingService` and `KitchenService`. Whenever Saksham admits a patient or Krishna swaps a bed/attaches a machine, Jatin's code automatically updates the patient's hourly bill and moves their meal order to the new floor (or switches it to `IV_FLUIDS` in the ICU).
* **Folders Owned:** `model/isolation/`, `model/billing/`, `service/containment/`, `service/auxiliary/`, `ui/auxiliary/`

---

### 👤 4. Siddhant — Bed Cleaning Locks, Ambulances & Pop-Up Alerts
* **Use Cases Owned:**
  * **U5:** Reserve Bed from Ambulance & Reroute if Full
  * **U6:** Mark Room Clean & Send Pop-Up Alert
* **What Siddhant Builds:**
  * **Bed Status Locks (State Pattern):** Builds the 5 `BedState` classes (`VacantState`, `OccupiedState`, `ReservedEnRouteState`, `RequiresSanitizationState`, `RestrictedBufferState`) so a bed cannot be given to a patient until cleaning staff mark it clean.
  * **Ambulance Booking & Rerouting:** Builds `ALSAmbulance` (for heart/critical cases) and `BLSAmbulance` (for minor injuries). Holds a bed for 15 minutes while the ambulance is on the way, and redirects the ambulance to the nearest partner hospital in under 1.5 seconds if the hospital fills up.
  * **Instant Pop-Up Alerts:** Builds `AlertNotificationManager` to show pop-up messages on nurse screens in under 0.5 seconds whenever a room is cleaned, an emergency swap happens, or an ambulance is redirected.
* **Folders Owned:** `model/state/`, `model/ambulance/`, `service/dispatch/`, `ui/operations/`

---

## 📂 Folder Structure Overview

```text
multi-hospital-bed-allocation/
├── pom.xml                                  # Maven setup file (Java 21 + MySQL Driver)
├── sql/
│   └── 01_schema.sql                        # SQL script to create all MySQL database tables
└── src/
    ├── main/java/com/group15/hospital/
    │   ├── config/                          # Database connection setup [Saksham]
    │   ├── exception/                       # Custom error messages [Shared]
    │   ├── model/
    │   │   ├── enums/                       # Fixed options like LEVEL_1, VACANT, VENTILATOR [Shared]
    │   │   ├── hierarchy/                   # Hospital, Ward, Room, Patient, AuditLog [Saksham]
    │   │   ├── bed/                         # Beds, Machine Decorators & Waitlist [Krishna]
    │   │   ├── isolation/                   # Map of neighboring beds [Jatin]
    │   │   ├── billing/                     # Patient bills & Kitchen meal orders [Jatin]
    │   │   ├── state/                       # Bed status rules (Vacant, Occupied, etc.) [Siddhant]
    │   │   └── ambulance/                   # ALS/BLS Ambulances & Alert messages [Siddhant]
    │   ├── dao/                             # Database SQL query classes [All 4 Members]
    │   ├── service/
    │   │   ├── strategy/                    # Bed search engine [Saksham]
    │   │   ├── preemption/                  # Emergency bed swap & Machine hour timer [Krishna]
    │   │   ├── containment/                 # Contagious neighbor bed blocker [Jatin]
    │   │   ├── auxiliary/                   # Automatic Billing & Kitchen updater [Jatin]
    │   │   └── dispatch/                    # Ambulance tracker, Room cleaning & Pop-ups [Siddhant]
    │   └── ui/                              # Java Swing desktop screens for each role
    └── test/java/com/group15/hospital/      # Tests for 50-user concurrency & decorators
```

---

## 🚀 How to Run This Project on Your Computer

### Step 1: What You Need Installed
* **Java JDK 21** (or higher)
* **Maven** (built into IntelliJ IDEA / VS Code)
* **MySQL Server**

### Step 2: Download the Project
```bash
git clone https://github.com/kadamkrishna577-ctrl/multi-hospital-bed-allocation.git
cd multi-hospital-bed-allocation
```

### Step 3: Create the Database in MySQL
Open MySQL Workbench (or terminal) and run:
```sql
CREATE DATABASE hospital_allocation_db;
USE hospital_allocation_db;
SOURCE sql/01_schema.sql;
```
*(Note: If your MySQL password is not `password`, update it inside `src/main/java/com/group15/hospital/config/DatabaseConnection.java`).*

### Step 4: Compile & Run the Tests
To check that all code compiles cleanly:
```bash
mvn clean compile
```

To run the test that checks equipment decorators, waitlist priority, and 50 nurses clicking at the same time:
```bash
mvn test-compile exec:java -Dexec.mainClass="com.group15.hospital.ConcurrencyAndDecoratorTest" -Dexec.classpathScope="test"
```
---
