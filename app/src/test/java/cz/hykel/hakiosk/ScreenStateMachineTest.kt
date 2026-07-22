package cz.hykel.hakiosk

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenStateMachineTest {
    @Test fun transitionsActiveDimmedOffAndBackToActive() {
        val machine = ScreenStateMachine(10_000L, 5_000L, startTimeMillis = 1_000L)
        assertEquals(ScreenStateMachine.Action.NONE, machine.tick(10_999L))
        assertEquals(ScreenStateMachine.Action.DIM, machine.tick(11_000L))
        assertEquals(ScreenStateMachine.State.DIMMED, machine.state)
        assertEquals(ScreenStateMachine.Action.NONE, machine.tick(15_999L))
        assertEquals(ScreenStateMachine.Action.TURN_OFF, machine.tick(16_000L))
        assertEquals(ScreenStateMachine.Action.ACTIVATE, machine.onMotion(17_000L))
        assertEquals(ScreenStateMachine.State.ACTIVE, machine.state)
    }

    @Test fun motionPostponesDimming() {
        val machine = ScreenStateMachine(10_000L, 5_000L, startTimeMillis = 0L)
        assertEquals(ScreenStateMachine.Action.NONE, machine.onMotion(8_000L))
        assertEquals(ScreenStateMachine.Action.NONE, machine.tick(17_999L))
        assertEquals(ScreenStateMachine.Action.DIM, machine.tick(18_000L))
    }
}
