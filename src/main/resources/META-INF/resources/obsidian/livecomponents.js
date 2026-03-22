/**
 * Morphdom - Fast DOM diffing/patching (CDN inline)
 * @see https://github.com/patrick-steele-idem/morphdom
 */
!function(e,t){"object"==typeof exports&&"undefined"!=typeof module?module.exports=t():"function"==typeof define&&define.amd?define(t):(e="undefined"!=typeof globalThis?globalThis:e||self).morphdom=t()}(this,(function(){"use strict";var e,t,n,o,r,i="http://www.w3.org/1999/xhtml",a=typeof document>"u"?void 0:document,l=!!a&&"content"in a.createElement("template"),s=!!a&&a.createRange&&"createContextualFragment"in a.createRange();function c(e){var t=a.createElement("template");return t.innerHTML=e,t.content.childNodes[0]}function u(e){return(l?c:s?function(e){return n||(n=a.createRange()).selectNode(a.body),n.createContextualFragment(e).childNodes[0]}:function(e){return(o||(o=a.createElement("body"))).innerHTML=e,o.childNodes[0]})(e)}function d(e,t){var n,o,r=e.nodeName,i=t.nodeName;return r===i||(n=r.charCodeAt(0),o=i.charCodeAt(0),n<=90&&o>=97?r===i.toUpperCase():o<=90&&n>=97&&i===r.toUpperCase())}function f(e,t,n){e[n]!==t[n]&&(e[n]=t[n],e[n]?e.setAttribute(n,""):e.removeAttribute(n))}var p={OPTION:function(e,t){var n=e.parentNode;if(n){var o=n.nodeName.toUpperCase();"OPTGROUP"===o&&(o=(n=n.parentNode)&&n.nodeName.toUpperCase()),"SELECT"!==o||n.hasAttribute("multiple")||(e.hasAttribute("selected")&&!t.selected&&(e.setAttribute("selected","selected"),e.removeAttribute("selected")),n.selectedIndex=-1)}f(e,t,"selected")},INPUT:function(e,t){f(e,t,"checked"),f(e,t,"disabled"),e.value!==t.value&&(e.value=t.value),t.hasAttribute("value")||e.removeAttribute("value")},TEXTAREA:function(e,t){var n=t.value;e.value!==n&&(e.value=n);var o=e.firstChild;if(o){var r=o.nodeValue;if(r==n||!n&&r==e.placeholder)return;o.nodeValue=n}},SELECT:function(e,t){if(!t.hasAttribute("multiple")){for(var n,o,r=-1,i=0,a=e.firstChild;a;)if("OPTGROUP"===(o=a.nodeName&&a.nodeName.toUpperCase()))a=(n=a).firstChild;else{"OPTION"===o&&(a.hasAttribute("selected")&&(r=i,i++));!(a=a.nextSibling)&&n&&(a=n.nextSibling,n=null)}e.selectedIndex=r}}};function m(){}function h(e){if(e)return e.getAttribute&&e.getAttribute("id")||e.id}var g=(e=function(e){return function(t,n,o){var a,l,s=o||{};if("string"==typeof n)if("#document"===t.nodeName||"HTML"===t.nodeName||"BODY"===t.nodeName){var c=n;(n=a.createElement("html")).innerHTML=c}else n=u(n);else 11===n.nodeType&&(n=n.firstElementChild);var c=s.getNodeKey||h,d=s.onBeforeNodeAdded||m,f=s.onNodeAdded||m,g=s.onBeforeElUpdated||m,v=s.onElUpdated||m,b=s.onBeforeNodeDiscarded||m,y=s.onNodeDiscarded||m,w=s.onBeforeElChildrenUpdated||m,k=!1!==s.childrenOnly,E=Object.create(null),C=[];function x(e){C.push(e)}function S(e,t){if(1===e.nodeType)for(var n=e.firstChild;n;){var o=void 0;t&&(o=c(n))?x(o):(y(n),n.firstChild&&S(n,t)),n=n.nextSibling}}function A(e,t,n){!1!==b(e)&&(t&&t.removeChild(e),y(e),S(e,n))}function N(e){f(e);for(var t=e.firstChild;t;){var n=t.nextSibling,o=c(t);if(o){var r=E[o];r&&d(t,r)?(t.parentNode.replaceChild(r,t),T(r,t)):N(t)}else N(t);t=n}}function T(t,n,o){var r,a=c(n);if(a&&delete E[a],!o){if(!1===g(t,n))return;if(t.actualize&&(t=t.actualize(t.ownerDocument||a)),e(t,n),v(t),!1===w(t,n))return}"TEXTAREA"!==t.nodeName?function(e,t,n,o,r){var a,l,s,u,f,m=t.firstChild,h=e.firstChild;e:for(;m;){for(u=m.nextSibling,a=c(m);h;){if(s=h.nextSibling,m.isSameNode&&m.isSameNode(h)){m=u,h=s;continue e}var g=c(h),v=l,b=m.nodeType,y=void 0;if(b===h.nodeType&&(1===b?(a?a!==g&&((f=E[a])?s===f?y=!1:(e.insertBefore(f,h),g?x(g):A(h,e,!0),h=f):y=!1):g&&(y=!1),(y=!1!==y&&d(h,m))&&T(h,m)):3!==b&&8!==b||(y=!0,h.nodeValue!==m.nodeValue&&(h.nodeValue=m.nodeValue))),y){m=u,h=s;continue e}g?x(g):A(h,e,!0),h=s}if(a&&(f=E[a])&&d(f,m))e.appendChild(f),T(f,m);else{var w=r(m);!1!==w&&(w&&(m=w),m.actualize&&(m=m.actualize(e.ownerDocument||i)),e.appendChild(m),N(m))}m=u,h=s}!function(e,t,n){for(;t;){var o=t.nextSibling;(n=c(t))?x(n):A(t,e,!0),t=o}}(e,h,0);var k=p[e.nodeName];k&&k(e,o)}(t,n,0,0,d):p.TEXTAREA(t,n)}(l=t).nodeType;return function(e){for(var t=e.firstChild;t;){var n=c(t);n&&(E[n]=t),t=t.nextSibling}}(l),function(e,t,n){1===t.nodeType&&(n=n||a,e===a?e=i:"string"==typeof e&&(e=n.createElement(e)),T(e,t,k));return e}(t,n,s.document||a)}}.call(t={},function(e,t){var n,o,r,i,a=t.attributes;if(11!==t.nodeType&&11!==e.nodeType){for(var l=a.length-1;l>=0;l--)o=(n=a[l]).name,r=n.namespaceURI,i=n.value,r?(o=n.localName||o,e.getAttributeNS(r,o)!==i&&("xmlns"===n.prefix&&(o=n.name),e.setAttributeNS(r,o,i))):e.getAttribute(o)!==i&&e.setAttribute(o,i);for(var s=e.attributes,c=s.length-1;c>=0;c--)o=(n=s[c]).name,(r=n.namespaceURI)?(o=n.localName||o,t.hasAttributeNS(r,o)||e.removeAttributeNS(r,o)):t.hasAttribute(o)||e.removeAttribute(o)}}),t.exports);return g}));

