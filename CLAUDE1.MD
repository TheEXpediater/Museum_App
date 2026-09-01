# CLAUDE.md — Final Handoff, Setup, Deployment & Local Runtime

## Role

Act as a **senior Kotlin/Android engineer, Python backend engineer, deployment engineer, and software handoff engineer**.

You are responsible for completing the project so another person can receive the project, install the required tools, restore the provided data/assets, start the backend, install the APK, and run the museum system with minimal technical knowledge.

Work autonomously. Do not repeatedly stop to ask for permission for ordinary implementation, cleanup, testing, configuration, or bug fixing.

Do not redesign the application unnecessarily. Preserve the current UI and previously completed UI work unless a change is required for functionality, reliability, or deployment.

---

# 1. FINAL OBJECTIVE

The final project must be easy for a new person to operate on a Windows laptop.

The intended workflow is:

```text
1. Receive project folder
2. Receive image ZIP
3. Put image ZIP in project root
4. Run Setup
5. Run Run
6. Install APK on Android phone
7. Phone connects to laptop through the same local network
8. Museum application works locally
```

The recipient should NOT need to manually type Python commands, install packages one by one, configure MongoDB manually, or understand the internal architecture.

The project should provide clear scripts/launchers for setup and runtime.

---

# 2. INTERNET POLICY

This project has two different operating modes:

## A. Setup / Installation

Internet access MAY be required during initial setup.

Setup may use the internet to:

* download Python if required
* download Node.js if required
* download Docker Desktop if required or provide instructions for installation
* install Python dependencies
* install Node dependencies
* download required model files
* download Gradle dependencies
* download Android dependencies
* obtain other required packages/tools

The setup process should detect whether dependencies are already installed before attempting to download them.

Do not unnecessarily reinstall existing tools.

## B. Normal Museum Runtime

After setup is complete, the museum system should operate **without internet access**.

The normal runtime must use:

```text
Android Phone
      │
      │ Local Wi-Fi / Mobile Hotspot
      │
      ▼
Windows Laptop
      │
      ├── Python Backend
      ├── MongoDB
      ├── AI Model
      └── Vector / Recognition Data
```

The application must NOT depend on:

* cloud servers
* external APIs
* Firebase for normal backend communication
* public DNS
* internet-based service discovery
* external authentication services
* internet access for recognition
* online image processing
* third-party hosted databases

Any functionality required by the museum application must run locally wherever the existing architecture permits.

If the project currently contains an optional internet-dependent feature, preserve it only where necessary and clearly separate it from the required offline/local runtime.

---

# 3. LOCAL NETWORK ARCHITECTURE

The Android application communicates directly with the Python backend running on the laptop.

The Android application must never use:

```text
localhost
127.0.0.1
```

as the laptop backend address during normal Android operation.

`localhost` on Android means the Android device itself.

The backend must listen on the laptop's LAN interface, for example:

```text
0.0.0.0:8000
```

The exact port must come from the existing project configuration.

Do not hardcode a specific laptop IP address.

---

# 4. SIMPLE BACKEND CONNECTION STRATEGY

Do NOT implement complicated automatic discovery such as:

* mDNS
* Bonjour
* Zeroconf
* NSD
* cloud discovery
* internet-based discovery

The application only needs a simple and reliable local-IP mechanism.

The Android application should:

```text
App starts
   ↓
Check current network
   ↓
Try previously saved backend IP
   ↓
Backend reachable?
   ├── YES → Connect
   └── NO  → Check current local network
                  ↓
             Backend found?
               ├── YES → Connect
               └── NO  → Show IP entry screen
```

---

# 5. SAVED BACKEND IP

When a backend connection succeeds, save:

```text
host
port
```

Example:

```text
192.168.43.100:8000
```

Use Android's existing local persistence mechanism if one is already present.

Do not save an IP merely because the user entered it.

Only save it after the backend health check succeeds.

---

# 6. NETWORK CHANGE BEHAVIOR

The saved IP is only a convenience, not a permanent configuration.

Example:

```text
Phone hotspot
    ↓
Laptop connected
    ↓
Backend = 192.168.43.100
    ↓
App connects
    ↓
IP saved
```

Later:

```text
Phone switches to another Wi-Fi
    ↓
192.168.43.100 no longer works
    ↓
App tests saved IP
    ↓
Fails
    ↓
Check current local network
    ↓
Backend unavailable
    ↓
Show manual IP screen
```

The application must NOT remain stuck trying the old address indefinitely.

When the current network changes, the user must be able to provide a new laptop IP easily.

---

# 7. MANUAL IP CONNECTION SCREEN

When no backend is detected, display a dedicated state such as:

