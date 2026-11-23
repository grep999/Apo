package com.emberfall.combat

import com.emberfall.board.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CombatCoreTest {
    private val engine = CombatEngine()

    private fun boardWith(vararg entries: Pair<Coord, Cell>): BoardState {
        var board = BoardState.empty()
        entries.forEach { board = board.withCell(it.first, it.second) }
        return board
    }

    private fun enemy(vararg intents: EnemyIntent) =
        EnemyDefinition("test-enemy", 40, intents.toList())

    private fun accepted(result: CombatResult): CombatResult.Accepted =
        assertIs<CombatResult.Accepted>(result)

    private fun beginCast(state: CombatState): CombatState {
        var s = accepted(engine.transition(state, CombatCommand.CompleteDraw)).state
        s = accepted(engine.transition(s, CombatCommand.EndWeave)).state
        return s
    }

    private fun beginEnemy(state: CombatState, incoming: List<Cell?> = List(7) { null }): CombatState {
        var s = beginCast(state)
        s = accepted(engine.transition(s, CombatCommand.EndCast)).state
        s = accepted(engine.transition(s, CombatCommand.ResolveWall(listOf(incoming)))).state
        return s
    }

    @Test fun initialStateExposesTwoStableIntentsAndStartsInDraw() {
        val definition = enemy(EnemyIntent.Strike(8), EnemyIntent.AshRow)
        val state = CombatState.start(BoardState.empty(), definition)
        assertEquals(CombatPhase.DRAW, state.phase)
        assertEquals(listOf(EnemyIntent.Strike(8), EnemyIntent.AshRow), state.nextIntents)
        assertEquals(70, state.player.vigil)
        assertEquals(2, state.player.flasks)
        assertEquals(3, state.tapsRemaining)
    }

    @Test fun successfulTapPaysInkDealsChipAndConsumesExactlyOneTap() {
        val board = boardWith(
            Coord(0, 6) to Cell.hue(Hue.EMBER),
            Coord(1, 6) to Cell.hue(Hue.EMBER)
        )
        var state = CombatState.start(board, enemy(EnemyIntent.Strike(8)))
        state = accepted(engine.transition(state, CombatCommand.CompleteDraw)).state
        val result = accepted(engine.transition(state, CombatCommand.Tap(Coord(0, 6))))
        assertEquals(2, result.state.player.immutableInk.getValue(Hue.EMBER))
        assertEquals(39, result.state.enemy.vigor)
        assertEquals(2, result.state.tapsRemaining)
        assertTrue(result.events.any { it is CombatEvent.TapResolved })
    }

    @Test fun rejectedTapIsAtomicAndDoesNotConsumeTapOrChangeDigest() {
        val state = accepted(engine.transition(
            CombatState.start(BoardState.empty(), enemy(EnemyIntent.Strike(8))),
            CombatCommand.CompleteDraw
        )).state
        val before = CombatStateDigest.sha256(state)
        val result = engine.transition(state, CombatCommand.Tap(Coord(0, 0)))
        assertEquals(CombatRejection.ILLEGAL_BOARD_TAP, assertIs<CombatResult.Rejected>(result).reason)
        assertEquals(before, CombatStateDigest.sha256(state))
        assertEquals(3, state.tapsRemaining)
    }

    @Test fun fourthTapIsRejectedAndDoesNotChangeState() {
        val board = boardWith(
            Coord(0, 5) to Cell.hue(Hue.EMBER), Coord(0, 6) to Cell.hue(Hue.EMBER),
            Coord(2, 5) to Cell.hue(Hue.GOLD), Coord(2, 6) to Cell.hue(Hue.GOLD),
            Coord(4, 5) to Cell.hue(Hue.MOSS), Coord(4, 6) to Cell.hue(Hue.MOSS),
            Coord(6, 5) to Cell.hue(Hue.TIDE), Coord(6, 6) to Cell.hue(Hue.TIDE)
        )
        var state = accepted(engine.transition(CombatState.start(board, enemy(EnemyIntent.Strike(1))), CombatCommand.CompleteDraw)).state
        listOf(Coord(0, 5), Coord(2, 5), Coord(4, 5)).forEach { target ->
            state = accepted(engine.transition(state, CombatCommand.Tap(target))).state
        }
        val before = CombatStateDigest.sha256(state)
        val rejected = engine.transition(state, CombatCommand.Tap(Coord(6, 5)))
        assertEquals(CombatRejection.NO_TAPS, assertIs<CombatResult.Rejected>(rejected).reason)
        assertEquals(before, CombatStateDigest.sha256(state))
    }

    @Test fun phasesAreStrictAndCastEffectsCannotRunDuringWeave() {
        val state = accepted(engine.transition(CombatState.start(BoardState.empty(), enemy(EnemyIntent.Strike(1))), CombatCommand.CompleteDraw)).state
        assertEquals(CombatRejection.WRONG_PHASE,
            assertIs<CombatResult.Rejected>(engine.transition(state, CombatCommand.CardBlock(5))).reason)
        assertEquals(CombatPhase.CAST, accepted(engine.transition(state, CombatCommand.EndWeave)).state.phase)
    }

    @Test fun strikeUsesPlayerBlockAndPlayerBlockResetsAtNextTurn() {
        var state = CombatState.start(BoardState.empty(), enemy(EnemyIntent.Strike(10), EnemyIntent.Strike(10)))
        state = accepted(engine.transition(state, CombatCommand.CompleteDraw)).state
        state = accepted(engine.transition(state, CombatCommand.EndWeave)).state
        state = accepted(engine.transition(state, CombatCommand.CardBlock(4))).state
        state = accepted(engine.transition(state, CombatCommand.EndCast)).state
        state = accepted(engine.transition(state, CombatCommand.ResolveWall(listOf(List(7) { null })))).state
        state = accepted(engine.transition(state, CombatCommand.ResolveEnemy)).state
        assertEquals(64, state.player.vigil)
        assertEquals(0, state.player.block)
        state = accepted(engine.transition(state, CombatCommand.FinishCleanup)).state
        assertEquals(0, state.player.block)
        assertEquals(1, state.turn)
        assertEquals(CombatPhase.DRAW, state.phase)
    }

    @Test fun enemyBlockIsAbsorbedByChipAndCardDamage() {
        val board = boardWith(Coord(0, 6) to Cell.hue(Hue.EMBER), Coord(1, 6) to Cell.hue(Hue.EMBER))
        var state = CombatState.start(board, enemy(EnemyIntent.Strike(1)))
        state = state.copy(enemy = EnemyState(40, 2))
        state = accepted(engine.transition(state, CombatCommand.CompleteDraw)).state
        state = accepted(engine.transition(state, CombatCommand.Tap(Coord(0, 6)))).state
        assertEquals(40, state.enemy.vigor)
        assertEquals(1, state.enemy.block)
        state = accepted(engine.transition(state, CombatCommand.EndWeave)).state
        state = accepted(engine.transition(state, CombatCommand.CardDamage(2))).state
        assertEquals(39, state.enemy.vigor)
        assertEquals(0, state.enemy.block)
    }

    @Test fun flaskHealsThirtyConsumesOneAndRejectsAtFullVigil() {
        var state = CombatState.start(BoardState.empty(), enemy(EnemyIntent.Strike(1)))
        state = state.copy(player = state.player.copy(vigil = 50))
        state = accepted(engine.transition(state, CombatCommand.UseFlask)).state
        assertEquals(2, state.player.flasks)
        assertEquals(70, state.player.vigil)
        assertEquals(CombatRejection.FULL_VIGIL,
            assertIs<CombatResult.Rejected>(engine.transition(state, CombatCommand.UseFlask)).reason)
    }

    @Test fun brandCostsTwoVigilPerBrandedBlockPopped() {
        val board = boardWith(
            Coord(0, 6) to Cell.hue(Hue.EMBER, branded = true),
            Coord(1, 6) to Cell.hue(Hue.EMBER, branded = true)
        )
        var state = CombatState.start(board, enemy(EnemyIntent.Strike(1)))
        state = accepted(engine.transition(state, CombatCommand.CompleteDraw)).state
        state = accepted(engine.transition(state, CombatCommand.Tap(Coord(0, 6)))).state
        assertEquals(66, state.player.vigil)
    }

    @Test fun overflowDealsEightUnmitigatedVigilAndConsumesOneFlask() {
        val board = boardWith(Coord(0, 0) to Cell.ash())
        var state = CombatState.start(board, enemy(EnemyIntent.Strike(1)))
        state = beginCast(state)
        state = accepted(engine.transition(state, CombatCommand.EndCast)).state
        state = accepted(engine.transition(state, CombatCommand.ResolveWall(listOf(List(7) { null })))).state
        assertEquals(62, state.player.vigil)
        assertEquals(1, state.player.flasks)
        assertEquals(CombatPhase.ENEMY, state.phase)
    }

    @Test fun ashRowAndStoneSeedChangeBoardDeterministically() {
        var state = CombatState.start(BoardState.empty(), enemy(EnemyIntent.AshRow, EnemyIntent.StoneSeed(2)))
        state = beginEnemy(state)
        state = accepted(engine.transition(state, CombatCommand.ResolveEnemy)).state
        assertTrue(state.board.incoming.all { it?.kind == TileKind.ASH })
        state = accepted(engine.transition(state, CombatCommand.FinishCleanup)).state
        state = beginEnemy(state)
        state = accepted(engine.transition(state, CombatCommand.ResolveEnemy)).state
        assertEquals(3, (0..6).count { y -> state.board[Coord(2, y)]?.kind == TileKind.STONE })
    }

    @Test fun stainSkipsSealedColumnsAndSealLastsThreeEnemyPhases() {
        val board = boardWith(
            Coord(0, 6) to Cell.hue(Hue.TIDE),
            Coord(1, 6) to Cell.hue(Hue.TIDE)
        ).sealColumn(1, 3)
        var state = CombatState.start(board, enemy(EnemyIntent.Stain(Hue.TIDE), EnemyIntent.Seal(2)))
        state = beginEnemy(state)
        state = accepted(engine.transition(state, CombatCommand.ResolveEnemy)).state
        assertEquals(TileKind.ASH, state.board[Coord(0, 6)]?.kind)
        assertEquals(Hue.TIDE, state.board[Coord(1, 6)]?.hue)
        assertEquals(2, state.board.sealTurnsRemaining[1])
        state = accepted(engine.transition(state, CombatCommand.FinishCleanup)).state
        state = beginEnemy(state)
        state = accepted(engine.transition(state, CombatCommand.ResolveEnemy)).state
        assertEquals(3, state.board.sealTurnsRemaining[2])
        assertEquals(1, state.board.sealTurnsRemaining[1])
    }

    @Test fun fogIsThreeSuccessfulTapsAndSurgeRequestsTwoWallRises() {
        var state = CombatState.start(BoardState.empty(), enemy(EnemyIntent.Fog, EnemyIntent.Surge))
        state = beginEnemy(state)
        state = accepted(engine.transition(state, CombatCommand.ResolveEnemy)).state
        assertEquals(3, state.fogTapsRemaining)
        state = accepted(engine.transition(state, CombatCommand.FinishCleanup)).state
        state = accepted(engine.transition(state, CombatCommand.CompleteDraw)).state
        assertEquals(CombatRejection.INVALID_WALL_ROWS,
            assertIs<CombatResult.Rejected>(engine.transition(state.copy(phase = CombatPhase.WALL), CombatCommand.ResolveWall(listOf(List(7) { null })))).reason)
        state = accepted(engine.transition(state, CombatCommand.EndWeave)).state
        state = accepted(engine.transition(state, CombatCommand.EndCast)).state
        state = accepted(engine.transition(state, CombatCommand.ResolveWall(listOf(List(7) { null })))).state
        state = accepted(engine.transition(state, CombatCommand.ResolveEnemy)).state
        assertEquals(2, state.wallRisesNextTurn)
    }

    @Test fun surgeConsumesExactlyTwoReplacementRowsOnNextWall() {
        var state = CombatState.start(BoardState.empty(), enemy(EnemyIntent.Surge))
        state = beginEnemy(state)
        state = accepted(engine.transition(state, CombatCommand.ResolveEnemy)).state
        state = accepted(engine.transition(state, CombatCommand.FinishCleanup)).state
        state = accepted(engine.transition(state, CombatCommand.CompleteDraw)).state
        state = accepted(engine.transition(state, CombatCommand.EndWeave)).state
        state = accepted(engine.transition(state, CombatCommand.EndCast)).state
        val rows = listOf(List(7) { Cell.ash() }, List(7) { Cell.stone() })
        val wall = accepted(engine.transition(state, CombatCommand.ResolveWall(rows)))
        assertEquals(CombatPhase.ENEMY, wall.state.phase)
        assertEquals(1, wall.state.wallRisesNextTurn)
        assertTrue(wall.events.any { it == CombatEvent.WallResolved(false, 2) })
    }

    @Test fun delayedIntentIsNotConsumedAndForecastRemainsStable() {
        var state = CombatState.start(BoardState.empty(), enemy(EnemyIntent.Strike(9), EnemyIntent.AshRow))
        state = beginCast(state)
        state = accepted(engine.transition(state, CombatCommand.DelayNextIntent)).state
        state = accepted(engine.transition(state, CombatCommand.EndCast)).state
        state = accepted(engine.transition(state, CombatCommand.ResolveWall(listOf(List(7) { null })))).state
        val before = state.nextIntents
        state = accepted(engine.transition(state, CombatCommand.ResolveEnemy)).state
        assertEquals(0, state.intentCursor)
        assertEquals(before, state.nextIntents)
        assertEquals(1, state.delayedIntents)
    }

    @Test fun snatchAndCurseAreExplicitCardEngineIntegrationEvents() {
        var state = CombatState.start(BoardState.empty(), enemy(EnemyIntent.Snatch, EnemyIntent.Curse))
        state = beginEnemy(state)
        val snatch = accepted(engine.transition(state, CombatCommand.ResolveEnemy))
        assertTrue(snatch.events.contains(CombatEvent.CardExhaustRequested))
        state = accepted(engine.transition(snatch.state, CombatCommand.FinishCleanup)).state
        state = beginEnemy(state)
        val curse = accepted(engine.transition(state, CombatCommand.ResolveEnemy))
        assertTrue(curse.events.contains(CombatEvent.CurseDiscardRequested))
    }

    @Test fun replayedCommandSequenceProducesIdenticalFinalDigest() {
        val definition = enemy(EnemyIntent.Strike(7), EnemyIntent.AshRow)
        val initial = CombatState.start(BoardState.empty(), definition)
        fun run(): CombatState {
            var state = accepted(engine.transition(initial, CombatCommand.CompleteDraw)).state
            state = accepted(engine.transition(state, CombatCommand.EndWeave)).state
            state = accepted(engine.transition(state, CombatCommand.EndCast)).state
            state = accepted(engine.transition(state, CombatCommand.ResolveWall(listOf(List(7) { null })))).state
            return accepted(engine.transition(state, CombatCommand.ResolveEnemy)).state
        }
        assertEquals(CombatStateDigest.sha256(run()), CombatStateDigest.sha256(run()))
        assertNotEquals(CombatStateDigest.sha256(initial), CombatStateDigest.sha256(run()))
    }
}
