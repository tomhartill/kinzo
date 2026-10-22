package com.tomhartill.kinzo

import kotlinx.coroutines.launch
import com.tomhartill.kinzo.ui.tips.TipOverlay
import com.tomhartill.kinzo.ui.screens.introduction.IntroductionScreen
import com.tomhartill.kinzo.ui.screens.introduction.IntroductionExit
import com.tomhartill.kinzo.domain.IntroductionStep
import com.tomhartill.kinzo.domain.Introduction
import com.tomhartill.kinzo.data.GuidanceRepository
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomhartill.kinzo.data.SettingsRepository
import com.tomhartill.kinzo.domain.ActiveWorkoutInfo
import com.tomhartill.kinzo.domain.RestTimer
import com.tomhartill.kinzo.resources.Res
import com.tomhartill.kinzo.ui.AppViewModel
import com.tomhartill.kinzo.ui.components.ActiveWorkoutBanner
import com.tomhartill.kinzo.ui.components.BottomBarItem
import com.tomhartill.kinzo.ui.components.ExerciseIconSet
import com.tomhartill.kinzo.ui.components.KinzoIcons
import com.tomhartill.kinzo.ui.components.KinzoBottomBar
import com.tomhartill.kinzo.ui.components.LocalExerciseIcons
import com.tomhartill.kinzo.ui.navigation.BackStackEntry
import com.tomhartill.kinzo.ui.navigation.Destination
import com.tomhartill.kinzo.ui.navigation.Navigator
import com.tomhartill.kinzo.ui.navigation.ProvideScreenStores
import com.tomhartill.kinzo.ui.navigation.ProvideViewModelStore
import com.tomhartill.kinzo.ui.navigation.ScreenStores
import com.tomhartill.kinzo.ui.navigation.SystemBackHandler
import com.tomhartill.kinzo.ui.navigation.Tab
import com.tomhartill.kinzo.ui.navigation.rememberNavigator
import com.tomhartill.kinzo.account.DeepLinks
import com.tomhartill.kinzo.ui.screens.DesignSystemScreen
import com.tomhartill.kinzo.ui.screens.account.AccountScreen
import com.tomhartill.kinzo.ui.screens.account.CodeSignInScreen
import com.tomhartill.kinzo.ui.screens.account.SignInScreen
import com.tomhartill.kinzo.ui.screens.account.UsernameScreen
import com.tomhartill.kinzo.ui.screens.friends.FriendsScreen
import com.tomhartill.kinzo.ui.screens.friends.InviteScreen
import com.tomhartill.kinzo.ui.screens.exercises.ExerciseDetailScreen
import com.tomhartill.kinzo.ui.screens.exercises.ExercisePickerScreen
import com.tomhartill.kinzo.ui.screens.history.HistoryScreen
import com.tomhartill.kinzo.ui.screens.history.WorkoutDetailScreen
import com.tomhartill.kinzo.ui.screens.library.LibraryScreen
import com.tomhartill.kinzo.ui.screens.library.LibrarySection
import com.tomhartill.kinzo.ui.screens.library.MesocycleScreen
import com.tomhartill.kinzo.ui.screens.library.RoutineDetailScreen
import com.tomhartill.kinzo.ui.screens.library.RoutineEditorScreen
import com.tomhartill.kinzo.ui.screens.library.RoutineEditorViewModel
import com.tomhartill.kinzo.ui.screens.profile.ProfileScreen
import com.tomhartill.kinzo.ui.screens.progress.ProgressPhotosScreen
import com.tomhartill.kinzo.ui.screens.today.TodayScreen
import com.tomhartill.kinzo.ui.screens.workout.ActiveWorkoutScreen
import com.tomhartill.kinzo.ui.theme.KinzoTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.koin.compose.koinInject
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
    val settingsRepository = koinInject<SettingsRepository>()
    val settings by remember(settingsRepository) { settingsRepository.settings() }.collectAsStateWithLifecycle(initialValue = null)
    // Read before anything else runs: the exercise library import is what marks an install as
    // one from before the introduction existed.
    val guidance = koinInject<GuidanceRepository>()
    val introductionSeen by produceState<Int?>(null, guidance) { value = guidance.introductionVersion() }
    // Nothing draws for the moment these take to read, rather than flashing the default theme
    // at someone who chose another.
    val current = settings ?: return
    val seenVersion = introductionSeen ?: return
    KinzoTheme(theme = current.theme, mode = current.themeMode) {
        val navigator = rememberNavigator()
        val scope = rememberCoroutineScope()
        var replayingIntroduction by rememberSaveable { mutableStateOf(false) }
        var introductionDone by rememberSaveable { mutableStateOf(false) }
        val introductionSteps = when {
            replayingIntroduction -> IntroductionStep.entries.toList()
            introductionDone -> emptyList()
            else -> Introduction.stepsAfter(seenVersion)
        }
        val showIntroduction = introductionSteps.isNotEmpty()
        CompositionLocalProvider(LocalExerciseIcons provides rememberExerciseIcons()) {
            ProvideScreenStores { stores ->
                Box(Modifier.fillMaxSize()) {
                    // The app is there underneath from the start, so the exercise library
                    // imports while the introduction shows. Screen readers skip it until then.
                    Box(Modifier.fillMaxSize().then(if (showIntroduction) Modifier.clearAndSetSemantics {} else Modifier)) {
                        AppContent(navigator, stores, onReplayIntroduction = { replayingIntroduction = true })
                    }
                    // Above every screen, so a spotlight can dim all of them.
                    TipOverlay()
                    if (showIntroduction) {
                        IntroductionScreen(
                            steps = introductionSteps,
                            onDone = { exit ->
                                replayingIntroduction = false
                                introductionDone = true
                                scope.launch { guidance.setIntroductionVersion(Introduction.VERSION) }
                                when (exit) {
                                    IntroductionExit.Today -> Unit
                                    IntroductionExit.ReadyMadeWorkouts -> navigator.selectTab(Tab.Library)
                                    IntroductionExit.SignIn -> navigator.push(Destination.SignIn)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Reads the exercise pictograms once. Without them, exercises just show no picture. */
@OptIn(ExperimentalResourceApi::class)
@Composable
private fun rememberExerciseIcons(): ExerciseIconSet? = produceState<ExerciseIconSet?>(null) {
    value = try {
        withContext(Dispatchers.Default) { ExerciseIconSet.parse(Res.readBytes(ExerciseIconSet.RESOURCE_PATH).decodeToString()) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        println("Kinzo: couldn't read the exercise pictograms: $e")
        null
    }
}.value

@Composable
private fun AppContent(
    navigator: Navigator,
    stores: ScreenStores,
    onReplayIntroduction: () -> Unit,
    viewModel: AppViewModel = koinViewModel(),
) {
    val activeWorkout by viewModel.activeWorkout.collectAsStateWithLifecycle()
    val rest by viewModel.rest.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val screenStates = rememberSaveableStateHolder()

    // A buzz when rest is over, whichever screen is showing.
    LaunchedEffect(viewModel) {
        viewModel.restFinished.collect { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    }

    // An invite link opened the app: show it over whatever is open.
    val pendingInvite by DeepLinks.pendingInvite.collectAsStateWithLifecycle()
    LaunchedEffect(pendingInvite) {
        val code = pendingInvite ?: return@LaunchedEffect
        navigator.push(Destination.Invite(code))
        DeepLinks.consumeInvite()
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
                Tabs(navigator, activeWorkout, rest, onReplayIntroduction)
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
private fun Tabs(
    navigator: Navigator,
    activeWorkout: ActiveWorkoutInfo?,
    rest: RestTimer.State?,
    onReplayIntroduction: () -> Unit,
) {
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
                Tab.History -> HistoryScreen(
                    onOpenWorkout = { open(Destination.WorkoutDetail(it)) },
                    onStarted = { open(Destination.ActiveWorkout) },
                    modifier = modifier,
                )
                Tab.Library -> LibraryScreen(
                    section = LibrarySection.entries[librarySection],
                    onSectionChange = { librarySection = it.ordinal },
                    onCreateWorkout = { open(Destination.RoutineEditor()) },
                    onOpenWorkout = { open(Destination.RoutineDetail(it)) },
                    onOpenMesocycle = { open(Destination.Mesocycle(it)) },
                    onOpenExercise = { open(Destination.ExerciseDetail(it)) },
                    modifier = modifier,
                )
                Tab.Profile -> ProfileScreen(
                    onSignIn = { open(Destination.SignIn) },
                    onOpenAccount = { open(Destination.Account) },
                    onOpenFriends = { open(Destination.Friends) },
                    onOpenProgressPhotos = { open(Destination.ProgressPhotos) },
                    onOpenDesignSystem = { open(Destination.DesignSystem) },
                    onReplayIntroduction = onReplayIntroduction,
                    modifier = modifier,
                )
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
    // A first sign-in asks for a username next; otherwise the screen that asked carries on.
    val signedInHere = { newUser: Boolean ->
        ifCurrent { if (newUser) navigator.push(Destination.Username(welcome = true)) }
    }
    val openCodeSignIn = { ifCurrent { navigator.push(Destination.CodeSignIn()) } }

    when (val destination = entry.destination) {
        Destination.ActiveWorkout -> ActiveWorkoutScreen(
            onMinimise = back,
            onAddExercises = { ifCurrent { navigator.push(Destination.AddExercises()) } },
            onOpenExercise = { id -> ifCurrent { navigator.push(Destination.ExerciseDetail(id)) } },
            onFinished = { id -> ifCurrent { navigator.replace(Destination.WorkoutDetail(id, justFinished = true)) } },
            onClosed = back,
        )
        is Destination.EditWorkout -> ActiveWorkoutScreen(
            onMinimise = back,
            onAddExercises = { ifCurrent { navigator.push(Destination.AddExercises(destination.workoutId)) } },
            onOpenExercise = { id -> ifCurrent { navigator.push(Destination.ExerciseDetail(id)) } },
            onFinished = { back() },
            onClosed = back,
            editWorkoutId = destination.workoutId,
        )
        is Destination.AddExercises -> ExercisePickerScreen(
            workoutId = destination.workoutId,
            onClose = back,
            onAdded = back,
            onSignIn = { ifCurrent { navigator.push(Destination.SignIn) } },
        )
        is Destination.ExerciseDetail -> ExerciseDetailScreen(exerciseId = destination.exerciseId, onBack = back)
        is Destination.WorkoutDetail -> WorkoutDetailScreen(
            workoutId = destination.workoutId,
            justFinished = destination.justFinished,
            onClose = back,
            // The resumed workout replaces everything, as starting one does.
            onResumed = { ifCurrent { navigator.resetTo(Destination.ActiveWorkout) } },
            onEdit = { ifCurrent { navigator.push(Destination.EditWorkout(destination.workoutId)) } },
            onStarted = { ifCurrent { navigator.resetTo(Destination.ActiveWorkout) } },
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
        is Destination.Mesocycle -> MesocycleScreen(
            mesocycleId = destination.mesocycleId,
            onBack = back,
            onOpenWorkout = { id -> ifCurrent { navigator.push(Destination.RoutineDetail(id)) } },
            onCreateWorkout = { ifCurrent { navigator.push(Destination.RoutineEditor()) } },
        )
        Destination.DesignSystem -> DesignSystemScreen(onBack = back)
        Destination.SignIn -> SignInScreen(
            onClose = back,
            onSignedIn = { newUser ->
                ifCurrent { if (newUser) navigator.replace(Destination.Username(welcome = true)) else navigator.pop() }
            },
            onEmail = openCodeSignIn,
        )
        is Destination.CodeSignIn -> CodeSignInScreen(
            addPhone = destination.addPhone,
            onClose = back,
            onSignedIn = { newUser ->
                ifCurrent {
                    // Back to whatever asked for sign-in, past the sign-in screen if that's below.
                    navigator.pop()
                    if (navigator.current?.destination == Destination.SignIn) navigator.pop()
                    if (newUser) navigator.push(Destination.Username(welcome = true))
                }
            },
            onPhoneAdded = back,
        )
        is Destination.Username -> UsernameScreen(welcome = destination.welcome, onDone = back)
        Destination.Account -> AccountScreen(
            onBack = back,
            onEditUsername = { ifCurrent { navigator.push(Destination.Username(welcome = false)) } },
            onOpenFriends = { ifCurrent { navigator.push(Destination.Friends) } },
            onAddPhone = { ifCurrent { navigator.push(Destination.CodeSignIn(addPhone = true)) } },
        )
        Destination.ProgressPhotos -> ProgressPhotosScreen(onBack = back)
        Destination.Friends -> FriendsScreen(onBack = back, onSignedIn = signedInHere, onEmail = openCodeSignIn)
        is Destination.Invite -> InviteScreen(
            code = destination.code,
            onClose = back,
            onSignedIn = signedInHere,
            onEmail = openCodeSignIn,
            onOpenFriends = { ifCurrent { navigator.replace(Destination.Friends) } },
        )
    }
}
