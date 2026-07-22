package com.illyism.transcribe.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueModelTest {
    @Test
    fun queueTransitionsSeparateExecutionFromPipelineStage() {
        assertTrue(JobStateMachine.canTransition(JobState.QUEUED, JobState.RUNNING))
        assertTrue(JobStateMachine.canTransition(JobState.RUNNING, JobState.WAITING_FOR_KEY))
        assertTrue(JobStateMachine.canTransition(JobState.WAITING_FOR_KEY, JobState.RUNNING))
        assertTrue(JobStateMachine.canTransition(JobState.RUNNING, JobState.COMPLETED))
        assertFalse(JobStateMachine.canTransition(JobState.COMPLETED, JobState.RUNNING))
        assertFalse(JobStateMachine.canTransition(JobState.QUEUED, JobState.COMPLETED))
    }

    @Test
    fun parsesSeekableSrtCues() {
        val cues = SrtBuilder.parse(
            """
            1
            00:00:01,250 --> 00:00:03,500
            First line

            2
            00:01:02,000 --> 00:01:04,125
            Second line
            """.trimIndent()
        )

        assertEquals(2, cues.size)
        assertEquals(1.25, cues[0].startSeconds, 0.001)
        assertEquals(64.125, cues[1].endSeconds, 0.001)
    }

    @Test
    fun estimatesWhisperCostFromDuration() {
        assertEquals(0.60, CostEstimator.estimate(6_000_000L, 0.006), 0.0001)
    }
}
