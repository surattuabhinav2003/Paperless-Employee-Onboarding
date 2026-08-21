import { describe, expect, it } from 'vitest'
import { editDistance, isSubsequence, normalize, searchCandidates } from '../utils/search'

const CANDIDATES = [
  {
    id: '1',
    name: 'Nobond Tester',
    email: 'nobond.e2e@example.com',
    role: 'QA Engineer',
    department: 'Engineering',
    createdAt: '2026-08-21T10:00:00Z',
  },
  {
    id: '2',
    name: 'Aarav Mehta',
    email: 'aarav.mehta@example.com',
    role: 'Software Engineer',
    department: 'Engineering',
    createdAt: '2026-08-20T10:00:00Z',
  },
  {
    id: '3',
    name: 'priya',
    email: 'priya@gmail.com',
    role: 'software Engineer traniee',
    department: 'engineeering',
    createdAt: '2026-08-19T10:00:00Z',
  },
]

const names = (result) => result.results.map((candidate) => candidate.name)

describe('normalize', () => {
  it('folds case, accents and punctuation', () => {
    expect(normalize('  Aarav-Méhta  ')).toBe('aarav mehta')
  })
})

describe('editDistance', () => {
  it('counts a swapped pair as one mistake', () => {
    expect(editDistance('nobond', 'nboond')).toBe(1)
  })

  it('counts insertions and substitutions', () => {
    expect(editDistance('mehta', 'mehat')).toBe(1)
    expect(editDistance('priya', 'prya')).toBe(1)
    expect(editDistance('abc', 'xyz')).toBe(3)
  })
})

describe('isSubsequence', () => {
  it('accepts skipped letters in order', () => {
    expect(isSubsequence('nbnd', 'nobond tester')).toBe(true)
  })

  it('rejects letters out of order', () => {
    expect(isSubsequence('dnbn', 'nobond')).toBe(false)
  })
})

describe('searchCandidates', () => {
  it('returns everything for an empty query', () => {
    expect(searchCandidates(CANDIDATES, '   ').results).toHaveLength(3)
  })

  it('finds an exact match', () => {
    expect(names(searchCandidates(CANDIDATES, 'aarav'))).toEqual(['Aarav Mehta'])
  })

  it('forgives a misspelling', () => {
    expect(names(searchCandidates(CANDIDATES, 'aarv'))).toContain('Aarav Mehta')
    expect(names(searchCandidates(CANDIDATES, 'nboond'))).toContain('Nobond Tester')
    expect(names(searchCandidates(CANDIDATES, 'prya'))).toContain('priya')
  })

  it('flags a result set that only matched by forgiving a mistake', () => {
    expect(searchCandidates(CANDIDATES, 'aarv').fuzzy).toBe(true)
    expect(searchCandidates(CANDIDATES, 'aarav').fuzzy).toBe(false)
  })

  it('matches on initials', () => {
    expect(names(searchCandidates(CANDIDATES, 'nt'))).toContain('Nobond Tester')
  })

  it('searches email, role and department too', () => {
    expect(names(searchCandidates(CANDIDATES, 'gmail'))).toEqual(['priya'])
    expect(names(searchCandidates(CANDIDATES, 'qa'))).toContain('Nobond Tester')
  })

  it('ranks a name match above a department match', () => {
    // "engineer" hits Aarav's role and everyone's department; the strongest
    // field wins, so ordering stays useful rather than arbitrary.
    const result = searchCandidates(CANDIDATES, 'software engineer')
    expect(result.results[0].name).toBe('Aarav Mehta')
  })

  it('requires every word to match something', () => {
    expect(searchCandidates(CANDIDATES, 'aarav zzzzz').results).toHaveLength(0)
  })

  it('returns nothing for a query that resembles nobody', () => {
    expect(searchCandidates(CANDIDATES, 'xylophone').results).toHaveLength(0)
  })
})
