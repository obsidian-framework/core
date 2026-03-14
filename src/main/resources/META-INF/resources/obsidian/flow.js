/**
 * Obsidian Flow - Client-side navigation system
 *
 * Intercepts link clicks and form submissions to perform AJAX-based
 * navigation without full page reloads. Automatically re-initializes
 * LiveComponents after each navigation.
 *
 * @class ObsidianFlow
 * @version 1.0.0
 *
 * Features:
 * - Intercepts <a> clicks and replaces <body> via fetch
 * - Handles browser back/forward (popstate)
 * - Opt-out via data-flow="false"
 * - Loading progress bar
 * - Re-initializes ObsidianComponents after navigation
 * - Re-executes inline <script> tags from new page
 * - Scroll reset on navigation
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

        this._createProgressBar();
        this._attachListeners();

        // Save initial state so back button works from first page
        history.replaceState({ obsidianFlow: true, url: location.href }, '', location.href);
    }

    // -------------------------------------------------------------------------
    // Progress bar
    // -------------------------------------------------------------------------

    /**
     * Creates and injects the loading progress bar into the DOM.
     * @private
     */
    _createProgressBar() {
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
        // Small delay then jump to 70% to give feedback
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
     * Attaches global click and popstate listeners.
     * @private
     */
    _attachListeners() {
        // Intercept link clicks
        document.addEventListener('click', (e) => this._onClick(e));

        // Handle browser back/forward
        window.addEventListener('popstate', (e) => this._onPopstate(e));
    }

    /**
     * Handles click events on anchor tags.
     * @private
     * @param {MouseEvent} e
     */
    _onClick(e) {
        // Only left clicks, no modifier keys
        if (e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;

        const a = e.target.closest('a');
        if (!a) return;

        // Opt-out
        if (a.dataset.flow === 'false') return;

        // Only same-origin navigation
        if (!a.href || !a.href.startsWith(location.origin)) return;

        // Skip anchors (#), mailto:, javascript:, etc.
        const url = new URL(a.href);
        if (url.hash && url.pathname === location.pathname) return;
        if (a.target && a.target !== '_self') return;

        e.preventDefault();
        this.navigate(a.href);
    }

    /**
     * Handles browser back/forward navigation.
     * @private
     * @param {PopStateEvent} e
     */
    _onPopstate(e) {
        if (e.state && e.state.obsidianFlow) {
            this.navigate(e.state.url, { pushState: false });
        }
    }

    // -------------------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------------------

    /**
     * Navigates to a URL by fetching the page and replacing the body.
     *
     * @param {string} url - Target URL
     * @param {Object} options
     * @param {boolean} [options.pushState=true] - Whether to push to history
     * @returns {Promise<void>}
     */
    async navigate(url, { pushState = true } = {}) {
        if (this.navigating) return;
        if (url === this.currentUrl && pushState) return;

        this.navigating = true;
        this._progressStart();

        try {
            const res = await fetch(url, {
                headers: {
                    'X-Obsidian-Flow': '1',
                    'X-Requested-With': 'XMLHttpRequest'
                },
                credentials: 'same-origin'
            });

            if (!res.ok) {
                // On error, fallback to regular navigation
                window.location.href = url;
                return;
            }

            const html = await res.text();
            const doc = new DOMParser().parseFromString(html, 'text/html');

            this._applyPage(doc, url, pushState);

        } catch (err) {
            // Network error — fallback to full navigation
            console.warn('[ObsidianFlow] Navigation failed, falling back:', err);
            window.location.href = url;
        } finally {
            this.navigating = false;
            this._progressDone();
        }
    }

    /**
     * Applies the fetched page to the current document.
     * Updates title, body, pushes history state, re-runs scripts,
     * and re-initializes LiveComponents.
     *
     * @private
     * @param {Document} doc - Parsed document from fetched page
     * @param {string} url - URL that was navigated to
     * @param {boolean} pushState - Whether to push to history
     */
    _applyPage(doc, url, pushState) {
        // Update <title>
        document.title = doc.title;

        // Update <body>
        document.body.innerHTML = doc.body.innerHTML;

        // Scroll to top
        window.scrollTo(0, 0);

        // Push to history
        if (pushState) {
            history.pushState({ obsidianFlow: true, url }, '', url);
        }
        this.currentUrl = url;

        // Re-execute inline scripts from new body
        this._rerunScripts(document.body);

        // Re-initialize LiveComponents
        this._reinitLiveComponents();

        // Dispatch navigation event (useful for analytics, etc.)
        window.dispatchEvent(new CustomEvent('obsidian:flow:load', { detail: { url } }));
    }

    /**
     * Re-executes <script> tags injected into the body after DOM replacement.
     * The browser does not auto-execute scripts set via innerHTML.
     *
     * @private
     * @param {Element} container - Container element to scan for scripts
     */
    _rerunScripts(container) {
        const scripts = container.querySelectorAll('script');
        scripts.forEach(oldScript => {
            // Skip LiveComponents script and Flow itself — already loaded
            if (oldScript.src && (
                oldScript.src.includes('livecomponents.js') ||
                oldScript.src.includes('obsidian-flow.js')
            )) return;

            const newScript = document.createElement('script');

            // Copy attributes
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
            // Reset component registry to avoid stale references
            window.ObsidianComponents.components.clear();
            window.ObsidianComponents.init();
        }
    }
}

// -------------------------------------------------------------------------
// Bootstrap
// -------------------------------------------------------------------------

/**
 * Initializes ObsidianFlow when DOM is ready.
 * Creates global instance accessible via window.ObsidianFlow.
 * Must be loaded AFTER livecomponents.js.
 */
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        window.ObsidianFlow = new ObsidianFlow();
    });
} else {
    window.ObsidianFlow = new ObsidianFlow();
}