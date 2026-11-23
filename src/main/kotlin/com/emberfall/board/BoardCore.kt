package com.emberfall.board

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Collections

const val BOARD_WIDTH = 7
const val BOARD_HEIGHT = 7
const val RULES_VERSION = "emberfall-v0.2-board-1"

enum class Hue { EMBER, GOLD, MOSS, TIDE, VIOLET }
enum class TileKind { HUE, ASH, STONE, PRISM }

data class Cell(val kind: TileKind, val hue: Hue? = null, val branded: Boolean = false) {
    init { require((kind == TileKind.HUE) == (hue != null)) }
    companion object {
        fun hue(h: Hue, branded: Boolean = false) = Cell(TileKind.HUE, h, branded)
        fun ash(branded: Boolean = false) = Cell(TileKind.ASH, branded = branded)
        fun stone() = Cell(TileKind.STONE)
        fun prism(branded: Boolean = false) = Cell(TileKind.PRISM, branded = branded)
    }
}

data class Coord(val x: Int, val y: Int) {
    init { require(x in 0..6 && y in 0..6) }
    fun index() = y * 7 + x
    fun neighbors() = buildList {
        if (y > 0) add(Coord(x, y - 1))
        if (x > 0) add(Coord(x - 1, y))
        if (x < 6) add(Coord(x + 1, y))
        if (y < 6) add(Coord(x, y + 1))
    }
}

class BoardState(
    cells: List<Cell?>,
    incoming: List<Cell?>,
    sealedColumns: Set<Int> = emptySet(),
    val rulesVersion: String = RULES_VERSION,
    sealTurnsRemaining: Map<Int, Int> = emptyMap()
) {
    // Snapshot and wrap every collection: callers cannot mutate a committed
    // board by retaining or casting a mutable constructor argument.
    val cells: List<Cell?> = Collections.unmodifiableList(cells.toList())
    val incoming: List<Cell?> = Collections.unmodifiableList(incoming.toList())
    val sealedColumns: Set<Int> = Collections.unmodifiableSet(sealedColumns.toSet())
    val sealTurnsRemaining: Map<Int, Int> = Collections.unmodifiableMap(sealTurnsRemaining.toMap())

    init {
        require(this.cells.size == 49 && this.incoming.size == 7)
        require(this.sealedColumns.all { it in 0..6 })
        require(this.sealTurnsRemaining.keys == this.sealedColumns) {
            "Every sealed column must have exactly one positive expiry timer"
        }
        require(this.sealTurnsRemaining.values.all { it > 0 })
        require(rulesVersion.isNotBlank())
    }
    fun copy(
        cells: List<Cell?> = this.cells,
        incoming: List<Cell?> = this.incoming,
        sealedColumns: Set<Int> = this.sealedColumns,
        rulesVersion: String = this.rulesVersion,
        sealTurnsRemaining: Map<Int, Int> = this.sealTurnsRemaining
    ) = BoardState(cells, incoming, sealedColumns, rulesVersion, sealTurnsRemaining)

    operator fun get(c: Coord) = cells[c.index()]
    fun sealColumn(column: Int, turns: Int = 3): BoardState {
        require(column in 0..6 && turns > 0)
        return copy(sealedColumns = sealedColumns + column, sealTurnsRemaining = sealTurnsRemaining + (column to turns))
    }
    /** Call once after each enemy phase; columns with no remaining turns are released. */
    fun advanceSealDurations(): BoardState {
        val remaining = sealTurnsRemaining.mapValues { (_, turns) -> turns - 1 }.filterValues { it > 0 }
        val expired = sealTurnsRemaining.keys - remaining.keys
        return copy(sealedColumns = sealedColumns - expired, sealTurnsRemaining = remaining)
    }
    fun withCell(c: Coord, value: Cell?) = copy(cells = cells.toMutableList().also { it[c.index()] = value })
    override fun equals(other: Any?): Boolean = other is BoardState &&
        cells == other.cells && incoming == other.incoming && sealedColumns == other.sealedColumns &&
        rulesVersion == other.rulesVersion && sealTurnsRemaining == other.sealTurnsRemaining
    override fun hashCode(): Int {
        var result = cells.hashCode()
        result = 31 * result + incoming.hashCode()
        result = 31 * result + sealedColumns.hashCode()
        result = 31 * result + rulesVersion.hashCode()
        result = 31 * result + sealTurnsRemaining.hashCode()
        return result
    }
    override fun toString(): String =
        "BoardState(cells=$cells, incoming=$incoming, sealedColumns=$sealedColumns, rulesVersion=$rulesVersion, sealTurnsRemaining=$sealTurnsRemaining)"

    companion object {
        fun empty() = BoardState(List(49) { null }, List(7) { null })
        fun fromRows(rows: List<List<Cell?>>, incoming: List<Cell?> = List(7) { null }, sealedColumns: Set<Int> = emptySet()): BoardState {
            require(rows.size == 7 && rows.all { it.size == 7 })
            return BoardState(rows.flatten(), incoming, sealedColumns)
        }
    }
}

