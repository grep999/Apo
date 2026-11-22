package com.example.engine

/**
 * Deterministic SplitMix64 PRNG and named subsystem streams so every run seed + command sequence
 * reproduces identical maps, board paints, card draws, enemy behaviors, and rewards.
 */
data class SeededRng(val state: Long) {

  fun nextLong(): Pair<Long, SeededRng> {
    val nextState = state + 0x9E3779B97F4A7C15UL.toLong()
    var z = nextState
    z = (z xor (z ushr 30)) * 0xBF58476D1CE4E5B9UL.toLong()
    z = (z xor (z ushr 27)) * 0x94D049BB133111EBUL.toLong()
    z = z xor (z ushr 31)
    return z to SeededRng(nextState)
  }

  fun nextInt(bound: Int): Pair<Int, SeededRng> {
    require(bound > 0) { "Bound must be positive, was $bound" }
    val boundLong = bound.toLong()
    val threshold = Long.MAX_VALUE - (Long.MAX_VALUE % boundLong)
    var current = this
    while (true) {
      val (raw, nextRng) = current.nextLong()
      current = nextRng
      val nonNegative = raw ushr 1
      if (nonNegative < threshold) {
        return (nonNegative % boundLong).toInt() to current
      }
    }
  }

  fun fork(salt: Long): SeededRng {
    val (_, advanced) = SeededRng(state xor salt).nextLong()
    return advanced
  }

  fun <T> shuffled(items: List<T>): Pair<List<T>, SeededRng> {
    if (items.size <= 1) return items.toList() to this
    val mutable = items.toMutableList()
    var rng = this
    for (i in mutable.lastIndex downTo 1) {
      val (j, nextRng) = rng.nextInt(i + 1)
      rng = nextRng
      val tmp = mutable[i]
      mutable[i] = mutable[j]
      mutable[j] = tmp
    }
    return mutable.toList() to rng
  }

  companion object {
    fun fromSeedString(seedText: String): Long {
      val trimmed = seedText.trim()
      trimmed.toLongOrNull()?.let { return if (it == 0L) 0x5EED1234L else it }
      if (trimmed.isEmpty()) return 0x454D424552L
      var hash = 0x100000001B3L
      for (ch in trimmed) {
        hash = (hash xor ch.code.toLong()) * 0x100000001B3L
      }
      return if (hash == 0L) 0x454D424552L else hash
    }
  }
}

/**
 * Named RNG streams persisted across saves so mutations in one subsystem never perturb another.
 */
data class RngStreams(
  val map: SeededRng,
  val boardPaint: SeededRng,
  val combatDeck: SeededRng,
  val enemy: SeededRng,
  val rewards: SeededRng,
) {
  companion object {
    fun fromRunSeed(seed: Long): RngStreams {
      val root = SeededRng(seed)
      return RngStreams(
        map = root.fork(0x1111_0001L),
        boardPaint = root.fork(0x2222_0002L),
        combatDeck = root.fork(0x3333_0003L),
        enemy = root.fork(0x4444_0004L),
        rewards = root.fork(0x5555_0005L),
      )
    }
  }
}
