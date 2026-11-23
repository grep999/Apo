package com.emberfall.combat

import com.emberfall.board.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Collections

const val BASE_TAPS_PER_TURN = 3
const val STARTING_VIGIL = 70
const val MAX_VIGIL = 70
const val STARTING_FLASKS = 2
const val MAX_FLASKS = 5
const val FLASK_HEAL = 30
const val INK_CAP_PER_HUE = 8
const val COMBAT_RULES_VERSION = "emberfall-v0.2-combat-1"

enum class CombatPhase { DRAW, WEAVE, CAST, WALL, ENEMY, CLEANUP, VICTORY, DEFEAT }

sealed interface EnemyIntent {
    data class Strike(val damage: Int) : EnemyIntent { init { require(damage >= 0) } }
    data object AshRow : EnemyIntent
    data class StoneSeed(val column: Int) : EnemyIntent { init { require(column in 0..6) } }
    data class Stain(val hue: Hue) : EnemyIntent
    data object Fog : EnemyIntent
    data class Seal(val column: Int) : EnemyIntent { init { require(column in 0..6) } }
    class Brand(cells: List<Coord>) : EnemyIntent {
        val targets: List<Coord> = Collections.unmodifiableList(cells.distinct().sortedBy { it.index() })
        override fun equals(other: Any?): Boolean = other is Brand && targets == other.targets
        override fun hashCode(): Int = targets.hashCode()
        override fun toString(): String = "Brand(targets=$targets)"
    }
    data object Surge : EnemyIntent
    data object Snatch : EnemyIntent
    data object Curse : EnemyIntent
}

class EnemyDefinition(
    val id: String,
    val maxVigor: Int,
    intents: List<EnemyIntent>
) {
    val intentSequence: List<EnemyIntent> = Collections.unmodifiableList(intents.toList())
    init {
        require(id.isNotBlank())
        require(maxVigor > 0)
        require(intentSequence.isNotEmpty())
    }
    fun intentAt(cursor: Long): EnemyIntent = intentSequence[(cursor % intentSequence.size).toInt()]
}

class PlayerState(
    val vigil: Int = STARTING_VIGIL,
    val block: Int = 0,
    val flasks: Int = STARTING_FLASKS,
    ink: Map<Hue, Int> = Hue.entries.associateWith { 0 },
    val spark: Int = 0
) {
    val immutableInk: Map<Hue, Int> = Collections.unmodifiableMap(Hue.entries.associateWith { ink[it] ?: 0 })
    val ink: Map<Hue, Int> get() = immutableInk
    init {
        require(vigil in 0..MAX_VIGIL)
        require(block >= 0)
        require(flasks in 0..MAX_FLASKS)
        require(immutableInk.values.all { it in 0..INK_CAP_PER_HUE })
        require(spark >= 0)
    }
    fun copy(
        vigil: Int = this.vigil,
        block: Int = this.block,
        flasks: Int = this.flasks,
        ink: Map<Hue, Int> = this.immutableInk,
        spark: Int = this.spark
    ) = PlayerState(vigil, block, flasks, ink, spark)
}

data class EnemyState(val vigor: Int, val block: Int = 0) {
    init { require(vigor >= 0 && block >= 0) }
}

