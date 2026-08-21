import { useCallback, useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { AuditTimeline } from '../../components/hr/AuditTimeline'
import { OfferPanel } from '../../components/hr/OfferPanel'
import { Button } from '../../components/ui/Button'
import { ErrorState } from '../../components/ui/EmptyState'
import { LoadingState } from '../../components/ui/Spinner'
import { StatusPill } from '../../components/ui/StatusPill'
import { useToast } from '../../context/ToastContext'
import { hrService } from '../../services/hrService'
import { hueOf } from '../../utils/avatar'
import { initialsOf } from '../../utils/format'
import { offerStatusMeta } from '../../utils/status'

/**
 * One candidate's offer letter, on its own screen.
 *
 * Publishing an offer means reading the record, writing a note and uploading a
 * file - too much to do inside a dialog, so it gets a route of its own and a way
 * back to the list.
 */
export function OfferDetailPage() {
  const { candidateId } = useParams()
  const navigate = useNavigate()
  const location = useLocation()
  const toast = useToast()
  const backTo = location.state?.from || '/offers'

  const [detail, setDetail] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setDetail(await hrService.candidate(candidateId))
    } catch (caught) {
      setError(caught)
      toast.apiError(caught, 'Could not load the offer')
    } finally {
      setLoading(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [candidateId])

  useEffect(() => {
    load()
  }, [load])

  if (loading && !detail) return <LoadingState label="Loading the offer" />

  if (error && !detail) {
    return (
      <div className="cf-card">
        <ErrorState
          message={error.message}
          action={
            <div className="flex flex-wrap justify-center gap-2.5">
              <Button onClick={load}>Try again</Button>
              <Button variant="secondary" onClick={() => navigate(backTo)}>
                Back to offer letters
              </Button>
            </div>
          }
        />
      </div>
    )
  }

  const candidate = detail.candidate
  const offer = offerStatusMeta(candidate.offerStatus)

  return (
    <>
      <Link to={backTo} className="c-back">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
          strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M19 12H5m7-7-7 7 7 7" />
        </svg>
        Back to offer letters
      </Link>

      <section className="c-panel c-offerhead">
        <span className={`c-ring c-ring--h${hueOf(candidate.name)}`}>
          {initialsOf(candidate.name)}
        </span>
        <div className="c-offerhead-text">
          <h1>{candidate.name}</h1>
          <p>
            {candidate.role} &middot; {candidate.department} &middot; {candidate.email}
          </p>
        </div>
        <StatusPill label={offer.label} tone={offer.tone} />
      </section>

      <div className="c-offerbody">
        <OfferPanel candidate={candidate} offer={detail.offer} onChanged={load} />

        <section className="c-panel" style={{ padding: 20 }}>
          <h2 style={{ fontSize: 14.5 }}>Offer activity</h2>
          <div style={{ marginTop: 14 }}>
            <AuditTimeline
              events={(detail.auditTrail || []).filter((event) =>
                event.eventType.startsWith('offer') || event.eventType === 'onboarding_completed',
              )}
              emptyMessage="Nothing has happened on the offer yet."
            />
          </div>
        </section>
      </div>
    </>
  )
}
