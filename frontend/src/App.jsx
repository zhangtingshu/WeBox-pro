import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  api, authApi, menuApi, preferenceApi, orderApi, aiApi,
  setToken, clearToken, onSessionExpired, ApiError,
} from './api'
import {
  yuan, yuanToCents, centsToInput, todayISO, useDebounced,
  friendlyError, lineKey, MAX_PORTIONS,
} from './utils'
import { DishCard, DishDetail, DishImage, Modal, Spinner, EmptyState, ToastStack } from './components'
import Console from './Console'

let toastSeq = 0
function newToast(message, kind = 'info') {
  return { id: ++toastSeq, message, kind }
}

export default function App() {
  const [booting, setBooting] = useState(true)
  const [user, setUser] = useState(null)
  const [prefs, setPrefs] = useState(null)
  const [meta, setMeta] = useState(null)
  const [view, setView] = useState('menu')
  const [consoleSection, setConsoleSection] = useState('dishes')
  const [toasts, setToasts] = useState([])
  const [cart, setCart] = useState([])
  const [lastOrder, setLastOrder] = useState(null)
  const [filters, setFilters] = useState({ keyword: '', categories: [], recommend: null, page: 0, date: '' })
  const debouncedKeyword = useDebounced(filters.keyword, 300)
  const [menu, setMenu] = useState({
    items: [], totalElements: 0, totalPages: 0, page: 0, size: 12,
    menuDate: todayISO(), defaultMealPeriod: 'Lunch', defaultDeliveryDate: todayISO(),
    cutoffNotice: '', recommendEnabled: false,
  })
  const [menuLoading, setMenuLoading] = useState(false)
  const [detail, setDetail] = useState(null)
  const [pendingAdd, setPendingAdd] = useState(null)
  const lastAddClick = useRef(0)

  const toast = useCallback((message, kind = 'info') => {
    const item = newToast(message, kind)
    setToasts((prev) => [...prev.slice(-3), item])
    setTimeout(() => setToasts((prev) => prev.filter((t) => t.id !== item.id)), 4200)
  }, [])
  const dismissToast = useCallback((id) => setToasts((prev) => prev.filter((t) => t.id !== id)), [])

  // ---- session bootstrap ----
  useEffect(() => {
    onSessionExpired(() => {
      clearToken()
      setUser(null)
      setCart([])
      setView('menu')
      toast('Your session has expired. Please sign in again.', 'warn')
    })
    ;(async () => {
      try {
        const session = await api('/auth/refresh', { method: 'POST', skipRefresh: true })
        setToken(session.accessToken)
        const me = await authApi.me()
        setUser(me.user)
        setPrefs(me.preferences)
      } catch {
        clearToken()
      }
      try { setMeta(await menuApi.meta()) } catch { /* offline meta is optional */ }
      setBooting(false)
    })()
  }, [toast])

  // ---- menu loading ----
  // Only the debounced keyword (plus explicit filter controls) triggers a request;
  // typing itself must not fire one request per keystroke during the morning peak.
  const filtersRef = useRef(filters)
  filtersRef.current = filters
  const loadMenu = useCallback(async () => {
    if (!user) return
    const f = filtersRef.current
    const params = {
      page: f.page,
      size: 12,
      keyword: debouncedKeyword,
      categories: f.categories.join(','),
      date: f.date || todayISO(),
    }
    if (f.recommend !== null) params.recommend = String(f.recommend)
    setMenuLoading(true)
    try {
      setMenu(await menuApi.list(params))
    } catch (error) {
      toast(friendlyError(error), 'error')
    } finally {
      setMenuLoading(false)
    }
  }, [user, debouncedKeyword, toast])

  useEffect(() => { loadMenu() }, [loadMenu,
    filters.page, filters.categories, filters.recommend, filters.date])
  const loadMenuRef = useRef(loadMenu)
  loadMenuRef.current = loadMenu

  // ---- real-time stock (SSE over fetch, token stays in the header) ----
  useEffect(() => {
    if (!user) return undefined
    const controller = new AbortController()
    menuApi.streamStock({
      signal: controller.signal,
      onEvent: (event, data) => {
        if (event === 'refresh') { loadMenuRef.current(); return }
        if (event !== 'stock') return
        try {
          const updates = JSON.parse(data)
          setMenu((prev) => ({
            ...prev,
            items: prev.items.map((item) => {
              const change = updates.find((u) => u.dishId === item.dish.id)
              if (!change) return item
              return {
                ...item,
                remaining: change.remaining,
                lowStock: change.remaining > 0 && change.remaining <= 3,
                soldOut: change.remaining <= 0,
              }
            }),
          }))
        } catch { /* ignore malformed event */ }
      },
    }).catch(() => { /* stream reconnects on next page load */ })
    return () => controller.abort()
  }, [user])

  // ---- cart ----
  const totalPortions = useMemo(() => cart.reduce((sum, line) => sum + line.quantity, 0), [cart])
  const totalCents = useMemo(() => cart.reduce((sum, line) => sum + line.unitCents * line.quantity, 0), [cart])
  const cartFull = totalPortions >= MAX_PORTIONS

  const commitAdd = useCallback((payload) => {
    if (cartFull) { toast('An order can contain at most 5 portions in total.', 'warn'); return }
    const key = lineKey(payload.dish.id, payload.optionIds)
    setCart((prev) => {
      const existing = prev.find((line) => line.key === key)
      if (existing) {
        return prev.map((line) => line.key === key ? { ...line, quantity: line.quantity + 1 } : line)
      }
      return [...prev, {
        key, dish: payload.dish, optionIds: payload.optionIds,
        labels: payload.labels, unitCents: payload.unitCents, quantity: 1,
      }]
    })
    toast(`${payload.dish.name} added to cart`, 'success')
  }, [cartFull, toast])

  // Throttle: protects the API from double-clicks during the 09:30-10:00 peak.
  const handleAdd = useCallback((dish, optionIds, labels, unitCents) => {
    const now = Date.now()
    if (now - lastAddClick.current < 200) return
    lastAddClick.current = now
    const conflicts = (dish.allergens || []).filter((a) => (prefs?.allergens || []).includes(a))
    const payload = { dish, optionIds, labels, unitCents }
    if (conflicts.length) {
      setPendingAdd({ payload, conflicts })
    } else {
      commitAdd(payload)
    }
  }, [prefs, commitAdd])

  function changeQuantity(key, delta) {
    setCart((prev) => prev.map((line) => {
      if (line.key !== key) return line
      const next = line.quantity + delta
      if (next <= 0) return null
      if (delta > 0 && totalPortions >= MAX_PORTIONS) return line
      return { ...line, quantity: next }
    }).filter(Boolean))
  }

  async function openDishById(dishId, remaining = 0) {
    try {
      const dish = await menuApi.detail(dishId)
      setDetail({ dish, remaining, lowStock: remaining > 0 && remaining <= 3, soldOut: remaining <= 0 })
    } catch (error) {
      toast(friendlyError(error), 'error')
    }
  }

  async function afterAuthenticated(credentials, isRegister) {
    if (isRegister) await authApi.register(credentials)
    const session = await authApi.login({ email: credentials.email, password: credentials.password })
    setToken(session.accessToken)
    const me = await authApi.me()
    setUser(me.user)
    setPrefs(me.preferences)
    setView('menu')
    toast(isRegister ? 'Welcome to WeBox! Your account is ready.' : 'Welcome back!', 'success')
  }

  async function signOut() {
    try { await authApi.logout() } catch { /* ignore */ }
    clearToken()
    setUser(null)
    setCart([])
    setView('menu')
    toast('You have been signed out.')
  }

  if (booting) {
    return <div className="boot"><Spinner label="Starting WeBox..." /></div>
  }
  if (!user) {
    return (
      <>
        <AuthScreen onAuthenticated={afterAuthenticated} toast={toast} />
        <ToastStack toasts={toasts} onDismiss={dismissToast} />
      </>
    )
  }

  const navItems = [
    { key: 'menu', label: 'Menu', icon: '▦' },
    { key: 'cart', label: `Cart${totalPortions ? ` (${totalPortions})` : ''}`, icon: '◰' },
    { key: 'orders', label: 'My Orders', icon: '≡' },
    { key: 'preferences', label: 'Preferences', icon: '⚙' },
  ]
  if (user.role === 'ADMIN') navItems.push({ key: 'console', label: 'Console', icon: '▤' })

  return (
    <div className={`app ${view === 'console' ? 'console-mode' : ''}`}>
      {view !== 'console' && (
        <header className="topbar">
          <div className="topbar-inner">
            <button className="brand" onClick={() => setView('menu')}>
              <span className="brand-mark">W</span>
              <span>WeBox <small>Meal Ordering</small></span>
            </button>
            <nav className="main-nav">
              {navItems.map((item) => (
                <button key={item.key}
                  className={`nav-link ${view === item.key || (view === 'checkout' && item.key === 'cart') || (view === 'success' && item.key === 'orders') ? 'active' : ''}`}
                  onClick={() => setView(item.key)}>
                  <span className="nav-icon">{item.icon}</span>{item.label}
                </button>
              ))}
            </nav>
            <div className="user-box">
              <span className="user-name">{user.fullName || user.email}</span>
              <span className="role-chip">{user.role === 'ADMIN' ? 'Admin' : 'Employee'}</span>
              <button className="btn btn-ghost btn-sm" onClick={signOut}>Sign out</button>
            </div>
          </div>
        </header>
      )}

      <main className={view === 'console' ? 'console-main' : 'page'}>
        {view === 'menu' && (
          <MenuPage
            menu={menu} loading={menuLoading} filters={filters} setFilters={setFilters}
            meta={meta} preferences={prefs}
            onOpen={(item) => setDetail(item)}
            onAdd={handleAdd} cartFull={cartFull}
            onToggleRecommend={() => setFilters((f) => ({ ...f, recommend: !(f.recommend ?? menu.recommendEnabled), page: 0 }))}
            onOpenDishById={openDishById}
            onChangePreferences={setPrefs}
            toast={toast}
          />
        )}
        {view === 'cart' && (
          <CartPage cart={cart} totalCents={totalCents} totalPortions={totalPortions}
            cartFull={cartFull} changeQuantity={changeQuantity}
            onCheckout={() => setView('checkout')} onBrowse={() => setView('menu')} />
        )}
        {view === 'checkout' && (
          <CheckoutPage cart={cart} totalCents={totalCents} preferences={prefs}
            defaults={menu} onBack={() => setView('cart')} toast={toast}
            onPlaced={(order) => { setLastOrder(order); setCart([]); setView('success') }} />
        )}
        {view === 'success' && lastOrder && (
          <OrderSuccess order={lastOrder} onOrders={() => setView('orders')} onMenu={() => setView('menu')} />
        )}
        {view === 'orders' && (
          <OrdersPage toast={toast} onBrowse={() => setView('menu')} />
        )}
        {view === 'preferences' && (
          <PreferencesPage preferences={prefs} meta={meta} toast={toast}
            onSaved={(updated) => { setPrefs(updated); setFilters((f) => ({ ...f, page: 0 })); loadMenu() }} />
        )}
        {view === 'console' && (
          <Console section={consoleSection} setSection={setConsoleSection}
            onExit={() => setView('menu')} toast={toast} meta={meta} />
        )}
      </main>

      {detail && (
        <DishDetail dish={detail.dish} preferences={prefs}
          onClose={() => setDetail(null)}
          onConfirm={(payload) => {
            setDetail(null)
            handleAdd(detail.dish, payload.optionIds, payload.labels, payload.unitCents)
          }} />
      )}

      {pendingAdd && (
        <Modal title="Allergen warning" onClose={() => setPendingAdd(null)}
          footer={<>
            <button className="btn btn-ghost" onClick={() => setPendingAdd(null)}>Cancel</button>
            <button className="btn btn-danger" onClick={() => { commitAdd(pendingAdd.payload); setPendingAdd(null) }}>
              Add anyway
            </button>
          </>}>
          <p>This dish contains an allergen you flagged: <strong>[{pendingAdd.conflicts.join(', ')}]</strong>. Add anyway?</p>
          <p className="muted">We never hide dishes from you — the choice is always yours.</p>
        </Modal>
      )}

      <ToastStack toasts={toasts} onDismiss={dismissToast} />
    </div>
  )
}

