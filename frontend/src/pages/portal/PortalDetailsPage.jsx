import { useEffect, useState } from 'react'
import { Link, useNavigate, useOutletContext } from 'react-router-dom'
import { Button } from '../../components/ui/Button'
import { ErrorState } from '../../components/ui/EmptyState'
import { Field, Select, TextArea, TextInput } from '../../components/ui/Field'
import { DateField } from '../../components/ui/DateField'
import { LoadingState } from '../../components/ui/Spinner'
import { useToast } from '../../context/ToastContext'
import { useAsync } from '../../hooks/useAsync'
import { hrService } from '../../services/hrService'
import { portalService } from '../../services/portalService'
import { formatDateTime } from '../../utils/format'

const EMPTY = {
  fullNameAsPerAadhaar: '',
  personalEmail: '',
  contactNumber: '',
  alternateContactNumber: '',
  dateOfBirth: '',
  gender: '',
  fathersName: '',
  permanentAddress: '',
  bloodGroup: '',
}

/**
 * The personal details CloudFuze collects alongside the documents. Education is
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

  const [form, setForm] = useState(EMPTY)
  const [errors, setErrors] = useState({})
  const [saving, setSaving] = useState(false)

  const meta = useAsync(() => hrService.metadata(), [])
  const existing = useAsync(() => portalService.profile(token), [token])

  const locked = !overview.profileEditable

  // Prefill from a previous submission, or seed the name HR already has.
  useEffect(() => {
    if (existing.data) {
      setForm({
        ...EMPTY,
        ...existing.data,
        alternateContactNumber: existing.data.alternateContactNumber || '',
      })
    } else if (!existing.loading) {
      setForm((current) => ({ ...current, fullNameAsPerAadhaar: overview.candidateName || '' }))
    }
  }, [existing.data, existing.loading, overview.candidateName])

  const set = (field) => (event) => setValue(field, event.target.value)

  /* Shared by the text inputs and the date picker, so both clear their own
     validation error as soon as the value changes. */
  const setValue = (field, value) => {
    setForm((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  const validate = () => {
    const next = {}
    if (!form.fullNameAsPerAadhaar.trim()) next.fullNameAsPerAadhaar = 'Full name as per Aadhaar is required'
    if (!form.personalEmail.trim()) next.personalEmail = 'Personal email is required'
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.personalEmail.trim()))
      next.personalEmail = 'Enter a valid email address'
    if (!form.contactNumber.trim()) next.contactNumber = 'Contact number is required'
    else if (!/^\+?[0-9][0-9\s-]{7,19}$/.test(form.contactNumber.trim()))
      next.contactNumber = 'Enter a valid contact number'
    if (!form.alternateContactNumber.trim()) next.alternateContactNumber = 'Alternate contact number is required'
    else if (!/^\+?[0-9][0-9\s-]{7,19}$/.test(form.alternateContactNumber.trim()))
      next.alternateContactNumber = 'Enter a valid number'
    if (!form.dateOfBirth) next.dateOfBirth = 'Date of birth is required'
    else if (new Date(form.dateOfBirth) >= new Date()) next.dateOfBirth = 'Date of birth must be in the past'
    if (!form.gender) next.gender = 'Select your gender'
    if (!form.fathersName.trim()) next.fathersName = "Father's name is required"
    if (form.permanentAddress.trim().length < 10) next.permanentAddress = 'Give your full permanent address'
    if (!form.bloodGroup) next.bloodGroup = 'Select your blood group'
    setErrors(next)
    return Object.keys(next).length === 0
  }

  const submit = async (event) => {
    event.preventDefault()
    if (!validate()) return
    setSaving(true)
    try {
      const saved = await portalService.saveProfile(token, {
        ...form,
        alternateContactNumber: form.alternateContactNumber.trim(),
      })
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
          CloudFuze needs these for your employee record and payroll. Enter your name exactly as it appears
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

      <section className="cf-card p-5 sm:p-6">
        <h3 className="text-[14.5px] font-semibold text-ink">Personal information</h3>
        <div className="mt-4 grid gap-4 sm:grid-cols-2">
          <Field label="Full name (as per Aadhaar)" htmlFor="fullNameAsPerAadhaar" required
            error={errors.fullNameAsPerAadhaar} className="sm:col-span-2">
            <TextInput id="fullNameAsPerAadhaar" value={form.fullNameAsPerAadhaar} disabled={locked}
              error={errors.fullNameAsPerAadhaar} onChange={set('fullNameAsPerAadhaar')} autoComplete="name" />
          </Field>

          <Field label="Personal email ID" htmlFor="personalEmail" required error={errors.personalEmail}
            hint="Your own email - separate from your CloudFuze address.">
            <TextInput id="personalEmail" type="email" value={form.personalEmail} disabled={locked}
              error={errors.personalEmail} onChange={set('personalEmail')} autoComplete="email" />
          </Field>
          <Field label="Contact number" htmlFor="contactNumber" required error={errors.contactNumber}>
            <TextInput id="contactNumber" type="tel" value={form.contactNumber} disabled={locked}
              error={errors.contactNumber} onChange={set('contactNumber')} placeholder="9876543210"
              autoComplete="tel" />
          </Field>
          <Field label="Alternate contact number" htmlFor="alternateContactNumber" required
            error={errors.alternateContactNumber} hint="A second number we can reach you on.">
            <TextInput id="alternateContactNumber" type="tel" value={form.alternateContactNumber}
              disabled={locked} error={errors.alternateContactNumber}
              onChange={set('alternateContactNumber')} />
          </Field>
          <Field label="Date of birth" htmlFor="dateOfBirth" required error={errors.dateOfBirth}>
            {/* Our own calendar: the native one is browser chrome and cannot
                be styled to match the app. */}
            <DateField
              id="dateOfBirth"
              value={form.dateOfBirth}
              disabled={locked}
              error={Boolean(errors.dateOfBirth)}
              onChange={(iso) => setValue('dateOfBirth', iso)}
              max="2015-12-31"
              yearsBack={70}
              placeholder="Choose your date of birth"
            />
          </Field>
          <Field label="Gender" htmlFor="gender" required error={errors.gender}>
            <Select id="gender" value={form.gender} disabled={locked} error={errors.gender}
              onChange={set('gender')}>
              <option value="">Select</option>
              {(meta.data?.genders || []).map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </Select>
          </Field>
          <Field label="Blood group" htmlFor="bloodGroup" required error={errors.bloodGroup}>
            <Select id="bloodGroup" value={form.bloodGroup} disabled={locked} error={errors.bloodGroup}
              onChange={set('bloodGroup')}>
              <option value="">Select</option>
              {(meta.data?.bloodGroups || []).map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </Select>
          </Field>
          <Field label="Father's name" htmlFor="fathersName" required error={errors.fathersName}>
            <TextInput id="fathersName" value={form.fathersName} disabled={locked}
              error={errors.fathersName} onChange={set('fathersName')} />
          </Field>
          <Field label="Permanent address" htmlFor="permanentAddress" required
            error={errors.permanentAddress} className="sm:col-span-2"
            hint="House number, street, city, state and PIN code.">
            <TextArea id="permanentAddress" rows={3} value={form.permanentAddress} disabled={locked}
              error={errors.permanentAddress} onChange={set('permanentAddress')} />
          </Field>
        </div>
      </section>

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
