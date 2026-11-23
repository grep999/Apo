package com.emberfall.cards

import com.emberfall.board.*
import com.emberfall.combat.*
import java.util.Collections

const val CARD_RULES_VERSION = "emberfall-v0.2-cards-1"
const val MAX_EFFECT_EVENTS_PER_ACTION = 128
const val MAX_EFFECT_DEPTH = 16
const val STARTING_HAND_SIZE = 5

enum class CardType { ATTACK, SKILL, POWER, CURSE }
enum class CardRarity { BASIC, COMMON, UNCOMMON, RARE, CURSE }

class CardCost(colored: Map<Hue, Int> = emptyMap(), val generic: Int = 0) {
    val colored: Map<Hue, Int> = Collections.unmodifiableMap(Hue.entries.associateWith { colored[it] ?: 0 }.filterValues { it > 0 })
    init {
        require(generic >= 0 && colored.values.all { it > 0 })
    }
    fun copy(colored: Map<Hue, Int> = this.colored, generic: Int = this.generic) = CardCost(colored, generic)
    override fun equals(other: Any?) = other is CardCost && colored == other.colored && generic == other.generic
    override fun hashCode() = 31 * colored.hashCode() + generic
}

sealed interface CardEffect {
    data class Damage(val amount: Int) : CardEffect { init { require(amount > 0) } }
    data class Block(val amount: Int) : CardEffect { init { require(amount > 0) } }
    data class Pop(val largest: Boolean = false) : CardEffect
    data class Draw(val amount: Int) : CardEffect { init { require(amount > 0) } }
    data class GainTapsNextTurn(val amount: Int) : CardEffect { init { require(amount > 0) } }
    data class DelayEnemy(val turns: Int = 1) : CardEffect { init { require(turns == 1) } }
    data class Recolor3x3(val hue: Hue) : CardEffect
    data class SpawnBeside(val cell: Cell) : CardEffect
    data class ShatterRow(val row: Int) : CardEffect { init { require(row in 0..6) } }
    data class ShatterColumn(val column: Int) : CardEffect { init { require(column in 0..6) } }
    data class FuseGroups(val hue: Hue) : CardEffect
    data class GainSpark(val amount: Int) : CardEffect { init { require(amount > 0) } }
    data object Echo : CardEffect
    data object Exhaust : CardEffect
    data class Nested(val effects: List<CardEffect>) : CardEffect
}

class CardDefinition(
    val id: String,
    val name: String,
    val frameHue: Hue,
    val type: CardType,
    val rarity: CardRarity,
    val cost: CardCost,
    effects: List<CardEffect>,
    val exhaust: Boolean = false,
    val retain: Boolean = false,
    upgradedCost: CardCost? = null,
    upgradedEffects: List<CardEffect>? = null
) {
    val effects: List<CardEffect> = Collections.unmodifiableList(effects.toList())
    val upgradedEffects: List<CardEffect>? = upgradedEffects?.let { Collections.unmodifiableList(it.toList()) }
    val upgradedCost: CardCost? = upgradedCost
    init {
        require(id.isNotBlank() && name.isNotBlank())
        require(this.effects.isNotEmpty())
        require((upgradedCost == null) == (this.upgradedEffects == null))
    }
    fun effects(upgraded: Boolean) = if (upgraded) upgradedEffects ?: effects else effects
    fun cost(upgraded: Boolean) = if (upgraded) upgradedCost ?: cost else cost
}

data class CardInstance(val instanceId: Long, val definitionId: String, val upgraded: Boolean = false) {
    init { require(instanceId >= 0 && definitionId.isNotBlank()) }
}

data class PaymentPlan(
    val coloredInk: Map<Hue, Int> = emptyMap(),
    val genericInk: Map<Hue, Int> = emptyMap(),
    val sparkForGeneric: Int = 0
) {
    init {
        require(coloredInk.values.all { it >= 0 } && genericInk.values.all { it >= 0 } && sparkForGeneric >= 0)
    }
    val genericPaid: Long get() = genericInk.values.sumOf { it.toLong() } + sparkForGeneric.toLong()
}

