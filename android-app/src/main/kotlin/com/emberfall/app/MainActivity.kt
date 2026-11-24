package com.emberfall.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emberfall.board.*
import com.emberfall.cards.*
import com.emberfall.combat.*
import com.emberfall.content.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EmberfallRoot() }
    }
}

private data class GameSession(
    val classId: PlayerClassId,
    val encounterId: String,
    val battle: CardBattleState,
    val engine: CardEngine,
    val deckProfile: DeckHueProfile
)

@Composable
private fun EmberfallRoot() {
    var session by remember { mutableStateOf<GameSession?>(null) }
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFFC59B55),
        secondary = Color(0xFF8CABB1),
        background = Color(0xFF111317),
        surface = Color(0xFF1B1E23),
        onSurface = Color(0xFFE8E0D2)
    )) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF111317)) {
            val active = session
            if (active == null) {
                ClassSelectScreen(onStart = { session = newSession(it) })
            } else {
                CombatScreen(active, onSessionChange = { session = it }, onExit = { session = null })
            }
        }
    }
}

private fun newSession(classId: PlayerClassId): GameSession {
    val seed = 0x454D42455246414CL
    val deck = PlayerClassCatalog.starterDeck(classId)
    val counts = deck.mapNotNull { ClassCardCatalog.cards[it]?.frameHue }.groupingBy { it }.eachCount()
    val profile = DeckHueProfile(deck.size, counts)
    val board = BoardGenerator().generate(seed, profile).board
    val encounter = EncounterCatalog.get("cinderling")
    val combat = CombatState.start(board, encounter.enemy, runSeed = seed)
    val engine = CardEngine(ClassCardCatalog.cards)
    val initial = PlayerClassCatalog.startBattle(combat, classId, seed)
    val started = (engine.transition(initial, CardBattleCommand.BeginTurn) as CardBattleResult.Accepted).state
    return GameSession(classId, encounter.id, started, engine, profile)
}

