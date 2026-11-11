import React, { useCallback, useEffect, useState } from 'react'
import { consoleApi } from './api'
import {
  yuan, yuanToCents, centsToInput, todayISO, useDebounced, friendlyError,
} from './utils'
import { DishImage, Modal, Spinner, EmptyState } from './components'

const CATEGORIES = ['Chinese', 'Western', 'Japanese', 'Light Meal', 'Korean', 'Southeast Asian']
const ALLERGENS = ['Peanuts', 'Dairy', 'Egg', 'Gluten', 'Soy', 'Fish', 'Shellfish']
const SPICE = ['None', 'Mild', 'Medium', 'Hot']

export default function Console({ section, setSection, onExit, toast, meta }) {
  const tabs = [
    { key: 'dishes', label: 'Dishes', icon: '▦' },
    { key: 'daily-menu', label: 'Daily Menu', icon: '▤' },
    { key: 'dashboard', label: 'Dashboard', icon: '◫' },
  ]
  return (
    <div className="console-shell">
      <aside className="console-sidebar">
        <div className="console-brand"><span className="brand-mark">W</span>
          <div><strong>WeBox Console</strong><small>Admin workspace</small></div>
        </div>
        <nav className="console-nav">
          {tabs.map((tab) => (
            <button key={tab.key} className={section === tab.key ? 'active' : ''}
              onClick={() => setSection(tab.key)}>
              <span>{tab.icon}</span>{tab.label}
            </button>
          ))}
        </nav>
        <div className="console-sidebar-foot">
          <button className="btn btn-ghost btn-block" onClick={onExit}>← Employee site</button>
          <p className="muted">Console is available to administrators only.</p>
        </div>
      </aside>
      <div className="console-content">
        <header className="console-head">
          <div><h1>{tabs.find((t) => t.key === section)?.label}</h1>
            <p className="muted">Manage the catalogue, publish daily menus and review operations.</p></div>
          <span className="role-chip">Admin</span>
        </header>
        {section === 'dishes' && <DishesSection toast={toast} />}
        {section === 'daily-menu' && <DailyMenuSection toast={toast} />}
        {section === 'dashboard' && <DashboardSection toast={toast} />}
      </div>
    </div>
  )
}

