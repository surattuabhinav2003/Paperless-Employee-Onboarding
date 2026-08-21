import { useMemo, useState } from 'react'
import { Link, useOutletContext } from 'react-router-dom'
import { Button } from '../../components/ui/Button'
import { Field, Select } from '../../components/ui/Field'
import { DocumentViewer } from '../../components/ui/DocumentViewer'
import { FileDropzone } from '../../components/ui/FileDropzone'
import { ProgressBar } from '../../components/ui/ProgressBar'
import { LoadingState } from '../../components/ui/Spinner'
import { ErrorState } from '../../components/ui/EmptyState'
import { useToast } from '../../context/ToastContext'
import { useAsync } from '../../hooks/useAsync'
import { fileUrl } from '../../services/apiClient'
import { portalService } from '../../services/portalService'
import { formatBytes, formatDateTime } from '../../utils/format'
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

        {!overview.submittedForReview && outstanding.length > 0 && (
          <p className="mt-3 text-[12.5px] text-ink-muted">
            {outstanding.length === 1
              ? '1 thing left before you can submit.'
              : `${outstanding.length} things left before you can submit.`}
          </p>
        )}
      </section>

      <DocumentViewer
        open={Boolean(viewing)}
        onClose={() => setViewing(null)}
        apiPath={viewing?.downloadUrl}
        title={viewing ? `${viewing.typeLabel}${viewing.courseLabel ? ` · ${viewing.courseLabel}` : ''}` : null}
        filename={viewing?.filename}
      />

      {(() => {
        const uploaded = documents.filter((d) => d.status !== 'pending' && d.status !== 'rejected').length
        const percent = documents.length ? Math.round((uploaded / documents.length) * 100) : 0
        /* The first thing still needing the candidate, so the page can point at
           one item instead of listing seven. */
        const nextUp = documents.find((d) => d.status === 'rejected')
          || documents.find((d) => d.status === 'pending')
        return (
          <div className="pd-progress">
            <div className="pd-progress-top">
              <span className="pd-progress-count">
                <b>{uploaded}</b> of {documents.length} uploaded
              </span>
              <span className="pd-progress-hint">
                {allowedExtensions.join(', ')} &middot; up to {formatBytes(maxFileSizeBytes)}
              </span>
            </div>
            <div className="pd-bar"><span style={{ width: `${percent}%` }} /></div>
            {nextUp && uploadAllowed && (
              <p className="pd-next">
                {nextUp.status === 'rejected' ? 'Needs a new copy: ' : 'Next up: '}
                <b>{nextUp.typeLabel}</b>
              </p>
            )}
            {!nextUp && (
              <p className="pd-next">All documents are in. Review and submit when you are ready.</p>
            )}
          </div>
        )
      })()}

      <div className="space-y-3">
        {documents.map((doc, index) => (
          <DocumentRow
            key={doc.type}
            doc={doc}
            index={index + 1}
            isNext={
              doc.type === (
                documents.find((d) => d.status === 'rejected')
                || documents.find((d) => d.status === 'pending')
              )?.type
            }
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

function DocumentRow({
  doc,
  index,
  isNext,
  uploadAllowed,
  uploading,
  maxBytes,
  allowedExtensions,
  onUpload,
  onView,
}) {
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

  const state = doc.status === 'verified' ? 'done'
    : doc.status === 'submitted' ? 'review'
      : doc.status === 'rejected' ? 'rejected'
        : isNext ? 'next' : ''

  return (
    <section className="cf-card overflow-hidden">
      <div className={`pd-item${state ? ` pd-item--${state}` : ''}`}>
        <span className="pd-num" aria-hidden="true">
          {doc.status === 'verified' || doc.status === 'submitted' ? (
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3"
              strokeLinecap="round" strokeLinejoin="round">
              <path d="M20 6 9 17l-5-5" />
            </svg>
          ) : index}
        </span>

        <div className="pd-body">
          <div className="pd-title-row">
            <h3 className="pd-title">{doc.typeLabel}</h3>
            {doc.mandatory && <span className="pd-req">Required</span>}
            {doc.courseLabel && <span className="d-course">{doc.courseLabel}</span>}
          </div>

          {/* One line about the file, and only when there is one. The status is
              already carried by the number badge. */}
          {doc.filename && (
            <p className="pd-file">
              <b>{doc.filename}</b>
              <span>&middot; {formatBytes(doc.sizeBytes)}</span>
              <span>&middot; {formatDateTime(doc.uploadedAt)}</span>
              {doc.downloadUrl && (
                <button type="button" className="c-linkbtn" onClick={onView}>View</button>
              )}
            </p>
          )}

          {doc.status === 'rejected' && doc.rejectReason && (
            <div className="pd-rejected">
              <b>Please upload a new copy</b>
              <p>{doc.rejectReason}</p>
            </div>
          )}

          {!canUpload && (doc.status === 'submitted' || doc.status === 'verified') && (
            <p className="pd-done-note">Received - nothing more needed for this one.</p>
          )}

          {canUpload && (
            <div className="pd-upload">
              {uploading ? (
                <LoadingState label="Uploading" className="py-3" />
              ) : (
                <>
                  {doc.requiresCourse && (
                    <div className="pd-course">
                      <Field
                        label={
                          doc.type === 'secondary_education_certificate'
                            ? 'Which secondary course is this?'
                            : 'Which course is this for?'
                        }
                        htmlFor={`course-${doc.type}`}
                        required
                      >
                        <Select
                          id={`course-${doc.type}`}
                          value={course}
                          onChange={(event) => setCourse(event.target.value)}
                        >
                          <option value="">Select a course</option>
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
                    </div>
                  )}
                  <FileDropzone
                    compact
                    showHint={false}
                    disabled={courseMissing}
                    maxBytes={maxBytes}
                    allowedExtensions={allowedExtensions}
                    onSelect={(file) => onUpload(file, course || null)}
                    label={
                      courseMissing
                        ? 'Choose the course above first'
                        : doc.status === 'rejected'
                          ? 'Upload a replacement'
                          : 'Choose file or drag it here'
                    }
                  />
                </>
              )}
            </div>
          )}
        </div>
      </div>
    </section>
  )
}
