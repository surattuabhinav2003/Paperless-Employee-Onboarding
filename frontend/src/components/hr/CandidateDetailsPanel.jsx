import { useState } from 'react'
import { customFieldRows } from '../../utils/profileForm'
import { Button } from '../ui/Button'
import { EmptyState } from '../ui/EmptyState'
import { formatDate, formatDateTime } from '../../utils/format'

/**
 * Read-only view of the details the candidate submitted about themselves.
 *
 * HR mostly reads these to re-key them into payroll and HRIS, so every value is
 * copyable - but the buttons stay hidden until a row is hovered. Framing all
 * nine values in their own filled box made the panel read as a wall of text;
 * a definition grid with hairline rules carries the same information quietly.
 */
export function CandidateDetailsPanel({ profile, onEdit, customFields = [] }) {
  const [copiedAll, setCopiedAll] = useState(false)

  if (!profile) {
    return (
      <div>
        <EmptyState
          icon="document"
          title="Details not submitted yet"
          message="The candidate has not filled in their personal details. Their documents cannot be approved
            until they do."
        />
        {onEdit && (
          <div className="flex justify-center pb-2">
            <Button variant="secondary" size="sm" onClick={onEdit}>
              Enter details for them
            </Button>
          </div>
        )}
      </div>
    )
  }

  const fields = [
    { label: 'Full name (as per Aadhaar)', value: profile.fullNameAsPerAadhaar },
    { label: "Father's name", value: profile.fathersName },
    { label: 'Personal email', value: profile.personalEmail },
    { label: 'Contact number', value: profile.contactNumber },
    { label: 'Alternate contact', value: profile.alternateContactNumber },
    { label: 'Date of birth', value: formatDate(profile.dateOfBirth) },
    { label: 'Gender', value: profile.genderLabel },
    { label: 'Blood group', value: profile.bloodGroupLabel },
    { label: 'Permanent address', value: profile.permanentAddress, wide: true },
    { label: 'Aadhaar number', value: profile.aadhaarNumber },
    { label: 'PAN number', value: profile.panNumber },
    { label: 'Emergency contact name', value: profile.emergencyContactName },
    { label: 'Emergency contact relation', value: profile.emergencyContactRelationLabel },
    { label: 'Emergency contact number', value: profile.emergencyContactNumber },
    ...customFieldRows(customFields, profile.customFields),
  ]

  const copyAll = async () => {
    const block = fields
      .filter((field) => field.value)
      .map((field) => `${field.label}: ${field.value}`)
      .join('\n')
    try {
      await navigator.clipboard.writeText(block)
      setCopiedAll(true)
      setTimeout(() => setCopiedAll(false), 2000)
    } catch {
      // Clipboard can be blocked; the per-field values stay selectable.
    }
  }

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h3 className="text-[14.5px] font-semibold text-ink">Personal information</h3>
        <div className="flex items-center gap-2">
          <Button variant="subtle" size="sm" onClick={copyAll}>
            {copiedAll ? 'Copied all' : 'Copy all'}
          </Button>
          {onEdit && (
            <Button variant="secondary" size="sm" onClick={onEdit}>
              Edit
            </Button>
          )}
        </div>
      </div>

      <dl className="r-fields" style={{ marginTop: 14 }}>
        {fields.map((field) => (
          <Field key={field.label} label={field.label} value={field.value} wide={field.wide} />
        ))}
      </dl>

      <p className="r-foot">
        Submitted {formatDateTime(profile.submittedAt)}
        {profile.revision > 1
          && ` · revision ${profile.revision}, last updated ${formatDateTime(profile.updatedAt)}`}
      </p>
    </div>
  )
}

function Field({ label, value, wide = false }) {
  const [copied, setCopied] = useState(false)
  const hasValue = value !== null && value !== undefined && value !== ''

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(String(value))
      setCopied(true)
      setTimeout(() => setCopied(false), 1600)
    } catch {
      // Clipboard can be blocked; the text stays selectable.
    }
  }

  return (
    <div className={`r-field${wide ? ' r-field--wide' : ''}`}>
      <dt>{label}</dt>
      <dd className={hasValue ? undefined : 'is-empty'}>{hasValue ? value : 'Not provided'}</dd>

      {hasValue && (
        <button
          type="button"
          className={`r-copy${copied ? ' is-done' : ''}`}
          onClick={copy}
          aria-label={copied ? `${label} copied` : `Copy ${label}`}
          title={copied ? 'Copied' : `Copy ${label}`}
        >
          {copied ? (
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3"
              strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M20 6 9 17l-5-5" />
            </svg>
          ) : (
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9"
              strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <rect x="9" y="9" width="11" height="11" rx="2" />
              <path d="M5 15V5a2 2 0 0 1 2-2h8" />
            </svg>
          )}
        </button>
      )}
    </div>
  )
}
