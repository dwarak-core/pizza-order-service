// === State ===
const API = '';
let menu = { sizes: [], toppings: [] };
let currentOrderId = null;
let pendingIdempotencyKey = null;
let lastPricing = null;
let orderItems = [];  // local list: { sizeId, sizeName, quantity, toppingIds, toppingNames }
let promoCode = null;
let tipMode = null;
let tipValue = null;

// === Init ===
document.addEventListener('DOMContentLoaded', async () => {
    await loadMenu();
    bindEvents();
});

async function loadMenu() {
    try {
        const res = await fetch(`${API}/api/menu`);
        menu = await res.json();
        renderSizeOptions();
        renderToppingOptions();
    } catch (e) { toast('Failed to load menu', 'error'); }
}

function renderSizeOptions() {
    const sel = document.getElementById('size-select');
    sel.innerHTML = menu.sizes.map(s =>
        `<option value="${s.id}" data-name="${s.name}" data-price="${s.basePrice}">${s.name} — $${Number(s.basePrice).toFixed(2)}</option>`
    ).join('');
}

function renderToppingOptions() {
    const container = document.getElementById('toppings-container');
    container.innerHTML = menu.toppings.map(t =>
        `<label class="topping-item">
            <input type="checkbox" value="${t.id}" data-name="${t.name}">
            ${t.name} <span class="cat">(${t.category})</span>
        </label>`
    ).join('');
}

// === Events ===
function bindEvents() {
    document.getElementById('add-item-btn').addEventListener('click', addItem);
    document.getElementById('apply-promo-btn').addEventListener('click', applyPromo);
    document.getElementById('tip-mode').addEventListener('change', onTipModeChange);
    document.getElementById('tip-value').addEventListener('input', clampTipDecimals);
    document.getElementById('tip-value').addEventListener('change', () => { pendingIdempotencyKey = null; refreshPricing(); });
    document.getElementById('submit-btn').addEventListener('click', submitOrder);
    document.getElementById('view-desc-btn').addEventListener('click', toggleDraftDescription);
    document.getElementById('refresh-status-btn').addEventListener('click', refreshStatus);
    document.getElementById('edit-order-btn').addEventListener('click', editSubmittedOrder);
    document.getElementById('view-submitted-desc-btn').addEventListener('click', toggleSubmittedDescription);
    document.getElementById('cancel-order-btn').addEventListener('click', cancelOrder);
    document.getElementById('new-order-btn').addEventListener('click', startNewOrder);
}

// === Add item ===
function addItem() {
    const sel = document.getElementById('size-select');
    const opt = sel.options[sel.selectedIndex];
    const qty = parseInt(document.getElementById('quantity-input').value) || 1;
    const checked = document.querySelectorAll('#toppings-container input:checked');
    const toppingIds = new Set();
    const toppingNames = [];
    checked.forEach(cb => { toppingIds.add(Number(cb.value)); toppingNames.push(cb.dataset.name); });

    orderItems.push({
        sizeId: Number(sel.value),
        sizeName: opt.dataset.name,
        quantity: qty,
        toppingIds: [...toppingIds],
        toppingNames: [...toppingNames]
    });

    // reset form
    document.getElementById('quantity-input').value = 1;
    document.querySelectorAll('#toppings-container input:checked').forEach(cb => cb.checked = false);

    pendingIdempotencyKey = null;
    renderOrderItems();
    refreshPricing();
}

function updateItemQty(idx, delta) {
    const next = orderItems[idx].quantity + delta;
    if (next < 1) {
        removeItem(idx);
        return;
    }
    orderItems[idx].quantity = next;
    pendingIdempotencyKey = null;
    renderOrderItems();
    refreshPricing();
}

function removeItem(idx) {
    orderItems.splice(idx, 1);
    pendingIdempotencyKey = null;
    renderOrderItems();
    if (orderItems.length === 0) {
        document.getElementById('extras-section').style.display = 'none';
        document.getElementById('no-items-msg').style.display = '';
        document.getElementById('draft-description').style.display = 'none';
    }
    refreshPricing();
}