```text
Backend Not Found

The museum backend could not be detected on this network.

Make sure the phone and laptop are connected to the same
Wi-Fi network or mobile hotspot.

Enter the laptop's backend address:

[ 192.168.x.x:8000 ]

[ Connect ]
```

When the user presses Connect:

```text
Entered IP
   ↓
Backend health request
   ↓
Valid museum backend?
   ├── NO → Show connection error
   └── YES
        ↓
     Save IP
        ↓
     Continue
```

Do not save failed addresses.

---

# 8. BACKEND HEALTH CHECK

Use the project's existing backend health/status endpoint when possible.

Do not create duplicate health endpoints unnecessarily.

A successful TCP connection alone is not enough.

The Android application should verify that it is communicating with the expected museum backend.

Connection state must be centralized.

Use appropriate states such as:

```text
CheckingNetwork
CheckingSavedBackend
SearchingLocalNetwork
BackendConnected
BackendNotFound
ManualIpEntry
ConnectingToBackend
ConnectionFailed
```

Screens must not independently implement their own backend discovery or IP storage logic.

---

# 9. BACKEND CONNECTION MANAGER

Create or reuse a centralized backend connection manager/service.

It should be the single source of truth for:

* current backend host
* current backend port
* connection status
* health checks
* retry behavior
* saved address
* manual IP connection
* network changes

Visitor screens, Admin screens, recognition, artifact operations, and other backend-dependent components must use the shared connection system.

Do not duplicate backend connection logic across ViewModels and Composables.

---

# 10. TIMEOUTS AND FAILURE HANDLING

All connection attempts must have short, reasonable timeouts.

Do not allow the UI to freeze while checking an IP.

Do not retry an unreachable backend forever.

When a connection fails:

```text
Show useful error
Allow retry
Allow new IP
Keep UI responsive
```

Do not crash the application.

Do not falsely report:

```text
Connected
Ready
Scanner Ready
AI Ready
```

when the required backend/AI component is actually unavailable.

---

# 11. AI / OPENCLIP RUNTIME

The OpenCLIP/recognition system must be available without requiring the user to open:

```text
Admin → Recognize
```

Visitor recognition must never depend on visiting the Admin recognition screen first.

Use one centralized AI lifecycle/manager.

The AI model should:

* load when required by the application lifecycle
* avoid unnecessary repeated initialization
* expose real readiness state
* reuse the loaded model
* fail gracefully
* provide clear loading/error states

Visitor Scan must show a genuine state.

Examples:

```text
Preparing AI...
```

```text
Scanner Ready
```

Do NOT display image totals/counts to visitors.

Do not use:

```text
Ready — 123 images
```

or similar.

Use simple visitor-facing status such as:

```text
Scanner Ready
```

---

# 12. CAMERA PERMISSION

Camera permission belongs to the Visitor Scan flow.

Opening Admin → Recognize must not be required to trigger camera permission.

Handle:

* first launch
* permission granted
* permission denied
* permanently denied
* returning from Android settings

Do not claim scanner readiness before camera and required AI components are actually ready.

---

# 13. DATABASE

The final handoff must include a reliable MongoDB backup/restore process.

The recipient should not need to manually recreate database records.

Setup must:

1. Detect whether Docker is installed/running.
2. Start the required MongoDB service/container.
3. Create required storage directories/volumes.
4. Restore the provided MongoDB backup.
5. Verify that restoration succeeded.
6. Confirm expected collections/data exist.
7. Ensure the backend can connect successfully.

Do not store unnecessary raw MongoDB runtime files inside the repository when a proper backup is already provided.

---

# 14. VECTOR / AI INDEX DATA

The vector/index data required by OpenCLIP recognition must be included as a restorable deployment asset.

Setup must:

1. Detect whether vector/index data already exists.
2. Restore it when missing.
3. Avoid rebuilding it unnecessarily.
4. Verify that the index is readable.
5. Verify that the AI recognition pipeline can load it.

The final runtime should not require internet access to recreate the AI library.

---

# 15. IMAGE ZIP

The recipient will separately receive one ZIP containing the museum image assets.

The expected process is:

```text
Project Root
│
├── Museum_App
├── image-assets.zip
├── setup.bat
├── run.bat
└── ...
```

The recipient should only need to place the provided ZIP in the expected project-root location.

Setup should then:

1. Detect the ZIP.
2. Validate that it is the expected asset archive.
3. Extract it to the correct upload/assets directory.
4. Avoid unnecessary duplicate extraction.
5. Process/index assets only when required.
6. Verify that the expected images exist.
7. Continue setup only after the asset stage is successful.

The image ZIP itself does not need to be committed into Git.

---

# 16. SETUP SCRIPT

Provide a simple Windows setup launcher.

Recommended:

```text
setup.bat
```

or an equivalent user-friendly executable launcher if already implemented.

