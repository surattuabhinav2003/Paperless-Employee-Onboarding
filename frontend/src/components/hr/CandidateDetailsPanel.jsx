import { EmptyState } from '../ui/EmptyState'
import { formatDate, formatDateTime } from '../../utils/format'

/** Read-only view of the details the candidate submitted about themselves. */
export function CandidateDetailsPanel({ profile }) {
  if (!profile) {
    return (
      <EmptyState
        icon="document"
        title="Details not submitted yet"
        message="The candidate has not filled in their personal and education details. Their documents cannot
          be approved until they do."
      />
    )
  }

  return (
    <div className="space-y-4">
      <section>
        <h3 className="text-[14.5px] font-semibold text-ink">Personal information</h3>
        <dl className="mt-3 grid gap-x-6 gap-y-3 sm:grid-cols-2">
          <Row label="Full name (as per Aadhaar)" value={profile.fullNameAsPerAadhaar} />
          <Row label="Father's name" value={profile.fathersName} />
          <Row label="Personal email" value={profile.personalEmail} />
          <Row label="Contact number" value={profile.contactNumber} />
          <Row label="Alternate contact" value={profile.alternateContactNumber} />
          <Row label="Date of birth" value={formatDate(profile.dateOfBirth)} />
          <Row label="Gender" value={profile.genderLabel} />
          <Row label="Blood group" value={profile.bloodGroupLabel} />
          <Row label="Permanent address" value={profile.permanentAddress} span />
        </dl>
      </section>

      <p className="text-[12.5px] text-ink-muted">
        Education is recorded per certificate on the Documents tab, including the course each one is for.
      </p>

      <p className="border-t border-surface-line pt-3 text-[12px] text-ink-muted">
        Submitted {formatDateTime(profile.submittedAt)}
        {profile.revision > 1 && ` · revision ${profile.revision}, last updated ${formatDateTime(profile.updatedAt)}`}
      </p>
    </div>
  )
}

function Row({ label, value, span = false }) {
  return (
    <div className={span ? 'sm:col-span-2' : undefined}>
      <dt className="text-[11.5px] font-medium uppercase tracking-[0.1em] text-ink-muted">{label}</dt>
      <dd className="mt-0.5 text-[13.5px] text-ink-body">
        {value === null || value === undefined || value === '' ? (
          <span className="text-ink-muted">Not provided</span>
        ) : (
          value
        )}
      </dd>
    </div>
  )
}
