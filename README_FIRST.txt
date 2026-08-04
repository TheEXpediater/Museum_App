PSAU MUSEUM VISITOR AND STUDENT CODEX PACK

CONTENTS

1. CODEX_PSAU_GUEST_STUDENT_VISITOR_APP_PROMPT.txt
2. ANDROID_IMAGE_COPY_INSTRUCTIONS.txt
3. ASSET_MANIFEST.json
4. visitor_images_source/illustrations/
5. visitor_images_source/icons/

HOW TO USE

1. Extract this pack.
2. Copy every file and folder from the extracted pack into the Museum_App repository root.
3. Confirm these paths exist:

Museum_App/CODEX_PSAU_GUEST_STUDENT_VISITOR_APP_PROMPT.txt
Museum_App/ANDROID_IMAGE_COPY_INSTRUCTIONS.txt
Museum_App/ASSET_MANIFEST.json
Museum_App/visitor_images_source/

4. Create a Git branch:

git checkout -b feature/visitor-guest-student-app

5. Open the repository root in Codex.

6. Send Codex:

Read CODEX_PSAU_GUEST_STUDENT_VISITOR_APP_PROMPT.txt, ANDROID_IMAGE_COPY_INSTRUCTIONS.txt, and ASSET_MANIFEST.json. Inspect the repository first. Preserve the completed administrator application. Copy the supplied images into android/app/src/main/assets/visitor_ui, implement the complete guest and student visitor experience, run every required test, fix failures, and report exact results.

7. After Codex finishes, verify this Android folder exists:

android/app/src/main/assets/visitor_ui/

8. Search the codebase for:

visitor_images_source

The only remaining references should be documentation. Kotlin and runtime files must not use the removable source folder.

9. Build and test.

10. After the APK builds and all images load, you may delete:

visitor_images_source/

Do not delete:

android/app/src/main/assets/visitor_ui/
