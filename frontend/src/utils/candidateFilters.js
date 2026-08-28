import { awaitingReviewCount } from './status'

/**
 * The one list of ways to narrow the candidate table.
 *
 * <p>Most of these double as dashboard tiles: a figure up there is the size of
 * one of these groups, and clicking it opens the table with that filter already
 * selected. The number is a way in, so it has to be the length of the list it
 * opens - which only holds while both ends read the same definition, hence this
 * file. `DashboardService` mirrors the ones that are tiles.
 *
 * <p>Signed offers are deliberately absent: that list lives on the offer desk,
 * which already separates not-sent from sent from signed. A second copy here
 * would be one more place to keep in step for no new answer.
 *
 * <p>"Documents pending" is the people who still owe something, not everyone
 * parked in the document stage: once a candidate has sent everything, chasing
 * them is the wrong action, and they belong under "Awaiting review" instead.
 *
 * <p>There is deliberately no "active" option. It sounds useful and is not: a
 * candidate whose documents are all verified is still unfinished, so they would
 * count as active while plainly sitting under Verified - two segments claiming
 * the same person for different reasons. "All" is the honest whole.
 */
export const CANDIDATE_FILTERS = [
  {
    value: '',
    label: 'All',
    match: () => true,
  },
  {
    value: 'docs_pending',
    label: 'Documents pending',
    match: (candidate) =>
      candidate.stage === 'docs_pending'
      && (candidate.documentsMissing || 0) + (candidate.documentsRejected || 0) > 0,
  },
  {
    value: 'awaiting_review',
    label: 'Awaiting review',
    match: (candidate) => awaitingReviewCount(candidate) > 0,
  },
  {
    value: 'verified',
    label: 'Verification done',
    match: (candidate) => candidate.stage === 'docs_approved',
  },
]

/** The filter for a URL value, falling back to the unfiltered list. */
export function candidateFilter(value) {
  return CANDIDATE_FILTERS.find((filter) => filter.value === value) || CANDIDATE_FILTERS[0]
}


