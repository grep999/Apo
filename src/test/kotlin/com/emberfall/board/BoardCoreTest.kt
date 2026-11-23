package com.emberfall.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoardCoreTest {
    private val engine = BoardEngine()
    private fun board(vararg entries: Pair<Coord, Cell>): BoardState {
        var b = BoardState.empty()
        entries.forEach { b = b.withCell(it.first, it.second) }
        return b
    }

    @Test fun wallRiseCommandDefensivelyCopiesIncomingRow() {
        val incoming = MutableList<Cell?>(7) { null }
        incoming[0] = Cell.ash()
        val command = BoardCommand.WallRise(incoming, 1)
        incoming[0] = Cell.hue(Hue.EMBER)

        assertEquals(Cell.ash(), command.nextIncoming[0])
        assertFailsWith<UnsupportedOperationException> {
            (command.nextIncoming as MutableList<Cell?>)[0] = null
        }
    }

    @Test fun boardStateAndDeckProfileDefensivelyCopyMutableInputs() {
        val cells = MutableList<Cell?>(49) { null }
        cells[0] = Cell.hue(Hue.EMBER)
        val incoming = MutableList<Cell?>(7) { null }
        incoming[0] = Cell.ash()
        val sealed = mutableSetOf(2)
        val durations = mutableMapOf(2 to 3)
        val state = BoardState(cells, incoming, sealed, sealTurnsRemaining = durations)

        cells[0] = null
        incoming[0] = null
        sealed.clear()
        durations[2] = 1

        assertEquals(Cell.hue(Hue.EMBER), state[Coord(0,0)])
        assertEquals(Cell.ash(), state.incoming[0])
        assertEquals(setOf(2), state.sealedColumns)
        assertEquals(mapOf(2 to 3), state.sealTurnsRemaining)
        assertFailsWith<UnsupportedOperationException> { (state.cells as MutableList<Cell?>)[0] = null }
        assertFailsWith<UnsupportedOperationException> { (state.sealedColumns as MutableSet<Int>).clear() }
        assertFailsWith<UnsupportedOperationException> { (state.sealTurnsRemaining as MutableMap<Int, Int>)[2] = 1 }

        val counts = mutableMapOf(Hue.EMBER to 1)
        val profile = DeckHueProfile(1, counts)
        val before = profile.weights()
        counts.clear()
        assertEquals(before, profile.weights())
    }

    @Test fun emptyBoardDigestHasStableCanonicalGoldenVector() {
        assertEquals(
            "e014ff03c0bcd7bd964644178722d674c52fb933f230d982a2906792d5ce36a6",
            BoardStateDigest.sha256(BoardState.empty(), SpectrumState())
        )
    }

    @Test fun wallRiseIgnoresOccupiedSealedTopCellAndMovesOnlyUnsealedColumns() {
        val incoming = List<Cell?>(7) { if (it == 0) Cell.ash() else Cell.hue(Hue.VIOLET) }
        val b = board(
            Coord(0,0) to Cell.hue(Hue.EMBER),
            Coord(0,1) to Cell.hue(Hue.GOLD),
            Coord(1,1) to Cell.hue(Hue.TIDE)
        ).sealColumn(0).copy(incoming = incoming)
        val nextIncoming = List(7) { Cell.hue(Hue.MOSS) }
        val rise = engine.riseWall(b, nextIncoming, 0)

        assertFalse(rise.overflow)
        assertEquals(Cell.hue(Hue.EMBER), rise.board[Coord(0,0)])
        assertEquals(Cell.hue(Hue.GOLD), rise.board[Coord(0,1)])
        assertEquals(Cell.hue(Hue.TIDE), rise.board[Coord(1,0)])
        assertEquals(Cell.hue(Hue.VIOLET), rise.board[Coord(1,6)])
        assertEquals(nextIncoming, rise.board.incoming)
    }

    @Test fun orthogonalGroupPopsButDiagonalDoesNotConnect() {
        val b = board(Coord(0,0) to Cell.hue(Hue.EMBER), Coord(1,0) to Cell.hue(Hue.EMBER),
            Coord(2,1) to Cell.hue(Hue.EMBER))
        val result = assertIs<TapOutcome.Accepted>(engine.tap(b, Coord(0,0))).result
        assertEquals(2, result.groupSize)
        assertEquals(1, result.chipDamage)
        assertEquals(2, result.inkPayout)
        assertEquals(Cell.hue(Hue.EMBER), result.board[Coord(2,6)])
    }

    @Test fun ashPaysNoInkAndDealsNMinusOneChip() {
        val b = board(Coord(0,0) to Cell.ash(), Coord(1,0) to Cell.ash())
        val result = assertIs<TapOutcome.Accepted>(engine.tap(b, Coord(0,0))).result
        assertTrue(result.isAsh)
        assertEquals(0, result.inkPayout)
        assertEquals(1, result.chipDamage)
    }

    @Test fun prismDoesNotBridgeDifferentBaseHues() {
        val b = board(Coord(0,0) to Cell.hue(Hue.EMBER), Coord(1,0) to Cell.prism(),
            Coord(2,0) to Cell.hue(Hue.TIDE), Coord(0,1) to Cell.hue(Hue.EMBER))
        val result = assertIs<TapOutcome.Accepted>(engine.tap(b, Coord(1,0))).result
        assertEquals(Hue.EMBER, result.resolvedHue)
        assertEquals(3, result.groupSize)
        assertEquals(Cell.hue(Hue.TIDE), result.board[Coord(2,6)])
    }

    @Test fun stoneBreakUsesOnePreRemovalSnapshot() {
        val b = board(Coord(0,0) to Cell.hue(Hue.GOLD), Coord(1,0) to Cell.hue(Hue.GOLD),
            Coord(0,1) to Cell.hue(Hue.GOLD), Coord(1,1) to Cell.hue(Hue.GOLD),
            Coord(2,1) to Cell.stone(), Coord(3,1) to Cell.stone())
        val result = assertIs<TapOutcome.Accepted>(engine.tap(b, Coord(0,0))).result
        assertEquals(listOf(Coord(2,1)), result.brokenStoneCells)
    }

    @Test fun brandCostIsTwoVigilPerPoppedBrandedCell() {
        val b = board(Coord(0,0) to Cell.hue(Hue.TIDE, true), Coord(1,0) to Cell.hue(Hue.TIDE, true),
            Coord(2,0) to Cell.hue(Hue.TIDE))
        val result = assertIs<TapOutcome.Accepted>(engine.tap(b, Coord(0,0))).result
        assertEquals(4, result.brandVigilCost)
    }

    @Test fun gravityPreservesRelativeOrderAndNeverSlidesHorizontally() {
        val b = board(Coord(0,0) to Cell.hue(Hue.EMBER), Coord(0,2) to Cell.hue(Hue.TIDE),
            Coord(0,4) to Cell.hue(Hue.MOSS), Coord(2,0) to Cell.hue(Hue.GOLD))
        val g = engine.gravity(b)
        assertEquals(Cell.hue(Hue.EMBER), g[Coord(0,4)])
        assertEquals(Cell.hue(Hue.TIDE), g[Coord(0,5)])
        assertEquals(Cell.hue(Hue.MOSS), g[Coord(0,6)])
        assertEquals(Cell.hue(Hue.GOLD), g[Coord(2,6)])
    }

    @Test fun sealedColumnIsFrozenDuringGravityAndWallRise() {
        val b = board(Coord(0,0) to Cell.hue(Hue.EMBER), Coord(1,1) to Cell.hue(Hue.TIDE))
            .sealColumn(0).copy(incoming = List(7) { Cell.hue(Hue.MOSS) })
        val g = engine.gravity(b)
        assertEquals(Cell.hue(Hue.EMBER), g[Coord(0,0)])
        val rise = engine.riseWall(g, List(7) { Cell.hue(Hue.GOLD) }, 2)
        assertEquals(Cell.hue(Hue.EMBER), rise.board[Coord(0,0)])
        assertEquals(Cell.hue(Hue.MOSS), rise.board[Coord(1,6)])
    }

    @Test fun overflowBurnsTopTwoRowsConsumesOneFlaskAndRetainsPreview() {
        val incoming = List<Cell?>(7) { Cell.ash() }
        val b = board(Coord(0,0) to Cell.hue(Hue.EMBER), Coord(0,1) to Cell.hue(Hue.TIDE)).copy(incoming = incoming)
        val rise = engine.riseWall(b, List(7) { Cell.hue(Hue.GOLD) }, 2)
        assertTrue(rise.overflow)
        assertEquals(8, rise.vigilDamage)
        assertEquals(1, rise.flasksConsumed)
        assertEquals(incoming, rise.board.incoming)
        assertNull(rise.board[Coord(0,0)])
        assertNull(rise.board[Coord(0,1)])
    }

    @Test fun spectrumUsesWheelAndRoundHalfUpIntegerArithmetic() {
        val first = board(Coord(0,0) to Cell.hue(Hue.EMBER), Coord(1,0) to Cell.hue(Hue.EMBER))
        val a = assertIs<TapOutcome.Accepted>(engine.tap(first, Coord(0,0))).result
        assertEquals(0, a.spectrum.run)
        val next = board(Coord(0,0) to Cell.hue(Hue.GOLD), Coord(1,0) to Cell.hue(Hue.GOLD))
        val b = assertIs<TapOutcome.Accepted>(engine.tap(next, Coord(0,0), a.spectrum)).result
        assertEquals(1, b.spectrum.run)
        assertEquals(3, b.inkPayout)
    }

    @Test fun prismCountsAsExactlyOneNextSpectrumHue() {
        val anchor = board(Coord(0,0) to Cell.hue(Hue.EMBER), Coord(1,0) to Cell.hue(Hue.EMBER))
        val first = assertIs<TapOutcome.Accepted>(engine.tap(anchor, Coord(0,0))).result
        assertEquals(Hue.EMBER, first.spectrum.lastHue)

        // Directly tapping the Prism selects a Tide group, but the Prism
        // substitutes for the next wheel hue (Gold) exactly once.
        val prismGroup = board(
            Coord(2,3) to Cell.hue(Hue.TIDE),
            Coord(3,3) to Cell.prism(),
            Coord(4,3) to Cell.prism()
        )
        val result = assertIs<TapOutcome.Accepted>(
            engine.tap(prismGroup, Coord(3,3), first.spectrum)
        ).result
        assertEquals(Hue.TIDE, result.resolvedHue)
        assertEquals(3, result.groupSize)
        assertEquals(1, result.spectrum.run)
        assertEquals(Hue.GOLD, result.spectrum.lastHue)
        assertEquals(5, result.inkPayout)
    }

    @Test fun ashPrismPopAdvancesSpectrumOnceWithoutPayingInk() {
        val ashPrismGroup = board(
            Coord(2,3) to Cell.ash(),
            Coord(3,3) to Cell.prism(),
            Coord(4,3) to Cell.prism()
        )
        val result = assertIs<TapOutcome.Accepted>(
            engine.tap(ashPrismGroup, Coord(3,3), SpectrumState(0, Hue.EMBER))
        ).result
        assertTrue(result.isAsh)
        assertEquals(0, result.inkPayout)
        assertEquals(1, result.spectrum.run)
        assertEquals(Hue.GOLD, result.spectrum.lastHue)
    }

    @Test fun rejectedTapIsTransactionalAndReplayDigestStable() {
        val b = board(Coord(0,0) to Cell.hue(Hue.EMBER))
        val commands = listOf(BoardCommand.Tap(Coord(0,0)))
        val one = BoardReplay(engine).replay(b, commands)
        val two = BoardReplay(engine).replay(b, commands)
        assertEquals(one, two)
        assertEquals(BoardStateDigest.sha256(one.last().board, one.last().spectrum),
            BoardStateDigest.sha256(two.last().board, two.last().spectrum))
        assertEquals(b, one.last().board)
        assertEquals(PopRejection.NO_LEGAL_GROUP, one.last().rejection)
        assertNotEquals(BoardStateDigest.sha256(b, SpectrumState()), "not-a-digest")
    }

    @Test fun replayRetainsPopAndWallResourceConsequences() {
        val popBoard = board(Coord(0,6) to Cell.hue(Hue.EMBER), Coord(1,6) to Cell.hue(Hue.EMBER))
        val popReplay = BoardReplay(engine).replay(popBoard, listOf(BoardCommand.Tap(Coord(0,6))))
        assertEquals(2, popReplay.last().lastPop?.groupSize)
        assertEquals(1, popReplay.last().lastPop?.chipDamage)
        assertEquals(2, popReplay.last().lastPop?.inkPayout)

        val incoming = List<Cell?>(7) { Cell.ash() }
        val wallBoard = board(Coord(0,0) to Cell.hue(Hue.EMBER)).copy(incoming = incoming)
        val wallReplay = BoardReplay(engine).replay(
            wallBoard,
            listOf(BoardCommand.WallRise(List(7) { Cell.hue(Hue.GOLD) }, 1))
        )
        assertEquals(8, wallReplay.last().lastWall?.vigilDamage)
        assertEquals(1, wallReplay.last().lastWall?.flasksConsumed)
        assertTrue(wallReplay.last().lastWall?.overflow == true)
        assertEquals(incoming, wallReplay.last().board.incoming)
    }

    @Test fun replayAppliesSpectrumTurnBoundaryAndSealEnemyPhaseCountdown() {
        val b = board(
            Coord(0,6) to Cell.hue(Hue.EMBER), Coord(1,6) to Cell.hue(Hue.EMBER),
            Coord(3,6) to Cell.hue(Hue.GOLD), Coord(4,6) to Cell.hue(Hue.GOLD)
        ).sealColumn(6)
        val history = BoardReplay(engine).replay(
            b,
            listOf(
                BoardCommand.Tap(Coord(0,6)),
                BoardCommand.Tap(Coord(3,6)),
                BoardCommand.TurnBoundary,
                BoardCommand.EnemyPhaseEnd,
                BoardCommand.EnemyPhaseEnd,
                BoardCommand.EnemyPhaseEnd
            )
        )
        assertEquals(1, history[2].spectrum.run)
        assertEquals(SpectrumState(), history[3].spectrum)
        assertEquals(2, history[4].board.sealTurnsRemaining[6])
        assertEquals(1, history[5].board.sealTurnsRemaining[6])
        assertTrue(6 !in history[6].board.sealedColumns)
    }

    @Test fun generationIsDeterministicAndMeetsOpeningTopology() {
        val deck = DeckHueProfile(12, mapOf(Hue.EMBER to 5, Hue.TIDE to 4, Hue.MOSS to 3))
        val generator = BoardGenerator()
        val a = generator.generate(42L, deck)
        val b = generator.generate(42L, deck)
        assertEquals(a, b)
        assertEquals(35, a.board.cells.count { it != null })
        assertTrue((0..1).all { y -> (0..6).all { x -> a.board[Coord(x,y)] == null } })
        assertTrue(hasLegalPair(a.board))
    }

    @Test fun hueWeightsNormalizeAndRespectFloor() {
        val weights = DeckHueProfile(12, mapOf(Hue.EMBER to 5, Hue.TIDE to 4, Hue.MOSS to 3)).weights()
        assertEquals(1_000_000L, weights.values.sum())
        assertTrue(weights.values.all { it > 0L })
    }

    @Test fun directPrismTiePrefersNextSpectrumHue() {
        val b = board(Coord(3,3) to Cell.prism(),
            Coord(2,3) to Cell.hue(Hue.GOLD), Coord(1,3) to Cell.hue(Hue.GOLD),
            Coord(4,3) to Cell.hue(Hue.EMBER), Coord(5,3) to Cell.hue(Hue.EMBER))
        val result = assertIs<TapOutcome.Accepted>(
            engine.tap(b, Coord(3,3), SpectrumState(0, Hue.EMBER))
        ).result
        assertEquals(Hue.GOLD, result.resolvedHue)
        assertEquals(3, result.groupSize)
    }

    @Test fun prismMayJoinAshButAshStillPaysNoInk() {
        val b = board(Coord(2,3) to Cell.ash(), Coord(1,3) to Cell.ash(), Coord(3,3) to Cell.prism())
        val result = assertIs<TapOutcome.Accepted>(engine.tap(b, Coord(3,3))).result
        assertTrue(result.isAsh)
        assertEquals(3, result.groupSize)
        assertEquals(0, result.inkPayout)
    }

    @Test fun prismOnlyClusterIsNotLegal() {
        val b = board(Coord(3,3) to Cell.prism(), Coord(2,3) to Cell.prism())
        assertEquals(TapOutcome.Rejected(PopRejection.NO_LEGAL_GROUP), engine.tap(b, Coord(3,3)))
    }

    @Test fun largeGroupInkUsesHalfRateAboveEight() {
        val entries = ((0..6).map { Coord(it, 0) to Cell.hue(Hue.VIOLET) } + (0..1).map { Coord(it, 1) to Cell.hue(Hue.VIOLET) }).toTypedArray()
        val nine = assertIs<TapOutcome.Accepted>(engine.tap(board(*entries), Coord(0,0))).result
        assertEquals(8, nine.baseInk)
        assertEquals(8, nine.inkPayout)
        val tenEntries = (0..6).map { Coord(it, 0) to Cell.hue(Hue.VIOLET) } +
            (0..2).map { Coord(it, 1) to Cell.hue(Hue.VIOLET) }
        val ten = assertIs<TapOutcome.Accepted>(engine.tap(board(*tenEntries.toTypedArray()), Coord(0,0))).result
        assertEquals(9, ten.baseInk)
    }

    @Test fun spectrumOutOfOrderPopResetsAndBecomesNewAnchor() {
        val b = board(Coord(0,0) to Cell.hue(Hue.EMBER), Coord(1,0) to Cell.hue(Hue.EMBER))
        val result = assertIs<TapOutcome.Accepted>(
            engine.tap(b, Coord(0,0), SpectrumState(2, Hue.GOLD))
        ).result
        assertEquals(0, result.spectrum.run)
        assertEquals(Hue.EMBER, result.spectrum.lastHue)
    }

    @Test fun overflowWithoutFlaskStillDealsEightAndPreservesPreview() {
        val incoming = List<Cell?>(7) { Cell.prism() }
        val b = board(Coord(6,0) to Cell.hue(Hue.EMBER)).copy(incoming = incoming)
        val result = engine.riseWall(b, List(7) { Cell.ash() }, 0)
        assertEquals(8, result.vigilDamage)
        assertEquals(0, result.flasksConsumed)
        assertEquals(incoming, result.board.incoming)
    }

    @Test fun sealedColumnsRequireMatchingExpiryTimers() {
        assertFailsWith<IllegalArgumentException> {
            BoardState(List(49) { null }, List(7) { null }, sealedColumns = setOf(2))
        }
        assertFailsWith<IllegalArgumentException> {
            BoardState(
                List(49) { null }, List(7) { null },
                sealedColumns = setOf(2), sealTurnsRemaining = mapOf(3 to 2)
            )
        }
        val sealed = BoardState.empty().sealColumn(2, turns = 2)
        assertEquals(setOf(2), sealed.sealedColumns)
        assertEquals(mapOf(2 to 2), sealed.sealTurnsRemaining)
    }

    @Test fun sealExpiresAfterExactlyThreeEnemyPhases() {
        var b = BoardState.empty().sealColumn(4)
        assertTrue(4 in b.sealedColumns)
        assertEquals(3, b.sealTurnsRemaining[4])
        b = b.advanceSealDurations()
        assertTrue(4 in b.sealedColumns)
        assertEquals(2, b.sealTurnsRemaining[4])
        b = b.advanceSealDurations()
        assertTrue(4 in b.sealedColumns)
        assertEquals(1, b.sealTurnsRemaining[4])
        b = b.advanceSealDurations()
        assertTrue(4 !in b.sealedColumns)
        assertTrue(4 !in b.sealTurnsRemaining)
    }

    @Test fun randomStreamsAreIndependentOfConsumptionOrder() {
        val interleaved = DeterministicStreams.fromSeed(991L)
        val control = DeterministicStreams.fromSeed(991L)
        repeat(1_000) { interleaved.board.nextLong() }
        repeat(257) { interleaved.cards.nextLong() }
        repeat(1_000) { control.board.nextLong() }
        repeat(257) { control.cards.nextLong() }
        assertEquals(List(32) { control.enemy.nextLong() }, List(32) { interleaved.enemy.nextLong() })
        assertEquals(List(32) { control.board.nextLong() }, List(32) { interleaved.board.nextLong() })
        assertEquals(List(32) { control.cards.nextLong() }, List(32) { interleaved.cards.nextLong() })
    }

    @Test fun oneHundredThousandSeedDistributionAndFallbackGate() {
        val deck = DeckHueProfile(12, mapOf(Hue.EMBER to 5, Hue.TIDE to 4, Hue.MOSS to 3))
        val expected = deck.weights()
        val counts = Hue.entries.associateWith { 0L }.toMutableMap()
        var fallbacks = 0
        val generator = BoardGenerator()
        repeat(100_000) { seed ->
            val generated = generator.generate(seed.toLong(), deck)
            if (generated.telemetry.fallbackUsed) fallbacks++
            assertEquals(35, generated.board.cells.count { it != null })
            assertTrue(hasLegalPair(generated.board), "seed=$seed")
            (generated.board.cells + generated.board.incoming).filterNotNull().forEach { cell ->
                if (cell.kind == TileKind.HUE) counts[cell.hue!!] = counts.getValue(cell.hue!!) + 1L
            }
        }
        assertTrue(fallbacks < 10, "fallbacks=$fallbacks must remain below 0.01%")
        val total = counts.values.sum().toDouble()
        Hue.entries.forEach { hue ->
            val observed = counts.getValue(hue) / total
            val target = expected.getValue(hue) / 1_000_000.0
            assertTrue(kotlin.math.abs(observed - target) < 0.005, "$hue observed=$observed expected=$target")
        }
    }

    private fun hasLegalPair(b: BoardState) = b.cells.indices.any { i ->
        val c = b.cells[i] ?: return@any false
        c.kind == TileKind.HUE && Coord(i % 7, i / 7).neighbors().any { n ->
            val other = b[n]
            other?.kind == TileKind.HUE && other.hue == c.hue
        }
    }
}
