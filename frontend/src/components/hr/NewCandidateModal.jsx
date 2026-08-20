import { useMemo, useState } from 'react'
import { Button } from '../ui/Button'
import { Field, TextInput } from '../ui/Field'
import { Modal } from '../ui/Modal'
import { useToast } from '../../context/ToastContext'
import { hrService } from '../../services/hrService'

const DEFAULT_SELECTION = [
  'ssc_certificate',
  'secondary_education_certificate',
  'higher_education_provisional',
  'higher_education_marksheet',
  'aadhaar_id',
  'pan_card',
  'passport_photo',
]

const EMPTY_FORM = { name: '', email: '', role: '', department: '' }

/**
 * New candidate form. On submit the backend creates the candidate, issues one
 * portal token and sends exactly one invitation email; the freshly generated
 * link comes back so HR can copy it if needed.
 */
export function NewCandidateModal({ open, onClose, documentTypes = [], onCreated }) {
  const toast = useToast()
  const [form, setForm] = useState(EMPTY_FORM)
  const [selected, setSelected] = useState(() => new Set(DEFAULT_SELECTION))
  const [optional, setOptional] = useState(() => new Set())
  const [errors, setErrors] = useState({})
  const [saving, setSaving] = useState(false)

  const optionGroups = useMemo(() => {
    const groups = new Map()
    documentTypes.forEach((option) => {
      const key = option.group || 'Other'
      if (!groups.has(key)) groups.set(key, [])
      groups.get(key).push(option)
    })
    return [...groups.entries()]
  }, [documentTypes])

  const reset = () => {
    setForm(EMPTY_FORM)
    setSelected(new Set(DEFAULT_SELECTION))
    setOptional(new Set())
    setErrors({})
  }

  const close = () => {
    if (saving) return
    reset()
    onClose()
  }

  const toggle = (value) => {
    setSelected((current) => {
      const next = new Set(current)
      if (next.has(value)) {
        next.delete(value)
        setOptional((opt) => {
          const nextOpt = new Set(opt)
          nextOpt.delete(value)
          return nextOpt
        })
      } else {
        next.add(value)
      }
      return next
    })
  }

  const toggleOptional = (value) => {
    setOptional((current) => {
      const next = new Set(current)
      if (next.has(value)) next.delete(value)
      else next.add(value)
      return next
    })
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
        requiredDocuments: Array.from(selected).map((type) => ({
          type,
          mandatory: !optional.has(type),
        })),
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
      size="lg"
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
        <div className="flex items-end justify-between gap-3">
          <div>
            <h3 className="text-[14px] font-semibold text-ink">Required documents</h3>
            <p className="mt-1 text-[12.5px] text-ink-muted">
              Pick what this candidate must provide. Mandatory documents gate the offer stage;
              optional ones do not.
            </p>
          </div>
          <span className="shrink-0 rounded-full bg-brand-tint px-2.5 py-1 text-[11.5px] font-medium text-brand">
            {selected.size} selected
          </span>
        </div>

        {errors.requiredDocuments && (
          <p className="mt-2 text-[12px] text-accent-red">{errors.requiredDocuments}</p>
        )}

        {/* Grouped exactly as the backend describes them: identity, education,
            previous employment, payroll. */}
        {optionGroups.map(([group, groupOptions]) => (
          <div key={group} className="mt-4">
            <p className="mb-2 text-[11px] font-semibold uppercase tracking-[0.12em] text-ink-muted">
              {group}
            </p>
            <div className="grid gap-2 sm:grid-cols-2">
              {groupOptions.map((option) => {
                const isSelected = selected.has(option.value)
                return (
                  <div
                    key={option.value}
                    className={`rounded border px-3.5 py-3 transition ${
                      isSelected
                        ? 'border-brand bg-brand-tint/50'
                        : 'border-surface-line bg-white hover:border-brand/40'
                    }`}
                  >
                    <label className="flex cursor-pointer items-start gap-2.5">
                      <input
                        type="checkbox"
                        checked={isSelected}
                        onChange={() => toggle(option.value)}
                        className="mt-0.5 h-4 w-4 shrink-0 cursor-pointer accent-brand"
                      />
                      <span className="min-w-0 flex-1">
                        <span className="block text-[13.5px] font-medium text-ink">{option.label}</span>
                        {isSelected && (
                          <button
                            type="button"
                            onClick={(event) => {
                              event.preventDefault()
                              toggleOptional(option.value)
                            }}
                            className="mt-1.5 inline-flex items-center gap-1 rounded-full border px-2 py-0.5
                              text-[11px] font-medium transition"
                            style={{
                              borderColor: optional.has(option.value) ? '#EBEBEB' : '#0129AC',
                              color: optional.has(option.value) ? '#707070' : '#0129AC',
                            }}
                          >
                            {optional.has(option.value) ? 'Optional' : 'Mandatory'}
                            <svg viewBox="0 0 24 24" className="h-3 w-3" fill="none" stroke="currentColor"
                              strokeWidth="2">
                              <path d="m7 10 5 5 5-5" />
                            </svg>
                          </button>
                        )}
                      </span>
                    </label>
                  </div>
                )
              })}
            </div>
          </div>
        ))}
      </div>
    </Modal>
  )
}