object CardPayment {
    private val wheel = listOf(Hue.EMBER, Hue.GOLD, Hue.MOSS, Hue.TIDE, Hue.VIOLET)

    fun automatic(cost: CardCost, player: PlayerState): PaymentPlan? {
        val colored = cost.colored
        if (colored.any { (h, n) -> player.immutableInk.getValue(h) < n }) return null
        val remaining = Hue.entries.associateWith { player.immutableInk.getValue(it) - (colored[it] ?: 0) }.toMutableMap()
        var spark = minOf(player.spark, cost.generic)
        var genericLeft = cost.generic - spark
        val generic = linkedMapOf<Hue, Int>()
        val order = Hue.entries.sortedWith(compareBy<Hue> { remaining.getValue(it) }.thenBy { wheel.indexOf(it) })
        for (hue in order) {
            if (genericLeft == 0) break
            val take = minOf(remaining.getValue(hue), genericLeft)
            if (take > 0) {
                generic[hue] = take
                remaining[hue] = remaining.getValue(hue) - take
                genericLeft -= take
            }
        }
        if (genericLeft > 0) return null
        return PaymentPlan(colored, generic, spark)
    }

    fun validate(cost: CardCost, player: PlayerState, plan: PaymentPlan): Boolean {
        if (Hue.entries.any { (plan.coloredInk[it] ?: 0) != (cost.colored[it] ?: 0) }) return false
        if (plan.genericPaid != cost.generic.toLong()) return false
        if (plan.sparkForGeneric > player.spark) return false
        return Hue.entries.all { h ->
            val spend = (plan.coloredInk[h] ?: 0).toLong() + (plan.genericInk[h] ?: 0).toLong()
            spend >= 0L && spend <= player.immutableInk.getValue(h).toLong()
        } && plan.genericInk.keys.all { it in Hue.entries }
    }

    fun apply(player: PlayerState, plan: PaymentPlan): PlayerState {
        val ink = player.immutableInk.toMutableMap()
        Hue.entries.forEach { h ->
            ink[h] = ink.getValue(h) - (plan.coloredInk[h] ?: 0) - (plan.genericInk[h] ?: 0)
        }
        return player.copy(ink = ink, spark = player.spark - plan.sparkForGeneric)
    }
}

