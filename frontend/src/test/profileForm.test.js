import { describe, expect, it } from 'vitest'
import {
  applyProfileChange,
  customFieldRows,
  EMPTY_PROFILE,
  validateProfile,
} from '../utils/profileForm'

/* A complete, valid answer to every built-in field, so the only thing any test
   below is varying is the admin-created part. */
const COMPLETE = {
  ...EMPTY_PROFILE,
  fullNameAsPerAadhaar: 'Priya Sharma',
  personalEmail: 'priya@example.com',
  contactNumber: '9876543210',
  alternateContactNumber: '9876500000',
  dateOfBirth: '2001-04-17',
  gender: 'female',
  fathersName: 'Rakesh Sharma',
  permanentAddress: '12-4-56 Banjara Hills, Hyderabad 500034',
  bloodGroup: 'o_positive',
  aadhaarNumber: '2345 6789 0123',
  panNumber: 'ABCDE1234F',
  emergencyContactName: 'Sunita Sharma',
  emergencyContactRelation: 'mother',
  emergencyContactNumber: '9123456780',
}

const field = (over = {}) => ({
  code: 't_shirt_size',
  label: 'T-shirt size',
  type: 'select',
  options: ['Small', 'Large'],
  group: 'additional',
  enabled: true,
  required: true,
  archived: false,
  ...over,
})

describe('applyProfileChange', () => {
  it('sets a built-in field directly', () => {
    expect(applyProfileChange(EMPTY_PROFILE, 'fathersName', 'Rakesh').fathersName).toBe('Rakesh')
  })

  it('nests a custom answer under its code rather than a dotted key', () => {
    const next = applyProfileChange(EMPTY_PROFILE, 'customFields.t_shirt_size', 'Large')
    expect(next.customFields).toEqual({ t_shirt_size: 'Large' })
    expect(next['customFields.t_shirt_size']).toBeUndefined()
  })

  it('keeps answers to other custom fields when one changes', () => {
    const start = { ...EMPTY_PROFILE, customFields: { shift: 'Night' } }
    const next = applyProfileChange(start, 'customFields.t_shirt_size', 'Small')
    expect(next.customFields).toEqual({ shift: 'Night', t_shirt_size: 'Small' })
  })
})

describe('validateProfile with admin-created fields', () => {
  it('accepts a complete profile when no custom fields exist', () => {
    expect(validateProfile(COMPLETE)).toEqual({})
  })

  it('reports a required custom field left empty, keyed the way the form names it', () => {
    const errors = validateProfile(COMPLETE, { customFields: [field()] })
    expect(errors['customFields.t_shirt_size']).toBe('T-shirt size is required')
  })

  it('ignores an empty optional field', () => {
    expect(validateProfile(COMPLETE, { customFields: [field({ required: false })] })).toEqual({})
  })

  it('does not ask for a field that is switched off or removed', () => {
    expect(validateProfile(COMPLETE, { customFields: [field({ enabled: false })] })).toEqual({})
    expect(validateProfile(COMPLETE, { customFields: [field({ archived: true })] })).toEqual({})
  })

  it('refuses an answer that is not one of the listed choices', () => {
    const form = { ...COMPLETE, customFields: { t_shirt_size: 'Enormous' } }
    expect(validateProfile(form, { customFields: [field()] })['customFields.t_shirt_size'])
      .toBe('Choose one of the listed options')
  })

  it('accepts a listed choice', () => {
    const form = { ...COMPLETE, customFields: { t_shirt_size: 'Large' } }
    expect(validateProfile(form, { customFields: [field()] })).toEqual({})
  })

  it('checks the format of typed answers', () => {
    const cases = [
      [{ code: 'work_email', type: 'email' }, 'not-an-email', 'Enter a valid email address'],
      [{ code: 'desk_phone', type: 'phone' }, 'abc', 'Enter a valid phone number'],
      [{ code: 'notice_days', type: 'number' }, 'thirty', 'Enter a number'],
    ]
    for (const [over, value, message] of cases) {
      const definition = field({ options: undefined, required: false, ...over })
      const form = { ...COMPLETE, customFields: { [definition.code]: value } }
      expect(validateProfile(form, { customFields: [definition] })[`customFields.${definition.code}`])
        .toBe(message)
    }
  })

  it('reports built-in and custom problems together, in one pass', () => {
    const form = { ...COMPLETE, fathersName: '' }
    const errors = validateProfile(form, { customFields: [field()] })
    expect(Object.keys(errors)).toEqual(['fathersName', 'customFields.t_shirt_size'])
  })
})

describe('customFieldRows', () => {
  it('pairs each live field with its answer, in definition order', () => {
    const rows = customFieldRows(
      [field(), field({ code: 'shift', label: 'Shift', type: 'text' })],
      { shift: 'Night', t_shirt_size: 'Large' },
    )
    expect(rows.map((r) => [r.label, r.value]))
      .toEqual([['T-shirt size', 'Large'], ['Shift', 'Night']])
  })

  it('hides a removed field, so an old answer never shows as a bare code', () => {
    expect(customFieldRows([field({ archived: true })], { t_shirt_size: 'Large' })).toEqual([])
  })

  it('survives having no definitions or no answers', () => {
    expect(customFieldRows()).toEqual([])
    expect(customFieldRows([field()], undefined)[0].value).toBeUndefined()
  })
})
