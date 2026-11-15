package app.lume

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import app.lume.ui.editor.EditorViewModel
import app.lume.ui.navigation.AppNavHost
import app.lume.ui.theme.LumeTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as LumeApp
        val container = app.container
        // EditorViewModel vive no escopo da Activity: a Biblioteca abre a
        // foto nele e o Editor consome — a URI nunca viaja pela rota
        // (URIs têm caracteres que quebram argumentos de navegação).
        val editorViewModel = ViewModelProvider(
            this,
            container.editorViewModelFactory()
        )[EditorViewModel::class.java]
        setContent {
            LumeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    AppNavHost(
                        container = container,
                        editorViewModel = editorViewModel
                    )
                }
            }
        }
    }
}