class CardBattleState(
    val combat: CombatState,
    drawPile: List<CardInstance>,
    hand: List<CardInstance> = emptyList(),
    discardPile: List<CardInstance> = emptyList(),
    exhaustPile: List<CardInstance> = emptyList(),
    val runSeed: Long = combat.runSeed,
    val drawOrdinal: Long = 0,
    val nextCardInstanceId: Long = 0,
    val bonusTapsNextTurn: Int = 0,
    lastTopLevelEffects: List<CardEffect>? = null,
    val lastTopLevelCardId: String? = null,
    val lastCardEchoed: Boolean = false,
    val playedThisTurn: Int = 0,
    val nextEffectSequence: Long = 0
) {
    val drawPile = Collections.unmodifiableList(drawPile.toList())
    val hand = Collections.unmodifiableList(hand.toList())
    val discardPile = Collections.unmodifiableList(discardPile.toList())
    val exhaustPile = Collections.unmodifiableList(exhaustPile.toList())
    val lastTopLevelEffects = lastTopLevelEffects?.let { Collections.unmodifiableList(it.toList()) }
    init {
        require(drawOrdinal >= 0 && nextCardInstanceId >= 0 && bonusTapsNextTurn >= 0)
        require(playedThisTurn >= 0 && nextEffectSequence >= 0)
        val ids = (this.drawPile + this.hand + this.discardPile + this.exhaustPile).map { it.instanceId }
        require(ids.distinct().size == ids.size) { "Card instance IDs must be unique across zones" }
    }
    fun copy(
        combat: CombatState = this.combat,
        drawPile: List<CardInstance> = this.drawPile,
        hand: List<CardInstance> = this.hand,
        discardPile: List<CardInstance> = this.discardPile,
        exhaustPile: List<CardInstance> = this.exhaustPile,
        runSeed: Long = this.runSeed,
        drawOrdinal: Long = this.drawOrdinal,
        nextCardInstanceId: Long = this.nextCardInstanceId,
        bonusTapsNextTurn: Int = this.bonusTapsNextTurn,
        lastTopLevelEffects: List<CardEffect>? = this.lastTopLevelEffects,
        lastTopLevelCardId: String? = this.lastTopLevelCardId,
        lastCardEchoed: Boolean = this.lastCardEchoed,
        playedThisTurn: Int = this.playedThisTurn,
        nextEffectSequence: Long = this.nextEffectSequence
    ) = CardBattleState(combat, drawPile, hand, discardPile, exhaustPile, runSeed, drawOrdinal,
        nextCardInstanceId, bonusTapsNextTurn, lastTopLevelEffects, lastTopLevelCardId,
        lastCardEchoed, playedThisTurn, nextEffectSequence)

    companion object {
        fun start(combat: CombatState, deck: List<String>, catalog: Map<String, CardDefinition>, seed: Long = combat.runSeed): CardBattleState {
            require(deck.isNotEmpty() && deck.all { it in catalog })
            val instances = deck.mapIndexed { i, id -> CardInstance(i.toLong(), id) }
            val shuffled = deterministicShuffle(instances, seed, 0)
            return CardBattleState(combat, shuffled, runSeed = seed, nextCardInstanceId = instances.size.toLong())
        }
    }
}

private fun deterministicShuffle(cards: List<CardInstance>, seed: Long, ordinal: Long): List<CardInstance> {
    val result = cards.toMutableList()
    val rng = DeterministicRng(seed xor (ordinal * -7046029254386353131L))
    for (i in result.lastIndex downTo 1) {
        val j = rng.nextInt(i + 1)
        val temp = result[i]; result[i] = result[j]; result[j] = temp
    }
    return result
}

sealed interface CardBattleCommand {
    data object BeginTurn : CardBattleCommand
    data object EndWeave : CardBattleCommand
    data class Play(val instanceId: Long, val payment: PaymentPlan? = null, val target: Coord? = null, val secondTarget: Coord? = null, val chosenHue: Hue? = null) : CardBattleCommand
    data object EndCast : CardBattleCommand
    data class ResolveWall(val rows: List<List<Cell?>>) : CardBattleCommand
    data object ResolveEnemy : CardBattleCommand
    data object FinishCleanup : CardBattleCommand
}

enum class CardRejection { WRONG_PHASE, UNKNOWN_CARD, INVALID_TARGET, UNAFFORDABLE, INVALID_PAYMENT, EFFECT_LIMIT, EFFECT_FAILED }
sealed interface CardEvent {
    data class Drew(val instanceId: Long, val cardId: String) : CardEvent
    data class Played(val instanceId: Long, val cardId: String, val echoed: Boolean) : CardEvent
    data class Paid(val plan: PaymentPlan) : CardEvent
    data class EffectResolved(val sequence: Long, val cardId: String, val effect: CardEffect, val echoed: Boolean) : CardEvent
    data class CardDiscarded(val instanceId: Long) : CardEvent
    data class CardExhausted(val instanceId: Long) : CardEvent
    data class EchoQueued(val cardId: String) : CardEvent
}
sealed interface CardBattleResult {
    data class Accepted(val state: CardBattleState, val events: List<CardEvent>) : CardBattleResult
    data class Rejected(val reason: CardRejection) : CardBattleResult
}

private data class QueuedEffect(val sequence: Long, val cardId: String, val effect: CardEffect, val depth: Int, val echoed: Boolean)

