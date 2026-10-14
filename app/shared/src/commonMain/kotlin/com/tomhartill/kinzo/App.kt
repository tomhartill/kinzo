package com.tomhartill.kinzo

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomhartill.kinzo.domain.ActiveWorkoutInfo
import com.tomhartill.kinzo.domain.RestTimer
import com.tomhartill.kinzo.ui.AppViewModel
import com.tomhartill.kinzo.ui.components.ActiveWorkoutBanner
import com.tomhartill.kinzo.ui.components.BottomBarItem
import com.tomhartill.kinzo.ui.components.KinzoIcons
import com.tomhartill.kinzo.ui.components.KinzoBottomBar
import com.tomhartill.kinzo.ui.navigation.BackStackEntry
import com.tomhartill.kinzo.ui.navigation.Destination
import com.tomhartill.kinzo.ui.navigation.Navigator
import com.tomhartill.kinzo.ui.navigation.ProvideScreenStores
import com.tomhartill.kinzo.ui.navigation.ProvideViewModelStore
import com.tomhartill.kinzo.ui.navigation.ScreenStores
import com.tomhartill.kinzo.ui.navigation.SystemBackHandler
import com.tomhartill.kinzo.ui.navigation.Tab
import com.tomhartill.kinzo.ui.navigation.rememberNavigator
import com.tomhartill.kinzo.ui.screens.DesignSystemScreen
import com.tomhartill.kinzo.ui.screens.exercises.ExerciseDetailScreen
import com.tomhartill.kinzo.ui.screens.exercises.ExercisePickerScreen
import com.tomhartill.kinzo.ui.screens.history.HistoryScreen
import com.tomhartill.kinzo.ui.screens.history.WorkoutDetailScreen
import com.tomhartill.kinzo.ui.screens.library.LibraryScreen
import com.tomhartill.kinzo.ui.screens.library.LibrarySection
import com.tomhartill.kinzo.ui.screens.library.RoutineDetailScreen
import com.tomhartill.kinzo.ui.screens.library.RoutineEditorScreen
import com.tomhartill.kinzo.ui.screens.library.RoutineEditorViewModel
import com.tomhartill.kinzo.ui.screens.profile.ProfileScreen
import com.tomhartill.kinzo.ui.screens.today.TodayScreen
import com.tomhartill.kinzo.ui.screens.workout.ActiveWorkoutScreen
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import org.koin.compose.viewmodel.koinViewModel

// In the same order as Tab. The AI coach gets its own tab here in phase 2.
private val TabItems = listOf(
    BottomBarItem("Today", Icons.Outlined.Home, Icons.Filled.Home),
    BottomBarItem("History", Icons.Outlined.DateRange, Icons.Filled.DateRange),
    BottomBarItem("Library", KinzoIcons.Dumbbell, KinzoIcons.Dumbbell),
    BottomBarItem("Profile", Icons.Outlined.Person, Icons.Filled.Person),
)

private const val TRANSITION_MILLIS = 300

@Composable
fun App() {
    KinzoTheme {
        val navigator = rememberNavigator()
        ProvideScreenStores { stores ->
            AppContent(navigator, stores)
        }
    }
}

@Composable
private fun AppContent(navigator: Navigator, stores: ScreenStores, viewModel: AppViewModel = koinViewModel()) {
    val activeWorkout by viewModel.activeWorkout.collectAsStateWithLifecycle()
    val rest by viewModel.rest.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val screenStates = rememberSaveableStateHolder()

    // A buzz when rest is over, whichever screen is showing.
    LaunchedEffect(viewModel) {
        viewModel.restFinished.collect { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    }

    // Back closes the top screen, then returns to Today, then leaves the app.
    SystemBackHandler(enabled = navigator.stack.isNotEmpty() || navigator.tab != Tab.Today) {
        if (!navigator.pop()) navigator.selectTab(Tab.Today)
    }

    AnimatedContent(
        targetState = navigator.current,
        transitionSpec = { screenTransition() },
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        label = "screens",
    ) { entry ->
        if (entry == null) {
            screenStates.SaveableStateProvider("tabs") {
                Tabs(navigator, activeWorkout, rest)
            }
        } else {
            val key = "entry-${entry.id}"
            // Left and right safe areas (landscape navigation bar, notch); the tabs' Scaffold
            // handles its own.
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
                screenStates.SaveableStateProvider(key) {
                    ProvideViewModelStore(stores.storeFor(entry)) {
                        Screen(entry, navigator)
                    }
                }
            }
            // Once a popped screen has animated away, its view models and saved state go too.
            DisposableEffect(entry.id) {
                onDispose {
                    if (!navigator.isOnStack(entry)) {
                        stores.clear(entry)
                        screenStates.removeState(key)
                    }
                }
            }
        }
    }
}

/**
 * The workout slides up like a sheet and back down; every other screen pushes in from the
 * right. Newer screens always draw on top, so a closing screen slides away over the one
 * underneath.
 */
private fun AnimatedContentTransitionScope<BackStackEntry?>.screenTransition(): ContentTransform {
    val from = initialState
    val to = targetState
    val forward = from == null || (to != null && to.id > from.id)
    val spec = tween<Float>(TRANSITION_MILLIS)
    val offsetSpec = tween<IntOffset>(TRANSITION_MILLIS)
    val transform = when {
        forward && to?.destination == Destination.ActiveWorkout ->
            slideInVertically(offsetSpec) { it } togetherWith fadeOut(spec, targetAlpha = 0.6f)
        !forward && from.destination == Destination.ActiveWorkout ->
            fadeIn(spec, initialAlpha = 0.6f) togetherWith slideOutVertically(offsetSpec) { it }
        forward ->
            slideInHorizontally(offsetSpec) { it } togetherWith
                (slideOutHorizontally(offsetSpec) { -it / 4 } + fadeOut(spec, targetAlpha = 0.6f))
        else ->
            (slideInHorizontally(offsetSpec) { -it / 4 } + fadeIn(spec, initialAlpha = 0.6f)) togetherWith
                slideOutHorizontally(offsetSpec) { it }
    }
    transform.targetContentZIndex = to?.id?.toFloat() ?: -1f
    return transform
}

