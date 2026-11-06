package com.sub2api.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sub2api.mobile.ui.Sub2ApiApp
import com.sub2api.mobile.ui.theme.Sub2ApiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Sub2ApiTheme {
                val viewModel: MainViewModel = viewModel()
                Sub2ApiApp(viewModel)
            }
        }
    }
}
