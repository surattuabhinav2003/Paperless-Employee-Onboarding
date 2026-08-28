import { useEffect, useState } from 'react'
import { Button } from '../ui/Button'
import { Checkbox, Field, Select, TextInput } from '../ui/Field'
import { Modal } from '../ui/Modal'
import { useToast } from '../../context/ToastContext'
import { hrService } from '../../services/hrService'

const GROUPS = [
  { value: 'other', label: 'Other' },
  { value: 'education', label: 'Education' },
  { value: 'identity', label: 'Identity & personal' },
  { value: 'employment', label: 'Previous employment' },
  { value: 'payroll', label: 'Payroll' },
]

const EMPTY = { label: '', description: '', group: 'other', enabled: true }

/**
 * Creating or editing a document type of the company's own.
 *
 * <p>Deliberately fewer settings than the built-in types have: those carry
 * behaviour an admin cannot supply at runtime, like which education course a
 * certificate belongs to. What is here is what a new type can honestly be - a
 * name, a section, and a note for the candidate.
 */
export function DocumentTypeModal({ open, onClose, documentType, onSaved }) {
  const toast = useToast()
  const [form, setForm] = useState(EMPTY)
  const [errors, setErrors] = useState({})
  const [saving, setSaving] = useState(false)

  const editing = Boolean(documentType)

  useEffect(() => {
    if (!open) return
    setErrors({})
    setForm(documentType
      ? {
          label: documentType.label,
          description: documentType.description || '',
          group: documentType.group,
          enabled: documentType.enabled,
        }
      : EMPTY)
  }, [open, documentType])

  const setValue = (key, value) => {
    setForm((current) => ({ ...current, [key]: value }))
    setErrors((current) => ({ ...current, [key]: undefined }))
  }

  const close = () => {
    if (saving) return
    onClose()
  }

  const submit = async (event) => {
    event?.preventDefault()
    if (!form.label.trim()) {
      setErrors({ label: 'Give the document a name' })
      return
    }

    setSaving(true)
    try {
      const payload = {
        label: form.label.trim(),
        description: form.description.trim() || null,
        group: form.group,
        enabled: form.enabled,
      }
      const types = editing
        ? await hrService.updateDocumentType(documentType.id, payload)
        : await hrService.addDocumentType(payload)

      toast.success(editing ? 'Document type updated' : 'Document type added',
        editing
          ? `"${payload.label}" is updated everywhere it appears.`
          : `HR can now ask candidates for "${payload.label}".`)
      onSaved(types)
      onClose()
    } catch (error) {
      if (error.fieldErrors && Object.keys(error.fieldErrors).length) {
        setErrors(error.fieldErrors)
      }
      toast.apiError(error, editing ? 'Could not update this type' : 'Could not add this type')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      open={open}
      onClose={close}
      title={editing ? 'Edit document type' : 'Add a document type'}
      description={editing
        ? 'Changes apply everywhere this type appears, including on candidates already asked for it.'
        : 'A document of your own, requested alongside the built-in ones.'}
      size="lg"
      footer={
        <>
          <Button variant="ghost" onClick={close} disabled={saving}>
            Cancel
          </Button>
          <Button onClick={submit} loading={saving}>
            {editing ? 'Save changes' : 'Add document type'}
          </Button>
        </>
      }
    >
      <form className="space-y-4" onSubmit={submit} noValidate>
        <Field
          label="Document name"
          htmlFor="dt-label"
          required
          error={errors.label}
          hint="What HR picks from the list and what the candidate is asked to upload."
        >
          <TextInput
            id="dt-label"
            autoFocus
            placeholder="e.g. Police Verification"
            value={form.label}
            error={errors.label}
            onChange={(e) => setValue('label', e.target.value)}
          />
        </Field>

        <Field
          label="Section"
          htmlFor="dt-group"
          error={errors.group}
          hint="Which heading it appears under on the checklist."
        >
          <Select
            id="dt-group"
            value={form.group}
            error={errors.group}
            onChange={(e) => setValue('group', e.target.value)}
          >
            {GROUPS.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </Select>
        </Field>

        <Field
          label="Note for the candidate"
          htmlFor="dt-description"
          error={errors.description}
          hint="Optional. Explains what to upload, e.g. where to obtain it."
        >
          <TextInput
            id="dt-description"
            value={form.description}
            error={errors.description}
            onChange={(e) => setValue('description', e.target.value)}
          />
        </Field>

        <div className="rounded border border-surface-line bg-surface-canvas px-3.5 py-3">
          <Checkbox
            label="Offer this to HR"
            description="Turn off to keep the type without listing it when choosing what to ask for."
            checked={form.enabled}
            onChange={(e) => setValue('enabled', e.target.checked)}
          />
        </div>

        {editing && (
          <p className="text-[12px] leading-5 text-ink-muted">
            Renaming keeps every document already uploaded under this type — the name changes, what the
            files are filed under does not.
          </p>
        )}
      </form>
    </Modal>
  )
}
