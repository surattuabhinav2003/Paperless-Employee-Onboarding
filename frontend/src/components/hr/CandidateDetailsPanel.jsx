import { useState } from 'react'
import { Button } from '../ui/Button'
import { EmptyState } from '../ui/EmptyState'
import { formatDate, formatDateTime } from '../../utils/format'

/**
 * Read-only view of the details the candidate submitted about themselves.
 * <p>
 * Every value is a framed row with its own copy button, since HR mostly reads
 * these to re-key them into payroll and HRIS systems.
 */
export function CandidateDetailsPanel({ profile }) {
  const [copiedAll, setCopiedAll] = useState(false)

  if (!profile) {
    return (
      <EmptyState
        icon="document"
        title="Details not submitted yet"
        message="The candidate has not filled in their personal details. Their documents cannot be approved
          until they do."
      />
    )
  }

  const fields = [
    { label: 'Full name (as per Aadhaar)', value: profile.fullNameAsPerAadhaar },
    { label: "Father's name", value: profile.fathersName },
    { label: 'Personal email', value: profile.personalEmail },
    { label: 'Contact number', value: profile.contactNumber },
    { label: 'Alternate contact', value: profile.alternateContactNumber },
    { label: 'Date of birth', value: formatDate(profile.dateOfBirth), raw: profile.dateOfBirth },
    { label: 'Gender', value: profile.genderLabel },
    { label: 'Blood group', value: profile.bloodGroupLabel },
    { label: 'Permanent address', value: profile.permanentAddress, span: true },
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
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h3 className="text-[14.5px] font-semibold text-ink">Personal information</h3>
        <Button variant="subtle" size="sm" onClick={copyAll}>
          {copiedAll ? 'Copied all' : 'Copy all'}
        </Button>
      </div>

      <dl className="grid gap-3 sm:grid-cols-2">
        {fields.map((field) => (
          <CopyableField key={field.label} label={field.label} value={field.value} span={field.span} />
        ))}
      </dl>

      <p className="flex items-start gap-2 rounded border border-surface-line bg-surface-offwhite/70
        px-3.5 py-2.5 text-[12.5px] leading-5 text-ink-muted">
        <svg viewBox="0 0 24 24" className="mt-0.5 h-3.5 w-3.5 shrink-0" fill="none" stroke="currentColor"
          strokeWidth="1.9" strokeLinecap="round">
          <circle cx="12" cy="12" r="9" />
          <path d="M12 16v-5m0-3h.01" />
        </svg>
        Education is recorded per certificate on the Documents tab, including the course each one is for.
      </p>

      <p className="border-t border-surface-line pt-3 text-[12px] text-ink-muted">
        Submitted {formatDateTime(profile.submittedAt)}
        {profile.revision > 1 && ` · revision ${profile.revision}, last updated ${formatDateTime(profile.updatedAt)}`}
      </p>
    </div>
  )
}

function CopyableField({ label, value, span = false }) {
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
    <div
      className={`group flex items-start justify-between gap-3 rounded border border-surface-line
        bg-surface-offwhite/60 px-3.5 py-2.5 transition hover:border-brand/35 hover:bg-brand-tint/30
        ${span ? 'sm:col-span-2' : ''}`}
    >
      <div className="min-w-0">
        <dt className="text-[10.5px] font-semibold uppercase tracking-[0.1em] text-ink-muted">{label}</dt>
        <dd className={`mt-1 break-words text-[13.5px] ${hasValue ? 'font-medium text-ink' : 'text-ink-muted'}`}>
          {hasValue ? value : 'Not provided'}
        </dd>
      </div>

      {hasValue && (
        <button
          type="button"
          onClick={copy}
          aria-label={copied ? `${label} copied` : `Copy ${label}`}
          title={copied ? 'Copied' : `Copy ${label}`}
          className={`shrink-0 rounded p-1.5 transition ${
            copied
              ? 'text-accent-green'
              : 'text-ink-muted opacity-60 hover:bg-white hover:text-brand group-hover:opacity-100'
          }`}
        >
          {copied ? (
            <svg viewBox="0 0 24 24" className="h-3.5 w-3.5" fill="none" stroke="currentColor"
              strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round">
              <path d="M20 6 9 17l-5-5" />
            </svg>
          ) : (
            <svg viewBox="0 0 24 24" className="h-3.5 w-3.5" fill="none" stroke="currentColor"
              strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round">
              <rect x="9" y="9" width="11" height="11" rx="2" />
              <path d="M5 15V5a2 2 0 0 1 2-2h8" />
            </svg>
          )}
        </button>
      )}
    </div>
  )
}
