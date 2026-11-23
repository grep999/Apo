package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.engine.GameCatalog
import com.example.engine.GameEngine
import com.example.engine.ProgressionCatalog
import com.example.engine.RunState
import com.example.ui.components.CardPillView
import com.example.ui.components.EmberfallTopBar
import com.example.ui.components.RunResourceBanner
import com.example.ui.theme.*

@Composable
fun RewardsScreen(
  run: RunState,
  onPickCard: (String) -> Unit,
  onSkipCard: () -> Unit,
  onRerollCards: () -> Unit = {},
  onClaimRelic: () -> Unit,
  onSkipRelic: () -> Unit,
  onFinishRewards: () -> Unit,
  onSocketSupport: (String, Int) -> Unit = { _, _ -> },
  onSkipSupport: () -> Unit = {},
  onBindArtifact: (String) -> Unit = {},
  onSkipArtifact: () -> Unit = {},
) {
  val reward = run.pendingReward ?: return
  var supportToReplace by remember(run.pendingReward) { mutableStateOf<String?>(null) }
  val canFinish = reward.allChoicesResolved

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(ObsidianVoid)
      .windowInsetsPadding(WindowInsets.safeDrawing),
  ) {
    EmberfallTopBar(title = "Victory Spoils", subtitle = "Act ${run.currentAct} • Seed ${run.seedLabel}")
    RunResourceBanner(run = run, modifier = Modifier.padding(horizontal = 16.dp))

    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Surface(
          color = CharredBasalt,
          shape = RoundedCornerShape(14.dp),
          border = BorderStroke(1.dp, GildedBrass.copy(alpha = 0.6f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("+${reward.cindersEarned} Cinders scavenged", style = MaterialTheme.typography.titleMedium, color = GildedBrass)
            if (reward.flaskRefill > 0) {
              Text("+${reward.flaskRefill} Ember Flask replenished", style = MaterialTheme.typography.bodyMedium, color = VerdantHeal)
            }
            if (reward.remnantRewardId != null) {
              val remnant = GameCatalog.remnantById(reward.remnantRewardId)
              Text("Shade Remnant Bound: ${remnant.name} — ${remnant.summary}", style = MaterialTheme.typography.bodyMedium, color = HueViolet)
            }
          }
        }
      }

      if (reward.relicRewardId != null) {
        val relic = GameCatalog.relicById(reward.relicRewardId)
        item {
          Surface(
            color = SlateStone,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, if (reward.relicPicked) SmokedBorder else GildedBrass),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text("Relic Found: ${relic.name}", style = MaterialTheme.typography.titleMedium, color = GildedBrass)
              Text(relic.summary, style = MaterialTheme.typography.bodyMedium, color = WarmParchment)
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Button(
                  onClick = onClaimRelic,
                  enabled = !reward.relicPicked,
                  modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("claim_relic_button"),
                  colors = ButtonDefaults.buttonColors(containerColor = GildedBrass, contentColor = ObsidianVoid),
                ) {
                  Text(if (reward.relicPicked) "Resolved" else "Claim")
                }
                if (!reward.relicPicked) {
                  OutlinedButton(
                    onClick = onSkipRelic,
                    modifier = Modifier
                      .height(48.dp)
                      .testTag("skip_relic_button"),
                    border = BorderStroke(1.dp, SmokedBorder),
                  ) {
                    Text("Skip Relic", color = MutedAsh)
                  }
                }
              }
            }
          }
        }
      }

      if (!reward.supportPicked && reward.supportChoices.isNotEmpty()) {
        item { Text("Dhāraṇā Support Engraving", style = MaterialTheme.typography.titleLarge, color = GildedBrass) }
        items(reward.supportChoices, key = { it }) { supportId ->
          val support = ProgressionCatalog.supportByIdOrNull(supportId)
          if (support != null) {
            Surface(
              onClick = {
                if (run.growth.socketedSupportIds.size >= run.growth.maxSockets) supportToReplace = supportId
                else onSocketSupport(supportId, -1)
              },
              color = SlateStone,
              shape = RoundedCornerShape(12.dp),
              border = BorderStroke(1.dp, MoltenEmber.copy(alpha = 0.8f)),
              modifier = Modifier.fillMaxWidth().testTag("reward_support_$supportId"),
            ) {
              Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(support.name, style = MaterialTheme.typography.titleMedium, color = GildedBrass)
                Text(support.traditionNote, style = MaterialTheme.typography.labelSmall, color = MutedAsh)
                Text(support.summary, style = MaterialTheme.typography.bodyMedium, color = WarmParchment)
              }
            }
          }
        }
        item {
          OutlinedButton(onClick = onSkipSupport, modifier = Modifier.fillMaxWidth().height(48.dp).testTag("skip_support_button")) {
            Text("Skip Support (+8 Cinders)", color = MutedAsh)
          }
        }
      }

      if (!reward.artifactPicked && reward.artifactChoices.isNotEmpty()) {
        item { Text("Tantric Artifact", style = MaterialTheme.typography.titleLarge, color = HueViolet) }
        items(reward.artifactChoices, key = { it }) { artifactId ->
          val artifact = ProgressionCatalog.artifactByIdOrNull(artifactId)
          if (artifact != null) {
            Surface(
              onClick = { onBindArtifact(artifactId) },
              color = CharredBasalt,
              shape = RoundedCornerShape(12.dp),
              border = BorderStroke(1.dp, HueViolet.copy(alpha = 0.8f)),
              modifier = Modifier.fillMaxWidth().testTag("reward_artifact_$artifactId"),
            ) {
              Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(artifact.name, style = MaterialTheme.typography.titleMedium, color = HueViolet)
                Text(artifact.summary, style = MaterialTheme.typography.bodyMedium, color = WarmParchment)
              }
            }
          }
        }
        item {
          OutlinedButton(onClick = onSkipArtifact, modifier = Modifier.fillMaxWidth().height(48.dp).testTag("skip_artifact_button")) {
            Text("Skip Artifact (+8 Cinders)", color = MutedAsh)
          }
        }
      }
      if (reward.cardChoices.isNotEmpty()) {
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = if (reward.cardPicked) "Card Draft Resolved" else "Draft 1 Ink Card (or Skip)",
              style = MaterialTheme.typography.titleLarge,
              color = WarmParchment,
            )
          }
        }
        items(reward.cardChoices) { cardId ->
          val def = GameCatalog.cardById(cardId)
          CardPillView(
            cardDef = def,
            upgraded = false,
            enabled = !reward.cardPicked,
            onClick = { onPickCard(cardId) },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("reward_card_$cardId"),
          )
        }
        if (!reward.cardPicked) {
          item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              if (!reward.rerollUsed) {
                val canAffordReroll = run.cinders >= GameEngine.REROLL_CARDS_COST
                OutlinedButton(
                  onClick = onRerollCards,
                  enabled = canAffordReroll,
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("reroll_cards_button"),
                  border = BorderStroke(1.2.dp, if (canAffordReroll) HueViolet else SmokedBorder),
                  shape = RoundedCornerShape(14.dp),
                  colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = CharredBasalt,
                    contentColor = if (canAffordReroll) HueViolet else MutedAsh,
                  ),
                ) {
                  Icon(
                    imageVector = Icons.Default.Casino,
                    contentDescription = null,
                    tint = if (canAffordReroll) HueViolet else MutedAsh,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(Modifier.width(6.dp))
                  Text(
                    text = "Transmute Draft (${GameEngine.REROLL_CARDS_COST} Cinders)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                  )
                }
              }

              OutlinedButton(
                onClick = onSkipCard,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(50.dp)
                  .testTag("skip_card_button"),
                border = BorderStroke(1.5.dp, GildedBrass),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                  containerColor = CharredBasalt,
                  contentColor = WarmParchment,
                ),
              ) {
                Icon(
                  imageVector = Icons.Default.SkipNext,
                  contentDescription = null,
                  tint = GildedBrass,
                  modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                  text = if (relicResolved) {
                    "Skip Card (+${GameEngine.SKIP_CARD_CINDER_BONUS} Cinders) & Map"
                  } else {
                    "Skip Card (+${GameEngine.SKIP_CARD_CINDER_BONUS} Cinders)"
                  },
                  style = MaterialTheme.typography.titleMedium,
                  color = GildedBrass,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
          }
        }
      }
    }

    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      if (!canFinish) {
        Text(
          text = "Claim or skip all spoils before returning to the Spire Map.",
          style = MaterialTheme.typography.labelSmall,
          color = GildedBrass,
        )
      }
      Button(
        onClick = onFinishRewards,
        enabled = canFinish,
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("finish_rewards_button"),
        colors = ButtonDefaults.buttonColors(containerColor = MoltenEmber, contentColor = ObsidianVoid),
        shape = RoundedCornerShape(14.dp),
      ) {
        Text("Return to Spire Map", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      }
    }
  }

  supportToReplace?.let { supportId ->
    com.example.ui.components.SupportReplacementDialog(
      supportId = supportId,
      growth = run.growth,
      onChooseSlot = { slot ->
        onSocketSupport(supportId, slot)
        supportToReplace = null
      },
      onDismiss = { supportToReplace = null },
    )
  }
}

