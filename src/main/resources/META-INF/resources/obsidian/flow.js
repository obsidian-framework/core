/**
 * Obsidian Flow - Client-side navigation system
 *
 * Intercepts link clicks and form submissions to perform AJAX-based
 * navigation without full page reloads. Automatically re-initializes
 * LiveComponents after each navigation.
 *
 * @class ObsidianFlow
 * @version 1.2.0
 *
 * Features:
 * - Intercepts <a> clicks and replaces <body> via fetch
 * - Intercepts <form> submissions (GET and POST)
 * - Updates <head> meta/link tags on navigation
 * - Handles browser back/forward (popstate) with scroll restoration
 * - Opt-out via data-flow="false"
 * - Per-link timeout override via data-flow-timeout
 * - Loading progress bar (color via --obsidian-flow-color)
 * - Fetch timeout (8s default) with fallback to full navigation
 * - Prefetch on hover for near-zero perceived latency
 * - View Transitions API support (Chrome 111+)
 * - Re-initializes ObsidianComponents after navigation
 * - Re-executes inline <script> tags from new page
 * - Scroll reset on navigation, scroll restoration on back/forward
 * - obsidian:flow:load event after each navigation
 * - obsidian:flow:redirect event on POST → GET redirects
 */
class ObsidianFlow
{
    constructor() {
        /** @type {boolean} Whether a navigation is in progress */
        this.navigating = false;

        /** @type {string} Current URL */
        this.currentUrl = location.href;

        /** @type {HTMLElement|null} Progress bar element */
        this.progressBar = null;

        /** @type {number|null} Progress bar timeout */
        this.progressTimeout = null;

        /** @type {number} Default fetch timeout in milliseconds */
        this.timeout = 8000;

        /** @type {Set<string>} URLs already prefetched — prevents duplicate fetches */
        this._prefetched = new Set();

        this._createProgressBar();
        this._attachListeners();

        // Save initial state so back button works from first page
        history.replaceState({ obsidianFlow: true, url: location.href, scrollY: 0 }, '', location.href);
    }

    // -------------------------------------------------------------------------
    // Progress bar
    // -------------------------------------------------------------------------

    /**
     * Creates and injects the loading progress bar into the DOM.
     * @private
     */
    _createProgressBar()
    {
        this.progressBar = document.createElement('div');
        this.progressBar.id = 'obsidian-flow-progress';
        this.progressBar.style.cssText = [
            'position: fixed',
            'top: 0',
            'left: 0',
            'width: 0%',
            'height: 3px',
            'background: var(--obsidian-flow-color, #e11d48)',
            'z-index: 99999',
            'transition: width 0.2s ease, opacity 0.3s ease',
            'opacity: 0',
            'pointer-events: none'
        ].join(';');
        document.head.appendChild(this.progressBar);
    }

    /**
     * Shows and animates the progress bar.
     * @private
     */
    _progressStart() {
        clearTimeout(this.progressTimeout);
        this.progressBar.style.opacity = '1';
        this.progressBar.style.width = '0%';
        requestAnimationFrame(() => {
            this.progressBar.style.width = '70%';
        });
    }

    /**
     * Completes and hides the progress bar.
     * @private
     */
    _progressDone() {
        this.progressBar.style.width = '100%';
        this.progressTimeout = setTimeout(() => {
            this.progressBar.style.opacity = '0';
            setTimeout(() => {
                this.progressBar.style.width = '0%';
            }, 300);
        }, 200);
    }

    // -------------------------------------------------------------------------
    // Event listeners
    // -------------------------------------------------------------------------

    /**
     * Attaches global click, submit, popstate and mouseover listeners.
     * mouseover is used (vs mouseenter) because mouseenter doesn't bubble —
     * delegation via capture mode works but is non-obvious. mouseover + Set dedup
     * achieves the same result without the footgun.
     * @private
     */
    _attachListeners() {
        document.addEventListener('click',     (e) => this._onClick(e));
        document.addEventListener('submit',    (e) => this._onSubmit(e));
        document.addEventListener('mouseover', (e) => this._onMouseover(e));
        window.addEventListener('popstate',    (e) => this._onPopstate(e));
    }

