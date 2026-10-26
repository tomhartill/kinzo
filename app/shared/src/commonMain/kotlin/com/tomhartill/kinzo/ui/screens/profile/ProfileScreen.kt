package com.tomhartill.kinzo.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomhartill.kinzo.domain.Format
import com.tomhartill.kinzo.domain.Settings
import com.tomhartill.kinzo.domain.ThemeMode
import com.tomhartill.kinzo.domain.WeightUnit
import com.tomhartill.kinzo.ui.components.Avatar
import com.tomhartill.kinzo.ui.components.ChoiceRow
import com.tomhartill.kinzo.ui.components.KinzoCard
import com.tomhartill.kinzo.ui.components.MenuSettingRow
import com.tomhartill.kinzo.ui.components.SecondaryButton
import com.tomhartill.kinzo.ui.components.SectionHeader
import com.tomhartill.kinzo.ui.components.ThemePicker
import com.tomhartill.kinzo.ui.components.kinzoSwitchColors
import com.tomhartill.kinzo.ui.screens.EmptyState
import com.tomhartill.kinzo.ui.screens.TitledScreen
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import org.koin.compose.viewmodel.koinViewModel

/** The Profile tab: the account, units and workout settings; body stats come with progression. */
@Composable
fun ProfileScreen(
    onSignIn: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenProgressPhotos: () -> Unit,
    onOpenDesignSystem: () -> Unit,
    onReplayIntroduction: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme

    TitledScreen(title = "Profile", modifier = modifier) {
        DemoProfileNotice()
        SectionHeader("Account")
        val signedIn = account
        if (signedIn == null) {
            KinzoCard(onClick = onSignIn) {
                Text("Sign in", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Follow friends, and back up your workouts soon. Optional: everything works without it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        } else {
            KinzoCard(onClick = onOpenAccount) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(signedIn.name)
                    Column {
                        Text(signedIn.name, style = MaterialTheme.typography.titleMedium)
                        Text("@${signedIn.username}", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                }
            }
            KinzoCard(onClick = onOpenFriends) {
                Text("Friends", style = MaterialTheme.typography.titleMedium)
                Text("Follow friends, see your followers and share your invite link", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }

        val current = settings
        if (current != null) {
            SectionHeader("Units")
            Column(verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.sm)) {
                ChoiceRow(
                    options = WeightUnit.entries,
                    selected = current.weightUnit,
                    optionLabel = { if (it == WeightUnit.Kilograms) "Kilograms" else "Pounds" },
                    onSelect = viewModel::setWeightUnit,
                )
                Text(
                    "Changes how weights show everywhere. What you've logged stays the same.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }

            SectionHeader("Appearance")
            ThemePicker(selected = current.theme, onSelect = viewModel::setTheme)
            ChoiceRow(
                options = ThemeMode.entries,
                selected = current.themeMode,
                optionLabel = { it.label },
                onSelect = viewModel::setThemeMode,
            )

            SectionHeader("Workouts")
            MenuSettingRow(
                title = "Default rest",
                description = "Starts when you tick a set. Change it for one exercise from the workout screen.",
                options = Settings.REST_OPTIONS,
                selected = current.restSeconds,
                optionLabel = { if (it == 0) "Off" else Format.duration(it) },
                onSelect = viewModel::setRestSeconds,
            )
        }

        SectionHeader("Body")
        KinzoCard(onClick = onOpenProgressPhotos) {
            Text("Progress photos", style = MaterialTheme.typography.titleMedium)
            Text(
                "A photo a week shows changes the scales miss. They stay private to this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
        EmptyState(title = "Body stats", body = "Height, weight and body fat tracking arrive with progression.")

        if (current != null) {
            SectionHeader("Help")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.md)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.xxs)) {
                    Text("Show tips", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "A short tip the first time you open each screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                Switch(checked = current.tipsEnabled, onCheckedChange = viewModel::setTipsEnabled, colors = kinzoSwitchColors())
            }
            SecondaryButton(text = "Show all tips again", onClick = viewModel::resetTips)
            SecondaryButton(text = "Replay introduction", onClick = onReplayIntroduction)
        }

        SectionHeader("About")
        Text(
            "Exercise library from free-exercise-db, released into the public domain, plus Kinzo's own machine variations.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )

        SectionHeader("Developer")
        DemoProfileSetting()
        SecondaryButton(text = "Design system", onClick = onOpenDesignSystem)
    }
}
