/**
 * The candidate's personal-details form, shared by the candidate's own portal
 * page and HR's correction dialog. Both must agree on what counts as valid -
 * duplicating fourteen fields of rules in two places is how they drift apart.
 */

export const EMPTY_PROFILE = {
  fullNameAsPerAadhaar: '',
  personalEmail: '',
  contactNumber: '',
  alternateContactNumber: '',
  dateOfBirth: '',
  gender: '',
  fathersName: '',
  permanentAddress: '',
  bloodGroup: '',
  aadhaarNumber: '',
  panNumber: '',
  emergencyContactName: '',
  emergencyContactRelation: '',
  emergencyContactNumber: '',
  /* Answers to admin-created fields, keyed by field code. */
  customFields: {},
}

/** The prefix `ProfileFormFields` uses for an admin-created field. */
const CUSTOM_PREFIX = 'customFields.'

/**
 * Applies one change to the form, whether it is a built-in field or an
 * admin-created one. Both consumers route through here so a custom answer is
 * nested under `customFields` rather than becoming a literal dotted key.
 */
export function applyProfileChange(form, key, value) {
  if (!key.startsWith(CUSTOM_PREFIX)) {
    return { ...form, [key]: value }
  }
  return {
    ...form,
    customFields: { ...(form.customFields || {}), [key.slice(CUSTOM_PREFIX.length)]: value },
  }
}

const PHONE_PATTERN = /^\+?[0-9][0-9\s-]{7,19}$/
const AADHAAR_PATTERN = /^\d{4}\s?\d{4}\s?\d{4}$/
const PAN_PATTERN = /^[A-Za-z]{5}[0-9]{4}[A-Za-z]$/
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/* Only digits matter for "is this the same number" - so "+91 98765 43210" and
   "9876543210" are recognised as a clash, not just an exact string match. */
const digitsOnly = (value) => (value || '').replace(/\D/g, '')

/* The server names fields in snake_case; the form uses camelCase keys. */
const toKey = (code) => code.replace(/_([a-z])/g, (_, c) => c.toUpperCase())

/**
 * Turns the server's field configuration into a lookup the form can use.
 *
 * <p>No configuration means everything is shown and required - the safe reading
 * while the settings are still loading, since it can only over-ask, never let
 * an incomplete profile through.
 */
export function fieldRules(candidateFields) {
  if (!candidateFields || candidateFields.length === 0) {
    return { shown: () => true, required: () => true }
  }
  const byKey = new Map(candidateFields.map((field) => [toKey(field.code), field]))
  return {
    shown: (key) => byKey.get(key)?.enabled ?? true,
    required: (key) => {
      const field = byKey.get(key)
      return field ? field.enabled && field.required : true
    },
  }
}

/**
 * @param {object} form the current values
 * @param {boolean} forHr softens the wording for HR, who is correcting someone
 *   else's details rather than entering their own
 * @returns {object} field name to error message; empty when the form is valid
 */
