# Grammar package — behavioural-fidelity audit

Scope: `com.techbucketdivision.mystcraft.symbol.grammar` — `GrammarTree`, `Grammar`, `GrammarRules`, `Rule`.

Authoritative source (MC 1.12.2):

* `grammar/GrammarTree.java`
* `grammar/GrammarGenerator.java`
* `utility/WeightedItemSelector.java`

Spec: `docs/REQUIREMENTS.md` §4.4.

Method: every method was compared against its original both by reading and by a normalised
token-level diff (comments/whitespace stripped, port identifiers mapped onto the original's).
After that pass, **every remaining difference in `GrammarTree` is cosmetic** (indexed `for` vs
enhanced `for`, `isEmpty()` vs `size() == 0`, `computeIfAbsent` vs the original's
`getOrCreateList` helper, `remove(0)` vs `removeFirst()`, brace style). The semantic deltas are
catalogued below.

**Result: no new genuine bug was found. No code changes were made.**

---

## 0. Context — the two bugs already fixed (not re-reported)

1. `Node.getRightPosition()` — the `&&` chain had been hoisted, making the sibling recursion
   unconditional → `StackOverflowError` on two unpositioned siblings.
2. `Grammar.randomRule()` — used the even-pick fallback, so a rank-`null` (connect-only, cyclic)
   rule could be selected during random expansion.

Both were used as the pattern for this sweep.

### Confirmation that the `getRightPosition` fix is exactly the original

Worth recording, because it explains *why* the hoist was fatal. The parent loop walks
right-to-left. `rightPos` is `null` on entry (line 70 returns early otherwise) and can only become
non-null in the *self* branch, which fires at the node's own index — i.e. after every
higher-indexed sibling has already been visited. The `else if` therefore **never** executes with
`rightPos != null`: in both the original and the restored port it is dead code. Hoisting the
recursive call out of the guard is what brought that dead branch to life, and with it the mutual
recursion. The restored port reproduces the original exactly, including the in-body `--i` that
skips the left neighbour after consuming it.

---

## 1. Divergences found

Severity: **none** = provably no behavioural difference; **info** = intentional modernisation or
unreachable difference; **low** = reachable only under contrived conditions.

### D1 — `GrammarTree.connectSubtreeShortest`: extra `pick != null` guard — *info*

* **Original:** inside `if (options.size() > 0 && getTotalWeight(options) > 0)` it unconditionally
  does `reverseExpand(subroot, getRandomItem(rand, options))`, `replaceNodeWithTree`, `return true`.
  A `null` pick would NPE inside `reverseExpand`.
* **Port:** wraps the same three statements in `if (pick != null) { … }`, so a `null` pick would fall
  through to the shortest-path branch instead of returning `true`.
* **Reachability:** unreachable. The guard is entered only when `totalWeight > 0`, which means at
  least one rule has `weight > 0`; `pickWeighted` assigns `last` for every such item, so it cannot
  return `null`.
* **Consequence:** none. Defensive-only.
* **Not fixed** — removing it would reintroduce an NPE path for no gain.

### D2 — `GrammarTree.buildSubtree`: `rules != null` check dropped — *none*

* **Original:** `while (rules != null && rules.size() == 1)`; `getParentRules` returns a list via
  `CollectionUtils.getOrCreateElement`, so it is never `null` anyway.
* **Port:** `while (rules.size() == 1)`; `Grammar.parentRules` returns `List.of()` for unknown tokens.
* **Consequence:** none — the short-circuit had nothing to protect.

### D3 — `Grammar.parentRules` no longer materialises an empty list in `REVERSE` — *none (port is safer)*

* **Original:** `getParentRules` used `getOrCreateElement`, which **inserts** an empty list into
  `reverseLookup` as a side effect of a *query*.
* **Port:** pure read; returns `List.of()`.
* **Consequence:** none in the original (the path table was frozen before any query). In the port it
  actively matters: `rebuild()` can run at any time and iterates `REVERSE.keySet()`, so keeping the
  original's side effect would have seeded the path table with spurious tokens. Dropping it is
  correct.

### D4 — `terminals` defaults to `List.of()` instead of `null` — *info (fixes a latent original NPE)*

* **Original:** `terminals` is `null` until `parseTerminals` runs. `getInsertions` then does
  `left = terminals.size()` for any leaf with no position → **NPE**. `GrammarAPIDelegate
  .generateFromToken(root, rand)` (the no-pages overload) never calls `parseTerminals`, so that
  overload NPEs in the original. Every other call site (`AgeData.getSymbols`,
  `DebugFlags global.grammar.generate`) calls `parseTerminals` first, with an empty list when there
  are no pages.
* **Port:** `terminals = List.of()` initially, so `Grammar.generateFromToken(token, rand)` behaves
  exactly like `parseTerminals(List.of())` + `getExpanded`.
* **Consequence:** identical output to the original's working path; the crashing path now works.
  This is the "null means unknown" substitution the audit brief asked about — it is the *only* one,
  and it does not change control flow (`terminals.size()` is `0` exactly when
  `parseTerminals(emptyList)` would have made it `0`).
* **Checked:** the "unplaced leaf" rule of §4.4.3 still lands at the end. `left = terminals.size()`
  equals `out.size()` at loop entry, giving `i = out.size() + 1` and `out.addAll(out.size(), …)` —
  an append. With no written symbols, `out` is empty, `i = 1`, and everything is inserted at index
  `0` in reverse product order, which reconstructs document order. Verified against the original's
  index arithmetic character by character.

### D5 — `Rule.weight()` / `Grammar.weightFor`: `0f` instead of NPE — *info, unreachable*

* **Original:** `ranks.get(parent).rankweights.get(rank)` — NPEs if the parent has no `RankData`, or
  if the rank has no weight.
* **Port:** `weightFor` returns `0f` for both cases.
* **Can a parent end up with no rank data?** No. `registerRule` creates `RANKS[parent]` on the first
  rule with a non-null rank, and `Rule.weight()` returns `0f` early for a `null` rank — so
  `weightFor` is only ever called for a `(parent, rank)` pair that `registerRule` has already
  recorded. `rankSizes` is grown to cover `rule.rank`, and `buildRankWeights` writes a weight for
  every index `0 … rankSizes.size()-1`, so `rankWeights.get(rank)` is never `null` either.
* **Can it silently disable a token?** Only if the above were violated. The guard against that is
  `warnUnexpandableTokens()`, which **is** correctly wired: it runs at the end of every `rebuild()`
  (after `buildRankWeights`, so the weights it reads are current), iterates `MAPPINGS`, and logs any
  parent whose rules all have `weight == 0` — which is exactly the "parent has no rank data" case,
  plus the "every rule registered with rank `null` by mistake" case.
* **Verified against the shipped grammar:** no token triggers the warning. Every parent that has a
  `rank == null` rule (`Biomes`, `BiomesExt`, each `*0`/`*Ext` sequence token, each modifier
  `X`/`X_Ext`, `Sunset_Ext`) also has at least one ranked rule.

### D6 — `pickEvenly` uses `nextInt(n)` where the original used `nextFloat() * n` — *info*

* Used only by `getShortestPath` to break ties between equal-length paths. The original called
  `WeightedItemSelector.getRandomItem` on a `List<List<Rule>>`; those elements are not
  `IWeightedItem`, so every weight was `1.0F`, total `n > 0`, i.e. a uniform pick.
* **Consequence:** identical distribution, different RNG consumption. Seed-for-seed parity with
  1.12.2 is impossible regardless (`RandomSource` vs `java.util.Random`), so this is not a fidelity
  loss.

### D7 — dropped `LoggerUtils.warn` in the selection helpers, `describe()` prints `null` not `?` — *none*

Cosmetic. `pickWeighted`/`pickEvenly` return `last` silently where the original logged first;
`describe()` is the port of the debug-only `print()`/`printNode()`.

### D8 — `toIdentifiers` drops non-`Identifier` tokens — *info*

The original returned every `ResourceLocation` and let `AgeController.reconstruct` log "unknown
symbol id" for leftovers. The port filters them out with a debug log. Net-equivalent; a consequence
of the intentional `String`-token modernisation.

### D9 — lazy `dirty`/`rebuild` replaces the original's frozen-registration invariant — *low*

* **Original:** `registerRule` throws `"You must register your rules before the grammar is
  finalized!"` once `buildGrammar()` has run; `getShortestPaths` throws if called before it.
  Registration and use are hard-separated.
* **Port:** rules may be registered at any time; derived tables rebuild lazily on the next query
  (documented in the class javadoc, and required so biome/fluid symbols can register per server).
* **Consequence:** `parentRules()` / `rules()` hand out `Collections.unmodifiableList` *views* of the
  live `REVERSE`/`MAPPINGS` lists. `GrammarTree.connectSubtreeShortest` iterates such a view while
  calling back into `Grammar` (`getShortestPath` → `ensureBuilt`). If another thread flipped `dirty`
  in between, `flushPending()` would mutate those lists mid-iteration →
  `ConcurrentModificationException`.
* **Reachable?** Not single-threaded: `generateFromToken`/`expandAge` call `ensureBuilt()` up front,
  after which `dirty` stays `false` for the whole expansion, and nothing inside `GrammarTree`
  registers rules. Cross-thread it would require `BiomeSymbols.registerAll` (server-start) to run
  concurrently with an Age expansion (server thread).
* **Not fixed** — the only faithful fix is the original's hard freeze, which would break the per-server
  symbol registration the port deliberately supports. Recorded as a known constraint.

### D10 — `Rule` has no `equals`/`hashCode`, so `REGISTERED` dedups by identity — *low*

`registerRule`'s `if (!REGISTERED.add(rule)) return;` only rejects the *same object* twice, not a
semantically equal duplicate. Registering an equal rule twice would double-count `rankSizes` and
skew every weight for that parent. Currently prevented by the callers (`coreRegistered` guard;
`SymbolRegistry.registerLate(...)` gating `addSymbolRule`), and the original had no dedup at all
(it froze registration instead). Recorded, not changed — adding value equality to `Rule` would
change `REGISTERED`/path-table identity semantics, i.e. restructuring.

---

## 2. Explicit checks the brief asked for

### Short-circuit / evaluation order

Every `&&`, `||` and ternary in the port was matched against the original. Preserved in all cases:

| Site | Guard |
|---|---|
| `Node.getLeftPosition` (child loop) | `leftPos == null \|\| leftPos > pos` — `\|\|` must short-circuit or the compare unboxes `null`. ✔ |
| `Node.getRightPosition` (child loop) | `rightPos == null \|\| rightPos < pos`. ✔ |
| `Node.getRightPosition` (sibling scan) | `rightPos != null` before recursing — the fixed bug. ✔ |
| `GrammarTree.getInsertions` | `getRightPosition()` called **only** when `getLeftPosition()` returned `null`. ✔ |
| `GrammarTree.reverseExpand` | `subroot != null && product.equals(subroot.token)`. ✔ |
| `GrammarTree.connectSubtreeShortest` | `!options.isEmpty() && totalWeight(...) > 0f`. ✔ |
| `GrammarTree.getExpanded` | `rules != null && rules.size() == 1`. ✔ |
| `Grammar.computePaths` | `!pathsToTarget.isEmpty() && getFirst().size() > path.size()`, then `isEmpty() \|\| getFirst().size() == path.size()`. ✔ |

No side-effecting or recursive call was hoisted out of a guard, and no guarded call was replaced by
an unguarded one.

### Loop index arithmetic (including the deliberate `--i` / `i--` mutations)

* `parseTerminals`: `for (int i = terminals.size(); i > 0; --i)` with `terminals.get(i-1)` and
  position `i-1` — identical. Last-to-first order preserved.
* `getExpanded` single-rule pass: `expandUnexploredNode(i--, …)` — passes the current index, then
  decrements so the loop's `++i` re-examines the same slot, now holding the first product.
  Identical.
* `expandUnexploredNode`: `for (int i = products.size(); i > 0; --i)` with `addChild(0, …)` and
  `unexplored.add(index, …)` — right-to-left insertion at the front yields left-to-right final
  order in both children and `unexplored`. Identical.
* `reverseExpand`: same right-to-left walk; `subroot` replaces the **right-most** matching token
  because `subroot` is nulled after the first (i.e. right-most) match. Identical.
* `getRightPosition`: the in-body `--i` (skip the consumed left neighbour) is preserved; the port
  captures `sibling` from index `i` *before* the mutation, which is the same element the original
  re-reads in its `else if`, since `i` only changes in the mutually exclusive `if` branch.
* `getLeftPosition`: forward scan, uses `i + 1` (right neighbour), `break` after the self match — no
  further-sibling scan, matching the original's asymmetry with `getRightPosition`.
* `getExpanded` insertion pass: `for (int i = out.size() + 1; i > 0; --i)`; bound captured once,
  before any insertion. `insertRight` → `out.addAll(i, …)` (after position `i-1`), `insertLeft` →
  `out.addAll(i-1, …)` (before position `i-1`). Inner `for (int j = products.size(); j > 0; --j)`
  inserts in reverse so the products land in order. Identical.
  * Bound safety: `i = out.size() + 1` makes `addAll(i, …)` an out-of-range insert, but
    `insertRight` keys are terminal positions in `[0, terminals.size()-1]`, so key `out.size()` is
    never present. Only `insertLeft` carries key `terminals.size()`, and it uses `i-1`. Same in the
    original.

### Null handling

The only substituted default is D4 (`terminals`), analysed above. Everywhere else `null` is
preserved as "unknown": `Node.leftPos`/`rightPos` (no `-1`/`0` sentinel), `Rule.rank`,
`Grammar.rules()` returning `null` for a terminal (distinct from `parentRules()` returning an empty
list), `shortestPaths()` returning `null` for unreachable, `randomRule()` returning `null`.
`getInsertions`'s three-way `left == null` / `right == null` / else cascade is byte-identical.

### `equals` vs `==`

* **Nodes:** the original's `parent.children.get(i).equals(this)` resolves to `Object.equals`, i.e.
  identity — `GrammarNode` overrides `clone()` and `toString()` but **not** `equals`/`hashCode`.
  Confirmed still true of the port's `Node`, so the port's `sibling == this` is exactly equivalent.
  `unexplored.remove(node)` (`List.remove(Object)`) is likewise identity-based in both.
* **Tokens:** all comparisons are value-based. `node.token.equals(subroot.token)`,
  `node.token.equals(rule.parent())`, `product.equals(subroot.token)`,
  `rules.getFirst().parent().equals(root.token)`, `target.equals(token)` — `String.equals` in the
  port where the original had `ResourceLocation.equals`. No `==` on tokens anywhere.

### Termination

| Site | Argument |
|---|---|
| `parseTerminals` | Finite countdown over the written list. |
| `getExpanded` single-rule pass | Could only spin on a token whose *sole* rule reproduces itself. No such rule exists (single-rule tokens are `Age`, `Spawning0`→ε, and the ε-only block categories); symbol tokens have no `MAPPINGS` entry, so no cycle closes. Same risk profile as the original. |
| `getExpanded` subroot loop | `subroots` strictly shrinks (`removeFirst` each pass) or `break`s. |
| `connectSubtreeShortest` | Single bounded pass over `unexplored`; returns on the first success. |
| `getLeftPosition` | Recursion moves strictly rightward among siblings (`i+1`) and downward into children; both are well-founded. |
| `getRightPosition` | After the fix, the sibling branch is unreachable (see §0), so recursion is only downward into children plus one step to the left neighbour, which itself cannot recurse back. |
| `getInsertions` | Plain tree DFS. The tree is acyclic: `unexplored` is cleared at the top of `getExpanded` and repopulated only from the main tree, so `connectSubtreeShortest` can never splice a subroot into one of its own descendants. Same as the original. |
| `connectSubtreeShortest` / `buildSubtree` reverse-expansion | `buildSubtree`'s `while (rules.size() == 1)` walks up single-parent chains and stops at the root token; the chain cannot cycle in this grammar. Identical to the original. |
| `Grammar.explore` | Bounded by `MAX_EXPLORE_DEPTH = 128` (a port addition). See below. |
| `Grammar.computePaths` BFS | FIFO queue seeded with length-1 paths and extended by one rule at a time, so path lengths are non-decreasing. A target only accepts paths of its already-recorded minimal length, and only accepted paths are extended, so expansion stops at the shortest-path frontier. `target.equals(token)` cuts self-loops. The `clear()` branch is dead (BFS never delivers a shorter path later) — true of the original too. |

**Depth-guard sanity check.** With the strict random pick in place, the only recursion in `explore`
is the left-recursive `*Adv` rules, and the guard is far from binding:

* `SunsAdv → SunsAdv Sun` (rank 4, weight 1) vs `→ Sun` (rank 2, weight 3): P(recurse) = 1/4.
* `DoodadsAdv` (ranks 5/2 → weights 1/4): P(recurse) = 1/5.
* `BiomesAdv → BiomesAdv Biome` (rank 2, weight 2) vs `→ Biome` (rank 3, weight 1): P(recurse) = 2/3,
  the worst case. P(depth ≥ 128) ≈ (2/3)^127 ≈ 1e-22.

So the guard never fires in the shipped grammar; it only caps a pathological add-on grammar. The
warning it logs makes such a case visible rather than silent.

### Insertion-point ordering vs §4.4.3

Verified identical to the original and consistent with the spec: `getInsertions` is a post-order DFS
so leaves are collected left-to-right; a leaf with a known left position is inserted immediately
before that written symbol, one with only a right position immediately after it, and one with
neither goes to the end (`terminals.size()`). Written symbols keep their order and their relative
positions because all insertion indices are taken from the pre-insertion `out`, and the outer loop
runs high-index-first so earlier insertions never shift later ones.

### `GrammarRules` vs §4.4.2

All rules and ranks cross-checked against the spec table, including the two helper shapes:

* `sequence(seq0, adv, ext, item, moreRank, oneRank)` = `seq0 → adv (1)`, `adv → adv item (moreRank)`,
  `adv → item (oneRank)`, `seq0 → ext item (null)`, `ext → ext item (null)`, `ext → ε (1)`.
* `modifierSequence(seq, adv, ext, basic, …)` differs from `sequence` in exactly one production —
  `ext → seq (null)` rather than `ext → ext basic (null)` — which matches the spec's
  `Angle_Ext → Angle (null)`.

Ranks verified one by one: `Age` 0; `Spawning0` 10; Biomes 1/2/3/null/null/1; Suns 4/2; Moons 2/2;
Starfields 3/2 + `Starfield → ε (1)`; Doodads 5/2 + `Doodad → ε (0)`; Visuals 3/2 + `Visual → ε (1)`;
FeatureLarge 2/2 + ε(4); FeatureMedium 2/3 + ε(4); FeatureSmall 2/4 + ε(4); Effects 3/2 + ε(1);
`SunsetUncommon → ε (2) | Sunset (3)`, `Sunset → ε (1)`, `Sunset_Ext → Sunset (null) | ε (1)`;
Angle/Period/Phase/Color 2/3, Gradient 2/2; block categories ε(0); `BLOCK_NONSOLID → BlockFluid (1) |
BlockGas (2)`. No discrepancy.

`buildRankWeights` is a character-for-character port (verified by normalised diff), including the
`weight != 1 && count > 0` guard, the integer division `lastTotal / count + step`, and the
`weight += step` at the tail of each iteration.

---

## 3. Summary

| # | Site | Severity | Fixed |
|---|---|---|---|
| D1 | `GrammarTree.connectSubtreeShortest` — extra `pick != null` guard | info (unreachable) | no |
| D2 | `GrammarTree.buildSubtree` — `rules != null` dropped | none | no |
| D3 | `Grammar.parentRules` — no `getOrCreateElement` side effect | none | no |
| D4 | `GrammarTree.terminals` — `List.of()` instead of `null` | info (fixes latent NPE) | no |
| D5 | `Grammar.weightFor` — `0f` instead of NPE | info (unreachable; warning wired) | no |
| D6 | `Grammar.pickEvenly` — `nextInt` vs `nextFloat` | info (same distribution) | no |
| D7 | dropped warn logs, `describe()` prints `null` | none | no |
| D8 | `Grammar.toIdentifiers` — drops non-id tokens | info | no |
| D9 | lazy `dirty`/`rebuild` vs frozen registration | low (cross-thread CME only) | no |
| D10 | `Rule` identity-based dedup in `REGISTERED` | low (guarded by callers) | no |

No genuine bug remains in `GrammarTree`, `Grammar`, `GrammarRules` or `Rule`. The two known defects
are the only ones of their kind in this package.
