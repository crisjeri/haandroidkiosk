package cz.hykel.hakiosk

class ScreenStateMachine(
    private val dimTimeoutMillis: Long,
    private val offDelayMillis: Long,
    startTimeMillis: Long,
    private val minimumActiveMillis: Long = 3_000L,
) {
    enum class State { ACTIVE, DIMMED, SCREEN_OFF }
    enum class Action { NONE, ACTIVATE, DIM, TURN_OFF }

    var state: State = State.ACTIVE
        private set
    private var lastMotionMillis = startTimeMillis
    private var activatedAtMillis = startTimeMillis
    private var dimmedAtMillis = 0L

    fun onMotion(nowMillis: Long): Action {
        lastMotionMillis = nowMillis
        return if (state != State.ACTIVE) {
            state = State.ACTIVE
            activatedAtMillis = nowMillis
            Action.ACTIVATE
        } else {
            Action.NONE
        }
    }

    fun tick(nowMillis: Long): Action {
        return when (state) {
            State.ACTIVE -> {
                val activeLongEnough = nowMillis - activatedAtMillis >= minimumActiveMillis
                if (activeLongEnough && nowMillis - lastMotionMillis >= dimTimeoutMillis) {
                    state = State.DIMMED
                    dimmedAtMillis = nowMillis
                    Action.DIM
                } else Action.NONE
            }
            State.DIMMED -> {
                if (nowMillis - dimmedAtMillis >= offDelayMillis) {
                    state = State.SCREEN_OFF
                    Action.TURN_OFF
                } else Action.NONE
            }
            State.SCREEN_OFF -> Action.NONE
        }
    }
}
