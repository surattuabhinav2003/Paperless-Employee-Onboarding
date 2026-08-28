import { DateField } from '../ui/DateField'
import { Field, Select, TextArea, TextInput } from '../ui/Field'
import { fieldRules } from '../../utils/profileForm'

/**
 * The candidate's personal-details fields, in the three groups they belong to.
 * Rendered identically whether the candidate is filling them in themselves or
 * HR is correcting them afterwards, so the two views can never disagree about
 * what is asked for.
 *
 * @param sectioned wraps each group in its own card (the candidate's full-page
 *   form); otherwise the groups are plain headed blocks, which suits a dialog
 */
export function ProfileFormFields({
  form, errors, meta, disabled = false, setValue, sectioned = false, candidateFields,
  customFields = [],
}) {
  const set = (field) => (event) => setValue(field, event.target.value)
  /* An admin can switch a field off or make it optional, so neither whether it
     appears nor whether it shows a required marker is decided here. */
  const rules = fieldRules(candidateFields)
  const Group = sectioned ? CardGroup : PlainGroup

  /* Fields an admin invented sit in whichever section they chose, after the
     built-in ones, so the form still reads top to bottom the way it did. */
  const custom = customFields.filter((field) => field.enabled && !field.archived)
  const customIn = (group) => custom.filter((field) => field.group === group)
  const setCustom = (code, value) => setValue(`customFields.${code}`, value)
  const extras = (group) => (
    <CustomFields fields={customIn(group)} form={form} errors={errors} disabled={disabled}
      setValue={setCustom} />
  )
  const additional = customIn('additional')

  return (
    <>
      <Group title="Personal information">
        <div className="grid gap-4 sm:grid-cols-2">
          {rules.shown('fullNameAsPerAadhaar') && <Field label="Full name (as per Aadhaar)" htmlFor="fullNameAsPerAadhaar" required={rules.required('fullNameAsPerAadhaar')}
            error={errors.fullNameAsPerAadhaar} className="sm:col-span-2">
            <TextInput id="fullNameAsPerAadhaar" value={form.fullNameAsPerAadhaar} disabled={disabled}
              error={errors.fullNameAsPerAadhaar} onChange={set('fullNameAsPerAadhaar')}
              autoComplete="name" />
          </Field>}

          {rules.shown('personalEmail') && <Field label="Personal email ID" htmlFor="personalEmail" required={rules.required('personalEmail')} error={errors.personalEmail}>
            <TextInput id="personalEmail" type="email" value={form.personalEmail} disabled={disabled}
              error={errors.personalEmail} onChange={set('personalEmail')} autoComplete="email" />
          </Field>}
          {rules.shown('contactNumber') && <Field label="Contact number" htmlFor="contactNumber" required={rules.required('contactNumber')} error={errors.contactNumber}>
            <TextInput id="contactNumber" type="tel" value={form.contactNumber} disabled={disabled}
              error={errors.contactNumber} onChange={set('contactNumber')} placeholder="9876543210"
              autoComplete="tel" />
          </Field>}
          {rules.shown('alternateContactNumber') && <Field label="Alternate contact number" htmlFor="alternateContactNumber" required={rules.required('alternateContactNumber')}
            error={errors.alternateContactNumber} hint="A second number we can reach them on.">
            <TextInput id="alternateContactNumber" type="tel" value={form.alternateContactNumber}
              disabled={disabled} error={errors.alternateContactNumber}
              onChange={set('alternateContactNumber')} />
          </Field>}
          {rules.shown('dateOfBirth') && <Field label="Date of birth" htmlFor="dateOfBirth" required={rules.required('dateOfBirth')} error={errors.dateOfBirth}>
            {/* Our own calendar: the native one is browser chrome and cannot
                be styled to match the app. */}
            <DateField
              id="dateOfBirth"
              value={form.dateOfBirth}
              disabled={disabled}
              error={Boolean(errors.dateOfBirth)}
              onChange={(iso) => setValue('dateOfBirth', iso)}
              max="2015-12-31"
              yearsBack={70}
              placeholder="Choose a date of birth"
            />
          </Field>}
          {rules.shown('gender') && <Field label="Gender" htmlFor="gender" required={rules.required('gender')} error={errors.gender}>
            <Select id="gender" value={form.gender} disabled={disabled} error={errors.gender}
              onChange={set('gender')}>
              <option value="">Select</option>
              {(meta?.genders || []).map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </Select>
          </Field>}
          {rules.shown('bloodGroup') && <Field label="Blood group" htmlFor="bloodGroup" required={rules.required('bloodGroup')} error={errors.bloodGroup}>
            <Select id="bloodGroup" value={form.bloodGroup} disabled={disabled} error={errors.bloodGroup}
              onChange={set('bloodGroup')}>
              <option value="">Select</option>
              {(meta?.bloodGroups || []).map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </Select>
          </Field>}
          {rules.shown('fathersName') && <Field label="Father's name" htmlFor="fathersName" required={rules.required('fathersName')} error={errors.fathersName}>
            <TextInput id="fathersName" value={form.fathersName} disabled={disabled}
              error={errors.fathersName} onChange={set('fathersName')} />
          </Field>}
          {rules.shown('permanentAddress') && <Field label="Permanent address" htmlFor="permanentAddress" required={rules.required('permanentAddress')}
            error={errors.permanentAddress} className="sm:col-span-2"
            hint="House number, street, city, state and PIN code.">
            <TextArea id="permanentAddress" rows={3} value={form.permanentAddress} disabled={disabled}
              error={errors.permanentAddress} onChange={set('permanentAddress')} />
          </Field>}
          {extras('personal')}
        </div>
      </Group>

      <Group title="Identity numbers" subtitle="Needed for the employee and payroll records.">
        <div className="grid gap-4 sm:grid-cols-2">
          {rules.shown('aadhaarNumber') && <Field label="Aadhaar number" htmlFor="aadhaarNumber" required={rules.required('aadhaarNumber')} error={errors.aadhaarNumber}
            hint="12 digits, as printed on the Aadhaar card.">
            <TextInput id="aadhaarNumber" inputMode="numeric" value={form.aadhaarNumber} disabled={disabled}
              error={errors.aadhaarNumber} onChange={set('aadhaarNumber')} placeholder="1234 5678 9012"
              maxLength={14} />
          </Field>}
          {rules.shown('panNumber') && <Field label="PAN number" htmlFor="panNumber" required={rules.required('panNumber')} error={errors.panNumber}>
            <TextInput id="panNumber" value={form.panNumber} disabled={disabled} error={errors.panNumber}
              onChange={(event) => setValue('panNumber', event.target.value.toUpperCase())}
              placeholder="ABCDE1234F" maxLength={10} className="uppercase" />
          </Field>}
          {extras('identity')}
        </div>
      </Group>

      <Group title="Emergency contact"
        subtitle="Someone to reach if the candidate cannot be reached - not their own number.">
        <div className="grid gap-4 sm:grid-cols-2">
          {rules.shown('emergencyContactName') && <Field label="Contact name" htmlFor="emergencyContactName" required={rules.required('emergencyContactName')}
            error={errors.emergencyContactName}>
            <TextInput id="emergencyContactName" value={form.emergencyContactName} disabled={disabled}
              error={errors.emergencyContactName} onChange={set('emergencyContactName')} />
          </Field>}
          {rules.shown('emergencyContactRelation') && <Field label="Relation" htmlFor="emergencyContactRelation" required={rules.required('emergencyContactRelation')}
            error={errors.emergencyContactRelation}>
            <Select id="emergencyContactRelation" value={form.emergencyContactRelation} disabled={disabled}
              error={errors.emergencyContactRelation} onChange={set('emergencyContactRelation')}>
              <option value="">Select</option>
              {(meta?.emergencyRelations || []).map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </Select>
          </Field>}
          {rules.shown('emergencyContactNumber') && <Field label="Contact number" htmlFor="emergencyContactNumber" required={rules.required('emergencyContactNumber')}
            error={errors.emergencyContactNumber} className="sm:col-span-2"
            hint="Must be different from the contact and alternate numbers above.">
            <TextInput id="emergencyContactNumber" type="tel" value={form.emergencyContactNumber}
              disabled={disabled} error={errors.emergencyContactNumber}
              onChange={set('emergencyContactNumber')} />
          </Field>}
          {extras('emergency')}
        </div>
      </Group>

      {/* Only appears once an admin has put something in it. */}
      {additional.length > 0 && (
        <Group title="Additional details">
          <div className="grid gap-4 sm:grid-cols-2">
            <CustomFields fields={additional} form={form} errors={errors} disabled={disabled}
              setValue={setCustom} />
          </div>
        </Group>
      )}
    </>
  )
}

/**
 * Renders the admin-created fields for one section.
 *
 * <p>Each type maps to the input a candidate would expect for it, and the whole
 * set is keyed by field code - the code never changes once the field is made,
 * so an answer stays attached to its question even after a rename.
 */
function CustomFields({ fields, form, errors, disabled, setValue }) {
  return fields.map((field) => {
    const id = `custom-${field.code}`
    const value = form.customFields?.[field.code] ?? ''
    const error = errors[`customFields.${field.code}`]
    const wide = field.type === 'textarea'
    const common = {
      id,
      value,
      disabled,
      error,
      onChange: (event) => setValue(field.code, event.target.value),
    }
    return (
      <Field
        key={field.code}
        label={field.label}
        htmlFor={id}
        required={field.required}
        error={error}
        hint={field.helpText}
        className={wide ? 'sm:col-span-2' : undefined}
      >
        {field.type === 'textarea' ? (
          <TextArea {...common} rows={3} />
        ) : field.type === 'select' ? (
          <Select {...common}>
            <option value="">Select</option>
            {(field.options || []).map((option) => (
              <option key={option} value={option}>{option}</option>
            ))}
          </Select>
        ) : field.type === 'date' ? (
          <DateField
            id={id}
            value={value}
            disabled={disabled}
            error={Boolean(error)}
            onChange={(iso) => setValue(field.code, iso)}
            placeholder={`Choose ${field.label.toLowerCase()}`}
          />
        ) : (
          <TextInput
            {...common}
            type={field.type === 'number' ? 'number' : field.type === 'email' ? 'email'
              : field.type === 'phone' ? 'tel' : 'text'}
          />
        )}
      </Field>
    )
  })
}

function CardGroup({ title, subtitle, children }) {
  return (
    <section className="cf-card p-5 sm:p-6">
      <h3 className="text-[14.5px] font-semibold text-ink">{title}</h3>
      {subtitle && <p className="mt-1 text-[12.5px] text-ink-muted">{subtitle}</p>}
      <div className="mt-4">{children}</div>
    </section>
  )
}

function PlainGroup({ title, subtitle, children }) {
  return (
    <section className="border-t border-surface-line pt-4 first:border-0 first:pt-0">
      <h3 className="text-[13.5px] font-semibold text-ink">{title}</h3>
      {subtitle && <p className="mt-0.5 text-[12px] text-ink-muted">{subtitle}</p>}
      <div className="mt-3">{children}</div>
    </section>
  )
}