class CardEngine(
    private val catalog: Map<String, CardDefinition>,
    private val combatEngine: CombatEngine = CombatEngine(),
    private val boardEngine: BoardEngine = BoardEngine()
) {
    init {
        require(catalog.isNotEmpty() && catalog.all { it.key == it.value.id })
    }

    fun transition(state: CardBattleState, command: CardBattleCommand): CardBattleResult = when (command) {
        CardBattleCommand.BeginTurn -> beginTurn(state)
        CardBattleCommand.EndWeave -> combatTransition(state, CombatCommand.EndWeave)
        is CardBattleCommand.Play -> play(state, command)
        CardBattleCommand.EndCast -> combatTransition(state, CombatCommand.EndCast)
        is CardBattleCommand.ResolveWall -> combatTransition(state, CombatCommand.ResolveWall(command.rows))
        CardBattleCommand.ResolveEnemy -> combatTransition(state, CombatCommand.ResolveEnemy)
        CardBattleCommand.FinishCleanup -> cleanup(state)
    }

    private fun beginTurn(state: CardBattleState): CardBattleResult {
        if (state.combat.phase != CombatPhase.DRAW) return CardBattleResult.Rejected(CardRejection.WRONG_PHASE)
        var next = state
        val events = mutableListOf<CardEvent>()
        if (state.bonusTapsNextTurn > 0) {
            val taps = state.combat.tapsRemaining.toLong() + state.bonusTapsNextTurn
            if (taps > Int.MAX_VALUE) return CardBattleResult.Rejected(CardRejection.EFFECT_FAILED)
            next = next.copy(combat = next.combat.copy(tapsRemaining = taps.toInt()), bonusTapsNextTurn = 0)
        }
        next = draw(next, STARTING_HAND_SIZE, events)
        return when (val result = combatEngine.transition(next.combat, CombatCommand.CompleteDraw)) {
            is CombatResult.Rejected -> CardBattleResult.Rejected(CardRejection.WRONG_PHASE)
            is CombatResult.Accepted -> CardBattleResult.Accepted(next.copy(
                combat = result.state, lastTopLevelEffects = null, lastTopLevelCardId = null,
                lastCardEchoed = false, playedThisTurn = 0
            ), events)
        }
    }

    private fun play(state: CardBattleState, command: CardBattleCommand.Play): CardBattleResult {
        if (state.combat.phase != CombatPhase.CAST) return CardBattleResult.Rejected(CardRejection.WRONG_PHASE)
        val index = state.hand.indexOfFirst { it.instanceId == command.instanceId }
        if (index < 0) return CardBattleResult.Rejected(CardRejection.UNKNOWN_CARD)
        val card = state.hand[index]
        val definition = catalog[card.definitionId] ?: return CardBattleResult.Rejected(CardRejection.UNKNOWN_CARD)
        if (!targetsValid(definition.effects(card.upgraded), command)) return CardBattleResult.Rejected(CardRejection.INVALID_TARGET)
        val cost = definition.cost(card.upgraded)
        val plan = command.payment ?: CardPayment.automatic(cost, state.combat.player)
            ?: return CardBattleResult.Rejected(CardRejection.UNAFFORDABLE)
        if (!CardPayment.validate(cost, state.combat.player, plan)) return CardBattleResult.Rejected(CardRejection.INVALID_PAYMENT)

        val paidCombat = state.combat.copy(player = CardPayment.apply(state.combat.player, plan))
        val isEchoCard = definition.effects(card.upgraded).any { it == CardEffect.Echo }
        var next = state.copy(combat = paidCombat, hand = state.hand.filterIndexed { i, _ -> i != index })
        val events = mutableListOf<CardEvent>(CardEvent.Paid(plan), CardEvent.Played(card.instanceId, definition.id, false))
        val exhausted = definition.exhaust || definition.effects(card.upgraded).contains(CardEffect.Exhaust)
        if (exhausted) {
            next = next.copy(exhaustPile = next.exhaustPile + card)
            events += CardEvent.CardExhausted(card.instanceId)
        } else {
            next = next.copy(discardPile = next.discardPile + card)
        }
        val queue = ArrayDeque<QueuedEffect>()
        var sequence = state.nextEffectSequence
        definition.effects(card.upgraded).forEach { effect ->
            queue.addLast(QueuedEffect(sequence++, definition.id, effect, 0, false))
        }
        var lastEffects = state.lastTopLevelEffects
        var lastId = state.lastTopLevelCardId
        var echoed = state.lastCardEchoed
        if (!isEchoCard) { lastEffects = definition.effects(card.upgraded); lastId = definition.id; echoed = false }
        var processed = 0
        while (queue.isNotEmpty()) {
            if (processed >= MAX_EFFECT_EVENTS_PER_ACTION) return CardBattleResult.Rejected(CardRejection.EFFECT_LIMIT)
            val item = queue.removeFirst()
            if (item.depth > MAX_EFFECT_DEPTH) return CardBattleResult.Rejected(CardRejection.EFFECT_LIMIT)
            processed++
            when (val result = resolveEffect(next, item, command, lastEffects, lastId, echoed, queue, sequence, events)) {
                is EffectResolution.Failed -> return CardBattleResult.Rejected(result.reason)
                is EffectResolution.Done -> {
                    next = result.state
                    sequence = result.nextSequence
                    echoed = result.echoed
                    events += CardEvent.EffectResolved(item.sequence, item.cardId, item.effect, item.echoed)
                }
            }
        }
        next = next.copy(lastTopLevelEffects = lastEffects, lastTopLevelCardId = lastId,
            lastCardEchoed = echoed, playedThisTurn = state.playedThisTurn + 1, nextEffectSequence = sequence)
        return CardBattleResult.Accepted(next, events)
    }

    private sealed interface EffectResolution {
        data class Done(val state: CardBattleState, val nextSequence: Long, val echoed: Boolean = false) : EffectResolution
        data class Failed(val reason: CardRejection) : EffectResolution
    }

    private fun resolveEffect(
        state: CardBattleState,
        item: QueuedEffect,
        command: CardBattleCommand.Play,
        lastEffects: List<CardEffect>?,
        lastId: String?,
        wasEchoed: Boolean,
        queue: ArrayDeque<QueuedEffect>,
        nextSequence: Long,
        events: MutableList<CardEvent>
    ): EffectResolution {
        var next = state
        var seq = nextSequence
        var echoed = wasEchoed
        fun combat(cmd: CombatCommand): Boolean {
            return when (val result = combatEngine.transition(next.combat, cmd)) {
                is CombatResult.Rejected -> false
                is CombatResult.Accepted -> { next = next.copy(combat = result.state); true }
            }
        }
        when (val effect = item.effect) {
            is CardEffect.Damage -> if (next.combat.phase !in setOf(CombatPhase.VICTORY, CombatPhase.DEFEAT) && !combat(CombatCommand.CardDamage(effect.amount))) return EffectResolution.Failed(CardRejection.EFFECT_FAILED)
            is CardEffect.Block -> {
                val totalBlock = next.combat.player.block.toLong() + effect.amount.toLong()
                if (totalBlock > Int.MAX_VALUE) return EffectResolution.Failed(CardRejection.EFFECT_FAILED)
                if (next.combat.phase !in setOf(CombatPhase.VICTORY, CombatPhase.DEFEAT) && !combat(CombatCommand.CardBlock(effect.amount))) return EffectResolution.Failed(CardRejection.EFFECT_FAILED)
            }
            is CardEffect.Pop -> {
                if (next.combat.phase == CombatPhase.VICTORY || next.combat.phase == CombatPhase.DEFEAT) return EffectResolution.Done(next, seq, echoed)
                val target = (if (effect.largest) largestLegalPop(next.combat) else command.target)
                    ?: return EffectResolution.Failed(CardRejection.INVALID_TARGET)
                if (!combat(CombatCommand.CardPop(target))) return EffectResolution.Failed(CardRejection.EFFECT_FAILED)
            }
            is CardEffect.Draw -> next = draw(next, effect.amount, events)
            is CardEffect.GainTapsNextTurn -> {
                val total = next.bonusTapsNextTurn.toLong() + effect.amount
                if (total > Int.MAX_VALUE) return EffectResolution.Failed(CardRejection.EFFECT_FAILED)
                next = next.copy(bonusTapsNextTurn = total.toInt())
            }
            is CardEffect.DelayEnemy -> if (next.combat.phase !in setOf(CombatPhase.VICTORY, CombatPhase.DEFEAT) && !combat(CombatCommand.DelayNextIntent)) return EffectResolution.Failed(CardRejection.EFFECT_FAILED)
            is CardEffect.Recolor3x3 -> {
                val center = command.target ?: return EffectResolution.Failed(CardRejection.INVALID_TARGET)
                val cells = next.combat.board.cells.toMutableList()
                for (y in maxOf(0, center.y - 1)..minOf(6, center.y + 1)) for (x in maxOf(0, center.x - 1)..minOf(6, center.x + 1)) {
                    if (x !in next.combat.board.sealedColumns) {
                        val i = y * 7 + x
                        val cell = cells[i]
                        if (cell != null && cell.kind != TileKind.STONE) cells[i] = Cell.hue(effect.hue, cell.branded)
                    }
                }
                next = next.copy(combat = next.combat.copy(board = next.combat.board.copy(cells = cells)))
            }
            is CardEffect.SpawnBeside -> {
                val anchor = command.target ?: return EffectResolution.Failed(CardRejection.INVALID_TARGET)
                val spot = anchor.neighbors().filter { it.x !in next.combat.board.sealedColumns && next.combat.board[it] == null }
                    .minByOrNull { it.index() } ?: return EffectResolution.Failed(CardRejection.EFFECT_FAILED)
                next = next.copy(combat = next.combat.copy(board = next.combat.board.withCell(spot, effect.cell)))
            }
            is CardEffect.ShatterRow -> {
                val cells = next.combat.board.cells.toMutableList()
                for (x in 0..6) if (x !in next.combat.board.sealedColumns) cells[effect.row * 7 + x] = null
                val board = boardEngine.gravity(next.combat.board.copy(cells = cells))
                next = next.copy(combat = next.combat.copy(board = board))
            }
            is CardEffect.ShatterColumn -> {
                if (effect.column !in next.combat.board.sealedColumns) {
                    val cells = next.combat.board.cells.toMutableList()
                    for (y in 0..6) cells[y * 7 + effect.column] = null
                    val board = boardEngine.gravity(next.combat.board.copy(cells = cells))
                    next = next.copy(combat = next.combat.copy(board = board))
                }
            }
            is CardEffect.FuseGroups -> {
                val first = command.target ?: return EffectResolution.Failed(CardRejection.INVALID_TARGET)
                val second = command.secondTarget ?: return EffectResolution.Failed(CardRejection.INVALID_TARGET)
                val a = hueComponent(next.combat.board, first) ?: return EffectResolution.Failed(CardRejection.INVALID_TARGET)
                val b = hueComponent(next.combat.board, second) ?: return EffectResolution.Failed(CardRejection.INVALID_TARGET)
                if (a.toSet() == b.toSet() || !a.any { p -> p.neighbors().any { it in b } }) return EffectResolution.Failed(CardRejection.INVALID_TARGET)
                val cells = next.combat.board.cells.toMutableList()
                (a + b).forEach { p -> cells[p.index()] = Cell.hue(effect.hue, cells[p.index()]?.branded == true) }
                next = next.copy(combat = next.combat.copy(board = next.combat.board.copy(cells = cells)))
            }
            is CardEffect.GainSpark -> {
                val spark = next.combat.player.spark.toLong() + effect.amount
                if (spark > Int.MAX_VALUE) return EffectResolution.Failed(CardRejection.EFFECT_FAILED)
                next = next.copy(combat = next.combat.copy(player = next.combat.player.copy(spark = spark.toInt())))
            }
            CardEffect.Echo -> {
                if (lastEffects == null || lastId == null || echoed || item.echoed) return EffectResolution.Failed(CardRejection.EFFECT_FAILED)
                lastEffects.filterNot { it == CardEffect.Echo }.forEach { child ->
                    queue.addLast(QueuedEffect(seq++, lastId, child, item.depth + 1, true))
                }
                echoed = true
                events += CardEvent.EchoQueued(lastId)
            }
            CardEffect.Exhaust -> Unit // The card's zone was committed to Exhaust before effects.
            is CardEffect.Nested -> effect.effects.forEach { child ->
                queue.addLast(QueuedEffect(seq++, item.cardId, child, item.depth + 1, item.echoed))
            }
        }
        return EffectResolution.Done(next, seq, echoed)
    }

    private fun targetsValid(effects: List<CardEffect>, command: CardBattleCommand.Play): Boolean {
        if (effects.any { it is CardEffect.Pop && !it.largest || it is CardEffect.Recolor3x3 || it is CardEffect.SpawnBeside || it is CardEffect.FuseGroups }) {
            if (command.target == null) return false
        }
        if (effects.any { it is CardEffect.FuseGroups } && command.secondTarget == null) return false
        return true
    }

    private fun largestLegalPop(combat: CombatState): Coord? {
        val candidates = mutableListOf<Pair<Coord, Int>>()
        for (y in 0..6) for (x in 0..6) {
            val coord = Coord(x, y)
            when (val result = boardEngine.tap(combat.board, coord, combat.spectrum)) {
                is TapOutcome.Accepted -> candidates += coord to result.result.groupSize
                is TapOutcome.Rejected -> Unit
            }
        }
        return candidates.sortedWith(compareByDescending<Pair<Coord, Int>> { it.second }.thenBy { it.first.index() })
            .firstOrNull()?.first
    }

    private fun hueComponent(board: BoardState, start: Coord): List<Coord>? {
        if (start.x in board.sealedColumns) return null
        val cell = board[start] ?: return null
        if (cell.kind != TileKind.HUE) return null
        val seen = linkedSetOf<Coord>()
        val queue = ArrayDeque<Coord>(); queue.add(start)
        while (queue.isNotEmpty()) {
            val p = queue.removeFirst()
            if (!seen.add(p)) continue
            p.neighbors().filter { it.x !in board.sealedColumns && board[it]?.kind == TileKind.HUE && board[it]?.hue == cell.hue && it !in seen }
                .forEach(queue::addLast)
        }
        return seen.sortedBy { it.index() }
    }

    private fun draw(state: CardBattleState, count: Int, events: MutableList<CardEvent>): CardBattleState {
        var next = state
        repeat(count) {
            if (next.drawPile.isEmpty() && next.discardPile.isNotEmpty()) {
                val shuffled = deterministicShuffle(next.discardPile, next.runSeed, next.drawOrdinal + 1)
                next = next.copy(drawPile = shuffled, discardPile = emptyList(), drawOrdinal = next.drawOrdinal + 1)
            }
            val card = next.drawPile.firstOrNull() ?: return@repeat
            next = next.copy(drawPile = next.drawPile.drop(1), hand = next.hand + card)
            events += CardEvent.Drew(card.instanceId, card.definitionId)
        }
        return next
    }

    private fun combatTransition(state: CardBattleState, command: CombatCommand): CardBattleResult =
        when (val result = combatEngine.transition(state.combat, command)) {
            is CombatResult.Rejected -> CardBattleResult.Rejected(CardRejection.WRONG_PHASE)
            is CombatResult.Accepted -> CardBattleResult.Accepted(state.copy(combat = result.state), emptyList())
        }

    private fun cleanup(state: CardBattleState): CardBattleResult {
        if (state.combat.phase != CombatPhase.CLEANUP) return CardBattleResult.Rejected(CardRejection.WRONG_PHASE)
        val retained = state.hand.filter { catalog[it.definitionId]?.retain == true }
        val discarded = state.hand.filterNot { catalog[it.definitionId]?.retain == true }
        var next = state.copy(hand = retained, discardPile = state.discardPile + discarded,
            lastTopLevelEffects = null, lastTopLevelCardId = null, lastCardEchoed = false, playedThisTurn = 0)
        val result = combatEngine.transition(next.combat, CombatCommand.FinishCleanup)
        return when (result) {
            is CombatResult.Rejected -> CardBattleResult.Rejected(CardRejection.WRONG_PHASE)
            is CombatResult.Accepted -> CardBattleResult.Accepted(next.copy(combat = result.state),
                discarded.map { CardEvent.CardDiscarded(it.instanceId) })
        }
    }
}

