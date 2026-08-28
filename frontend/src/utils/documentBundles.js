/*
 * The candidate uploads six separate payslips (one per month), but HR should
 * not have to tick six near-identical boxes to ask for them - one card stands
 * for all six underlying document types wherever a document picker groups
 * options by their backend category.
 */
export const PAYSLIP_CODES = [
  'payslip_month_1', 'payslip_month_2', 'payslip_month_3',
  'payslip_month_4', 'payslip_month_5', 'payslip_month_6',
]
export const PAYSLIP_BUNDLE_VALUE = 'payslip_bundle'

/** Groups document type options by category, collapsing the payslip months into one card. */
export function groupWithPayslipBundle(documentTypes) {
  const groups = new Map()
  documentTypes.forEach((option) => {
    const key = option.group || 'Other'
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key).push(option)
  })
  for (const groupOptions of groups.values()) {
    const firstIndex = groupOptions.findIndex((o) => o.value === PAYSLIP_CODES[0])
    if (firstIndex === -1) continue
    const allPresent = PAYSLIP_CODES.every((code) => groupOptions.some((o) => o.value === code))
    if (!allPresent) continue
    groupOptions.splice(firstIndex, PAYSLIP_CODES.length, {
      value: PAYSLIP_BUNDLE_VALUE,
      label: 'Payslips (Last 6 Months)',
      hint: 'Candidate uploads 6 separate payslips, one per month.',
      bundle: PAYSLIP_CODES,
    })
  }
  return [...groups.entries()]
}