data class SpectrumState(val run: Int = 0, val lastHue: Hue? = null) {
    init { require(run >= 0) }
}
enum class PopRejection { EMPTY, STONE, NO_LEGAL_GROUP }
sealed interface TapOutcome {
    data class Accepted(val result: PopResult) : TapOutcome
    data class Rejected(val reason: PopRejection) : TapOutcome
}
data class PopResult(
    val board: BoardState, val orderedCells: List<Coord>, val resolvedHue: Hue?, val isAsh: Boolean,
    val groupSize: Int, val baseInk: Int, val inkPayout: Int, val chipDamage: Int,
    val brandVigilCost: Int, val brokenStoneCells: List<Coord>, val spectrum: SpectrumState
)
data class WallOutcome(val board: BoardState, val vigilDamage: Int, val flasksConsumed: Int,
                       val overflow: Boolean, val incomingRetained: Boolean)

class BoardEngine {
    private val wheel = listOf(Hue.EMBER, Hue.GOLD, Hue.MOSS, Hue.TIDE, Hue.VIOLET)

    fun tap(board: BoardState, target: Coord, spectrum: SpectrumState = SpectrumState()): TapOutcome {
        val selected = board[target] ?: return TapOutcome.Rejected(PopRejection.EMPTY)
        if (target.x in board.sealedColumns) return TapOutcome.Rejected(PopRejection.NO_LEGAL_GROUP)
        if (selected.kind == TileKind.STONE) return TapOutcome.Rejected(PopRejection.STONE)
        val resolved = if (selected.kind == TileKind.PRISM) directPrism(board, target, spectrum)
                       else normalGroup(board, target, selected)
        if (resolved == null || resolved.second.size < 2) return TapOutcome.Rejected(PopRejection.NO_LEGAL_GROUP)
        return TapOutcome.Accepted(pop(board, resolved.second, resolved.first, spectrum))
    }

    private fun normalGroup(board: BoardState, start: Coord, cell: Cell): Pair<Hue?, List<Coord>>? {
        val accepts: (Cell) -> Boolean = when (cell.kind) {
            TileKind.ASH -> { c -> c.kind == TileKind.ASH || c.kind == TileKind.PRISM }
            TileKind.HUE -> { c -> c.kind == TileKind.PRISM || (c.kind == TileKind.HUE && c.hue == cell.hue) }
            else -> return null
        }
        return (if (cell.kind == TileKind.ASH) null else cell.hue) to flood(board, start, accepts)
    }

