# CLAUDE.md

# Museum_App Claude Code Instructions


## 1. PROJECT ROLE

Museum_App is a professional museum artifact management and AI recognition platform.

Primary platforms:

* Android mobile application
* FastAPI backend
* MongoDB
* Qdrant
* OpenCLIP

Main application areas:

### Visitor

* Artifact catalogue
* Search
* Category filtering
* Artifact details
* AI recognition
* Museum information

### Admin

* Dashboard
* Artifact management
* Artifact creation/editing
* Image management
* AI Library
* Settings
* User/administrator management

---

# 2. CURRENT SYSTEM STATE

IMPORTANT:

The application is currently functional.

The Android application can connect to the backend and run on a physical phone.

MongoDB is being moved/used through Docker.

Qdrant is ALREADY migrated and contains existing vector data.

OpenCLIP/vector data already exists.

Therefore:

**The existing AI/vector system is NOT a migration task anymore.**

---

# 3. DATA PRESERVATION — ABSOLUTE RULE

The following are production/source-of-truth data:

* MongoDB application data
* Qdrant collections
* Existing Qdrant vectors
* OpenCLIP-generated embeddings
* Artifact records
* Artifact images

NEVER destroy, reset, or regenerate them as a troubleshooting shortcut.

DO NOT:

* Delete MongoDB databases
* Drop MongoDB collections
* Delete Docker database volumes
* Delete Qdrant volumes
* Delete Qdrant collections
* Recreate existing Qdrant collections unnecessarily
* Rebuild the vector database
* Re-index all artifacts
* Regenerate OpenCLIP embeddings
* Run destructive Docker cleanup
* Run `docker compose down -v`
* Run `docker volume rm`
* Run `docker system prune`
* Run `docker volume prune`

If a database, collection, or vector appears missing:

**Investigate first. Do not rebuild.**

If a migration/configuration issue is suspected, report the evidence before making destructive or structural changes.

---

# 4. QDRANT / OPENCLIP RULES

Qdrant already contains the application's vector data.

The existing collection is expected to be:

`artifact_images`

The AI system should use the existing vectors.

Do not modify the indexing architecture during UI work.

Do not change embedding generation.

Do not change vector dimensions.

Do not change collection names.

Do not rebuild vectors.

Do not create a new collection merely because a request returns 404.

If Qdrant reports:

* collection not found
* vector cleanup failure
* AI index unavailable
* vector status unavailable

first inspect:

1. Actual Qdrant collections
2. Actual collection name
3. Backend configuration
4. Environment variables
5. URL encoding/escaping
6. Repository configuration
7. Docker networking
8. Qdrant client configuration

Only fix the configuration/reference causing the problem.

Example:

If the backend requests an incorrectly escaped collection name while the actual collection is `artifact_images`, correct the reference/configuration.

Do NOT rebuild the collection.

---

# 5. CURRENT DEVELOPMENT PRIORITY

The priority is now:

1. Correct application loading/state behavior
2. Professional Android UI/UX redesign
3. Visitor experience
4. Admin experience
5. Reliable AI status display
6. Reliable AI Library interaction
7. Backend/data stability
8. Deployment readiness

Do not perform unrelated refactoring.

Do not redesign the backend architecture.

---

# 6. SENIOR KOTLIN ENGINEERING STANDARD

Act as a senior Android/Kotlin engineer.

Prioritize:

* Clear architecture
* Reusable components
* Strong state management
* Lifecycle awareness
* Configuration-driven behavior
* Material Design principles
* Responsive layouts
* Accessibility
* Proper loading/error/success states
* Coroutine correctness
* ViewModel responsibility
* Separation of UI and business logic
* Minimal recomposition/re-rendering
* Maintainability
* Small, focused changes

Prefer existing architecture over introducing new frameworks.

Do not introduce a new architecture pattern merely for cosmetic reasons.

Do not rewrite working screens when targeted improvements are sufficient.

---

# 7. TOKEN / REPOSITORY EFFICIENCY

DO NOT scan the entire repository.

Do not inspect unrelated files.

Do not read generated files.

Do not inspect:

* node_modules
* `.venv`
* `venv`
* build directories
* Gradle caches
* image binaries
* APKs
* generated files
* database dumps
* migration backups
* Docker runtime data
* BSON files
* ZIP files
* logs unless directly relevant

For a UI task, inspect only:

