import { Button } from '../ui/Button'
import { CopyField } from '../ui/CopyField'
import { Modal } from '../ui/Modal'
import { formatDateTime } from '../../utils/format'

/**
 * Shown once, right after a portal link is generated. The raw token exists only
 * in this response - the backend stores a hash of it.
 */
export function InviteLinkModal({ open, onClose, invitation, candidateName, regenerated = false }) {
  if (!invitation) return null
  return (
    <Modal
      open={open}
      onClose={onClose}
      title={regenerated ? 'New portal link generated' : 'Candidate created'}
      description={
        regenerated
          ? 'The previous link stopped working the moment this one was created.'
          : 'One invitation email covering documents, offer and bond has been sent.'
      }
      size="md"
      footer={<Button onClick={onClose}>Done</Button>}
    >
      <div className="space-y-4">
        <div className="rounded-card border border-accent-green/30 bg-[#F3FCF7] px-4 py-3">
          <p className="text-[13px] text-ink-body">
            {invitation.sentAt ? (
              <>
                Invitation emailed to <strong>{invitation.emailedTo}</strong> at{' '}
                {formatDateTime(invitation.sentAt)} via the{' '}
                <strong>{invitation.emailProvider}</strong> transport.
              </>
            ) : (
              <>
                The link is ready, but the email could not be delivered right now. Share the link below or
                resend from the pipeline.
              </>
            )}
          </p>
        </div>

        <CopyField label="Secure portal link" value={invitation.portalUrl} />

        <dl className="grid gap-3 text-[12.5px] sm:grid-cols-2">
          <div className="rounded border border-surface-line bg-surface-offwhite/60 px-3 py-2.5">
            <dt className="text-ink-muted">Candidate</dt>
            <dd className="mt-0.5 font-medium text-ink">{candidateName || invitation.emailedTo}</dd>
          </div>
          <div className="rounded border border-surface-line bg-surface-offwhite/60 px-3 py-2.5">
            <dt className="text-ink-muted">Link expires</dt>
            <dd className="mt-0.5 font-medium text-ink">{formatDateTime(invitation.expiresAt)}</dd>
          </div>
        </dl>

        <p className="text-[12px] leading-5 text-ink-muted">
          This is the only time the link is shown. It is stored as a hash, so it cannot be retrieved later -
          generate a new one if the candidate loses it.
        </p>
      </div>
    </Modal>
  )
}
