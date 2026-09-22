package com.example.museumapp.ui.visitor.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.museumapp.data.repository.AdminRepository
import com.example.museumapp.data.repository.VisitorRepository
import com.example.museumapp.data.session.AdminSession
import com.example.museumapp.data.session.VisitorSession
import com.example.museumapp.ui.visitor.artifacts.VisitorArtifactDetailsScreen
import com.example.museumapp.ui.visitor.artifacts.VisitorArtifactsScreen
import com.example.museumapp.ui.visitor.entry.VisitorEntryScreen
import com.example.museumapp.ui.visitor.guest.GuestInfoScreen
import com.example.museumapp.ui.visitor.home.VisitorHomeScreen
import com.example.museumapp.ui.visitor.model3d.ArtifactModel3DScreen
import com.example.museumapp.ui.visitor.onboarding.VisitorOnboardingScreen
import com.example.museumapp.ui.visitor.scan.VisitorCameraScreen
import com.example.museumapp.ui.visitor.settings.VisitorSettingsScreen
import com.example.museumapp.ui.visitor.student.StudentLoginScreen
import com.example.museumapp.ui.visitor.student.StudentRegistrationPendingScreen
import com.example.museumapp.ui.visitor.student.StudentRegistrationScreen
import kotlinx.coroutines.launch

@Composable
fun VisitorNavGraph(
    repository: VisitorRepository,
    adminRepository: AdminRepository,
    startupDestination: StartupDestination,
    onAdminLogin: () -> Unit
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val startRoute = when (startupDestination) {
        StartupDestination.VisitorHome -> VisitorRoutes.Home
        StartupDestination.VisitorEntry -> VisitorRoutes.Entry
        else -> VisitorRoutes.Onboarding
    }

    LaunchedEffect(startupDestination) {
        when (startupDestination) {
            StartupDestination.VisitorHome -> navController.navigate(VisitorRoutes.Home) {
                popUpTo(0)
                launchSingleTop = true
            }
            StartupDestination.VisitorEntry -> navController.navigate(VisitorRoutes.Entry) {
                popUpTo(0)
                launchSingleTop = true
            }
            StartupDestination.VisitorOnboarding -> navController.navigate(VisitorRoutes.Onboarding) {
                popUpTo(0)
                launchSingleTop = true
            }
            else -> Unit
        }
    }

    NavHost(navController = navController, startDestination = startRoute) {
        composable(VisitorRoutes.Onboarding) {
            VisitorOnboardingScreen(
                onComplete = {
                    scope.launch {
                        repository.setOnboardingCompleted(true)
                        navController.navigate(VisitorRoutes.Entry) {
                            popUpTo(VisitorRoutes.Onboarding) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
        composable(VisitorRoutes.Entry) {
            VisitorEntryScreen(
                adminRepository = adminRepository,
                onGuest = { navController.navigate(VisitorRoutes.GuestInfo) },
                onStudentLogin = { navController.navigate(VisitorRoutes.StudentLogin) },
                onStudentRegister = { navController.navigate(VisitorRoutes.StudentRegister) },
                onAdminLogin = onAdminLogin
            )
        }
        composable(VisitorRoutes.GuestInfo) {
            GuestInfoScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
                onComplete = { navController.navigateToVisitorHome() }
            )
        }
        composable(VisitorRoutes.StudentLogin) {
            StudentLoginScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
                onComplete = { navController.navigateToVisitorHome() }
            )
        }
        composable(VisitorRoutes.StudentRegister) {
            StudentRegistrationScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
                onComplete = {
                    navController.navigate(VisitorRoutes.StudentRegisterPending) {
                        popUpTo(VisitorRoutes.StudentRegister) { inclusive = true }
                    }
                }
            )
        }
        composable(VisitorRoutes.StudentRegisterPending) {
            StudentRegistrationPendingScreen(
                onBackToSignIn = {
                    navController.navigate(VisitorRoutes.StudentLogin) {
                        popUpTo(VisitorRoutes.Entry)
                    }
                }
            )
        }
        composable(VisitorRoutes.Home) {
            VisitorShell(
                currentRoute = currentRoute,
                repository = repository,
                onNavigate = { navController.navigateVisitorTopLevel(it) },
                onOpenCamera = { navController.navigate(VisitorRoutes.Camera) }
            ) { padding, openScanSheet ->
                VisitorHomeScreen(
                    repository = repository,
                    padding = padding,
                    onExploreArtifacts = { navController.navigateVisitorTopLevel(VisitorRoutes.Artifacts) },
                    onScanArtifact = openScanSheet,
                    onArtifactDetails = { navController.navigate(VisitorRoutes.artifactDetails(it)) }
                )
            }
        }
        composable(VisitorRoutes.Artifacts) {
            VisitorShell(
                currentRoute = currentRoute,
                repository = repository,
                onNavigate = { navController.navigateVisitorTopLevel(it) },
                onOpenCamera = { navController.navigate(VisitorRoutes.Camera) }
            ) { padding, _ ->
                VisitorArtifactsScreen(
                    repository = repository,
                    padding = padding,
                    onArtifactDetails = { navController.navigate(VisitorRoutes.artifactDetails(it)) }
                )
            }
        }
        composable(VisitorRoutes.Settings) {
            VisitorShell(
                currentRoute = currentRoute,
                repository = repository,
                onNavigate = { navController.navigateVisitorTopLevel(it) },
                onOpenCamera = { navController.navigate(VisitorRoutes.Camera) }
            ) { padding, _ ->
                VisitorSettingsScreen(
                    repository = repository,
                    adminRepository = adminRepository,
                    padding = padding,
                    onLoggedOut = {
                        navController.navigate(VisitorRoutes.Entry) {
                            popUpTo(0)
                            launchSingleTop = true
                        }
                    },
                    onAdminLogin = onAdminLogin,
                    onMuseumInfo = {
                        navController.navigateVisitorTopLevel(VisitorRoutes.Artifacts)
                    }
                )
            }
        }
        composable(VisitorRoutes.Camera) {
            VisitorCameraScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
                onViewArtifact = { navController.navigate(VisitorRoutes.artifactDetails(it, fromScan = true)) }
            )
        }
        composable(
            route = VisitorRoutes.ArtifactDetails,
            arguments = listOf(
                navArgument("artifactId") { type = NavType.StringType },
                navArgument("fromScan") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { entry ->
            VisitorArtifactDetailsScreen(
                repository = repository,
                artifactId = entry.arguments?.getString("artifactId"),
                openedFromScan = entry.arguments?.getBoolean("fromScan") ?: false,
                onBack = { navController.popBackStack() },
                onScanAgain = { navController.navigate(VisitorRoutes.Camera) },
                onViewModel3D = { id -> navController.navigate(VisitorRoutes.artifactModel3D(id)) }
            )
        }
        composable(
            route = VisitorRoutes.ArtifactModel3D,
            arguments = listOf(navArgument("artifactId") { type = NavType.StringType })
        ) { entry ->
            ArtifactModel3DScreen(
                repository = repository,
                artifactId = entry.arguments?.getString("artifactId"),
                onBack = { navController.popBackStack() }
            )
        }
    }
}

private fun NavHostController.navigateToVisitorHome() {
    navigate(VisitorRoutes.Home) {
        popUpTo(0)
        launchSingleTop = true
    }
}

private fun NavHostController.navigateVisitorTopLevel(route: String) {
    navigate(route) {
        popUpTo(VisitorRoutes.Home) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