class CombatState(
    val board: BoardState,
    val spectrum: SpectrumState,
    val phase: CombatPhase,
    val player: PlayerState,
    val enemy: EnemyState,
    val enemyDefinition: EnemyDefinition,
    val turn: Int,
    val tapsRemaining: Int,
    val intentCursor: Long,
    val runSeed: Long,
    forecast: List<EnemyIntent>,
    val wallRisesNextTurn: Int = 1,
    val fogTapsRemaining: Int = 0,
    val delayedIntents: Int = 0
) {
    val forecast: List<EnemyIntent> = Collections.unmodifiableList(forecast.toList())
    val nextIntents: List<EnemyIntent> get() = this.forecast
    init {
        require(turn >= 0 && tapsRemaining >= 0 && intentCursor >= 0)
        require(wallRisesNextTurn in 1..2)
        require(fogTapsRemaining >= 0 && delayedIntents >= 0)
        require(enemy.vigor <= enemyDefinition.maxVigor)
        require(nextIntents.size == 2)
    }

    fun copy(
        board: BoardState = this.board,
        spectrum: SpectrumState = this.spectrum,
        phase: CombatPhase = this.phase,
        player: PlayerState = this.player,
        enemy: EnemyState = this.enemy,
        enemyDefinition: EnemyDefinition = this.enemyDefinition,
        turn: Int = this.turn,
        tapsRemaining: Int = this.tapsRemaining,
        intentCursor: Long = this.intentCursor,
        runSeed: Long = this.runSeed,
        forecast: List<EnemyIntent> = this.forecast,
        wallRisesNextTurn: Int = this.wallRisesNextTurn,
        fogTapsRemaining: Int = this.fogTapsRemaining,
        delayedIntents: Int = this.delayedIntents
    ) = CombatState(board, spectrum, phase, player, enemy, enemyDefinition, turn, tapsRemaining,
        intentCursor, runSeed, forecast, wallRisesNextTurn, fogTapsRemaining, delayedIntents)

    companion object {
        fun start(board: BoardState, enemy: EnemyDefinition, spectrum: SpectrumState = SpectrumState(), runSeed: Long = 0L): CombatState =
            CombatState(
                board = board,
                spectrum = spectrum,
                phase = CombatPhase.DRAW,
                player = PlayerState(),
                enemy = EnemyState(enemy.maxVigor),
                enemyDefinition = enemy,
                turn = 0,
                tapsRemaining = BASE_TAPS_PER_TURN,
                intentCursor = 0,
                runSeed = runSeed,
                forecast = listOf(enemy.intentAt(0), enemy.intentAt(1))
            )
    }
}

sealed interface CombatCommand {
    data object CompleteDraw : CombatCommand
    data class Tap(val coord: Coord) : CombatCommand
    data object EndWeave : CombatCommand
    /** Integration seam for EFB-004; only a resolved card effect may issue this during Cast. */
    data class CardDamage(val amount: Int) : CombatCommand { init { require(amount > 0) } }
    /** Card Pop resolves a board group during Cast without spending a Weave Tap. */
    data class CardPop(val coord: Coord) : CombatCommand
    /** Integration seam for EFB-004; only a resolved card effect may issue this during Cast. */
    data class CardBlock(val amount: Int) : CombatCommand { init { require(amount > 0) } }
    /** Integration seam for EFB-004 (e.g. Tide Surge). */
    data object DelayNextIntent : CombatCommand
    data object EndCast : CombatCommand
    /** Each entry is the new hidden incoming row after the corresponding rise. */
    class ResolveWall(replacementRows: List<List<Cell?>>) : CombatCommand {
        val rows: List<List<Cell?>> = Collections.unmodifiableList(replacementRows.map { Collections.unmodifiableList(it.toList()) })
        init { require(rows.all { it.size == BOARD_WIDTH }) }
    }
    data object ResolveEnemy : CombatCommand
    data object FinishCleanup : CombatCommand
    data object UseFlask : CombatCommand
}

enum class CombatRejection {
    WRONG_PHASE, NO_TAPS, ILLEGAL_BOARD_TAP, NO_FLASKS, FULL_VIGIL, INVALID_WALL_ROWS, NO_ENEMY_TO_DAMAGE
}

sealed interface CombatEvent {
    data object DrawStarted : CombatEvent
    data class GroupPopped(val coord: Coord, val groupSize: Int, val hue: Hue?, val chipDamage: Int) : CombatEvent
    data class TapResolved(val coord: Coord, val groupSize: Int, val hue: Hue?, val chipDamage: Int) : CombatEvent
    data class InkGained(val hue: Hue, val accepted: Int, val convertedToSpark: Int) : CombatEvent
    data class VigilLost(val amount: Int, val cause: String) : CombatEvent
    data class VigilRestored(val amount: Int) : CombatEvent
    data class EnemyDamaged(val amount: Int, val remainingVigor: Int) : CombatEvent
    data class BlockGained(val amount: Int) : CombatEvent
    data class WallResolved(val overflow: Boolean, val rises: Int) : CombatEvent
    data class IntentForecasted(val first: EnemyIntent, val second: EnemyIntent) : CombatEvent
    data class IntentResolved(val intent: EnemyIntent) : CombatEvent
    data class IntentDelayed(val intent: EnemyIntent) : CombatEvent
    data class BoardChanged(val cause: String) : CombatEvent
    data object FogApplied : CombatEvent
    data object CardExhaustRequested : CombatEvent
    data object CurseDiscardRequested : CombatEvent
    data object PlayerDefeated : CombatEvent
    data object EnemyDefeated : CombatEvent
    data object TurnCompleted : CombatEvent
}

