package com.example.museumapp.ui.visitor.components

object VisitorAssets {
    private const val Base = "file:///android_asset/visitor_ui"

    const val OnboardingWelcome = "$Base/illustrations/onboarding_welcome.webp"
    const val OnboardingExplore = "$Base/illustrations/onboarding_explore.webp"
    const val OnboardingAiScan = "$Base/illustrations/onboarding_ai_scan.webp"
    const val AuthGuestStudent = "$Base/illustrations/auth_guest_student.webp"
    const val HomeMuseumHero = "$Base/illustrations/home_museum_hero.webp"
    const val ArtifactsFactsArticles = "$Base/illustrations/artifacts_facts_articles.webp"
    const val MuseumLocation = "$Base/illustrations/museum_location.webp"
    const val NewsAnnouncements = "$Base/illustrations/news_announcements.webp"
    const val AppLogo = "$Base/icons/psau_museum_app_logo.webp"
    const val AiScanIcon = "$Base/icons/ai_scan_icon.webp"

    val RequiredAssets = listOf(
        OnboardingWelcome,
        OnboardingExplore,
        OnboardingAiScan,
        AuthGuestStudent,
        HomeMuseumHero,
        ArtifactsFactsArticles,
        MuseumLocation,
        NewsAnnouncements,
        AppLogo,
        AiScanIcon
    )
}
