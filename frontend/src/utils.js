import { useEffect, useState } from 'react'

// All money is handled as integer cents. Formatting is the only place a division happens.
export function yuan(cents) {
  const value = Number(cents || 0)
  const sign = value < 0 ? '-' : ''
  const abs = Math.abs(value)
  return `${sign}¥${Math.floor(abs / 100)}.${String(abs % 100).padStart(2, '0')}`
}

// String-based parse: never multiply a float, so ¥0.07 + ¥0.03 style drift is impossible.
export function yuanToCents(input) {
  const text = String(input ?? '').trim().replace(/^¥/, '')
  if (!/^\d+(\.\d{1,2})?$/.test(text)) return 0
  const [whole, fraction = ''] = text.split('.')
  return Number(whole) * 100 + Number(fraction.padEnd(2, '0'))
}

export function centsToInput(cents) {
  const value = Number(cents || 0)
  return `${Math.floor(value / 100)}.${String(value % 100).padStart(2, '0')}`
}

export function todayISO(offsetDays = 0) {
  const d = new Date()
  d.setDate(d.getDate() + offsetDays)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

export function useDebounced(value, delay = 300) {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay)
    return () => clearTimeout(timer)
  }, [value, delay])
  return debounced
}

export function friendlyError(error, fallback = 'Something went wrong. Please try again.') {
  if (!error) return fallback
  if (error.code === 'OFFLINE') return error.message
  if (error.code === 'STOCK_INSUFFICIENT') return error.message
  if (error.code === 'DUPLICATE_ORDER') return error.message
  if (error.code === 'RULE_VIOLATION') return error.message
  return error.message || fallback
}

export function lineKey(dishId, optionIds) {
  return `${dishId}:${[...(optionIds || [])].sort((a, b) => a - b).join(',')}`
}

export const MAX_PORTIONS = 5
