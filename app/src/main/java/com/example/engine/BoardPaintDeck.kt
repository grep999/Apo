package com.example.engine

/**
 * ADR-003 Deck-Frame-Driven Board Paint Deck.
 * Strictly separated from CombatDeck zones while deriving hue weights directly from the player's
 * current masterDeck card frame hues:
 *   weight(h) = max(0.08, (cards_of_hue_h + 1) / (deck_size + 5))
 */
object BoardPaintDeck {

  fun computeHueWeights(masterDeck: List<CardInstance>): Map<Hue, Double> {
    val deckSize = masterDeck.size.toDouble()
    val counts = Hue.entries.associateWith { h ->
      masterDeck.count { GameCatalog.cardById(it.defId).frameHue == h }
    }
    return Hue.entries.associateWith { h ->
      val raw = ((counts[h] ?: 0) + 1.0) / (deckSize + 5.0)
      maxOf(0.08, raw)
    }
  }

  fun computeHueWeightsBasisPoints(masterDeck: List<CardInstance>): Map<Hue, Int> {
    val weights = computeHueWeights(masterDeck)
    return weights.mapValues { (_, w) -> (w * 10000.0).toInt().coerceAtLeast(800) }
  }

  fun createInitial(
    masterDeck: List<CardInstance>,
    rng: SeededRng,
  ): Pair<BoardPaintDeckState, SeededRng> {
    val weightsBp = computeHueWeightsBasisPoints(masterDeck)
    val bag = buildWeightedBag(weightsBp)
    val (shuffled, nextRng) = rng.shuffled(bag)
    return BoardPaintDeckState(
      drawBag = shuffled,
      discardBag = emptyList(),
      hueWeightsBp = weightsBp,
    ) to nextRng
  }

  private fun buildWeightedBag(weightsBp: Map<Hue, Int>): List<TileKind> {
    val totalBp = weightsBp.values.sum().coerceAtLeast(1)
    val bag = mutableListOf<TileKind>()
    // Scale normalized ADR-003 weights into a ~50-tile deterministic paint bag + controlled neutrals
    for (hue in Hue.entries) {
      val bp = weightsBp[hue] ?: 2000
      val count = ((bp * 50 + totalBp / 2) / totalBp).coerceAtLeast(4)
      repeat(count) { bag.add(TileKind.Playable(hue)) }
    }
    repeat(3) { bag.add(TileKind.Ash) }
    repeat(2) { bag.add(TileKind.Stone) }
    repeat(1) { bag.add(TileKind.Prism) }
    return bag
  }

  fun drawTileKind(
    deck: BoardPaintDeckState,
    rng: SeededRng,
  ): Triple<TileKind, BoardPaintDeckState, SeededRng> {
    var currentDraw = deck.drawBag
    var currentDiscard = deck.discardBag
    var currentRng = rng

    if (currentDraw.isEmpty()) {
      val source = if (currentDiscard.size >= 18) currentDiscard else buildWeightedBag(deck.hueWeightsBp)
      val (reshuffled, nextRng) = currentRng.shuffled(source)
      currentDraw = reshuffled
      currentDiscard = emptyList()
      currentRng = nextRng
    }

    val drawn = currentDraw.first()
    val remaining = currentDraw.drop(1)
    return Triple(
      drawn,
      deck.copy(drawBag = remaining, discardBag = currentDiscard),
      currentRng,
    )
  }

  fun discardKinds(
    deck: BoardPaintDeckState,
    consumed: List<TileKind>,
  ): BoardPaintDeckState {
    // Forged/seeded Prisms are special board payoffs and must not recycle endlessly into the refill bag.
    val recyclable = consumed.filterNot { it is TileKind.Prism }
    if (recyclable.isEmpty()) return deck
    return deck.copy(discardBag = deck.discardBag + recyclable)
  }
}
