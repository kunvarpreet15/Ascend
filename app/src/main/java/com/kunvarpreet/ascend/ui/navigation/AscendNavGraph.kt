package com.kunvarpreet.ascend.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kunvarpreet.ascend.domain.model.ExerciseType
import com.kunvarpreet.ascend.ui.components.AscendBottomNavBar
import com.kunvarpreet.ascend.ui.dashboard.DashboardScreen
import com.kunvarpreet.ascend.ui.dashboard.DashboardViewModel
import com.kunvarpreet.ascend.ui.evolution.EvolutionScreen
import com.kunvarpreet.ascend.ui.shop.ShopScreen
import com.kunvarpreet.ascend.ui.status.StatusScreen
import com.kunvarpreet.ascend.ui.workout.WorkoutScreen
import com.kunvarpreet.ascend.ui.workout.WorkoutViewModel

@Composable
fun AscendApp(
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            // Only show bottom nav on root top-level tabs
            if (currentRoute in listOf(Screen.Dashboard.route, Screen.Status.route, Screen.Shop.route, Screen.Evolution.route)) {
                AscendBottomNavBar(
                    currentRoute = currentRoute,
                    onNavigate = { screen ->
                        if (currentRoute != screen.route) {
                            navController.navigate(screen.route) {
                                popUpTo(Screen.Dashboard.route) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                val viewModel: DashboardViewModel = viewModel()
                DashboardScreen(
                    viewModel = viewModel,
                    onTargetClick = { target ->
                        if (target.exerciseType == ExerciseType.RUNNING) {
                            navController.navigate(Screen.Running.createRoute(target.id))
                        } else {
                            navController.navigate(Screen.Workout.createRoute(target.id))
                        }
                    }
                )
            }

            composable(Screen.Status.route) {
                StatusScreen()
            }

            composable(Screen.Shop.route) {
                ShopScreen()
            }

            composable(Screen.Evolution.route) {
                EvolutionScreen()
            }

            composable(Screen.Workout.route) { backStackEntry ->
                val targetId = backStackEntry.arguments?.getString("targetId") ?: ""
                val workoutViewModel: WorkoutViewModel = viewModel()
                WorkoutScreen(
                    targetId = targetId,
                    viewModel = workoutViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Running.route) { backStackEntry ->
                val targetId = backStackEntry.arguments?.getString("targetId") ?: ""
                DashboardScreen(
                    viewModel = viewModel(),
                    onTargetClick = {}
                )
            }
        }
    }
}