// ------------------------------------------------------------ dishes
function DishesSection({ toast }) {
  const [keyword, setKeyword] = useState('')
  const debouncedKeyword = useDebounced(keyword, 300)
  const [category, setCategory] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState({ items: [], totalPages: 0, totalElements: 0, page: 0 })
  const [loading, setLoading] = useState(true)
  const [editing, setEditing] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setData(await consoleApi.dishes({ keyword: debouncedKeyword, category, page, size: 10 }))
    } catch (e) { toast(friendlyError(e), 'error') }
    finally { setLoading(false) }
  }, [debouncedKeyword, category, page, toast])
  useEffect(() => { load() }, [load])

  async function toggleActive(dish) {
    try {
      await consoleApi.setActive(dish.id, !dish.active)
      toast(`${dish.name} is now ${dish.active ? 'off the menu' : 'available'}.`, 'success')
      load()
    } catch (e) { toast(friendlyError(e), 'error') }
  }

  return (
    <>
      <div className="toolbar">
        <input type="search" maxLength={50} placeholder="Search dishes..." value={keyword}
          onChange={(e) => { setKeyword(e.target.value); setPage(0) }} />
        <select value={category} onChange={(e) => { setCategory(e.target.value); setPage(0) }}>
          <option value="">All categories</option>
          {CATEGORIES.map((c) => <option key={c}>{c}</option>)}
        </select>
        <button className="btn btn-primary" onClick={() => setEditing({ mode: 'create' })}>+ New dish</button>
      </div>

      {loading ? <Spinner label="Loading dishes..." /> : (
        <div className="panel table-panel">
          <table className="data-table">
            <thead><tr>
              <th>Image</th><th>Name</th><th>Category</th><th>Price</th>
              <th>Allergens</th><th>Spice</th><th>Status</th><th>Actions</th>
            </tr></thead>
            <tbody>
              {data.items.map((dish) => (
                <tr key={dish.id}>
                  <td><DishImage className="table-thumb" src={dish.imageUrl} alt="" /></td>
                  <td><strong>{dish.name}</strong><div className="cell-sub">{dish.description?.slice(0, 48)}</div></td>
                  <td>{dish.category}</td>
                  <td>{yuan(dish.priceCents)}</td>
                  <td>{dish.allergens?.join(', ') || 'None'}</td>
                  <td>{dish.spiceLevel}</td>
                  <td><span className={`status ${dish.active ? 'confirmed' : 'cancelled'}`}>
                    {dish.active ? 'Available' : 'Hidden'}</span></td>
                  <td className="row gap">
                    <button className="btn btn-ghost btn-sm" onClick={() => setEditing({ mode: 'edit', dish })}>Edit</button>
                    <button className="btn btn-ghost btn-sm" onClick={() => toggleActive(dish)}>
                      {dish.active ? 'Take offline' : 'Publish'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {data.items.length === 0 && <EmptyState title="No dishes match" description="Adjust the search or category filter." />}
          <div className="pager">
            <button className="btn btn-ghost btn-sm" disabled={page <= 0} onClick={() => setPage(page - 1)}>← Previous</button>
            <span>Page {data.page + 1} of {Math.max(1, data.totalPages)} · {data.totalElements} dishes</span>
            <button className="btn btn-ghost btn-sm" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Next →</button>
          </div>
        </div>
      )}
      {editing && <DishEditor initial={editing.dish} toast={toast}
        onClose={() => setEditing(null)} onSaved={() => { setEditing(null); load() }} />}
    </>
  )
}

function DishEditor({ initial, toast, onClose, onSaved }) {
  const [form, setForm] = useState({
    name: initial?.name || '',
    description: initial?.description || '',
    price: initial ? centsToInput(initial.priceCents) : '0.00',
    category: initial?.category || 'Chinese',
    protein: initial?.protein || '',
    allergens: initial?.allergens || [],
    spiceLevel: initial?.spiceLevel || 'None',
    imageUrl: initial?.imageUrl || '/images/image_01.jpg',
    active: initial?.active ?? true,
  })
  const [groups, setGroups] = useState(() => (initial?.optionGroups || []).map((group) => ({
    name: group.name, required: group.required, maxSelect: group.maxSelect,
    items: (group.items || []).map((item) => ({ name: item.name, extra: centsToInput(item.extraCents) })),
  })))
  const [saving, setSaving] = useState(false)
  const [uploading, setUploading] = useState(false)

  function toggleAllergen(value) {
    setForm((f) => ({
      ...f,
      allergens: f.allergens.includes(value)
        ? f.allergens.filter((a) => a !== value) : [...f.allergens, value],
    }))
  }

  async function upload(event) {
    const file = event.target.files?.[0]
    if (!file) return
    setUploading(true)
    const body = new FormData()
    body.append('file', file)
    try {
      const result = await consoleApi.upload(body)
      setForm((f) => ({ ...f, imageUrl: result.url }))
      toast('Image uploaded.', 'success')
    } catch (e) { toast(friendlyError(e), 'error') }
    finally { setUploading(false) }
  }

  function updateGroup(index, patch) {
    setGroups((prev) => prev.map((g, i) => i === index ? { ...g, ...patch } : g))
  }

  async function save() {
    if (!form.name.trim()) { toast('Dish name is required.', 'warn'); return }
    setSaving(true)
    const payload = {
      name: form.name.trim(),
      description: form.description.trim(),
      priceCents: yuanToCents(form.price),
      category: form.category,
      protein: form.protein.trim(),
      allergens: form.allergens,
      spiceLevel: form.spiceLevel,
      imageUrl: form.imageUrl,
      active: form.active,
      optionGroups: groups.map((group) => ({
        name: group.name || 'Options',
        required: group.required,
        maxSelect: Math.max(1, Number(group.maxSelect) || 1),
        items: group.items.map((item) => ({
          name: item.name || 'Option', extraCents: yuanToCents(item.extra),
        })),
      })),
    }
    try {
      if (initial) await consoleApi.updateDish(initial.id, payload)
      else await consoleApi.createDish(payload)
      toast(initial ? 'Dish updated.' : 'Dish created.', 'success')
      onSaved()
    } catch (e) { toast(friendlyError(e), 'error') }
    finally { setSaving(false) }
  }

  return (
    <Modal title={initial ? `Edit ${initial.name}` : 'New dish'} onClose={onClose} wide
      footer={<>
        <button className="btn btn-ghost" onClick={onClose}>Cancel</button>
        <button className="btn btn-primary" disabled={saving} onClick={save}>
          {saving ? 'Saving...' : 'Save dish'}
        </button>
      </>}>
      <div className="two-col">
        <label className="field"><span>Name *</span>
          <input maxLength={120} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
        </label>
        <label className="field"><span>Price (¥) *</span>
          <input inputMode="decimal" value={form.price} onChange={(e) => setForm({ ...form, price: e.target.value })} />
        </label>
      </div>
      <label className="field"><span>Description</span>
        <textarea maxLength={500} rows={2} value={form.description}
          onChange={(e) => setForm({ ...form, description: e.target.value })} />
      </label>
      <div className="three-col">
        <label className="field"><span>Category</span>
          <select value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })}>
            {CATEGORIES.map((c) => <option key={c}>{c}</option>)}
          </select>
        </label>
        <label className="field"><span>Protein</span>
          <input maxLength={80} value={form.protein}
            onChange={(e) => setForm({ ...form, protein: e.target.value })} placeholder="Chicken" />
        </label>
        <label className="field"><span>Spice level</span>
          <select value={form.spiceLevel} onChange={(e) => setForm({ ...form, spiceLevel: e.target.value })}>
            {SPICE.map((s) => <option key={s}>{s}</option>)}
          </select>
        </label>
      </div>
      <div className="field"><span>Allergens</span>
        <div className="chip-row">
          {ALLERGENS.map((a) => (
            <button key={a} className={`chip ${form.allergens.includes(a) ? 'selected danger' : ''}`}
              onClick={() => toggleAllergen(a)}>{a}</button>
          ))}
        </div>
      </div>
      <div className="two-col">
        <label className="field"><span>Image</span>
          <input type="file" accept="image/jpeg,image/png,image/webp" onChange={upload} disabled={uploading} />
        </label>
        <div className="upload-preview">
          {uploading ? <Spinner label="Uploading..." /> : <DishImage src={form.imageUrl} alt="Preview" eager />}
          <span className="muted">{form.imageUrl}</span>
        </div>
      </div>
      <label className="switch">
        <input type="checkbox" checked={form.active}
          onChange={(e) => setForm({ ...form, active: e.target.checked })} />
        <span>Available to employees</span>
      </label>

      <div className="option-editor">
        <div className="row between">
          <h3>Customization groups</h3>
          <button className="btn btn-ghost btn-sm" onClick={() => setGroups((g) => [
            ...g, { name: 'Options', required: false, maxSelect: 1, items: [] },
          ])}>+ Add group</button>
        </div>
        {groups.length === 0 && <p className="muted">No options for this dish.</p>}
        {groups.map((group, index) => (
          <div className="group-editor" key={index}>
            <div className="three-col">
              <label className="field"><span>Group name</span>
                <input value={group.name} onChange={(e) => updateGroup(index, { name: e.target.value })} />
              </label>
              <label className="field"><span>Max selections</span>
                <input type="number" min="1" max="10" value={group.maxSelect}
                  onChange={(e) => updateGroup(index, { maxSelect: Number(e.target.value) })} />
              </label>
              <label className="switch inline">
                <input type="checkbox" checked={group.required}
                  onChange={(e) => updateGroup(index, { required: e.target.checked })} />
                <span>Required</span>
              </label>
            </div>
            {group.items.map((item, itemIndex) => (
              <div className="row gap" key={itemIndex}>
                <input placeholder="Option name" value={item.name}
                  onChange={(e) => updateGroup(index, {
                    items: group.items.map((it, i) => i === itemIndex ? { ...it, name: e.target.value } : it),
                  })} />
                <input placeholder="Extra ¥" value={item.extra}
                  onChange={(e) => updateGroup(index, {
                    items: group.items.map((it, i) => i === itemIndex ? { ...it, extra: e.target.value } : it),
                  })} />
                <button className="icon-btn" onClick={() => updateGroup(index, {
                  items: group.items.filter((_, i) => i !== itemIndex),
                })}>×</button>
              </div>
            ))}
            <div className="row gap">
              <button className="btn btn-ghost btn-sm" onClick={() => updateGroup(index, {
                items: [...group.items, { name: '', extra: '0.00' }],
              })}>+ Add option</button>
              <button className="btn btn-ghost btn-sm" onClick={() => setGroups((g) => g.filter((_, i) => i !== index))}>
                Remove group
              </button>
            </div>
          </div>
        ))}
      </div>
    </Modal>
  )
}