sealed interface CombatResult {
    data class Accepted(val state: CombatState, val events: List<CombatEvent>) : CombatResult
    data class Rejected(val reason: CombatRejection) : CombatResult
}

class CombatEngine(private val boardEngine: BoardEngine = BoardEngine()) {
    fun transition(state: CombatState, command: CombatCommand): CombatResult {
        if (state.phase == CombatPhase.VICTORY || state.phase == CombatPhase.DEFEAT) {
            return CombatResult.Rejected(CombatRejection.WRONG_PHASE)
        }
        return when (command) {
            CombatCommand.CompleteDraw -> if (state.phase != CombatPhase.DRAW) rejectPhase()
                else accept(state.copy(phase = CombatPhase.WEAVE), CombatEvent.DrawStarted)
            is CombatCommand.Tap -> tap(state, command.coord)
            CombatCommand.EndWeave -> if (state.phase != CombatPhase.WEAVE) rejectPhase()
                else accept(state.copy(phase = CombatPhase.CAST))
            is CombatCommand.CardDamage -> cardDamage(state, command.amount)
            is CombatCommand.CardPop -> cardPop(state, command.coord)
            is CombatCommand.CardBlock -> cardBlock(state, command.amount)
            CombatCommand.DelayNextIntent -> if (state.phase != CombatPhase.CAST) rejectPhase()
                else accept(state.copy(delayedIntents = state.delayedIntents + 1))
            CombatCommand.EndCast -> if (state.phase != CombatPhase.CAST) rejectPhase()
                else accept(state.copy(phase = CombatPhase.WALL))
            is CombatCommand.ResolveWall -> resolveWall(state, command.rows)
            CombatCommand.ResolveEnemy -> resolveEnemy(state)
            CombatCommand.FinishCleanup -> if (state.phase != CombatPhase.CLEANUP) rejectPhase()
                else startNextTurn(state)
            CombatCommand.UseFlask -> useFlask(state)
        }
    }

    private fun tap(state: CombatState, coord: Coord): CombatResult {
        if (state.phase != CombatPhase.WEAVE) return rejectPhase()
        if (state.tapsRemaining <= 0) return CombatResult.Rejected(CombatRejection.NO_TAPS)
        return resolvePop(state, coord, consumeTap = true)
    }

    private fun cardPop(state: CombatState, coord: Coord): CombatResult {
        if (state.phase != CombatPhase.CAST) return rejectPhase()
        return resolvePop(state, coord, consumeTap = false)
    }

