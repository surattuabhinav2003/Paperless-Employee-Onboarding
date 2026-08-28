import { useEffect, useMemo, useRef, useState } from 'react'

/**
 * A date picker that matches the rest of the app.
 *
 * `<input type="date">` opens the browser's own calendar, which is drawn outside
 * the document - no stylesheet can reach it, so it always looks like Chrome
 * rather than like Neutara. This renders the calendar itself instead.
 *
 * Value and onChange use the same `YYYY-MM-DD` strings the native input did, so
 * callers and the API are unchanged.
 */

const WEEKDAYS = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa']
const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December']

/* Parsed as local dates on purpose: `new Date('1998-04-11')` is treated as UTC
   and can land on the previous day west of Greenwich. */
function parse(iso) {
  if (!iso) return null
  const [y, m, d] = String(iso).split('-').map(Number)
  if (!y || !m || !d) return null
  const date = new Date(y, m - 1, d)
  return Number.isNaN(date.getTime()) ? null : date
}

function toIso(date) {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

function sameDay(a, b) {
  return a && b && a.getFullYear() === b.getFullYear()
    && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
}

function startOfDay(date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate())
}

export function DateField({
  id,
  value,
  onChange,
  disabled = false,
  error = false,
  /* Inclusive bounds, as ISO strings. A date of birth passes today as `max`. */
  min,
  max,
  placeholder = 'Choose a date',
  yearsBack = 80,
}) {
  const selected = parse(value)
  const minDate = parse(min)
  const maxDate = parse(max)
  const today = startOfDay(new Date())

  const [open, setOpen] = useState(false)
  const [view, setView] = useState(selected || maxDate || today)
  const root = useRef(null)

  /* Reopening should land on the chosen date, not wherever it was left. */
  useEffect(() => {
    if (open) setView(selected || maxDate || today)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

  useEffect(() => {
    if (!open) return undefined
    const onDocClick = (event) => {
      if (root.current && !root.current.contains(event.target)) setOpen(false)
    }
    const onKey = (event) => {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onDocClick)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDocClick)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  const years = useMemo(() => {
    const last = (maxDate || today).getFullYear()
    const first = minDate ? minDate.getFullYear() : last - yearsBack
    const list = []
    for (let year = last; year >= first; year -= 1) list.push(year)
    return list
  }, [min, max, yearsBack])

  /* Six weeks, so the grid never changes height between months. */
  const cells = useMemo(() => {
    const firstOfMonth = new Date(view.getFullYear(), view.getMonth(), 1)
    const start = new Date(firstOfMonth)
    start.setDate(1 - firstOfMonth.getDay())
    return Array.from({ length: 42 }, (_, i) => {
      const date = new Date(start)
      date.setDate(start.getDate() + i)
      return date
    })
  }, [view])

  const blocked = (date) => (minDate && date < minDate) || (maxDate && date > maxDate)

  const shift = (months) => {
    setView(new Date(view.getFullYear(), view.getMonth() + months, 1))
  }

  const pick = (date) => {
    onChange?.(toIso(date))
    setOpen(false)
  }

  const label = selected
    ? selected.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })
    : placeholder

  return (
    <div className="dp" ref={root}>
      <button
        type="button"
        id={id}
        className={`dp-trigger${open ? ' is-open' : ''}${selected ? '' : ' is-empty'}${error ? ' is-invalid' : ''}`}
        disabled={disabled}
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={() => setOpen((current) => !current)}
      >
        {label}
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
          strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <rect x="3" y="5" width="18" height="16" rx="2" />
          <path d="M8 3v4M16 3v4M3 11h18" />
        </svg>
      </button>

      {open && !disabled && (
        <div className="dp-panel" role="dialog" aria-label="Choose a date">
          <div className="dp-head">
            <button type="button" className="dp-nav" onClick={() => shift(-1)} aria-label="Previous month">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
                strokeLinecap="round" strokeLinejoin="round"><path d="m15 18-6-6 6-6" /></svg>
            </button>

            <div className="dp-selects">
              <select
                className="dp-select"
                aria-label="Month"
                value={view.getMonth()}
                onChange={(e) => setView(new Date(view.getFullYear(), Number(e.target.value), 1))}
              >
                {MONTHS.map((month, index) => (
                  <option key={month} value={index}>{month}</option>
                ))}
              </select>
              <select
                className="dp-select"
                aria-label="Year"
                value={view.getFullYear()}
                onChange={(e) => setView(new Date(Number(e.target.value), view.getMonth(), 1))}
              >
                {years.map((year) => <option key={year} value={year}>{year}</option>)}
              </select>
            </div>

            <button type="button" className="dp-nav" onClick={() => shift(1)} aria-label="Next month">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
                strokeLinecap="round" strokeLinejoin="round"><path d="m9 18 6-6-6-6" /></svg>
            </button>
          </div>

          <div className="dp-week" aria-hidden="true">
            {WEEKDAYS.map((day) => <span key={day}>{day}</span>)}
          </div>

          <div className="dp-grid">
            {cells.map((date) => {
              const outside = date.getMonth() !== view.getMonth()
              const isSelected = sameDay(date, selected)
              return (
                <button
                  key={date.toISOString()}
                  type="button"
                  className={`dp-day${outside ? ' is-outside' : ''}${
                    sameDay(date, today) ? ' is-today' : ''}${isSelected ? ' is-selected' : ''}`}
                  disabled={blocked(date)}
                  aria-current={isSelected ? 'date' : undefined}
                  onClick={() => pick(date)}
                >
                  {date.getDate()}
                </button>
              )
            })}
          </div>

          <div className="dp-foot">
            <button type="button" onClick={() => { onChange?.(''); setOpen(false) }}>Clear</button>
            <button
              type="button"
              className="is-muted"
              disabled={blocked(today)}
              onClick={() => pick(today)}
            >
              Today
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
