package com.tomhartill.kinzo.ui.screens.introduction

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomhartill.kinzo.domain.Format
import com.tomhartill.kinzo.domain.Introduction
import com.tomhartill.kinzo.domain.IntroductionStep
import com.tomhartill.kinzo.domain.Settings
import com.tomhartill.kinzo.domain.ThemeMode
import com.tomhartill.kinzo.domain.WeightUnit
import com.tomhartill.kinzo.ui.components.ChoiceRow
import com.tomhartill.kinzo.ui.components.PrimaryButton
import com.tomhartill.kinzo.ui.components.SecondaryButton
import com.tomhartill.kinzo.ui.components.ThemePicker
import com.tomhartill.kinzo.ui.navigation.SystemBackHandler
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import org.koin.compose.viewmodel.koinViewModel

/** Where to go once the introduction is done. */
enum class IntroductionExit { Today, ReadyMadeWorkouts, SignIn }

/**
 * The first-run introduction: one idea per page, Skip always there, and every choice already
 * set to a sensible default so skipping loses nothing. Choices apply as they're made, so the
 * theme page recolours the introduction itself.
 */
@Composable
fun IntroductionScreen(
    steps: List<IntroductionStep>,
    onDone: (IntroductionExit) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IntroductionViewModel = koinViewModel(),
) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = steps.getOrNull(index) ?: steps.last()
    val isLast = index >= steps.lastIndex
    val spacing = KinzoTheme.spacing
    val colors = KinzoTheme.colors

    SystemBackHandler(enabled = index > 0) { index-- }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = spacing.screen, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = spacing.touchTarget), verticalAlignment = Alignment.CenterVertically) {
            if (steps.size > 1) StepDots(count = steps.size, current = index, modifier = Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
            if (!isLast) {
                TextButton(onClick = { onDone(IntroductionExit.Today) }) {
                    Text("Skip", style = MaterialTheme.typography.labelLarge, color = colors.textMuted)
                }
            }
        }

        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "introduction",
        ) { shown ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                Spacer(Modifier.size(spacing.xl))
                val current = settings
                if (current != null) StepContent(shown, current, viewModel)
            }
        }

        if (step == IntroductionStep.Ready) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                PrimaryButton(text = "Start training", onClick = { onDone(IntroductionExit.Today) })
                SecondaryButton(text = "Browse ready-made workouts", onClick = { onDone(IntroductionExit.ReadyMadeWorkouts) })
                TextButton(onClick = { onDone(IntroductionExit.SignIn) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Sign in to train with friends", style = MaterialTheme.typography.labelLarge, color = colors.text)
                }
            }
        } else {
            PrimaryButton(
                text = if (isLast) "Done" else "Next",
                onClick = { if (isLast) onDone(IntroductionExit.Today) else index++ },
            )
        }
    }
}

@Composable
private fun StepContent(step: IntroductionStep, settings: Settings, viewModel: IntroductionViewModel) {
    when (step) {
        IntroductionStep.Welcome -> Heading(
            "Welcome",
            "Log your workouts, follow a plan and watch your progress. It's free, works offline and doesn't need an account.",
        )
        IntroductionStep.Units -> {
            Heading("Kilograms or pounds?", "Weights show in this everywhere. You can change it any time in Profile.")
            ChoiceRow(
                options = WeightUnit.entries,
                selected = settings.weightUnit,
                optionLabel = { if (it == WeightUnit.Kilograms) "Kilograms" else "Pounds" },
                onSelect = viewModel::setWeightUnit,
            )
        }
        IntroductionStep.Rest -> {
            Heading("Rest between sets", "A timer starts when you tick a set. You can change it for one exercise during a workout.")
            ChoiceRow(
                options = Introduction.REST_CHOICES,
                // A rest set in Profile that isn't offered here shows the nearest choice.
                selected = Introduction.REST_CHOICES.minBy { kotlin.math.abs(it - settings.restSeconds) },
                optionLabel = { if (it == 0) "Off" else Format.duration(it) },
                onSelect = viewModel::setRestSeconds,
            )
        }
        IntroductionStep.Theme -> {
            Heading("Pick a colour", "The whole app changes as you tap. You can change it any time in Profile.")
            ThemePicker(selected = settings.theme, onSelect = viewModel::setTheme)
            ChoiceRow(
                options = ThemeMode.entries,
                selected = settings.themeMode,
                optionLabel = { it.label },
                onSelect = viewModel::setThemeMode,
            )
        }
        IntroductionStep.Ready -> Heading(
            "You're all set",
            "The first time you open each screen, a short tip explains it. Tap the info button on a screen to see its tips again.",
        )
    }
}

@Composable
private fun Heading(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.sm)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, color = KinzoTheme.colors.text)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = KinzoTheme.colors.textMuted)
    }
}

@Composable
private fun StepDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val colors = KinzoTheme.colors
    Row(
        modifier.semantics { contentDescription = "Page ${current + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { dot ->
            Box(
                Modifier
                    .size(if (dot == current) 8.dp else 6.dp)
                    .background(if (dot == current) colors.accent else colors.surfaceHigh, CircleShape),
            )
        }
    }
}