function renderOrderItems() {
    const container = document.getElementById('order-items');
    if (orderItems.length === 0) {
        container.innerHTML = '';
        document.getElementById('no-items-msg').style.display = '';
        document.getElementById('extras-section').style.display = 'none';
        return;
    }
    document.getElementById('no-items-msg').style.display = 'none';
    document.getElementById('extras-section').style.display = '';

    container.innerHTML = orderItems.map((item, i) => `
        <div class="order-item">
            <div class="item-info">
                <div class="item-title">${item.quantity}x ${item.sizeName} Pizza</div>
                <div class="item-toppings">${item.toppingNames.length ? item.toppingNames.join(', ') : 'Plain'}</div>
            </div>
            <div class="item-controls">
                <button class="qty-btn" onclick="updateItemQty(${i}, -1)">-</button>
                <button class="qty-btn" onclick="updateItemQty(${i}, 1)">+</button>
                <button class="item-remove" onclick="removeItem(${i})">✕</button>
            </div>
        </div>
    `).join('');
}

// === Promo ===
async function applyPromo() {
    const input = document.getElementById('promo-input');
    promoCode = input.value.trim() || null;
    pendingIdempotencyKey = null;
    const p = await refreshPricing();
    if (p && p.promoError) {
        toast(p.promoError, 'error');
    } else if (p && p.appliedPromoCode) {
        toast(`Promo ${p.appliedPromoCode} applied`, 'success');
    }
}

// === Tip ===
function clampTipDecimals(e) {
    const val = e.target.value;
    const dot = val.indexOf('.');
    if (dot !== -1 && val.length - dot - 1 > 2) {
        e.target.value = val.slice(0, dot + 3);
    }
}

function onTipModeChange() {
    const mode = document.getElementById('tip-mode').value;
    tipMode = mode || null;
    pendingIdempotencyKey = null;
    const group = document.getElementById('tip-value-group');
    const label = document.getElementById('tip-value-label');
    if (mode) {
        group.style.display = '';
        label.textContent = mode === 'DOLLAR' ? 'Amount ($)' : 'Percent (%)';
    } else {
        group.style.display = 'none';
        tipValue = null;
    }
    refreshPricing();
}

// === Pricing ===
async function refreshPricing() {
    if (orderItems.length === 0) return;

    const tv = document.getElementById('tip-value').value;
    tipValue = tipMode && tv ? Number(tv) : null;

    const body = {
        lineItems: orderItems.map(i => ({ sizeId: i.sizeId, quantity: i.quantity, toppingIds: i.toppingIds })),
        promoCode: promoCode,
        tipMode: tipMode,
        tipValue: tipValue
    };

    try {
        const res = await fetch(`${API}/api/orders/calculate`, {
            method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
        });
        if (!res.ok) {
            const err = await res.json();
            toast(err.message || 'Pricing error', 'error');
            return;
        }
        const p = await res.json();
        lastPricing = p;
        document.getElementById('p-subtotal').textContent = fmt(p.subtotal);
        document.getElementById('p-tax').textContent = fmt(p.tax);
        document.getElementById('p-total').textContent = fmt(p.total);

        const discountRow = document.getElementById('discount-row');
        if (p.discount > 0) {
            discountRow.style.display = '';
            document.getElementById('discount-label').textContent = `Discount (${p.appliedPromoCode})`;
            document.getElementById('p-discount').textContent = '-' + fmt(p.discount);
        } else { discountRow.style.display = 'none'; }

        const promoErrEl = document.getElementById('promo-error');
        if (p.promoError) {
            promoErrEl.textContent = p.promoError;
            promoErrEl.style.display = '';
        } else {
            promoErrEl.textContent = '';
            promoErrEl.style.display = 'none';
        }

        const tipRow = document.getElementById('tip-row');
        if (p.tip > 0) {
            tipRow.style.display = '';
            document.getElementById('p-tip').textContent = fmt(p.tip);
        } else { tipRow.style.display = 'none'; }

        return p;
    } catch (e) { toast('Failed to calculate pricing', 'error'); }
}

