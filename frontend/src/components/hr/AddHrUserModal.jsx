import { useState } from 'react'
import { Button } from '../ui/Button'
import { Field, Select, TextInput } from '../ui/Field'
import { Modal } from '../ui/Modal'
import { useToast } from '../../context/ToastContext'
import { hrService } from '../../services/hrService'

const EMPTY = { email: '', fullName: '', jobTitle: '', role: 'hr' }
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/**
 * Adds someone to the console before they have signed in.
 *
 * <p>Only the address is really needed - the name arrives from Microsoft the
 * first time they sign in - so everything else is optional and the form stays
 * one field wide in practice.
 */
export function AddHrUserModal({ open, onClose, onAdded }) {
  const toast = useToast()
  const [form, setForm] = useState(EMPTY)
  const [errors, setErrors] = useState({})
  const [saving, setSaving] = useState(false)

  const setValue = (key, value) => {
    setForm((current) => ({ ...current, [key]: value }))
    setErrors((current) => ({ ...current, [key]: undefined }))
  }

  const close = () => {
    if (saving) return
    setForm(EMPTY)
    setErrors({})
    onClose()
  }

  const submit = async (event) => {
    event.preventDefault()
    const email = form.email.trim()
    if (!email) return setErrors({ email: 'Work email is required' })
    if (!EMAIL_PATTERN.test(email)) return setErrors({ email: 'Enter a valid work email address' })

    setSaving(true)
    try {
      const added = await hrService.addUser({
        email,
        fullName: form.fullName.trim(),
        jobTitle: form.jobTitle.trim(),
        role: form.role,
      })
      toast.success(
        form.role === 'admin' ? 'Administrator added' : 'Person added',
        `${added.email} can sign in with Microsoft using this address.`,
      )
      onAdded(added)
      setForm(EMPTY)
      setErrors({})
      onClose()
    } catch (error) {
      if (error.fieldErrors && Object.keys(error.fieldErrors).length) {
        setErrors(error.fieldErrors)
      } else {
        // A duplicate address is the common failure, and it is about the email
        // box rather than the form as a whole.
        setErrors({ email: error.message })
      }
      toast.apiError(error, 'Could not add this person')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      open={open}
      onClose={close}
      title="Add someone to the console"
      description="They sign in with their Microsoft work account - there is no password to set or send."
      size="md"
      footer={
        <>
          <Button variant="ghost" onClick={close} disabled={saving}>
            Cancel
          </Button>
          <Button onClick={submit} loading={saving}>
            Add person
          </Button>
        </>
      }
    >
      <form className="space-y-4" onSubmit={submit} noValidate>
        <Field label="Work email" htmlFor="hr-email" required error={errors.email}>
          <TextInput
            id="hr-email"
            type="email"
            autoFocus
            placeholder="name@cloudfuze.com"
            value={form.email}
            error={errors.email}
            onChange={(e) => setValue('email', e.target.value)}
          />
        </Field>

        <div className="grid gap-4 sm:grid-cols-2">
          <Field
            label="Full name"
            htmlFor="hr-name"
            error={errors.fullName}
            hint="Optional - filled in from Microsoft when they sign in."
          >
            <TextInput
              id="hr-name"
              value={form.fullName}
              error={errors.fullName}
              onChange={(e) => setValue('fullName', e.target.value)}
            />
          </Field>

          <Field label="Job title" htmlFor="hr-title" error={errors.jobTitle}>
            <TextInput
              id="hr-title"
              value={form.jobTitle}
              error={errors.jobTitle}
              onChange={(e) => setValue('jobTitle', e.target.value)}
            />
          </Field>
        </div>

        <Field
          label="Access"
          htmlFor="hr-role"
          error={errors.role}
          hint={form.role === 'admin'
            ? 'Administrators can add people, grant admin, and change what candidates are asked for.'
            : 'Can run onboarding: invite candidates, review documents, approve.'}
        >
          <Select
            id="hr-role"
            value={form.role}
            error={errors.role}
            onChange={(e) => setValue('role', e.target.value)}
          >
            <option value="hr">HR - day-to-day onboarding</option>
            <option value="admin">Administrator - can also change settings</option>
          </Select>
        </Field>
      </form>
    </Modal>
  )
}
