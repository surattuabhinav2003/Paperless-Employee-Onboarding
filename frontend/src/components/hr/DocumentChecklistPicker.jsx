import { useMemo } from 'react'
import { groupWithPayslipBundle } from '../../utils/documentBundles'

/** What a brand-new candidate is asked for unless HR changes it. */
export const DEFAULT_DOCUMENT_SELECTION = [
  'ssc_certificate',
  'secondary_education_certificate',
  'higher_education_provisional',
  'higher_education_original_degree',
  'higher_education_marksheet',
  'aadhaar_id',
  'pan_card',
  'passport_photo',
]

/**
 * Turns the two selection sets into the payload shape the API expects.
 * Anything selected is mandatory unless HR marked it optional.
 */
export function toRequiredDocuments(selected, optional) {
  return Array.from(selected).map((type) => ({ type, mandatory: !optional.has(type) }))
}

/**
 * The "required documents" checklist, shared by the single-candidate form and
 * the bulk invite so the two can never drift on what a bundle means or how
 * mandatory/optional is expressed.
 *
 * <p>A bundled card (payslips) toggles every underlying code together, so it is
 * always all six or none - the one checkbox HR sees stays honest about what
 * selecting it actually requires of the candidate.
 */
export function DocumentChecklistPicker({
  documentTypes = [],
  selected,
  optional,
  setSelected,
  setOptional,
  error,
  title = 'Required documents',
  description = 'Pick what this candidate must provide. Mandatory documents gate the offer stage; optional ones do not.',
}) {
  const optionGroups = useMemo(() => groupWithPayslipBundle(documentTypes), [documentTypes])

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

  return (
    <div>
      <div className="flex items-end justify-between gap-3">
        <div>
          <h3 className="text-[14px] font-semibold text-ink">{title}</h3>
          <p className="mt-1 text-[12.5px] text-ink-muted">{description}</p>
        </div>
        <span className="shrink-0 rounded bg-brand-tint px-2.5 py-1 text-[11.5px] font-medium text-brand">
          {selected.size} document{selected.size === 1 ? '' : 's'}
        </span>
      </div>

      {error && <p className="mt-2 text-[12px] text-accent-red">{error}</p>}

      {/* Grouped exactly as the backend describes them: identity, education,
          previous employment. Three columns once there is room, so a group
          like previous employment (nine items, six of them payslips) reads
          as a tidy grid instead of one long single-file list. */}
      {optionGroups.map(([group, groupOptions]) => (
        <div key={group} className="mt-4">
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
                            borderColor: isOptional ? '#EBEBEB' : '#234297',
                            color: isOptional ? '#707070' : '#234297',
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
      ))}
    </div>
  )
}
