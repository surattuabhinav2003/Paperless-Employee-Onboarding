import { useEffect, useState } from 'react'
import { Link, useNavigate, useOutletContext } from 'react-router-dom'
import { Button } from '../../components/ui/Button'
import { ErrorState } from '../../components/ui/EmptyState'
import { ProfileFormFields } from '../../components/profile/ProfileFormFields'
import { LoadingState } from '../../components/ui/Spinner'
import { useToast } from '../../context/ToastContext'
import { useAsync } from '../../hooks/useAsync'
import { hrService } from '../../services/hrService'
import { portalService } from '../../services/portalService'
import { formatDateTime } from '../../utils/format'
import { applyProfileChange, EMPTY_PROFILE, toProfilePayload, validateProfile } from '../../utils/profileForm'

/**
 * The personal details Neutara collects alongside the documents. Education is
 * captured per certificate on the documents step instead, where the candidate
 * picks the course each certificate belongs to.
 * <p>
 * Options come from /api/meta, so the form never hardcodes enum values, and the
 * backend re-validates everything on submit.
 */
export function PortalDetailsPage() {
  const { overview, reloadOverview, token } = useOutletContext()
  const toast = useToast()
  const navigate = useNavigate()

  const [form, setForm] = useState(EMPTY_PROFILE)
  const [errors, setErrors] = useState({})
  const [saving, setSaving] = useState(false)

  const meta = useAsync(() => hrService.metadata(), [])
  const existing = useAsync(() => portalService.profile(token), [token])

  const locked = !overview.profileEditable

  // Prefill from a previous submission, or seed the name HR already has.
  useEffect(() => {
    if (existing.data) {
      setForm({
        ...EMPTY_PROFILE,
        ...existing.data,
        alternateContactNumber: existing.data.alternateContactNumber || '',
        customFields: existing.data.customFields || {},
      })
    } else if (!existing.loading) {
      setForm((current) => ({ ...current, fullNameAsPerAadhaar: overview.candidateName || '' }))
    }
  }, [existing.data, existing.loading, overview.candidateName])

  /* Clears a field's validation error as soon as its value changes. */
  const setValue = (field, value) => {
    setForm((current) => applyProfileChange(current, field, value))
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  const validate = () => {
    const next = validateProfile(form, {
      candidateFields: meta.data?.candidateFields,
      customFields: meta.data?.customCandidateFields,
    })
    setErrors(next)
    return Object.keys(next).length === 0
  }

  const submit = async (event) => {
    event.preventDefault()
    if (!validate()) return
    setSaving(true)
    try {
      const saved = await portalService.saveProfile(token, toProfilePayload(form))
      existing.setData(saved)
      await reloadOverview()
      toast.success(
        existing.data ? 'Details updated' : 'Details submitted',
        'Next, upload the documents listed for you.',
      )
      navigate(`/portal/${token}/documents`)
    } catch (error) {
      if (error.fieldErrors && Object.keys(error.fieldErrors).length) {
        setErrors(error.fieldErrors)
      }
      toast.apiError(error, 'We could not save your details')
    } finally {
      setSaving(false)
    }
  }

  if (existing.loading && !existing.data) return <LoadingState label="Loading your details" />
  if (existing.error && existing.error.code !== 'RESOURCE_NOT_FOUND' && !existing.data) {
    return (
      <div className="cf-card">
        <ErrorState title="We could not load your details" message={existing.error.message} />
      </div>
    )
  }

  return (
    <form className="space-y-5" onSubmit={submit} noValidate>
      <section className="cf-card p-5 sm:p-6">
        <h2 className="text-[16px] font-semibold text-ink">Your details</h2>
        <p className="mt-1 max-w-2xl text-[13px] leading-6 text-ink-muted">
          Neutara needs these for your employee record and payroll. Enter your name exactly as it appears
          on your Aadhaar card. Your education certificates are uploaded on the documents step.
        </p>
        {existing.data && (
          <p className="mt-3 rounded border border-surface-line bg-surface-canvas px-3.5 py-2.5
            text-[12.5px] text-ink-muted">
            {locked
              ? `Submitted on ${formatDateTime(existing.data.submittedAt)} and reviewed by HR - these can no longer be changed here.`
              : `Submitted on ${formatDateTime(existing.data.submittedAt)}. You can still correct anything until HR approves your documents.`}
          </p>
        )}
      </section>

      <ProfileFormFields
        form={form}
        errors={errors}
        meta={meta.data}
        disabled={locked}
        setValue={setValue}
        candidateFields={meta.data?.candidateFields}
        customFields={meta.data?.customCandidateFields}
        sectioned
      />

      <div className="flex flex-wrap items-center gap-3">
        {!locked && (
          <Button type="submit" size="lg" loading={saving}>
            {existing.data ? 'Save changes' : 'Submit my details'}
          </Button>
        )}
        <Link to={`/portal/${token}/${overview.readyToSubmit ? 'review' : 'documents'}`}>
          <Button variant="secondary" size={locked ? 'lg' : 'md'}>
            {overview.readyToSubmit
              ? 'Review & submit'
              : existing.data
                ? 'Go to my documents'
                : 'Skip for now, upload documents'}
          </Button>
        </Link>
        {!locked && (
          <span className="text-[12.5px] text-ink-muted">
            You can come back and correct these any time before HR approves your documents.
          </span>
        )}
      </div>
    </form>
  )
}
