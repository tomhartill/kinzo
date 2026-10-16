package com.tomhartill.kinzo.data

import com.tomhartill.kinzo.domain.Routine
import com.tomhartill.kinzo.domain.RoutineSource
import com.tomhartill.kinzo.domain.WorkoutType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SplitRepositoryTest {
    private val db = inMemoryDatabase()
    private val clock = FakeClock()
    private val splits = SplitRepository(db, clock, Dispatchers.Unconfined)
    private val routines = RoutineRepository(db, clock, Dispatchers.Unconfined)

    private fun bundle(version: Int, splitNames: List<String>) = """{"version":$version,
        "splits":[${splitNames.joinToString(",") { """{"id":"kz-split-${it.lowercase()}","name":"$it","type":"resistance"}""" }}],
        "workouts":[{"id":"kz-push","name":"Push","type":"resistance","splitId":"kz-split-push","exercises":[
          {"id":"kz-push-0","exerciseId":"ex-bench","sets":3,"reps":8}]}]}"""

    @BeforeTest
    fun seed() = runTest {
        CatalogueImporter(db, clock, Dispatchers.Unconfined) { smallCatalogue() }.importIfNeeded()
        KinzoWorkoutsImporter(db, clock, Dispatchers.Unconfined) { bundle(1, listOf("Upper", "Lower", "Push")) }.importIfNeeded()
    }

    @Test
    fun builtInSplitsComeFirstInBundledOrder() = runTest {
        val own = assertNotNull(splits.add("  Chest   and back ", WorkoutType.Resistance))
        assertEquals("Chest and back", own.name)
        val list = splits.splits().first()
        assertEquals(listOf("Upper", "Lower", "Push", "Chest and back"), list.map { it.name })
        assertEquals(listOf(true, true, true, false), list.map { it.isBuiltIn })
        assertTrue(list.all { it.type == WorkoutType.Resistance })

        // Ready-made workouts are filed under theirs.
        val push = assertNotNull(routines.routine("kz-push").first())
        assertEquals(WorkoutType.Resistance, push.type)
        assertEquals("kz-split-push", push.splitId)
        assertEquals("Push", push.splitName)
        assertEquals("Push", routines.routines().first().single().splitName)
    }

    @Test
    fun addingANameThatExistsReusesIt() = runTest {
        assertEquals("kz-split-push", splits.add("push", WorkoutType.Resistance)?.id)
        assertNull(splits.add("   ", WorkoutType.Resistance))
        val laps = assertNotNull(splits.add("Push", WorkoutType.Swimming))
        assertEquals(WorkoutType.Swimming, laps.type)
        assertEquals(laps, splits.add("PUSH", WorkoutType.Swimming))
        assertEquals(4, splits.splits().first().size)
        assertEquals(SplitRepository.MAX_NAME_LENGTH, splits.add("x".repeat(100), WorkoutType.Resistance)?.name?.length)
    }

    @Test
    fun onlyTheUsersOwnSplitsCanBeRemovedAndWorkoutsKeepTheName() = runTest {
        val own = assertNotNull(splits.add("Glutes", WorkoutType.Resistance))
        routines.save(Routine("mine", "Glute day", null, RoutineSource.User, emptyList(), splitId = own.id))

        splits.delete("kz-split-push")
        splits.delete(own.id)
        assertEquals(listOf("Upper", "Lower", "Push"), splits.splits().first().map { it.name })
        assertEquals("Glutes", assertNotNull(routines.routine("mine").first()).splitName)
        assertEquals("Glutes", routines.routines().first().single { it.id == "mine" }.splitName)
    }

    @Test
    fun aNewBundleDropsSplitsItNoLongerHas() = runTest {
        KinzoWorkoutsImporter(db, clock, Dispatchers.Unconfined) { bundle(2, listOf("Push", "Legs")) }.importIfNeeded()
        assertEquals(listOf("Push", "Legs"), splits.splits().first().map { it.name })
    }
}
