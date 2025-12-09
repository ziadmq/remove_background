package com.editpictures.ziadmq.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.editpictures.ziadmq.ui.viewmodel.BackgroundViewModel

@Composable
fun AppNavGraph(navController: NavHostController, viewModel: BackgroundViewModel) {
    NavHost(navController, startDestination = "home") {

        composable("home") {
            HomeScreen(viewModel) { url ->
                navController.navigate("preview/$url")
            }
        }

        composable("preview/{url}") { backStack ->
            val imgUrl = backStack.arguments?.getString("url") ?: ""
            PreviewScreen(imgUrl)
        }
    }
}
