import { useMemo, useState } from 'react'
import { Button } from '../ui/Button'
import { Modal } from '../ui/Modal'
import { useToast } from '../../context/ToastContext'
import { hrService } from '../../services/hrService'
import { groupWithPayslipBundle } from '../../utils/documentBundles'

/**
 * Lets HR request more documents from a candidate after the invitation has
 * already gone out - only offers types not already required, so nothing here
 * can duplicate an existing requirement.
 */
export function AddRequiredDocumentModal({ open, onClose, candidateId, documentTypes = [], existingTypes = [],
  onAdded }) {
  const toast = useToast()
  const [selected, setSelected] = useState(() => new Set())
  const [optional, setOptional] = useState(() => new Set())
  const [saving, setSaving] = useState(false)

  const existing = useMemo(() => new Set(existingTypes), [existingTypes])
  const available = useMemo(
    () => documentTypes.filter((option) => !existing.has(option.value)),
    [documentTypes, existing],
  )
  const optionGroups = useMemo(() => groupWithPayslipBundle(available), [available])

  const reset = () => {
    setSelected(new Set())
    setOptional(new Set())
  }

  const close = () => {
    if (saving) return
    reset()
    onClose()
  }

  const toggle = (option) => {
    const codes = option.bundle || [option.value]
    setSelected((current) => {
      const next = new Set(current)
      const allSelected = codes.every((code) => next.has(code))
      if (allSelected) {
        codes.forEach((code) => next.delete(code))
        setOptional((opt) => {
          const nextOpt = new Set(opt)
          codes.forEach((code) => nextOpt.delete(code))
          return nextOpt
        })
      } else {
        codes.forEach((code) => next.add(code))
      }
      return next
    })
  }

  const toggleOptional = (option) => {
    const codes = option.bundle || [option.value]
    setOptional((current) => {
      const next = new Set(current)
      const allOptional = codes.every((code) => next.has(code))
      if (allOptional) codes.forEach((code) => next.delete(code))
      else codes.forEach((code) => next.add(code))
      return next
    })
  }

  const submit = async () => {
    if (selected.size === 0) return
    setSaving(true)
    try {
      const requiredDocuments = Array.from(selected).map((type) => ({
        type,
        mandatory: !optional.has(type),
      }))
      const documents = await hrService.addRequiredDocuments(candidateId, requiredDocuments)
      toast.success('Document requirement added',
        `The candidate can now see ${selected.size === 1 ? 'it' : 'them'} in their portal.`)
      reset()
      onAdded(documents)
    } catch (error) {
      toast.apiError(error, 'Could not add the document requirement')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      open={open}
      onClose={close}
      title="Add document requirement"
      description="Ask this candidate for something extra. It appears in their portal immediately."
      size="xl"
      footer={
        <>
          <Button variant="ghost" onClick={close} disabled={saving}>
            Cancel
          </Button>
          <Button onClick={submit} loading={saving} disabled={selected.size === 0}>
            Add {selected.size > 0 ? `${selected.size} document${selected.size === 1 ? '' : 's'}` : ''}
          </Button>
        </>
      }
    >
      {available.length === 0 ? (
        <p className="py-6 text-center text-[13px] text-ink-muted">
          Every document type is already required for this candidate.
        </p>
      ) : (
        optionGroups.map(([group, groupOptions]) => (
          <div key={group} className="mt-4 first:mt-0">
            <p className="mb-2 text-[11px] font-semibold uppercase tracking-[0.12em] text-ink-muted">
              {group}
            </p>
            <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
              {groupOptions.map((option) => {
                const codes = option.bundle || [option.value]
                const isSelected = codes.every((code) => selected.has(code))
                const isOptional = codes.every((code) => optional.has(code))
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
                        onChange={() => toggle(option)}
                        className="mt-0.5 h-4 w-4 shrink-0 cursor-pointer accent-brand"
                      />
                      <span className="min-w-0 flex-1">
                        <span className="block text-[13.5px] font-medium text-ink">{option.label}</span>
                        {option.hint && (
                          <span className="mt-0.5 block text-[11.5px] text-ink-muted">{option.hint}</span>
                        )}
                        {isSelected && (
                          <button
                            type="button"
                            onClick={(event) => {
                              event.preventDefault()
                              toggleOptional(option)
                            }}
                            className="mt-1.5 inline-flex items-center gap-1 rounded-[3px] border px-2 py-0.5
                              text-[11px] font-medium transition"
                            style={{
                              borderColor: isOptional ? '#EBEBEB' : '#174F96',
                              color: isOptional ? '#707070' : '#174F96',
                            }}
                          >
                            {isOptional ? 'Optional' : 'Mandatory'}
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
        ))
      )}
    </Modal>
  )
}
