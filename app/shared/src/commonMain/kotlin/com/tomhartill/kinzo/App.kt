package com.tomhartill.kinzo

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.padding
import com.tomhartill.kinzo.ui.screens.DesignSystemScreen
import com.tomhartill.kinzo.ui.screens.ExercisesScreen
import com.tomhartill.kinzo.ui.screens.HistoryScreen
import com.tomhartill.kinzo.ui.screens.ProfileScreen
import com.tomhartill.kinzo.ui.screens.TodayScreen
import com.tomhartill.kinzo.ui.theme.KinzoTheme

// The AI coach gets its own tab here in phase 2.
private enum class Tab(val label: String, val icon: ImageVector) {
    Today("Today", Icons.Filled.Home),
    History("History", Icons.Filled.DateRange),
    Exercises("Exercises", Icons.Filled.Search),
    Profile("Profile", Icons.Filled.Person),
}

@Composable
fun App() {
    KinzoTheme {
        var tab by rememberSaveable { mutableStateOf(Tab.Today) }
        var showDesignSystem by rememberSaveable { mutableStateOf(false) }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    Tab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = {
                                tab = item
                                showDesignSystem = false
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            val modifier = Modifier.padding(padding)
            when {
                showDesignSystem -> DesignSystemScreen(onBack = { showDesignSystem = false }, modifier = modifier)
                tab == Tab.Today -> TodayScreen(modifier)
                tab == Tab.History -> HistoryScreen(modifier)
                tab == Tab.Exercises -> ExercisesScreen(modifier)
                tab == Tab.Profile -> ProfileScreen(onOpenDesignSystem = { showDesignSystem = true }, modifier = modifier)
            }
        }
    }
}
