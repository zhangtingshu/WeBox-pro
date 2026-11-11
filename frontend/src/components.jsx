import React, { useEffect, useMemo, useState } from 'react'
import { yuan } from './utils'

const PLACEHOLDER_IMAGE = '/images/placeholder.svg'

/** Dish photo with a real fallback: a missing file shows a branded placeholder, never a blank box. */
export function DishImage({ src, alt, className = '', eager = false }) {
  return (
    <img
      className={className}
      src={src && src.trim() ? src : PLACEHOLDER_IMAGE}
      alt={alt}
      loading={eager ? 'eager' : 'lazy'}
      decoding="async"
      onError={(event) => {
        const image = event.currentTarget
        if (!image.src.endsWith(PLACEHOLDER_IMAGE)) image.src = PLACEHOLDER_IMAGE
      }}
    />
  )
}

export function Spinner({ label = 'Loading...' }) {
  return <div className="loading"><span className="spinner" /> {label}</div>
}

export function EmptyState({ title, description }) {
  return (
    <div className="empty">
      <div className="empty-icon">◻</div>
      <h3>{title}</h3>
      {description && <p>{description}</p>}
    </div>
  )
}

export function ToastStack({ toasts, onDismiss }) {
  return (
    <div className="toast-wrap" role="status" aria-live="polite">
      {toasts.map((toast) => (
        <div key={toast.id} className={`toast ${toast.kind || 'info'}`} onClick={() => onDismiss(toast.id)}>
          <span>{toast.message}</span>
          <button className="toast-close" aria-label="Dismiss">×</button>
        </div>
      ))}
    </div>
  )
}

export function Modal({ title, onClose, children, footer, wide = false }) {
  useEffect(() => {
    const handler = (e) => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [onClose])
  return (
    <div className="modal-backdrop" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose() }}>
      <div className={`modal ${wide ? 'modal-wide' : ''}`} role="dialog" aria-modal="true">
        <div className="modal-head">
          <h2>{title}</h2>
          <button className="icon-btn" onClick={onClose} aria-label="Close">×</button>
        </div>
        <div className="modal-body">{children}</div>
        {footer && <div className="modal-foot">{footer}</div>}
      </div>
    </div>
  )
}

export function DishCard({ item, onOpen, onAdd, canAdd = true, disabledReason = '' }) {
  const dish = item.dish
  return (
    <article className={`dish-card ${item.recommended ? 'is-recommended' : ''} ${item.soldOut ? 'is-soldout' : ''}`}>
      <button className="dish-media" onClick={() => onOpen(item)} aria-label={`View ${dish.name} details`}>
        <DishImage src={dish.imageUrl} alt={dish.name} />
        {item.soldOut && <span className="sold-out-veil">Sold out</span>}
        {item.recommended && <span className="recommend-flag">★ For you</span>}
      </button>
      <div className="dish-body">
        <div className="dish-tags">
          <span className="tag">{dish.category}</span>
          <span className={`tag subtle ${dish.spiceLevel !== 'None' ? 'spice' : ''}`}>{dish.spiceLevel}</span>
        </div>
        <h3 className="dish-name" onClick={() => onOpen(item)}>{dish.name}</h3>
        <p className="dish-desc">{dish.description}</p>
        <div className="dish-meta">
          <span className="dish-price">{yuan(dish.priceCents)}</span>
          {item.soldOut
            ? <span className="stock-badge out">Sold out</span>
            : item.lowStock
              ? <span className="stock-badge low">Only {item.remaining} left</span>
              : <span className="stock-badge">{item.remaining} left</span>}
        </div>
        <div className="dish-actions">
          <button className="btn btn-ghost btn-sm" onClick={() => onOpen(item)}>Details</button>
          <button className="btn btn-primary btn-sm" disabled={!canAdd}
            title={!canAdd ? disabledReason : 'Add to cart'}
            onClick={() => (dish.optionGroups || []).some((group) => group.required)
              ? onOpen(item)
              : onAdd(dish, [], [], dish.priceCents)}>
            {(dish.optionGroups || []).some((group) => group.required) ? 'Choose options' : '+ Add'}
          </button>
        </div>
      </div>
    </article>
  )
}

