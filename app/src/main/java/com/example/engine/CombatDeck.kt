package com.example.engine

/**
 * Player CombatDeck managing Draw Pile, Hand, Discard Pile, and Exhaust Pile.
 * Strictly separated from BoardPaintDeck.
 */
object CombatDeck {

  const val MAX_HAND_SIZE = 7
  const val BASE_DRAW_COUNT = 5

  fun startCombat(
    masterDeck: List<CardInstance>,
    rng: SeededRng,
  ): Pair<DeckState, SeededRng> {
    val (shuffled, nextRng) = rng.shuffled(masterDeck)
    return drawCards(
      state = DeckState(
        drawPile = shuffled,
        hand = emptyList(),
        discardPile = emptyList(),
        exhaustPile = emptyList(),
      ),
      count = BASE_DRAW_COUNT,
      rng = nextRng,
    )
  }

  fun drawCards(
    state: DeckState,
    count: Int,
    rng: SeededRng,
  ): Pair<DeckState, SeededRng> {
    var draw = state.drawPile
    var hand = state.hand
    var discard = state.discardPile
    var currentRng = rng

    repeat(count) {
      if (hand.size >= MAX_HAND_SIZE) return@repeat
      if (draw.isEmpty()) {
        if (discard.isEmpty()) return@repeat
        val (reshuffled, nextRng) = currentRng.shuffled(discard)
        draw = reshuffled
        discard = emptyList()
        currentRng = nextRng
      }
      hand = hand + draw.first()
      draw = draw.drop(1)
    }

    return state.copy(
      drawPile = draw,
      hand = hand,
      discardPile = discard,
    ) to currentRng
  }

  fun playFromHand(
    state: DeckState,
    cardUid: Long,
    exhaust: Boolean,
  ): DeckState {
    val card = state.hand.firstOrNull { it.uid == cardUid } ?: return state
    val nextHand = state.hand.filterNot { it.uid == cardUid }
    return if (exhaust) {
      state.copy(hand = nextHand, exhaustPile = state.exhaustPile + card)
    } else {
      state.copy(hand = nextHand, discardPile = state.discardPile + card)
    }
  }

  fun discardHand(state: DeckState): DeckState {
    if (state.hand.isEmpty()) return state
    return state.copy(
      hand = emptyList(),
      discardPile = state.discardPile + state.hand,
    )
  }
}