/** Small original vertical-slice catalogue; content is data, not one class per card. */
object StarterCardCatalog {
    val cards: Map<String, CardDefinition> = listOf(
        CardDefinition("ember_strike", "Ember Strike", Hue.EMBER, CardType.ATTACK, CardRarity.BASIC,
            CardCost(mapOf(Hue.EMBER to 1)), listOf(CardEffect.Damage(6))),
        CardDefinition("moss_ward", "Moss Ward", Hue.MOSS, CardType.SKILL, CardRarity.BASIC,
            CardCost(mapOf(Hue.MOSS to 1)), listOf(CardEffect.Block(5))),
        CardDefinition("pop_shot", "Pop Shot", Hue.GOLD, CardType.ATTACK, CardRarity.COMMON,
            CardCost(generic = 1), listOf(CardEffect.Pop(largest = true), CardEffect.Damage(3)), exhaust = false),
        CardDefinition("tide_surge", "Tide Surge", Hue.TIDE, CardType.SKILL, CardRarity.UNCOMMON,
            CardCost(mapOf(Hue.TIDE to 2)), listOf(CardEffect.DelayEnemy())),
        CardDefinition("echo_chime", "Echo Chime", Hue.VIOLET, CardType.SKILL, CardRarity.RARE,
            CardCost(generic = 1), listOf(CardEffect.Echo), exhaust = true),
        CardDefinition("ash_ward", "Ash Ward", Hue.GOLD, CardType.SKILL, CardRarity.BASIC,
            CardCost(mapOf(Hue.GOLD to 1)), listOf(CardEffect.GainSpark(1), CardEffect.Block(3))),
        CardDefinition("cinder_tithe", "Cinder Tithe", Hue.GOLD, CardType.SKILL, CardRarity.UNCOMMON,
            CardCost(mapOf(Hue.GOLD to 1)), listOf(CardEffect.GainSpark(6)), exhaust = true),
        CardDefinition("hearthlight", "Hearthlight", Hue.GOLD, CardType.SKILL, CardRarity.UNCOMMON,
            CardCost(mapOf(Hue.GOLD to 1)), listOf(CardEffect.GainTapsNextTurn(1))),
        CardDefinition("quarry_chisel", "Quarry Chisel", Hue.VIOLET, CardType.SKILL, CardRarity.COMMON,
            CardCost(mapOf(Hue.VIOLET to 1)), listOf(CardEffect.ShatterColumn(3))),
        CardDefinition("pyre_line", "Pyre Line", Hue.EMBER, CardType.ATTACK, CardRarity.UNCOMMON,
            CardCost(mapOf(Hue.EMBER to 2)), listOf(CardEffect.ShatterRow(5), CardEffect.Damage(6)))
    ).associateBy { it.id }
}
