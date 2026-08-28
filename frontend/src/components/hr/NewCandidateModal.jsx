import { useState } from 'react'
import { Button } from '../ui/Button'
import { Field, TextInput } from '../ui/Field'
import { Modal } from '../ui/Modal'
import { useToast } from '../../context/ToastContext'
import { hrService } from '../../services/hrService'
import { DEFAULT_DOCUMENT_SELECTION, DocumentChecklistPicker, toRequiredDocuments } from './DocumentChecklistPicker'

const EMPTY_FORM = { name: '', email: '', role: '', department: '' }

/**
 * New candidate form. On submit the backend creates the candidate, issues one
 * portal token and sends exactly one invitation email; the freshly generated
 * link comes back so HR can copy it if needed.
 */
export function NewCandidateModal({ open, onClose, documentTypes = [], onCreated }) {
  const toast = useToast()
  const [form, setForm] = useState(EMPTY_FORM)
  const [selected, setSelected] = useState(() => new Set(DEFAULT_DOCUMENT_SELECTION))
  const [optional, setOptional] = useState(() => new Set())
  const [errors, setErrors] = useState({})
  const [saving, setSaving] = useState(false)

  const reset = () => {
    setForm(EMPTY_FORM)
    setSelected(new Set(DEFAULT_DOCUMENT_SELECTION))
    setOptional(new Set())
    setErrors({})
  }

  const close = () => {
    if (saving) return
    reset()
    onClose()
  }

  const validate = () => {
    const next = {}
    if (!form.name.trim()) next.name = 'Candidate name is required'
    if (!form.email.trim()) next.email = 'Email is required'
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) next.email = 'Enter a valid email address'
    if (!form.role.trim()) next.role = 'Role is required'
    if (!form.department.trim()) next.department = 'Department is required'
    if (selected.size === 0) next.requiredDocuments = 'Select at least one document'
    setErrors(next)
    return Object.keys(next).length === 0
  }

  const submit = async () => {
    if (!validate()) return
    setSaving(true)
    try {
      const payload = {
        name: form.name.trim(),
        email: form.email.trim(),
        role: form.role.trim(),
        department: form.department.trim(),
        requiredDocuments: toRequiredDocuments(selected, optional),
      }
      const created = await hrService.createCandidate(payload)
      toast.success(
        'Candidate created',
        `One invitation email was sent to ${created.candidate.email}.`,
      )
      reset()
      onCreated(created)
    } catch (error) {
      if (error.fieldErrors && Object.keys(error.fieldErrors).length) {
        setErrors(error.fieldErrors)
      }
      toast.apiError(error, 'Could not create candidate')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      open={open}
      onClose={close}
      title="New candidate"
      description="Creates the candidate, generates one secure portal link and emails the invitation."
      size="xl"
      footer={
        <>
          <Button variant="ghost" onClick={close} disabled={saving}>
            Cancel
          </Button>
          <Button onClick={submit} loading={saving}>
            Create candidate & send invite
          </Button>
        </>
      }
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Candidate name" htmlFor="candidate-name" required error={errors.name}>
          <TextInput
            id="candidate-name"
            value={form.name}
            error={errors.name}
            placeholder="Priya Sharma"
            onChange={(event) => setForm({ ...form, name: event.target.value })}
          />
        </Field>
        <Field label="Email" htmlFor="candidate-email" required error={errors.email}
          hint="The invitation and portal link go here.">
          <TextInput
            id="candidate-email"
            type="email"
            value={form.email}
            error={errors.email}
            placeholder="priya.sharma@example.com"
            onChange={(event) => setForm({ ...form, email: event.target.value })}
          />
        </Field>
        <Field label="Role" htmlFor="candidate-role" required error={errors.role}>
          <TextInput
            id="candidate-role"
            value={form.role}
            error={errors.role}
            placeholder="Software Engineer"
            onChange={(event) => setForm({ ...form, role: event.target.value })}
          />
        </Field>
        <Field label="Department" htmlFor="candidate-department" required error={errors.department}>
          <TextInput
            id="candidate-department"
            value={form.department}
            error={errors.department}
            placeholder="Engineering"
            onChange={(event) => setForm({ ...form, department: event.target.value })}
          />
        </Field>
      </div>

      <div className="mt-6">
        <DocumentChecklistPicker
          documentTypes={documentTypes}
          selected={selected}
          optional={optional}
          setSelected={setSelected}
          setOptional={setOptional}
          error={errors.requiredDocuments}
        />
      </div>
    </Modal>
  )
}
