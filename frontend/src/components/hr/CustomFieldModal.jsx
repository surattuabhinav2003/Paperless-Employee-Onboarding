import { useEffect, useState } from 'react'
import { Button } from '../ui/Button'
import { Checkbox, Field, Select, TextArea, TextInput } from '../ui/Field'
import { Modal } from '../ui/Modal'
import { useToast } from '../../context/ToastContext'
import { hrService } from '../../services/hrService'

const TYPES = [
  { value: 'text', label: 'Short text' },
  { value: 'textarea', label: 'Long text' },
  { value: 'number', label: 'Number' },
  { value: 'date', label: 'Date' },
  { value: 'email', label: 'Email address' },
  { value: 'phone', label: 'Phone number' },
  { value: 'select', label: 'Choice from a list' },
]

const GROUPS = [
  { value: 'additional', label: 'Additional details' },
  { value: 'personal', label: 'Personal information' },
  { value: 'identity', label: 'Identity numbers' },
  { value: 'emergency', label: 'Emergency contact' },
]

const EMPTY = {
  label: '',
  type: 'text',
  options: '',
  helpText: '',
  group: 'additional',
  enabled: true,
  required: false,
}

/**
 * Creating or editing a detail field an admin invented.
 *
 * <p>The same dialog does both. Editing is deliberately narrower than creating
 * looks: the label, type and settings can all change, but the field's identity
 * cannot - answers already collected are filed under it.
 */
export function CustomFieldModal({ open, onClose, field, onSaved }) {
  const toast = useToast()
  const [form, setForm] = useState(EMPTY)
  const [errors, setErrors] = useState({})
  const [saving, setSaving] = useState(false)

  const editing = Boolean(field)

  useEffect(() => {
    if (!open) return
    setErrors({})
    setForm(field
      ? {
          label: field.label,
          type: field.type,
          options: (field.options || []).join('\n'),
          helpText: field.helpText || '',
          group: field.group,
          enabled: field.enabled,
          required: field.required,
        }
      : EMPTY)
  }, [open, field])

  const setValue = (key, value) => {
    setForm((current) => ({ ...current, [key]: value }))
    setErrors((current) => ({ ...current, [key]: undefined }))
  }

  const close = () => {
    if (saving) return
    onClose()
  }

  const validate = () => {
    const next = {}
    if (!form.label.trim()) {
      next.label = 'Give the field a label'
    }
    if (form.type === 'select') {
      const options = form.options.split('\n').map((o) => o.trim()).filter(Boolean)
      if (options.length < 2) next.options = 'List at least two choices, one per line'
    }
    setErrors(next)
    return Object.keys(next).length === 0
  }

  const submit = async (event) => {
    event?.preventDefault()
    if (!validate()) return

    setSaving(true)
    try {
      const payload = {
        label: form.label.trim(),
        type: form.type,
        options: form.type === 'select' ? form.options : null,
        helpText: form.helpText.trim() || null,
        group: form.group,
        enabled: form.enabled,
        required: form.required,
      }
      const fields = editing
        ? await hrService.updateCustomField(field.id, payload)
        : await hrService.addCustomField(payload)

      toast.success(editing ? 'Field updated' : 'Field added',
        editing
          ? `Candidates now see "${payload.label}" as you have set it.`
          : `Candidates are now asked for "${payload.label}".`)
      onSaved(fields)
      onClose()
    } catch (error) {
      if (error.fieldErrors && Object.keys(error.fieldErrors).length) {
        setErrors(error.fieldErrors)
      }
      toast.apiError(error, editing ? 'Could not update this field' : 'Could not add this field')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      open={open}
      onClose={close}
      title={editing ? 'Edit field' : 'Add a detail field'}
      description={editing
        ? 'Changes apply immediately, to the candidate form and to HR editing a candidate.'
        : 'A question of your own, asked alongside the built-in details.'}
      size="lg"
      footer={
        <>
          <Button variant="ghost" onClick={close} disabled={saving}>
            Cancel
          </Button>
          <Button onClick={submit} loading={saving}>
            {editing ? 'Save changes' : 'Add field'}
          </Button>
        </>
      }
    >
      <form className="space-y-4" onSubmit={submit} noValidate>
        <Field
          label="Label"
          htmlFor="cf-label"
          required
          error={errors.label}
          hint="What the candidate sees above the box, e.g. “T-shirt size”."
        >
          <TextInput
            id="cf-label"
            autoFocus
            value={form.label}
            error={errors.label}
            onChange={(e) => setValue('label', e.target.value)}
          />
        </Field>

        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Answer type" htmlFor="cf-type" error={errors.type}>
            <Select
              id="cf-type"
              value={form.type}
              error={errors.type}
              onChange={(e) => setValue('type', e.target.value)}
            >
              {TYPES.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </Select>
          </Field>

          <Field
            label="Section"
            htmlFor="cf-group"
            error={errors.group}
            hint="Which part of the form it appears under."
          >
            <Select
              id="cf-group"
              value={form.group}
              error={errors.group}
              onChange={(e) => setValue('group', e.target.value)}
            >
              {GROUPS.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </Select>
          </Field>
        </div>

        {/* Only a list needs a list. */}
        {form.type === 'select' && (
          <Field
            label="Choices"
            htmlFor="cf-options"
            required
            error={errors.options}
            hint="One per line. The candidate picks exactly one of these."
          >
            <TextArea
              id="cf-options"
              rows={5}
              value={form.options}
              error={errors.options}
              placeholder={'Small\nMedium\nLarge'}
              onChange={(e) => setValue('options', e.target.value)}
            />
          </Field>
        )}

        <Field
          label="Help text"
          htmlFor="cf-help"
          error={errors.helpText}
          hint="Optional. Shown under the box on the candidate's form."
        >
          <TextInput
            id="cf-help"
            value={form.helpText}
            error={errors.helpText}
            onChange={(e) => setValue('helpText', e.target.value)}
          />
        </Field>

        <div className="space-y-2.5 rounded border border-surface-line bg-surface-canvas px-3.5 py-3">
          <Checkbox
            label="Ask candidates for this"
            description="Turn off to keep the field without showing it on the form."
            checked={form.enabled}
            onChange={(e) => {
              const enabled = e.target.checked
              setForm((current) => ({
                ...current,
                enabled,
                // Off but required cannot be satisfied, so the two move
                // together rather than leaving an impossible combination.
                required: enabled && current.required,
              }))
            }}
          />
          <Checkbox
            label="Required"
            description="Candidates cannot submit their details without answering."
            checked={form.required}
            disabled={!form.enabled}
            onChange={(e) => setValue('required', e.target.checked)}
          />
        </div>

        {editing && (
          <p className="text-[12px] leading-5 text-ink-muted">
            Renaming a field keeps every answer already given — the label changes, what it is attached to
            does not.
          </p>
        )}
      </form>
    </Modal>
  )
}