    private fun directPrism(board: BoardState, prism: Coord, spectrum: SpectrumState): Pair<Hue?, List<Coord>>? {
        val candidates = prism.neighbors().filter { it.x !in board.sealedColumns }.mapNotNull { n ->
            val c = board[n] ?: return@mapNotNull null
            if (c.kind == TileKind.HUE || c.kind == TileKind.ASH) normalGroup(board, n, c) else null
        }.distinctBy { it.second.toSet() }
        if (candidates.isEmpty()) return null
        val next = if (spectrum.lastHue == null) wheel.first() else wheel[(wheel.indexOf(spectrum.lastHue) + 1) % 5]
        return candidates.sortedWith(
            compareByDescending<Pair<Hue?, List<Coord>>> { it.second.size }
                .thenBy { if (it.first == next) 0 else 1 }
                .thenBy { wheel.indexOf(it.first).let { i -> if (i < 0) Int.MAX_VALUE else i } }
                .thenBy { it.second.minOf { c -> c.index() } }
        ).first()
    }

    private fun flood(board: BoardState, start: Coord, accepts: (Cell) -> Boolean): List<Coord> {
        val first = board[start] ?: return emptyList()
        if (!accepts(first) || start.x in board.sealedColumns) return emptyList()
        val seen = linkedSetOf<Coord>()
        val queue = ArrayDeque<Coord>()
        queue.add(start)
        while (queue.isNotEmpty()) {
            val at = queue.removeFirst()
            if (!seen.add(at)) continue
            for (n in at.neighbors()) {
                if (n.x in board.sealedColumns || n in seen) continue
                val c = board[n] ?: continue
                if (accepts(c)) queue.add(n)
            }
        }
        return seen.sortedBy { it.index() }
    }

    private fun pop(board: BoardState, group: List<Coord>, hue: Hue?, spectrum: SpectrumState): PopResult {
        val groupSet = group.toSet()
        val cells = group.mapNotNull { board[it] }
        val ash = hue == null
        val stones = if (group.size >= 4) board.cells.indices.mapNotNull { i ->
            val p = Coord(i % 7, i / 7)
            if (board[p]?.kind == TileKind.STONE && p.x !in board.sealedColumns && p.neighbors().any { it in groupSet }) p else null
        }.sortedBy { it.index() } else emptyList()
        val next = board.cells.toMutableList()
        (group + stones).forEach { next[it.index()] = null }
        val settled = gravity(board.copy(cells = next))
        val containsPrism = group.any { board[it]?.kind == TileKind.PRISM }
        val runState = advance(spectrum, hue, containsPrism)
        val base = if (ash) 0 else minOf(group.size, 8) + maxOf(group.size - 8, 0) / 2
        val payout = if (base == 0) 0 else (base * (2 + runState.run) + 1) / 2
        return PopResult(settled, group, hue, ash, group.size, base, payout, group.size - 1,
            cells.count { it.branded } * 2, stones, runState)
    }

    fun resetSpectrumAtTurnBoundary(): SpectrumState = SpectrumState()

    fun gravity(board: BoardState): BoardState {
        val next = board.cells.toMutableList()
        for (x in 0..6) {
            if (x in board.sealedColumns) continue
            val survivors = (6 downTo 0).mapNotNull { y -> board[Coord(x, y)] }
            for (y in 0..6) next[y * 7 + x] = null
            survivors.forEachIndexed { i, cell -> next[(6 - i) * 7 + x] = cell }
        }
        return board.copy(cells = next)
    }

    fun riseWall(board: BoardState, nextIncoming: List<Cell?>, flasks: Int): WallOutcome {
        require(nextIncoming.size == 7 && flasks >= 0)
        val overflow = (0..6).any { x -> x !in board.sealedColumns && board[Coord(x, 0)] != null }
        val next = board.cells.toMutableList()
        if (overflow) {
            for (x in 0..6) if (x !in board.sealedColumns) {
                next[x] = null
                next[7 + x] = null
            }
            return WallOutcome(board.copy(cells = next), 8, if (flasks > 0) 1 else 0, true, true)
        }
        for (x in 0..6) if (x !in board.sealedColumns) {
            for (y in 0..5) next[y * 7 + x] = board[Coord(x, y + 1)]
            next[42 + x] = board.incoming[x]
        }
        return WallOutcome(board.copy(cells = next, incoming = nextIncoming), 0, 0, false, false)
    }