@Composable
fun EventScreen(
  run: RunState,
  onChooseOption: (String) -> Unit,
  onLeave: () -> Unit,
) {
  val event = run.activeEvent ?: return
  val isResolved = event.resolvedSummary != null

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(ObsidianVoid)
      .windowInsetsPadding(WindowInsets.safeDrawing),
  ) {
    EmberfallTopBar(title = event.title, subtitle = "Act ${run.currentAct} • Citadel Event")
    RunResourceBanner(run = run, modifier = Modifier.padding(horizontal = 16.dp))

    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Surface(
          color = CharredBasalt,
          shape = RoundedCornerShape(16.dp),
          border = BorderStroke(1.dp, HueTide.copy(alpha = 0.6f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = event.body,
            style = MaterialTheme.typography.bodyLarge,
            color = WarmParchment,
            modifier = Modifier.padding(16.dp),
          )
        }
      }

      if (!isResolved) {
        items(event.choices, key = { it.id }) { choice ->
          val meetsReq = run.cinders >= choice.minCindersRequired &&
            run.hp >= choice.minHpRequired &&
            run.flasks >= choice.minFlasksRequired

          Surface(
            onClick = { if (meetsReq) onChooseOption(choice.id) },
            enabled = meetsReq,
            modifier = Modifier
              .fillMaxWidth()
              .defaultMinSize(minHeight = 64.dp)
              .testTag("event_choice_${choice.id}"),
            color = if (meetsReq) SlateStone else CharredBasalt,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, if (meetsReq) GildedBrass else SmokedBorder),
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = choice.label,
                style = MaterialTheme.typography.titleMedium,
                color = if (meetsReq) GildedBrass else MutedAsh,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = choice.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = if (meetsReq) WarmParchment else MutedAsh,
              )
              if (!meetsReq) {
                val reqNote = buildList {
                  if (run.cinders < choice.minCindersRequired) add("Requires ${choice.minCindersRequired} Cinders")
                  if (run.hp < choice.minHpRequired) add("Requires ${choice.minHpRequired} HP")
                  if (run.flasks < choice.minFlasksRequired) add("Requires ${choice.minFlasksRequired} Flasks")
                }.joinToString(" • ")
                Text(
                  text = reqNote,
                  style = MaterialTheme.typography.labelSmall,
                  color = CrimsonRuin,
                )
              }
            }
          }
        }
      } else {
        item {
          Surface(
            color = SlateStone,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, VerdantHeal),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("event_resolved_banner"),
          ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text("Outcome", style = MaterialTheme.typography.labelMedium, color = VerdantHeal)
              Text(
                text = event.resolvedSummary ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = WarmParchment,
              )
            }
          }
        }
      }
    }

    Box(modifier = Modifier.padding(16.dp)) {
      Button(
        onClick = onLeave,
        enabled = isResolved,
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("event_leave_button"),
        colors = ButtonDefaults.buttonColors(containerColor = MoltenEmber, contentColor = ObsidianVoid),
        shape = RoundedCornerShape(14.dp),
      ) {
        Text(
          text = if (isResolved) "Continue Climb" else "Choose an Action First",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
      }
    }
  }
}

