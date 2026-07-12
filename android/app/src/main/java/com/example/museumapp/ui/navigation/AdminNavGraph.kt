package com.example.museumapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.museumapp.data.repository.AdminRepository
import com.example.museumapp.data.session.AdminSession
import com.example.museumapp.ui.admin.artifactform.ArtifactFormScreen
import com.example.museumapp.ui.admin.artifactlist.ArtifactListScreen
import com.example.museumapp.ui.admin.login.AdminLoginScreen

object AdminRoutes {
    const val Login = "admin_login"
    const val ArtifactList = "admin_artifact_list"
    const val ArtifactCreate = "admin_artifact_create"
    const val ArtifactEdit = "admin_artifact_edit/{artifactId}"

    fun editArtifact(artifactId: String): String = "admin_artifact_edit/$artifactId"
}

@Composable
fun AdminNavGraph(repository: AdminRepository) {
    val navController = rememberNavController()
    val session by repository.session.collectAsStateWithLifecycle(initialValue = AdminSession())

    LaunchedEffect(session.isAuthenticated) {
        if (session.isAuthenticated) {
            if (navController.currentDestination?.route == AdminRoutes.Login) {
                navController.navigate(AdminRoutes.ArtifactList) {
                    popUpTo(AdminRoutes.Login) { inclusive = true }
                    launchSingleTop = true
                }
            }
        } else {
            navController.navigate(AdminRoutes.Login) {
                popUpTo(0)
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (session.isAuthenticated) AdminRoutes.ArtifactList else AdminRoutes.Login
    ) {
        composable(AdminRoutes.Login) {
            AdminLoginScreen(
                repository = repository,
                onLoginSuccess = {
                    navController.navigate(AdminRoutes.ArtifactList) {
                        popUpTo(AdminRoutes.Login) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(AdminRoutes.ArtifactList) {
            ArtifactListScreen(
                repository = repository,
                onAddArtifact = { navController.navigate(AdminRoutes.ArtifactCreate) },
                onEditArtifact = { navController.navigate(AdminRoutes.editArtifact(it)) }
            )
        }
        composable(AdminRoutes.ArtifactCreate) {
            ArtifactFormScreen(
                repository = repository,
                artifactId = null,
                onClose = { navController.popBackStack() }
            )
        }
        composable(
            route = AdminRoutes.ArtifactEdit,
            arguments = listOf(navArgument("artifactId") { type = NavType.StringType })
        ) { backStackEntry ->
            ArtifactFormScreen(
                repository = repository,
                artifactId = backStackEntry.arguments?.getString("artifactId"),
                onClose = { navController.popBackStack() }
            )
        }
    }
}