/**
 * Obsidian LiveComponents v2.0
 *
 * Client-side runtime for server-side reactive components.
 * Manages state synchronization, action dispatch, DOM patching,
 * WebSocket push subscriptions, and inter-component event propagation.
 *
 * New in v2:
 * - Optimistic UI  : DOM updated immediately, rolled back on server error.
 * - Action queue   : actions are serialized per component, deduped for live:model.
 * - WebSocket push : server can push re-renders and patches without polling.
 * - Patch          : field-level DOM update via live:patch without morphdom.
 * - Event bus      : live:on / on() connect components without a page reload.
 * - Visibility poll: polling pauses when the tab is hidden, resumes on focus.
 *
 * @version 2.0.0
 */
class ObsidianComponents {

    /**
     * Initializes the runtime, discovers already-rendered components in the DOM,
     * mounts lazy placeholders, and attaches global event listeners.
     */
    constructor() {
        /**
         * Registry of active component states, keyed by component UUID.
         * @type {Map<string, {element: Element, loading: boolean, pollInterval: number|null, wsSocket: WebSocket|null, queue: Array, processing: boolean}>}
         */
        this.components = new Map();

        /**
         * CSRF token read once at startup from a meta tag or cookie.
         * Sent as X-CSRF-TOKEN on every action request.
         * @type {string|null}
         */
        this.csrfToken = this.getCsrfToken();

        /**
         * Internal EventTarget used as a lightweight inter-component event bus.
         * Events emitted by server actions are dispatched here in addition to document.
         * @type {EventTarget}
         */
        this._bus = new EventTarget();

        this.init();
    }

    /**
     * Runs the startup sequence: discovers components, mounts lazy placeholders,
     * and attaches the global click listener.
     */
    init() {
        this.discoverComponents();
        this.mountLazyComponents();
        this.attachGlobalListeners();
        console.log('[Obsidian] LiveComponents v2 — ' + this.components.size + ' component(s) found');
    }