export function validateProfile(form, { forHr = false, candidateFields, customFields } = {}) {
  const rules = fieldRules(candidateFields)
  const you = forHr ? 'the candidate' : 'you'
  const your = forHr ? "the candidate's" : 'your'
  const next = {}

  /* Each field is checked in the same shape: skip it entirely when the admin
     has turned it off, complain about emptiness only when it is required, and
     always check the format of whatever was actually typed. */
  const check = (key, value, { required: msg, invalid } = {}) => {
    if (!rules.shown(key)) return false
    const filled = typeof value === 'string' ? value.trim() : Boolean(value)
    if (!filled) {
      if (rules.required(key) && msg) next[key] = msg
      return false
    }
    if (invalid) next[key] = invalid
    return !invalid
  }

  const text = (key, msg) => check(key, form[key], { required: msg })

  text('fullNameAsPerAadhaar', 'Full name as per Aadhaar is required')

  check('personalEmail', form.personalEmail, {
    required: 'Personal email is required',
    invalid: form.personalEmail.trim() && !EMAIL_PATTERN.test(form.personalEmail.trim())
      ? 'Enter a valid email address' : null,
  })

  check('contactNumber', form.contactNumber, {
    required: 'Contact number is required',
    invalid: form.contactNumber.trim() && !PHONE_PATTERN.test(form.contactNumber.trim())
      ? 'Enter a valid contact number' : null,
  })

  check('alternateContactNumber', form.alternateContactNumber, {
    required: 'Alternate contact number is required',
    invalid: form.alternateContactNumber.trim() && !PHONE_PATTERN.test(form.alternateContactNumber.trim())
      ? 'Enter a valid number' : null,
  })

  check('dateOfBirth', form.dateOfBirth, {
    required: 'Date of birth is required',
    invalid: form.dateOfBirth && new Date(form.dateOfBirth) >= new Date()
      ? 'Date of birth must be in the past' : null,
  })

  check('gender', form.gender, { required: `Select ${forHr ? 'a' : 'your'} gender` })
  text('fathersName', "Father's name is required")

  check('permanentAddress', form.permanentAddress, {
    required: `Give ${your} full permanent address`,
    invalid: form.permanentAddress.trim() && form.permanentAddress.trim().length < 10
      ? `Give ${your} full permanent address` : null,
  })

  check('bloodGroup', form.bloodGroup, { required: `Select ${forHr ? 'a' : 'your'} blood group` })

  check('aadhaarNumber', form.aadhaarNumber, {
    required: 'Aadhaar number is required',
    invalid: form.aadhaarNumber.trim() && !AADHAAR_PATTERN.test(form.aadhaarNumber.trim())
      ? 'Enter a valid 12-digit Aadhaar number' : null,
  })

  check('panNumber', form.panNumber, {
    required: 'PAN number is required',
    invalid: form.panNumber.trim() && !PAN_PATTERN.test(form.panNumber.trim())
      ? 'Enter a valid PAN (e.g. ABCDE1234F)' : null,
  })

  text('emergencyContactName', 'Emergency contact name is required')
  check('emergencyContactRelation', form.emergencyContactRelation, {
    required: `Select the contact's relation to ${you}`,
  })

  const emergencyOk = check('emergencyContactNumber', form.emergencyContactNumber, {
    required: 'Emergency contact number is required',
    invalid: form.emergencyContactNumber.trim() && !PHONE_PATTERN.test(form.emergencyContactNumber.trim())
      ? 'Enter a valid emergency contact number' : null,
  })

  if (emergencyOk) {
    // Cannot be the candidate's own contact or alternate number - the whole
    // point is reaching someone else if the candidate cannot be reached.
    const emergency = digitsOnly(form.emergencyContactNumber)
    if (emergency && emergency === digitsOnly(form.contactNumber)) {
      next.emergencyContactNumber = `This is the same as ${your} contact number - give a different number`
    } else if (emergency && emergency === digitsOnly(form.alternateContactNumber)) {
      next.emergencyContactNumber =
        `This is the same as ${your} alternate contact number - give a different number`
    }
  }

  Object.assign(next, validateCustomFields(form, customFields))

  return next
}

/** Normalises the form into the shape the API expects. */
export function toProfilePayload(form) {
  return {
    ...form,
    alternateContactNumber: form.alternateContactNumber.trim(),
    panNumber: form.panNumber.trim().toUpperCase(),
    customFields: form.customFields || {},
  }
}

/**
 * Admin-created fields as label/value rows, for the places that display a
 * profile rather than edit it - HR's detail panel and the candidate's own
 * review and overview pages.
 *
 * <p>Driven by the definitions rather than the stored answers, so rows appear
 * in form order under their current label, and an answer to a field that has
 * since been removed stays hidden rather than surfacing as a bare code.
 */
export function customFieldRows(customFields = [], values = {}) {
  return (customFields || [])
    .filter((field) => !field.archived)
    .map((field) => ({
      key: field.code,
      label: field.label,
      value: values?.[field.code],
      wide: field.type === 'textarea',
    }))
}

/**
 * Client-side checks for admin-created fields, mirroring the server so the
 * candidate is told about an empty required field before the round trip. The
 * server re-checks everything - this only saves a wasted submission.
 */
export function validateCustomFields(form, customFields = []) {
  const values = form.customFields || {}
  const next = {}
  for (const field of customFields) {
    if (!field.enabled || field.archived) continue
    const raw = values[field.code]
    const value = typeof raw === 'string' ? raw.trim() : ''
    const key = `${CUSTOM_PREFIX}${field.code}`

    if (!value) {
      if (field.required) next[key] = `${field.label} is required`
      continue
    }
    if (field.type === 'email' && !EMAIL_PATTERN.test(value)) {
      next[key] = 'Enter a valid email address'
    } else if (field.type === 'phone' && !PHONE_PATTERN.test(value)) {
      next[key] = 'Enter a valid phone number'
    } else if (field.type === 'number' && !/^-?\d+(\.\d+)?$/.test(value)) {
      next[key] = 'Enter a number'
    } else if (field.type === 'select' && !(field.options || []).includes(value)) {
      next[key] = 'Choose one of the listed options'
    }
  }
  return next
}