// ------------------------------------------------------------ daily menu
function DailyMenuSection({ toast }) {
  const [date, setDate] = useState(todayISO(1))
  const [dishes, setDishes] = useState([])
  const [rows, setRows] = useState({})
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [all, menu] = await Promise.all([
        consoleApi.dishes({ size: 48 }), consoleApi.dailyMenu(date),
      ])
      setDishes(all.items)
      const next = {}
      for (const entry of menu.entries) {
        next[entry.dishId] = { included: true, qty: String(entry.totalQty), remaining: entry.remainingQty }
      }
      setRows(next)
    } catch (e) { toast(friendlyError(e), 'error') }
    finally { setLoading(false) }
  }, [date, toast])
  useEffect(() => { load() }, [load])

  async function save() {
    setSaving(true)
    const entries = dishes
      .filter((dish) => rows[dish.id]?.included)
      .map((dish) => ({ dishId: dish.id, totalQty: Math.max(0, Number(rows[dish.id].qty) || 0) }))
    try {
      await consoleApi.saveDailyMenu({ date, entries })
      toast(`Menu for ${date} published.`, 'success')
      load()
    } catch (e) { toast(friendlyError(e), 'error') }
    finally { setSaving(false) }
  }

  return (
    <>
      <div className="toolbar">
        <label className="inline-field">Menu date
          <input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
        </label>
        <button className="btn btn-ghost btn-sm" onClick={() => setDate(todayISO())}>Today</button>
        <button className="btn btn-ghost btn-sm" onClick={() => setDate(todayISO(1))}>Tomorrow</button>
        <button className="btn btn-primary" disabled={saving} onClick={save}>
          {saving ? 'Saving...' : 'Publish menu'}
        </button>
      </div>
      <p className="muted">Tick the dishes to include them and set each day's supply. Sold quantities are preserved when you adjust a supply.</p>
      {loading ? <Spinner label="Loading daily menu..." /> : (
        <div className="panel table-panel">
          <table className="data-table">
            <thead><tr><th>On menu</th><th>Dish</th><th>Category</th><th>Supply</th><th>Remaining</th></tr></thead>
            <tbody>
              {dishes.map((dish) => {
                const row = rows[dish.id] || { included: false, qty: '12', remaining: 0 }
                return (
                  <tr key={dish.id}>
                    <td><input type="checkbox" checked={row.included}
                      onChange={(e) => setRows((r) => ({ ...r,
                        [dish.id]: { ...row, included: e.target.checked } }))} /></td>
                    <td><strong>{dish.name}</strong></td>
                    <td>{dish.category}</td>
                    <td><input className="qty-input" type="number" min="0" max="9999"
                      disabled={!row.included} value={row.qty}
                      onChange={(e) => setRows((r) => ({ ...r,
                        [dish.id]: { ...row, qty: e.target.value } }))} /></td>
                    <td>{row.included ? row.remaining ?? 0 : '—'}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}

// ------------------------------------------------------------ dashboard
function DashboardSection({ toast }) {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    try { setData(await consoleApi.dashboard()) } catch (e) { toast(friendlyError(e), 'error') }
    finally { setLoading(false) }
  }, [toast])
  useEffect(() => { load() }, [load])

  if (loading && !data) return <Spinner label="Loading dashboard..." />
  if (!data) return <EmptyState title="Dashboard unavailable" description="Please try refreshing." />

  return (
    <>
      <div className="row between">
        <p className="muted">Data comes from real orders · generated {data.generatedAt?.replace('T', ' ').slice(0, 19)}</p>
        <button className="btn btn-ghost btn-sm" onClick={load}>↻ Refresh</button>
      </div>
      <div className="metric-grid">
        <Metric label="Today's orders" value={data.today.orders} />
        <Metric label="Today's revenue" value={yuan(data.today.revenueCents)} accent />
        <Metric label="Pending" value={data.today.pending} />
        <Metric label="Confirmed" value={data.today.confirmed} />
        <Metric label="Completed" value={data.today.completed} />
        <Metric label="Cancelled" value={data.today.cancelled} />
      </div>
      <div className="two-col charts">
        <div className="panel">
          <h3>Top 10 dishes by volume</h3>
          <BarChart items={data.topDishes.map((d) => ({ label: d.name, value: d.quantity }))} />
        </div>
        <div className="panel">
          <h3>Lunch vs Dinner (today)</h3>
          <BarChart items={[
            { label: 'Lunch', value: data.mealSplit.lunch },
            { label: 'Dinner', value: data.mealSplit.dinner },
          ]} />
          <h3 className="mt">Low stock (≤ 3 portions)</h3>
          {data.lowStock.length === 0
            ? <p className="muted">No low-stock dishes right now.</p>
            : <ul className="low-stock-list">
                {data.lowStock.map((item) => (
                  <li key={`${item.dishId}-${item.remaining}`}>
                    <span>{item.name}</span><strong>{item.remaining} left</strong>
                  </li>
                ))}
              </ul>}
        </div>
      </div>
      <div className="panel">
        <h3>Last 7 days: orders and revenue</h3>
        <TrendChart points={data.trend} />
      </div>
    </>
  )
}

function Metric({ label, value, accent }) {
  return <div className={`metric ${accent ? 'accent' : ''}`}>
    <span className="metric-label">{label}</span>
    <strong className="metric-value">{value}</strong>
  </div>
}

function BarChart({ items }) {
  const max = Math.max(1, ...items.map((item) => Number(item.value) || 0))
  if (items.length === 0) return <p className="muted">No sales recorded yet.</p>
  return (
    <div className="bar-chart">
      {items.map((item, index) => (
        <div className="bar-row" key={`${item.label}-${index}`}>
          <span className="bar-label" title={item.label}>{item.label}</span>
          <div className="bar-track">
            <div className="bar-fill" style={{ width: `${(Number(item.value) / max) * 100}%` }} />
          </div>
          <strong className="bar-value">{item.value}</strong>
        </div>
      ))}
    </div>
  )
}

function TrendChart({ points }) {
  const width = 620, height = 200, padding = 34
  const maxOrders = Math.max(1, ...points.map((p) => p.orders))
  const maxRevenue = Math.max(1, ...points.map((p) => p.revenueCents))
  const x = (i) => padding + (i * (width - padding * 2)) / Math.max(1, points.length - 1)
  const yOrders = (v) => height - padding - (v / maxOrders) * (height - padding * 2)
  const yRevenue = (v) => height - padding - (v / maxRevenue) * (height - padding * 2)
  const ordersLine = points.map((p, i) => `${x(i)},${yOrders(p.orders)}`).join(' ')
  const revenueLine = points.map((p, i) => `${x(i)},${yRevenue(p.revenueCents)}`).join(' ')
  return (
    <div className="trend-wrap">
      <div className="legend">
        <span><i className="dot orders" /> Orders</span>
        <span><i className="dot revenue" /> Revenue (¥)</span>
      </div>
      <svg viewBox={`0 0 ${width} ${height}`} className="trend-svg" role="img"
        aria-label="Seven day orders and revenue trend">
        <line x1={padding} y1={height - padding} x2={width - padding} y2={height - padding} className="axis" />
        <line x1={padding} y1={padding} x2={padding} y2={height - padding} className="axis" />
        <polyline points={ordersLine} className="line orders" />
        <polyline points={revenueLine} className="line revenue" />
        {points.map((p, i) => (
          <g key={p.date}>
            <circle cx={x(i)} cy={yOrders(p.orders)} r="3.5" className="point orders" />
            <circle cx={x(i)} cy={yRevenue(p.revenueCents)} r="3.5" className="point revenue" />
            <text x={x(i)} y={height - 10} textAnchor="middle" className="axis-label">{p.date.slice(5)}</text>
          </g>
        ))}
      </svg>
    </div>
  )
}