    private fun resolvePop(state: CombatState, coord: Coord, consumeTap: Boolean): CombatResult {
        return when (val outcome = boardEngine.tap(state.board, coord, state.spectrum)) {
            is TapOutcome.Rejected -> CombatResult.Rejected(CombatRejection.ILLEGAL_BOARD_TAP)
            is TapOutcome.Accepted -> {
                val pop = outcome.result
                val ink = state.player.immutableInk.toMutableMap()
                var spark = state.player.spark
                val events = mutableListOf<CombatEvent>()
                if (pop.resolvedHue != null && pop.inkPayout > 0) {
                    val before = ink.getValue(pop.resolvedHue)
                    val accepted = minOf(INK_CAP_PER_HUE - before, pop.inkPayout)
                    val overflow = pop.inkPayout - accepted
                    ink[pop.resolvedHue] = before + accepted
                    spark += overflow
                    events += CombatEvent.InkGained(pop.resolvedHue, accepted, overflow)
                }
                var enemyBlock = state.enemy.block
                var enemyVigor = state.enemy.vigor
                val chipBlocked = minOf(enemyBlock, pop.chipDamage)
                enemyBlock -= chipBlocked
                val chipToVigor = pop.chipDamage - chipBlocked
                if (chipToVigor > 0) {
                    enemyVigor = maxOf(0, enemyVigor - chipToVigor)
                    events += CombatEvent.EnemyDamaged(chipToVigor, enemyVigor)
                }
                val brandCost = pop.brandVigilCost
                val actualBrandLoss = minOf(state.player.vigil, brandCost)
                val vigil = state.player.vigil - actualBrandLoss
                if (actualBrandLoss > 0) events += CombatEvent.VigilLost(actualBrandLoss, "Brand")
                events += CombatEvent.GroupPopped(coord, pop.groupSize, pop.resolvedHue, pop.chipDamage)
                if (consumeTap) events += CombatEvent.TapResolved(coord, pop.groupSize, pop.resolvedHue, pop.chipDamage)
                val defeated = enemyVigor == 0
                val playerDefeated = vigil == 0
                val next = state.copy(
                    board = pop.board,
                    spectrum = pop.spectrum,
                    player = state.player.copy(vigil = vigil, ink = ink, spark = spark),
                    enemy = state.enemy.copy(vigor = enemyVigor, block = enemyBlock),
                    tapsRemaining = if (consumeTap) state.tapsRemaining - 1 else state.tapsRemaining,
                    fogTapsRemaining = if (consumeTap) maxOf(0, state.fogTapsRemaining - 1) else state.fogTapsRemaining,
                    phase = when { playerDefeated -> CombatPhase.DEFEAT; defeated -> CombatPhase.VICTORY; else -> state.phase }
                )
                if (defeated) events += CombatEvent.EnemyDefeated
                if (playerDefeated) events += CombatEvent.PlayerDefeated
                CombatResult.Accepted(next, events)
            }
        }
    }

    private fun cardDamage(state: CombatState, amount: Int): CombatResult {
        if (state.phase != CombatPhase.CAST) return rejectPhase()
        if (state.enemy.vigor == 0) return CombatResult.Rejected(CombatRejection.NO_ENEMY_TO_DAMAGE)
        val blocked = minOf(state.enemy.block, amount)
        val damage = amount - blocked
        val vigor = maxOf(0, state.enemy.vigor - damage)
        val events = if (damage > 0) listOf(CombatEvent.EnemyDamaged(damage, vigor)) else emptyList()
        val defeated = vigor == 0
        val next = state.copy(enemy = state.enemy.copy(vigor = vigor, block = state.enemy.block - blocked),
            phase = if (defeated) CombatPhase.VICTORY else state.phase)
        return CombatResult.Accepted(next, events + if (defeated) listOf(CombatEvent.EnemyDefeated) else emptyList())
    }

    private fun cardBlock(state: CombatState, amount: Int): CombatResult {
        if (state.phase != CombatPhase.CAST) return rejectPhase()
        return accept(state.copy(player = state.player.copy(block = state.player.block + amount)), CombatEvent.BlockGained(amount))
    }

    private fun useFlask(state: CombatState): CombatResult {
        if (state.phase !in setOf(CombatPhase.DRAW, CombatPhase.WEAVE, CombatPhase.CAST)) return rejectPhase()
        if (state.player.flasks <= 0) return CombatResult.Rejected(CombatRejection.NO_FLASKS)
        if (state.player.vigil >= MAX_VIGIL) return CombatResult.Rejected(CombatRejection.FULL_VIGIL)
        val healed = minOf(FLASK_HEAL, MAX_VIGIL - state.player.vigil)
        return accept(state.copy(player = state.player.copy(
            vigil = state.player.vigil + healed, flasks = state.player.flasks - 1
        )), CombatEvent.VigilRestored(healed))
    }

