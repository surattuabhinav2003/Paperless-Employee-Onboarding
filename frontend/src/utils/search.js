/**
 * Candidate search.
 *
 * HR types fast and misspells names, so an exact `LIKE` match is the wrong tool:
 * "Nobnd" or "aarv" should still find the person. This scores every candidate
 * against the query across four fields and ranks them, tolerating typos,
 * initials and skipped letters.
 *
 * It runs on the records already in the page (capped at 100), so results are
 * instant and there is no round-trip per keystroke.
 */

/** Lowercase, strip accents, and reduce punctuation to spaces. */
export function normalize(value) {
  return String(value ?? '')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, ' ')
    .trim()
}

/** Damerau-Levenshtein: counts a swapped pair of letters as one mistake. */
export function editDistance(a, b) {
  if (a === b) return 0
  if (!a.length) return b.length
  if (!b.length) return a.length

  const rows = []
  for (let i = 0; i <= a.length; i += 1) rows.push([i, ...Array(b.length).fill(0)])
  for (let j = 0; j <= b.length; j += 1) rows[0][j] = j

  for (let i = 1; i <= a.length; i += 1) {
    for (let j = 1; j <= b.length; j += 1) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1
      rows[i][j] = Math.min(
        rows[i - 1][j] + 1,
        rows[i][j - 1] + 1,
        rows[i - 1][j - 1] + cost,
      )
      // Transposition, so "Nboond" is one mistake away from "Nobond".
      if (i > 1 && j > 1 && a[i - 1] === b[j - 2] && a[i - 2] === b[j - 1]) {
        rows[i][j] = Math.min(rows[i][j], rows[i - 2][j - 2] + 1)
      }
    }
  }
  return rows[a.length][b.length]
}

/** Do the query's letters appear in order? Catches skipped letters: nbnd -> nobond. */
export function isSubsequence(query, haystack) {
  if (!query) return true
  let at = 0
  for (const character of haystack) {
    if (character === query[at]) at += 1
    if (at === query.length) return true
  }
  return false
}

/** How many mistakes are forgiven, by how much the user typed. */
function tolerance(length) {
  if (length <= 3) return 0
  if (length <= 5) return 1
  return 2
}

/* Name outranks email outranks role outranks department. */
const FIELDS = [
  { key: 'name', weight: 100 },
  { key: 'email', weight: 70 },
  { key: 'role', weight: 55 },
  { key: 'department', weight: 45 },
]

/**
 * Best score for one query word against one candidate.
 * Returns { score, fuzzy } - fuzzy is true when the word only matched by
 * forgiving a mistake, which the UI surfaces as "close matches".
 */
function scoreWord(candidate, word) {
  let best = 0
  let fuzzy = true

  const initials = normalize(candidate.name)
    .split(' ')
    .map((part) => part[0] || '')
    .join('')

  for (const field of FIELDS) {
    const hay = normalize(candidate[field.key])
    if (!hay) continue

    const index = hay.indexOf(word)
    if (index === 0) {
      if (field.weight > best) { best = field.weight; fuzzy = false }
      continue
    }
    if (index > 0) {
      // A match at a word boundary is worth more than one mid-word.
      const boundary = hay[index - 1] === ' '
      const score = field.weight * (boundary ? 0.9 : 0.75)
      if (score > best) { best = score; fuzzy = false }
      continue
    }

    // "nt" -> Nobond Tester
    if (word.length >= 2 && initials.startsWith(word)) {
      const score = field.weight * 0.85
      if (score > best) { best = score; fuzzy = false }
      continue
    }

    // A typo inside any single word of the field.
    const allowed = tolerance(word.length)
    if (allowed > 0) {
      let closest = Infinity
      for (const part of hay.split(' ')) {
        closest = Math.min(closest, editDistance(part, word))
        // Also compare against the same-length prefix, so "aarv" reaches "aarav".
        if (part.length > word.length) {
          closest = Math.min(closest, editDistance(part.slice(0, word.length), word))
        }
      }
      if (closest <= allowed) {
        const score = field.weight * (0.7 - 0.15 * closest)
        if (score > best) best = score
        continue
      }
    }

    if (word.length >= 4 && isSubsequence(word, hay)) {
      const score = field.weight * 0.4
      if (score > best) best = score
    }
  }

  return { score: best, fuzzy }
}

/**
 * Rank candidates against a query.
 *
 * Every word the user typed has to match something, so "priya engineer" does not
 * return every engineer. Order is by score, then most recent.
 */
export function searchCandidates(candidates = [], query = '') {
  const cleaned = normalize(query)
  if (!cleaned) return { results: candidates, fuzzy: false, query: '' }

  const words = cleaned.split(' ')
  const scored = []
  let anyFuzzy = false

  for (const candidate of candidates) {
    let total = 0
    let matchedAll = true
    let usedFuzzy = false

    for (const word of words) {
      const { score, fuzzy } = scoreWord(candidate, word)
      if (score === 0) { matchedAll = false; break }
      total += score
      if (fuzzy) usedFuzzy = true
    }

    if (matchedAll) {
      scored.push({ candidate, score: total, usedFuzzy })
      if (usedFuzzy) anyFuzzy = true
    }
  }

  scored.sort((a, b) => {
    if (b.score !== a.score) return b.score - a.score
    return String(b.candidate.createdAt || '').localeCompare(String(a.candidate.createdAt || ''))
  })

  return {
    results: scored.map((entry) => entry.candidate),
    // Only call it a close match if nothing matched exactly.
    fuzzy: scored.length > 0 && scored.every((entry) => entry.usedFuzzy),
    query: cleaned,
  }
}
