package dev.xhos.null_mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import dev.xhos.null_mobile.navigation.AppNavigation
import dev.xhos.null_mobile.ui.theme.NullmobileTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NullmobileTheme {
                Surface(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                    AppNavigation()
                }
            }
        }
    }
}
