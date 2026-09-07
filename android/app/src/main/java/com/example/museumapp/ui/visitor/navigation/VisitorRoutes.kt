package com.example.museumapp.ui.visitor.navigation

object VisitorRoutes {
    const val Onboarding = "visitor_onboarding"
    const val Entry = "visitor_entry"
    const val GuestInfo = "visitor_guest_info"
    const val StudentLogin = "visitor_student_login"
    const val StudentRegister = "visitor_student_register"
    const val Home = "visitor_home"
    const val Artifacts = "visitor_artifacts"
    const val Scan = "visitor_scan"
    const val Settings = "visitor_settings"
    const val Camera = "visitor_camera"
    const val ArtifactDetails = "visitor_artifact_details/{artifactId}?fromScan={fromScan}"
    const val ArtifactModel3D = "visitor_artifact_model3d/{artifactId}"

    fun artifactDetails(artifactId: String, fromScan: Boolean = false): String =
        "visitor_artifact_details/$artifactId?fromScan=$fromScan"

    fun artifactModel3D(artifactId: String): String = "visitor_artifact_model3d/$artifactId"
}
