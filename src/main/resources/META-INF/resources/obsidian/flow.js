/* morphdom v2.7.4 — bundled, do not edit */
const morphdom = (() => {
    const module = { exports: {} };
    "use strict";var range,DOCUMENT_FRAGMENT_NODE=11;function morphAttrs(e,t){var n,r,o,a,i=t.attributes;if(t.nodeType!==DOCUMENT_FRAGMENT_NODE&&e.nodeType!==DOCUMENT_FRAGMENT_NODE){for(var d=i.length-1;d>=0;d--)r=(n=i[d]).name,o=n.namespaceURI,a=n.value,o?(r=n.localName||r,e.getAttributeNS(o,r)!==a&&("xmlns"===n.prefix&&(r=n.name),e.setAttributeNS(o,r,a))):e.getAttribute(r)!==a&&e.setAttribute(r,a);for(var l=e.attributes,c=l.length-1;c>=0;c--)r=(n=l[c]).name,(o=n.namespaceURI)?(r=n.localName||r,t.hasAttributeNS(o,r)||e.removeAttributeNS(o,r)):t.hasAttribute(r)||e.removeAttribute(r)}}var NS_XHTML="http://www.w3.org/1999/xhtml",doc="undefined"==typeof document?void 0:document,HAS_TEMPLATE_SUPPORT=!!doc&&"content"in doc.createElement("template"),HAS_RANGE_SUPPORT=!!doc&&doc.createRange&&"createContextualFragment"in doc.createRange();function createFragmentFromTemplate(e){var t=doc.createElement("template");return t.innerHTML=e,t.content.childNodes[0]}function createFragmentFromRange(e){return range||(range=doc.createRange()).selectNode(doc.body),range.createContextualFragment(e).childNodes[0]}function createFragmentFromWrap(e){var t=doc.createElement("body");return t.innerHTML=e,t.childNodes[0]}function toElement(e){return e=e.trim(),HAS_TEMPLATE_SUPPORT?createFragmentFromTemplate(e):HAS_RANGE_SUPPORT?createFragmentFromRange(e):createFragmentFromWrap(e)}function compareNodeNames(e,t){var n,r,o=e.nodeName,a=t.nodeName;return o===a||(n=o.charCodeAt(0),r=a.charCodeAt(0),n<=90&&r>=97?o===a.toUpperCase():r<=90&&n>=97&&a===o.toUpperCase())}function createElementNS(e,t){return t&&t!==NS_XHTML?doc.createElementNS(t,e):doc.createElement(e)}function moveChildren(e,t){for(var n=e.firstChild;n;){var r=n.nextSibling;t.appendChild(n),n=r}return t}function syncBooleanAttrProp(e,t,n){e[n]!==t[n]&&(e[n]=t[n],e[n]?e.setAttribute(n,""):e.removeAttribute(n))}var specialElHandlers={OPTION:function(e,t){var n=e.parentNode;if(n){var r=n.nodeName.toUpperCase();"OPTGROUP"===r&&(r=(n=n.parentNode)&&n.nodeName.toUpperCase()),"SELECT"!==r||n.hasAttribute("multiple")||(e.hasAttribute("selected")&&!t.selected&&(e.setAttribute("selected","selected"),e.removeAttribute("selected")),n.selectedIndex=-1)}syncBooleanAttrProp(e,t,"selected")},INPUT:function(e,t){syncBooleanAttrProp(e,t,"checked"),syncBooleanAttrProp(e,t,"disabled"),e.value!==t.value&&(e.value=t.value),t.hasAttribute("value")||e.removeAttribute("value")},TEXTAREA:function(e,t){var n=t.value;e.value!==n&&(e.value=n);var r=e.firstChild;if(r){var o=r.nodeValue;if(o==n||!n&&o==e.placeholder)return;r.nodeValue=n}},SELECT:function(e,t){if(!t.hasAttribute("multiple")){for(var n,r,o=-1,a=0,i=e.firstChild;i;)if("OPTGROUP"===(r=i.nodeName&&i.nodeName.toUpperCase()))i=(n=i).firstChild;else{if("OPTION"===r){if(i.hasAttribute("selected")){o=a;break}a++}!(i=i.nextSibling)&&n&&(i=n.nextSibling,n=null)}e.selectedIndex=o}}},ELEMENT_NODE=1,DOCUMENT_FRAGMENT_NODE$1=11,TEXT_NODE=3,COMMENT_NODE=8;function noop(){}function defaultGetNodeKey(e){if(e)return e.getAttribute&&e.getAttribute("id")||e.id}function morphdomFactory(e){return function(t,n,r){if(r||(r={}),"string"==typeof n)if("#document"===t.nodeName||"HTML"===t.nodeName||"BODY"===t.nodeName){var o=n;(n=doc.createElement("html")).innerHTML=o}else n=toElement(n);else n.nodeType===DOCUMENT_FRAGMENT_NODE$1&&(n=n.firstElementChild);var a=r.getNodeKey||defaultGetNodeKey,i=r.onBeforeNodeAdded||noop,d=r.onNodeAdded||noop,l=r.onBeforeElUpdated||noop,c=r.onElUpdated||noop,u=r.onBeforeNodeDiscarded||noop,N=r.onNodeDiscarded||noop,m=r.onBeforeElChildrenUpdated||noop,E=r.skipFromChildren||noop,f=r.addChild||function(e,t){return e.appendChild(t)},s=!0===r.childrenOnly,p=Object.create(null),T=[];function v(e){T.push(e)}function A(e,t){if(e.nodeType===ELEMENT_NODE)for(var n=e.firstChild;n;){var r=void 0;t&&(r=a(n))?v(r):(N(n),n.firstChild&&A(n,t)),n=n.nextSibling}}function h(e,t,n){!1!==u(e)&&(t&&t.removeChild(e),N(e),A(e,n))}function O(e){if(e.nodeType===ELEMENT_NODE||e.nodeType===DOCUMENT_FRAGMENT_NODE$1)for(var t=e.firstChild;t;){var n=a(t);n&&(p[n]=t),O(t),t=t.nextSibling}}function C(e){d(e);for(var t=e.firstChild;t;){var n=t.nextSibling,r=a(t);if(r){var o=p[r];o&&compareNodeNames(t,o)?(t.parentNode.replaceChild(o,t),b(o,t)):C(t)}else C(t);t=n}}function b(t,n,r){var o=a(n);if(o&&delete p[o],!r){var d=l(t,n);if(!1===d)return;if(d instanceof HTMLElement&&O(t=d),e(t,n),c(t),!1===m(t,n))return}"TEXTAREA"!==t.nodeName?function(e,t){var n,r,o,d,l,c=E(e,t),u=t.firstChild,N=e.firstChild;e:for(;u;){for(d=u.nextSibling,n=a(u);!c&&N;){if(o=N.nextSibling,u.isSameNode&&u.isSameNode(N)){u=d,N=o;continue e}r=a(N);var m=N.nodeType,s=void 0;if(m===u.nodeType&&(m===ELEMENT_NODE?(n?n!==r&&((l=p[n])?o===l?s=!1:(e.insertBefore(l,N),r?v(r):h(N,e,!0),r=a(N=l)):s=!1):r&&(s=!1),(s=!1!==s&&compareNodeNames(N,u))&&b(N,u)):m!==TEXT_NODE&&m!=COMMENT_NODE||(s=!0,N.nodeValue!==u.nodeValue&&(N.nodeValue=u.nodeValue))),s){u=d,N=o;continue e}r?v(r):h(N,e,!0),N=o}if(n&&(l=p[n])&&compareNodeNames(l,u))c||f(e,l),b(l,u);else{var T=i(u);!1!==T&&(T&&(u=T),u.actualize&&(u=u.actualize(e.ownerDocument||doc)),f(e,u),C(u))}u=d,N=o}!function(e,t,n){for(;t;){var r=t.nextSibling;(n=a(t))?v(n):h(t,e,!0),t=r}}(e,N,r);var A=specialElHandlers[e.nodeName];A&&A(e,t)}(t,n):specialElHandlers.TEXTAREA(t,n)}O(t);var g=t,_=g.nodeType,M=n.nodeType;if(!s)if(_===ELEMENT_NODE)M===ELEMENT_NODE?compareNodeNames(t,n)||(N(t),g=moveChildren(t,createElementNS(n.nodeName,n.namespaceURI))):g=n;else if(_===TEXT_NODE||_===COMMENT_NODE){if(M===_)return g.nodeValue!==n.nodeValue&&(g.nodeValue=n.nodeValue),g;g=n}if(g===n)N(t);else{if(n.isSameNode&&n.isSameNode(g))return;if(b(g,n,s),T)for(var S=0,D=T.length;S<D;S++){var R=p[T[S]];R&&h(R,R.parentNode,!1)}}return!s&&g!==t&&t.parentNode&&(g.actualize&&(g=g.actualize(t.ownerDocument||doc)),t.parentNode.replaceChild(g,t)),g}}var morphdom=morphdomFactory(morphAttrs);module.exports=morphdom;
    return module.exports;
})();

/**
 * Obsidian Flow - Client-side navigation system
 *
 * Intercepts link clicks and form submissions to perform AJAX-based
 * navigation without full page reloads. Automatically re-initializes
 * LiveComponents after each navigation.
 *
 * @class ObsidianFlow
 * @version 1.3.0
 *
 * Features:
 * - Intercepts <a> clicks and diffs <body> via morphdom (bundled)
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
    _onClick(e) {
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
    _onSubmit(e) {
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
    _onMouseover(e) {
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
    async navigate(url, { pushState = true, method = 'GET', body = null, timeout, restoreScrollY } = {}) {
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
    _applyPage(doc, url, pushState, restoreScrollY) {
        // Capture scroll position before morphdom mutates the DOM
        const currentScrollY = window.scrollY;

        document.title = doc.title;

        this._applyHead(doc.head);

        // Diff the body — only mutate nodes that actually changed
        morphdom(document.body, doc.body, {
            onBeforeElUpdated(from, to) {
                // Skip identical nodes — no DOM mutation needed
                if (from.isEqualNode(to)) return false;
                return true;
            }
        });

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