// ------------------------------------------------------------------ auth
function AuthScreen({ onAuthenticated, toast }) {
  const [mode, setMode] = useState('login')
  const [form, setForm] = useState({ email: '', password: '', fullName: '' })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  function validate() {
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) return 'Please enter a valid email address.'
    if (form.email.length > 200) return 'Email is too long (maximum 200 characters).'
    if (!/^(?=.*[A-Za-z])(?=.*\d).{8,}$/.test(form.password)) {
      return 'Password must be at least 8 characters and contain both letters and numbers.'
    }
    if (form.password.length > 64) return 'Password is too long (maximum 64 characters).'
    return ''
  }

  async function submit(event) {
    event.preventDefault()
    const problem = validate()
    if (problem) { setError(problem); return }
    setBusy(true); setError('')
    try {
      const credentials = mode === 'register'
        ? { email: form.email.trim(), password: form.password, fullName: form.fullName.trim() }
        : { email: form.email.trim(), password: form.password }
      await onAuthenticated(credentials, mode === 'register')
    } catch (e) {
      setError(friendlyError(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-hero">
        <div className="brand big"><span className="brand-mark">W</span><span>WeBox</span></div>
        <h1>Fresh meals, ordered ahead.</h1>
        <p>Browse today's menu, customise your plate and let the canteen know what to prepare.</p>
        <ul>
          <li>Browse 30+ dishes across six cuisines</li>
          <li>Live stock during the morning rush</li>
          <li>Personalised preferences and AI picks</li>
        </ul>
      </div>
      <div className="auth-card">
        <h2>{mode === 'login' ? 'Sign in' : 'Create an account'}</h2>
        <p className="muted">{mode === 'login' ? 'Use your work email to continue.' : 'Register with your work email.'}</p>
        <form onSubmit={submit} noValidate>
          {mode === 'register' && (
            <label className="field">
              <span>Full name</span>
              <input maxLength={100} value={form.fullName}
                onChange={(e) => setForm({ ...form, fullName: e.target.value })}
                placeholder="Jane Doe" />
            </label>
          )}
          <label className="field">
            <span>Work email</span>
            <input type="email" maxLength={200} value={form.email}
              onChange={(e) => setForm({ ...form, email: e.target.value })}
              placeholder="you@company.com" autoComplete="username" />
          </label>
          <label className="field">
            <span>Password</span>
            <input type="password" maxLength={64} value={form.password}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
              placeholder="At least 8 chars, letters + digits" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} />
          </label>
          {error && <div className="form-error">{error}</div>}
          <button className="btn btn-primary btn-block" disabled={busy}>
            {busy ? 'Please wait...' : mode === 'login' ? 'Sign in' : 'Create account'}
          </button>
        </form>
        <button className="link-btn" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError('') }}>
          {mode === 'login' ? 'New here? Create an account' : 'Already registered? Sign in'}
        </button>
        <div className="demo-hint">
          <strong>Demo accounts</strong>
          <div>Employee: employee@webox.com / Employee@123</div>
          <div>Admin: admin@webox.com / Admin@12345</div>
        </div>
      </div>
    </div>
  )
}

