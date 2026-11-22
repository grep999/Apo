package com.example.engine

/**
 * 7x7 visible board + 1 incoming row (ADR-008), non-mutating group preview,
 * ADR-004 Prism contact-hue wheel resolution (no multi-hue bridging),
 * ADR-005 ordered Spectrum wheel (Run 0 start, consecutive increment, out-of-order reset),
 * ADR-006 Ash direct 2+ or adjacent clear (0 Ink, n-1 chip),
 * ADR-007 Stone 4+ iterative orthogonal chain break,
 * ADR-010 Branded tiles (2 Vigil/block),
 * ADR-012 Sealed columns freeze,
 * and gravity + paint-deck refill with TileKind.Empty headroom.
 */
object BoardEngine {

  const val ROWS = 7
  const val COLS = 7
  const val HEADROOM_ROWS = 2
  const val MIN_OCCUPIED_ROWS = ROWS - HEADROOM_ROWS // 5 occupied rows (rows 2..6)
  const val MAX_STONE_CHAIN_STEPS = 7

  private val orthogonalDirs = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)

  data class BoardCascadeOutcome(
    val board: BoardState,
    val paintDeck: BoardPaintDeckState,
    val rng: SeededRng,
    val didReshuffle: Boolean = false,
  )

  data class WallRiseOutcome(
    val board: BoardState,
    val paintDeck: BoardPaintDeckState,
    val rng: SeededRng,
    val overflowTriggered: Boolean,
    val wallSkippedDueToOverflow: Boolean,
    val burnedTopRowsCount: Int,
    val didReshuffle: Boolean = false,
  )

  fun seedBoard(
    paintDeck: BoardPaintDeckState,
    rng: SeededRng,
    startingPrismCount: Int = 1,
  ): Triple<BoardState, BoardPaintDeckState, SeededRng> {
    var deck = paintDeck
    var currentRng = rng
    var nextId = 1L

    val grid = MutableList(ROWS) { r ->
      MutableList(COLS) {
        if (r < HEADROOM_ROWS) {
          Tile(id = nextId++, kind = TileKind.Empty)
        } else {
          val (kind, nextDeck, nextRng) = BoardPaintDeck.drawTileKind(deck, currentRng)
          deck = nextDeck
          currentRng = nextRng
          Tile(id = nextId++, kind = kind)
        }
      }
    }

    repeat(startingPrismCount.coerceAtLeast(0)) {
      val (rOffset, r1) = currentRng.nextInt(MIN_OCCUPIED_ROWS)
      val (c, r2) = r1.nextInt(COLS)
      currentRng = r2
      val r = HEADROOM_ROWS + rOffset
      grid[r][c] = grid[r][c].copy(kind = TileKind.Prism)
    }

    breakInitialLongRuns(grid)

    val incomingRow = List(COLS) {
      val (kind, nextDeck, nextRng) = BoardPaintDeck.drawTileKind(deck, currentRng)
      deck = nextDeck
      currentRng = nextRng
      Tile(id = nextId++, kind = kind)
    }

    val rawBoard = BoardState(
      rows = ROWS,
      cols = COLS,
      grid = grid.map { it.toList() },
      incomingRow = incomingRow,
      sealedColumns = emptyMap(),
      nextTileId = nextId,
    )
    val ensured = ensurePlayableSwap(rawBoard, deck, currentRng, currentRunHue = null)
    return Triple(ensured.board, ensured.paintDeck, ensured.rng)
  }

  /**
   * Prevents pre-solved 3+ horizontal or vertical lines (including Prism wildcards and Ash lines)
   * and 5+ clusters on the initial combat board via deterministic in-grid swaps, preserving the
   * ADR-003 drawn tile multiset.
   */
  private fun breakInitialLongRuns(
    grid: MutableList<MutableList<Tile>>,
    sealedColumns: Map<Int, Int> = emptyMap(),
  ) {
    repeat(96) {
      val offender = findOffendingClusterCell(grid, sealedColumns) ?: return
      val (orow, ocol) = offender
      val offenderKind = grid[orow][ocol].kind

      var swapped = false
      for (sr in 0 until ROWS) {
        for (sc in 0 until COLS) {
          if ((sealedColumns[sc] ?: 0) > 0) continue
          val candidateKind = grid[sr][sc].kind
          if (candidateKind is TileKind.Empty || candidateKind is TileKind.Stone) continue
          if (candidateKind == offenderKind) continue
          if (sr == orow && sc == ocol) continue

          val tmp = grid[orow][ocol]
          grid[orow][ocol] = grid[sr][sc]
          grid[sr][sc] = tmp

          if (!isPartOfLongRunOrLargeCluster(grid, orow, ocol, sealedColumns) &&
            !isPartOfLongRunOrLargeCluster(grid, sr, sc, sealedColumns)
          ) {
            swapped = true
            break
          }

          grid[sr][sc] = grid[orow][ocol]
          grid[orow][ocol] = tmp
        }
        if (swapped) break
      }
      if (!swapped) {
        // Fallback if multiset is heavily skewed (e.g. Unlit 12-card Gold deck): shift offender hue safely
        val currentHue = (offenderKind as? TileKind.Playable)?.hue
        if (currentHue != null) {
          val safeHue = Hue.WHEEL_ORDER.firstOrNull { alt ->
            alt != currentHue && run {
              val prev = grid[orow][ocol]
              grid[orow][ocol] = prev.copy(kind = TileKind.Playable(alt))
              val ok = !isPartOfLongRunOrLargeCluster(grid, orow, ocol, sealedColumns)
              grid[orow][ocol] = prev
              ok
            }
          }
          if (safeHue != null) {
            grid[orow][ocol] = grid[orow][ocol].copy(kind = TileKind.Playable(safeHue))
          } else {
            return
          }
        } else {
          return
        }
      }
    }
  }

  private fun findOffendingClusterCell(
    grid: List<List<Tile>>,
    sealedColumns: Map<Int, Int> = emptyMap(),
  ): Pair<Int, Int>? {
    for (r in 0 until ROWS) {
      for (c in 0 until COLS) {
        if ((sealedColumns[c] ?: 0) > 0) continue
        if (isPartOfLongRunOrLargeCluster(grid, r, c, sealedColumns)) {
          return r to c
        }
      }
    }
    return null
  }

  private fun isPartOfLongRunOrLargeCluster(
    grid: List<List<Tile>>,
    row: Int,
    col: Int,
    sealedColumns: Map<Int, Int> = emptyMap(),
  ): Boolean {
    if ((sealedColumns[col] ?: 0) > 0) return false
    val kind = grid[row][col].kind
    if (kind is TileKind.Empty || kind is TileKind.Stone) return false

    if (kind is TileKind.Ash) {
      val hRun = countLineRun(grid, row, col, 0, 1, sealedColumns) { it is TileKind.Ash }
      if (hRun >= 3) return true
      val vRun = countLineRun(grid, row, col, 1, 0, sealedColumns) { it is TileKind.Ash }
      return vRun >= 3
    }

    if (kind is TileKind.Prism) {
      // Prism acts as wildcard for any adjacent hue -> ensure it doesn't complete a 3-in-a-row on spawn
      for (hue in Hue.WHEEL_ORDER) {
        val hRun = countLineRun(grid, row, col, 0, 1, sealedColumns) {
          it == TileKind.Playable(hue) || it is TileKind.Prism
        }
        if (hRun >= 3) return true
        val vRun = countLineRun(grid, row, col, 1, 0, sealedColumns) {
          it == TileKind.Playable(hue) || it is TileKind.Prism
        }
        if (vRun >= 3) return true
      }
      return false
    }

    val hue = (kind as? TileKind.Playable)?.hue ?: return false
    val matchesHueOrPrism: (TileKind) -> Boolean = { it == TileKind.Playable(hue) || it is TileKind.Prism }
    if (countLineRun(grid, row, col, 0, 1, sealedColumns, matchesHueOrPrism) >= 3) return true
    if (countLineRun(grid, row, col, 1, 0, sealedColumns, matchesHueOrPrism) >= 3) return true

    val seen = mutableSetOf<BoardCoord>()
    val q = ArrayDeque<BoardCoord>()
    val start = BoardCoord(row, col)
    seen.add(start)
    q.add(start)
    while (q.isNotEmpty() && seen.size < 5) {
      val curr = q.removeFirst()
      for ((dr, dc) in orthogonalDirs) {
        val nr = curr.row + dr
        val nc = curr.col + dc
        if (nr !in 0 until ROWS || nc !in 0 until COLS) continue
        if ((sealedColumns[nc] ?: 0) > 0) continue
        val nCoord = BoardCoord(nr, nc)
        if (nCoord !in seen && (grid[nr][nc].kind as? TileKind.Playable)?.hue == hue) {
          seen.add(nCoord)
          q.add(nCoord)
        }
      }
    }
    return seen.size >= 5
  }

  private fun countLineRun(
    grid: List<List<Tile>>,
    row: Int,
    col: Int,
    dr: Int,
    dc: Int,
    sealedColumns: Map<Int, Int>,
    predicate: (TileKind) -> Boolean,
  ): Int {
    if (!predicate(grid[row][col].kind)) return 0
    var count = 1
    var r = row - dr
    var c = col - dc
    while (r in 0 until ROWS && c in 0 until COLS && (sealedColumns[c] ?: 0) == 0 && predicate(grid[r][c].kind)) {
      count++
      r -= dr
      c -= dc
    }
    r = row + dr
    c = col + dc
    while (r in 0 until ROWS && c in 0 until COLS && (sealedColumns[c] ?: 0) == 0 && predicate(grid[r][c].kind)) {
      count++
      r += dr
      c += dc
    }
    return count
  }

  /**
   * Non-mutating group resolution & preview:
   * - Sealed column / Empty / Stone: direct tap rejected.
   * - Ash (ADR-006 / ADR-017): connected 2+ Ash can be tapped directly (0 Ink, n-1 chip after Enemy Block).
   * - Playable(H): orthogonally connected Playable(H) + Prism tiles (>= 2 tiles). Never crosses into another hue.
   * - Prism (ADR-004): joins ONE contacting hue. Ties resolved deterministically by Spectrum wheel order
   *   starting after [currentRunHue] (Ember -> Gold -> Moss -> Tide -> Violet). Never bridges unrelated hue clusters.
   * - Stone (ADR-007): breaks orthogonally adjacent Stone ONLY when hue/Prism group size >= 4 via bounded chain.
   * - Brand (ADR-010): every popped tile with [Tile.isBranded] costs 2 Vigil.
   */
  fun groupAt(
    board: BoardState,
    row: Int,
    col: Int,
    currentRunHue: Hue? = null,
  ): GroupResolution {
    if (col !in 0 until board.cols || board.isColumnSealed(col)) return emptyInvalid()
    val startTile = board.tileAt(row, col) ?: return emptyInvalid()
    if (startTile.kind is TileKind.Empty || startTile.kind is TileKind.Stone) return emptyInvalid()

    // Direct 2+ Ash group tap (ADR-006 / ADR-017)
    if (startTile.kind is TileKind.Ash) {
      val ashVisited = mutableSetOf<BoardCoord>()
      val q = ArrayDeque<BoardCoord>()
      val start = BoardCoord(row, col)
      ashVisited.add(start)
      q.add(start)
      while (q.isNotEmpty()) {
        val curr = q.removeFirst()
        for ((dr, dc) in orthogonalDirs) {
          val nr = curr.row + dr
          val nc = curr.col + dc
          if (nc !in 0 until board.cols || board.isColumnSealed(nc)) continue
          val neighbor = board.tileAt(nr, nc) ?: continue
          val nCoord = BoardCoord(nr, nc)
          if (nCoord !in ashVisited && neighbor.kind is TileKind.Ash) {
            ashVisited.add(nCoord)
            q.add(nCoord)
          }
        }
      }
      if (ashVisited.size < 2) return emptyInvalid()
      val branded = ashVisited.filter { board.tileAt(it.row, it.col)?.isBranded == true }.toSet()
      return GroupResolution(
        coords = ashVisited.toSet(),
        clearedAsh = ashVisited.toSet(),
        shatteredStone = emptySet(),
        brandedCoords = branded,
        brandVigilCost = branded.size * 2,
        resolvedHue = null,
        isDirectAshGroup = true,
        prismCount = 0,
        basePower = 0,
        inkHue = null,
        inkAmount = 0,
        ashChipDamage = maxOf(0, ashVisited.size - 1),
        stoneDefenseBonus = 0,
        isValid = true,
      )
    }

    val visited = mutableSetOf<BoardCoord>()
    var prismCount = 0
    val resolvedHue: Hue?

    when (val kind = startTile.kind) {
      is TileKind.Prism -> {
        val prismQueue = ArrayDeque<BoardCoord>()
        val prismCluster = mutableSetOf<BoardCoord>()
        val start = BoardCoord(row, col)
        prismCluster.add(start)
        prismQueue.add(start)

        val contactingSeeds = mutableListOf<Pair<BoardCoord, Hue>>()
        while (prismQueue.isNotEmpty()) {
          val curr = prismQueue.removeFirst()
          for ((dr, dc) in orthogonalDirs) {
            val nr = curr.row + dr
            val nc = curr.col + dc
            if (nc !in 0 until board.cols || board.isColumnSealed(nc)) continue
            val neighbor = board.tileAt(nr, nc) ?: continue
            val nCoord = BoardCoord(nr, nc)
            when (val nk = neighbor.kind) {
              is TileKind.Prism -> {
                if (nCoord !in prismCluster) {
                  prismCluster.add(nCoord)
                  prismQueue.add(nCoord)
                }
              }
              is TileKind.Playable -> contactingSeeds.add(nCoord to nk.hue)
              else -> Unit
            }
          }
        }

        val contactingHues = contactingSeeds.map { it.second }.toSet()
        resolvedHue = if (contactingHues.isEmpty()) {
          null
        } else {
          Hue.wheelOrderedFrom(currentRunHue).first { it in contactingHues }
        }

        visited.addAll(prismCluster)
        prismCount = prismCluster.size

        if (resolvedHue != null) {
          val hueQueue = ArrayDeque<BoardCoord>()
          for ((seedCoord, seedHue) in contactingSeeds) {
            if (seedHue == resolvedHue && seedCoord !in visited) {
              visited.add(seedCoord)
              hueQueue.add(seedCoord)
            }
          }
          while (hueQueue.isNotEmpty()) {
            val curr = hueQueue.removeFirst()
            for ((dr, dc) in orthogonalDirs) {
              val nr = curr.row + dr
              val nc = curr.col + dc
              if (nc !in 0 until board.cols || board.isColumnSealed(nc)) continue
              val neighbor = board.tileAt(nr, nc) ?: continue
              val nCoord = BoardCoord(nr, nc)
              if (nCoord in visited) continue
              when (neighbor.kind) {
                is TileKind.Playable -> if (neighbor.kind.hue == resolvedHue) {
                  visited.add(nCoord)
                  hueQueue.add(nCoord)
                }
                is TileKind.Prism -> {
                  visited.add(nCoord)
                  prismCount++
                  hueQueue.add(nCoord)
                }
                else -> Unit
              }
            }
          }
        }
      }

      is TileKind.Playable -> {
        resolvedHue = kind.hue
        val queue = ArrayDeque<BoardCoord>()
        val start = BoardCoord(row, col)
        visited.add(start)
        queue.add(start)

        while (queue.isNotEmpty()) {
          val curr = queue.removeFirst()
          val tile = board.tileAt(curr.row, curr.col) ?: continue
          if (tile.kind is TileKind.Prism) prismCount++

          for ((dr, dc) in orthogonalDirs) {
            val nr = curr.row + dr
            val nc = curr.col + dc
            if (nc !in 0 until board.cols || board.isColumnSealed(nc)) continue
            val neighbor = board.tileAt(nr, nc) ?: continue
            val nCoord = BoardCoord(nr, nc)
            if (nCoord in visited) continue
            if (neighbor.kind is TileKind.Prism || neighbor.kind == TileKind.Playable(resolvedHue)) {
              visited.add(nCoord)
              queue.add(nCoord)
            }
          }
        }
      }

      TileKind.Empty, TileKind.Stone, TileKind.Ash -> return emptyInvalid()
    }

    val isValid = visited.size >= 2 || prismCount > 0
    if (!isValid) return emptyInvalid()

    // ADR-006: Adjacent unsealed Ash clears (0 Ink, n - 1 chip after Enemy Block)
    val clearedAsh = mutableSetOf<BoardCoord>()
    for (coord in visited) {
      for ((dr, dc) in orthogonalDirs) {
        val nr = coord.row + dr
        val nc = coord.col + dc
        if (nc !in 0 until board.cols || board.isColumnSealed(nc)) continue
        val neighbor = board.tileAt(nr, nc) ?: continue
        if (neighbor.kind is TileKind.Ash) {
          clearedAsh.add(BoardCoord(nr, nc))
        }
      }
    }

    // ADR-007: Stone breaks ONLY on qualifying 4+ hue/Prism group with bounded orthogonal chain
    val shatteredStone = mutableSetOf<BoardCoord>()
    if (visited.size >= 4) {
      var frontier = mutableListOf<BoardCoord>()
      for (coord in visited.sortedWith(compareBy({ it.row }, { it.col }))) {
        for ((dr, dc) in orthogonalDirs) {
          val nr = coord.row + dr
          val nc = coord.col + dc
          if (nc !in 0 until board.cols || board.isColumnSealed(nc)) continue
          val neighbor = board.tileAt(nr, nc) ?: continue
          val nCoord = BoardCoord(nr, nc)
          if (neighbor.kind is TileKind.Stone && nCoord !in shatteredStone) {
            shatteredStone.add(nCoord)
            frontier.add(nCoord)
          }
        }
      }
      var chainStep = 0
      while (frontier.isNotEmpty() && chainStep < MAX_STONE_CHAIN_STEPS) {
        chainStep++
        val nextFrontier = mutableListOf<BoardCoord>()
        for (stoneCoord in frontier) {
          for ((dr, dc) in orthogonalDirs) {
            val nr = stoneCoord.row + dr
            val nc = stoneCoord.col + dc
            if (nc !in 0 until board.cols || board.isColumnSealed(nc)) continue
            val neighbor = board.tileAt(nr, nc) ?: continue
            val nCoord = BoardCoord(nr, nc)
            if (neighbor.kind is TileKind.Stone && nCoord !in shatteredStone) {
              shatteredStone.add(nCoord)
              nextFrontier.add(nCoord)
            }
          }
        }
        frontier = nextFrontier
      }
    }

    val allAffected = visited + clearedAsh + shatteredStone
    val brandedCoords = allAffected.filter { board.tileAt(it.row, it.col)?.isBranded == true }.toSet()

    val count = visited.size
    val groupBonus = if (count >= 4) (count - 3) * 2 else 0
    val basePower = (count * 2) + groupBonus + (prismCount * 3) + (shatteredStone.size * 2)
    // ADR-006: Ash NEVER contributes Ink
    val inkAmount = if (resolvedHue != null) 1 + (count / 3) + prismCount else 0
    val ashChipDamage = maxOf(0, clearedAsh.size - 1)
    val stoneDefenseBonus = shatteredStone.size * 2

    return GroupResolution(
      coords = visited.toSet(),
      clearedAsh = clearedAsh.toSet(),
      shatteredStone = shatteredStone.toSet(),
      brandedCoords = brandedCoords,
      brandVigilCost = brandedCoords.size * 2,
      resolvedHue = resolvedHue,
      isDirectAshGroup = false,
      prismCount = prismCount,
      basePower = basePower,
      inkHue = resolvedHue,
      inkAmount = inkAmount,
      ashChipDamage = ashChipDamage,
      stoneDefenseBonus = stoneDefenseBonus,
      isValid = true,
    )
  }

  /**
   * ADR-005 ordered Spectrum wheel:
   * - Wheel: Ember -> Gold -> Moss -> Tide -> Violet -> Ember
   * - Turn starts at Run 0 (currentHue = null, halfSteps = 2 = 1.0x)
   * - First colored pop establishes anchor at Run 0 (1.0x)
   * - Next consecutive wheel hue increments Run (+0.5x: Run 1 = 1.5x, Run 2 = 2.0x ...)
   * - Out-of-order or repeated hue resolves at Run 0 (1.0x) then resets to currentHue = null
   * - Lone Prism advances wheel by 1 step; direct Ash pop leaves Spectrum unchanged
   *
   * Returns (effectiveHalfStepsForPop, nextSpectrumStateAfterPop).
   */
  fun previewSpectrumForPop(
    current: SpectrumState,
    resolvedHue: Hue?,
    usedPrism: Boolean,
    isDirectAshGroup: Boolean = false,
  ): Pair<Int, SpectrumState> {
    if (isDirectAshGroup) {
      return current.halfSteps to current
    }
    if (resolvedHue == null) {
      if (!usedPrism) return current.halfSteps to current
      return if (current.currentHue == null) {
        2 to SpectrumState(currentHue = Hue.EMBER, runLength = 0, halfSteps = 2)
      } else {
        val nextHue = Hue.nextInWheel(current.currentHue)
        val nextRun = current.runLength + 1
        val nextHalf = 2 + nextRun
        nextHalf to SpectrumState(currentHue = nextHue, runLength = nextRun, halfSteps = nextHalf)
      }
    }

    val currentHue = current.currentHue
    if (currentHue == null) {
      return 2 to SpectrumState(currentHue = resolvedHue, runLength = 0, halfSteps = 2)
    }

    val expectedNext = Hue.nextInWheel(currentHue)
    return if (resolvedHue == expectedNext) {
      val nextRun = current.runLength + 1
      val nextHalf = 2 + nextRun
      nextHalf to SpectrumState(currentHue = resolvedHue, runLength = nextRun, halfSteps = nextHalf)
    } else {
      2 to SpectrumState(currentHue = null, runLength = 0, halfSteps = 2)
    }
  }

  fun advanceSpectrum(
    current: SpectrumState,
    resolvedHue: Hue?,
    usedPrism: Boolean,
    isDirectAshGroup: Boolean = false,
  ): SpectrumState = previewSpectrumForPop(current, resolvedHue, usedPrism, isDirectAshGroup).second

  private fun emptyInvalid(): GroupResolution = GroupResolution(
    coords = emptySet(),
    clearedAsh = emptySet(),
    shatteredStone = emptySet(),
    brandedCoords = emptySet(),
    brandVigilCost = 0,
    resolvedHue = null,
    isDirectAshGroup = false,
    prismCount = 0,
    basePower = 0,
    inkHue = null,
    inkAmount = 0,
    ashChipDamage = 0,
    stoneDefenseBonus = 0,
    isValid = false,
  )

  fun hasAnyValidMove(board: BoardState, currentRunHue: Hue? = null): Boolean {
    for (r in 0 until board.rows) {
      for (c in 0 until board.cols) {
        if (groupAt(board, r, c, currentRunHue).isValid) return true
      }
    }
    return false
  }

  /**
   * Enumerates all unique legal taps on the board deterministically using [groupAt].
   * Each returned [ValidClusterOption.anchor] is guaranteed to resolve to the exact same
   * [GroupResolution] when tapped via [GameCommand.TapCell].
   */
  fun findValidGroups(
    board: BoardState,
    spectrum: SpectrumState = SpectrumState(),
  ): List<ValidClusterOption> {
    val seenCoordSets = mutableSetOf<Set<BoardCoord>>()
    val options = mutableListOf<ValidClusterOption>()

    for (r in 0 until board.rows) {
      for (c in 0 until board.cols) {
        if (board.isColumnSealed(c)) continue
        val res = groupAt(board, r, c, spectrum.currentHue)
        if (!res.isValid) continue
        if (seenCoordSets.add(res.coords)) {
          val (effectiveHalfSteps, nextSpectrum) = previewSpectrumForPop(
            current = spectrum,
            resolvedHue = res.resolvedHue,
            usedPrism = res.prismCount > 0,
            isDirectAshGroup = res.isDirectAshGroup,
          )
          val previewDamage = if (res.isDirectAshGroup) {
            res.ashChipDamage
          } else {
            (res.basePower * effectiveHalfSteps) / 2
          }
          options.add(
            ValidClusterOption(
              anchor = BoardCoord(r, c),
              resolution = res,
              effectiveHalfSteps = effectiveHalfSteps,
              previewDamage = previewDamage,
              advancesWheel = nextSpectrum.runLength > spectrum.runLength,
            )
          )
        }
      }
    }

    return options.sortedWith(
      compareByDescending<ValidClusterOption> { it.advancesWheel }
        .thenByDescending { it.previewDamage }
        .thenByDescending { it.resolution.coords.size }
        .thenBy { it.anchor.row }
        .thenBy { it.anchor.col }
    )
  }

  /**
   * Ensures at least one legal board move exists without ever injecting un-accounted tiles.
   * 1) Deterministic RNG swaps among unsealed occupied cells (multiset-preserving).
   * 2) Fallback: discard all unsealed occupied tiles to BoardPaintDeck and redraw cleanly.
   */
  fun ensurePlayableMove(
    board: BoardState,
    paintDeck: BoardPaintDeckState,
    rng: SeededRng,
    currentRunHue: Hue? = null,
  ): BoardCascadeOutcome {
    if (hasAnyValidMove(board, currentRunHue)) {
      return BoardCascadeOutcome(board, paintDeck, rng, didReshuffle = false)
    }

    var currentRng = rng
    val mutableGrid = board.grid.map { it.toMutableList() }.toMutableList()
    val occupiedCoords = mutableListOf<BoardCoord>()
    for (r in 0 until board.rows) {
      for (c in 0 until board.cols) {
        if (!board.isColumnSealed(c) && mutableGrid[r][c].kind !is TileKind.Empty) {
          occupiedCoords.add(BoardCoord(r, c))
        }
      }
    }

    if (occupiedCoords.size >= 2) {
      repeat(24) {
        val (i1, r1) = currentRng.nextInt(occupiedCoords.size)
        val (i2, r2) = r1.nextInt(occupiedCoords.size)
        currentRng = r2
        if (i1 != i2) {
          val a = occupiedCoords[i1]
          val b = occupiedCoords[i2]
          val tmp = mutableGrid[a.row][a.col]
          mutableGrid[a.row][a.col] = mutableGrid[b.row][b.col]
          mutableGrid[b.row][b.col] = tmp
          val candidate = board.copy(grid = mutableGrid.map { it.toList() })
          if (hasAnyValidMove(candidate, currentRunHue)) {
            return BoardCascadeOutcome(candidate, paintDeck, currentRng, didReshuffle = true)
          }
        }
      }
    }

    // Fallback: discard unsealed occupied tiles to BoardPaintDeck and redraw cleanly
    var deck = paintDeck
    var nextId = board.nextTileId
    repeat(3) {
      val oldKinds = occupiedCoords.map { mutableGrid[it.row][it.col].kind }
      deck = BoardPaintDeck.discardKinds(deck, oldKinds)
      for (coord in occupiedCoords) {
        val (kind, nextDeck, nextRng) = BoardPaintDeck.drawTileKind(deck, currentRng)
        deck = nextDeck
        currentRng = nextRng
        mutableGrid[coord.row][coord.col] = Tile(id = nextId++, kind = kind, isBranded = false)
      }
      val candidate = board.copy(grid = mutableGrid.map { it.toList() }, nextTileId = nextId)
      if (hasAnyValidMove(candidate, currentRunHue)) {
        return BoardCascadeOutcome(candidate, deck, currentRng, didReshuffle = true)
      }
    }

    // Deterministic guaranteed rescue: ensure postcondition hasAnyValidMove(result, currentRunHue) == true.
    // 1. If there are >= 1 unsealed occupied cells, form an adjacent matching pair if possible,
    //    otherwise transmute the first unsealed occupied cell into a Prism (always a valid 1-tile move).
    var rescuedBoard = board.copy(grid = mutableGrid.map { it.toList() }, nextTileId = nextId)
    if (!hasAnyValidMove(rescuedBoard, currentRunHue)) {
      if (occupiedCoords.isNotEmpty()) {
        val occupiedSet = occupiedCoords.toSet()
        var paired = false
        for (coord in occupiedCoords) {
          val adj = orthogonalDirs
            .map { (dr, dc) -> BoardCoord(coord.row + dr, coord.col + dc) }
            .firstOrNull { it in occupiedSet }
          if (adj != null) {
            val chosenHue = (mutableGrid[coord.row][coord.col].kind as? TileKind.Playable)?.hue
              ?: (mutableGrid[adj.row][adj.col].kind as? TileKind.Playable)?.hue
              ?: currentRunHue?.let { Hue.nextInWheel(it) }
              ?: Hue.EMBER
            val replacedKinds = listOf(mutableGrid[coord.row][coord.col].kind, mutableGrid[adj.row][adj.col].kind)
            deck = BoardPaintDeck.discardKinds(deck, replacedKinds)
            mutableGrid[coord.row][coord.col] = Tile(id = nextId++, kind = TileKind.Playable(chosenHue))
            mutableGrid[adj.row][adj.col] = Tile(id = nextId++, kind = TileKind.Playable(chosenHue))
            paired = true
            break
          }
        }
        if (!paired) {
          val target = occupiedCoords.first()
          deck = BoardPaintDeck.discardKinds(deck, listOf(mutableGrid[target.row][target.col].kind))
          mutableGrid[target.row][target.col] = Tile(id = nextId++, kind = TileKind.Prism)
        }
        rescuedBoard = board.copy(grid = mutableGrid.map { it.toList() }, nextTileId = nextId)
      } else {
        // Extreme edge case: all columns sealed or board completely empty.
        // Unseal the column with shortest remaining seal duration (or col 0) and place a Prism so play cannot deadlock.
        val updatedSeals = board.sealedColumns.toMutableMap()
        val unsealedCol = updatedSeals.entries.minByOrNull { it.value }?.key ?: 0
        updatedSeals.remove(unsealedCol)
        val targetRow = (board.rows - 1 downTo 0).firstOrNull { r ->
          mutableGrid[r][unsealedCol].kind !is TileKind.Empty
        } ?: (board.rows - 1)
        val oldKind = mutableGrid[targetRow][unsealedCol].kind
        if (oldKind !is TileKind.Empty) {
          deck = BoardPaintDeck.discardKinds(deck, listOf(oldKind))
        }
        mutableGrid[targetRow][unsealedCol] = Tile(id = nextId++, kind = TileKind.Prism)
        rescuedBoard = board.copy(
          grid = mutableGrid.map { it.toList() },
          sealedColumns = updatedSeals.toMap(),
          nextTileId = nextId,
        )
      }
    }

    return BoardCascadeOutcome(
      board = rescuedBoard,
      paintDeck = deck,
      rng = currentRng,
      didReshuffle = true,
    )
  }

  // ============================================================================================
  // Match-3 Swap & 3-in-a-Row Horizontal/Vertical Line Detection + Cascade Engine
  // ============================================================================================

  fun swapTilesInBoard(
    board: BoardState,
    r1: Int,
    c1: Int,
    r2: Int,
    c2: Int,
  ): BoardState {
    val t1 = board.tileAt(r1, c1) ?: return board
    val t2 = board.tileAt(r2, c2) ?: return board
    val mutableGrid = board.grid.map { it.toMutableList() }.toMutableList()
    mutableGrid[r1][c1] = t2
    mutableGrid[r2][c2] = t1
    return board.copy(grid = mutableGrid.map { it.toList() })
  }

  /**
   * Scans every unsealed row and column for contiguous horizontal or vertical runs of >= 3 matching tiles:
   * - Playable(hue) + Prism wildcard (requires at least 1 Playable(hue) tile)
   * - Pure Prism runs >= 3
   * - Direct Ash runs >= 3 (ADR-006 / ADR-017)
   */
  fun findLineMatches(board: BoardState): List<LineMatch> {
    val results = mutableListOf<LineMatch>()
    val coveredByHue = mutableSetOf<BoardCoord>()

    fun scanLines(
      hue: Hue?,
      isAsh: Boolean,
      isPurePrism: Boolean,
      predicate: (TileKind) -> Boolean,
    ) {
      // Horizontal scan
      for (r in 0 until board.rows) {
        var c = 0
        while (c < board.cols) {
          if (board.isColumnSealed(c) || !predicate(board.grid[r][c].kind)) {
            c++
            continue
          }
          val start = c
          while (c < board.cols && !board.isColumnSealed(c) && predicate(board.grid[r][c].kind)) {
            c++
          }
          val len = c - start
          if (len >= 3) {
            val coords = (start until c).map { BoardCoord(r, it) }.toSet()
            val prisms = coords.count { board.grid[it.row][it.col].kind is TileKind.Prism }
            val hasHueTile = hue != null && coords.any { board.grid[it.row][it.col].kind == TileKind.Playable(hue) }
            when {
              hue != null && hasHueTile -> {
                coveredByHue.addAll(coords)
                results.add(LineMatch(coords, hue, isAshLine = false, prismCount = prisms, direction = MatchDirection.HORIZONTAL))
              }
              isPurePrism && coords.any { it !in coveredByHue } -> {
                results.add(LineMatch(coords, hue = null, isAshLine = false, prismCount = prisms, direction = MatchDirection.HORIZONTAL))
              }
              isAsh -> {
                results.add(LineMatch(coords, hue = null, isAshLine = true, prismCount = 0, direction = MatchDirection.HORIZONTAL))
              }
            }
          }
        }
      }

      // Vertical scan
      for (c in 0 until board.cols) {
        if (board.isColumnSealed(c)) continue
        var r = 0
        while (r < board.rows) {
          if (!predicate(board.grid[r][c].kind)) {
            r++
            continue
          }
          val start = r
          while (r < board.rows && predicate(board.grid[r][c].kind)) {
            r++
          }
          val len = r - start
          if (len >= 3) {
            val coords = (start until r).map { BoardCoord(it, c) }.toSet()
            val prisms = coords.count { board.grid[it.row][it.col].kind is TileKind.Prism }
            val hasHueTile = hue != null && coords.any { board.grid[it.row][it.col].kind == TileKind.Playable(hue) }
            when {
              hue != null && hasHueTile -> {
                coveredByHue.addAll(coords)
                results.add(LineMatch(coords, hue, isAshLine = false, prismCount = prisms, direction = MatchDirection.VERTICAL))
              }
              isPurePrism && coords.any { it !in coveredByHue } -> {
                results.add(LineMatch(coords, hue = null, isAshLine = false, prismCount = prisms, direction = MatchDirection.VERTICAL))
              }
              isAsh -> {
                results.add(LineMatch(coords, hue = null, isAshLine = true, prismCount = 0, direction = MatchDirection.VERTICAL))
              }
            }
          }
        }
      }
    }

    for (hue in Hue.WHEEL_ORDER) {
      scanLines(hue = hue, isAsh = false, isPurePrism = false) { kind ->
        kind == TileKind.Playable(hue) || kind is TileKind.Prism
      }
    }
    scanLines(hue = null, isAsh = false, isPurePrism = true) { it is TileKind.Prism }
    scanLines(hue = null, isAsh = true, isPurePrism = false) { it is TileKind.Ash }

    return results
  }

  private fun emptySwapInvalid(): SwapWaveResolution = SwapWaveResolution(
    coords = emptySet(),
    clearedAsh = emptySet(),
    shatteredStone = emptySet(),
    brandedCoords = emptySet(),
    brandVigilCost = 0,
    primaryHue = null,
    hueCounts = emptyMap(),
    isDirectAshOnly = false,
    prismCount = 0,
    basePower = 0,
    ashChipDamage = 0,
    stoneDefenseBonus = 0,
    isValid = false,
  )

  /**
   * Resolves all current 3+ horizontal/vertical line matches on [board] (plus optional Prism detonation
   * [extraCoords]) into a unified [SwapWaveResolution] including adjacent Ash clear, 4+ Stone shatter,
   * and Brand Vigil cost.
   */
  fun resolveBoardMatches(
    board: BoardState,
    currentRunHue: Hue? = null,
    extraCoords: Set<BoardCoord> = emptySet(),
    forcedHue: Hue? = null,
  ): SwapWaveResolution {
    val lineMatches = findLineMatches(board)
    if (lineMatches.isEmpty() && extraCoords.isEmpty()) return emptySwapInvalid()

    val hueCoordSets = mutableMapOf<Hue, MutableSet<BoardCoord>>()
    val nonAshCoords = mutableSetOf<BoardCoord>()
    val directAshCoords = mutableSetOf<BoardCoord>()

    for (m in lineMatches) {
      if (m.isAshLine) {
        directAshCoords.addAll(m.coords)
      } else {
        nonAshCoords.addAll(m.coords)
        if (m.hue != null) {
          hueCoordSets.getOrPut(m.hue) { mutableSetOf() }.addAll(m.coords)
        }
      }
    }

    for (coord in extraCoords) {
      if (coord.col !in 0 until board.cols || board.isColumnSealed(coord.col)) continue
      val tile = board.tileAt(coord.row, coord.col) ?: continue
      when (val k = tile.kind) {
        is TileKind.Ash -> directAshCoords.add(coord)
        is TileKind.Playable -> {
          nonAshCoords.add(coord)
          hueCoordSets.getOrPut(k.hue) { mutableSetOf() }.add(coord)
        }
        is TileKind.Prism -> {
          nonAshCoords.add(coord)
          if (forcedHue != null) {
            hueCoordSets.getOrPut(forcedHue) { mutableSetOf() }.add(coord)
          }
        }
        else -> Unit
      }
    }

    val isDirectAshOnly = nonAshCoords.isEmpty() && directAshCoords.isNotEmpty()
    val matchedCoords = (nonAshCoords + directAshCoords).toSet()
    if (matchedCoords.isEmpty()) return emptySwapInvalid()

    val hueCounts = hueCoordSets.mapValues { it.value.size }.filterValues { it > 0 }
    val wheelOrder = Hue.wheelOrderedFrom(currentRunHue)
    val expectedNext = currentRunHue?.let { Hue.nextInWheel(it) }
    val primaryHue: Hue? = when {
      isDirectAshOnly -> null
      forcedHue != null && (hueCounts[forcedHue] ?: 0) > 0 -> forcedHue
      expectedNext != null && (hueCounts[expectedNext] ?: 0) > 0 -> expectedNext
      hueCounts.isNotEmpty() -> {
        val maxCount = hueCounts.values.max()
        wheelOrder.first { (hueCounts[it] ?: 0) == maxCount }
      }
      else -> null
    }

    val prismCount = matchedCoords.count { board.tileAt(it.row, it.col)?.kind is TileKind.Prism }

    // ADR-006: Adjacent unsealed Ash clears alongside any hue/Prism match
    val clearedAsh = directAshCoords.toMutableSet()
    for (coord in nonAshCoords) {
      for ((dr, dc) in orthogonalDirs) {
        val nr = coord.row + dr
        val nc = coord.col + dc
        if (nc !in 0 until board.cols || board.isColumnSealed(nc)) continue
        if (board.tileAt(nr, nc)?.kind is TileKind.Ash) {
          clearedAsh.add(BoardCoord(nr, nc))
        }
      }
    }

    // ADR-007: Stone breaks ONLY on qualifying 4+ hue/Prism wave with bounded orthogonal chain
    val shatteredStone = mutableSetOf<BoardCoord>()
    if (!isDirectAshOnly && nonAshCoords.size >= 4) {
      var frontier = mutableListOf<BoardCoord>()
      for (coord in nonAshCoords.sortedWith(compareBy({ it.row }, { it.col }))) {
        for ((dr, dc) in orthogonalDirs) {
          val nr = coord.row + dr
          val nc = coord.col + dc
          if (nc !in 0 until board.cols || board.isColumnSealed(nc)) continue
          val nCoord = BoardCoord(nr, nc)
          if (board.tileAt(nr, nc)?.kind is TileKind.Stone && nCoord !in shatteredStone) {
            shatteredStone.add(nCoord)
            frontier.add(nCoord)
          }
        }
      }
      var chainStep = 0
      while (frontier.isNotEmpty() && chainStep < MAX_STONE_CHAIN_STEPS) {
        chainStep++
        val nextFrontier = mutableListOf<BoardCoord>()
        for (stoneCoord in frontier) {
          for ((dr, dc) in orthogonalDirs) {
            val nr = stoneCoord.row + dr
            val nc = stoneCoord.col + dc
            if (nc !in 0 until board.cols || board.isColumnSealed(nc)) continue
            val nCoord = BoardCoord(nr, nc)
            if (board.tileAt(nr, nc)?.kind is TileKind.Stone && nCoord !in shatteredStone) {
              shatteredStone.add(nCoord)
              nextFrontier.add(nCoord)
            }
          }
        }
        frontier = nextFrontier
      }
    }

    val allAffected = matchedCoords + clearedAsh + shatteredStone
    val brandedCoords = allAffected.filter { board.tileAt(it.row, it.col)?.isBranded == true }.toSet()

    val count = nonAshCoords.size
    val groupBonus = if (count >= 4) (count - 3).coerceAtMost(2) else 0
    val basePower = if (isDirectAshOnly) {
      0
    } else {
      ((count + 1) / 2).coerceAtMost(4) + groupBonus + prismCount + shatteredStone.size
    }
    val ashChipDamage = maxOf(0, clearedAsh.size - 1)
    val stoneDefenseBonus = shatteredStone.size * 2

    return SwapWaveResolution(
      coords = matchedCoords,
      clearedAsh = clearedAsh.toSet(),
      shatteredStone = shatteredStone.toSet(),
      brandedCoords = brandedCoords,
      brandVigilCost = brandedCoords.size * 2,
      primaryHue = primaryHue,
      hueCounts = hueCounts,
      isDirectAshOnly = isDirectAshOnly,
      prismCount = prismCount,
      basePower = basePower,
      ashChipDamage = ashChipDamage,
      stoneDefenseBonus = stoneDefenseBonus,
      isValid = true,
    )
  }

  /**
   * Non-mutating Match-3 swap preview:
   * - Must swap two orthogonally adjacent, unsealed, non-Empty, non-Stone tiles.
   * - Valid if the swap forms >= 1 horizontal or vertical line of 3+ matching tiles,
   *   OR if either swapped tile is a Prism (Prism <-> Hue detonates matching Hue along cross;
   *   Prism <-> Prism detonates full cross; Prism <-> Ash detonates adjacent Ash).
   */
  fun previewSwap(
    board: BoardState,
    r1: Int,
    c1: Int,
    r2: Int,
    c2: Int,
    currentRunHue: Hue? = null,
  ): SwapWaveResolution {
    if (r1 !in 0 until board.rows || c1 !in 0 until board.cols) return emptySwapInvalid()
    if (r2 !in 0 until board.rows || c2 !in 0 until board.cols) return emptySwapInvalid()
    if (kotlin.math.abs(r1 - r2) + kotlin.math.abs(c1 - c2) != 1) return emptySwapInvalid()
    if (board.isColumnSealed(c1) || board.isColumnSealed(c2)) return emptySwapInvalid()

    val t1 = board.tileAt(r1, c1) ?: return emptySwapInvalid()
    val t2 = board.tileAt(r2, c2) ?: return emptySwapInvalid()
    if (t1.kind is TileKind.Empty || t1.kind is TileKind.Stone) return emptySwapInvalid()
    if (t2.kind is TileKind.Empty || t2.kind is TileKind.Stone) return emptySwapInvalid()

    val swapped = swapTilesInBoard(board, r1, c1, r2, c2)
    val k1After = swapped.tileAt(r1, c1)?.kind ?: return emptySwapInvalid()
    val k2After = swapped.tileAt(r2, c2)?.kind ?: return emptySwapInvalid()

    // Special Prism swaps: always tactile and rewarding
    if (k1After is TileKind.Prism && k2After is TileKind.Prism) {
      val extra = mutableSetOf(BoardCoord(r1, c1), BoardCoord(r2, c2))
      for (c in 0 until swapped.cols) {
        if (!swapped.isColumnSealed(c)) {
          if (swapped.tileAt(r1, c)?.kind.let { it is TileKind.Playable || it is TileKind.Prism || it is TileKind.Ash }) {
            extra.add(BoardCoord(r1, c))
          }
          if (swapped.tileAt(r2, c)?.kind.let { it is TileKind.Playable || it is TileKind.Prism || it is TileKind.Ash }) {
            extra.add(BoardCoord(r2, c))
          }
        }
      }
      for (r in 0 until swapped.rows) {
        if (swapped.tileAt(r, c1)?.kind.let { it is TileKind.Playable || it is TileKind.Prism || it is TileKind.Ash }) {
          extra.add(BoardCoord(r, c1))
        }
        if (swapped.tileAt(r, c2)?.kind.let { it is TileKind.Playable || it is TileKind.Prism || it is TileKind.Ash }) {
          extra.add(BoardCoord(r, c2))
        }
      }
      return resolveBoardMatches(swapped, currentRunHue, extraCoords = extra)
    }

    if (k1After is TileKind.Prism || k2After is TileKind.Prism) {
      val prismCoord = if (k1After is TileKind.Prism) BoardCoord(r1, c1) else BoardCoord(r2, c2)
      val partnerCoord = if (k1After is TileKind.Prism) BoardCoord(r2, c2) else BoardCoord(r1, c1)
      val partnerKind = swapped.tileAt(partnerCoord.row, partnerCoord.col)?.kind
      when (partnerKind) {
        is TileKind.Playable -> {
          val targetHue = partnerKind.hue
          val extra = mutableSetOf(prismCoord, partnerCoord)
          for (c in 0 until swapped.cols) {
            if (!swapped.isColumnSealed(c) && swapped.tileAt(partnerCoord.row, c)?.kind == TileKind.Playable(targetHue)) {
              extra.add(BoardCoord(partnerCoord.row, c))
            }
          }
          for (r in 0 until swapped.rows) {
            if (!swapped.isColumnSealed(partnerCoord.col) && swapped.tileAt(r, partnerCoord.col)?.kind == TileKind.Playable(targetHue)) {
              extra.add(BoardCoord(r, partnerCoord.col))
            }
          }
          return resolveBoardMatches(swapped, currentRunHue, extraCoords = extra, forcedHue = targetHue)
        }
        is TileKind.Ash -> {
          val extra = mutableSetOf(prismCoord, partnerCoord)
          for ((dr, dc) in orthogonalDirs) {
            val nr = partnerCoord.row + dr
            val nc = partnerCoord.col + dc
            if (nc in 0 until swapped.cols && !swapped.isColumnSealed(nc) && swapped.tileAt(nr, nc)?.kind is TileKind.Ash) {
              extra.add(BoardCoord(nr, nc))
            }
          }
          return resolveBoardMatches(swapped, currentRunHue, extraCoords = extra)
        }
        else -> Unit
      }
    }

    return resolveBoardMatches(swapped, currentRunHue)
  }

  fun hasAnyValidSwap(board: BoardState, currentRunHue: Hue? = null): Boolean {
    for (r in 0 until board.rows) {
      for (c in 0 until board.cols) {
        if (board.isColumnSealed(c)) continue
        if (c + 1 < board.cols && !board.isColumnSealed(c + 1)) {
          if (previewSwap(board, r, c, r, c + 1, currentRunHue).isValid) return true
        }
        if (r + 1 < board.rows) {
          if (previewSwap(board, r, c, r + 1, c, currentRunHue).isValid) return true
        }
      }
    }
    return false
  }

  fun findValidSwaps(
    board: BoardState,
    spectrum: SpectrumState = SpectrumState(),
  ): List<ValidSwapOption> {
    val options = mutableListOf<ValidSwapOption>()
    for (r in 0 until board.rows) {
      for (c in 0 until board.cols) {
        if (board.isColumnSealed(c)) continue
        val neighbors = buildList {
          if (c + 1 < board.cols && !board.isColumnSealed(c + 1)) add(BoardCoord(r, c + 1))
          if (r + 1 < board.rows) add(BoardCoord(r + 1, c))
        }
        for (to in neighbors) {
          val preview = previewSwap(board, r, c, to.row, to.col, spectrum.currentHue)
          if (!preview.isValid) continue
          val (effectiveHalfSteps, nextSpectrum) = previewSpectrumForPop(
            current = spectrum,
            resolvedHue = preview.primaryHue,
            usedPrism = preview.prismCount > 0,
            isDirectAshGroup = preview.isDirectAshOnly,
          )
          val previewDamage = if (preview.isDirectAshOnly) {
            preview.ashChipDamage
          } else {
            (preview.basePower * effectiveHalfSteps) / 2
          }
          options.add(
            ValidSwapOption(
              from = BoardCoord(r, c),
              to = to,
              preview = preview,
              effectiveHalfSteps = effectiveHalfSteps,
              previewDamage = previewDamage,
              advancesWheel = nextSpectrum.runLength > spectrum.runLength,
            )
          )
        }
      }
    }
    return options.sortedWith(
      compareByDescending<ValidSwapOption> { it.advancesWheel }
        .thenByDescending { it.previewDamage }
        .thenByDescending { it.preview.coords.size }
        .thenBy { it.from.row }
        .thenBy { it.from.col }
    )
  }

  /**
   * Ensures ZERO pre-existing 3+ line matches sit idle on the board AND at least one valid adjacent swap exists.
   * 1) Breaks any idle 3+ lines.
   * 2) Tries deterministic RNG multiset swaps among unsealed occupied cells.
   * 3) Redraws unsealed tiles cleanly via BoardPaintDeck if needed.
   * 4) Deterministic guaranteed rescue placing a clean 1-swap-away bent-3 pattern (or Prism next to Playable).
   */
  fun ensurePlayableSwap(
    board: BoardState,
    paintDeck: BoardPaintDeckState,
    rng: SeededRng,
    currentRunHue: Hue? = null,
  ): BoardCascadeOutcome {
    var workingBoard = board
    var didReshuffle = false

    if (findLineMatches(workingBoard).isNotEmpty()) {
      val cleanGrid = workingBoard.grid.map { it.toMutableList() }.toMutableList()
      breakInitialLongRuns(cleanGrid, workingBoard.sealedColumns)
      workingBoard = workingBoard.copy(grid = cleanGrid.map { it.toList() })
    }

    if (findLineMatches(workingBoard).isEmpty() && hasAnyValidSwap(workingBoard, currentRunHue)) {
      return BoardCascadeOutcome(workingBoard, paintDeck, rng, didReshuffle = false)
    }

    var currentRng = rng
    val mutableGrid = workingBoard.grid.map { it.toMutableList() }.toMutableList()
    val occupiedCoords = mutableListOf<BoardCoord>()
    for (r in 0 until workingBoard.rows) {
      for (c in 0 until workingBoard.cols) {
        val kind = mutableGrid[r][c].kind
        if (!workingBoard.isColumnSealed(c) && kind !is TileKind.Empty && kind !is TileKind.Stone) {
          occupiedCoords.add(BoardCoord(r, c))
        }
      }
    }

    if (occupiedCoords.size >= 3) {
      // Every trial starts from the same source grid. A rejected permutation must never leak
      // into the next trial; otherwise 48 failed attempts silently become one uncontrolled shuffle.
      val shuffleBaseline = mutableGrid.map { it.toList() }
      repeat(48) {
        val (i1, r1) = currentRng.nextInt(occupiedCoords.size)
        val (i2, r2) = r1.nextInt(occupiedCoords.size)
        currentRng = r2
        if (i1 != i2) {
          val candidateGrid = shuffleBaseline.map { it.toMutableList() }.toMutableList()
          val a = occupiedCoords[i1]
          val b = occupiedCoords[i2]
          val tmp = candidateGrid[a.row][a.col]
          candidateGrid[a.row][a.col] = candidateGrid[b.row][b.col]
          candidateGrid[b.row][b.col] = tmp
          val candidate = workingBoard.copy(grid = candidateGrid.map { it.toList() })
          if (findLineMatches(candidate).isEmpty() && hasAnyValidSwap(candidate, currentRunHue)) {
            return BoardCascadeOutcome(candidate, paintDeck, currentRng, didReshuffle = true)
          }
        }
      }
    }

    // Fallback: redraw unsealed occupied tiles via BoardPaintDeck
    var deck = paintDeck
    var nextId = workingBoard.nextTileId
    repeat(4) {
      val oldKinds = occupiedCoords.map { mutableGrid[it.row][it.col].kind }
      deck = BoardPaintDeck.discardKinds(deck, oldKinds)
      for (coord in occupiedCoords) {
        val (kind, nextDeck, nextRng) = BoardPaintDeck.drawTileKind(deck, currentRng)
        deck = nextDeck
        currentRng = nextRng
        mutableGrid[coord.row][coord.col] = Tile(id = nextId++, kind = kind, isBranded = false)
      }
      breakInitialLongRuns(mutableGrid, workingBoard.sealedColumns)
      val candidate = workingBoard.copy(grid = mutableGrid.map { it.toList() }, nextTileId = nextId)
      if (findLineMatches(candidate).isEmpty() && hasAnyValidSwap(candidate, currentRunHue)) {
        return BoardCascadeOutcome(candidate, deck, currentRng, didReshuffle = true)
      }
    }

    // Deterministic guaranteed rescue: place a clean 1-swap-away bent-3 pattern or Prism+Playable pair
    // while strictly preserving all active Seal column durations unless ALL columns are sealed.
    val rescuedSeals = workingBoard.sealedColumns.toMutableMap()
    val rescuedGrid = mutableGrid.map { it.toMutableList() }.toMutableList()
    val rescueHue = currentRunHue?.let { Hue.nextInWheel(it) } ?: Hue.EMBER
    val blockerHue = Hue.nextInWheel(rescueHue)

    // Only unseal a column if ALL columns are sealed (true total deadlock); otherwise preserve every Seal!
    var unsealedCols = (0 until workingBoard.cols).filter { (rescuedSeals[it] ?: 0) == 0 }
    if (unsealedCols.isEmpty() && rescuedSeals.isNotEmpty()) {
      val shortestCol = rescuedSeals.entries.minByOrNull { it.value }?.key
      if (shortestCol != null) {
        rescuedSeals.remove(shortestCol)
        unsealedCols = (0 until workingBoard.cols).filter { (rescuedSeals[it] ?: 0) == 0 }
      }
    }

    // 1) Try horizontal 3-wide x 2-high unsealed region:
    //    [H, H, X]
    //    [?, ?, H] -> swapping (r, c+2) with (r+1, c+2) creates 3-in-a-row of H!
    var placedPattern = false
    for (r in (workingBoard.rows - 2) downTo 0) {
      for (c in 0..(workingBoard.cols - 3)) {
        if ((rescuedSeals[c] ?: 0) == 0 && (rescuedSeals[c + 1] ?: 0) == 0 && (rescuedSeals[c + 2] ?: 0) == 0) {
          val coords = listOf(
            BoardCoord(r, c),
            BoardCoord(r, c + 1),
            BoardCoord(r, c + 2),
            BoardCoord(r + 1, c + 2),
          )
          if (coords.all { rescuedGrid[it.row][it.col].kind !is TileKind.Empty }) {
            val old = coords.map { rescuedGrid[it.row][it.col].kind }
            deck = BoardPaintDeck.discardKinds(deck, old)
            rescuedGrid[r][c] = Tile(id = nextId++, kind = TileKind.Playable(rescueHue))
            rescuedGrid[r][c + 1] = Tile(id = nextId++, kind = TileKind.Playable(rescueHue))
            rescuedGrid[r][c + 2] = Tile(id = nextId++, kind = TileKind.Playable(blockerHue))
            rescuedGrid[r + 1][c + 2] = Tile(id = nextId++, kind = TileKind.Playable(rescueHue))
            placedPattern = true
            break
          }
        }
      }
      if (placedPattern) break
    }

    // 2) Try vertical 4-high pattern inside a single unsealed column (preserves all Seals even on 6-of-7 sealed boards!):
    //    [H, H, X, H]^T -> swapping (r+2, c) with (r+3, c) creates vertical 3-in-a-row of H!
    if (!placedPattern) {
      for (c in unsealedCols) {
        for (r in (workingBoard.rows - 4) downTo 0) {
          val coords = listOf(
            BoardCoord(r, c),
            BoardCoord(r + 1, c),
            BoardCoord(r + 2, c),
            BoardCoord(r + 3, c),
          )
          if (coords.all { rescuedGrid[it.row][it.col].kind !is TileKind.Empty }) {
            val old = coords.map { rescuedGrid[it.row][it.col].kind }
            deck = BoardPaintDeck.discardKinds(deck, old)
            rescuedGrid[r][c] = Tile(id = nextId++, kind = TileKind.Playable(rescueHue))
            rescuedGrid[r + 1][c] = Tile(id = nextId++, kind = TileKind.Playable(rescueHue))
            rescuedGrid[r + 2][c] = Tile(id = nextId++, kind = TileKind.Playable(blockerHue))
            rescuedGrid[r + 3][c] = Tile(id = nextId++, kind = TileKind.Playable(rescueHue))
            placedPattern = true
            break
          }
        }
        if (placedPattern) break
      }
    }

    // 3) Fallback: place Prism adjacent to Playable inside an unsealed column (vertical if only 1 unsealed column)
    if (!placedPattern) {
      val colA = unsealedCols.getOrElse(0) { 0 }
      val adjacentUnsealedCol = unsealedCols.firstOrNull { kotlin.math.abs(it - colA) == 1 }
      val colB = adjacentUnsealedCol ?: colA
      val rowA = workingBoard.rows - 1
      val rowB = if (colA == colB) (workingBoard.rows - 2).coerceAtLeast(0) else rowA
      val oldKinds = listOf(rescuedGrid[rowA][colA].kind, rescuedGrid[rowB][colB].kind)
        .filter { it !is TileKind.Empty }
      if (oldKinds.isNotEmpty()) {
        deck = BoardPaintDeck.discardKinds(deck, oldKinds)
      }
      rescuedGrid[rowA][colA] = Tile(id = nextId++, kind = TileKind.Prism)
      rescuedGrid[rowB][colB] = Tile(id = nextId++, kind = TileKind.Playable(rescueHue))
    }

    didReshuffle = true
    var finalBoard = workingBoard.copy(
      grid = rescuedGrid.map { it.toList() },
      sealedColumns = rescuedSeals.toMap(),
      nextTileId = nextId,
    )
    if (findLineMatches(finalBoard).isNotEmpty()) {
      breakInitialLongRuns(rescuedGrid, finalBoard.sealedColumns)
      finalBoard = finalBoard.copy(grid = rescuedGrid.map { it.toList() })
    }
    if (!hasAnyValidSwap(finalBoard, currentRunHue)) {
      val colA = unsealedCols.getOrElse(0) { 0 }
      val adjacentUnsealedCol = unsealedCols.firstOrNull { kotlin.math.abs(it - colA) == 1 }
      val colB = adjacentUnsealedCol ?: colA
      val rowA = workingBoard.rows - 1
      val rowB = if (colA == colB) (workingBoard.rows - 2).coerceAtLeast(0) else rowA
      rescuedGrid[rowA][colA] = Tile(id = nextId++, kind = TileKind.Prism)
      rescuedGrid[rowB][colB] = Tile(id = nextId++, kind = TileKind.Playable(rescueHue))
      // Ensure the rescue Prism does not accidentally form a 3-in-a-row with neighbors of rescueHue
      val neighborsToSanitize = listOf(
        BoardCoord(rowA, colA - 1), BoardCoord(rowA, colA + 1),
        BoardCoord(rowA - 1, colA), BoardCoord(rowA + 1, colA),
        BoardCoord(rowB, colB - 1), BoardCoord(rowB, colB + 1),
        BoardCoord(rowB - 1, colB), BoardCoord(rowB + 1, colB),
      )
      for (nc in neighborsToSanitize) {
        if (nc == BoardCoord(rowA, colA) || nc == BoardCoord(rowB, colB)) continue
        if (nc.row in 0 until workingBoard.rows && nc.col in 0 until workingBoard.cols && (rescuedSeals[nc.col] ?: 0) == 0) {
          if (rescuedGrid[nc.row][nc.col].kind == TileKind.Playable(rescueHue)) {
            rescuedGrid[nc.row][nc.col] = Tile(id = nextId++, kind = TileKind.Playable(blockerHue))
          }
        }
      }
      finalBoard = workingBoard.copy(
        grid = rescuedGrid.map { it.toList() },
        sealedColumns = rescuedSeals.toMap(),
        nextTileId = nextId,
      )
    }
    return BoardCascadeOutcome(
      board = finalBoard,
      paintDeck = deck,
      rng = currentRng,
      didReshuffle = didReshuffle,
    )
  }

  /**
   * Consumes [consumedCoords], applies downward gravity and PaintDeck refill (preserving 2 Empty
   * headroom rows and freezing sealed columns) WITHOUT premature reshuffle so multi-wave cascades
   * can resolve naturally in [GameEngine].
   */
  fun stepConsumeWithoutEnsure(
    board: BoardState,
    consumedCoords: Set<BoardCoord>,
    paintDeck: BoardPaintDeckState,
    rng: SeededRng,
  ): BoardCascadeOutcome {
    if (consumedCoords.isEmpty()) return BoardCascadeOutcome(board, paintDeck, rng, didReshuffle = false)

    val consumedKinds = mutableListOf<TileKind>()
    for (coord in consumedCoords) {
      if (!board.isColumnSealed(coord.col)) {
        val kind = board.tileAt(coord.row, coord.col)?.kind
        if (kind != null && kind !is TileKind.Empty) {
          consumedKinds.add(kind)
        }
      }
    }

    var deck = BoardPaintDeck.discardKinds(paintDeck, consumedKinds)
    var currentRng = rng
    var nextId = board.nextTileId

    val colsList = List(board.cols) { col ->
      if (board.isColumnSealed(col)) {
        // ADR-012: Sealed columns remain completely frozen during gravity and refill
        List(board.rows) { row -> board.grid[row][col] }
      } else {
        val survivors = mutableListOf<Tile>()
        for (row in 0 until board.rows) {
          val coord = BoardCoord(row, col)
          val tile = board.grid[row][col]
          if (coord !in consumedCoords && tile.kind !is TileKind.Empty) {
            survivors.add(tile)
          }
        }
        val targetOccupied = maxOf(MIN_OCCUPIED_ROWS, survivors.size).coerceAtMost(board.rows)
        val refillCount = (targetOccupied - survivors.size).coerceAtLeast(0)
        val emptyTopCount = (board.rows - targetOccupied).coerceAtLeast(0)

        val emptySockets = List(emptyTopCount) { Tile(id = nextId++, kind = TileKind.Empty) }
        val newTiles = List(refillCount) {
          val (kind, nextDeck, nextRng) = BoardPaintDeck.drawTileKind(deck, currentRng)
          deck = nextDeck
          currentRng = nextRng
          Tile(id = nextId++, kind = kind)
        }
        emptySockets + newTiles + survivors
      }
    }

    val nextGrid = List(board.rows) { r ->
      List(board.cols) { c -> colsList[c][r] }
    }

    return BoardCascadeOutcome(
      board = board.copy(grid = nextGrid, nextTileId = nextId),
      paintDeck = deck,
      rng = currentRng,
      didReshuffle = false,
    )
  }

  fun consumeAndCascade(
    board: BoardState,
    consumedCoords: Set<BoardCoord>,
    paintDeck: BoardPaintDeckState,
    rng: SeededRng,
    currentRunHue: Hue? = null,
  ): BoardCascadeOutcome {
    if (consumedCoords.isEmpty()) return BoardCascadeOutcome(board, paintDeck, rng, didReshuffle = false)
    val stepped = stepConsumeWithoutEnsure(board, consumedCoords, paintDeck, rng)
    return ensurePlayableMove(stepped.board, stepped.paintDeck, stepped.rng, currentRunHue)
  }

  /**
   * ADR-008 + ADR-012 Wall Rise & Overflow:
   * - Check BEFORE rising: if unsealed columns exist and NONE have an Empty socket in row 0 -> OVERFLOW!
   *   Overflow discards unsealed rows 0..1 to BoardPaintDeck (replacing with Empty), skips Wall rise.
   * - Otherwise: each unsealed column shifts rows 1..6 -> 0..5, pushes incomingRow[c] into row 6,
   *   and draws a new incoming tile. Sealed columns stay frozen (incomingRow[c] preserved).
   */
  fun riseIncomingRow(
    board: BoardState,
    paintDeck: BoardPaintDeckState,
    rng: SeededRng,
    currentRunHue: Hue? = null,
  ): WallRiseOutcome {
    val unsealedCols = (0 until board.cols).filter { !board.isColumnSealed(it) }
    val isOverflow = unsealedCols.isNotEmpty() && unsealedCols.none { col ->
      board.grid[0][col].kind is TileKind.Empty
    }

    var deck = paintDeck
    var currentRng = rng
    var nextId = board.nextTileId

    if (isOverflow) {
      val burnedKinds = mutableListOf<TileKind>()
      val mutableGrid = board.grid.map { it.toMutableList() }.toMutableList()
      for (r in 0 until minOf(2, board.rows)) {
        for (c in unsealedCols) {
          val tile = mutableGrid[r][c]
          if (tile.kind !is TileKind.Empty) {
            burnedKinds.add(tile.kind)
          }
          mutableGrid[r][c] = Tile(id = nextId++, kind = TileKind.Empty, isBranded = false)
        }
      }
      deck = BoardPaintDeck.discardKinds(deck, burnedKinds)
      val burnedBoard = board.copy(grid = mutableGrid.map { it.toList() }, nextTileId = nextId)
      val ensured = ensurePlayableSwap(burnedBoard, deck, currentRng, currentRunHue)
      return WallRiseOutcome(
        board = ensured.board,
        paintDeck = ensured.paintDeck,
        rng = ensured.rng,
        overflowTriggered = true,
        wallSkippedDueToOverflow = true,
        burnedTopRowsCount = burnedKinds.size,
        didReshuffle = ensured.didReshuffle,
      )
    }

    val discardedTopKinds = mutableListOf<TileKind>()
    val mutableGrid = MutableList(board.rows) { r ->
      MutableList(board.cols) { c -> board.grid[r][c] }
    }
    val nextIncoming = board.incomingRow.toMutableList()

    for (c in 0 until board.cols) {
      if (board.isColumnSealed(c)) continue
      val topKind = board.grid[0][c].kind
      if (topKind !is TileKind.Empty) {
        discardedTopKinds.add(topKind)
      }
      for (r in 0 until board.rows - 1) {
        mutableGrid[r][c] = board.grid[r + 1][c]
      }
      mutableGrid[board.rows - 1][c] = board.incomingRow[c]

      val (drawnKind, nextDeck, nextRng) = BoardPaintDeck.drawTileKind(deck, currentRng)
      deck = nextDeck
      currentRng = nextRng
      nextIncoming[c] = Tile(id = nextId++, kind = drawnKind)
    }

    deck = BoardPaintDeck.discardKinds(deck, discardedTopKinds)
    val shiftedBoard = board.copy(
      grid = mutableGrid.map { it.toList() },
      incomingRow = nextIncoming.toList(),
      nextTileId = nextId,
    )
    val ensured = ensurePlayableSwap(shiftedBoard, deck, currentRng, currentRunHue)
    return WallRiseOutcome(
      board = ensured.board,
      paintDeck = ensured.paintDeck,
      rng = ensured.rng,
      overflowTriggered = false,
      wallSkippedDueToOverflow = false,
      burnedTopRowsCount = 0,
      didReshuffle = ensured.didReshuffle,
    )
  }

  fun recolourTile(
    board: BoardState,
    row: Int,
    col: Int,
    targetHue: Hue,
  ): BoardState {
    if (col !in 0 until board.cols || board.isColumnSealed(col)) return board
    val existing = board.tileAt(row, col) ?: return board
    if (existing.kind is TileKind.Empty) return board
    val updatedGrid = board.grid.mapIndexed { rIdx, rowList ->
      rowList.mapIndexed { cIdx, tile ->
        if (rIdx == row && cIdx == col) existing.copy(kind = TileKind.Playable(targetHue)) else tile
      }
    }
    return board.copy(grid = updatedGrid)
  }

  fun paintTiles(
    board: BoardState,
    targetKind: TileKind,
    count: Int,
    rng: SeededRng,
  ): Pair<BoardState, SeededRng> {
    if (targetKind is TileKind.Empty || count <= 0) return board to rng
    var currentRng = rng
    val mutableGrid = board.grid.map { it.toMutableList() }.toMutableList()
    val candidates = mutableListOf<BoardCoord>()
    for (r in 0 until board.rows) {
      for (c in 0 until board.cols) {
        if (!board.isColumnSealed(c)) {
          val kind = mutableGrid[r][c].kind
          if (kind !is TileKind.Empty && kind != targetKind) {
            candidates.add(BoardCoord(r, c))
          }
        }
      }
    }
    val (shuffled, nextRng) = currentRng.shuffled(candidates)
    currentRng = nextRng
    for (coord in shuffled.take(count)) {
      mutableGrid[coord.row][coord.col] = mutableGrid[coord.row][coord.col].copy(kind = targetKind)
    }
    return board.copy(grid = mutableGrid.map { it.toList() }) to currentRng
  }

  fun brandTiles(
    board: BoardState,
    count: Int,
    rng: SeededRng,
  ): Triple<BoardState, Int, SeededRng> {
    if (count <= 0) return Triple(board, 0, rng)
    val candidates = mutableListOf<BoardCoord>()
    for (r in 0 until board.rows) {
      for (c in 0 until board.cols) {
        val tile = board.grid[r][c]
        if (!board.isColumnSealed(c) && tile.kind !is TileKind.Empty && !tile.isBranded) {
          candidates.add(BoardCoord(r, c))
        }
      }
    }
    if (candidates.isEmpty()) return Triple(board, 0, rng)
    val (shuffled, nextRng) = rng.shuffled(candidates)
    val chosen = shuffled.take(count).toSet()
    val updatedGrid = board.grid.mapIndexed { rIdx, rowList ->
      rowList.mapIndexed { cIdx, tile ->
        if (BoardCoord(rIdx, cIdx) in chosen) tile.copy(isBranded = true) else tile
      }
    }
    return Triple(board.copy(grid = updatedGrid), chosen.size, nextRng)
  }

  fun shatterStones(
    board: BoardState,
    maxCount: Int,
  ): Pair<BoardState, Int> {
    if (maxCount <= 0) return board to 0
    val mutableGrid = board.grid.map { it.toMutableList() }.toMutableList()
    var shattered = 0
    for (r in 0 until board.rows) {
      for (c in 0 until board.cols) {
        if (shattered >= maxCount) break
        if (!board.isColumnSealed(c) && mutableGrid[r][c].kind is TileKind.Stone) {
          mutableGrid[r][c] = mutableGrid[r][c].copy(kind = TileKind.Ash)
          shattered++
        }
      }
    }
    return board.copy(grid = mutableGrid.map { it.toList() }) to shattered
  }
}
