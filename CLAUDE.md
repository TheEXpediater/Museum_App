# CLAUDE.md

# Museum_App Claude Code Instructions

## 1. CURRENT MIGRATION STATUS

IMPORTANT:

This project is currently undergoing migration from an old computer.

Completed:

- Source code migration completed.
- Artifact image backup completed.
- images.zip extracted to:

backend/uploads/images/

- MongoDB backup completed using mongodump.
- BSON backup files created for restore.
- MongoDB migration tools available:

  - mongodump.exe
  - mongorestore.exe
  - bsondump.exe


Not completed:

- Docker environment setup.
- MongoDB Docker container setup.
- MongoDB BSON restore.
- Qdrant vector migration.
- OpenCLIP production verification.


IMPORTANT:

Do NOT assume missing database data is an application bug.

Do NOT rebuild AI vectors.

Do NOT modify AI indexing logic.

Do NOT modify Qdrant workflow until migration verification is complete.


Before AI-related changes:

1. MongoDB restore must be completed.
2. Artifact records must be verified.
3. Qdrant storage must be checked.
4. OpenCLIP status must be verified.


Missing collections, empty vectors, or AI errors may be caused by incomplete migration.


---

# 2. DEVELOPMENT RULES

Before coding:

Always:

1. Identify exact files involved.
2. Explain current behavior.
3. Explain proposed changes.
4. Provide implementation plan.

Do not modify code immediately.

Wait for approval before large changes.


Rules:

- Make the smallest possible changes.
- Preserve existing functionality.
- Reuse existing architecture.

Do not:

- Rewrite working modules.
- Change API contracts unnecessarily.
- Refactor unrelated code.
- Create unnecessary abstractions.


---

# 3. TOKEN OPTIMIZATION RULES

DO NOT scan the entire repository unless explicitly requested.

Always start with targeted inspection.


For UI tasks inspect only:

- Pages
- Components
- Styling
- Frontend state management
- Related API calls


For backend tasks inspect only:

- Routes
- Services
- Repositories
- Models
- Related configuration


Never scan:

- node_modules/
- .venv/
- venv/
- uploads/images/
- image files
- build folders
- generated files
- logs
- migration backups
- MongoDB BSON files
- ZIP archives


---

# 4. PROJECT OVERVIEW

Museum_App is a museum artifact management and AI recognition platform.


## Visitor Application

Functions:

- Browse artifacts
- Search artifacts
- View artifact details
- AI recognition


## Admin Application

Functions:

- Manage artifacts
- Upload images
- Manage categories
- Manage AI library
- Manage users


## Backend

Technology:

- FastAPI
- MongoDB
- Qdrant
- OpenCLIP


## AI System

Technology:

- OpenCLIP embeddings
- Vector similarity search

Vector database:

Qdrant

Collection:

artifact_images


---

# 5. FRONTEND PRIORITY

Current priority:

Improve the existing frontend experience first.

Focus:

1. Visitor UX
2. Admin UX
3. Loading and state management
4. Artifact workflows


Do not rebuild the frontend framework.

Reuse:

- Existing components
- Existing API endpoints
- Existing data models


Goal:

Improve the application, not replace it.


---

# 6. UI DESIGN OBJECTIVE

Goal:

Create a professional museum digital platform.


Design:

- Modern
- Clean
- Minimal
- Professional
- Responsive
- Fast


Avoid:

- Large unnecessary headers
- Excessive filters
- Crowded cards
- Repeated information
- Unnecessary labels


Prefer:

- Compact layouts
- Floating actions
- Modal workflows
- Toast notifications
- Skeleton loading
- Clear states


---

# 7. VISITOR UI REQUIREMENTS


## Artifact Catalogue

Problem:

Header and filters consume too much space.


Improve:

- Smaller heading area
- Compact search
- Floating filter button
- Dropdown filter panel


Artifact cards should become the main focus.


---

## Category Filtering

Current:

Typing categories.


Change:

Selectable category list.


Example:

☐ Agricultural Tool

☐ Farm Equipment

☐ Weapon

☐ Cultural Artifact


Support multiple categories.


Example:

Selected:

Agricultural Tool

Farm Equipment


Behavior:

Click checkbox:
Add category


Click again:
Remove category


Display selected categories as removable chips.


---

## Artifact Information

Remove unnecessary labels.

Default:

Show information only.


Add optional:

"Add Label"


Only create labels when requested.


---

# 8. LOADING EXPERIENCE


Never display incomplete pages.


Show loading state before content.


Examples:

- Loading Dashboard...
- Loading Artifacts...
- Loading AI System...


Use:

- Skeleton loaders
- Progress indicators
- Proper API loading states


Apply to:

- Dashboard
- Artifact list
- AI Recognition
- Settings


---

# 9. SAVE EXPERIENCE


When updating artifacts:


If changes exist:

Show:

"Updated successfully"


Use toast notification.


If no changes:

Do not save.


Show modal:

"No changes were made."


Options:

- Stay
- Return to Artifact List


---

# 10. AI LIBRARY UX


Improve only the user experience.

Do not change AI processing logic.


When feeding artifacts:

Show:

"Adding Artifact to AI Library"

"Please wait while AI processing is running."


Display:

Example:

3/14 completed


States:

- Processing
- Completed
- Failed


After completion:

Show:

Successful: X

Failed: X


---

# 11. ARTIFACT ACTION MENU


Current:

- Edit
- Delete


Change:

- Edit
- Feed to AI Library
- Delete


Rules:

Show "Feed to AI Library" only when artifact is not indexed.


If indexed:

Show:

Already in AI Library


---

# 12. AI STATUS UI


Problem:

OpenCLIP may be loaded but UI shows:

"AI Model is not ready"


Improve:

Refresh AI status after:

- Page load
- Model initialization
- Settings reload
- Backend health check


States:

- Loading AI Model
- AI Ready
- AI Index Available
- Needs Rebuild


Do not show false errors.


---

# 13. QDRANT RULES


Do not modify Qdrant logic until migration is complete.


Known issue:

artifact_images collection may not exist.


Future improvement:

Before vector operations:

Check collection existence.


If missing:

Skip safely.

Log:

"Vector collection unavailable. Skipping cleanup."


---

# 14. ADMIN ROLE SYSTEM


Roles:


## SUPER ADMIN

Permissions:

- Manage administrators
- Manage users
- Manage artifacts
- AI settings
- Vector rebuild
- System settings
- Logs


## ADMIN

Allowed:

- Create artifacts
- Edit artifacts
- Upload images
- Manage metadata
- Feed AI library


Restricted:

- User management
- System settings
- Vector rebuild


---

# 15. ADMIN USER MANAGEMENT


Fields:

- Name
- Email
- Role
- Status
- Created Date


Actions:

- Edit
- Disable
- Delete


---

# 16. ADMIN DASHBOARD


Priorities:

1. Artifact statistics
2. AI status
3. Recent activity
4. System health


Avoid:

Large unused panels.


Prefer:

- Compact cards
- Clear indicators
- Useful charts only


---

# 17. IMPLEMENTATION WORKFLOW


For every request:


Phase 1:
Analyze only.

Return:

- Current files
- Current behavior
- Problems
- Recommended changes


Phase 2:
Planning.

Return:

- Files to modify
- Files not to modify
- Testing checklist


Phase 3:
Implement only after approval.


---

# FINAL STANDARD


The application should feel like:

A professional museum digital platform.


Priority:

1. Preserve existing functionality
2. User experience
3. Stability
4. Clean architecture
5. New features