// === Submit / Update ===
async function submitOrder() {
    const isUpdate = currentOrderId !== null;
    if (!isUpdate && !pendingIdempotencyKey) {
        pendingIdempotencyKey = crypto.randomUUID();
    }

    const body = {
        lineItems: orderItems.map(i => ({ sizeId: i.sizeId, quantity: i.quantity, toppingIds: i.toppingIds })),
        promoCode: promoCode,
        tipMode: tipMode,
        tipValue: tipValue
    };
    if (!isUpdate) {
        body.idempotencyKey = pendingIdempotencyKey;
    }

    const url = isUpdate ? `${API}/api/orders/${currentOrderId}` : `${API}/api/orders`;
    const method = isUpdate ? 'PUT' : 'POST';

    try {
        const res = await fetch(url, {
            method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
        });
        if (!res.ok) {
            const err = await res.json();
            toast(err.message || 'Failed to submit order', 'error');
            return;
        }
        const order = await res.json();
        currentOrderId = order.id;
        pendingIdempotencyKey = null;
        showStatusSection(order);
        toast(isUpdate ? 'Order updated!' : 'Order submitted!', 'success');
    } catch (e) { toast('Network error — please retry', 'error'); }
}

// === Order Description ===
function toggleDraftDescription() {
    const box = document.getElementById('draft-description');
    if (box.style.display !== 'none') {
        box.style.display = 'none';
        return;
    }
    const lines = orderItems.map(i =>
        `- ${i.quantity}x ${i.sizeName} Pizza — ${i.toppingNames.length ? i.toppingNames.join(', ') : 'Plain'}`
    );
    if (lastPricing) {
        lines.push(`Subtotal: ${fmt(lastPricing.subtotal)}`);
        if (lastPricing.discount > 0) {
            lines.push(`Promo (${lastPricing.appliedPromoCode}): -${fmt(lastPricing.discount)}`);
        }
        lines.push(`Tax: ${fmt(lastPricing.tax)}`);
        if (lastPricing.tip > 0) {
            lines.push(`Tip: ${fmt(lastPricing.tip)}`);
        }
        lines.push(`Total: ${fmt(lastPricing.total)}`);
    }
    box.textContent = lines.join('\n');
    box.style.display = '';
}

async function toggleSubmittedDescription() {
    const box = document.getElementById('submitted-description');
    if (box.style.display !== 'none') {
        box.style.display = 'none';
        return;
    }
    if (!currentOrderId) return;
    try {
        const res = await fetch(`${API}/api/orders/${currentOrderId}/description`);
        if (!res.ok) {
            toast('Failed to load order description', 'error');
            return;
        }
        box.textContent = await res.text();
        box.style.display = '';
    } catch (e) { toast('Failed to load order description', 'error'); }
}

// === Edit Submitted Order ===
async function editSubmittedOrder() {
    if (!currentOrderId) return;
    try {
        const res = await fetch(`${API}/api/orders/${currentOrderId}`);
        if (!res.ok) {
            toast('Failed to load order for editing', 'error');
            return;
        }
        const order = await res.json();
        if (order.status !== 'NOT_STARTED') {
            updateStatusBadge(order.status);
            toast('Order can no longer be edited (kitchen already started)', 'error');
            return;
        }

        orderItems = order.lineItems.map(li => ({
            sizeId: li.sizeId,
            sizeName: li.sizeName,
            quantity: li.quantity,
            toppingIds: li.toppings.map(t => t.id),
            toppingNames: li.toppings.map(t => t.name)
        }));
        promoCode = order.promoCode || null;
        tipMode = order.tipMode || null;
        tipValue = order.tipValue ?? null;

        document.getElementById('promo-input').value = promoCode || '';
        document.getElementById('tip-mode').value = tipMode || '';
        const tipGroup = document.getElementById('tip-value-group');
        if (tipMode) {
            tipGroup.style.display = '';
            document.getElementById('tip-value-label').textContent =
                tipMode === 'DOLLAR' ? 'Amount ($)' : 'Percent (%)';
            document.getElementById('tip-value').value = tipValue ?? 0;
        } else {
            tipGroup.style.display = 'none';
            document.getElementById('tip-value').value = '0';
        }

        document.getElementById('submit-btn').textContent = 'Update Order';
        document.getElementById('status-section').style.display = 'none';
        document.getElementById('builder-section').style.display = '';
        document.getElementById('order-section').style.display = '';
        renderOrderItems();
        await refreshPricing();
    } catch (e) { toast('Network error loading order', 'error'); }
}

