package com.example

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import android.app.Application
import com.example.audio.CachedTrackMeta
import com.example.audio.MusicMode
import com.example.audio.SoundtrackModelTier
import com.example.audio.SoundtrackPreset
import com.example.audio.SoundtrackSlot
import com.example.audio.SoundtrackSlotStatus
import com.example.audio.SoundtrackUiState
import com.example.data.ActiveRunEntity
import com.example.data.ActiveRunLoadResult
import com.example.data.EmberfallDao
import com.example.data.EmberfallDatabase
import com.example.data.EmberfallRepository
import com.example.data.PlayerSettings
import com.example.data.RunHistoryEntity
import com.example.data.RunSnapshotCodec
import com.example.data.SettingsDataStore
import com.example.data.ShadeDeckEntity
import com.example.engine.*
import com.example.ui.components.BoardJuiceFx
import com.example.ui.components.FxCell
import com.example.ui.components.FxWave
import com.example.ui.screens.ActMapScreen
import com.example.ui.screens.CampfireScreen
import com.example.ui.screens.ClassSelectScreen
import com.example.ui.screens.CombatArenaScreen
import com.example.ui.screens.EventScreen
import com.example.ui.screens.RewardsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TitleScreen
import com.example.ui.theme.EmberfallTheme
import com.example.ui.viewmodel.AppDestination
import com.example.ui.viewmodel.EmberfallViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-xxhdpi")
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Emberfall", appName)
  }

  @Test
  fun `run snapshot codec v3 roundtrip migrates v2 and rejects corrupt TileKind`() {
    val started = GameEngine.startNewRun(HeroClass.THE_UNLIT, "CODEC-V3", unlitCovenantActive = true, dreadLevel = 1)
    val nodeId = GameEngine.availableNodeIds(started).first()
    val inCombat = GameEngine.execute(started, GameCommand.ChooseNode(nodeId)).run
    val c0 = requireNotNull(inCombat.combat)
    val mutated = inCombat.copy(
      dreadConsecutiveAshTurns = 2,
      combat = c0.copy(
        board = c0.board.copy(
          sealedColumns = mapOf(1 to 2),
          grid = c0.board.grid.mapIndexed { r, row ->
            row.mapIndexed { c, tile -> if (r == 4 && c == 3) tile.copy(isBranded = true) else tile }
          },
        ),
        player = c0.player.copy(fogTapsRemaining = 3, vigil = 5),
      ),
    )

    val json = RunSnapshotCodec.encode(mutated)
    val decoded = RunSnapshotCodec.decode(json)

    assertEquals(RunState.CURRENT_SCHEMA_VERSION, decoded.schemaVersion)
    assertEquals(2, decoded.dreadConsecutiveAshTurns)
    assertEquals(mapOf(1 to 2), decoded.combat?.board?.sealedColumns)
    assertEquals(true, decoded.combat?.board?.tileAt(4, 3)?.isBranded)
    assertEquals(3, decoded.combat?.player?.fogTapsRemaining)
    assertEquals(5, decoded.combat?.player?.vigil)
    assertEquals(mutated.combat?.board?.grid, decoded.combat?.board?.grid)

    // Verify v2 -> v3 migration cleanly maps legacy fogStacks -> fogTapsRemaining
    val legacyObj = JSONObject(json)
    legacyObj.put("schemaVersion", 2)
    val legacyPlayer = legacyObj.getJSONObject("combat").getJSONObject("player")
    legacyPlayer.remove("fogTapsRemaining")
    legacyPlayer.put("fogStacks", 2)
    legacyObj.getJSONObject("combat").getJSONObject("board").remove("sealedColumns")
    val migrated = RunSnapshotCodec.decode(legacyObj.toString())
    assertEquals(3, migrated.schemaVersion)
    assertEquals(2, migrated.combat?.player?.fogTapsRemaining)

    // Verify corrupt unknown TileKind throws IllegalArgumentException (never silently becomes Ember)
    val corruptJson = json.replace("\"ASH\"", "\"CORRUPT_TILE_XYZ\"").replace("\"HUE:GOLD\"", "\"CORRUPT_TILE_XYZ\"")
    assertThrows(IllegalArgumentException::class.java) {
      RunSnapshotCodec.decode(corruptJson)
    }
  }

  @Test
  fun `room migration v3 to v4 preserves run history and repository mutex serializes writes`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dbName = "test_migration_v3_v4_${System.nanoTime()}.db"
    context.deleteDatabase(dbName)

    // Create a v3 SQLite database matching pre-v4 schema & insert a RunHistory row
    val helper = FrameworkSQLiteOpenHelperFactory().create(
      SupportSQLiteOpenHelper.Configuration.builder(context)
        .name(dbName)
        .callback(object : SupportSQLiteOpenHelper.Callback(3) {
          override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL(
              """
              CREATE TABLE active_run (
                id INTEGER NOT NULL PRIMARY KEY,
                seedLabel TEXT NOT NULL,
                heroClassName TEXT NOT NULL,
                act INTEGER NOT NULL,
                hp INTEGER NOT NULL,
                maxHp INTEGER NOT NULL,
                flasks INTEGER NOT NULL,
                payloadJson TEXT NOT NULL,
                updatedAtMillis INTEGER NOT NULL
              )
              """.trimIndent()
            )
            db.execSQL(
              """
              CREATE TABLE shade_deck (
                id INTEGER NOT NULL PRIMARY KEY,
                heroClassName TEXT NOT NULL,
                seedLabel TEXT NOT NULL,
                cardIdsCsv TEXT NOT NULL,
                recordedAtMillis INTEGER NOT NULL
              )
              """.trimIndent()
            )
            db.execSQL(
              """
              CREATE TABLE run_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                seedLabel TEXT NOT NULL,
                heroClassName TEXT NOT NULL,
                actReached INTEGER NOT NULL,
                victory INTEGER NOT NULL,
                terminationReason TEXT NOT NULL,
                enemiesSlain INTEGER NOT NULL,
                turnsTaken INTEGER NOT NULL,
                highestSpectrumHalfSteps INTEGER NOT NULL,
                finishedAtMillis INTEGER NOT NULL
              )
              """.trimIndent()
            )
            db.execSQL(
              """
              INSERT INTO run_history (seedLabel, heroClassName, actReached, victory, terminationReason, enemiesSlain, turnsTaken, highestSpectrumHalfSteps, finishedAtMillis)
              VALUES ('LEGACY-V3', 'Ashen Monk', 2, 0, 'Fallen in Combat', 7, 21, 5, 1000)
              """.trimIndent()
            )
          }

          override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        })
        .build()
    )
    helper.writableDatabase.close()
    helper.close()

    // Open with Room v4 using explicit ALL_MIGRATIONS -> existing row preserved with dreadEndingAchieved = false
    val roomDb = Room.databaseBuilder(context, EmberfallDatabase::class.java, dbName)
      .addMigrations(*EmberfallDatabase.ALL_MIGRATIONS)
      .allowMainThreadQueries()
      .build()

    try {
      val history = roomDb.emberfallDao().observeRunHistory().first()
      assertEquals(1, history.size)
      assertEquals("LEGACY-V3", history.first().seedLabel)
      assertFalse(history.first().dreadEndingAchieved)

      // Exercise concurrent repository writes under Mutex
      val repo = EmberfallRepository(roomDb.emberfallDao(), SettingsDataStore(context))
      val run = GameEngine.startNewRun(HeroClass.THE_UNLIT, "MUTEX-TEST")
      val jobs = (1..6).map { idx ->
        async {
          repo.saveActiveRun(run.copy(cinders = 50 + idx))
        }
      }
      jobs.awaitAll()
      val loaded = repo.loadActiveRun()
      assertNotNull(loaded)
      assertTrue((loaded?.cinders ?: 0) in 51..56)
    } finally {
      roomDb.close()
      context.deleteDatabase(dbName)
    }
  }

  @Test
  fun `title screen enters canonical class selection enforces Unlit-only Covenant and one-tap block click pops group`() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("new_expedition_button").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()

    // Default selection is Ashen Monk -> Unlit Covenant switch MUST be disabled
    composeTestRule.onNodeWithTag("class_card_ashen_monk").assertIsDisplayed()
    composeTestRule.onNodeWithTag("class_select_list").performScrollToNode(hasTestTag("toggle_unlit_covenant"))
    composeTestRule.onNodeWithTag("toggle_unlit_covenant").assertIsNotEnabled()

    // Select The Unlit -> Unlit Covenant switch becomes enabled
    composeTestRule.onNodeWithTag("class_select_list").performScrollToNode(hasTestTag("class_card_the_unlit"))
    composeTestRule.onNodeWithTag("class_card_the_unlit").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("class_select_list").performScrollToNode(hasTestTag("toggle_unlit_covenant"))
    composeTestRule.onNodeWithTag("toggle_unlit_covenant").assertIsEnabled()

    // Switch to Quarry Witch and start expedition
    composeTestRule.onNodeWithTag("class_select_list").performScrollToNode(hasTestTag("class_card_quarry_witch"))
    composeTestRule.onNodeWithTag("class_card_quarry_witch").assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag("start_expedition_button").assertIsDisplayed().performClick()

    composeTestRule.waitUntil(timeoutMillis = 5000) {
      composeTestRule.onAllNodesWithTag("map_node_a1_r1_c0").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("map_node_a1_r1_c0").assertIsDisplayed().performClick()

    composeTestRule.waitUntil(timeoutMillis = 5000) {
      composeTestRule.onAllNodesWithTag("board_cell_2_0").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithTag("proceed_cast_button").assertIsDisplayed()

    // 1. Two-click swap on adjacent tiles executes Match-3 swap (MOVES drops 3/3 -> 2/3 or wins combat)
    var swappedViaClicks = false
    for (r in 2 until 7) {
      for (c in 0 until 6) {
        if (composeTestRule.onAllNodesWithTag("inline_spoils_overlay").fetchSemanticsNodes().isNotEmpty() ||
          composeTestRule.onAllNodesWithTag("combat_outcome_continue").fetchSemanticsNodes().isNotEmpty()
        ) {
          swappedViaClicks = true
          break
        }
        composeTestRule.onNodeWithTag("board_cell_${r}_${c}").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("board_cell_${r}_${c + 1}").performClick()
        composeTestRule.waitForIdle()
        if (composeTestRule.onAllNodesWithText("2/3").fetchSemanticsNodes().isNotEmpty() ||
          composeTestRule.onAllNodesWithTag("inline_spoils_overlay").fetchSemanticsNodes().isNotEmpty() ||
          composeTestRule.onAllNodesWithTag("combat_outcome_continue").fetchSemanticsNodes().isNotEmpty()
        ) {
          swappedViaClicks = true
          break
        }
      }
      if (swappedViaClicks) break
    }
    if (!swappedViaClicks) {
      for (r in 2 until 6) {
        for (c in 0 until 7) {
          if (composeTestRule.onAllNodesWithTag("inline_spoils_overlay").fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule.onAllNodesWithTag("combat_outcome_continue").fetchSemanticsNodes().isNotEmpty()
          ) {
            swappedViaClicks = true
            break
          }
          composeTestRule.onNodeWithTag("board_cell_${r}_${c}").performClick()
          composeTestRule.waitForIdle()
          composeTestRule.onNodeWithTag("board_cell_${r + 1}_${c}").performClick()
          composeTestRule.waitForIdle()
          if (composeTestRule.onAllNodesWithText("2/3").fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule.onAllNodesWithTag("inline_spoils_overlay").fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule.onAllNodesWithTag("combat_outcome_continue").fetchSemanticsNodes().isNotEmpty()
          ) {
            swappedViaClicks = true
            break
          }
        }
        if (swappedViaClicks) break
      }
    }
    assertTrue("Two-click swap on a valid Match-3 pair should align 3+ tiles and advance moves to 2/3", swappedViaClicks)

    // 2. If enemy survived move 1, clicking Valid Swap quick chip executes move 2
    //    (account for up to 2 Ember Surge 4-match free moves per turn before moves drop 2/3 -> 1/3 or combat wins)
    if (composeTestRule.onAllNodesWithTag("combat_outcome_continue").fetchSemanticsNodes().isEmpty()) {
      var progressed = false
      repeat(3) {
        if (!progressed &&
          composeTestRule.onAllNodesWithTag("valid_swap_chip_0").fetchSemanticsNodes().isNotEmpty()
        ) {
          composeTestRule.onNodeWithTag("valid_swap_chip_0").assertIsDisplayed().performClick()
          composeTestRule.waitForIdle()
          progressed = composeTestRule.onAllNodesWithText("1/3").fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule.onAllNodesWithTag("combat_outcome_continue").fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule.onAllNodesWithTag("inline_spoils_overlay").fetchSemanticsNodes().isNotEmpty()
        }
      }
      assertTrue("Clicking valid_swap_chip_0 should advance moves to 1/3 or clear the encounter", progressed)
    }
  }

  @Test
  fun `fog combat hides Ink pool numbers while preserving hue glyphs`() {
    val started = GameEngine.startNewRun(HeroClass.ASHEN_MONK, "UI-FOG")
    val inCombat = GameEngine.execute(started, GameCommand.ChooseNode(GameEngine.availableNodeIds(started).first())).run
    val foggedRun = inCombat.copy(
      combat = requireNotNull(inCombat.combat).copy(
        player = requireNotNull(inCombat.combat).player.copy(fogTapsRemaining = 3),
        resources = ResourceState(inkPools = mapOf(Hue.EMBER to 4, Hue.GOLD to 2)),
      ),
    )

    composeTestRule.activityRule.scenario.onActivity { activity ->
      activity.setContent {
        EmberfallTheme {
          CombatArenaScreen(
            run = foggedRun,
            selectedGroup = null,
            boardFeedback = null,
            settings = PlayerSettings(colorblindSymbols = false),
            onTapBoardCell = { _, _ -> },
            onConfirmSelectedGroup = {},
            onUseChiselWand = { _, _, _ -> },
            onProceedToCast = {},
            onPlayCard = { _, _ -> },
            onUseFlask = {},
            onEndTurn = {},
            onProceedVictory = {},
            onProceedDefeat = {},
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    // Fog hides Ink counts with '?' and Valid Swap quick button remains accessible without leaking hue
    composeTestRule.onNodeWithText("EMB ?").assertIsDisplayed()
    composeTestRule.onNodeWithText("3T").assertIsDisplayed()
    composeTestRule.onNodeWithTag("valid_swap_chip_0").assertIsDisplayed()
    // Even with colorblindSymbols=false, Fog preserves tile glyphs on occupied cells
    val anyGlyphVisible = listOf("◆", "☀", "✦", "●", "☾", "★").any { glyph ->
      composeTestRule.onAllNodesWithText(glyph).fetchSemanticsNodes().isNotEmpty()
    }
    assertTrue("Tile glyphs must remain visible during Fog", anyGlyphVisible)
  }

  @Test
  fun `event screen renders choices resolves selected option and enables leave button`() {
    val base = GameEngine.startNewRun(HeroClass.THE_UNLIT, "UI-EVENT")
    val def = GameCatalog.eventById("event_forgotten_kiln")
    var currentRun = base.copy(
      inNonCombatNode = true,
      activeEvent = EventState(def.id, def.title, def.body, def.choices),
    )
    var leftEvent = false

    composeTestRule.activityRule.scenario.onActivity { activity ->
      activity.setContent {
        var state by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(currentRun) }
        EmberfallTheme {
          EventScreen(
            run = state,
            onChooseOption = { choiceId ->
              val res = GameEngine.execute(state, GameCommand.ChooseEventOption(choiceId))
              if (res.accepted) {
                state = res.run
                currentRun = res.run
              }
            },
            onLeave = { leftEvent = true },
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("event_leave_button").assertIsNotEnabled()
    composeTestRule.onNodeWithTag("event_choice_kiln_scavenge").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("event_resolved_banner").assertIsDisplayed()
    composeTestRule.onNodeWithTag("event_leave_button").assertIsEnabled().performClick()
    assertTrue(leftEvent)
    assertEquals(base.cinders + 35, currentRun.cinders)
  }

  @Test
  fun `boss combat displays painterly portrait and circular Spectrum wheel dial`() {
    val started = GameEngine.startNewRun(HeroClass.THE_UNLIT, "UI-BOSS", unlitCovenantActive = true)
    val inCombat = GameEngine.execute(started, GameCommand.ChooseNode(GameEngine.availableNodeIds(started).first())).run
    val c0 = requireNotNull(inCombat.combat)
    val bossRun = inCombat.copy(
      currentAct = 3,
      combat = c0.copy(
        enemy = c0.enemy.copy(
          defId = "act3_boss_malakor",
          name = "The Hearthkeeper",
          act = 3,
          hp = 195,
          maxHp = 195,
          isBoss = true,
        ),
      ),
    )

    composeTestRule.activityRule.scenario.onActivity { activity ->
      activity.setContent {
        EmberfallTheme {
          CombatArenaScreen(
            run = bossRun,
            selectedGroup = null,
            boardFeedback = null,
            settings = PlayerSettings(),
            onTapBoardCell = { _, _ -> },
            onConfirmSelectedGroup = {},
            onUseChiselWand = { _, _, _ -> },
            onProceedToCast = {},
            onPlayCard = { _, _ -> },
            onUseFlask = {},
            onEndTurn = {},
            onProceedVictory = {},
            onProceedDefeat = {},
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("enemy_portrait_art").assertIsDisplayed()
    composeTestRule.onNodeWithTag("spectrum_wheel_dial").assertIsDisplayed()
    composeTestRule.onNodeWithTag("dread_streak_badge").assertIsDisplayed()
  }

  @Test
  fun `act map displays tooltip when tapping locked future node and class select shows Dread tier label`() {
    val run = GameEngine.startNewRun(HeroClass.ASHEN_MONK, "UI-MAP-TOOLTIP", dreadLevel = 2)
    val row2Node = run.actMap.nodes.first { it.row == 2 }

    composeTestRule.activityRule.scenario.onActivity { activity ->
      activity.setContent {
        EmberfallTheme {
          ActMapScreen(
            run = run,
            onSelectNode = {},
            onExitToTitle = {},
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("act_map_list").performScrollToNode(hasTestTag("map_node_${row2Node.id}"))
    composeTestRule.onNodeWithTag("map_node_${row2Node.id}").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("map_node_tooltip").assertIsDisplayed()
    composeTestRule.onNodeWithTag("dismiss_map_tooltip").performClick()
    composeTestRule.waitForIdle()
    assertTrue(composeTestRule.onAllNodesWithTag("map_node_tooltip").fetchSemanticsNodes().isEmpty())

    // Verify Dread tier mechanical description renders on ClassSelectScreen
    composeTestRule.activityRule.scenario.onActivity { activity ->
      activity.setContent {
        EmberfallTheme {
          ClassSelectScreen(
            onBack = {},
            onStartRun = { _, _, _, _ -> },
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("class_select_list").performScrollToNode(hasTestTag("dread_tier_description"))
    composeTestRule.onNodeWithTag("dread_tier_description").assertIsDisplayed()
  }

  @Test
  fun `minimal techno audio toggles render and update in combat and settings screens`() {
    val defaults = PlayerSettings()
    assertFalse("One-tap board fire must default to false", defaults.oneTapBoardFire)
    assertTrue("Minimal techno soundtrack must default to true", defaults.musicEnabled)
    assertTrue("Minimal synth SFX must default to true", defaults.sfxEnabled)

    val started = GameEngine.startNewRun(HeroClass.ASHEN_MONK, "UI-AUDIO")
    val inCombat = GameEngine.execute(started, GameCommand.ChooseNode(GameEngine.availableNodeIds(started).first())).run
    var muteToggledCount = 0

    composeTestRule.activityRule.scenario.onActivity { activity ->
      activity.setContent {
        EmberfallTheme {
          CombatArenaScreen(
            run = inCombat,
            selectedGroup = null,
            boardFeedback = null,
            settings = defaults,
            onTapBoardCell = { _, _ -> },
            onConfirmSelectedGroup = {},
            onUseChiselWand = { _, _, _ -> },
            onProceedToCast = {},
            onPlayCard = { _, _ -> },
            onUseFlask = {},
            onEndTurn = {},
            onProceedVictory = {},
            onProceedDefeat = {},
            onToggleAudioMute = { muteToggledCount++ },
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("combat_audio_toggle").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    assertEquals(1, muteToggledCount)

    var musicState = true
    var sfxState = true
    composeTestRule.activityRule.scenario.onActivity { activity ->
      activity.setContent {
        var currentSettings by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(defaults) }
        EmberfallTheme {
          SettingsScreen(
            settings = currentSettings,
            onToggleMusic = {
              musicState = it
              currentSettings = currentSettings.copy(musicEnabled = it)
            },
            onToggleSfx = {
              sfxState = it
              currentSettings = currentSettings.copy(sfxEnabled = it)
            },
            onToggleColorblind = {},
            onToggleHints = {},
            onToggleFastAnimations = {},
            onToggleHaptics = {},
            onToggleOneTap = {},
            onBack = {},
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("toggle_music").assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag("toggle_sfx").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    assertFalse("Music toggle should switch to false", musicState)
    assertFalse("SFX toggle should switch to false", sfxState)
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun `pc mouse drag-to-swap moves adjacent tiles to align 3-in-a-row while non-matching swap triggers feedback`() {
    val started = GameEngine.startNewRun(HeroClass.ASHEN_MONK, "UI-MOUSE-SWAP")
    val inCombat = GameEngine.execute(started, GameCommand.ChooseNode(GameEngine.availableNodeIds(started).first())).run
    val c0 = requireNotNull(inCombat.combat)

    // Deterministic Match-3 board:
    // (5,1)=EMBER, (5,2)=EMBER, (5,3)=TIDE, (4,3)=EMBER -> dragging (4,3) -> (5,3) creates horizontal 3 EMBER!
    // Dragging (2,0) -> (2,1) forms NO 3-in-a-row -> invalid swap guidance!
    val customGrid = c0.board.grid.mapIndexed { r, row ->
      row.mapIndexed { col, tile ->
        when {
          r < 2 -> tile.copy(kind = TileKind.Empty)
          r == 5 && (col == 1 || col == 2) -> tile.copy(kind = TileKind.Playable(Hue.EMBER))
          r == 5 && col == 3 -> tile.copy(kind = TileKind.Playable(Hue.TIDE))
          r == 4 && col == 3 -> tile.copy(kind = TileKind.Playable(Hue.EMBER))
          else -> tile.copy(kind = TileKind.Playable(Hue.WHEEL_ORDER[(r * 2 + col + 1) % 5]))
        }
      }
    }
    val customRun = inCombat.copy(combat = c0.copy(board = c0.board.copy(grid = customGrid)))

    val swapsRequested = mutableListOf<Pair<BoardCoord, BoardCoord>>()
    var invalidSwapCount = 0

    composeTestRule.activityRule.scenario.onActivity { activity ->
      activity.setContent {
        EmberfallTheme {
          CombatArenaScreen(
            run = customRun,
            selectedGroup = null,
            selectedSwapCoord = BoardCoord(4, 3),
            boardFeedback = null,
            settings = PlayerSettings(),
            onTapBoardCell = { _, _ -> },
            onSwapBoardCells = { r1, c1, r2, c2 -> swapsRequested.add(BoardCoord(r1, c1) to BoardCoord(r2, c2)) },
            onConfirmSelectedGroup = {},
            onUseChiselWand = { _, _, _ -> },
            onProceedToCast = {},
            onPlayCard = { _, _ -> },
            onUseFlask = {},
            onEndTurn = {},
            onProceedVictory = {},
            onProceedDefeat = {},
            onInvalidSwapDrag = { invalidSwapCount++ },
          )
        }
      }
    }
    composeTestRule.waitForIdle()

    val cell43Bounds = composeTestRule.onNodeWithTag("board_cell_4_3").fetchSemanticsNode().boundsInRoot
    val cell53Bounds = composeTestRule.onNodeWithTag("board_cell_5_3").fetchSemanticsNode().boundsInRoot
    val cell20Bounds = composeTestRule.onNodeWithTag("board_cell_2_0").fetchSemanticsNode().boundsInRoot
    val cell21Bounds = composeTestRule.onNodeWithTag("board_cell_2_1").fetchSemanticsNode().boundsInRoot
    val gridBounds = composeTestRule.onNodeWithTag("combat_board_grid").fetchSemanticsNode().boundsInRoot

    val local43 = Offset(cell43Bounds.center.x - gridBounds.left, cell43Bounds.center.y - gridBounds.top)
    val local53 = Offset(cell53Bounds.center.x - gridBounds.left, cell53Bounds.center.y - gridBounds.top)
    val local20 = Offset(cell20Bounds.center.x - gridBounds.left, cell20Bounds.center.y - gridBounds.top)
    val local21 = Offset(cell21Bounds.center.x - gridBounds.left, cell21Bounds.center.y - gridBounds.top)

    // 1. Hovering adjacent valid swap partner (5,3) when (4,3) is selected shows live Match-3 swap preview banner
    composeTestRule.onNodeWithTag("combat_board_grid").performMouseInput {
      moveTo(local53)
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("hover_swap_preview").assertIsDisplayed()

    // 2. Dragging (4,3) -> (5,3) commits a valid Match-3 swap!
    composeTestRule.onNodeWithTag("combat_board_grid").performTouchInput {
      swipe(start = local43, end = local53, durationMillis = 120)
    }
    composeTestRule.waitForIdle()
    assertEquals(
      "Dragging (4,3) onto (5,3) should commit a Match-3 swap",
      listOf(BoardCoord(4, 3) to BoardCoord(5, 3)),
      swapsRequested,
    )
    assertEquals(0, invalidSwapCount)

    // 3. Dragging (2,0) -> (2,1) (no 3-in-a-row) triggers invalid swap guidance!
    composeTestRule.onNodeWithTag("combat_board_grid").performTouchInput {
      swipe(start = local20, end = local21, durationMillis = 120)
    }
    composeTestRule.waitForIdle()
    assertEquals("Dragging a non-matching pair should trigger invalid swap guidance", 1, invalidSwapCount)
  }

  @Test
  fun `EFB-005 used campfire save restores to Campfire destination so LeaveNode unlocks map`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = EmberfallViewModel(app)
    val started = GameEngine.startNewRun(HeroClass.ASHEN_MONK, "EFB005-CAMP")
    val campfireNode = started.actMap.nodes.first { it.type == MapNodeType.CAMPFIRE }
    val atCampfire = started.copy(
      hp = 35,
      currentNodeId = campfireNode.id,
      inNonCombatNode = true,
      campfireUsed = false,
      actMap = started.actMap.copy(
        nodes = started.actMap.nodes.map { if (it.id == campfireNode.id) it.copy(visited = true) else it }
      ),
    )

    val rested = GameEngine.execute(atCampfire, GameCommand.CampfireRest)
    assertTrue(rested.accepted)
    assertTrue(rested.run.campfireUsed)
    assertTrue(rested.run.inNonCombatNode)

    // Roundtrip snapshot as if app were killed and resumed after Rest before Leave
    val restored = RunSnapshotCodec.decode(RunSnapshotCodec.encode(rested.run))
    assertEquals(
      "Used campfire save must restore to Campfire so player can tap Leave",
      AppDestination.Campfire,
      viewModel.resolveDestinationForRun(restored),
    )

    // Leaving the campfire unlocks the map cleanly
    val left = GameEngine.execute(restored, GameCommand.LeaveNode)
    assertTrue(left.accepted)
    assertFalse(left.run.inNonCombatNode)
    assertEquals(AppDestination.ActMap, viewModel.resolveDestinationForRun(left.run))
    assertTrue(GameEngine.availableNodeIds(left.run).isNotEmpty())
  }

  @Test
  fun `EFB-006 and EFB-009 victory preserves previous dead Shade deck defeat overwrites it and finishRunAtomic is idempotent`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, EmberfallDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val repo = EmberfallRepository(db.emberfallDao(), SettingsDataStore(context))

      // 1. Defeated run populates Shade deck
      val lostRun = GameEngine.startNewRun(HeroClass.ASHEN_MONK, "EFB006-DEFEAT")
        .copy(hp = 0, gameOverReason = GameOverReason.HP_DEPLETED, runWon = false)
      repo.saveActiveRun(lostRun)
      repo.recordFinishedRun(lostRun)

      val expectedDeadDeck = lostRun.masterDeck.map { it.defId }
      assertEquals(expectedDeadDeck, repo.loadShadeDeckIds())
      assertNull(db.emberfallDao().getActiveRun())
      assertEquals(1, db.emberfallDao().observeRunHistory().first().size)

      // 2. Victorious run MUST NOT overwrite the dead Shade deck
      val wonRun = GameEngine.startNewRun(HeroClass.THE_UNLIT, "EFB006-VICTORY")
        .copy(currentAct = 3, runWon = true, gameOverReason = null)
      repo.saveActiveRun(wonRun)
      repo.recordFinishedRun(wonRun)

      assertEquals("Winning run must preserve previous defeated Shade deck", expectedDeadDeck, repo.loadShadeDeckIds())
      assertNull(db.emberfallDao().getActiveRun())
      assertEquals(2, db.emberfallDao().observeRunHistory().first().size)

      // 3. Duplicate recordFinishedRun call on same finished run is idempotent (no duplicate history row)
      repo.recordFinishedRun(wonRun)
      assertEquals(2, db.emberfallDao().observeRunHistory().first().size)
    } finally {
      db.close()
    }
  }

  @Test
  fun `EFB-007 and EFB-022 v2 completed-combat snapshot migrates unlocked and invalid snapshots throw IllegalArgumentException`() {
    val started = GameEngine.startNewRun(HeroClass.QUARRY_WITCH, "EFB007-V2")
    val row1Combat = started.actMap.nodes.first { it.row == 1 }
    val postCombatMap = started.copy(
      currentNodeId = row1Combat.id,
      combat = null,
      pendingReward = null,
      shopStock = null,
      activeEvent = null,
      inNonCombatNode = false,
      campfireUsed = false,
      actMap = started.actMap.copy(
        nodes = started.actMap.nodes.map { if (it.id == row1Combat.id) it.copy(visited = true) else it }
      ),
    )

    // Simulate a legacy v2 snapshot on a completed combat node
    val v2Obj = JSONObject(RunSnapshotCodec.encode(postCombatMap))
    v2Obj.put("schemaVersion", 2)
    v2Obj.remove("inNonCombatNode")
    val migrated = RunSnapshotCodec.decode(v2Obj.toString())
    assertFalse("Migrated v2 completed combat node must NOT set inNonCombatNode=true", migrated.inNonCombatNode)
    assertTrue("Next map row must remain selectable after v2 migration", GameEngine.availableNodeIds(migrated).isNotEmpty())

    // EFB-022: Unsupported engineVersion rejected
    val badEngineVer = JSONObject(RunSnapshotCodec.encode(postCombatMap)).apply { put("engineVersion", 99) }
    assertThrows(IllegalArgumentException::class.java) {
      RunSnapshotCodec.decode(badEngineVer.toString())
    }

    // EFB-022: Unknown card defId rejected
    val badCard = JSONObject(RunSnapshotCodec.encode(postCombatMap)).apply {
      getJSONArray("masterDeck").getJSONObject(0).put("defId", "fake_nonexistent_card")
    }
    assertThrows(IllegalArgumentException::class.java) {
      RunSnapshotCodec.decode(badCard.toString())
    }

    // EFB-022: Unknown relic id rejected
    val badRelic = JSONObject(RunSnapshotCodec.encode(postCombatMap)).apply {
      getJSONArray("relics").put("fake_nonexistent_relic")
    }
    assertThrows(IllegalArgumentException::class.java) {
      RunSnapshotCodec.decode(badRelic.toString())
    }
  }

  @Test
  fun `EFB-010 corrupt active_run returns Corrupt load result and TitleScreen displays recovery dialog`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, EmberfallDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val repo = EmberfallRepository(db.emberfallDao(), SettingsDataStore(context))
      assertTrue(repo.loadActiveRunResult() is ActiveRunLoadResult.Missing)

      val corruptEntity = ActiveRunEntity(
        id = 1,
        seedLabel = "CORRUPT-1",
        heroClassName = "Ashen Monk",
        act = 1,
        hp = 40,
        maxHp = 60,
        flasks = 3,
        payloadJson = "{not valid json",
        updatedAtMillis = 1234L,
      )
      db.emberfallDao().saveActiveRun(corruptEntity)

      val loadResult = repo.loadActiveRunResult()
      assertTrue("Corrupt snapshot must return ActiveRunLoadResult.Corrupt", loadResult is ActiveRunLoadResult.Corrupt)

      var discarded = false
      composeTestRule.activityRule.scenario.onActivity { activity ->
        activity.setContent {
          EmberfallTheme {
            TitleScreen(
              activeRun = corruptEntity,
              corruptSaveError = (loadResult as ActiveRunLoadResult.Corrupt).reason,
              onDiscardCorruptSave = { discarded = true },
              onNewExpedition = {},
              onContinueRun = {},
              onOpenCompendium = {},
              onOpenSettings = {},
            )
          }
        }
      }
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("corrupt_save_dialog").assertIsDisplayed()
      composeTestRule.onNodeWithTag("discard_corrupt_save_button").assertIsDisplayed().performClick()
      assertTrue(discarded)
    } finally {
      db.close()
    }
  }

  @Test
  fun `EFB-013 masterAudioMuted preserves independent musicEnabled and sfxEnabled settings`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val ds = SettingsDataStore(context)
    ds.setMusicEnabled(true)
    ds.setSfxEnabled(false)
    ds.setMasterAudioMuted(true)

    val muted = ds.settingsFlow.first()
    assertTrue(muted.musicEnabled)
    assertFalse(muted.sfxEnabled)
    assertTrue(muted.masterAudioMuted)
    assertFalse(muted.effectiveAudioOn)

    ds.setMasterAudioMuted(false)
    val unmuted = ds.settingsFlow.first()
    assertTrue("Unmuting master must preserve musicEnabled=true", unmuted.musicEnabled)
    assertFalse("Unmuting master must preserve sfxEnabled=false", unmuted.sfxEnabled)
    assertTrue(unmuted.effectiveAudioOn)

    // Restore default clean state for other tests
    ds.setSfxEnabled(true)
  }

  @Test
  fun `EFB-027 v2 campfire migration handles before action after Rest after Upgrade and after Leave transitions`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = EmberfallViewModel(app)
    val started = GameEngine.startNewRun(HeroClass.ASHEN_MONK, "EFB027-CAMP-V2")
    val campfireNode = started.actMap.nodes.first { it.type == MapNodeType.CAMPFIRE }
    val nextMapNodeId = campfireNode.nextNodeIds.first()

    fun toLegacyV2Json(run: RunState): String = JSONObject(RunSnapshotCodec.encode(run)).apply {
      put("schemaVersion", 2)
      remove("inNonCombatNode")
    }.toString()

    // State 1: v2 snapshot at Campfire BEFORE Rest/Upgrade
    val atCampfireBefore = started.copy(
      hp = 36,
      currentNodeId = campfireNode.id,
      inNonCombatNode = true,
      campfireUsed = false,
      commandLog = listOf("NODE:${campfireNode.id}"),
      actMap = started.actMap.copy(
        nodes = started.actMap.nodes.map { if (it.id == campfireNode.id) it.copy(visited = true) else it }
      ),
    )
    val migratedBefore = RunSnapshotCodec.decode(toLegacyV2Json(atCampfireBefore))
    assertTrue("Before-action Campfire v2 save must keep inNonCombatNode=true", migratedBefore.inNonCombatNode)
    assertFalse(migratedBefore.campfireUsed)
    assertEquals(AppDestination.Campfire, viewModel.resolveDestinationForRun(migratedBefore))
    assertTrue(GameEngine.execute(migratedBefore, GameCommand.CampfireRest).accepted)
    assertFalse(GameEngine.execute(migratedBefore, GameCommand.ChooseNode(nextMapNodeId)).accepted)

    // State 2: v2 snapshot at Campfire AFTER Rest (campfireUsed=true, before LeaveNode)
    val afterRest = GameEngine.execute(atCampfireBefore, GameCommand.CampfireRest).run
    val migratedAfterRest = RunSnapshotCodec.decode(toLegacyV2Json(afterRest))
    assertTrue("After-Rest Campfire v2 save must keep inNonCombatNode=true", migratedAfterRest.inNonCombatNode)
    assertTrue(migratedAfterRest.campfireUsed)
    assertEquals(AppDestination.Campfire, viewModel.resolveDestinationForRun(migratedAfterRest))
    assertFalse(GameEngine.execute(migratedAfterRest, GameCommand.CampfireRest).accepted)
    assertTrue(GameEngine.execute(migratedAfterRest, GameCommand.LeaveNode).accepted)

    // State 3: v2 snapshot at Campfire AFTER Upgrade (campfireUsed=true, before LeaveNode)
    val upgradeUid = atCampfireBefore.masterDeck.first { !it.upgraded }.uid
    val afterUpgrade = GameEngine.execute(atCampfireBefore, GameCommand.CampfireUpgradeCard(upgradeUid)).run
    val migratedAfterUpgrade = RunSnapshotCodec.decode(toLegacyV2Json(afterUpgrade))
    assertTrue("After-Upgrade Campfire v2 save must keep inNonCombatNode=true", migratedAfterUpgrade.inNonCombatNode)
    assertTrue(migratedAfterUpgrade.campfireUsed)
    assertEquals(AppDestination.Campfire, viewModel.resolveDestinationForRun(migratedAfterUpgrade))
    assertFalse(GameEngine.execute(migratedAfterUpgrade, GameCommand.CampfireRest).accepted)
    assertTrue(GameEngine.execute(migratedAfterUpgrade, GameCommand.LeaveNode).accepted)

    // State 4a: v2 snapshot AFTER LeaveNode (still referencing campfireNode, last command LEAVE_NODE)
    val afterLeave = GameEngine.execute(afterRest, GameCommand.LeaveNode).run
    val migratedAfterLeave = RunSnapshotCodec.decode(toLegacyV2Json(afterLeave))
    assertFalse("After-Leave Campfire v2 save must restore with inNonCombatNode=false", migratedAfterLeave.inNonCombatNode)
    assertEquals(AppDestination.ActMap, viewModel.resolveDestinationForRun(migratedAfterLeave))
    assertFalse(GameEngine.execute(migratedAfterLeave, GameCommand.LeaveNode).accepted)
    assertTrue(nextMapNodeId in GameEngine.availableNodeIds(migratedAfterLeave))
    assertTrue(GameEngine.execute(migratedAfterLeave, GameCommand.ChooseNode(nextMapNodeId)).accepted)

    // State 4b: v2 snapshot AFTER transitioning to next map node
    val transitioned = GameEngine.execute(afterLeave, GameCommand.ChooseNode(nextMapNodeId)).run
    val migratedTransitioned = RunSnapshotCodec.decode(toLegacyV2Json(transitioned))
    assertEquals(nextMapNodeId, migratedTransitioned.currentNodeId)
    assertNotEquals(AppDestination.Campfire, viewModel.resolveDestinationForRun(migratedTransitioned))
  }

  @Test
  fun `room migrations v1 to v4 and v2 to v4 preserve active_run run_history and shade_deck data`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val sampleRun = GameEngine.startNewRun(HeroClass.QUARRY_WITCH, "MIGRATE-ACTIVE")
    val sampleV2Json = JSONObject(RunSnapshotCodec.encode(sampleRun)).apply {
      put("schemaVersion", 2)
      remove("inNonCombatNode")
    }.toString()

    // 1. Real v1 SQLite database -> Room v4 (runs MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
    val dbNameV1 = "test_migration_v1_v4_${System.nanoTime()}.db"
    context.deleteDatabase(dbNameV1)
    val helperV1 = FrameworkSQLiteOpenHelperFactory().create(
      SupportSQLiteOpenHelper.Configuration.builder(context)
        .name(dbNameV1)
        .callback(object : SupportSQLiteOpenHelper.Callback(1) {
          override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL(
              """
              CREATE TABLE active_run (
                id INTEGER NOT NULL PRIMARY KEY,
                seedLabel TEXT NOT NULL,
                heroClassName TEXT NOT NULL,
                act INTEGER NOT NULL,
                hp INTEGER NOT NULL,
                maxHp INTEGER NOT NULL,
                flasks INTEGER NOT NULL,
                payloadJson TEXT NOT NULL,
                updatedAtMillis INTEGER NOT NULL
              )
              """.trimIndent()
            )
            db.execSQL(
              """
              CREATE TABLE run_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                seedLabel TEXT NOT NULL,
                heroClassName TEXT NOT NULL,
                actReached INTEGER NOT NULL,
                victory INTEGER NOT NULL,
                terminationReason TEXT NOT NULL,
                enemiesSlain INTEGER NOT NULL,
                turnsTaken INTEGER NOT NULL,
                highestSpectrumHalfSteps INTEGER NOT NULL,
                finishedAtMillis INTEGER NOT NULL
              )
              """.trimIndent()
            )
            db.execSQL(
              "INSERT INTO active_run VALUES (1, 'MIGRATE-ACTIVE', 'Quarry Witch', 1, 56, 56, 3, ?, 1001)",
              arrayOf<Any>(sampleV2Json),
            )
            db.execSQL(
              """
              INSERT INTO run_history (seedLabel, heroClassName, actReached, victory, terminationReason, enemiesSlain, turnsTaken, highestSpectrumHalfSteps, finishedAtMillis)
              VALUES ('LEGACY-V1', 'The Unlit', 1, 0, 'Zero Flasks', 3, 9, 4, 1002)
              """.trimIndent()
            )
          }

          override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        })
        .build()
    )
    helperV1.writableDatabase.close()
    helperV1.close()

    val roomDbV1 = Room.databaseBuilder(context, EmberfallDatabase::class.java, dbNameV1)
      .addMigrations(*EmberfallDatabase.ALL_MIGRATIONS)
      .allowMainThreadQueries()
      .build()
    try {
      val repo = EmberfallRepository(roomDbV1.emberfallDao(), SettingsDataStore(context))
      val loaded = repo.loadActiveRun()
      assertNotNull("Active run must survive v1->v4 migration", loaded)
      assertEquals("MIGRATE-ACTIVE", loaded?.seedLabel)
      val history = roomDbV1.emberfallDao().observeRunHistory().first()
      assertEquals(1, history.size)
      assertEquals("LEGACY-V1", history.first().seedLabel)
      assertFalse(history.first().dreadEndingAchieved)
      assertNull("Newly created shade_deck table should start empty", roomDbV1.emberfallDao().getShadeDeck())
    } finally {
      roomDbV1.close()
      context.deleteDatabase(dbNameV1)
    }

    // 2. Real v2 SQLite database -> Room v4 (runs MIGRATION_2_3, MIGRATION_3_4 preserving shade_deck too)
    val dbNameV2 = "test_migration_v2_v4_${System.nanoTime()}.db"
    context.deleteDatabase(dbNameV2)
    val helperV2 = FrameworkSQLiteOpenHelperFactory().create(
      SupportSQLiteOpenHelper.Configuration.builder(context)
        .name(dbNameV2)
        .callback(object : SupportSQLiteOpenHelper.Callback(2) {
          override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL(
              """
              CREATE TABLE active_run (
                id INTEGER NOT NULL PRIMARY KEY,
                seedLabel TEXT NOT NULL,
                heroClassName TEXT NOT NULL,
                act INTEGER NOT NULL,
                hp INTEGER NOT NULL,
                maxHp INTEGER NOT NULL,
                flasks INTEGER NOT NULL,
                payloadJson TEXT NOT NULL,
                updatedAtMillis INTEGER NOT NULL
              )
              """.trimIndent()
            )
            db.execSQL(
              """
              CREATE TABLE shade_deck (
                id INTEGER NOT NULL PRIMARY KEY,
                heroClassName TEXT NOT NULL,
                seedLabel TEXT NOT NULL,
                cardIdsCsv TEXT NOT NULL,
                recordedAtMillis INTEGER NOT NULL
              )
              """.trimIndent()
            )
            db.execSQL(
              """
              CREATE TABLE run_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                seedLabel TEXT NOT NULL,
                heroClassName TEXT NOT NULL,
                actReached INTEGER NOT NULL,
                victory INTEGER NOT NULL,
                terminationReason TEXT NOT NULL,
                enemiesSlain INTEGER NOT NULL,
                turnsTaken INTEGER NOT NULL,
                highestSpectrumHalfSteps INTEGER NOT NULL,
                finishedAtMillis INTEGER NOT NULL
              )
              """.trimIndent()
            )
            db.execSQL(
              "INSERT INTO active_run VALUES (1, 'MIGRATE-ACTIVE', 'Quarry Witch', 1, 56, 56, 3, ?, 2001)",
              arrayOf<Any>(sampleV2Json),
            )
            db.execSQL(
              "INSERT INTO shade_deck VALUES (1, 'Ashen Monk', 'SHADE-V2', 'monk_ember_palm,monk_bead_strike', 2002)"
            )
            db.execSQL(
              """
              INSERT INTO run_history (seedLabel, heroClassName, actReached, victory, terminationReason, enemiesSlain, turnsTaken, highestSpectrumHalfSteps, finishedAtMillis)
              VALUES ('LEGACY-V2', 'Ashen Monk', 3, 1, 'Ember Throne Reclaimed', 14, 42, 7, 2003)
              """.trimIndent()
            )
          }

          override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        })
        .build()
    )
    helperV2.writableDatabase.close()
    helperV2.close()

    val roomDbV2 = Room.databaseBuilder(context, EmberfallDatabase::class.java, dbNameV2)
      .addMigrations(*EmberfallDatabase.ALL_MIGRATIONS)
      .allowMainThreadQueries()
      .build()
    try {
      val repo = EmberfallRepository(roomDbV2.emberfallDao(), SettingsDataStore(context))
      assertEquals("MIGRATE-ACTIVE", repo.loadActiveRun()?.seedLabel)
      assertEquals(listOf("monk_ember_palm", "monk_bead_strike"), repo.loadShadeDeckIds())
      val history = roomDbV2.emberfallDao().observeRunHistory().first()
      assertEquals(1, history.size)
      assertEquals("LEGACY-V2", history.first().seedLabel)
      assertTrue(history.first().victory)
      assertFalse(history.first().dreadEndingAchieved)
    } finally {
      roomDbV2.close()
      context.deleteDatabase(dbNameV2)
    }
  }

  @Test
  fun `rewards screen renders prominent Skip Card button and combat arena renders block disappearance VFX overlay`() {
    val started = GameEngine.startNewRun(HeroClass.ASHEN_MONK, "REWARD-SKIP-UI")
    val inCombat = GameEngine.execute(started, GameCommand.ChooseNode(GameEngine.availableNodeIds(started).first())).run

    // 1. Verify BoardJuiceFxOverlay displays match burst headline when blocks disappear
    val sampleFx = BoardJuiceFx(
      id = 99L,
      waves = listOf(
        FxWave(
          waveIndex = 1,
          matchedCells = listOf(
            FxCell(4, 1, TileKind.Playable(Hue.EMBER)),
            FxCell(4, 2, TileKind.Playable(Hue.EMBER)),
            FxCell(4, 3, TileKind.Playable(Hue.EMBER)),
          ),
          primaryHue = Hue.EMBER,
          waveDamage = 18,
        )
      ),
      totalDamage = 18,
      cascadeCount = 2,
      primaryHue = Hue.EMBER,
      isPrismBlast = false,
      headline = "CASCADE ×2!",
      subline = "-18 DMG • +3 EMB",
    )
    composeTestRule.activityRule.scenario.onActivity { activity ->
      activity.setContent {
        EmberfallTheme {
          CombatArenaScreen(
            run = inCombat,
            selectedGroup = null,
            boardFeedback = null,
            settings = PlayerSettings(fastAnimations = false),
            onTapBoardCell = { _, _ -> },
            onConfirmSelectedGroup = {},
            onUseChiselWand = { _, _, _ -> },
            onProceedToCast = {},
            onPlayCard = { _, _ -> },
            onUseFlask = {},
            onEndTurn = {},
            onProceedVictory = {},
            onProceedDefeat = {},
            boardJuiceFx = sampleFx,
          )
        }
      }
    }
    composeTestRule.mainClock.autoAdvance = false
    composeTestRule.mainClock.advanceTimeBy(120L)
    composeTestRule.onNodeWithTag("board_juice_fx_overlay").assertIsDisplayed()
    composeTestRule.onNodeWithText("CASCADE ×2!").assertIsDisplayed()
    composeTestRule.mainClock.autoAdvance = true

    // 2. Verify RewardsScreen renders prominent Skip Card button and fires callback cleanly
    val realCards = GameCatalog.starterDeckIds(HeroClass.ASHEN_MONK).distinct().take(3)
    val rewardRun = started.copy(
      growth = CharacterGrowthState(
        socketedSupportIds = listOf("sup_echo_strike"),
        maxSockets = 1,
      ),
      pendingReward = RewardBundle(
        cindersEarned = 28,
        flaskRefill = 0,
        cardChoices = realCards,
        relicRewardId = null,
        cardPicked = false,
        relicPicked = false,
        supportChoices = listOf("sup_prism_forge"),
        artifactChoices = listOf("art_nada_damaru"),
      ),
    )
    var skipped = false
    var replacedSlot = -1
    var boundArtifact: String? = null
    composeTestRule.activityRule.scenario.onActivity { activity ->
      activity.setContent {
        EmberfallTheme {
          RewardsScreen(
            run = rewardRun,
            onPickCard = {},
            onSkipCard = { skipped = true },
            onClaimRelic = {},
            onSkipRelic = {},
            onFinishRewards = {},
            onSocketSupport = { _, slot -> replacedSlot = slot },
            onBindArtifact = { boundArtifact = it },
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("reward_support_sup_prism_forge").performScrollTo().assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag("replace_support_slot_0").assertIsDisplayed().performClick()
    assertEquals("Full support sockets must request an explicit replacement slot", 0, replacedSlot)
    composeTestRule.onNodeWithTag("reward_artifact_art_nada_damaru").performScrollTo().assertIsDisplayed().performClick()
    assertEquals("Artifact reward must invoke bind callback", "art_nada_damaru", boundArtifact)
    composeTestRule.onNodeWithTag("skip_card_button").performScrollTo().assertIsDisplayed().performClick()
    assertTrue("Clicking Skip Card button must invoke onSkipCard", skipped)
  }

  @Test
  fun snapshotCodecAndCreativeUiControlsRoundtripAndInvokeCallbacks() {
    // 1. RunSnapshotCodec lossless roundtrip of arenaTwist, overdriveCharge, bonusMovesGrantedThisTurn, rerollUsed
    val started = GameEngine.startNewRun(HeroClass.ASHEN_MONK, "CODEC-FUN-V2")
    val entered = GameEngine.enterNode(started, GameEngine.availableNodeIds(started).first())
    val c0 = requireNotNull(entered.combat)
    val decoratedRun = entered.copy(
      combat = c0.copy(
        arenaTwist = ArenaTwist.BLOOD_KILN,
        player = c0.player.copy(overdriveCharge = 85),
        turn = c0.turn.copy(bonusMovesGrantedThisTurn = 2),
      ),
      pendingReward = RewardBundle(
        cindersEarned = 25,
        flaskRefill = 1,
        cardChoices = GameCatalog.starterDeckIds(HeroClass.ASHEN_MONK).distinct().take(3),
        relicRewardId = null,
        cardPicked = false,
        relicPicked = false,
        rerollUsed = true,
        supportChoices = listOf("sup_echo_strike"),
        supportPicked = false,
        artifactChoices = listOf("art_rudraksha_mala"),
        artifactPicked = false,
      ),
    )
    val encoded = RunSnapshotCodec.encode(decoratedRun)
    val decoded = RunSnapshotCodec.decode(encoded)
    assertEquals(ArenaTwist.BLOOD_KILN, decoded.combat?.arenaTwist)
    assertEquals(85, decoded.combat?.player?.overdriveCharge)
    assertEquals(2, decoded.combat?.turn?.bonusMovesGrantedThisTurn)
    assertEquals(true, decoded.pendingReward?.rerollUsed)
    assertEquals(listOf("sup_echo_strike"), decoded.pendingReward?.supportChoices)
    assertEquals(false, decoded.pendingReward?.supportPicked)
    assertEquals(listOf("art_rudraksha_mala"), decoded.pendingReward?.artifactChoices)
    assertEquals(false, decoded.pendingReward?.artifactPicked)

    // 2. TitleScreen Quick Dive button fires callback
    var quickDived = false
    composeTestRule.activityRule.scenario.onActivity { activity ->
      activity.setContent {
        EmberfallTheme {
          TitleScreen(
            activeRun = null,
            onNewExpedition = {},
            onQuickDive = { quickDived = true },
            onContinueRun = {},
            onOpenCompendium = {},
            onOpenSettings = {},
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("quick_dive_button").assertIsDisplayed().performClick()
    assertTrue("Quick Dive button must invoke onQuickDive", quickDived)

    // 3. ActMapScreen shows pinned Next Room Crossroads Strip and enters selected room
    var selectedNodeId: String? = null
    composeTestRule.activityRule.scenario.onActivity { activity ->
      activity.setContent {
        EmberfallTheme {
          ActMapScreen(
            run = started,
            onSelectNode = { selectedNodeId = it },
            onExitToTitle = {},
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    val firstAvailable = GameEngine.availableNodeIds(started).first()
    composeTestRule.onNodeWithTag("next_crossroads_strip").assertIsDisplayed()
    composeTestRule.onNodeWithTag("crossroads_node_$firstAvailable").assertIsDisplayed().performClick()
    assertEquals("Crossroads card must enter selected node directly", firstAvailable, selectedNodeId)

    // 4. CombatArenaScreen renders Arena Twist badge and clickable Ignite Overdrive button at 100%
    var overdriveTriggered = false
    val readyCombatRun = entered.copy(
      combat = c0.copy(
        arenaTwist = ArenaTwist.VOLATILE_PRISMS,
        player = c0.player.copy(overdriveCharge = PlayerCombatState.MAX_OVERDRIVE),
      ),
    )
    composeTestRule.activityRule.scenario.onActivity { activity ->
      activity.setContent {
        EmberfallTheme {
          CombatArenaScreen(
            run = readyCombatRun,
            selectedGroup = null,
            boardFeedback = null,
            settings = PlayerSettings(fastAnimations = true),
            onTapBoardCell = { _, _ -> },
            onConfirmSelectedGroup = {},
            onUseChiselWand = { _, _, _ -> },
            onTriggerOverdrive = { overdriveTriggered = true },
            onProceedToCast = {},
            onPlayCard = { _, _ -> },
            onUseFlask = {},
            onEndTurn = {},
            onProceedVictory = {},
            onProceedDefeat = {},
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("arena_twist_badge").assertIsDisplayed()
    composeTestRule.onNodeWithTag("trigger_overdrive_button").assertIsDisplayed().performClick()
    assertTrue("Clicking Ignite Overdrive button must invoke onTriggerOverdrive", overdriveTriggered)

    // 5. RewardsScreen Transmute Draft reroll button fires callback
    var rerolled = false
    val rerollRewardRun = started.copy(
      cinders = 40,
      pendingReward = RewardBundle(
        cindersEarned = 24,
        flaskRefill = 0,
        cardChoices = GameCatalog.starterDeckIds(HeroClass.ASHEN_MONK).distinct().take(3),
        relicRewardId = null,
        cardPicked = false,
        relicPicked = false,
        rerollUsed = false,
      ),
    )
    composeTestRule.activityRule.scenario.onActivity { activity ->
      activity.setContent {
        EmberfallTheme {
          RewardsScreen(
            run = rerollRewardRun,
            onPickCard = {},
            onSkipCard = {},
            onRerollCards = { rerolled = true },
            onClaimRelic = {},
            onSkipRelic = {},
            onFinishRewards = {},
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("reroll_cards_button").performScrollTo().assertIsDisplayed().performClick()
    assertTrue("Clicking Transmute Draft reroll button must invoke onRerollCards", rerolled)

    // 6. CampfireScreen Stoke the Kiln gamble button fires callback
    var gambled = false
    val campRun = started.copy(hp = 50, campfireUsed = false)
    composeTestRule.activityRule.scenario.onActivity { activity ->
      activity.setContent {
        EmberfallTheme {
          CampfireScreen(
            run = campRun,
            onRest = {},
            onUpgradeCard = {},
            onGamble = { gambled = true },
            onLeave = {},
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("campfire_gamble_button").assertIsDisplayed().performClick()
    assertTrue("Clicking Stoke the Kiln button must invoke onGamble", gambled)
  }

  @Test
  fun trikaProgressionMindIllusionCodecAndInlineSpoilsFlowWorkEndToEnd() {
    val started = GameEngine.startNewRun(HeroClass.ASHEN_MONK, "TRIKA-UI-TEST")
    val entered = GameEngine.enterNode(started, GameEngine.availableNodeIds(started).first())
    val c0 = requireNotNull(entered.combat)

    // 1. Lossless RunSnapshotCodec roundtrip of CharacterGrowthState & MindIllusionState
    val enriched = entered.copy(
      growth = CharacterGrowthState(
        level = 3,
        xp = 12,
        xpToNextLevel = 32,
        unspentPassivePoints = 1,
        unlockedPassiveIds = listOf("pass_foundry_1"),
        socketedSupportIds = listOf("sup_echo_strike"),
        tantricArtifactIds = listOf("art_rudraksha_mala", "art_sphatika_mala"),
        maxSockets = 2,
      ),
      combat = c0.copy(
        mindIllusion = MindIllusionState(
          illusion = EmotionalIllusion.BHAYA_DREAD,
          attachment = 5,
          clarity = 2,
          veilLayers = 2,
          shattered = false,
          witnessUsedThisTurn = false,
          detachedBonusSpanda = 4,
        ),
      ),
    )
    val decoded = RunSnapshotCodec.decode(RunSnapshotCodec.encode(enriched))
    assertEquals(enriched.growth, decoded.growth)
    assertEquals(enriched.combat?.mindIllusion, decoded.combat?.mindIllusion)

    // 2. CombatArenaScreen renders Copperplate Wax Seal intents, MindIllusionBar, Sākṣī Witness button,
    //    Spanda × Vimarśa Kiln Ledger, Signature Skill socket plate, and interactive Upāya Circuit dialog
    var witnessed = false
    var unlockedPassive: String? = null
    composeTestRule.activityRule.scenario.onActivity { activity ->
      activity.setContent {
        EmberfallTheme {
          CombatArenaScreen(
            run = enriched,
            selectedGroup = null,
            boardFeedback = null,
            settings = PlayerSettings(fastAnimations = true),
            onTapBoardCell = { _, _ -> },
            onConfirmSelectedGroup = {},
            onUseChiselWand = { _, _, _ -> },
            onWitnessIllusion = { witnessed = true },
            onUnlockPassive = { unlockedPassive = it },
            onProceedToCast = {},
            onPlayCard = { _, _ -> },
            onUseFlask = {},
            onEndTurn = {},
            onProceedVictory = {},
            onProceedDefeat = {},
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onAllNodesWithTag("wax_seal_intent").assertCountEquals(2)
    composeTestRule.onNodeWithTag("mind_illusion_bar").assertIsDisplayed()
    composeTestRule.onNodeWithTag("kiln_ledger_hud").assertIsDisplayed()
    composeTestRule.onNodeWithTag("signature_skill_plate").assertIsDisplayed()
    composeTestRule.onNodeWithTag("witness_illusion_button").assertIsDisplayed().performClick()
    assertTrue("Tapping Sākṣī Witness button must invoke onWitnessIllusion", witnessed)

    // Open Upāya Circuit modal and etch a passive node
    composeTestRule.onNodeWithTag("open_upaya_circuit_button").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("brass_circuit_picker").assertIsDisplayed()
    composeTestRule.onNodeWithTag("passive_node_pass_foundry_2").performClick()
    assertEquals("pass_foundry_2", unlockedPassive)

    // 3. Inline Spoils & Single-Tap Next-Room Overlay on COMBAT_WON
    val currentNode = enriched.actMap.nodes.first { it.id == enriched.currentNodeId }
    val nextNodeId = currentNode.nextNodeIds.first()
    val wonRun = enriched.copy(
      combat = requireNotNull(enriched.combat).copy(
        turn = c0.turn.copy(phase = TurnPhase.COMBAT_WON),
      ),
      pendingReward = RewardBundle(
        cindersEarned = 38,
        cardChoices = GameCatalog.starterDeckIds(HeroClass.ASHEN_MONK).distinct().take(3),
        cardPicked = true,
        relicPicked = true,
        xpEarned = 45,
        leveledUp = true,
        supportChoices = listOf("sup_prism_forge"),
        supportPicked = false,
        artifactChoices = listOf("art_nada_damaru"),
        artifactPicked = false,
      ),
    )
    var advancedNodeId: String? = null
    composeTestRule.activityRule.scenario.onActivity { activity ->
      activity.setContent {
        EmberfallTheme {
          CombatArenaScreen(
            run = wonRun,
            selectedGroup = null,
            boardFeedback = null,
            settings = PlayerSettings(fastAnimations = true),
            onTapBoardCell = { _, _ -> },
            onConfirmSelectedGroup = {},
            onUseChiselWand = { _, _, _ -> },
            onProceedToCast = {},
            onPlayCard = { _, _ -> },
            onUseFlask = {},
            onEndTurn = {},
            onProceedVictory = {},
            onProceedDefeat = {},
            onAdvanceInlineToNode = { advancedNodeId = it },
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("inline_spoils_overlay").assertIsDisplayed()
    composeTestRule.onNodeWithTag("inline_socket_support_sup_prism_forge").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("inline_bind_artifact_art_nada_damaru").performScrollTo().assertIsDisplayed()
    composeTestRule.onAllNodesWithTag("inline_next_node_$nextNodeId").assertCountEquals(0)

    // The next-room action must remain unavailable until every reward channel is resolved.
    val resolvedRun = wonRun.copy(
      pendingReward = requireNotNull(wonRun.pendingReward).copy(
        supportPicked = true,
        artifactPicked = true,
      ),
    )
    composeTestRule.activityRule.scenario.onActivity { activity ->
      activity.setContent {
        EmberfallTheme {
          CombatArenaScreen(
            run = resolvedRun,
            selectedGroup = null,
            boardFeedback = null,
            settings = PlayerSettings(fastAnimations = true),
            onTapBoardCell = { _, _ -> },
            onConfirmSelectedGroup = {},
            onUseChiselWand = { _, _, _ -> },
            onProceedToCast = {},
            onPlayCard = { _, _ -> },
            onUseFlask = {},
            onEndTurn = {},
            onProceedVictory = {},
            onProceedDefeat = {},
            onAdvanceInlineToNode = { advancedNodeId = it },
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("inline_next_node_$nextNodeId").assertIsDisplayed().performClick()
    assertEquals("Single-tap next-room chip must invoke onAdvanceInlineToNode after rewards resolve", nextNodeId, advancedNodeId)
  }

  @Test
  fun lyriaSoundtrackStudioAndTitlePillRenderAndInvokeCallbacks() {
    var openedStudio = false
    val readyMeta = CachedTrackMeta(
      slot = SoundtrackSlot.EXPLORATION,
      modelId = SoundtrackModelTier.PRO_FULL_LOOP.modelId,
      presetId = SoundtrackPreset.INTERLOCKING_PULSE.id,
      customDirection = "",
      fileName = "track_exploration.mp3",
      mimeType = "audio/mpeg",
      byteSize = 192_000L,
      generatedAtMs = 1_700_000_000L,
    )
    val studioState = SoundtrackUiState(
      exploration = SoundtrackSlotStatus.Ready(readyMeta, "/tmp/track_exploration.mp3"),
      combat = SoundtrackSlotStatus.Error("Network timeout during Lyria generation."),
      selectedTier = SoundtrackModelTier.PRO_FULL_LOOP,
      selectedPreset = SoundtrackPreset.INTERLOCKING_PULSE,
      activeMusicMode = MusicMode.EXPLORATION,
    )

    // 1. Title screen renders soundtrack pill and routes tap to studio
    composeTestRule.activityRule.scenario.onActivity { activity ->
      activity.setContent {
        EmberfallTheme {
          TitleScreen(
            activeRun = null,
            audioEnabled = true,
            soundtrackState = studioState,
            onOpenSoundtrackStudio = { openedStudio = true },
            onNewExpedition = {},
            onContinueRun = {},
            onOpenCompendium = {},
            onOpenSettings = {},
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("title_soundtrack_pill").assertIsDisplayed().performClick()
    assertTrue("Tapping title_soundtrack_pill must invoke onOpenSoundtrackStudio", openedStudio)

    // 2. Settings screen embeds SoundtrackStudioPanel with working model, preset, audition, compose & retry handlers
    var selectedTier: SoundtrackModelTier? = null
    var selectedPreset: SoundtrackPreset? = null
    var auditionedMode: MusicMode? = null
    var composedSlot: SoundtrackSlot? = null
    var retried = false
    var cleared = false

    composeTestRule.activityRule.scenario.onActivity { activity ->
      activity.setContent {
        EmberfallTheme {
          SettingsScreen(
            settings = PlayerSettings(musicEnabled = true, sfxEnabled = true),
            soundtrackState = studioState,
            onSelectSoundtrackTier = { selectedTier = it },
            onSelectSoundtrackPreset = { selectedPreset = it },
            onAuditionSoundtrackMode = { auditionedMode = it },
            onComposeSoundtrackSlot = { composedSlot = it },
            onRetryMissingSoundtracks = { retried = true },
            onClearSoundtrackCache = { cleared = true },
            onToggleColorblind = {},
            onToggleHints = {},
            onToggleFastAnimations = {},
            onToggleHaptics = {},
            onToggleOneTap = {},
            onBack = {},
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("soundtrack_studio_panel").assertIsDisplayed()
    composeTestRule.onNodeWithTag("soundtrack_error_banner").assertIsDisplayed()
    composeTestRule.onNodeWithTag("soundtrack_retry_button").performClick()
    assertTrue("Retry button must invoke onRetryMissingSoundtracks", retried)

    composeTestRule.onNodeWithTag("soundtrack_audition_combat").performScrollTo().performClick()
    assertEquals(MusicMode.COMBAT, auditionedMode)

    composeTestRule.onNodeWithTag("soundtrack_compose_exploration").performScrollTo().performClick()
    assertEquals(SoundtrackSlot.EXPLORATION, composedSlot)

    composeTestRule.onNodeWithTag("soundtrack_tier_clip_preview_30s").performScrollTo().performClick()
    assertEquals(SoundtrackModelTier.CLIP_PREVIEW_30S, selectedTier)

    composeTestRule.onNodeWithTag("soundtrack_preset_plucked_stillness").performScrollTo().performClick()
    assertEquals(SoundtrackPreset.PLUCKED_STILLNESS, selectedPreset)

    composeTestRule.onNodeWithTag("soundtrack_clear_cache_button").performScrollTo().performClick()
    assertTrue("Clear cache button must invoke onClearSoundtrackCache", cleared)
  }

  @Test
  fun `P0_3 EmberfallRepository loadActiveRunResult rethrows CancellationException instead of reporting Corrupt`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val cancellingDao = object : EmberfallDao {
      override fun observeActiveRun() = kotlinx.coroutines.flow.flowOf<ActiveRunEntity?>(null)
      override suspend fun getActiveRun(): ActiveRunEntity = throw kotlinx.coroutines.CancellationException("simulated job cancel")
      override suspend fun saveActiveRun(entity: ActiveRunEntity) = Unit
      override suspend fun clearActiveRun() = Unit
      override fun observeRunHistory() = kotlinx.coroutines.flow.flowOf<List<RunHistoryEntity>>(emptyList())
      override suspend fun insertRunHistory(entry: RunHistoryEntity) = Unit
      override suspend fun getShadeDeck(): ShadeDeckEntity? = null
      override suspend fun saveShadeDeck(entity: ShadeDeckEntity) = Unit
      override suspend fun countMatchingHistory(
        seedLabel: String,
        heroClassName: String,
        actReached: Int,
        turnsTaken: Int,
        enemiesSlain: Int,
        reason: String,
      ): Int = 0
    }
    val repo = EmberfallRepository(cancellingDao, SettingsDataStore(context))

    var propagatedCancellation = false
    runBlocking {
      try {
        repo.loadActiveRunResult()
      } catch (_: kotlinx.coroutines.CancellationException) {
        propagatedCancellation = true
      }
    }
    assertTrue("CancellationException MUST propagate from loadActiveRunResult()", propagatedCancellation)
  }

  @Test
  fun `P0_2 One-Tap Quick-Swap setting fires valid Match-3 swap on single tap when enabled and selects on first tap when disabled`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val settingsStore = SettingsDataStore(app)
    val vm = EmberfallViewModel(app)

    fun pumpMain(condition: () -> Boolean) {
      var tries = 0
      while (!condition() && tries < 80) {
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        Thread.sleep(15)
        tries++
      }
      org.robolectric.shadows.ShadowLooper.idleMainLooper()
    }

    // 1. Disable One-Tap Quick-Swap -> single tap on a valid swap tile ONLY selects it
    runBlocking(kotlinx.coroutines.Dispatchers.IO) {
      settingsStore.setOneTapBoardFire(false)
    }
    vm.quickDive(HeroClass.ASHEN_MONK)
    pumpMain { vm.runState.value?.combat != null && !vm.settings.value.oneTapBoardFire }

    val runOff = requireNotNull(vm.runState.value) { "quickDive must initialize active combat run" }
    val combatOff = requireNotNull(runOff.combat)
    assertFalse("Settings Flow must reflect oneTapBoardFire = false", vm.settings.value.oneTapBoardFire)
    val swapOff = BoardEngine.findValidSwaps(combatOff.board, combatOff.spectrum).first()
    val tapsBeforeOff = combatOff.tapsRemaining

    vm.onBoardCellTapped(swapOff.from.row, swapOff.from.col)
    assertEquals(
      "With One-Tap Quick-Swap OFF, first tap must only select the coordinate",
      swapOff.from,
      vm.selectedSwapCoord.value,
    )
    assertEquals(
      "With One-Tap Quick-Swap OFF, first tap must not spend a move",
      tapsBeforeOff,
      vm.runState.value?.combat?.tapsRemaining,
    )
    // Second tap on adjacent partner executes the swap
    vm.onBoardCellTapped(swapOff.to.row, swapOff.to.col)
    assertNull("Swap completion must clear selectedSwapCoord", vm.selectedSwapCoord.value)
    assertNotEquals("Second adjacent tap must mutate board/enemy state", runOff, vm.runState.value)

    // 2. Enable One-Tap Quick-Swap -> single tap on a valid swap tile IMMEDIATELY executes the swap
    runBlocking(kotlinx.coroutines.Dispatchers.IO) {
      settingsStore.setOneTapBoardFire(true)
    }
    vm.quickDive(HeroClass.ASHEN_MONK)
    pumpMain { vm.settings.value.oneTapBoardFire && vm.runState.value?.combat != null && vm.runState.value?.seedLabel != runOff.seedLabel }

    val runOn = requireNotNull(vm.runState.value)
    val combatOn = requireNotNull(runOn.combat)
    assertTrue("Settings Flow must reflect oneTapBoardFire = true", vm.settings.value.oneTapBoardFire)
    val swapOn = BoardEngine.findValidSwaps(combatOn.board, combatOn.spectrum).first()

    vm.onBoardCellTapped(swapOn.from.row, swapOn.from.col)
    assertNull("With One-Tap Quick-Swap ON, single tap must execute immediately without leaving selection pending", vm.selectedSwapCoord.value)
    assertNotEquals("With One-Tap Quick-Swap ON, single tap must advance run state via SwapCells", runOn, vm.runState.value)
  }

  @Test
  fun `unresolved support and artifact rewards survive Room snapshot save and reload`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, EmberfallDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val repository = EmberfallRepository(db.emberfallDao(), SettingsDataStore(context))
      val base = GameEngine.startNewRun(HeroClass.ASHEN_MONK, "ROOM-PENDING-REWARDS")
      val pending = base.copy(
        growth = CharacterGrowthState(
          level = 3,
          xp = 7,
          xpToNextLevel = 30,
          socketedSupportIds = listOf("sup_echo_strike", "sup_chain_cascade"),
          maxSockets = 2,
        ),
        pendingReward = RewardBundle(
          cindersEarned = 24,
          cardChoices = listOf(GameCatalog.starterDeckIds(HeroClass.ASHEN_MONK).first()),
          relicRewardId = "relic_cinder_tongs",
          supportChoices = listOf("sup_prism_forge"),
          supportPicked = false,
          artifactChoices = listOf("art_rudraksha_mala"),
          artifactPicked = false,
        ),
      )
      repository.saveActiveRun(pending)
      val loaded = repository.loadActiveRunResult()
      assertTrue("Pending reward snapshot must load successfully", loaded is ActiveRunLoadResult.Loaded)
      assertEquals(pending, (loaded as ActiveRunLoadResult.Loaded).run)
      assertFalse(loaded.run.pendingReward?.allChoicesResolved ?: true)
    } finally {
      db.close()
    }
  }
  @Test
  fun `P0_6 EmberfallViewModel persists rapid sequential player actions in exact FIFO order across reload`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = EmberfallViewModel(app)

    fun pumpMain(condition: () -> Boolean) {
      var tries = 0
      while (!condition() && tries < 80) {
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        Thread.sleep(15)
        tries++
      }
      org.robolectric.shadows.ShadowLooper.idleMainLooper()
    }

    vm.quickDive(HeroClass.ASHEN_MONK)
    pumpMain { vm.runState.value?.combat != null }

    val initial = requireNotNull(vm.runState.value) { "quickDive must populate combat state" }
    val c0 = requireNotNull(initial.combat)
    val swap = BoardEngine.findValidSwaps(c0.board, c0.spectrum).first()

    // Fire rapid sequential player mutations back-to-back without waiting between calls:
    // 1) Witness Mind Illusion  2) Execute Match-3 Swap  3) Proceed to Cast  4) End Turn
    vm.witnessIllusion()
    vm.onBoardSwap(swap.from.row, swap.from.col, swap.to.row, swap.to.col)
    if (vm.runState.value?.combat?.phase == TurnPhase.WEAVE) {
      vm.proceedToCast()
    }
    if (vm.runState.value?.combat?.phase == TurnPhase.CAST) {
      vm.endTurn()
    }

    val expectedFinal = requireNotNull(vm.runState.value)
    var flushed = false
    val flushJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
      vm.flushPendingPersistence()
      flushed = true
    }
    pumpMain { flushed }
    flushJob.cancel()
    assertTrue("Pending FIFO persistence queue must flush cleanly", flushed)

    // Recreate a fresh ViewModel (simulating process death & relaunch) and verify exact persisted state restored
    val reloadedVm = EmberfallViewModel(app)
    reloadedVm.continueSavedRun()
    pumpMain { reloadedVm.runState.value != null }
    val actualReloaded = requireNotNull(reloadedVm.runState.value) { "Reloaded RunState must not be null" }

    // RunSnapshotCodec intentionally omits transient UI-only event log / scoring breakdown overlays;
    // every persisted gameplay, RNG, board, combat, mind-illusion, progression, and command-log field must match bit-for-bit.
    val expectedPersisted = expectedFinal.copy(
      combat = expectedFinal.combat?.copy(lastEvents = emptyList(), lastScoringBreakdown = null),
    )
    assertEquals(
      "Reloaded RunState after rapid sequential actions must match exact latest committed persisted state",
      expectedPersisted,
      actualReloaded,
    )
    assertEquals(expectedFinal.commandLog, actualReloaded.commandLog)
    assertEquals(expectedFinal.hp, actualReloaded.hp)
    assertEquals(expectedFinal.stats, actualReloaded.stats)
  }

  @Test
  fun `P0-UI-1 combat arena keeps opponent bar compact and dedicates dominant viewport to 7x7 puzzle board`() {
    val started = GameEngine.startNewRun(HeroClass.ASHEN_MONK, "UI-VIEWPORT-BUDGET", dreadLevel = 3)
    val entered = GameEngine.enterNode(started, GameEngine.availableNodeIds(started).first())
    val c0 = requireNotNull(entered.combat)

    // Worst-case HUD density: Arena Twist + Dread 3 + Mind Illusion + 100% Overdrive + Artifacts + Passive Points
    val denseRun = entered.copy(
      growth = CharacterGrowthState(
        level = 4,
        xp = 18,
        xpToNextLevel = 42,
        unspentPassivePoints = 2,
        unlockedPassiveIds = listOf("pass_foundry_1"),
        socketedSupportIds = listOf("sup_echo_strike", "sup_prism_forge"),
        tantricArtifactIds = listOf("art_rudraksha_mala", "art_sphatika_mala"),
        maxSockets = 2,
      ),
      combat = c0.copy(
        arenaTwist = ArenaTwist.VOLATILE_PRISMS,
        player = c0.player.copy(overdriveCharge = PlayerCombatState.MAX_OVERDRIVE),
        mindIllusion = MindIllusionState(
          illusion = EmotionalIllusion.BHAYA_DREAD,
          attachment = 6,
          clarity = 3,
          veilLayers = 2,
          shattered = false,
          witnessUsedThisTurn = false,
          detachedBonusSpanda = 4,
        ),
      ),
    )

    composeTestRule.activityRule.scenario.onActivity { activity ->
      activity.setContent {
        EmberfallTheme {
          CombatArenaScreen(
            run = denseRun,
            selectedGroup = null,
            boardFeedback = null,
            settings = PlayerSettings(fastAnimations = true),
            onTapBoardCell = { _, _ -> },
            onConfirmSelectedGroup = {},
            onUseChiselWand = { _, _, _ -> },
            onProceedToCast = {},
            onPlayCard = { _, _ -> },
            onUseFlask = {},
            onEndTurn = {},
            onProceedVictory = {},
            onProceedDefeat = {},
          )
        }
      }
    }
    composeTestRule.waitForIdle()

    val density = composeTestRule.density.density
    val enemyHeaderBounds = composeTestRule.onNodeWithTag("combat_enemy_header").fetchSemanticsNode().boundsInRoot
    val boardBounds = composeTestRule.onNodeWithTag("combat_board_grid").fetchSemanticsNode().boundsInRoot
    val tileBounds = composeTestRule.onNodeWithTag("board_cell_0_0").fetchSemanticsNode().boundsInRoot

    val enemyHeaderHeightDp = enemyHeaderBounds.height / density
    val boardWidthDp = boardBounds.width / density
    val boardHeightDp = boardBounds.height / density
    val tileWidthDp = tileBounds.width / density
    val tileHeightDp = tileBounds.height / density

    assertTrue(
      "Opponent header bar must stay compact (<= 95dp), was ${enemyHeaderHeightDp}dp",
      enemyHeaderHeightDp <= 95f,
    )
    assertTrue(
      "7x7 board width must fill dominant phone width (>= 350dp), was ${boardWidthDp}dp",
      boardWidthDp >= 350f,
    )
    assertTrue(
      "7x7 board height must fill dominant vertical arena (>= 350dp), was ${boardHeightDp}dp",
      boardHeightDp >= 350f,
    )
    assertTrue(
      "Individual puzzle tiles must be large and legible (>= 44dp), was ${tileWidthDp}x${tileHeightDp}dp",
      tileWidthDp >= 44f && tileHeightDp >= 44f,
    )

    // Verify tapping the compact Kiln Ledger pill opens the full Prakāśa Kiln Ledger dialog
    composeTestRule.onNodeWithTag("kiln_ledger_hud").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Prakāśa Kiln Ledger & Artifacts").assertIsDisplayed()
  }

  @Test
  fun `P1-UI-2 act map dedicates dominant canvas to illustrated journey with cartographers key and bottom crossroads`() {
    val started = GameEngine.startNewRun(HeroClass.QUARRY_WITCH, "UI-MAP-JOURNEY", dreadLevel = 2)

    composeTestRule.activityRule.scenario.onActivity { activity ->
      activity.setContent {
        EmberfallTheme {
          ActMapScreen(
            run = started,
            onSelectNode = {},
            onExitToTitle = {},
          )
        }
      }
    }
    composeTestRule.waitForIdle()

    val density = composeTestRule.density.density
    val headerBounds = composeTestRule.onNodeWithTag("act_map_header").fetchSemanticsNode().boundsInRoot
    val canvasBounds = composeTestRule.onNodeWithTag("act_map_canvas").fetchSemanticsNode().boundsInRoot

    val headerHeightDp = headerBounds.height / density
    val canvasHeightDp = canvasBounds.height / density

    assertTrue(
      "Act Map floating header must stay compact (<= 110dp), was ${headerHeightDp}dp",
      headerHeightDp <= 110f,
    )
    assertTrue(
      "Illustrated world map canvas must occupy dominant viewport (>= 640dp on 915dp phone), was ${canvasHeightDp}dp",
      canvasHeightDp >= 640f,
    )

    // Verify docked bottom crossroads bar & Cartographer's Key modal
    composeTestRule.onNodeWithTag("next_crossroads_strip").assertIsDisplayed()
    composeTestRule.onNodeWithTag("map_key_button").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Cartographer's Key • ${started.heroClass.displayName}").assertIsDisplayed()
    composeTestRule.onNodeWithText("Return to Map").performClick()
    composeTestRule.waitForIdle()
  }
}
