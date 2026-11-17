/**
 * VaultCore UI Script
 * Soft-light glassmorphic theme with living motion & accessible controls.
 * Offline-first, vanilla JavaScript (no external libraries or CDNs).
 */

(function () {
  'use strict';

  // --- Stale Key Cleanup & Storage Constants ---
  try {
    localStorage.removeItem('vaultcore-theme');
  } catch (e) {
    // Ignore storage restriction if any
  }

  const USER_STORAGE_KEY = 'vaultcore-selected-user';

  // Check reduced motion preference
  function prefersReducedMotion() {
    return window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }

  // --- Header Elevation on Scroll (Part 3) ---
  function initHeaderScroll() {
    const header = document.querySelector('.header');
    if (!header) return;

    const onScroll = function () {
      if (window.scrollY > 12) {
        header.classList.add('scrolled');
      } else {
        header.classList.remove('scrolled');
      }
    };

    window.addEventListener('scroll', onScroll, { passive: true });
    onScroll();
  }

  // --- Motion Pausing on Tab Inactivity (Part 4) ---
  function initVisibilityMotionHandler() {
    document.addEventListener('visibilitychange', function () {
      if (document.hidden) {
        document.body.classList.add('motion-paused');
      } else {
        document.body.classList.remove('motion-paused');
      }
    });
  }

  // --- Count-Up Animated Numbers (Part 4) ---
  function initCountUpNumbers() {
    if (prefersReducedMotion()) return; // Skip animation if reduced motion preferred

    const elements = document.querySelectorAll('.count-up');
    elements.forEach(function (el) {
      const targetStr = el.getAttribute('data-target');
      if (targetStr === null || targetStr === '') return;

      const targetVal = parseInt(targetStr, 10);
      if (isNaN(targetVal)) return;

      const duration = 700; // ms
      let startTime = null;
      const startVal = 0;

      // Quartic ease out
      function easeOutQuart(x) {
        return 1 - Math.pow(1 - x, 4);
      }

      function step(timestamp) {
        if (!startTime) startTime = timestamp;
        const progress = Math.min((timestamp - startTime) / duration, 1);
        const current = Math.round(startVal + (targetVal - startVal) * easeOutQuart(progress));
        el.textContent = current.toLocaleString();

        if (progress < 1) {
          window.requestAnimationFrame(step);
        } else {
          el.textContent = targetVal.toLocaleString();
        }
      }

      window.requestAnimationFrame(step);
    });
  }

  // --- User Selector Management ---
  function initUserSelector() {
    const selector = document.getElementById('globalUserSelector');
    if (!selector) return;

    const urlParams = new URLSearchParams(window.location.search);
    const queryEmail = urlParams.get('email');
    const savedEmail = localStorage.getItem(USER_STORAGE_KEY);

    if (queryEmail) {
      selector.value = queryEmail;
      localStorage.setItem(USER_STORAGE_KEY, queryEmail);
    } else if (savedEmail && selector.querySelector(`option[value="${savedEmail}"]`)) {
      selector.value = savedEmail;
      const currentPath = window.location.pathname;
      if (currentPath === '/' || currentPath === '/history' || currentPath === '/transactions/new') {
        urlParams.set('email', savedEmail);
        window.location.search = urlParams.toString();
        return;
      }
    } else if (selector.value) {
      localStorage.setItem(USER_STORAGE_KEY, selector.value);
    }

    selector.addEventListener('change', function () {
      const selectedEmail = this.value;
      localStorage.setItem(USER_STORAGE_KEY, selectedEmail);
      const params = new URLSearchParams(window.location.search);
      params.set('email', selectedEmail);
      if (params.has('page')) {
        params.set('page', '0');
      }
      window.location.search = params.toString();
    });
  }

  // --- New Transaction Form Handling ---
  function initTransactionForm() {
    const form = document.getElementById('transactionForm');
    if (!form) return;

    const idempotencyInput = document.getElementById('idempotencyKey');
    const keyDisplay = document.getElementById('keyDisplay');
    const submitBtn = document.getElementById('submitTxBtn');
    const outcomeAlert = document.getElementById('outcomeAlert');

    // Generate UUID for idempotency key
    function generateIdempotencyKey() {
      const newKey = (typeof crypto !== 'undefined' && crypto.randomUUID)
        ? crypto.randomUUID()
        : 'key-' + Date.now() + '-' + Math.random().toString(36).substring(2, 9);
      if (idempotencyInput) idempotencyInput.value = newKey;
      if (keyDisplay) keyDisplay.textContent = newKey;
      return newKey;
    }

    generateIdempotencyKey();

    const resetKeyBtn = document.getElementById('regenerateKeyBtn');
    if (resetKeyBtn) {
      resetKeyBtn.addEventListener('click', function (e) {
        e.preventDefault();
        generateIdempotencyKey();
      });
    }

    // Pre-select user if present in localStorage
    const formUserSelect = document.getElementById('userEmail');
    const savedEmail = localStorage.getItem(USER_STORAGE_KEY);
    if (formUserSelect && savedEmail && formUserSelect.querySelector(`option[value="${savedEmail}"]`)) {
      formUserSelect.value = savedEmail;
    }

    form.addEventListener('submit', async function (e) {
      e.preventDefault();
      clearOutcome();
      clearInlineErrors();

      // Read selected radio or select for type
      let type = '';
      const checkedRadio = form.querySelector('input[name="type"]:checked');
      if (checkedRadio) {
        type = checkedRadio.value;
      } else {
        const typeSelect = document.getElementById('transactionType');
        if (typeSelect) type = typeSelect.value;
      }

      const email = document.getElementById('userEmail').value.trim();
      const assetCode = document.getElementById('assetCode').value;
      const amountStr = document.getElementById('amount').value.trim();
      const idempotencyKey = idempotencyInput.value.trim();

      let hasError = false;
      if (!type) {
        showInlineError('transactionType', 'Please select a transaction type');
        hasError = true;
      }
      if (!email) {
        showInlineError('userEmail', 'User account is required');
        hasError = true;
      } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        showInlineError('userEmail', 'Valid email address is required');
        hasError = true;
      }
      if (!assetCode) {
        showInlineError('assetCode', 'Asset currency is required');
        hasError = true;
      }
      const amount = parseInt(amountStr, 10);
      if (!amountStr || isNaN(amount) || amount <= 0) {
        showInlineError('amount', 'Amount must be a whole positive number greater than 0');
        hasError = true;
      }
      if (!idempotencyKey) {
        showInlineError('idempotencyKey', 'Idempotency key is required');
        hasError = true;
      }

      if (hasError) return;

      setFormLoading(true);

      const payload = {
        type: type,
        email: email,
        assetCode: assetCode,
        amount: amount,
        idempotencyKey: idempotencyKey
      };

      try {
        const response = await fetch('/api/v1/transactions', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Accept': 'application/json'
          },
          body: JSON.stringify(payload)
        });

        const data = await response.json();

        if (response.status === 201) {
          showOutcome(
            'alert-success',
            'Transaction Completed Successfully',
            `Type: ${escapeHtml(data.transactionType || type)} | Amount: ${Number(data.amount).toLocaleString()} | New Balance: ${Number(data.balanceAfter).toLocaleString()} | Tx ID: ${escapeHtml(data.transactionId)}`
          );
          generateIdempotencyKey();
          document.getElementById('amount').value = '';
        } else if (response.status === 200) {
          showOutcome(
            'alert-replay',
            'Idempotent Replay (Previously Processed)',
            `This request was already processed, showing the original result. Amount: ${Number(data.amount).toLocaleString()} | Historical Balance: ${Number(data.balanceAfter).toLocaleString()} | Tx ID: ${escapeHtml(data.transactionId)}`
          );
          generateIdempotencyKey();
        } else {
          const msg = data.message || data.error || 'Transaction failed. Please check inputs and try again.';
          showOutcome('alert-error', 'Transaction Failed', msg);
          generateIdempotencyKey();
        }
      } catch (networkErr) {
        showOutcome(
          'alert-error',
          'Network or Connection Interruption',
          'Could not contact the server. Your idempotency key has been preserved. You can safely retry without duplicate side effects.'
        );
      } finally {
        setFormLoading(false);
      }
    });

    function setFormLoading(isLoading) {
      if (submitBtn) {
        submitBtn.disabled = isLoading;
        if (isLoading) {
          submitBtn.innerHTML = '<span class="btn-spinner" aria-hidden="true"></span> Processing...';
        } else {
          submitBtn.innerHTML = 'Submit Transaction';
        }
      }
    }

    function showOutcome(cssClass, title, message) {
      if (!outcomeAlert) return;
      outcomeAlert.className = `alert ${cssClass} glass`;
      outcomeAlert.style.display = 'block';
      outcomeAlert.innerHTML = `<div class="alert-title">${escapeHtml(title)}</div><div>${message}</div>`;
      outcomeAlert.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }

    function clearOutcome() {
      if (!outcomeAlert) return;
      outcomeAlert.className = 'alert';
      outcomeAlert.style.display = 'none';
      outcomeAlert.innerHTML = '';
    }

    function showInlineError(fieldId, errorMsg) {
      const errEl = document.getElementById(fieldId + 'Error');
      if (errEl) {
        errEl.textContent = errorMsg;
      }
    }

    function clearInlineErrors() {
      const errEls = document.querySelectorAll('.form-error-inline');
      errEls.forEach(el => el.textContent = '');
    }
  }

  // --- History Local Timestamps Formatting ---
  function initHistoryTimestamps() {
    const timeCells = document.querySelectorAll('.tx-timestamp');
    timeCells.forEach(cell => {
      const rawIso = cell.getAttribute('data-utc');
      if (rawIso) {
        try {
          const date = new Date(rawIso);
          if (!isNaN(date.getTime())) {
            cell.textContent = date.toLocaleString(undefined, {
              year: 'numeric',
              month: 'short',
              day: 'numeric',
              hour: '2-digit',
              minute: '2-digit',
              second: '2-digit'
            });
          }
        } catch (e) {
          // keep fallback server string
        }
      }
    });
  }

  function escapeHtml(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  // DOM ready initialization
  document.addEventListener('DOMContentLoaded', function () {
    initHeaderScroll();
    initVisibilityMotionHandler();
    initUserSelector();
    initTransactionForm();
    initHistoryTimestamps();
    initCountUpNumbers();
  });
})();