@Composable
private fun ClassSelectScreen(onStart: (PlayerClassId) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("EMBERFALL", color = Color(0xFFC59B55), fontSize = 14.sp, letterSpacing = 4.sp, fontWeight = FontWeight.Bold)
        Text("Choose your burden.", color = Color(0xFFF0E8D8), fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Text("Three paths into the Cairn. Each begins with a different deck and a different promise.",
            color = Color(0xFFAAA49A), fontSize = 14.sp)
        PlayerClassCatalog.classes.values.sortedBy { it.id.ordinal }.forEach { profile ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF20242A)), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(profile.displayName.uppercase(), color = Color(0xFFE5D2AC), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("${profile.firstHue.name} / ${profile.secondHue.name}", color = Color(0xFFB9B0A0), fontSize = 12.sp)
                    Text(profile.fantasy, color = Color(0xFFD0C9BD), fontSize = 14.sp)
                    Text("${profile.signatureRelic.name} — ${relicDescription(profile.signatureRelic.rule)}",
                        color = Color(0xFFB99A66), fontSize = 12.sp)
                    Button(onClick = { onStart(profile.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text("ENTER THE CAIRN")
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text("A deterministic combat prototype · local session only", color = Color(0xFF77736D), fontSize = 11.sp)
    }
}

@Composable
private fun CombatScreen(session: GameSession, onSessionChange: (GameSession) -> Unit, onExit: () -> Unit) {
    val battle = session.battle
    val combat = battle.combat
    val enemy = EncounterCatalog.get(session.encounterId)
    var feedback by remember { mutableStateOf("Pop connected groups to earn Ink.") }
    var selectedCardId by remember { mutableStateOf<Long?>(null) }
    var chiselMode by remember { mutableStateOf(false) }
    var chiselHue by remember { mutableStateOf(Hue.TIDE) }

    fun apply(command: CardBattleCommand): Boolean {
        return when (val result = session.engine.transition(battle, command)) {
            is CardBattleResult.Accepted -> {
                onSessionChange(session.copy(battle = result.state))
                feedback = result.events.filterIsInstance<CardEvent.RelicTriggered>()
                    .joinToString(" · ") { "${it.relicId.removePrefix("relic_")} +${it.amount} ${it.resource}" }
                    .ifBlank { "Action resolved." }
                true
            }
            is CardBattleResult.Rejected -> {
                feedback = when (result.reason) {
                    CardRejection.INVALID_TARGET -> "Choose a valid tile target."
                    CardRejection.UNAFFORDABLE -> "Not enough Ink or Spark."
                    CardRejection.WRONG_PHASE -> "That action is not available now."
                    CardRejection.EFFECT_FAILED -> "The effect could not resolve; no state was committed."
                    else -> "Action rejected: ${result.reason.name.lowercase().replace('_', ' ')}."
                }
                false
            }
        }
    }

    fun chooseCard(instanceId: Long) {
        if (combat.phase != CombatPhase.CAST) {
            feedback = "Cards are played during Cast."
            return
        }
        if (apply(CardBattleCommand.Play(instanceId))) {
            selectedCardId = null
        } else if (feedback == "Choose a valid tile target.") {
            selectedCardId = instanceId
            feedback = "Tap a board tile to target this card."
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("EMBERFALL", color = Color(0xFFC59B55), fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Text(session.classId.name.replace('_', ' '), color = Color(0xFFB9A77F), fontSize = 11.sp)
            TextButton(onClick = onExit) { Text("EXIT", color = Color(0xFFB8B0A4)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("VIGIL", "${combat.player.vigil}/70")
            Stat("BLOCK", combat.player.block.toString())
            Stat("SPARK", combat.player.spark.toString())
            Stat("TAPS", combat.tapsRemaining.toString())
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Hue.entries.forEach { hue ->
                Text("${hueGlyph(hue)} ${combat.player.immutableInk.getValue(hue)}",
                    color = hueColor(hue), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF202329))) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(enemy.displayName.uppercase(), color = Color(0xFFE1D3B8), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("${combat.enemy.vigor}/${combat.enemyDefinition.maxVigor}", color = Color(0xFFCE8A77), fontSize = 13.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    combat.nextIntents.forEachIndexed { index, intent ->
                        Column(Modifier.weight(1f)) {
                            Text(if (index == 0) "NEXT" else "AFTER", color = Color(0xFF8D8982), fontSize = 9.sp, letterSpacing = 1.sp)
                            Text(intentLabel(intent), color = Color(0xFFD7C9AF), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(phaseLabel(combat.phase), color = Color(0xFFC59B55), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text("TURN ${combat.turn + 1}", color = Color(0xFF8F8980), fontSize = 11.sp)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxWidth().height(300.dp),
            userScrollEnabled = false
        ) {
            items(49) { index ->
                val coord = Coord(index % 7, index / 7)
                val cell = combat.board[coord]
                Surface(
                    onClick = {
                        when {
                            chiselMode -> {
                                if (apply(CardBattleCommand.UseSignatureRecolor(coord, chiselHue))) chiselMode = false
                            }
                            combat.phase == CombatPhase.WEAVE -> apply(CardBattleCommand.Tap(coord))
                            combat.phase == CombatPhase.CAST && selectedCardId != null -> {
                                if (apply(CardBattleCommand.Play(selectedCardId!!, target = coord))) selectedCardId = null
                            }
                        }
                    },
                    modifier = Modifier.padding(2.dp).aspectRatio(1f)
                        .semantics { contentDescription = tileDescription(coord, cell) },
                    shape = RoundedCornerShape(5.dp),
                    color = tileColor(cell)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(tileGlyph(cell), color = tileForeground(cell), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Text(feedback, color = Color(0xFFB8AA91), fontSize = 11.sp, minLines = 1)
        if (combat.phase == CombatPhase.CAST) {
            if (chiselMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("REPAINT:", color = Color(0xFFAAA49A), fontSize = 10.sp)
                    Hue.entries.forEach { hue ->
                        TextButton(onClick = { chiselHue = hue }) {
                            Text(hue.name.take(1), color = if (chiselHue == hue) Color.White else hueColor(hue))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if ("relic_chisel_wand" in battle.equippedRelicIds && "relic_chisel_wand" !in battle.usedRelicIds) {
                    TextButton(onClick = { chiselMode = true; selectedCardId = null }) {
                        Text("CHISEL WAND", color = Color(0xFFC59B55), fontSize = 10.sp)
                    }
                } else Spacer(Modifier.width(1.dp))
                TextButton(onClick = { selectedCardId = null; chiselMode = false; apply(CardBattleCommand.EndCast) }) {
                    Text("END CAST →", color = Color(0xFFE0C38E), fontWeight = FontWeight.Bold)
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                battle.hand.forEach { instance ->
                    val definition = ClassCardCatalog.cards[instance.definitionId] ?: return@forEach
                    Card(
                        onClick = { chooseCard(instance.instanceId) },
                        modifier = Modifier.width(116.dp).height(88.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF282C32)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Text(definition.name, color = hueColor(definition.frameHue), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(costLabel(definition.cost), color = Color(0xFFD0C7B8), fontSize = 10.sp)
                            Text(definition.type.name, color = Color(0xFF89847C), fontSize = 8.sp)
                        }
                    }
                }
            }
        } else if (combat.phase !in setOf(CombatPhase.VICTORY, CombatPhase.DEFEAT)) {
            PhaseAction(combat.phase) {
                when (combat.phase) {
                    CombatPhase.WEAVE -> apply(CardBattleCommand.EndWeave)
                    CombatPhase.WALL -> {
                        val rows = List(combat.wallRisesNextTurn) { index -> nextIncoming(session, index.toLong()) }
                        apply(CardBattleCommand.ResolveWall(rows))
                    }
                    CombatPhase.ENEMY -> apply(CardBattleCommand.ResolveEnemy)
                    CombatPhase.CLEANUP -> {
                        val finish = session.engine.transition(battle, CardBattleCommand.FinishCleanup)
                        if (finish is CardBattleResult.Accepted) {
                            val begin = session.engine.transition(finish.state, CardBattleCommand.BeginTurn)
                            if (begin is CardBattleResult.Accepted) onSessionChange(session.copy(battle = begin.state))
                            else feedback = "Could not begin the next turn."
                        } else feedback = "Cleanup rejected."
                    }
                    CombatPhase.DRAW -> apply(CardBattleCommand.BeginTurn)
                    else -> Unit
                }
            }
        }
        if (combat.phase == CombatPhase.VICTORY || combat.phase == CombatPhase.DEFEAT) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF282C32))
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(if (combat.phase == CombatPhase.VICTORY) "ENEMY DEFEATED" else "THE LIGHT FADES",
                        color = Color(0xFFE0C38E), fontWeight = FontWeight.Bold)
                    Text("This combat slice is complete. Run map and rewards are the next integration milestone.",
                        color = Color(0xFFB8B0A4), fontSize = 11.sp)
                    TextButton(onClick = onExit) { Text("RETURN TO CLASS SELECT") }
                }
            }
        }
    }
}

@Composable
private fun PhaseAction(phase: CombatPhase, onAction: () -> Unit) {
    val label = when (phase) {
        CombatPhase.WEAVE -> "END WEAVE"
        CombatPhase.WALL -> "RAISE THE WALL"
        CombatPhase.ENEMY -> "RESOLVE ENEMY INTENT"
        CombatPhase.CLEANUP -> "NEXT TURN"
        CombatPhase.DRAW -> "DRAW HAND"
        else -> "CONTINUE"
    }
    Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(label) }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, color = Color(0xFF88837B), fontSize = 9.sp, letterSpacing = 1.sp)
        Text(value, color = Color(0xFFE2D9C9), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun nextIncoming(session: GameSession, ordinal: Long): List<Cell?> {
    val weights = session.deckProfile.weights()
    val rng = DeterministicRng(session.battle.runSeed xor (session.battle.combat.turn.toLong() shl 32) xor ordinal xor 0x57414C4CL)
    return List(7) {
        val roll = rng.nextInt(1_000_000)
        var total = 0L
        var chosen = Hue.VIOLET
        for (hue in Hue.entries) {
            total += weights.getValue(hue)
            if (roll < total) { chosen = hue; break }
        }
        Cell.hue(chosen)
    }
}

private fun phaseLabel(phase: CombatPhase) = when (phase) {
    CombatPhase.DRAW -> "DRAW"
    CombatPhase.WEAVE -> "WEAVE · 3 TAPS"
    CombatPhase.CAST -> "CAST"
    CombatPhase.WALL -> "THE WALL RISES"
    CombatPhase.ENEMY -> "ENEMY ACTS"
    CombatPhase.CLEANUP -> "CLEANUP"
    CombatPhase.VICTORY -> "VICTORY"
    CombatPhase.DEFEAT -> "DEFEAT"
}

private fun intentLabel(intent: EnemyIntent): String = when (intent) {
    is EnemyIntent.Strike -> "STRIKE · ${intent.damage}"
    EnemyIntent.AshRow -> "ASH ROW"
    is EnemyIntent.StoneSeed -> "STONE · COL ${intent.column + 1}"
    is EnemyIntent.Stain -> "STAIN · ${intent.hue.name}"
    EnemyIntent.Fog -> "FOG"
    is EnemyIntent.Seal -> "SEAL · COL ${intent.column + 1}"
    is EnemyIntent.Brand -> "BRAND · ${intent.targets.size}"
    EnemyIntent.Surge -> "SURGE"
    EnemyIntent.Snatch -> "SNATCH"
    EnemyIntent.Curse -> "CURSE"
}

private fun tileDescription(coord: Coord, cell: Cell?) =
    "Column ${coord.x + 1}, row ${coord.y + 1}, ${cell?.kind?.name ?: "empty"} ${cell?.hue?.name.orEmpty()}"

private fun tileGlyph(cell: Cell?): String = when (cell?.kind) {
    null -> ""
    TileKind.ASH -> "✧"
    TileKind.STONE -> "▣"
    TileKind.PRISM -> "◇"
    TileKind.HUE -> hueGlyph(cell.hue!!)
}

private fun hueGlyph(hue: Hue) = when (hue) {
    Hue.EMBER -> "▲"
    Hue.GOLD -> "●"
    Hue.MOSS -> "♣"
    Hue.TIDE -> "≈"
    Hue.VIOLET -> "✦"
}

private fun hueColor(hue: Hue) = when (hue) {
    Hue.EMBER -> Color(0xFFE26C48)
    Hue.GOLD -> Color(0xFFD8B15F)
    Hue.MOSS -> Color(0xFF79A67C)
    Hue.TIDE -> Color(0xFF6EA7B8)
    Hue.VIOLET -> Color(0xFF9A82C2)
}

private fun tileColor(cell: Cell?): Color = when (cell?.kind) {
    null -> Color(0xFF24282E)
    TileKind.ASH -> Color(0xFF827C73)
    TileKind.STONE -> Color(0xFF4A4A4C)
    TileKind.PRISM -> Color(0xFFB9A8D1)
    TileKind.HUE -> hueColor(cell.hue!!).copy(alpha = 0.72f)
}

private fun tileForeground(cell: Cell?): Color =
    if (cell?.kind == TileKind.STONE) Color(0xFFE1DED8) else Color(0xFF17191C)

private fun costLabel(cost: CardCost): String = buildString {
    cost.colored.forEach { (hue, amount) -> append(hueGlyph(hue)).append(amount).append(' ') }
    if (cost.generic > 0) append("◇").append(cost.generic)
    if (isEmpty()) append("FREE")
}

private fun relicDescription(rule: SignatureRelicRule) = when (rule) {
    SignatureRelicRule.FIRST_TAP_BONUS_INK -> "first Tap each turn grants +2 Ink"
    SignatureRelicRule.FREE_RECOLOR_ONCE_PER_FIGHT -> "recolor one block free once per fight"
    SignatureRelicRule.SPARK_PER_ASH_BLOCK -> "+1 Spark per Ash block popped"
}
