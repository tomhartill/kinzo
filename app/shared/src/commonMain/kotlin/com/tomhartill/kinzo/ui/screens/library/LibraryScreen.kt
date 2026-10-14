package com.tomhartill.kinzo.ui.screens.library

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomhartill.kinzo.domain.Dates
import com.tomhartill.kinzo.domain.RoutineSummary
import com.tomhartill.kinzo.domain.WorkoutType
import com.tomhartill.kinzo.ui.components.ChoiceRow
import com.tomhartill.kinzo.ui.components.KinzoCard
import com.tomhartill.kinzo.ui.components.KinzoIcons
import com.tomhartill.kinzo.ui.components.PrimaryButton
import com.tomhartill.kinzo.ui.components.SectionHeader
import com.tomhartill.kinzo.ui.components.rememberNow
import com.tomhartill.kinzo.ui.screens.EmptyState
import com.tomhartill.kinzo.ui.screens.ScreenTitle
import com.tomhartill.kinzo.ui.screens.exercises.ExerciseBrowser
import com.tomhartill.kinzo.ui.screens.exercises.ExerciseBrowserViewModel
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import kotlinx.datetime.TimeZone
import org.koin.compose.viewmodel.koinViewModel

/** The parts of the Library tab. */
enum class LibrarySection(val label: String) {
    Workouts("Workouts"),
    Mesocycles("Mesocycles"),
    Exercises("Exercises"),
}

/**
 * The Library tab: saved workouts to start from (your own in the current mesocycle, ready-made
 * ones, and later the AI coach's), the mesocycles, and the searchable exercise library. The app
 * holds [section], so other tabs can open a particular one.
 */
