import { useRef, useState } from 'react'
import { Button } from '../ui/Button'
import { Field, TextInput } from '../ui/Field'
import { Modal } from '../ui/Modal'
import { useToast } from '../../context/ToastContext'
import { hrService } from '../../services/hrService'

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/* Which domains are allowed comes from the server, never a copy here - the two
   drifted once already, and the form happily accepted an address the API then
   rejected. Empty list means no restriction. */
function isAllowedAddress(email, domains) {
  if (!domains || domains.length === 0) return true
  const domain = email.trim().toLowerCase().split('@')[1] || ''
  return domains.some((d) => d.trim().toLowerCase() === domain)
}

const EMPTY = { recipientName: '', recipientEmail: '', title: '' }

/**
 * Creates an NDA + NOC packet.
 *
 * <p>Both PDFs are uploaded together and combined server-side before anything
 * else happens, because the fields HR places next are positioned against pages
 * of the <em>combined</em> document. The recipient then signs one document once.
 */
export function NewNocModal({ open, onClose, onCreated, allowedDomains = [] }) {
  const toast = useToast()
  const [form, setForm] = useState(EMPTY)
  const [nda, setNda] = useState(null)
  const [noc, setNoc] = useState(null)
  const [errors, setErrors] = useState({})
  const [saving, setSaving] = useState(false)
  const ndaRef = useRef(null)
  const nocRef = useRef(null)

  const reset = () => {
    setForm(EMPTY)
    setNda(null)
    setNoc(null)
    setErrors({})
    if (ndaRef.current) ndaRef.current.value = ''
    if (nocRef.current) nocRef.current.value = ''
  }

  const close = () => {
    if (saving) return
    reset()
    onClose()
  }

  const validate = () => {
    const next = {}
    if (!form.recipientName.trim()) next.recipientName = 'Recipient name is required'
    if (!form.recipientEmail.trim()) next.recipientEmail = 'Email is required'
    else if (!EMAIL_RE.test(form.recipientEmail.trim())) next.recipientEmail = 'Enter a valid email address'
    else if (!isAllowedAddress(form.recipientEmail, allowedDomains)) {
      next.recipientEmail = `Use a company Microsoft account (${allowedDomains.join(', ')})`
    }
    if (!nda) next.nda = 'Upload the NDA'
    if (!noc) next.noc = 'Upload the NOC'
    setErrors(next)
    return Object.keys(next).length === 0
  }

  const submit = async () => {
    if (!validate()) return
    setSaving(true)
    try {
      const created = await hrService.createNoc({
        nda,
        noc,
        recipientName: form.recipientName.trim(),
        recipientEmail: form.recipientEmail.trim(),
        title: form.title.trim(),
      })
      toast.success(
        'Documents combined',
        `The NDA and NOC are now one ${created.pageCount}-page document. Place the fields next.`,
      )
      reset()
      onCreated(created)
    } catch (error) {
      if (error.fieldErrors && Object.keys(error.fieldErrors).length) setErrors(error.fieldErrors)
      toast.apiError(error, 'Could not combine these documents')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      open={open}
      onClose={close}
      title="New NDA + NOC"
      description="Upload both documents. They are combined into one, so the recipient signs once."
      size="lg"
      footer={
        <>
          <Button variant="ghost" onClick={close} disabled={saving}>Cancel</Button>
          <Button onClick={submit} loading={saving}>Combine &amp; add fields</Button>
        </>
      }
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Recipient name" htmlFor="noc-name" required error={errors.recipientName}>
          <TextInput
            id="noc-name"
            value={form.recipientName}
            error={errors.recipientName}
            placeholder="Nikhil Menon"
            onChange={(event) => setForm({ ...form, recipientName: event.target.value })}
          />
        </Field>
        <Field label="Email" htmlFor="noc-email" required error={errors.recipientEmail}
          hint={allowedDomains.length ? `Company Microsoft account only - ${allowedDomains.join(", ")}` : 'The signing link goes here.'}>
          <TextInput
            id="noc-email"
            type="email"
            value={form.recipientEmail}
            error={errors.recipientEmail}
            placeholder="nikhil.menon@cloudfuze.com"
            onChange={(event) => setForm({ ...form, recipientEmail: event.target.value })}
          />
        </Field>
      </div>

      <div className="mt-4">
        <Field label="Title" htmlFor="noc-title" hint="Optional. Shown to the recipient and in the list.">
          <TextInput
            id="noc-title"
            value={form.title}
            placeholder="NDA and NOC 2026"
            onChange={(event) => setForm({ ...form, title: event.target.value })}
          />
        </Field>
      </div>

      <div className="mt-6">
        <h3 className="text-[14px] font-semibold text-ink">The two documents</h3>
        <p className="mt-1 text-[12.5px] text-ink-muted">
          PDF or Word. Word files are converted to PDF automatically, then joined in this order -
          NDA first, then NOC - into a single document the recipient reads and signs in one pass.
        </p>

        <div className="mt-3 grid gap-3 sm:grid-cols-2">
          <FilePick
            label="NDA"
            inputRef={ndaRef}
            file={nda}
            error={errors.nda}
            onPick={(file) => { setNda(file); setErrors((e) => ({ ...e, nda: undefined })) }}
          />
          <FilePick
            label="NOC"
            inputRef={nocRef}
            file={noc}
            error={errors.noc}
            onPick={(file) => { setNoc(file); setErrors((e) => ({ ...e, noc: undefined })) }}
          />
        </div>
      </div>
    </Modal>
  )
}

function FilePick({ label, inputRef, file, error, onPick }) {
  return (
    <div>
      <div
        className={`rounded border px-3.5 py-3 transition ${
          error ? 'border-accent-red bg-[#FFF5F5]'
            : file ? 'border-brand bg-brand-tint/50' : 'border-surface-line bg-white hover:border-brand/40'
        }`}
      >
        <p className="text-[11px] font-semibold uppercase tracking-[0.12em] text-ink-muted">{label}</p>
        <p className="mt-1 truncate text-[13.5px] font-medium text-ink">
          {file ? file.name : 'No file chosen'}
        </p>
        <input
          ref={inputRef}
          type="file"
          accept=".pdf,.doc,.docx"
          className="hidden"
          onChange={(event) => onPick(event.target.files?.[0] || null)}
        />
        <Button variant="secondary" size="sm" className="mt-2" onClick={() => inputRef.current?.click()}>
          {file ? 'Choose another' : `Choose ${label}`}
        </Button>
      </div>
      {error && <p className="mt-1 text-[12px] text-accent-red">{error}</p>}
    </div>
  )
}