    private fun resolveWall(state: CombatState, rows: List<List<Cell?>>): CombatResult {
        if (state.phase != CombatPhase.WALL) return rejectPhase()
        if (rows.size != state.wallRisesNextTurn) return CombatResult.Rejected(CombatRejection.INVALID_WALL_ROWS)
        var board = state.board
        var flasks = state.player.flasks
        var vigil = state.player.vigil
        var rises = 0
        var overflow = false
        val events = mutableListOf<CombatEvent>()
        for (row in rows) {
            // BoardEngine preserves sealed board cells but its incoming-row replacement
            // is global. Carry sealed-column previews through unchanged to honor ADR-012.
            val effectiveIncoming = row.mapIndexed { column, replacement ->
                if (column in board.sealedColumns) board.incoming[column] else replacement
            }
            val result = boardEngine.riseWall(board, effectiveIncoming, flasks)
            board = result.board
            flasks -= result.flasksConsumed
            if (result.overflow) {
                overflow = true
                vigil = maxOf(0, vigil - result.vigilDamage)
                events += CombatEvent.VigilLost(result.vigilDamage, "Wall overflow")
                if (vigil == 0) {
                    rises++
                    break
                }
            }
            rises++
        }
        val defeated = vigil == 0
        val next = state.copy(
            board = board,
            player = state.player.copy(vigil = vigil, flasks = flasks),
            phase = if (defeated) CombatPhase.DEFEAT else CombatPhase.ENEMY,
            wallRisesNextTurn = 1
        )
        events += CombatEvent.WallResolved(overflow, rises)
        if (defeated) events += CombatEvent.PlayerDefeated
        return CombatResult.Accepted(next, events)
    }

    private fun resolveEnemy(state: CombatState): CombatResult {
        if (state.phase != CombatPhase.ENEMY) return rejectPhase()
        val intent = state.nextIntents.first()
        var board = state.board
        var vigil = state.player.vigil
        var block = state.player.block
        var fog = state.fogTapsRemaining
        var wallRises = 1
        val events = mutableListOf<CombatEvent>()
        var delayed = state.delayedIntents
        var cursor = state.intentCursor
        val enemyBlockAtStart = 0 // Enemy Block expires at the start of each enemy phase (ADR-013).
        if (delayed > 0) {
            delayed--
            events += CombatEvent.IntentDelayed(intent)
        } else {
            when (intent) {
                is EnemyIntent.Strike -> {
                    val absorbed = minOf(block, intent.damage)
                    block -= absorbed
                    val damage = intent.damage - absorbed
                    vigil = maxOf(0, vigil - damage)
                    if (damage > 0) events += CombatEvent.VigilLost(damage, "Strike")
                }
                EnemyIntent.AshRow -> {
                    val incoming = board.incoming.mapIndexed { column, current ->
                        if (column in board.sealedColumns) current else Cell.ash()
                    }
                    board = board.copy(incoming = incoming)
                    events += CombatEvent.BoardChanged("AshRow")
                }
                is EnemyIntent.StoneSeed -> {
                    if (intent.column !in board.sealedColumns) {
                        val cells = board.cells.toMutableList()
                        var placed = 0
                        for (y in BOARD_HEIGHT - 1 downTo 0) {
                            val i = y * BOARD_WIDTH + intent.column
                            if (cells[i] == null && placed < 3) { cells[i] = Cell.stone(); placed++ }
                        }
                        board = board.copy(cells = cells)
                    }
                    events += CombatEvent.BoardChanged("StoneSeed")
                }
                is EnemyIntent.Stain -> {
                    val cells = board.cells.mapIndexed { index, cell ->
                        val x = index % BOARD_WIDTH
                        if (x !in board.sealedColumns && cell?.kind == TileKind.HUE && cell.hue == intent.hue) Cell.ash(cell.branded) else cell
                    }
                    val incoming = board.incoming.mapIndexed { column, cell ->
                        if (column !in board.sealedColumns && cell?.kind == TileKind.HUE && cell.hue == intent.hue) Cell.ash(cell.branded) else cell
                    }
                    board = board.copy(cells = cells, incoming = incoming)
                    events += CombatEvent.BoardChanged("Stain")
                }
                EnemyIntent.Fog -> {
                    fog = 3
                    events += CombatEvent.FogApplied
                }
                is EnemyIntent.Seal -> {
                    if (intent.column !in board.sealedColumns) board = board.sealColumn(intent.column, 4)
                    else board = board.copy(sealTurnsRemaining = board.sealTurnsRemaining + (intent.column to 4))
                    events += CombatEvent.BoardChanged("Seal")
                }
                is EnemyIntent.Brand -> {
                    val cells = board.cells.toMutableList()
                    intent.targets.filter { it.x !in board.sealedColumns }.forEach { coord ->
                        val index = coord.index()
                        cells[index]?.let { cells[index] = it.copy(branded = true) }
                    }
                    board = board.copy(cells = cells)
                    events += CombatEvent.BoardChanged("Brand")
                }
                EnemyIntent.Surge -> wallRises = 2
                EnemyIntent.Snatch -> events += CombatEvent.CardExhaustRequested
                EnemyIntent.Curse -> events += CombatEvent.CurseDiscardRequested
            }
            events += CombatEvent.IntentResolved(intent)
            cursor++
        }
        board = board.advanceSealDurations()
        val nextForecast = listOf(state.enemyDefinition.intentAt(cursor), state.enemyDefinition.intentAt(cursor + 1))
        val defeated = vigil == 0
        val next = state.copy(
            board = board,
            player = state.player.copy(vigil = vigil, block = block),
            enemy = state.enemy.copy(block = enemyBlockAtStart),
            phase = if (defeated) CombatPhase.DEFEAT else CombatPhase.CLEANUP,
            intentCursor = cursor,
            forecast = nextForecast,
            wallRisesNextTurn = wallRises,
            fogTapsRemaining = fog,
            delayedIntents = delayed
        )
        events += CombatEvent.IntentForecasted(nextForecast[0], nextForecast[1])
        if (defeated) events += CombatEvent.PlayerDefeated
        return CombatResult.Accepted(next, events)
    }