    /**
     * Scans the DOM for elements carrying a [live:id] attribute and registers
     * any that are not already tracked in the component registry.
     */
    discoverComponents() {
        document.querySelectorAll('[live\\:id]').forEach(el => {
            const id = el.getAttribute('live:id');
            if (!this.components.has(id)) this._register(id, el);
        });
    }

    /**
     * Creates the state entry for a component, stores it in the registry,
     * wires all live: directive listeners, and opens a WebSocket if requested.
     *
     * @param {string}  componentId  the component UUID from [live:id]
     * @param {Element} el           the root DOM element of the component
     */
    _register(componentId, el) {
        this.components.set(componentId, {
            element:      el,
            loading:      false,
            pollInterval: null,
            wsSocket:     null,
            queue:        [],
            processing:   false,
        });
        this._wire(componentId, el);
        this._startWebSocket(componentId, el);
    }

    /**
     * Attaches all live: directive listeners to a component root element.
     * Called both on initial registration and when morphdom adds new root elements.
     *
     * @param {string}  componentId  the component UUID
     * @param {Element} el           the component root element to wire
     */
    _wire(componentId, el) {
        this._attachModelBindings(el, componentId);
        this._attachPolling(el, componentId);
        this._attachInit(el, componentId);
        this._attachSubmit(el, componentId);
        this._attachEventListeners(el, componentId);
    }

    /**
     * Finds all [live:lazy] placeholder elements, fetches their rendered HTML from
     * the server, replaces the placeholder in the DOM, and registers the new component.
     * Props are read from the [live:props] attribute as a JSON string.
     */
    mountLazyComponents() {
        document.querySelectorAll('[live\\:lazy]').forEach(placeholder => {
            const componentName = placeholder.getAttribute('live:lazy');
            const propsAttr     = placeholder.getAttribute('live:props');
            let url = `/obsidian/components/mount?component=${encodeURIComponent(componentName)}`;
            if (propsAttr) url += `&props=${encodeURIComponent(propsAttr)}`;

            fetch(url)
                .then(r => r.json())
                .then(data => {
                    if (!data.success || !data.html) {
                        console.error('[Obsidian] Lazy mount failed for', componentName, data.error);
                        return;
                    }
                    const tmp = document.createElement('div');
                    tmp.innerHTML = data.html;
                    const newEl = tmp.firstElementChild;
                    if (!newEl) return;
                    placeholder.parentNode.replaceChild(newEl, placeholder);
                    const id = newEl.getAttribute('live:id');
                    if (id) this._register(id, newEl);
                })
                .catch(err => console.error('[Obsidian] Lazy mount error for', componentName, err));
        });
    }