    /**
     * A popped Prism substitutes for exactly one step on the Spectrum wheel.
     * The resolved base hue remains available for Ink attribution, while the
     * Spectrum anchor advances to the expected wheel hue. An Ash+Prism pop
     * still pays zero Ink but can consume that one Spectrum step.
     */
    private fun advance(s: SpectrumState, hue: Hue?, containsPrism: Boolean): SpectrumState {
        if (s.lastHue == null) return if (hue == null) s else SpectrumState(0, hue)
        val expected = wheel[(wheel.indexOf(s.lastHue) + 1) % 5]
        if (containsPrism) return SpectrumState(s.run + 1, expected)
        if (hue == null) return s
        return if (hue == expected) SpectrumState(s.run + 1, hue) else SpectrumState(0, hue)
    }
}

class DeckHueProfile(val deckSize: Int, cardCounts: Map<Hue, Int>) {
    val cardCounts: Map<Hue, Int> = Collections.unmodifiableMap(cardCounts.toMap())
    init {
        require(deckSize >= 0 && this.cardCounts.values.all { it >= 0 })
        require(this.cardCounts.values.sumOf { it.toLong() } <= deckSize.toLong())
    }
    fun weights(): Map<Hue, Long> {
        val raw = Hue.entries.associateWith { h -> maxOf(80_000L, ((cardCounts[h] ?: 0).toLong() + 1) * 1_000_000L / (deckSize + 5L)) }
        val total = raw.values.sum()
        val first = Hue.entries.dropLast(1).associateWith { h -> raw.getValue(h) * 1_000_000L / total }
        return first + (Hue.entries.last() to (1_000_000L - first.values.sum()))
    }
}

class DeterministicRng(seed: Long) {
    private val initialSeed = seed
    private var state = seed
    fun nextLong(): Long {
        state += -7046029254386353131L
        var z = state
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        return z xor (z ushr 31)
    }
    fun nextInt(bound: Int): Int {
        require(bound > 0)
        val b = bound.toLong()
        while (true) {
            val v = nextLong().ushr(1)
            val m = v % b
            if (v - m + (b - 1) >= 0L) return m.toInt()
        }
    }
    fun fork(stream: Long): DeterministicRng {
        var z = initialSeed xor stream
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        return DeterministicRng(z xor (z ushr 31))
    }
}

data class GenerationTelemetry(val attempts: Int, val fallbackUsed: Boolean, val fallbackCount: Int)
data class GeneratedBoard(val board: BoardState, val telemetry: GenerationTelemetry)

class BoardGenerator {
    fun generate(seed: Long, deck: DeckHueProfile): GeneratedBoard {
        val rng = DeterministicRng(seed)
        val weights = deck.weights()
        var last: BoardState? = null
        repeat(32) { attempt ->
            last = sample(rng, weights)
            if (hasPair(last!!)) return GeneratedBoard(last!!, GenerationTelemetry(attempt + 1, false, 0))
        }
        val b = last!!
        val hue = sampleHue(rng, weights)
        val y = 2 + rng.nextInt(5)
        val x = rng.nextInt(6)
        return GeneratedBoard(b.withCell(Coord(x, y), Cell.hue(hue)).withCell(Coord(x + 1, y), Cell.hue(hue)),
            GenerationTelemetry(32, true, 1))
    }
    private fun sample(rng: DeterministicRng, weights: Map<Hue, Long>): BoardState {
        val cells = MutableList<Cell?>(49) { null }
        for (y in 2..6) for (x in 0..6) cells[y * 7 + x] = Cell.hue(sampleHue(rng, weights))
        return BoardState(cells, List(7) { Cell.hue(sampleHue(rng, weights)) })
    }
    private fun sampleHue(rng: DeterministicRng, weights: Map<Hue, Long>): Hue {
        val roll = rng.nextInt(1_000_000)
        var sum = 0L
        for (h in Hue.entries) { sum += weights.getValue(h); if (roll < sum) return h }
        return Hue.VIOLET
    }
    private fun hasPair(b: BoardState) = b.cells.indices.any { i ->
        val c = b.cells[i] ?: return@any false
        c.kind == TileKind.HUE && Coord(i % 7, i / 7).neighbors().any { n ->
            val other = b[n]
            other?.kind == TileKind.HUE && other.hue == c.hue
        }
    }
}

