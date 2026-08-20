import { useMemo, useState } from 'react'
import { Link, useOutletContext } from 'react-router-dom'
import { Button } from '../../components/ui/Button'
import { Field, Select } from '../../components/ui/Field'
import { DocumentViewer } from '../../components/ui/DocumentViewer'
import { FileDropzone } from '../../components/ui/FileDropzone'
import { ProgressBar } from '../../components/ui/ProgressBar'
import { LoadingState } from '../../components/ui/Spinner'
import { StatusPill } from '../../components/ui/StatusPill'
import { ErrorState } from '../../components/ui/EmptyState'
import { useToast } from '../../context/ToastContext'
import { useAsync } from '../../hooks/useAsync'
import { fileUrl } from '../../services/apiClient'
import { portalService } from '../../services/portalService'
import { formatBytes, formatDateTime } from '../../utils/format'
const CANDIDATE_STATUS = {
  pending: { label: 'Not uploaded', tone: 'grey' },
  submitted: { label: 'Submitted', tone: 'blue' },
  verified: { label: 'Submitted', tone: 'blue' },
  rejected: { label: 'Needs a new copy', tone: 'red' },
}

function candidateStatus(status) {
  return CANDIDATE_STATUS[status] || CANDIDATE_STATUS.pending
}

export function PortalDocumentsPage() {
  const { token, reloadOverview, overview } = useOutletContext()
  const toast = useToast()
  const [uploadingType, setUploadingType] = useState(null)
  const [viewing, setViewing] = useState(null)

  const { data, error, loading, setData, reload } = useAsync(
    () => portalService.documents(token),
    [token],
  )

  const upload = async (documentType, file, course) => {
    setUploadingType(documentType)
    try {
      const updated = await portalService.uploadDocument(token, documentType, file, course)
      setData(updated)
      reloadOverview()
      toast.success('Document uploaded', 'HR will review it and you will see the status here.')
    } catch (err) {
      toast.apiError(err, 'Upload failed')
      reload().catch(() => {})
    } finally {
      setUploadingType(null)
    }
  }

  if (loading && !data) return <LoadingState label="Loading your documents" />
  if (error && !data) {
    return (
      <div className="cf-card">
        <ErrorState title="We could not load your documents" message={error.message} />
      </div>
    )
  }

  const { documents, progress, uploadAllowed, message, maxFileSizeBytes, allowedExtensions } = data
  const outstanding = overview.outstandingItems || []

  return (
    <div className="space-y-5">
      {!overview.profileSubmitted && (
        <section className="cf-card border-[#FE5833]/30 bg-[#FFF4EC] p-5">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <h2 className="text-[15px] font-semibold text-ink">Your details are still needed</h2>
              <p className="mt-1 max-w-xl text-[13px] leading-6 text-[#B94A18]">
                Fill in your personal and education details as well. Your offer letter unlocks once your
                details are in and HR has approved every document.
              </p>
            </div>
            <Link to={`/portal/${token}/details`}>
              <Button>Fill in my details</Button>
            </Link>
          </div>
        </section>
      )}

      <section className="cf-card p-5 sm:p-6">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h2 className="text-[16px] font-semibold text-ink">Your documents</h2>
            <p className="mt-1 max-w-xl text-[13px] leading-6 text-ink-muted">{message}</p>
          </div>
          <div className="w-full max-w-[220px]">
            <p className="mb-1.5 text-[11.5px] font-medium uppercase tracking-[0.12em] text-ink-muted">
              Uploaded
            </p>
            {/* Upload progress only - whether HR has verified each one is their
                side of the process, and nothing the candidate needs to track. */}
            <ProgressBar
              value={progress.required - progress.missing}
              total={progress.required}
              tone={progress.missing === 0 ? 'green' : 'brand'}
            />
          </div>
        </div>

        <div className="mt-4 flex flex-wrap items-center gap-2.5 border-t border-surface-line pt-4">
          {!overview.submittedForReview &&
            (overview.readyToSubmit ? (
              <Link to={`/portal/${token}/review`}>
                <Button>Review &amp; submit</Button>
              </Link>
            ) : (
              // Nothing to review until every required item is in, so the button
              // stays inert rather than bouncing them to a page that just says no.
              <Button disabled title="Finish the items below first">
                Review &amp; submit
              </Button>
            ))}
          <Link to={`/portal/${token}/details`}>
            <Button variant="secondary" size="sm"
              icon={
                <svg viewBox="0 0 24 24" className="h-3.5 w-3.5" fill="none" stroke="currentColor"
                  strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M19 12H5m7-7-7 7 7 7" />
                </svg>
              }
            >
              Back to my details
            </Button>
          </Link>
          <span className="text-[12px] text-ink-muted">
            {overview.submittedForReview
              ? 'Your pack is with HR. You can still view everything here.'
              : 'Need to correct your name, address or contact number? Go back and edit them.'}
          </span>
        </div>

        {!overview.submittedForReview && !overview.readyToSubmit && outstanding.length > 0 && (
          <div className="mt-4 rounded border border-[#FE5833]/25 bg-[#FFF4EC] px-4 py-3">
            <p className="text-[12.5px] font-semibold text-[#B94A18]">
              Still to finish before you can submit
            </p>
            <ul className="mt-2 space-y-1.5">
              {outstanding.map((item) => (
                <li key={item} className="flex items-start gap-2 text-[12.5px] text-[#8E3A11]">
                  <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-accent-orange" />
                  {item}
                </li>
              ))}
            </ul>
          </div>
        )}
      </section>

      <DocumentViewer
        open={Boolean(viewing)}
        onClose={() => setViewing(null)}
        apiPath={viewing?.downloadUrl}
        title={viewing ? `${viewing.typeLabel}${viewing.courseLabel ? ` · ${viewing.courseLabel}` : ''}` : null}
        filename={viewing?.filename}
      />

      <div className="space-y-3.5">
        {documents.map((doc) => (
          <DocumentRow
            key={doc.type}
            doc={doc}
            uploadAllowed={uploadAllowed}
            uploading={uploadingType === doc.type}
            maxBytes={maxFileSizeBytes}
            allowedExtensions={allowedExtensions}
            onUpload={(file, course) => upload(doc.type, file, course)}
            onView={() => setViewing(doc)}
          />
        ))}
      </div>
    </div>
  )
}