@Composable
fun CampfireScreen(
  run: RunState,
  onRest: () -> Unit,
  onUpgradeCard: (Long) -> Unit,
  onGamble: () -> Unit = {},
  onLeave: () -> Unit,
) {
  var showUpgradeList by remember { mutableStateOf(false) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(ObsidianVoid)
      .windowInsetsPadding(WindowInsets.safeDrawing),
  ) {
    EmberfallTopBar(title = "Ember Campfire", subtitle = "Rest, Temper, or Tempt the Kiln")
    RunResourceBanner(run = run, modifier = Modifier.padding(horizontal = 16.dp))

    Column(
      modifier = Modifier
        .weight(1f)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Surface(
        onClick = onRest,
        enabled = !run.campfireUsed,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("campfire_rest_button"),
        color = SlateStone,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, if (!run.campfireUsed) VerdantHeal else SmokedBorder),
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Text("Kindle & Rest", style = MaterialTheme.typography.titleLarge, color = VerdantHeal)
          Spacer(Modifier.height(3.dp))
          Text(
            text = "Restore 35% HP and replenish +1 Ember Flask charge.",
            style = MaterialTheme.typography.bodyMedium,
            color = WarmParchment,
          )
        }
      }

      Surface(
        onClick = { showUpgradeList = !showUpgradeList },
        enabled = !run.campfireUsed,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("campfire_forge_button"),
        color = SlateStone,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, if (!run.campfireUsed) GildedBrass else SmokedBorder),
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Text("Forge & Temper", style = MaterialTheme.typography.titleLarge, color = GildedBrass)
          Spacer(Modifier.height(3.dp))
          Text(
            text = "Choose any card in your deck to upgrade its Damage or Guard.",
            style = MaterialTheme.typography.bodyMedium,
            color = WarmParchment,
          )
        }
      }

      val canGamble = !run.campfireUsed && run.hp > GameEngine.CAMPFIRE_GAMBLE_HP_COST
      Surface(
        onClick = onGamble,
        enabled = canGamble,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("campfire_gamble_button"),
        color = SlateStone,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, if (canGamble) MoltenEmber else SmokedBorder),
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Default.Whatshot,
              contentDescription = null,
              tint = if (canGamble) MoltenEmber else MutedAsh,
              modifier = Modifier.size(20.dp),
            )
            Text(
              text = "Stoke the Kiln (Risk & Reward)",
              style = MaterialTheme.typography.titleLarge,
              color = if (canGamble) MoltenEmber else MutedAsh,
            )
          }
          Spacer(Modifier.height(3.dp))
          Text(
            text = "Pay ${GameEngine.CAMPFIRE_GAMBLE_HP_COST} HP to claim +${GameEngine.CAMPFIRE_GAMBLE_CINDERS} Cinders AND temper a random card!",
            style = MaterialTheme.typography.bodyMedium,
            color = if (canGamble) WarmParchment else MutedAsh,
          )
        }
      }

      if (showUpgradeList && !run.campfireUsed) {
        LazyColumn(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(run.masterDeck.filter { !it.upgraded }, key = { it.uid }) { card ->
            CardPillView(
              cardDef = GameCatalog.cardById(card.defId),
              upgraded = false,
              onClick = {
                onUpgradeCard(card.uid)
                showUpgradeList = false
              },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("upgrade_card_${card.uid}"),
            )
          }
        }
      }

      if (run.campfireUsed) {
        Text(
          text = "The embers settle. Ready to continue your climb.",
          style = MaterialTheme.typography.bodyMedium,
          color = GildedBrass,
        )
      }
    }

    Box(modifier = Modifier.padding(16.dp)) {
      Button(
        onClick = onLeave,
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("campfire_continue_button"),
        colors = ButtonDefaults.buttonColors(containerColor = MoltenEmber, contentColor = ObsidianVoid),
        shape = RoundedCornerShape(14.dp),
      ) {
        Text("Continue Climb", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Composable
fun MerchantScreen(
  run: RunState,
  onBuyCard: (String) -> Unit,
  onBuyRelic: (String) -> Unit,
  onBuyFlask: () -> Unit,
  onRemoveCard: (Long) -> Unit,
  onLeave: () -> Unit,
) {
  val shop = run.shopStock ?: return
  var showRemoveDialog by remember { mutableStateOf(false) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(ObsidianVoid)
      .windowInsetsPadding(WindowInsets.safeDrawing),
  ) {
    EmberfallTopBar(title = "Ash Merchant", subtitle = "Run-Scoped Cinders Barter (No Meta-Currency)")
    RunResourceBanner(run = run, modifier = Modifier.padding(horizontal = 16.dp))

    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
          Button(
            onClick = onBuyFlask,
            enabled = !shop.flaskPurchased && run.cinders >= shop.flaskPrice && run.flasks < run.maxFlasks,
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("shop_buy_flask"),
            colors = ButtonDefaults.buttonColors(containerColor = VerdantHeal, contentColor = ObsidianVoid),
          ) {
            Text("Flask (${shop.flaskPrice})", style = MaterialTheme.typography.labelLarge)
          }

          OutlinedButton(
            onClick = { showRemoveDialog = true },
            enabled = !shop.removeUsed && run.cinders >= shop.removePrice && run.masterDeck.size > 4,
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("shop_remove_card"),
            border = BorderStroke(1.dp, CrimsonRuin),
          ) {
            Text("Burn Card (${shop.removePrice})", style = MaterialTheme.typography.labelLarge, color = CrimsonRuin)
          }
        }
      }

      if (shop.relicIds.isNotEmpty()) {
        item {
          Text("Relics", style = MaterialTheme.typography.titleLarge, color = GildedBrass)
        }
        items(shop.relicIds) { relicId ->
          val relic = GameCatalog.relicById(relicId)
          Surface(
            color = CharredBasalt,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, SmokedBorder),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(relic.name, style = MaterialTheme.typography.titleMedium, color = GildedBrass)
                Text(relic.summary, style = MaterialTheme.typography.bodyMedium, color = WarmParchment)
              }
              Spacer(Modifier.width(8.dp))
              Button(
                onClick = { onBuyRelic(relicId) },
                enabled = run.cinders >= relic.price,
                colors = ButtonDefaults.buttonColors(containerColor = GildedBrass, contentColor = ObsidianVoid),
              ) {
                Text("${relic.price}")
              }
            }
          }
        }
      }

      if (shop.cardIds.isNotEmpty()) {
        item {
          Text("Ink Cards", style = MaterialTheme.typography.titleLarge, color = WarmParchment)
        }
        items(shop.cardIds) { cardId ->
          val def = GameCatalog.cardById(cardId)
          val price = GameEngine.cardPrice(def)
          Column {
            CardPillView(
              cardDef = def,
              upgraded = false,
              enabled = run.cinders >= price,
              onClick = { onBuyCard(cardId) },
              modifier = Modifier.fillMaxWidth(),
            )
            Text(
              text = "Cost: $price Cinders",
              style = MaterialTheme.typography.labelSmall,
              color = if (run.cinders >= price) GildedBrass else CrimsonRuin,
              modifier = Modifier.padding(start = 8.dp, top = 2.dp),
            )
          }
        }
      }
    }

    Box(modifier = Modifier.padding(16.dp)) {
      Button(
        onClick = onLeave,
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("shop_leave_button"),
        colors = ButtonDefaults.buttonColors(containerColor = MoltenEmber, contentColor = ObsidianVoid),
        shape = RoundedCornerShape(14.dp),
      ) {
        Text("Leave Merchant", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      }
    }
  }

  if (showRemoveDialog) {
    AlertDialog(
      onDismissRequest = { showRemoveDialog = false },
      containerColor = CharredBasalt,
      title = { Text("Burn a Card (${shop.removePrice} Cinders)", color = WarmParchment) },
      text = {
        LazyColumn(modifier = Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          items(run.masterDeck, key = { it.uid }) { card ->
            CardPillView(
              cardDef = GameCatalog.cardById(card.defId),
              upgraded = card.upgraded,
              onClick = {
                onRemoveCard(card.uid)
                showRemoveDialog = false
              },
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showRemoveDialog = false }) {
          Text("Cancel", color = MutedAsh)
        }
      },
    )
  }
}
