package com.tomhartill.kinzo.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DestinationTest {
    // The back stack is saved as routes, so every screen has to come back as itself.
    @Test
    fun everyDestinationSurvivesItsRoute() {
        val id = "0199e3a1-7c2b-7d3e-9f00-123456789abc"
        val destinations = listOf(
            Destination.ActiveWorkout,
            Destination.AddExercises(),
            Destination.AddExercises(id),
            Destination.EditWorkout(id),
            Destination.DesignSystem,
            Destination.ExerciseDetail(id),
            Destination.WorkoutDetail(id),
            Destination.WorkoutDetail(id, justFinished = true),
            Destination.RoutineDetail(id),
            Destination.RoutineEditor(),
            Destination.RoutineEditor(routineId = id),
            Destination.RoutineEditor(copyFromId = id),
            Destination.Mesocycle(id),
            Destination.SignIn,
            Destination.Username(welcome = true),
            Destination.Username(welcome = false),
            Destination.Account,
            Destination.Friends,
            Destination.Invite("aB3dE5fG7h"),
            Destination.CodeSignIn(),
            Destination.CodeSignIn(addPhone = true),
        )
        destinations.forEach { destination ->
            assertEquals(destination, Destination.fromRoute(destination.route), destination.route)
        }
        assertEquals(destinations.size, destinations.map { it.route }.toSet().size)
    }

    @Test
    fun unknownRoutesAreDropped() {
        assertNull(Destination.fromRoute("exercises"))
        assertNull(Destination.fromRoute("routine-editor/only-one-part"))
    }
}
