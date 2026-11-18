package app.lume.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.lume.di.AppContainer
import app.lume.ui.account.AccountViewModel
import app.lume.ui.editor.EditorScreen
import app.lume.ui.editor.EditorViewModel
import app.lume.ui.library.HomeScreen
import app.lume.ui.library.LibraryViewModel
import app.lume.ui.paywall.PaywallScreen
import app.lume.ui.paywall.PaywallViewModel

object Routes {
    const val LIBRARY: String = "library"
    const val EDITOR: String = "editor"
    const val PAYWALL: String = "paywall"
}

@Composable
fun AppNavHost(
    container: AppContainer,
    editorViewModel: EditorViewModel,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Routes.LIBRARY,
        modifier = modifier
    ) {
        composable(Routes.LIBRARY) {
            val libraryViewModel: LibraryViewModel = viewModel(
                factory = container.libraryViewModelFactory()
            )
            val accountViewModel: AccountViewModel = viewModel(
                factory = container.accountViewModelFactory()
            )
            HomeScreen(
                libraryViewModel = libraryViewModel,
                accountViewModel = accountViewModel,
                onOpenEditor = { uri ->
                    editorViewModel.openPhoto(uri)
                    navController.navigate(Routes.EDITOR)
                },
                onOpenPaywall = { navController.navigate(Routes.PAYWALL) }
            )
        }
        composable(Routes.EDITOR) {
            EditorScreen(
                viewModel = editorViewModel,
                onBack = { navController.popBackStack() },
                onOpenPaywall = { navController.navigate(Routes.PAYWALL) }
            )
        }
        composable(Routes.PAYWALL) {
            val paywallViewModel: PaywallViewModel = viewModel(
                factory = container.paywallViewModelFactory()
            )
            PaywallScreen(
                viewModel = paywallViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