// ------------------------------------------------------------------ menu
function MenuPage({ menu, loading, filters, setFilters, meta, preferences, onOpen, onAdd,
                    cartFull, onToggleRecommend, onOpenDishById, toast }) {
  const [aiOpen, setAiOpen] = useState(false)
  const categories = meta?.categories || ['Chinese', 'Western', 'Japanese', 'Light Meal', 'Korean', 'Southeast Asian']

  function toggleCategory(category) {
    setFilters((f) => ({
      ...f, page: 0,
      categories: f.categories.includes(category)
        ? f.categories.filter((c) => c !== category)
        : [...f.categories, category],
    }))
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Today's Menu</h1>
          <p className="muted">{menu.menuDate} · {menu.totalElements} dishes available</p>
        </div>
        <button className="btn btn-accent" onClick={() => setAiOpen(true)}>✦ Ask the AI assistant</button>
      </div>

      {menu.cutoffNotice && <div className="notice">{menu.cutoffNotice}</div>}

      <div className="filter-bar">
        <div className="search-wrap">
          <span className="search-icon">⌕</span>
          <input type="search" maxLength={50} placeholder="Search dishes or ingredients..."
            value={filters.keyword} onChange={(e) => setFilters((f) => ({ ...f, keyword: e.target.value, page: 0 }))} />
        </div>
        <div className="chip-row">
          {categories.map((category) => (
            <button key={category}
              className={`chip ${filters.categories.includes(category) ? 'selected' : ''}`}
              onClick={() => toggleCategory(category)}>{category}</button>
          ))}
          {filters.categories.length > 0 && (
            <button className="chip clear" onClick={() => setFilters((f) => ({ ...f, categories: [], page: 0 }))}>Clear</button>
          )}
        </div>
        <label className="switch">
          <input type="checkbox" checked={filters.recommend ?? menu.recommendEnabled}
            onChange={onToggleRecommend} />
          <span>Recommend for me</span>
        </label>
      </div>

      {loading && menu.items.length === 0
        ? <Spinner label="Loading today's menu..." />
        : menu.items.length === 0
          ? <EmptyState title="No dishes found"
              description="Try a different keyword or clear the category filters." />
          : (
            <>
              <div className="dish-grid">
                {menu.items.map((item) => (
                  <DishCard key={`${item.dish.id}-${item.dish.name}`} item={item}
                    onOpen={onOpen} onAdd={onAdd}
                    canAdd={!cartFull && !item.soldOut}
                    disabledReason={cartFull ? 'Maximum 5 portions per order' : 'Sold out'} />
                ))}
              </div>
              {menu.totalPages > 1 && (
                <div className="pager">
                  <button className="btn btn-ghost btn-sm" disabled={menu.page <= 0}
                    onClick={() => setFilters((f) => ({ ...f, page: f.page - 1 }))}>← Previous</button>
                  <span>Page {menu.page + 1} of {menu.totalPages}</span>
                  <button className="btn btn-ghost btn-sm" disabled={menu.page + 1 >= menu.totalPages}
                    onClick={() => setFilters((f) => ({ ...f, page: f.page + 1 }))}>Next →</button>
                </div>
              )}
            </>
          )}

      {cartFull && (
        <div className="sticky-hint">Maximum 5 portions per order reached. Remove an item before adding more.</div>
      )}

      {aiOpen && (
        <AiAssistant onClose={() => setAiOpen(false)} onOpenDishById={onOpenDishById}
          onQuickAdd={(dishId) => onOpenDishById(dishId)} toast={toast} />
      )}
    </>
  )
}