// === Status ===
function showStatusSection(order) {
    document.getElementById('builder-section').style.display = 'none';
    document.getElementById('order-section').style.display = 'none';
    document.getElementById('status-section').style.display = '';
    document.getElementById('submitted-description').style.display = 'none';
    document.getElementById('display-order-id').textContent = '#' + order.id;
    updateStatusBadge(order.status);

    const editable = order.status === 'NOT_STARTED';
    document.getElementById('edit-order-btn').style.display = editable ? '' : 'none';
    document.getElementById('cancel-order-btn').style.display = editable ? '' : 'none';
}

function updateStatusBadge(status) {
    const badge = document.getElementById('display-status');
    badge.textContent = status.replace(/_/g, ' ');
    badge.className = 'badge badge-' + status.toLowerCase().replace(/_/g, '-');
}

async function refreshStatus() {
    if (!currentOrderId) return;
    try {
        const res = await fetch(`${API}/api/orders/${currentOrderId}/status`);
        const data = await res.json();
        updateStatusBadge(data.status);
        const editable = data.status === 'NOT_STARTED';
        document.getElementById('edit-order-btn').style.display = editable ? '' : 'none';
        document.getElementById('cancel-order-btn').style.display = editable ? '' : 'none';
    } catch (e) { toast('Failed to refresh status', 'error'); }
}

async function cancelOrder() {
    if (!currentOrderId) return;
    try {
        const res = await fetch(`${API}/api/orders/${currentOrderId}`, { method: 'DELETE' });
        if (res.ok || res.status === 204) {
            updateStatusBadge('CANCELLED');
            document.getElementById('edit-order-btn').style.display = 'none';
            document.getElementById('cancel-order-btn').style.display = 'none';
            toast('Order cancelled', 'success');
        } else {
            const err = await res.json();
            toast(err.message || 'Cannot cancel', 'error');
        }
    } catch (e) { toast('Network error', 'error'); }
}

function startNewOrder() {
    currentOrderId = null;
    pendingIdempotencyKey = null;
    lastPricing = null;
    orderItems = [];
    promoCode = null;
    tipMode = null;
    tipValue = null;
    document.getElementById('submit-btn').textContent = 'Submit Order';
    document.getElementById('draft-description').style.display = 'none';
    document.getElementById('submitted-description').style.display = 'none';
    document.getElementById('promo-input').value = '';
    document.getElementById('promo-error').textContent = '';
    document.getElementById('promo-error').style.display = 'none';
    document.getElementById('tip-mode').value = '';
    document.getElementById('tip-value').value = '0';
    document.getElementById('tip-value-group').style.display = 'none';
    renderOrderItems();
    document.getElementById('builder-section').style.display = '';
    document.getElementById('order-section').style.display = '';
    document.getElementById('status-section').style.display = 'none';
}

// === Helpers ===
function fmt(n) { return '$' + Number(n).toFixed(2); }

function toast(msg, type = 'success') {
    const el = document.getElementById('toast');
    el.textContent = msg;
    el.className = 'toast toast-' + type;
    el.style.display = '';
    setTimeout(() => { el.style.display = 'none'; }, 3000);
}
