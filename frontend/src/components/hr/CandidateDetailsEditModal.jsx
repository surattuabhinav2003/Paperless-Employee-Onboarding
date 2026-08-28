import { useEffect, useState } from 'react'
import { ProfileFormFields } from '../profile/ProfileFormFields'
import { Button } from '../ui/Button'
import { Modal } from '../ui/Modal'
import { useToast } from '../../context/ToastContext'
import { hrService } from '../../services/hrService'
import { applyProfileChange, EMPTY_PROFILE, toProfilePayload, validateProfile } from '../../utils/profileForm'

/**
 * HR correcting the candidate's personal details.
 *
 * <p>The candidate's own form locks once they submit, and tells them to contact
 * HR - this is the other half of that sentence. It stays available at every
 * stage, including after the offer is signed, because a typo in an Aadhaar
 * number still has to be fixable.
 */
export function CandidateDetailsEditModal({ open, onClose, candidateId, candidateName, profile, meta, onSaved }) {
  const toast = useToast()
  const [form, setForm] = useState(EMPTY_PROFILE)
  const [errors, setErrors] = useState({})
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!open) return
    setErrors({})
    setForm(profile
      ? {
          ...EMPTY_PROFILE,
          ...profile,
          alternateContactNumber: profile.alternateContactNumber || '',
          customFields: profile.customFields || {},
        }
      // Nothing submitted yet: seed the name HR already holds so they are not
      // retyping what the system knows.
      : { ...EMPTY_PROFILE, fullNameAsPerAadhaar: candidateName || '' })
  }, [open, profile, candidateName])

  const setValue = (field, value) => {
    setForm((current) => applyProfileChange(current, field, value))
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  const submit = async () => {
    const next = validateProfile(form, {
      forHr: true,
      candidateFields: meta?.candidateFields,
      customFields: meta?.customCandidateFields,
    })
    setErrors(next)
    if (Object.keys(next).length) {
      // The form is taller than the dialog, so a failure further up would
      // otherwise look like the Save button simply doing nothing.
      const firstInvalid = document.getElementById(Object.keys(next)[0])
      firstInvalid?.scrollIntoView({ behavior: 'smooth', block: 'center' })
      firstInvalid?.focus({ preventScroll: true })
      return
    }

    setSaving(true)
    try {
      const saved = await hrService.saveCandidateProfile(candidateId, toProfilePayload(form))
      toast.success(profile ? 'Details updated' : 'Details saved',
        'The change is recorded in the audit trail.')
      onSaved(saved)
    } catch (error) {
      if (error.fieldErrors && Object.keys(error.fieldErrors).length) setErrors(error.fieldErrors)
      toast.apiError(error, 'Could not save the details')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      open={open}
      onClose={() => (saving ? null : onClose())}
      title={profile ? 'Edit personal details' : 'Enter personal details'}
      description={profile
        ? 'Corrections are saved against your name in the audit trail.'
        : 'The candidate has not filled these in. Anything you enter is saved on their behalf.'}
      size="lg"
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={saving}>Cancel</Button>
          <Button onClick={submit} loading={saving}>Save details</Button>
        </>
      }
    >
      <div className="space-y-4">
        <ProfileFormFields form={form} errors={errors} meta={meta} setValue={setValue}
          candidateFields={meta?.candidateFields} customFields={meta?.customCandidateFields} />
      </div>
    </Modal>
  )
}
