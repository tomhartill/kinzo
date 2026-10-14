package com.tomhartill.kinzo.domain

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object Ids {
    /**
     * A new row ID. UUIDv7 starts with a timestamp, so rows created on the phone sort by
     * creation time and index well on the server once they sync.
     */
    @OptIn(ExperimentalUuidApi::class)
    fun newId(): String = Uuid.generateV7().toString()
}
