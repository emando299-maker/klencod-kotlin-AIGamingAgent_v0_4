package com.aigamingagent.action

import com.aigamingagent.ludo.DecisionEngine
import com.aigamingagent.ludo.LudoRules
import com.aigamingagent.ludo.LudoState
import com.aigamingagent.ludo.PlayerColor
import com.aigamingagent.ludo.ScoredMove
import com.aigamingagent.vision.VisionResult

interface Tapper {
    /** Returns true only if the gesture was actually dispatched. */
    fun tap(x: Float, y: Float): Boolean
}

enum class ActionKind {
    NO_BOARD, UNCERTAIN, NEED_ROLL, NO_LEGAL_MOVE, COOLDOWN, DRY_RUN, EXECUTING, VERIFIED, FAILED, HALTED
}

data class ActionStatus(val kind: ActionKind, val text: String, val decision: ScoredMove? = null)

data class ActionConfig(
    val minConfidence: Double = 0.8,
    val verifyTimeoutMs: Long = 4000,
    val maxConsecutiveFailures: Int = 2,
    val minIntervalMs: Long = 1500
)

/**
 * Safety-gated act/verify loop. Rules enforced here:
 *  - never acts unless the board state is fully understood and confidence >= minConfidence;
 *  - never acts without a known dice value;
 *  - never acts again until the previous move was verified (my pieces moved as predicted) or timed out;
 *  - halts for good after [ActionConfig.maxConsecutiveFailures] unverified moves.
 */
class ActionController(
    private val tapper: Tapper,
    private val rules: LudoRules = LudoRules(),
    private val engine: DecisionEngine = DecisionEngine(rules),
    private val config: ActionConfig = ActionConfig()
) {
    private class Awaiting(val me: PlayerColor, val expectedMine: List<Int>, val deadline: Long)

    var moves = 0
        private set
    var halted = false
        private set
    private var failures = 0
    private var awaiting: Awaiting? = null
    private var lastActionAt: Long? = null

    fun halt() { halted = true; awaiting = null }

    fun onFrame(
        result: VisionResult,
        roll: Int?,
        me: PlayerColor,
        frameToScreen: Double,
        live: Boolean,
        now: Long
    ): ActionStatus {
        if (halted) return ActionStatus(ActionKind.HALTED, "متوقف نهائيًا بعد فشل التحقق — أعد التشغيل")

        awaiting?.let { a ->
            val mine = result.state?.takeIf { me in it.pieces }?.positions(me)?.sorted()
            if (mine == a.expectedMine) {
                awaiting = null; failures = 0; moves++
                return ActionStatus(ActionKind.VERIFIED, "تم التحقق من الحركة")
            }
            if (now < a.deadline) return ActionStatus(ActionKind.EXECUTING, "بانتظار التحقق من الحركة")
            awaiting = null
            return fail("لم تتغير اللوحة كما هو متوقع")
        }

        val board = result.board ?: return ActionStatus(ActionKind.NO_BOARD, "لوحة لودو غير ظاهرة")
        val state = result.state
        if (state == null || result.confidence < config.minConfidence || me !in state.pieces) {
            return ActionStatus(ActionKind.UNCERTAIN, "حالة غير مؤكدة — لا قرار")
        }
        if (roll == null) return ActionStatus(ActionKind.NEED_ROLL, "اللوحة مفهومة — النرد غير معروف")

        val decision = engine.choose(state, me, roll)
            ?: return ActionStatus(ActionKind.NO_LEGAL_MOVE, "لا حركة قانونية برقم $roll")
        if (!live) return ActionStatus(ActionKind.DRY_RUN, "تجربة (بدون تنفيذ)", decision)

        lastActionAt?.let {
            if (now - it < config.minIntervalMs) return ActionStatus(ActionKind.COOLDOWN, "انتظار بين الحركات", decision)
        }
        val points = ActionPlanner.plan(result, decision.move, frameToScreen)
        if (points.isEmpty() || board.cell <= 0) return fail("تعذّر تحديد مكان الضغط")
        lastActionAt = now
        for ((x, y) in points) {
            if (!tapper.tap(x, y)) return fail("فشل إرسال اللمسة (خدمة إمكانية الوصول غير مفعّلة؟)")
        }
        val expected = rules.apply(state, decision.move).positions(me).sorted()
        awaiting = Awaiting(me, expected, now + config.verifyTimeoutMs)
        return ActionStatus(ActionKind.EXECUTING, "تم الضغط — بانتظار التحقق", decision)
    }

    private fun fail(reason: String): ActionStatus {
        failures++
        if (failures >= config.maxConsecutiveFailures) {
            halted = true
            return ActionStatus(ActionKind.HALTED, "إيقاف: $reason ($failures محاولات فاشلة)")
        }
        return ActionStatus(ActionKind.FAILED, reason)
    }
}
