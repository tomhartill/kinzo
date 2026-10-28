package com.tomhartill.kinzo.ui.components

import androidx.compose.ui.graphics.vector.VectorPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class ExerciseIconSetTest {
    private val icons = ExerciseIconSet.parse(
        """{"version":1,"viewport":48,"icons":{
            "generic":[{"d":"M10 10L38 38","stroke":3}],
            "squat":[{"d":"M24 8A4 4 0 1 1 24 16A4 4 0 1 1 24 8Z","fill":true},{"d":"M24 16L24 30","stroke":2.5}]}}""",
    )

    @Test
    fun drawsFillsAndStrokesOnTheBundledViewport() {
        val squat = assertNotNull(icons.icon("squat"))
        assertEquals(48f, squat.viewportWidth)
        val paths = (0 until squat.root.size).map { squat.root[it] as VectorPath }
        assertNotNull(paths[0].fill)
        assertNull(paths[0].stroke)
        assertNull(paths[1].fill)
        assertEquals(2.5f, paths[1].strokeLineWidth)
        // Built once, then reused.
        assertSame(squat, icons.icon("squat"))
    }

    @Test
    fun unknownKeysGetTheGenericPictogram() {
        assertSame(icons.icon("generic"), icons.icon("no_such_movement"))
        assertNull(icons.icon(null))
    }
}