function DocumentRow({ doc, uploadAllowed, uploading, maxBytes, allowedExtensions, onUpload, onView }) {
  const meta = candidateStatus(doc.status)
  const canUpload = uploadAllowed && doc.uploadAllowed
  // Education certificates above Class 10 must say which course they are for.
  const [course, setCourse] = useState(doc.course || '')
  const courseMissing = doc.requiresCourse && !course
  const courseGroups = useMemo(() => {
    const groups = new Map()
    ;(doc.courseOptions || []).forEach((option) => {
      const key = option.group || 'Other'
      if (!groups.has(key)) groups.set(key, [])
      groups.get(key).push(option)
    })
    return [...groups.entries()]
  }, [doc.courseOptions])

  return (
    <section className="cf-card overflow-hidden">
      <div className="flex flex-wrap items-start justify-between gap-3 px-5 py-4">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="text-[14.5px] font-semibold text-ink">{doc.typeLabel}</h3>
            <StatusPill label={meta.label} tone={meta.tone} />
            <span className="text-[11px] font-medium uppercase tracking-[0.1em] text-ink-muted">
              {doc.mandatory ? 'Required' : 'Optional'}
            </span>
            {doc.courseLabel && (
              <span className="rounded-full bg-brand-tint px-2 py-0.5 text-[11px] font-medium text-brand">
                {doc.courseLabel}
              </span>
            )}
          </div>

          {doc.filename ? (
            <p className="mt-1.5 flex flex-wrap items-center gap-x-2 gap-y-1 text-[12.5px] text-ink-muted">
              <svg viewBox="0 0 24 24" className="h-3.5 w-3.5 shrink-0" fill="none" stroke="currentColor"
                strokeWidth="1.8">
                <path d="M14 3v5h5M6 3h9l5 5v13H6V3Z" />
              </svg>
              <span className="font-medium text-ink-body">{doc.filename}</span>
              <span>&middot; {formatBytes(doc.sizeBytes)}</span>
              <span>&middot; uploaded {formatDateTime(doc.uploadedAt)}</span>
              {doc.version > 1 && <span>&middot; version {doc.version}</span>}
            </p>
          ) : (
            <p className="mt-1.5 text-[12.5px] text-ink-muted">Not uploaded yet.</p>
          )}

          {doc.status === 'rejected' && doc.rejectReason && (
            <div className="mt-3 rounded border border-accent-red/30 bg-[#FFECEC] px-3.5 py-2.5">
              <p className="text-[12px] font-semibold uppercase tracking-[0.08em] text-[#C21414]">
                Please upload a new copy
              </p>
              <p className="mt-1 text-[13px] leading-5 text-[#8E1010]">{doc.rejectReason}</p>
            </div>
          )}

        </div>

        {doc.downloadUrl && (
          <button
            type="button"
            onClick={onView}
            className="shrink-0 rounded border border-surface-line px-3 py-1.5 text-[12.5px] font-semibold
              text-brand transition hover:border-brand hover:bg-brand-tint"
          >
            View
          </button>
        )}
      </div>

      {canUpload && (
        <div className="border-t border-surface-line bg-surface-offwhite/40 px-5 py-4">
          {uploading ? (
            <LoadingState label="Uploading" className="py-4" />
          ) : (
            <div className="space-y-3">
              {doc.requiresCourse && (
                <Field
                  label={
                    doc.type === 'secondary_education_certificate'
                      ? 'Which secondary course is this?'
                      : 'Which course is this for?'
                  }
                  htmlFor={`course-${doc.type}`}
                  required
                  hint="Pick this first - it is stored with the certificate."
                >
                  <Select
                    id={`course-${doc.type}`}
                    value={course}
                    onChange={(event) => setCourse(event.target.value)}
                    className="sm:max-w-xs"
                  >
                    <option value="">Select</option>
                    {courseGroups.map(([group, options]) => (
                      <optgroup key={group} label={group}>
                        {options.map((option) => (
                          <option key={option.value} value={option.value}>
                            {option.label}
                          </option>
                        ))}
                      </optgroup>
                    ))}
                  </Select>
                </Field>
              )}
              <FileDropzone
                compact
                disabled={courseMissing}
                maxBytes={maxBytes}
                allowedExtensions={allowedExtensions}
                onSelect={(file) => onUpload(file, course || null)}
                label={
                  courseMissing
                    ? 'Select the course above first'
                    : doc.status === 'rejected'
                      ? 'Upload a replacement'
                      : `Upload ${doc.typeLabel}`
                }
              />
            </div>
          )}
        </div>
      )}

      {!canUpload && (doc.status === 'submitted' || doc.status === 'verified') && (
        <div className="border-t border-surface-line bg-surface-offwhite/40 px-5 py-3">
          <p className="text-[12.5px] text-ink-muted">
            Received - nothing more needed for this one.
          </p>
        </div>
      )}
    </section>
  )
}