* relevant Activity
* relevant Fragment
* relevant Composable
* ViewModel
* UI state
* navigation
* theme/design system
* relevant API/service calls

For a backend issue, inspect only:

* affected route
* service
* repository
* relevant model/configuration
* relevant environment/configuration

Do not read entire files if only a small section is relevant.

Use targeted searches first.

---

# 8. CHANGE MANAGEMENT

For normal UI/UX changes:

You may inspect and implement directly when the requested behavior is unambiguous and low-risk.

Before making architectural, database, networking, or AI changes:

STOP and explain:

* What is wrong
* Evidence
* Root cause
* Files involved
* Proposed fix
* Risk

Never make a destructive or architectural change silently.

---

# 9. UI/UX PRINCIPLES

The application must feel like a professional museum product.

Visual direction:

* Modern
* Elegant
* Clean
* Premium
* Minimal
* Museum-oriented
* Easy to navigate
* Mobile-first
* Professional
* Consistent

Prioritize content over decoration.

Avoid:

* Huge headers
* Excessive empty space
* Oversized filter sections
* Tiny icons
* Tiny action buttons
* Excessive rounded cards
* Excessive shadows
* Inconsistent spacing
* Random colors
* Redundant labels
* Cluttered controls
* Prototype-looking dialogs

Reuse a consistent visual system.

---

# 10. LOADING STATE STANDARD

Never reveal a partially initialized screen.

When a screen requires API/data/model initialization:

Show a proper loading state first.

Example:

`Loading Dashboard...`

`Loading Artifacts...`

`Loading AI System...`

`Loading Settings...`

Only reveal the main content after the required state is ready.

Avoid showing the dashboard briefly and then replacing it with loading content.

Use:

* Skeleton loading
* Progress indicators
* Proper placeholder content
* ViewModel state
* Lifecycle-aware loading

The UI should transition:

Loading → Success

or

Loading → Error

without flashing incorrect/incomplete content.

---

# 11. SUCCESS / ERROR / EMPTY STATES

Every important operation should have a clear state.

Examples:

Save:

Success:
`Updated!`

No changes:

Show a modal:

`No changes were made.`

Actions:

`Remain`

`Back to Artifact List`

Errors:

Explain what actually failed.

Do not display contradictory messages.

---

# 12. ARTIFACT EDITING

When editing an artifact:

Track the original values.

Track the current values.

Determine whether changes actually exist.

If changes exist:

Save normally.

After successful save:

Show:

`Updated!`

If no changes exist:

Do not make an unnecessary API request.

Show a modal:

`No changes were made.`

Actions:

* Remain
* Back to Artifact List

---

# 13. ARTIFACT CATALOGUE

The artifact list should receive most of the visual emphasis.

Do not allow the header/filter area to consume approximately half of the screen.

Use:

* Compact heading
* Compact search
* Floating filter control
* Dropdown/modal filter panel

The catalogue should remain visible and usable while filters are opened.

---

# 14. SEARCH

Search should be compact.

Do not keep a large permanent search section.

Allow the user to reveal search through the appropriate compact UI control.

Maintain fast access.

---

# 15. CATEGORY FILTERING

Category selection must NOT require typing.

Show available categories as selectable options.

Example:

☐ Agricultural Tool

☐ Farm Equipment

☐ Weapon

☐ Cultural Artifact

Multiple categories must be supported.

Example selected value:

`Agricultural Tool, Farm Equipment`

Behavior:

* Tap unchecked category → add category
* Tap checked category → remove category

Display selected categories as removable chips where appropriate.

Filtering should support multiple selected categories.

Do not replace this with a free-text category field.

---

# 16. ARTIFACT INFORMATION LABELS

Do not automatically display an unnecessary "Label" section.

Artifact information should remain clean.

Provide an optional:

`Add Label`

action.

Only display/create a label when the user explicitly chooses to add one.

---

# 17. ARTIFACT ACTION MENU

Each artifact in the catalogue should have an overflow menu.

Expected actions:

* Edit
* Feed to AI Library
* Delete

`Feed to AI Library` should ONLY appear when the artifact is not already indexed.

If already indexed:

Show:

`Already in AI Library`

Do not allow duplicate feeding unless the existing system explicitly supports reprocessing.

---

# 18. AI LIBRARY FEEDING

Feeding an artifact to the AI Library must feel like a professional operation.

When processing starts, show a modal/dialog such as:

`Adding Artifact to AI Library`

`Please wait while AI processing is running.`