    /**
     * Opens a WebSocket connection for a component that carries the [live:ws] attribute,
     * enabling the server to push re-renders and field patches at any time.
     * Reconnects automatically after a 2-second back-off on unexpected closure.
     *
     * @param {string}  componentId  the component UUID sent as a query parameter
     * @param {Element} el           the component root element; must carry [live:ws]
     */
    _startWebSocket(componentId, el) {
        if (!el.hasAttribute('live:ws')) return;
        const sessionId = el.getAttribute('live:session') || '';
        const proto     = location.protocol === 'https:' ? 'wss' : 'ws';
        const url       = `${proto}://${location.host}/obsidian/components/ws?sessionId=${encodeURIComponent(sessionId)}&componentId=${encodeURIComponent(componentId)}`;

        const ws    = new WebSocket(url);
        const state = this.components.get(componentId);
        if (state) state.wsSocket = ws;

        ws.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data);
                this._applyResponse(componentId, data);
            } catch (e) {
                console.error('[Obsidian] WS message parse error', e);
            }
        };

        ws.onclose = () => {
            setTimeout(() => {
                const s = this.components.get(componentId);
                if (s) this._startWebSocket(componentId, s.element);
            }, 2000);
        };

        ws.onerror = err => console.warn('[Obsidian] WS error for', componentId, err);
    }

    /**
     * Enqueues an action for serial execution against a component.
     * If an updateField_ action for the same field is already waiting in the queue,
     * it is replaced by the new one (dedup) to avoid redundant server round-trips.
     * Returns a Promise that resolves with the server response or rejects on error.
     *
     * @param {string}        componentId   the target component UUID
     * @param {string}        action        action name, optionally with inline params e.g. "delete(42)"
     * @param {Object}        [customParams={}]  pass {field, value} to trigger a live:model update
     * @param {Function|null} [optimisticFn=null] optional function called immediately with the
     *                                            component root element before the server responds;
     *                                            the DOM is rolled back automatically on error
     * @returns {Promise<Object>} resolves with the parsed server response
     */
    call(componentId, action, customParams = {}, optimisticFn = null) {
        const state = this.components.get(componentId);
        if (!state) return Promise.resolve();

        if (customParams.field) {
            const existingIdx = state.queue.findIndex(
                item => item.action === `updateField_${customParams.field}`
            );
            if (existingIdx !== -1) state.queue.splice(existingIdx, 1);
        }

        return new Promise((resolve, reject) => {
            state.queue.push({ action, customParams, resolve, reject, optimisticFn });
            if (!state.processing) this._processQueue(componentId);
        });
    }

    /**
     * Pulls the next item from the component's action queue and executes it.
     * Calls itself recursively until the queue is empty, then resets the processing flag.
     *
     * @param {string} componentId  the component whose queue should be drained
     */
    async _processQueue(componentId) {
        const state = this.components.get(componentId);
        if (!state || state.queue.length === 0) {
            if (state) state.processing = false;
            return;
        }

        state.processing = true;
        const item = state.queue.shift();

        try {
            const result = await this._executeAction(componentId, item);
            item.resolve(result);
        } catch (err) {
            item.reject(err);
        }

        this._processQueue(componentId);
    }

    /**
     * Executes a single action item against the server.
     * If an optimistic function was provided, it is called synchronously before the fetch
     * and the DOM is rolled back via {@link _rollback} if the request fails.
     *
     * @param {string} componentId                          the target component UUID
     * @param {Object} item                                 queue item produced by {@link call}
     * @param {string} item.action                          raw action string
     * @param {Object} item.customParams                    optional {field, value} for model updates
     * @param {Function|null} item.optimisticFn             optional pre-fetch DOM mutation
     * @returns {Promise<Object>} resolves with the parsed server JSON response
     * @throws {Error} re-throws fetch or HTTP errors after rolling back optimistic changes
     */
    async _executeAction(componentId, { action, customParams, optimisticFn }) {
        const state = this.components.get(componentId);
        if (!state) return;

        let optimisticSnapshot = null;
        if (optimisticFn && typeof optimisticFn === 'function') {
            optimisticSnapshot = state.element.outerHTML;
            try { optimisticFn(state.element); }
            catch (e) { console.warn('[Obsidian] Optimistic update failed', e); }
        }

        this._showLoading(state.element);
        this._clearValidationErrors(state.element);

        const parsed      = this._parseAction(action);
        const finalParams = customParams.field ? [customParams.value] : parsed.params;
        const finalAction = customParams.field ? `updateField_${customParams.field}` : parsed.name;

        try {
            const response = await fetch('/obsidian/components', {
                method:  'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-CSRF-TOKEN': this.csrfToken
                },
                body: JSON.stringify({
                    componentId,
                    action: finalAction,
                    state:  this._captureState(state.element),
                    params: finalParams
                })
            });

            if (!response.ok) throw new Error(`HTTP ${response.status}`);

            const data = await response.json();
            this._applyResponse(componentId, data);
            return data;

        } catch (err) {
            if (optimisticSnapshot) this._rollback(componentId, optimisticSnapshot);
            this._showError(state.element, err.message);
            console.error('[Obsidian] Action error:', err);
            throw err;
        } finally {
            this._hideLoading(state.element);
        }
    }

    /**
     * Applies a server response to the component DOM.
     * Shared by both the HTTP action path and the WebSocket push path so both
     * update sources go through identical handling logic.
     *
     * @param {string} componentId  the target component UUID
     * @param {Object} data         parsed JSON response from the server
     * @param {boolean} data.success     whether the action succeeded
     * @param {string}  [data.redirect]  if set, navigates the browser to this URL
     * @param {string}  [data.type]      "PATCH" for field-only updates
     * @param {string}  [data.html]      rendered component HTML for full re-renders
     * @param {Object}  [data.diff]      map of changed field names to new values
     * @param {string}  [data.event]     custom event name to dispatch on document
     * @param {*}       [data.eventPayload] payload attached to event.detail
     * @param {Object}  [data.state]     full state snapshot; checked for validation errors
     */
    _applyResponse(componentId, data) {
        const state = this.components.get(componentId);
        if (!state) return;

        if (!data.success) {
            if (data.html) this._updateComponent(componentId, data.html);
            this._showError(state.element, data.error);
            return;
        }

        if (data.redirect) {
            window.location.href = data.redirect;
            return;
        }

        if (data.type === 'PATCH' && data.diff) {
            this._applyPatch(componentId, data.diff);
            return;
        }

        if (data.html) this._updateComponent(componentId, data.html);

        if (data.diff && Object.keys(data.diff).length > 0) {
            this._applyPatch(componentId, data.diff);
        }

        if (data.event) {
            document.dispatchEvent(new CustomEvent(data.event, {
                bubbles: true,
                detail:  data.eventPayload ?? null
            }));
            this._bus.dispatchEvent(new CustomEvent(data.event, { detail: data.eventPayload ?? null }));
        }

        if (data.state?.errors) {
            this._displayValidationErrors(state.element, data.state.errors);
        }
    }

    /**
     * Restores the component DOM to a previously captured snapshot.
     * Called automatically by {@link _executeAction} when a server request fails
     * after an optimistic DOM mutation was already applied.
     *
     * @param {string} componentId    the component UUID to restore
     * @param {string} snapshotHtml   the outerHTML captured before the optimistic mutation
     */
    _rollback(componentId, snapshotHtml) {
        const state = this.components.get(componentId);
        if (!state || !document.contains(state.element)) return;
        console.warn('[Obsidian] Rolling back optimistic update for', componentId);
        this._updateComponent(componentId, snapshotHtml);
    }

    /**
     * Applies a partial field diff to the DOM without a full morphdom re-render.
     * For each entry in {@code diff}, updates all [live:patch="fieldName"] elements:
     * sets .value on form controls and .textContent on all other elements.
     *
     * @param {string} componentId  the component UUID
     * @param {Object} diff         map of field names to their new values
     */
    _applyPatch(componentId, diff) {
        const state = this.components.get(componentId);
        if (!state) return;
        Object.entries(diff).forEach(([field, value]) => {
            state.element.querySelectorAll(`[live\\:patch="${field}"]`).forEach(el => {
                if (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.tagName === 'SELECT') {
                    el.value = value;
                } else {
                    el.textContent = value;
                }
            });
        });
    }

    /**
     * Wires [live:on] nodes inside a component to the internal event bus.
     * When the named event is dispatched by any component via emit(), the action
     * specified in [live:on-action] is called on this component.
     * Defaults to "__refresh" if [live:on-action] is absent.
     *
     * @param {Element} el           the component root element to scan for [live:on] nodes
     * @param {string}  componentId  the component UUID that will receive the action call
     */
    _attachEventListeners(el, componentId) {
        el.querySelectorAll('[live\\:on]').forEach(node => {
            if (node._liveOnBound) return;
            node._liveOnBound = true;
            const eventName = node.getAttribute('live:on');
            const action    = node.getAttribute('live:on-action') || '__refresh';
            this._bus.addEventListener(eventName, () => {
                this.call(componentId, action);
            });
        });
    }

    /**
     * Wires [live:model] inputs to the action queue.
     * Each input gets a debounced handler (default 300 ms) that enqueues an updateField_
     * action with an optimistic patch on any matching [live:patch] elements.
     * Supports [live:blur] to update only on blur and [live:enter] to update only on Enter.
     *
     * @param {Element} element      the component root element to scan for [live:model] inputs
     * @param {string}  componentId  the component UUID
     */
    _attachModelBindings(element, componentId) {
        element.querySelectorAll('[live\\:model]').forEach(input => {
            if (input._liveModelBound) return;
            input._liveModelBound = true;

            const fieldName     = input.getAttribute('live:model');
            const debounceTime  = parseInt(input.getAttribute('live:debounce')) || 300;
            const updateOnBlur  = input.hasAttribute('live:blur');
            const updateOnEnter = input.hasAttribute('live:enter');

            const update = (value) => {
                const optimistic = (root) => {
                    root.querySelectorAll(`[live\\:patch="${fieldName}"]`).forEach(el => {
                        el.textContent = value;
                    });
                };
                this.call(componentId, '', { field: fieldName, value }, optimistic);
            };

            if (updateOnBlur) {
                input.addEventListener('blur', () => update(input.value));
            } else if (updateOnEnter) {
                input.addEventListener('keydown', e => {
                    if (e.key === 'Enter') { e.preventDefault(); update(input.value); }
                });
            } else {
                const debounced = this._debounce(update, debounceTime);
                input.addEventListener('input', e => debounced(e.target.value));
            }
        });
    }

    /**
     * Wires [live:submit] forms to the action queue.
     * Prevents default form submission, clears validation errors, and calls the action.
     *
     * @param {Element} element      the component root element to scan for [live:submit] forms
     * @param {string}  componentId  the component UUID
     */
    _attachSubmit(element, componentId) {
        element.querySelectorAll('[live\\:submit]').forEach(form => {
            if (form._liveSubmitBound) return;
            form._liveSubmitBound = true;
            const action = form.getAttribute('live:submit');
            form.addEventListener('submit', e => {
                e.preventDefault();
                this._clearValidationErrors(element);
                this.call(componentId, action);
            });
        });
    }

    /**
     * Attaches a single delegated click listener on document for all [live:click] elements.
     * Supports [live:confirm] for a confirmation dialog before the action is enqueued.
     * Applies an optimistic "live-optimistic" class to the clicked element immediately.
     */
    attachGlobalListeners() {
        document.addEventListener('click', e => {
            const target = e.target.closest('[live\\:click]');
            if (!target) return;
            e.preventDefault();

            const confirmMsg = target.getAttribute('live:confirm');
            if (confirmMsg && !confirm(confirmMsg)) return;

            const action    = target.getAttribute('live:click');
            const component = target.closest('[live\\:id]');
            if (!component) return;

            const componentId = component.getAttribute('live:id');
            const optimistic  = () => target.classList.add('live-optimistic');
            this.call(componentId, action, {}, optimistic);
        });
    }

    /**
     * Starts a polling interval for a component carrying the [live:poll] attribute.
     * Interval value is parsed from the attribute: plain number = ms, suffix "s" = seconds,
     * suffix "m" = minutes. The action to call is read from [live:poll.Xs="actionName"];
     * defaults to "__refresh" if no action attribute is found.
     * Polling ticks are skipped when document.hidden is true, resuming immediately
     * on the next visibilitychange event.
     *
     * @param {Element} element      the component root element carrying [live:poll]
     * @param {string}  componentId  the component UUID
     */
    _attachPolling(element, componentId) {
        const pollAttr = element.getAttribute('live:poll');
        if (!pollAttr) return;

        let interval = parseInt(pollAttr);
        if (pollAttr.endsWith('s'))      interval = parseInt(pollAttr) * 1000;
        else if (pollAttr.endsWith('m')) interval = parseInt(pollAttr) * 60000;

        let action = '__refresh';
        for (const attr of element.attributes) {
            if (attr.name.startsWith('live:poll.') && !attr.name.includes('live:poll.class')) {
                action = attr.value; break;
            }
        }

        const state = this.components.get(componentId);
        if (!state) return;

        const tick = () => {
            if (!document.contains(element)) { clearInterval(state.pollInterval); return; }
            if (document.hidden) return;
            this.call(componentId, action);
        };

        state.pollInterval = setInterval(tick, interval);

        document.addEventListener('visibilitychange', () => {
            if (!document.hidden && this.components.has(componentId)) tick();
        });
    }

    /**
     * Calls a [live:init] action once after a short delay when the component mounts.
     * The 100 ms delay ensures the component is fully rendered before the action fires.
     *
     * @param {Element} element      the component root element, checked for [live:init]
     * @param {string}  componentId  the component UUID
     */
    _attachInit(element, componentId) {
        const initAction = element.getAttribute('live:init');
        if (initAction) setTimeout(() => this.call(componentId, initAction), 100);
    }

    /**
     * Patches the component's root element in-place using morphdom.
     * Preserves focus and cursor position on the active input by skipping its update.
     * Re-wires live:model, live:submit, and live:on bindings on any newly added nodes.
     * Removes the component from the registry if its root element is no longer in the DOM.
     *
     * @param {string} componentId  the component UUID
     * @param {string} html         new outer HTML string to morph the root element into
     */
    _updateComponent(componentId, html) {
        const state = this.components.get(componentId);
        if (!state) return;

        if (!document.contains(state.element)) {
            this.components.delete(componentId); return;
        }

        const updated = morphdom(state.element, html, {
            onBeforeElUpdated(from, to) {
                if (from === document.activeElement &&
                    (from.tagName === 'INPUT' || from.tagName === 'TEXTAREA' || from.tagName === 'SELECT'))
                    return false;
                return true;
            },
            onNodeAdded: (node) => {
                if (node.nodeType !== 1) return node;
                if (node.hasAttribute?.('live:model') && !node._liveModelBound)
                    this._attachModelBindings(node.parentElement || state.element, componentId);
                if (node.hasAttribute?.('live:submit') && !node._liveSubmitBound)
                    this._attachSubmit(node.parentElement || state.element, componentId);
                if (node.hasAttribute?.('live:on') && !node._liveOnBound)
                    this._attachEventListeners(node.parentElement || state.element, componentId);
                return node;
            }
        });

        this.components.set(componentId, { ...state, element: updated });
    }

    /**
     * Reads the current values of all input, textarea, and select elements
     * inside the component root and returns them as a plain object.
     * Keys are taken from the [name] attribute first, then [live:model].
     * Checkboxes contribute a boolean; radio buttons contribute their value only if checked.
     *
     * @param {Element} element  the component root element to scan
     * @returns {Object} map of field names to their current form values
     */
    _captureState(element) {
        const state = {};
        element.querySelectorAll('input, textarea, select').forEach(input => {
            const key = input.getAttribute('name') || input.getAttribute('live:model');
            if (!key) return;
            if (input.type === 'checkbox')     state[key] = input.checked;
            else if (input.type === 'radio') { if (input.checked) state[key] = input.value; }
            else                               state[key] = input.value;
        });
        return state;
    }

    /**
     * Renders server-side validation errors returned in the state response.
     * Adds the "is-invalid" and "border-red-500" classes to the offending input
     * and inserts a sibling error span if one does not already exist.
     *
     * @param {Element} element  the component root element
     * @param {Object}  errors   map of field names to error message strings
     */
    _displayValidationErrors(element, errors) {
        if (!errors || typeof errors !== 'object') return;
        Object.entries(errors).forEach(([field, message]) => {
            const input = element.querySelector(`[name="${field}"], [live\\:model="${field}"]`);
            if (!input) return;
            input.classList.add('is-invalid', 'border-red-500');
            let err = input.parentElement.querySelector('.error-message');
            if (!err) {
                err = document.createElement('span');
                err.className = 'error-message validation-error text-red-500 text-sm mt-1';
                err.setAttribute('data-validation-error', field);
                input.parentElement.insertBefore(err, input.nextSibling);
            }
            err.textContent = message;
            err.style.display = 'block';
        });
    }

    /**
     * Removes all validation error classes and error span elements from the component root.
     * Called before every action dispatch to reset the error state.
     *
     * @param {Element} element  the component root element to clear
     */
    _clearValidationErrors(element) {
        element.querySelectorAll('.is-invalid, .border-red-500')
            .forEach(el => el.classList.remove('is-invalid', 'border-red-500'));
        element.querySelectorAll('[data-validation-error]').forEach(el => el.remove());
    }

    /**
     * Activates loading indicators and disables interactive elements while an action is in flight.
     * Handles three indicator variants via [live:loading.class], [live:loading.add/remove],
     * and a plain [live:loading] visibility toggle.
     *
     * @param {Element} element  the component root element
     */
    _showLoading(element) {
        element.querySelectorAll('[live\\:loading]').forEach(indicator => {
            const cls    = indicator.getAttribute('live:loading.class');
            const add    = indicator.getAttribute('live:loading.add');
            const remove = indicator.getAttribute('live:loading.remove');
            if (cls)      cls.split(' ').forEach(c => indicator.classList.add(c));
            else if (add) add.split(' ').forEach(c => indicator.classList.add(c));
            else          indicator.style.display = '';
            if (remove)   remove.split(' ').forEach(c => indicator.classList.remove(c));
        });
        element.querySelectorAll('[live\\:click], button[type="submit"]').forEach(btn => {
            btn.disabled      = true;
            btn.style.opacity = '0.6';
            btn.style.cursor  = 'not-allowed';
        });
    }

    /**
     * Deactivates loading indicators and re-enables interactive elements after an action completes.
     * Reverses exactly the changes made by {@link _showLoading}.
     *
     * @param {Element} element  the component root element
     */
    _hideLoading(element) {
        element.querySelectorAll('[live\\:loading]').forEach(indicator => {
            const cls    = indicator.getAttribute('live:loading.class');
            const add    = indicator.getAttribute('live:loading.add');
            const remove = indicator.getAttribute('live:loading.remove');
            if (cls)      cls.split(' ').forEach(c => indicator.classList.remove(c));
            else if (add) add.split(' ').forEach(c => indicator.classList.remove(c));
            else          indicator.style.display = 'none';
            if (remove)   remove.split(' ').forEach(c => indicator.classList.add(c));
        });
        element.querySelectorAll('[live\\:click], button[type="submit"]').forEach(btn => {
            btn.disabled      = false;
            btn.style.opacity = '';
            btn.style.cursor  = '';
        });
    }

    /**
     * Inserts a transient red error banner at the top of the component root.
     * The banner removes itself automatically after 5 seconds.
     *
     * @param {Element} element  the component root element
     * @param {string}  message  the error text to display
     */
    _showError(element, message) {
        const div = document.createElement('div');
        div.style.cssText = 'background:#ef4444;color:white;padding:1rem;margin:1rem 0;border-radius:.5rem;';
        div.textContent = 'Error: ' + message;
        element.insertBefore(div, element.firstChild);
        setTimeout(() => div.remove(), 5000);
    }

    /**
     * Returns a debounced version of {@code fn} that delays invocation by {@code wait} ms.
     * Each new call resets the timer, so the function fires only after {@code wait} ms
     * of inactivity.
     *
     * @param {Function} fn    the function to debounce
     * @param {number}   wait  delay in milliseconds
     * @returns {Function} the debounced wrapper function
     */
    _debounce(fn, wait) {
        let t;
        return (...args) => { clearTimeout(t); t = setTimeout(() => fn(...args), wait); };
    }

    /**
     * Reads the CSRF token from the first matching source: a <meta name="csrf-token"> tag,
     * a CSRF-TOKEN cookie, or a _csrf cookie.
     *
     * @returns {string|null} the CSRF token string, or null if none is found
     */
    getCsrfToken() {
        const meta = document.querySelector('meta[name="csrf-token"]');
        if (meta?.content) return meta.content;
        for (const cookie of document.cookie.split(';')) {
            const [name, value] = cookie.trim().split('=');
            if (name === 'CSRF-TOKEN' || name === '_csrf') return decodeURIComponent(value);
        }
        return null;
    }

    /**
     * Parses an action string into a method name and a typed parameter list.
     * Supports bare names ("increment") and call syntax ("delete(42, 'foo')").
     * String delimiters inside params are consumed and stripped from the values.
     *
     * @param {string} actionString  the raw action attribute value
     * @returns {{name: string, params: Array}} parsed name and parameter array
     */
    _parseAction(actionString) {
        const match = actionString.match(/^(\w+)\((.*)\)$/);
        if (!match) return { name: actionString, params: [] };
        const name = match[1];
        const raw  = match[2];
        if (!raw.trim()) return { name, params: [] };

        const params = [];
        let current = '', inString = false, strChar = null;
        for (let i = 0; i < raw.length; i++) {
            const ch = raw[i];
            if ((ch === '"' || ch === "'") && raw[i - 1] !== '\\') {
                if (!inString) { inString = true; strChar = ch; }
                else if (ch === strChar) { inString = false; strChar = null; }
                continue;
            }
            if (ch === ',' && !inString) { params.push(this._parseValue(current.trim())); current = ''; }
            else current += ch;
        }
        if (current.trim()) params.push(this._parseValue(current.trim()));
        return { name, params };
    }

    /**
     * Coerces a raw string token from an action parameter list to its native JS type.
     * Converts "true"/"false" to booleans, "null" to null, numeric strings to numbers,
     * and leaves everything else as a string.
     *
     * @param {string} v  the raw token string to coerce
     * @returns {boolean|null|number|string} the typed value
     */
    _parseValue(v) {
        if (v === 'true')  return true;
        if (v === 'false') return false;
        if (v === 'null')  return null;
        if (!isNaN(v) && v !== '') return Number(v);
        return v;
    }

    /**
     * Subscribes to an inter-component event dispatched via server-side emit().
     * The callback receives the event payload (equivalent to event.detail).
     *
     * @param {string}   event     the custom event name to listen for
     * @param {Function} callback  called with the event payload when the event fires
     */
    on(event, callback) {
        this._bus.addEventListener(event, e => callback(e.detail));
    }
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        window.ObsidianComponents = new ObsidianComponents();
    });
} else {
    window.ObsidianComponents = new ObsidianComponents();
}