The user experience should be:

```text
Double-click Setup
        ↓
Check requirements
        ↓
Create required directories
        ↓
Check Docker
        ↓
Configure environment
        ↓
Restore database
        ↓
Restore vector/index data
        ↓
Extract image assets
        ↓
Install missing dependencies
        ↓
Verify backend
        ↓
Verify AI
        ↓
Complete
```

Setup must be safe to run more than once.

It must be **idempotent**.

Running setup a second time should not blindly destroy existing data.

---

# 17. ENVIRONMENT CONFIGURATION

Provide:

```text
.env.example
```

The setup process should create/configure the real:

```text
.env
```

Do not commit secrets or machine-specific credentials.

Do not hardcode another person's IP address into source code.

Machine-specific configuration should be generated during setup or supplied through environment/configuration files.

---

# 18. RUN SCRIPT

Provide a simple:

```text
run.bat
```

The user should be able to double-click Run instead of opening CMD and manually typing commands.

Run should:

1. Start required Docker services.
2. Start MongoDB.
3. Start the Python backend.
4. Verify backend health.
5. Confirm the backend is listening on the LAN.
6. Display the laptop's current usable local IP address.
7. Display the backend port.
8. Display the address that can be entered into the Android app.

Example:

```text
========================================
 MUSEUM BACKEND
========================================

Status: RUNNING

Local:
http://localhost:8000

Network:
http://192.168.43.100:8000

Android Backend Address:
192.168.43.100:8000

Keep this window running while using the APK.
========================================
```

The actual port and addresses must be generated dynamically.

---

# 19. WINDOWS FIREWALL / LAN ACCESS

The setup/run process must verify that Windows firewall rules do not prevent the Android phone from reaching the backend.

Do not disable Windows Firewall globally.

If required, add a narrowly scoped inbound rule for the museum backend port.

Document what the setup process changes.

---

# 20. LOCAL-ONLY RUNTIME

The final museum system should work when the laptop and Android phone have no internet connection, as long as they are connected to the same local network/hotspot.

Example:

```text
Internet: OFF

Phone
  │
  │ Wi-Fi / hotspot
  │
  ▼
Laptop
  │
  ├── Backend
  ├── MongoDB
  ├── OpenCLIP
  └── Vector data
```

This must still allow:

* Visitor entry
* Visitor catalogue
* Visitor scanner
* AI recognition
* Admin login
* Artifact management
* Artifact updates
* AI library management
* Backend API requests
* Database operations

Do not introduce an internet dependency into these workflows.

---

# 21. HOTSPOT SUPPORT

The application must support mobile-hotspot scenarios.

Example:

```text
Phone hotspot ON
       ↓
Laptop connects to phone hotspot
       ↓
Backend starts on laptop
       ↓
Phone app checks saved IP
       ↓
If old IP fails:
check current local network
       ↓
If backend found:
connect
       ↓
Otherwise:
ask for laptop IP
```

The implementation must not assume that every network uses the same subnet.

Do not hardcode:

```text
192.168.43.x
192.168.1.x
10.0.0.x
```

Those are examples only.

---

# 22. APPLICATION STARTUP

Startup must be deliberate.

Do not open the main application dashboard/content and only later discover that the backend or required AI services are unavailable.

Use proper loading states.

Example:

```text
Starting Museum System
        ↓
Checking backend
        ↓
Checking AI
        ↓
Preparing application
        ↓
Ready
```

While loading, display a real loading screen/state.

Do not reveal the dashboard prematurely.

---

# 23. PREVIOUS UI REQUIREMENTS TO PRESERVE

Do NOT undo previously completed fixes.

Preserve these behaviors:

### Saving artifact changes

After successfully updating an artifact:

```text
Updated!
```

or the established success feedback should appear.

### No changes made

If Save Changes is pressed without modifications:

show a modal explaining that there are no changes.

Provide actions similar to:

```text
No Changes Made

Nothing was changed.

[ Stay ]
[ Back to Artifact List ]
```

Do not perform a fake save.

### Filters

Keep the improved compact/floating filter experience.

Do not reintroduce the large filter area.

### AI Library

Preserve the Artifact AI Library status and actions already implemented.

### Visitor scanner

Keep the compact visitor-facing scanner UI.

Do not reintroduce image-count information.

---

# 24. PROJECT CLEANUP

Before final handoff, inspect the entire repository.

Remove only files that are genuinely unnecessary.

Look for:

* duplicate images already embedded/packaged elsewhere
* obsolete ZIP archives
* old EXE files
* stale build artifacts
* obsolete MongoDB runtime data
* temporary development files
* duplicate generated assets
* abandoned scripts
* outdated documentation
* unused test artifacts

Do NOT blindly delete files.