@Composable
fun LibraryScreen(
    section: LibrarySection,
    onSectionChange: (LibrarySection) -> Unit,
    onCreateWorkout: () -> Unit,
    onOpenWorkout: (routineId: String) -> Unit,
    onOpenMesocycle: (mesocycleId: String) -> Unit,
    onOpenExercise: (exerciseId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = koinViewModel(),
    exercises: ExerciseBrowserViewModel = koinViewModel(),
) {
    val spacing = KinzoTheme.spacing
    var nextMesocycle by remember { mutableStateOf<MesocycleDraft?>(null) }

    // The exercise list stops at the keyboard so its last results stay reachable.
    Column(modifier.fillMaxSize().imePadding()) {
        Column(
            modifier = Modifier.padding(start = spacing.screen, end = spacing.screen, top = spacing.xl, bottom = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            ScreenTitle("Library")
            ChoiceRow(
                options = LibrarySection.entries,
                selected = section,
                optionLabel = { it.label },
                onSelect = onSectionChange,
            )
        }
        when (section) {
            LibrarySection.Workouts -> {
                val state by viewModel.state.collectAsStateWithLifecycle()
                WorkoutList(
                    state = state,
                    onCreateWorkout = onCreateWorkout,
                    onOpenWorkout = onOpenWorkout,
                    onOpenMesocycles = { onSectionChange(LibrarySection.Mesocycles) },
                    modifier = Modifier.weight(1f),
                )
            }
            LibrarySection.Mesocycles -> {
                val state by viewModel.state.collectAsStateWithLifecycle()
                MesocycleList(
                    state = state,
                    onStartNext = { nextMesocycle = viewModel.nextDraft() },
                    onOpenMesocycle = onOpenMesocycle,
                    modifier = Modifier.weight(1f),
                )
                val draft = nextMesocycle
                val today = state.today
                if (draft != null && today != null) {
                    MesocycleFormDialog(
                        title = "Start next mesocycle",
                        confirmLabel = "Start",
                        initial = draft,
                        today = today,
                        defaultEnd = viewModel::defaultEnd,
                        startAfter = state.latest?.mesocycle,
                        copyCount = state.latest?.workouts?.size ?: 0,
                        startingNext = true,
                        onConfirm = { next, copy ->
                            nextMesocycle = null
                            viewModel.startNext(next, copy, onStarted = { onOpenMesocycle(it.id) })
                        },
                        onDismiss = { nextMesocycle = null },
                    )
                }
            }
            LibrarySection.Exercises -> {
                val state by exercises.state.collectAsStateWithLifecycle()
                ExerciseBrowser(
                    query = exercises.query,
                    state = state,
                    onQueryChange = exercises::search,
                    onMuscleChange = exercises::setMuscle,
                    onEquipmentChange = exercises::setEquipment,
                    onExerciseClick = { onOpenExercise(it.id) },
                    modifier = Modifier.weight(1f).padding(top = spacing.xs),
                    contentPadding = PaddingValues(bottom = spacing.lg),
                )
            }
        }
    }
}

@Composable
private fun WorkoutList(
    state: LibraryState,
    onCreateWorkout: () -> Unit,
    onOpenWorkout: (routineId: String) -> Unit,
    onOpenMesocycles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = KinzoTheme.spacing
    val zone = remember { TimeZone.currentSystemDefault() }
    val now by rememberNow(intervalMillis = 60_000)
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = spacing.screen, end = spacing.screen, top = spacing.sm, bottom = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        item(key = "create") { PrimaryButton(text = "Create workout", onClick = onCreateWorkout) }
        item(key = "ai") { GenerateWithAiCard() }

        header("My workouts")
        val current = state.current
        val today = state.today
        if (current != null && today != null) {
            item(key = "mesocycle") {
                Text(
                    "${current.name} · ${mesocycleStatus(current, today)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClickLabel = "See mesocycles", onClick = onOpenMesocycles),
                )
            }
        }
        if (state.mine.isEmpty()) {
            if (!state.loading) {
                item(key = "mine-empty") {
                    // A fresh mesocycle is empty, but the user's earlier workouts are still there, and
                    // ones made for a mesocycle planned for later show once it starts.
                    val hasEarlier = state.earlier.any { it.workouts.isNotEmpty() }
                    val planned = state.upcoming.lastOrNull { it.workouts.isNotEmpty() }?.mesocycle
                    if (current != null && hasEarlier) {
                        EmptyState(
                            title = "No workouts in ${current.name} yet",
                            body = "Create the workouts for this block of training. Your earlier ones are under Mesocycles, " +
                                "where you can open one and duplicate it to bring it into this block.",
                        )
                    } else if (current != null && today != null && planned != null) {
                        EmptyState(
                            title = "No workouts in ${current.name}",
                            body = "Your workouts for ${planned.name} show here from ${Dates.dateLabel(planned.start, today)}. " +
                                "Until then, create one for this block or use a ready-made workout below.",
                        )
                    } else {
                        EmptyState(
                            title = "No workouts of your own yet",
                            body = "Create one from scratch, or open a ready-made workout below and copy it to make it yours.",
                        )
                    }
                }
            }
        } else {
            routineCards(state.mine, now, zone, onOpenWorkout)
        }

        if (state.generated.isNotEmpty()) {
            header("From the AI coach")
            routineCards(state.generated, now, zone, onOpenWorkout)
        }

        if (state.readyMade.isNotEmpty()) {
            header("Ready-made")
            routineCards(state.readyMade, now, zone, onOpenWorkout)
        }
    }
}

private fun LazyListScope.header(title: String) {
    item(key = "header-$title") {
        SectionHeader(title, modifier = Modifier.padding(top = KinzoTheme.spacing.md))
    }
}

private fun LazyListScope.routineCards(
    routines: List<RoutineSummary>,
    now: Long,
    zone: TimeZone,
    onOpen: (routineId: String) -> Unit,
) {
    items(routines, key = { it.id }) { routine ->
        RoutineCard(routine = routine, now = now, zone = zone, onClick = { onOpen(routine.id) })
    }
}

/** A saved workout in a list: its name, when it was last done, and its exercises. */
@Composable
internal fun RoutineCard(
    routine: RoutineSummary,
    now: Long,
    zone: TimeZone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val count = routine.exerciseNames.size
    KinzoCard(modifier = modifier, onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.xxs)) {
            Row(horizontalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.sm)) {
                Text(
                    routine.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).alignByBaseline(),
                )
                val lastUsed = routine.lastUsedAt
                if (lastUsed != null) {
                    Text(
                        Dates.dayLabel(lastUsed, now, zone),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
            Text(
                listOfNotNull(
                    // Push, say, or Swimming when it's not weight training.
                    if (routine.type == WorkoutType.Resistance) routine.splitName else routine.type.label,
                    if (count == 1) "1 exercise" else "$count exercises",
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        if (count > 0) {
            Text(
                routine.exerciseNames.joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Where AI-made workouts will start. Not tappable until the coach exists. */
@Composable
private fun GenerateWithAiCard() {
    val colors = MaterialTheme.colorScheme
    KinzoCard(modifier = Modifier.semantics { stateDescription = "Coming soon" }) {
        Row(horizontalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.md), verticalAlignment = Alignment.Top) {
            Icon(KinzoIcons.Sparkle, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.xxs)) {
                Row(horizontalArrangement = Arrangement.spacedBy(KinzoTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Text("Generate with AI", style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
                    Text(
                        "SOON",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier
                            .border(1.dp, colors.outlineVariant, MaterialTheme.shapes.extraSmall)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Text(
                    "Say what you want, like \"45 minutes, dumbbells only\", and Kinzo will build the workout for you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}