@Composable
private fun Tabs(navigator: Navigator, activeWorkout: ActiveWorkoutInfo?, rest: RestTimer.State?) {
    val tabStates = rememberSaveableStateHolder()
    // Which half of Library shows. Kept here so Today's "See all workouts" can pick Workouts;
    // an index rather than the enum, so it saves on every platform.
    var librarySection by rememberSaveable { mutableIntStateOf(LibrarySection.Workouts.ordinal) }
    // Taps on the tabs while they slide away under a new screen are ignored.
    fun open(destination: Destination) {
        if (navigator.current == null) navigator.push(destination)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                if (activeWorkout != null) {
                    ActiveWorkoutBanner(
                        name = activeWorkout.name,
                        startedAt = activeWorkout.startedAt,
                        rest = rest,
                        onClick = { open(Destination.ActiveWorkout) },
                    )
                }
                KinzoBottomBar(
                    items = TabItems,
                    selectedIndex = navigator.tab.ordinal,
                    onSelect = { navigator.selectTab(Tab.entries[it]) },
                )
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding).consumeWindowInsets(padding)
        tabStates.SaveableStateProvider(navigator.tab.name) {
            when (navigator.tab) {
                Tab.Today -> TodayScreen(
                    onOpenWorkout = { open(Destination.ActiveWorkout) },
                    onCreateWorkout = { open(Destination.RoutineEditor()) },
                    onOpenRoutine = { open(Destination.RoutineDetail(it)) },
                    onSeeAllRoutines = {
                        librarySection = LibrarySection.Workouts.ordinal
                        navigator.selectTab(Tab.Library)
                    },
                    onOpenFinishedWorkout = { open(Destination.WorkoutDetail(it)) },
                    modifier = modifier,
                )
                Tab.History -> HistoryScreen(onOpenWorkout = { open(Destination.WorkoutDetail(it)) }, modifier = modifier)
                Tab.Library -> LibraryScreen(
                    section = LibrarySection.entries[librarySection],
                    onSectionChange = { librarySection = it.ordinal },
                    onCreateWorkout = { open(Destination.RoutineEditor()) },
                    onOpenWorkout = { open(Destination.RoutineDetail(it)) },
                    onOpenExercise = { open(Destination.ExerciseDetail(it)) },
                    modifier = modifier,
                )
                Tab.Profile -> ProfileScreen(onOpenDesignSystem = { open(Destination.DesignSystem) }, modifier = modifier)
            }
        }
    }
}

/** A screen pushed over the tabs. */
@Composable
private fun Screen(entry: BackStackEntry, navigator: Navigator) {
    // A screen that is already on its way out (mid-animation) can't navigate any more.
    fun ifCurrent(action: () -> Unit) {
        if (navigator.current?.id == entry.id) action()
    }
    val back = { ifCurrent { navigator.pop() } }

    when (val destination = entry.destination) {
        Destination.ActiveWorkout -> ActiveWorkoutScreen(
            onMinimise = back,
            onAddExercises = { ifCurrent { navigator.push(Destination.AddExercises) } },
            onOpenExercise = { id -> ifCurrent { navigator.push(Destination.ExerciseDetail(id)) } },
            onFinished = { id -> ifCurrent { navigator.replace(Destination.WorkoutDetail(id, justFinished = true)) } },
            onClosed = back,
        )
        Destination.AddExercises -> ExercisePickerScreen(onClose = back, onAdded = back)
        is Destination.ExerciseDetail -> ExerciseDetailScreen(exerciseId = destination.exerciseId, onBack = back)
        is Destination.WorkoutDetail -> WorkoutDetailScreen(
            workoutId = destination.workoutId,
            justFinished = destination.justFinished,
            onClose = back,
        )
        is Destination.RoutineDetail -> RoutineDetailScreen(
            routineId = destination.routineId,
            onBack = back,
            // The workout replaces everything, so closing it goes back to the tabs.
            onStarted = { ifCurrent { navigator.resetTo(Destination.ActiveWorkout) } },
            onEdit = { ifCurrent { navigator.push(Destination.RoutineEditor(routineId = destination.routineId)) } },
            onCopy = { ifCurrent { navigator.push(Destination.RoutineEditor(copyFromId = destination.routineId)) } },
            onOpenExercise = { id -> ifCurrent { navigator.push(Destination.ExerciseDetail(id)) } },
        )
        is Destination.RoutineEditor -> RoutineEditorScreen(
            args = RoutineEditorViewModel.Args(routineId = destination.routineId, copyFromId = destination.copyFromId),
            onClose = back,
            onSaved = { id ->
                ifCurrent {
                    // A new workout opens to show it off; an edited one goes back to where it was.
                    if (destination.routineId == null) navigator.replace(Destination.RoutineDetail(id)) else navigator.pop()
                }
            },
            onOpenExercise = { id -> ifCurrent { navigator.push(Destination.ExerciseDetail(id)) } },
        )
        Destination.DesignSystem -> DesignSystemScreen(onBack = back)
    }
}