data class DeterministicStreams(val board: DeterministicRng, val enemy: DeterministicRng, val cards: DeterministicRng) {
    companion object {
        fun fromSeed(seed: Long): DeterministicStreams {
            val root = DeterministicRng(seed)
            return DeterministicStreams(root.fork(0x424F415244L), root.fork(0x454E454D59L), root.fork(0x4341524453L))
        }
    }
}

sealed interface BoardCommand {
    data class Tap(val coord: Coord) : BoardCommand
    class WallRise(nextIncoming: List<Cell?>, val flasks: Int) : BoardCommand {
        val nextIncoming: List<Cell?> = Collections.unmodifiableList(nextIncoming.toList())
        override fun equals(other: Any?): Boolean =
            other is WallRise && nextIncoming == other.nextIncoming && flasks == other.flasks
        override fun hashCode(): Int = 31 * nextIncoming.hashCode() + flasks
        override fun toString(): String = "WallRise(nextIncoming=$nextIncoming, flasks=$flasks)"
    }
    data object TurnBoundary : BoardCommand
    data object EnemyPhaseEnd : BoardCommand
}
data class ReplayStep(
    val board: BoardState,
    val spectrum: SpectrumState,
    val lastPop: PopResult? = null,
    val lastWall: WallOutcome? = null,
    val rejection: PopRejection? = null
)
class BoardReplay(private val engine: BoardEngine = BoardEngine()) {
    fun replay(initial: BoardState, commands: List<BoardCommand>): List<ReplayStep> {
        var state = ReplayStep(initial, SpectrumState())
        val out = mutableListOf(state)
        for (command in commands) {
            state = when (command) {
                is BoardCommand.Tap -> when (val r = engine.tap(state.board, command.coord, state.spectrum)) {
                    is TapOutcome.Accepted -> ReplayStep(r.result.board, r.result.spectrum, lastPop = r.result)
                    is TapOutcome.Rejected -> ReplayStep(state.board, state.spectrum, rejection = r.reason)
                }
                is BoardCommand.WallRise -> engine.riseWall(state.board, command.nextIncoming, command.flasks)
                    .let { ReplayStep(it.board, state.spectrum, lastWall = it) }
                BoardCommand.TurnBoundary -> ReplayStep(state.board, engine.resetSpectrumAtTurnBoundary())
                BoardCommand.EnemyPhaseEnd -> ReplayStep(state.board.advanceSealDurations(), state.spectrum)
            }
            out += state
        }
        return out
    }
}
object BoardStateDigest {
    fun sha256(board: BoardState, spectrum: SpectrumState): String {
        val canonical = buildString {
            append(board.rulesVersion).append('|').append(spectrum.run).append('|').append(spectrum.lastHue?.name ?: "-").append('|')
            append(board.sealedColumns.sorted().joinToString(",")).append('|')
            append(board.sealTurnsRemaining.toSortedMap().entries.joinToString(",") { "${it.key}:${it.value}" }).append('|')
            for (c in board.cells + board.incoming) {
                append(when (c?.kind) { null -> "_"; TileKind.ASH -> "A"; TileKind.STONE -> "S"; TileKind.PRISM -> "P"; TileKind.HUE -> "H${c.hue!!.ordinal}" })
                append(if (c?.branded == true) "B" else "-").append(';')
            }
        }
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
