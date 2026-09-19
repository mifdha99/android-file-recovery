package com.eslam.recovery.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun AppNav(vm: MainViewModel) {
    val hasAccess by vm.hasAccess.collectAsStateWithLifecycle()
    if (!hasAccess) {
        PermissionScreen(vm)
        return
    }
    val nav = rememberNavController()
    NavHost(nav, startDestination = "home") {
        composable("home") {
            HomeScreen(vm, onOpenResults = { nav.navigate("results") })
        }
        composable("results") {
            ResultsScreen(vm, onBack = { nav.popBackStack() }, onPreview = { index -> nav.navigate("preview/$index") })
        }
        composable("preview/{index}", arguments = listOf(navArgument("index") { type = NavType.IntType })) { entry ->
            PreviewScreen(vm, startIndex = entry.arguments?.getInt("index") ?: 0, onBack = { nav.popBackStack() })
        }
    }
    RecoveryDialog(vm)
}