    private fun startNextTurn(state: CombatState): CombatResult {
        val next = state.copy(
            phase = CombatPhase.DRAW,
            player = state.player.copy(block = 0),
            spectrum = boardEngine.resetSpectrumAtTurnBoundary(),
            turn = state.turn + 1,
            tapsRemaining = BASE_TAPS_PER_TURN,
            fogTapsRemaining = state.fogTapsRemaining
        )
        return accept(next, CombatEvent.TurnCompleted, CombatEvent.DrawStarted)
    }

    private fun accept(state: CombatState, vararg events: CombatEvent) =
        CombatResult.Accepted(state, events.toList())

    private fun rejectPhase() = CombatResult.Rejected(CombatRejection.WRONG_PHASE)
}


data class CombatReplayFrame(
    val state: CombatState,
    val events: List<CombatEvent>,
    val rejection: CombatRejection?,
    val digest: String
)

class CombatReplay(private val engine: CombatEngine = CombatEngine()) {
    fun replay(initial: CombatState, commands: List<CombatCommand>): List<CombatReplayFrame> {
        var state = initial
        val frames = mutableListOf(
            CombatReplayFrame(state, emptyList(), null, CombatStateDigest.sha256(state))
        )
        for (command in commands) {
            when (val result = engine.transition(state, command)) {
                is CombatResult.Accepted -> {
                    state = result.state
                    frames += CombatReplayFrame(
                        state,
                        Collections.unmodifiableList(result.events.toList()),
                        null,
                        CombatStateDigest.sha256(state)
                    )
                }
                is CombatResult.Rejected -> frames += CombatReplayFrame(
                    state,
                    emptyList(),
                    result.reason,
                    CombatStateDigest.sha256(state)
                )
            }
        }
        return Collections.unmodifiableList(frames.toList())
    }
}

/** Stable replay identity over the accepted board digest and all combat-relevant state. */
object CombatStateDigest {
    fun sha256(state: CombatState): String {
        val canonical = buildString {
            append(COMBAT_RULES_VERSION).append('|')
            append(BoardStateDigest.sha256(state.board, state.spectrum)).append('|')
            append(state.phase.name).append('|').append(state.turn).append('|').append(state.tapsRemaining).append('|')
            append(state.player.vigil).append('|').append(state.player.block).append('|').append(state.player.flasks).append('|')
            append(state.player.immutableInk.toSortedMap(compareBy { it.ordinal }).entries.joinToString(",") { "${it.key.name}:${it.value}" }).append('|')
            append(state.player.spark).append('|').append(state.enemy.vigor).append('|').append(state.enemy.block).append('|')
            append(state.runSeed).append('|').append(state.enemyDefinition.id).append('|').append(state.enemyDefinition.maxVigor).append('|')
            append(state.enemyDefinition.intentSequence.joinToString(";")).append('|').append(state.intentCursor).append('|')
            append(state.nextIntents.joinToString(";") { it.toString() }).append('|')
            append(state.wallRisesNextTurn).append('|').append(state.fogTapsRemaining).append('|').append(state.delayedIntents)
        }
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