Before deleting anything:

1. Search for references.
2. Determine whether it is required at runtime.
3. Determine whether it is required for setup/restore.
4. Determine whether it is intentionally included as a backup.
5. Remove only when safe.

The final root should be clean and understandable.

---

# 25. RECOMMENDED FINAL ROOT STRUCTURE

Use the existing project structure where practical, but aim for something similar to:

```text
Museum_App/
│
├── android/
│   └── ...
│
├── backend/
│   ├── app/
│   ├── data/
│   ├── scripts/
│   └── ...
│
├── data/
│   ├── mongodb-backup/
│   └── vector-index/
│
├── deployment/
│   ├── ...
│
├── image-assets.zip
├── setup.bat
├── run.bat
├── .env.example
├── README.md
├── CLAUDE.md
└── ...
```

Do not force this exact structure if it conflicts with the current project architecture.

The objective is a clean and understandable handoff.

---

# 26. README

Create/update the final README so a new recipient can operate the project without asking the original developer basic setup questions.

The README should explain:

### Requirements

* Windows version
* Android device requirements
* Docker requirement
* Python requirement
* Node requirement if needed
* disk space
* network requirements

### Initial setup

```text
1. Put image-assets.zip in project root.
2. Run setup.bat.
3. Wait for setup verification.
```

### Running the system

```text
1. Start the laptop.
2. Connect phone and laptop to the same Wi-Fi/hotspot.
3. Run run.bat.
4. Install/open APK.
5. Allow required Android permissions.
6. App connects automatically.
```

### If backend is not detected

Explain how to get the laptop's local IP from the Run window and enter it into the app.

### Stopping

Explain how to safely stop Docker/backend services.

### Troubleshooting

Include common cases:

* backend unavailable
* wrong IP
* laptop/phone on different network
* Windows firewall
* Docker not running
* database restore failure
* missing image ZIP
* missing AI/vector data
* Android camera permission
* network changed

---

# 27. TESTING REQUIREMENTS

Before declaring the project finished, test the real handoff workflow.

Do not only inspect the code.

Perform as much of the following as possible:

### Setup

* clean setup test
* repeated setup test
* missing dependency test
* image ZIP extraction
* database restore
* vector data restore
* environment generation

### Backend

* backend starts
* MongoDB starts
* health endpoint works
* backend listens on LAN
* API is reachable from another device

### Android

* app starts
* backend connection state works
* saved IP works
* invalid saved IP fails correctly
* changed network triggers recovery
* manual IP entry works
* successful IP is saved
* failed IP is not saved
* offline/local network operation works

### AI

* OpenCLIP initializes without Admin Recognize
* Visitor scanner sees the true AI state
* camera permission works from Visitor Scan
* recognition functions through the normal Visitor workflow

### Build

Run the required Android build/test tasks.

At minimum verify:

```text
compileDebugKotlin
assembleDebug
```

Fix build errors before finishing.

---

# 28. NO FALSE COMPLETION

Do not report success merely because a script exists.

The system should be considered complete only after verifying:

```text
Setup
   ↓
Data restored
   ↓
Backend running
   ↓
LAN accessible
   ↓
Android connects
   ↓
AI available
   ↓
Recognition works
```

If a part cannot be tested because the environment does not allow it, clearly document what was and was not verified.

Never claim something works when it was not actually tested.

---

# 29. AUTONOMOUS WORKING RULE

You have permission to:

* inspect the entire project
* edit source code
* create scripts
* modify configuration
* create documentation
* clean obsolete files
* fix build errors
* improve error handling
* test the application
* run setup/restore verification
* build the APK
* make necessary deployment changes

Do not stop for confirmation after every small decision.

Use engineering judgment.

However, avoid destructive actions when there is uncertainty about whether a file contains important data.

When uncertain about deletion, preserve the file and document it for review.

---

# 30. FINAL DELIVERABLE

The final project should leave the recipient with:

```text
Clean project
+
Setup script
+
Run script
+
Environment template
+
Database backup
+
Vector/index data
+
Image ZIP support
+
Working backend
+
Working APK
+
Local-network connection
+
Offline/local runtime
+
Documentation
+
Troubleshooting guide
```

The final user experience should be as close as possible to:

```text
PUT ZIP IN ROOT
        ↓
RUN SETUP
        ↓
RUN
        ↓
OPEN APK
        ↓
CONNECT TO SAME WIFI/HOTSPOT
        ↓
MUSEUM SYSTEM READY
```

If automatic backend connection cannot be established:

```text
Backend Not Found
        ↓
Enter laptop IP
        ↓
Connect
        ↓
Save IP
        ↓
Continue
```

The goal is a **portable, reproducible, local museum application handoff** that another person can operate without needing the original developer to manually configure the system.