// Backward-compat aliases — v1 public API preserved
ObsidianComponents.prototype.updateModel             = function(id, field, value) { return this.call(id, '', { field, value }); };
ObsidianComponents.prototype.captureState            = function(el)               { return this._captureState(el); };
ObsidianComponents.prototype.updateComponent         = function(id, html)         { return this._updateComponent(id, html); };
ObsidianComponents.prototype.showLoading             = function(el)               { return this._showLoading(el); };
ObsidianComponents.prototype.hideLoading             = function(el)               { return this._hideLoading(el); };
ObsidianComponents.prototype.showError               = function(el, msg)          { return this._showError(el, msg); };
ObsidianComponents.prototype.clearValidationErrors   = function(el)               { return this._clearValidationErrors(el); };
ObsidianComponents.prototype.displayValidationErrors = function(el, errors)       { return this._displayValidationErrors(el, errors); };
ObsidianComponents.prototype.attachEventListeners    = function()                 { return this.attachGlobalListeners(); };
ObsidianComponents.prototype.debounce                = function(fn, wait)         { return this._debounce(fn, wait); };
ObsidianComponents.prototype.parseAction             = function(s)                { return this._parseAction(s); };
ObsidianComponents.prototype.parseValue              = function(v)                { return this._parseValue(v); };