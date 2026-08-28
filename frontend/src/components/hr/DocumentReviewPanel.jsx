import { useState } from 'react'
import { Button } from '../ui/Button'
import { EmptyState } from '../ui/EmptyState'
import { Field, TextArea } from '../ui/Field'
import { DocumentViewer } from '../ui/DocumentViewer'
import { Modal } from '../ui/Modal'
import { StatusPill } from '../ui/StatusPill'
import { useToast } from '../../context/ToastContext'
import { hrService } from '../../services/hrService'
import { formatBytes, formatDateTime } from '../../utils/format'
import { documentStatusMeta } from '../../utils/status'

/**
 * HR review surface for one candidate: open the file, verify it, or reject it
 * with a reason. All decisions go through the backend, which also decides when
 * the document stage is complete.
 */
export function DocumentReviewPanel({ documents = [], candidateId, onChanged, readOnly = false }) {
  const toast = useToast()
  const [busyId, setBusyId] = useState(null)
  const [rejecting, setRejecting] = useState(null)
  const [reason, setReason] = useState('')
  const [reasonError, setReasonError] = useState(null)
  const [viewing, setViewing] = useState(null)
  const [mandatoryBusyType, setMandatoryBusyType] = useState(null)

  const toggleMandatory = async (doc) => {
    setMandatoryBusyType(doc.type)
    try {
      const updated = await hrService.setDocumentMandatory(candidateId, doc.type, !doc.mandatory)
      onChanged?.(updated)
    } catch (error) {
      toast.apiError(error, 'Could not update this requirement')
    } finally {
      setMandatoryBusyType(null)
    }
  }

  const verify = async (doc) => {
    setBusyId(doc.id)
    try {
      const updated = await hrService.verifyDocument(doc.id)
      toast.success('Document reviewed', `${doc.typeLabel} is checked off.`)
      onChanged?.(updated)
    } catch (error) {
      toast.apiError(error, 'Could not verify the document')
    } finally {
      setBusyId(null)
    }
  }

  // Verifying is not final - HR can change their mind on any document right up
  // until they click Approve, which is the one action that locks review.
  const reopen = async (doc) => {
    setBusyId(doc.id)
    try {
      const updated = await hrService.reopenDocument(doc.id)
      toast.success('Document reopened', `${doc.typeLabel} is back to awaiting review.`)
      onChanged?.(updated)
    } catch (error) {
      toast.apiError(error, 'Could not reopen the document')
    } finally {
      setBusyId(null)
    }
  }

  const submitRejection = async () => {
    if (reason.trim().length < 5) {
      setReasonError('Give the candidate a clear reason (at least 5 characters)')
      return
    }
    setBusyId(rejecting.id)
    try {
      const updated = await hrService.rejectDocument(rejecting.id, reason.trim())
      toast.success('Document rejected', 'The candidate can now upload a replacement.')
      setRejecting(null)
      setReason('')
      setReasonError(null)
      onChanged?.(updated)
    } catch (error) {
      toast.apiError(error, 'Could not reject the document')
    } finally {
      setBusyId(null)
    }
  }

  if (!documents.length) {
    return (
      <EmptyState
        icon="document"
        title="No documents requested"
        message="This candidate has no document requirements configured."
      />
    )
  }

  return (
    <>
      {/* Rows rather than a fixed-width table, so the review actions stay
          reachable in both the full page and the narrower candidate drawer. */}
      <ul className="d-list">
        {documents.map((doc) => {
          // readOnly means review is closed, which only happens once HR has
          // approved - the moment a checked-off document becomes "Verified".
          const meta = documentStatusMeta(doc.status, readOnly)
          const busy = busyId === doc.id
          const extension = (doc.filename || '').split('.').pop()
          return (
            <li key={doc.type}>
              <div className={`d-row d-row--${doc.status}`}>
                <span className="d-tile" aria-hidden="true">
                  {doc.filename ? extension : (
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7"
                      strokeLinecap="round" strokeLinejoin="round" style={{ width: 16, height: 16 }}>
                      <path d="M14 3v5h5M6 3h9l5 5v13H6V3Z" />
                    </svg>
                  )}
                </span>

                <div className="d-body">
                  <div className="d-title-row">
                    <span className="d-title">{doc.typeLabel}</span>
                    <StatusPill label={meta.label} tone={meta.tone} />
                    {doc.courseLabel && <span className="d-course">{doc.courseLabel}</span>}
                  </div>

                  {/* One meta line: the file, and who decided what. "Not reviewed
                      yet" is dropped - the Awaiting review pill already says it. */}
                  <p className="d-meta">
                    {doc.filename
                      ? `${doc.filename} · ${formatBytes(doc.sizeBytes)} · uploaded ${formatDateTime(doc.uploadedAt)}`
                      : 'Not uploaded yet.'}
                    {doc.reviewedAt
                      ? ` · Reviewed by ${doc.reviewedBy} on ${formatDateTime(doc.reviewedAt)}`
                      : ''}
                  </p>

                  {doc.status === 'rejected' && doc.rejectReason && (
                    <p className="d-reason">{doc.rejectReason}</p>
                  )}
                </div>

                {/* Its own fixed-width column so "Mandatory"/"Optional" lines up
                    from row to row regardless of how long the title above it is. */}
                <div className="d-req-col">
                  {readOnly ? (
                    <span className={`d-req d-req--${doc.mandatory ? 'mandatory' : 'optional'}`}>
                      {doc.mandatory ? 'Mandatory' : 'Optional'}
                      {doc.version > 1 ? ` · v${doc.version}` : ''}
                    </span>
                  ) : (
                    <button
                      type="button"
                      disabled={mandatoryBusyType === doc.type}
                      onClick={() => toggleMandatory(doc)}
                      title="Click to change whether this document is mandatory"
                      className={`d-req-toggle d-req-toggle--${doc.mandatory ? 'mandatory' : 'optional'}`}
                    >
                      {doc.mandatory ? 'Mandatory' : 'Optional'}
                      {doc.version > 1 ? ` · v${doc.version}` : ''}
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                        <path d="m7 10 5 5 5-5" />
                      </svg>
                    </button>
                  )}
                </div>

                <div className="d-actions">
                  {doc.downloadUrl && (
                    <Button variant="subtle" size="sm" onClick={() => setViewing(doc)}>
                      View
                    </Button>
                  )}
                  {!readOnly && doc.status === 'submitted' && (
                    <>
                      <button
                        type="button"
                        className="d-btn d-verify"
                        disabled={busy}
                        onClick={() => verify(doc)}
                      >
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.6"
                          strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                          <path d="M20 6 9 17l-5-5" />
                        </svg>
                        {busy ? 'Verifying' : 'Verify'}
                      </button>
                      <button
                        type="button"
                        className="d-btn d-reject"
                        disabled={busy}
                        onClick={() => {
                          setRejecting(doc)
                          setReason('')
                          setReasonError(null)
                        }}
                      >
                        Reject
                      </button>
                    </>
                  )}
                  {!readOnly && doc.status === 'verified' && (
                    <button
                      type="button"
                      className="d-btn d-reopen"
                      disabled={busy}
                      title="Undo the verify and send it back to awaiting review"
                      onClick={() => reopen(doc)}
                    >
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2"
                        strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                        <path d="M3 12a9 9 0 1 1 3 6.7M3 12V7m0 5h5" />
                      </svg>
                      {busy ? 'Reopening' : 'Re-verify'}
                    </button>
                  )}
                  {doc.status === 'pending' && (
                    <span className="d-waiting">Waiting on candidate</span>
                  )}
                </div>
              </div>
            </li>
          )
        })}
      </ul>

      <DocumentViewer
        open={Boolean(viewing)}
        onClose={() => setViewing(null)}
        apiPath={viewing?.downloadUrl}
        title={viewing ? `${viewing.typeLabel}${viewing.courseLabel ? ` · ${viewing.courseLabel}` : ''}` : null}
        filename={viewing?.filename}
      />

      <Modal
        open={Boolean(rejecting)}
        onClose={() => setRejecting(null)}
        title={`Reject ${rejecting?.typeLabel || 'document'}`}
        description="The candidate sees this reason and can re-upload only this document."
        size="sm"
        footer={
          <>
            <Button variant="ghost" onClick={() => setRejecting(null)}>
              Cancel
            </Button>
            <Button variant="danger" loading={busyId === rejecting?.id} onClick={submitRejection}>
              Reject document
            </Button>
          </>
        }
      >
        <Field label="Rejection reason" htmlFor="reject-reason" required error={reasonError}
          hint="Be specific - this is the only guidance the candidate gets.">
          <TextArea
            id="reject-reason"
            rows={4}
            value={reason}
            error={reasonError}
            placeholder="The PAN card scan is cut off on the right edge. Please upload a full, clear copy."
            onChange={(event) => setReason(event.target.value)}
          />
        </Field>
      </Modal>
    </>
  )
}