export function DishDetail({ dish, preferences, onClose, onConfirm }) {
  const groups = dish.optionGroups || []
  const [selected, setSelected] = useState({})
  const [allergenStep, setAllergenStep] = useState(false)

  useEffect(() => { setSelected({}); setAllergenStep(false) }, [dish.id])

  const conflicts = (dish.allergens || [])
    .filter((allergen) => (preferences?.allergens || []).includes(allergen))

  const optionIds = useMemo(() => Object.values(selected).flat(), [selected])
  const labels = useMemo(() => groups.flatMap((group) =>
    (group.items || [])
      .filter((item) => optionIds.includes(item.id))
      .map((item) => `${group.name}: ${item.name}`)), [groups, optionIds])
  const extraCents = useMemo(() => groups.flatMap((group) => group.items || [])
    .filter((item) => optionIds.includes(item.id))
    .reduce((sum, item) => sum + item.extraCents, 0), [groups, optionIds])
  const unitCents = dish.priceCents + extraCents
  const missingGroups = groups.filter((group) => group.required && !(selected[group.id] || []).length)

  function toggle(group, item) {
    setSelected((previous) => {
      const current = previous[group.id] || []
      if (group.maxSelect <= 1) {
        return { ...previous, [group.id]: current.includes(item.id) ? [] : [item.id] }
      }
      if (current.includes(item.id)) {
        return { ...previous, [group.id]: current.filter((id) => id !== item.id) }
      }
      if (current.length >= group.maxSelect) return previous
      return { ...previous, [group.id]: [...current, item.id] }
    })
  }

  function handleAdd() {
    if (conflicts.length && !allergenStep) { setAllergenStep(true); return }
    onConfirm({ optionIds, labels, unitCents })
  }

  return (
    <Modal title={dish.name} onClose={onClose} wide
      footer={
        <div className="detail-footer">
          <div>
            <span className="muted">Total per portion</span>
            <div className="detail-price">{yuan(unitCents)}</div>
          </div>
          <button className="btn btn-primary" onClick={handleAdd}>Add to cart</button>
        </div>
      }>
      <div className="detail-grid">
        <DishImage className="detail-image" src={dish.imageUrl} alt={dish.name} eager />
        <div>
          <p className="detail-description">{dish.description}</p>
          <dl className="detail-facts">
            <div><dt>Category</dt><dd>{dish.category}</dd></div>
            <div><dt>Protein</dt><dd>{dish.protein || 'None'}</dd></div>
            <div><dt>Spice level</dt><dd>{dish.spiceLevel}</dd></div>
            <div><dt>Allergens</dt><dd>{(dish.allergens || []).length ? dish.allergens.join(', ') : 'None'}</dd></div>
          </dl>
          {conflicts.length > 0 && (
            <div className="inline-warning">
              Heads up: this dish contains {conflicts.join(', ')}, which you flagged as an allergen.
            </div>
          )}
        </div>
      </div>

      {groups.map((group) => (
        <div className="option-group" key={group.id}>
          <div className="option-group-head">
            <h4>{group.name}</h4>
            <span className="muted">
              {group.required ? 'Required · choose 1' : `Optional · up to ${group.maxSelect}`}
            </span>
          </div>
          <div className="option-list">
            {(group.items || []).map((item) => {
              const checked = (selected[group.id] || []).includes(item.id)
              return (
                <label className={`option-item ${checked ? 'checked' : ''}`} key={item.id}>
                  <input type={group.maxSelect <= 1 ? 'radio' : 'checkbox'}
                    name={`group-${group.id}`} checked={checked}
                    onChange={() => toggle(group, item)} />
                  <span>{item.name}</span>
                  {item.extraCents > 0 && <span className="option-extra">+{yuan(item.extraCents)}</span>}
                </label>
              )
            })}
          </div>
        </div>
      ))}

      {missingGroups.length > 0 && (
        <p className="form-hint">Please choose: {missingGroups.map((g) => g.name).join(', ')}.</p>
      )}

      {allergenStep && (
        <div className="allergen-confirm">
          <strong>This dish contains an allergen you flagged: [{conflicts.join(', ')}]. Add anyway?</strong>
          <div className="row gap">
            <button className="btn btn-ghost btn-sm" onClick={() => setAllergenStep(false)}>Cancel</button>
            <button className="btn btn-danger btn-sm" onClick={() => onConfirm({ optionIds, labels, unitCents })}>
              Add anyway
            </button>
          </div>
        </div>
      )}
    </Modal>
  )
}
