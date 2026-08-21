/**
 * A candidate's avatar colour.
 *
 * Derived from the name, so the same person is the same colour in every list,
 * every session - which is what lets a face be recognised before the name is
 * read. Five gradients, all inside the CloudFuze blue-to-teal range.
 */
export function hueOf(name = '') {
  let sum = 0
  for (let i = 0; i < name.length; i += 1) sum += name.charCodeAt(i)
  return sum % 5
}