    /**
     * Handles click events on anchor tags.
     * @private
     * @param {MouseEvent} e
     */
    _onClick(e)
    {
        // Only left clicks, no modifier keys
        if (e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;

        const a = e.target.closest('a');
        if (!a) return;

        // Opt-out
        if (a.dataset.flow === 'false') return;

        // Only same-origin navigation
        if (!a.href || !a.href.startsWith(location.origin)) return;

        // Skip anchors (#) on the same page
        const url = new URL(a.href);
        if (url.hash && url.pathname === location.pathname) return;
        if (a.target && a.target !== '_self') return;

        e.preventDefault();

        const timeout = a.dataset.flowTimeout ? parseInt(a.dataset.flowTimeout, 10) : undefined;
        this.navigate(a.href, { timeout });
    }

    /**
     * Handles form submissions.
     * Supports GET (query string) and POST (FormData body).
     * Captures the submitter button's name/value if present.
     * @private
     * @param {SubmitEvent} e
     */
    _onSubmit(e)
    {
        const form = e.target.closest('form');
        if (!form) return;

        // Opt-out
        if (form.dataset.flow === 'false') return;

        // Only same-origin
        const action = form.action || location.href;
        if (!action.startsWith(location.origin)) return;

        // Skip forms handled by LiveComponents
        if (form.hasAttribute('live:submit')) return;

        e.preventDefault();

        const method = (form.method || 'get').toLowerCase();
        const formData = new FormData(form);

        // Capture submitter button value (e.g. <button name="action" value="delete">)
        const submitter = e.submitter;
        if (submitter && submitter.name && submitter.value) {
            formData.set(submitter.name, submitter.value);
        }

        if (method === 'get') {
            const params = new URLSearchParams(formData).toString();
            const url = action.split('?')[0] + (params ? '?' + params : '');
            this.navigate(url);
        } else {
            this.navigate(action, { method: 'POST', body: formData });
        }
    }

    /**
     * Prefetches a page on link hover to reduce perceived navigation latency.
     * Uses the browser's cache — the actual navigate() call will hit cache if
     * the prefetch completes first.
     * @private
     * @param {MouseEvent} e
     */
    _onMouseover(e)
    {
        const a = e.target.closest('a');
        if (!a || a.dataset.flow === 'false') return;
        if (!a.href || !a.href.startsWith(location.origin)) return;

        const url = new URL(a.href);
        if (url.hash && url.pathname === location.pathname) return;
        if (a.target && a.target !== '_self') return;
        if (this._prefetched.has(a.href) || a.href === this.currentUrl) return;

        // Mark immediately — mouseover fires continuously, this prevents duplicate fetches
        this._prefetched.add(a.href);

        fetch(a.href, {
            method: 'GET',
            headers: {
                'X-Obsidian-Flow': '1',
                'X-Obsidian-Prefetch': '1',
                'X-Requested-With': 'XMLHttpRequest'
            },
            credentials: 'same-origin'
        }).catch(() => {
            // Prefetch failures are silent — navigate() will retry on click
            // Remove from cache so a future hover can retry
            this._prefetched.delete(a.href);
        });
    }

    /**
     * Handles browser back/forward navigation with scroll restoration.
     * @private
     * @param {PopStateEvent} e
     */
    _onPopstate(e) {
        if (e.state && e.state.obsidianFlow) {
            this.navigate(e.state.url, {
                pushState: false,
                restoreScrollY: e.state.scrollY ?? 0
            });
        }
    }

    // -------------------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------------------

    /**
     * Navigates to a URL by fetching the page and replacing the document.
     *
     * @param {string} url - Target URL
     * @param {Object} options
     * @param {boolean}  [options.pushState=true]    - Whether to push to history
     * @param {string}   [options.method='GET']      - HTTP method
     * @param {FormData} [options.body]              - Request body for POST
     * @param {number}   [options.timeout]           - Override default fetch timeout (ms)
     * @param {number}   [options.restoreScrollY]    - Scroll position to restore (back/forward)
     * @returns {Promise<void>}
     */
    async navigate(url, { pushState = true, method = 'GET', body = null, timeout, restoreScrollY } = {})
    {
        if (this.navigating) return;
        if (method === 'GET' && url === this.currentUrl && pushState) return;

        this.navigating = true;
        this._progressStart();

        const effectiveTimeout = timeout ?? this.timeout;
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), effectiveTimeout);

