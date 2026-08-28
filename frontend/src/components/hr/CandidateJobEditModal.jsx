import { useEffect, useState } from 'react'
import { Button } from '../ui/Button'
import { Field, TextInput } from '../ui/Field'
import { Modal } from '../ui/Modal'
import { useToast } from '../../context/ToastContext'
import { hrService } from '../../services/hrService'

/**
 * The facts HR entered when creating the candidate. Email is shown but not
 * editable: it is where the invitation went and what the portal link is bound
 * to, so changing it would leave a live link pointing at the wrong person.
 */
export function CandidateJobEditModal({ open, onClose, candidate, onSaved }) {
  const toast = useToast()
  const [form, setForm] = useState({ name: '', role: '', department: '' })
  const [errors, setErrors] = useState({})
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!open) return
    setErrors({})
    setForm({
      name: candidate.name || '',
      role: candidate.role || '',
      department: candidate.department || '',
    })
  }, [open, candidate])

  const setValue = (field) => (event) => {
    setForm((current) => ({ ...current, [field]: event.target.value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  const submit = async () => {
    const next = {}
    if (!form.name.trim()) next.name = 'Candidate name is required'
    if (!form.role.trim()) next.role = 'Role is required'
    if (!form.department.trim()) next.department = 'Department is required'
    setErrors(next)
    if (Object.keys(next).length) return

    setSaving(true)
    try {
      const saved = await hrService.updateCandidate(candidate.id, {
        name: form.name.trim(),
        role: form.role.trim(),
        department: form.department.trim(),
      })
      toast.success('Candidate updated', 'The change is recorded in the audit trail.')
      onSaved(saved)
    } catch (error) {
      if (error.fieldErrors && Object.keys(error.fieldErrors).length) setErrors(error.fieldErrors)
      toast.apiError(error, 'Could not update the candidate')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      open={open}
      onClose={() => (saving ? null : onClose())}
      title="Edit candidate"
      description="Name, role and department as they appear across the portal."
      size="md"
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={saving}>Cancel</Button>
          <Button onClick={submit} loading={saving}>Save changes</Button>
        </>
      }
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Candidate name" htmlFor="candidate-edit-name" required error={errors.name}
          className="sm:col-span-2">
          <TextInput id="candidate-edit-name" value={form.name} error={errors.name}
            onChange={setValue('name')} />
        </Field>
        <Field label="Role" htmlFor="candidate-edit-role" required error={errors.role}>
          <TextInput id="candidate-edit-role" value={form.role} error={errors.role}
            onChange={setValue('role')} />
        </Field>
        <Field label="Department" htmlFor="candidate-edit-department" required error={errors.department}>
          <TextInput id="candidate-edit-department" value={form.department} error={errors.department}
            onChange={setValue('department')} />
        </Field>
        <Field label="Email" htmlFor="candidate-edit-email" className="sm:col-span-2"
          hint="Not editable - the invitation and portal link are bound to this address.">
          <TextInput id="candidate-edit-email" value={candidate.email} disabled />
        </Field>
      </div>
    </Modal>
  )
}
