package com.kunvarpreet.ascend.ui.navigation

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object Workout : Screen("workout/{targetId}") {
        fun createRoute(targetId: String) = "workout/$targetId"
    }
    object Running : Screen("running/{targetId}") {
        fun createRoute(targetId: String) = "running/$targetId"
    }
    object Status : Screen("status")
    object Shop : Screen("shop")
    object Evolution : Screen("evolution")
    object Settings : Screen("settings")
}