// ------------------------------------------------------------------ AI assistant
function AiAssistant({ onClose, onOpenDishById, toast }) {
  const [query, setQuery] = useState('I want something light today')
  const [status, setStatus] = useState('Describe what you feel like eating, then press Ask.')
  const [thinking, setThinking] = useState('')
  const [dishes, setDishes] = useState([])
  const [running, setRunning] = useState(false)
  const controllerRef = useRef(null)

  useEffect(() => () => controllerRef.current?.abort(), [])

  async function ask() {
    if (!query.trim() || running) return
    setRunning(true); setDishes([]); setThinking(''); setStatus('Connecting to the assistant...')
    const controller = new AbortController()
    controllerRef.current = controller
    try {
      await aiApi.recommend(query.trim(), {
        signal: controller.signal,
        onEvent: (event, data) => {
          try {
            const payload = JSON.parse(data)
            if (event === 'status') setStatus(payload.message || '')
            else if (event === 'delta') setThinking((prev) => (prev + (payload.text || '')).slice(-800))
            else if (event === 'dish') setDishes((prev) => [...prev, payload])
            else if (event === 'done') {
              setStatus(`Done · ${payload.count} recommendation(s) · engine: ${payload.engine}`)
            }
          } catch { /* ignore */ }
        },
      })
    } catch (error) {
      if (error?.name !== 'AbortError') {
        setStatus(friendlyError(error))
        toast(friendlyError(error), 'error')
      }
    } finally {
      setRunning(false)
    }
  }

  return (
    <div className="drawer-backdrop" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose() }}>
      <aside className="drawer">
        <div className="drawer-head">
          <div><h2>✦ AI Meal Assistant</h2><p className="muted">English prompts work best</p></div>
          <button className="icon-btn" onClick={onClose} aria-label="Close">×</button>
        </div>
        <div className="drawer-body">
          <div className="ai-input">
            <input maxLength={500} value={query}
              onChange={(e) => setQuery(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter') ask() }}
              placeholder="e.g. A high-protein low-fat lunch" />
            <button className="btn btn-primary" disabled={running} onClick={ask}>
              {running ? 'Thinking...' : 'Ask'}
            </button>
          </div>
          <div className="ai-suggestions">
            {['I want something light today', 'A high-protein low-fat lunch', 'Nothing I ate yesterday']
              .map((sample) => <button key={sample} className="chip" onClick={() => setQuery(sample)}>{sample}</button>)}
          </div>
          <div className="ai-status">{status}</div>
          {thinking && <pre className="ai-thinking">{thinking}</pre>}
          <div className="ai-results">
            {dishes.map((dish) => (
              <div className="ai-card" key={dish.dishId}>
                <DishImage src={dish.imageUrl} alt={dish.name} />
                <div>
                  <div className="row between">
                    <strong>{dish.name}</strong><span className="dish-price">{yuan(dish.priceCents)}</span>
                  </div>
                  <span className="tag subtle">{dish.category}</span>
                  <p className="ai-reason">{dish.reason}</p>
                  <button className="btn btn-ghost btn-sm" onClick={() => onOpenDishById(dish.dishId, dish.remaining)}>
                    View & add
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      </aside>
    </div>
  )
}

// ------------------------------------------------------------------ cart
function CartPage({ cart, totalCents, totalPortions, cartFull, changeQuantity, onCheckout, onBrowse }) {
  if (cart.length === 0) {
    return (
      <div className="center">
        <EmptyState title="Your cart is empty" description="Add a dish from today's menu to get started." />
        <button className="btn btn-primary" onClick={onBrowse}>Browse the menu</button>
      </div>
    )
  }
  return (
    <>
      <div className="page-head"><div><h1>Your Cart</h1><p className="muted">{totalPortions} of {MAX_PORTIONS} portions allowed per order</p></div></div>
      {cartFull && <div className="notice">Maximum 5 portions per order reached. The add buttons are now disabled.</div>}
      <div className="panel">
        {cart.map((line) => (
          <div className="cart-line" key={line.key}>
            <DishImage src={line.dish.imageUrl} alt="" />
            <div className="cart-line-main">
              <strong>{line.dish.name}</strong>
              {line.labels.length > 0
                ? <div className="cart-options">{line.labels.join(' · ')}</div>
                : <div className="cart-options muted">Standard</div>}
              <span className="muted">{yuan(line.unitCents)} each</span>
            </div>
            <div className="qty-control">
              <button onClick={() => changeQuantity(line.key, -1)} aria-label="Decrease">−</button>
              <span>{line.quantity}</span>
              <button onClick={() => changeQuantity(line.key, 1)} disabled={cartFull} aria-label="Increase">+</button>
            </div>
            <strong className="line-total">{yuan(line.unitCents * line.quantity)}</strong>
          </div>
        ))}
      </div>
      <div className="checkout-summary">
        <div><span className="muted">Total</span><div className="grand-total">{yuan(totalCents)}</div></div>
        <button className="btn btn-primary" onClick={onCheckout}>Proceed to checkout →</button>
      </div>
    </>
  )
}

// ------------------------------------------------------------------ checkout
function CheckoutPage({ cart, totalCents, preferences, defaults, onBack, onPlaced, toast }) {
  const [date, setDate] = useState(defaults.defaultDeliveryDate || todayISO())
  const [period, setPeriod] = useState(defaults.defaultMealPeriod || 'Lunch')
  const [address, setAddress] = useState('')
  const [addresses, setAddresses] = useState([])
  const [active, setActive] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  // Stable across repeated clicks: the server uses it to guarantee one order only.
  const idempotencyKey = useRef(`chk-${Date.now()}-${Math.random().toString(36).slice(2)}`)

  useEffect(() => {
    orderApi.addresses().then(setAddresses).catch(() => {})
  }, [])
  useEffect(() => {
    orderApi.active(date, period).then(setActive).catch(() => {})
  }, [date, period])

  const budgetMax = preferences?.budgetMaxCents || 0
  const overBudget = budgetMax > 0 && totalCents > budgetMax

  async function submit() {
    if (submitting) return
    if (!address.trim()) { setError('Please enter a delivery address.'); return }
    if (address.trim().length > 200) { setError('Address must be 200 characters or fewer.'); return }
    setSubmitting(true); setError('')
    try {
      const order = await orderApi.place({
        deliveryDate: date,
        mealPeriod: period,
        address: address.trim(),
        items: cart.map((line) => ({
          dishId: line.dish.id, quantity: line.quantity, optionItemIds: line.optionIds,
        })),
      }, idempotencyKey.current)
      onPlaced(order)
    } catch (e) {
      setError(friendlyError(e))
      toast(friendlyError(e), 'error')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <>
      <div className="page-head"><div><h1>Checkout</h1><p className="muted">Review your order before submitting</p></div></div>
      <div className="two-col">
        <div className="panel">
          <h3>Order summary</h3>
          {cart.map((line) => (
            <div className="summary-line" key={line.key}>
              <div><strong>{line.dish.name}</strong> <span className="muted">× {line.quantity}</span>
                {line.labels.length > 0 && <div className="cart-options">{line.labels.join(' · ')}</div>}
              </div>
              <span>{yuan(line.unitCents * line.quantity)}</span>
            </div>
          ))}
          <div className="summary-total"><span>Total</span><strong>{yuan(totalCents)}</strong></div>
        </div>

        <div className="panel">
          <h3>Delivery details</h3>
          <label className="field"><span>Delivery date</span>
            <input type="date" min={todayISO()} value={date} onChange={(e) => setDate(e.target.value)} />
          </label>
          <div className="field"><span>Meal period</span>
            <div className="segmented">
              {['Lunch', 'Dinner'].map((value) => (
                <button key={value} className={period === value ? 'selected' : ''}
                  onClick={() => setPeriod(value)}>{value}</button>
              ))}
            </div>
          </div>
          <label className="field"><span>Delivery address</span>
            <input list="saved-addresses" maxLength={200} value={address}
              onChange={(e) => setAddress(e.target.value)}
              placeholder="e.g. Floor 3, Building A" />
            <datalist id="saved-addresses">
              {addresses.map((saved) => <option key={saved} value={saved} />)}
            </datalist>
          </label>
          <p className="form-hint">Lunch closes at 10:00, Dinner at 15:00. Late orders move to the next available meal.</p>
          {overBudget && <div className="notice warn">This order exceeds your per-meal budget of {yuan(budgetMax)}. You can still place it.</div>}
          {active?.notice && <div className="notice">{active.notice}</div>}
          {active?.exists && (
            <div className="notice warn">
              You already have an active {active.resolvedMealPeriod} order for {active.resolvedDate}.
            </div>
          )}
          {error && <div className="form-error">{error}</div>}
          {active?.exists
            ? <button className="btn btn-accent btn-block" onClick={() => toast('Open My Orders to view your existing order.')}>
                View Existing Order
              </button>
            : <button className="btn btn-primary btn-block" disabled={submitting} onClick={submit}>
                {submitting ? 'Placing order...' : `Place Order · ${yuan(totalCents)}`}
              </button>}
          <button className="link-btn" onClick={onBack}>← Back to cart</button>
        </div>
      </div>
    </>
  )
}

// ------------------------------------------------------------------ order success
function OrderSuccess({ order, onOrders, onMenu }) {
  return (
    <div className="success-card">
      <div className="success-icon">✓</div>
      <h1>Order placed!</h1>
      <p className="muted">Your order has been received by the canteen.</p>
      <div className="success-order">
        <div><span className="muted">Order number</span><strong>{order.orderNo}</strong></div>
        <div><span className="muted">Delivery date</span><strong>{order.deliveryDate}</strong></div>
        <div><span className="muted">Meal period</span><strong>{order.mealPeriod}</strong></div>
        <div><span className="muted">Address</span><strong>{order.address}</strong></div>
        <div><span className="muted">Total</span><strong>{yuan(order.totalCents)}</strong></div>
        <div><span className="muted">Status</span><strong>{order.status}</strong></div>
      </div>
      {order.notice && <div className="notice">{order.notice}</div>}
      <div className="row gap center">
        <button className="btn btn-primary" onClick={onOrders}>View my orders</button>
        <button className="btn btn-ghost" onClick={onMenu}>Back to menu</button>
      </div>
    </div>
  )
}

// ------------------------------------------------------------------ orders
function OrdersPage({ toast, onBrowse }) {
  const [orders, setOrders] = useState([])
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    try { setOrders(await orderApi.list()) } catch (e) { toast(friendlyError(e), 'error') }
    finally { setLoading(false) }
  }, [toast])
  useEffect(() => { load() }, [load])

  async function cancel(id) {
    try {
      const updated = await orderApi.cancel(id)
      setSelected(updated)
      toast('Order cancelled. Stock has been restored.', 'success')
      load()
    } catch (e) { toast(friendlyError(e), 'error') }
  }

  return (
    <>
      <div className="page-head"><div><h1>My Orders</h1><p className="muted">All of your past and current orders</p></div></div>
      {loading ? <Spinner label="Loading orders..." />
        : orders.length === 0
          ? <div className="center"><EmptyState title="No orders yet" description="Place your first order from today's menu." /><button className="btn btn-primary" onClick={onBrowse}>Browse the menu</button></div>
          : <div className="panel">
              {orders.map((order) => (
                <div className="order-row" key={order.id}>
                  <div><strong>{order.orderNo}</strong><div className="muted">{order.createdAt?.replace('T', ' ').slice(0, 16)}</div></div>
                  <div className="muted">{order.deliveryDate} · {order.mealPeriod}</div>
                  <div><span className={`status ${order.status.toLowerCase()}`}>{order.status}</span></div>
                  <strong>{yuan(order.totalCents)}</strong>
                  <button className="btn btn-ghost btn-sm" onClick={() => setSelected(order)}>Details</button>
                </div>
              ))}
            </div>}
      {selected && (
        <Modal title={`Order ${selected.orderNo}`} onClose={() => setSelected(null)} wide
          footer={<>
            {selected.status === 'Pending' && (
              <button className="btn btn-danger" onClick={() => cancel(selected.id)}>Cancel order</button>
            )}
            <button className="btn btn-ghost" onClick={() => setSelected(null)}>Close</button>
          </>}>
          <div className="detail-facts">
            <div><dt className="muted">Status</dt><dd>{selected.status}</dd></div>
            <div><dt className="muted">Delivery</dt><dd>{selected.deliveryDate} · {selected.mealPeriod}</dd></div>
            <div><dt className="muted">Address</dt><dd>{selected.address}</dd></div>
          </div>
          <table className="data-table">
            <thead><tr><th>Dish</th><th>Options</th><th>Qty</th><th>Unit</th><th>Subtotal</th></tr></thead>
            <tbody>
              {(selected.items || []).map((item, index) => (
                <tr key={`${item.dishId}-${index}`}>
                  <td>{item.dishName}</td>
                  <td>{(item.optionLabels || []).join(' · ') || 'Standard'}</td>
                  <td>{item.quantity}</td>
                  <td>{yuan(item.unitCents)}</td>
                  <td>{yuan(item.subtotalCents)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="summary-total"><span>Total</span><strong>{yuan(selected.totalCents)}</strong></div>
          {selected.notice && <div className="notice">{selected.notice}</div>}
        </Modal>
      )}
    </>
  )
}

// ------------------------------------------------------------------ preferences
function PreferencesPage({ preferences, meta, toast, onSaved }) {
  const [form, setForm] = useState({
    allergens: preferences?.allergens || [],
    cuisines: preferences?.cuisines || [],
    spiceLevel: preferences?.spiceLevel || 'None',
    taste: preferences?.taste || 'Balanced',
    budgetMin: centsToInput(preferences?.budgetMinCents || 0),
    budgetMax: centsToInput(preferences?.budgetMaxCents || 0),
    recommendEnabled: preferences?.recommendEnabled || false,
  })
  const [saving, setSaving] = useState(false)

  function toggle(listKey, value) {
    setForm((f) => ({
      ...f,
      [listKey]: f[listKey].includes(value)
        ? f[listKey].filter((item) => item !== value)
        : [...f[listKey], value],
    }))
  }

  async function save() {
    setSaving(true)
    try {
      const updated = await preferenceApi.save({
        allergens: form.allergens,
        cuisines: form.cuisines,
        spiceLevel: form.spiceLevel,
        taste: form.taste,
        budgetMinCents: yuanToCents(form.budgetMin),
        budgetMaxCents: yuanToCents(form.budgetMax),
        recommendEnabled: form.recommendEnabled,
      })
      toast('Preferences saved.', 'success')
      onSaved(updated)
    } catch (e) {
      toast(friendlyError(e), 'error')
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      <div className="page-head"><div><h1>My Preferences</h1><p className="muted">Used for recommendations, highlighting and budget hints</p></div></div>
      <div className="panel pref-panel">
        <section>
          <h3>Allergens</h3>
          <p className="muted">We will warn you when a dish contains one of these — we never hide dishes.</p>
          <div className="chip-row">
            {(meta?.allergens || ['Peanuts', 'Dairy', 'Egg', 'Gluten', 'Soy', 'Fish', 'Shellfish'])
              .map((value) => (
                <button key={value} className={`chip ${form.allergens.includes(value) ? 'selected danger' : ''}`}
                  onClick={() => toggle('allergens', value)}>{value}</button>
              ))}
          </div>
        </section>
        <section>
          <h3>Preferred cuisines</h3>
          <div className="chip-row">
            {(meta?.categories || ['Chinese', 'Western', 'Japanese', 'Light Meal', 'Korean', 'Southeast Asian'])
              .map((value) => (
                <button key={value} className={`chip ${form.cuisines.includes(value) ? 'selected' : ''}`}
                  onClick={() => toggle('cuisines', value)}>{value}</button>
              ))}
          </div>
        </section>
        <section className="two-col">
          <label className="field"><span>Spice preference</span>
            <select value={form.spiceLevel} onChange={(e) => setForm({ ...form, spiceLevel: e.target.value })}>
              {(meta?.spiceLevels || ['None', 'Mild', 'Medium', 'Hot']).map((v) => <option key={v}>{v}</option>)}
            </select>
          </label>
          <label className="field"><span>Taste preference</span>
            <select value={form.taste} onChange={(e) => setForm({ ...form, taste: e.target.value })}>
              {(meta?.tastes || ['Light', 'Balanced', 'Rich']).map((v) => <option key={v}>{v}</option>)}
            </select>
          </label>
        </section>
        <section className="two-col">
          <label className="field"><span>Minimum budget (¥)</span>
            <input inputMode="decimal" value={form.budgetMin}
              onChange={(e) => setForm({ ...form, budgetMin: e.target.value })} placeholder="15.00" />
          </label>
          <label className="field"><span>Maximum budget (¥)</span>
            <input inputMode="decimal" value={form.budgetMax}
              onChange={(e) => setForm({ ...form, budgetMax: e.target.value })} placeholder="30.00" />
          </label>
        </section>
        <section>
          <label className="switch">
            <input type="checkbox" checked={form.recommendEnabled}
              onChange={(e) => setForm({ ...form, recommendEnabled: e.target.checked })} />
            <span>Enable "Recommend for me" by default</span>
          </label>
        </section>
        <button className="btn btn-primary" disabled={saving} onClick={save}>
          {saving ? 'Saving...' : 'Save preferences'}
        </button>
      </div>
    </>
  )
}
