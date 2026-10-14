package com.tomhartill.kinzo.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

enum class Tab { Today, History, Library, Profile }

/** Screens pushed on top of the tabs. */
sealed interface Destination {
    val route: String

    data object ActiveWorkout : Destination {
        override val route = "workout"
    }

    data object AddExercises : Destination {
        override val route = "workout/add"
    }

    data class ExerciseDetail(val exerciseId: String) : Destination {
        override val route = "exercise/$exerciseId"
    }

    /** A finished workout. [justFinished] shows the end-of-workout summary header. */
    data class WorkoutDetail(val workoutId: String, val justFinished: Boolean = false) : Destination {
        override val route = "history/$workoutId/${if (justFinished) "finished" else "view"}"
    }

    /** A saved workout: its plan, and the button to start it. */
    data class RoutineDetail(val routineId: String) : Destination {
        override val route = "routine/$routineId"
    }

    /**
     * Creates or edits one of the user's own workouts. With no [routineId] it makes a new one,
     * starting from a copy of [copyFromId] if given (a ready-made workout, say).
     */
    data class RoutineEditor(val routineId: String? = null, val copyFromId: String? = null) : Destination {
        override val route = "routine-editor/${routineId ?: NONE}/${copyFromId ?: NONE}"
    }

    /** A mesocycle: its dates, how far through it is, and its workouts. */
    data class Mesocycle(val mesocycleId: String) : Destination {
        override val route = "mesocycle/$mesocycleId"
    }

    data object DesignSystem : Destination {
        override val route = "design-system"
    }

    data object SignIn : Destination {
        override val route = "sign-in"
    }

    /** Picking a username. [welcome] is straight after a first sign-in. */
    data class Username(val welcome: Boolean) : Destination {
        override val route = "username/${if (welcome) "welcome" else "edit"}"
    }

    /** Email and a texted or WhatsApped code; with [addPhone], adding a phone to the account. */
    data class CodeSignIn(val addPhone: Boolean = false) : Destination {
        override val route = "code-sign-in/${if (addPhone) "add-phone" else "sign-in"}"
    }

    data object Account : Destination {
        override val route = "account"
    }

    data object Friends : Destination {
        override val route = "friends"
    }

    /** A friend's invite link. */
    data class Invite(val code: String) : Destination {
        override val route = "invite/$code"
    }

    companion object {
        private const val NONE = "-"

        fun fromRoute(route: String): Destination? {
            val parts = route.split('/')
            return when {
                route == ActiveWorkout.route -> ActiveWorkout
                route == AddExercises.route -> AddExercises
                route == DesignSystem.route -> DesignSystem
                route == SignIn.route -> SignIn
                route == Account.route -> Account
                route == Friends.route -> Friends
                parts.size == 2 && parts[0] == "username" -> Username(welcome = parts[1] == "welcome")
                parts.size == 2 && parts[0] == "invite" -> Invite(parts[1])
                parts.size == 2 && parts[0] == "code-sign-in" -> CodeSignIn(addPhone = parts[1] == "add-phone")
                parts.size == 2 && parts[0] == "exercise" -> ExerciseDetail(parts[1])
                parts.size == 3 && parts[0] == "history" -> WorkoutDetail(parts[1], justFinished = parts[2] == "finished")
                parts.size == 2 && parts[0] == "routine" -> RoutineDetail(parts[1])
                parts.size == 2 && parts[0] == "mesocycle" -> Mesocycle(parts[1])
                parts.size == 3 && parts[0] == "routine-editor" ->
                    RoutineEditor(parts[1].takeUnless { it == NONE }, parts[2].takeUnless { it == NONE })
                else -> null
            }
        }
    }
}

/** One screen on the back stack. [id] tells two visits to the same destination apart. */
data class BackStackEntry(val id: Long, val destination: Destination)

/** A small back stack over the tabs. [ScreenStores] keeps each entry's view models. */
@Stable
class Navigator internal constructor(
    initialTab: Tab = Tab.Today,
    initialStack: List<BackStackEntry> = emptyList(),
    private var nextId: Long = (initialStack.maxOfOrNull { it.id } ?: -1L) + 1,
) {
    var tab by mutableStateOf(initialTab)
        private set

    private val _stack = mutableStateListOf<BackStackEntry>().apply { addAll(initialStack) }
    val stack: List<BackStackEntry> get() = _stack

    val current: BackStackEntry? get() = _stack.lastOrNull()

    fun selectTab(tab: Tab) {
        this.tab = tab
    }

    fun push(destination: Destination) {
        // Ignore a double tap that would open the same screen twice.
        if (_stack.lastOrNull()?.destination == destination) return
        _stack.add(BackStackEntry(nextId++, destination))
    }

    /** Swaps the top screen for another, for example the workout for its summary. */
    fun replace(destination: Destination) {
        if (_stack.isNotEmpty()) _stack.removeAt(_stack.lastIndex)
        _stack.add(BackStackEntry(nextId++, destination))
    }

    /** Goes back one screen. Returns false when already at the tabs. */
    fun pop(): Boolean {
        if (_stack.isEmpty()) return false
        _stack.removeAt(_stack.lastIndex)
        return true
    }

    /** Pops back to the tabs. */
    fun popToRoot() {
        _stack.clear()
    }

    /**
     * Replaces every screen with [destination], for example the workout just started from a
     * saved one: closing it then goes straight back to the tabs.
     */
    fun resetTo(destination: Destination) {
        _stack.clear()
        _stack.add(BackStackEntry(nextId++, destination))
    }

    /** False once [entry] has been popped, as opposed to hidden under another screen. */
    fun isOnStack(entry: BackStackEntry): Boolean = _stack.any { it.id == entry.id }

    companion object {
        // Saved as [tab, next ID, "ID route" per entry]. IDs are kept so each screen gets its
        // own saved state back, not another screen's.
        val Saver: Saver<Navigator, List<String>> = Saver(
            save = { navigator ->
                listOf(navigator.tab.name, navigator.nextId.toString()) +
                    navigator.stack.map { "${it.id} ${it.destination.route}" }
            },
            restore = { saved ->
                val stack = saved.drop(2).mapNotNull { line ->
                    val id = line.substringBefore(' ').toLongOrNull() ?: return@mapNotNull null
                    val destination = Destination.fromRoute(line.substringAfter(' ')) ?: return@mapNotNull null
                    BackStackEntry(id, destination)
                }
                Navigator(
                    initialTab = Tab.entries.firstOrNull { it.name == saved.firstOrNull() } ?: Tab.Today,
                    initialStack = stack,
                    nextId = maxOf(saved.getOrNull(1)?.toLongOrNull() ?: 0L, (stack.maxOfOrNull { it.id } ?: -1L) + 1),
                )
            },
        )
    }
}

/** The app's navigator. Its stack, entry IDs included, survives process death. */
@Composable
fun rememberNavigator(): Navigator = rememberSaveable(saver = Navigator.Saver) { Navigator() }

/** Gives [content] its own view model store, so `koinViewModel()` scopes to this screen. */
@Composable
internal fun ProvideViewModelStore(store: ViewModelStore, content: @Composable () -> Unit) {
    val owner = remember(store) {
        object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = store
        }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}