Show progress where available.

Example:

`3/14 completed`

Provide states:

* Processing
* Completed
* Failed

When processing finishes:

Show:

`Successful: X`

`Failed: X`

Do not immediately display "failed" while the backend is still initializing or processing.

The UI must distinguish:

* request failed
* processing failed
* model unavailable
* processing completed
* already indexed

---

# 19. AI STATUS

The UI must not incorrectly state:

`AI Model is not ready`

when OpenCLIP is already loaded.

AI status should be refreshed after:

* Initial screen load
* Backend health check
* Model initialization
* Returning to the AI screen
* Settings reload
* App resume where appropriate

Use a proper state flow such as:

Loading AI Model

→

AI Ready

→

AI Index Available

or

Needs Attention

Do not show stale state from the previous screen lifecycle.

The AI screen should be able to refresh its state rather than relying only on the first response received.

---

# 20. AI STATUS DIAGNOSTICS

When AI status is inconsistent:

Inspect the actual backend responses.

Relevant endpoints may include:

* `/api/v1/ai/health`
* `/api/v1/ai/index/status`

Do not infer AI failure purely from a stale UI state.

If backend logs show a Qdrant error:

Identify whether the problem is:

* connection
* collection name
* escaping
* URL/configuration
* Docker networking
* actual missing collection

Do not assume vector corruption.

Do not rebuild the vectors.

---

# 21. PAGE REFRESH / LIFECYCLE

Screens that depend on changing backend state must refresh appropriately.

For example:

When returning to AI Recognition or Settings:

Refresh the relevant AI/model/index status.

When returning to an artifact list:

Refresh the relevant artifact state when appropriate.

Use Android lifecycle-aware mechanisms.

Avoid excessive polling.

Avoid duplicate API calls.

Do not refresh every component indiscriminately.

---

# 22. ADMIN DASHBOARD

Admin dashboard priorities:

1. Artifact statistics
2. AI status
3. System health
4. Recent activity

Use compact, useful cards.

Avoid large decorative panels that do not provide information.

---

# 23. ADMIN ROLE SYSTEM

SUPER ADMIN:

* Manage administrators
* Manage users
* Manage artifacts
* AI settings
* Vector rebuild controls
* System settings
* Logs

ADMIN:

* Create artifacts
* Edit artifacts
* Upload images
* Manage metadata
* Feed AI Library

Restricted for ADMIN:

* User management
* System settings
* Vector rebuild

Do not weaken authorization rules for UI convenience.

---

# 24. API / BACKEND PRESERVATION

Do not change API contracts for UI styling.

Do not change:

* Endpoint semantics
* Request structures
* Response structures
* Authentication behavior
* Database schemas

unless the requested bug genuinely requires it.

If a backend change is required, make the smallest possible targeted fix.

---

# 25. NETWORKING

The Android application must not depend permanently on the developer's current laptop IP.

The eventual deployment model is:

Client laptop:
Docker

* MongoDB
* Qdrant
* FastAPI backend

Client phone:
Android APK

The APK must be designed so that changing the client laptop/network does not require rewriting application source code.

Do not hardcode the developer's current IP into production code.

Networking architecture changes must be analyzed separately from UI changes.

---

# 26. FILE MODIFICATION RULE

Prefer modifying existing files over creating unnecessary new files.

Before creating a reusable component, verify that an equivalent component does not already exist.

Do not create:

* duplicate themes
* duplicate dialogs
* duplicate buttons
* duplicate card components
* duplicate API clients
* duplicate state classes

Reuse existing project architecture.

---

# 27. FINAL VERIFICATION

After implementation, verify the affected functionality.

For UI changes check:

* Android build
* Compilation
* Navigation
* Loading state
* Error state
* Success state
* Empty state
* Screen lifecycle
* API behavior
* Physical-device behavior when possible

For AI-related changes additionally verify:

* `/api/v1/ai/health`
* `/api/v1/ai/index/status`
* Existing Qdrant collection
* Existing vector availability

Never run a vector rebuild as a verification step.

---

# 28. ENGINEERING PRINCIPLE

When choosing between:

A large rewrite

and

A small targeted correction:

Choose the small targeted correction.

When choosing between:

Changing data

and

Fixing configuration/state management:

Choose configuration/state management.

When choosing between:

Adding complexity

and

Reusing existing architecture:

Reuse existing architecture.

The goal is:

**A polished production-quality museum application built on the existing working system.**