        try {
            const res = await fetch(url, {
                method,
                body,
                headers: {
                    'X-Obsidian-Flow': '1',
                    'X-Requested-With': 'XMLHttpRequest'
                },
                credentials: 'same-origin',
                signal: controller.signal
            });

            clearTimeout(timeoutId);

            if (!res.ok) {
                window.location.href = url;
                return;
            }

            // Detect POST → GET redirects and notify listeners
            const finalUrl = res.url || url;
            if (method === 'POST' && finalUrl !== url) {
                window.dispatchEvent(new CustomEvent('obsidian:flow:redirect', {
                    detail: { from: url, to: finalUrl }
                }));
            }

            const html = await res.text();
            const doc = new DOMParser().parseFromString(html, 'text/html');

            const apply = () => this._applyPage(doc, finalUrl, pushState, restoreScrollY);

            if (document.startViewTransition) {
                document.startViewTransition(apply);
            } else {
                apply();
            }

        } catch (err) {
            clearTimeout(timeoutId);
            if (err.name === 'AbortError') {
                console.warn('[ObsidianFlow] Request timed out, falling back to full navigation');
            } else {
                console.warn('[ObsidianFlow] Navigation failed, falling back:', err);
            }
            window.location.href = url;
        } finally {
            this.navigating = false;
            this._progressDone();
        }
    }

    /**
     * Applies the fetched page to the current document.
     * Updates <head> meta/link tags, title, body, history,
     * re-runs scripts and re-initializes LiveComponents.
     *
     * @private
     * @param {Document} doc            - Parsed document from fetched page
     * @param {string}   url            - URL that was navigated to
     * @param {boolean}  pushState      - Whether to push to history
     * @param {number}   [restoreScrollY] - Scroll position to restore (back/forward)
     */
    _applyPage(doc, url, pushState, restoreScrollY)
    {
        // Capture scroll position before any DOM mutation — body replacement can reset it
        const currentScrollY = window.scrollY;

        document.title = doc.title;

        this._applyHead(doc.head);

        document.body.innerHTML = doc.body.innerHTML;

        if (pushState) {
            // Save scroll position of the page we're leaving
            history.replaceState(
                { ...history.state, scrollY: currentScrollY },
                '',
                this.currentUrl
            );
            history.pushState({ obsidianFlow: true, url, scrollY: 0 }, '', url);
        }

        if (restoreScrollY !== undefined) {
            requestAnimationFrame(() => window.scrollTo(0, restoreScrollY));
        } else {
            window.scrollTo(0, 0);
        }

        this.currentUrl = url;

        this._rerunScripts(document.body);
        this._reinitLiveComponents();

        window.dispatchEvent(new CustomEvent('obsidian:flow:load', { detail: { url } }));
    }

    /**
     * Syncs <head> meta and link tags from the fetched page.
     * Adds new tags, removes stale ones. Skips scripts to avoid re-loading JS.
     * Compares by semantic key (name, property, rel + href) rather than outerHTML
     * to avoid false mismatches caused by attribute ordering differences.
     *
     * @private
     * @param {HTMLHeadElement} newHead - <head> from fetched document
     */
    _applyHead(newHead) {
        /**
         * Returns a stable string key for a <meta> or <link> element.
         * @param {Element} el
         * @returns {string}
         */
        const key = (el) => {
            if (el.tagName === 'META') {
                return `meta:${el.getAttribute('name') || el.getAttribute('property') || el.getAttribute('http-equiv') || ''}:${el.getAttribute('content') || ''}`;
            }
            if (el.tagName === 'LINK') {
                return `link:${el.getAttribute('rel') || ''}:${el.getAttribute('href') || ''}`;
            }
            return el.outerHTML;
        };

        const current = new Map();
        document.head.querySelectorAll('meta, link').forEach(el => {
            current.set(key(el), el);
        });

        const incoming = new Set();
        newHead.querySelectorAll('meta, link').forEach(el => {
            const k = key(el);
            incoming.add(k);
            if (!current.has(k)) {
                document.head.appendChild(el.cloneNode(true));
            }
        });

        // Remove stale elements not present in new head
        current.forEach((el, k) => {
            if (!incoming.has(k)) {
                el.remove();
            }
        });
    }

    /**
     * Re-executes <script> tags injected into the body after DOM replacement.
     * The browser does not auto-execute scripts set via innerHTML.
     * Skips ObsidianFlow and LiveComponents scripts — already loaded once.
     *
     * @private
     * @param {Element} container - Container element to scan for scripts
     */
    _rerunScripts(container) {
        const SKIP = [/\/flow\.js(\?|$)/, /\/livecomponents\.js(\?|$)/];

        container.querySelectorAll('script').forEach(oldScript => {
            if (oldScript.src && SKIP.some(re => re.test(oldScript.src))) return;

            const newScript = document.createElement('script');
            Array.from(oldScript.attributes).forEach(attr => {
                newScript.setAttribute(attr.name, attr.value);
            });
            newScript.textContent = oldScript.textContent;
            oldScript.parentNode.replaceChild(newScript, oldScript);
        });
    }

    /**
     * Re-initializes the ObsidianComponents instance after navigation.
     * Clears existing component registry and re-discovers components in new body.
     *
     * @private
     */
    _reinitLiveComponents() {
        if (window.ObsidianComponents) {
            window.ObsidianComponents.components.clear();
            window.ObsidianComponents.init();
        }
    }
}

// -------------------------------------------------------------------------
// Bootstrap
// -------------------------------------------------------------------------

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        window.ObsidianFlow = new ObsidianFlow();
    });
} else {
    window.ObsidianFlow = new ObsidianFlow();
}