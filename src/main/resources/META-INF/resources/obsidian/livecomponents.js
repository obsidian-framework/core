/**
 * Morphdom - Fast DOM diffing/patching (CDN inline)
 * @see https://github.com/patrick-steele-idem/morphdom
 */
window.morphdom=(function(){"use strict";var e,t,n,o,r,i="http://www.w3.org/1999/xhtml",a=typeof document>"u"?void 0:document,l=!!a&&"content"in a.createElement("template"),s=!!a&&a.createRange&&"createContextualFragment"in a.createRange();function c(e){var t=a.createElement("template");return t.innerHTML=e,t.content.childNodes[0]}function u(e){return(l?c:s?function(e){return n||(n=a.createRange()).selectNode(a.body),n.createContextualFragment(e).childNodes[0]}:function(e){return(o||(o=a.createElement("body"))).innerHTML=e,o.childNodes[0]})(e)}function d(e,t){var n,o,r=e.nodeName,i=t.nodeName;return r===i||(n=r.charCodeAt(0),o=i.charCodeAt(0),n<=90&&o>=97?r===i.toUpperCase():o<=90&&n>=97&&i===r.toUpperCase())}function f(e,t,n){e[n]!==t[n]&&(e[n]=t[n],e[n]?e.setAttribute(n,""):e.removeAttribute(n))}var p={OPTION:function(e,t){var n=e.parentNode;if(n){var o=n.nodeName.toUpperCase();"OPTGROUP"===o&&(o=(n=n.parentNode)&&n.nodeName.toUpperCase()),"SELECT"!==o||n.hasAttribute("multiple")||(e.hasAttribute("selected")&&!t.selected&&(e.setAttribute("selected","selected"),e.removeAttribute("selected")),n.selectedIndex=-1)}f(e,t,"selected")},INPUT:function(e,t){f(e,t,"checked"),f(e,t,"disabled"),e.value!==t.value&&(e.value=t.value),t.hasAttribute("value")||e.removeAttribute("value")},TEXTAREA:function(e,t){var n=t.value;e.value!==n&&(e.value=n);var o=e.firstChild;if(o){var r=o.nodeValue;if(r==n||!n&&r==e.placeholder)return;o.nodeValue=n}},SELECT:function(e,t){if(!t.hasAttribute("multiple")){for(var n,o,r=-1,i=0,a=e.firstChild;a;)if("OPTGROUP"===(o=a.nodeName&&a.nodeName.toUpperCase()))a=(n=a).firstChild;else{if("OPTION"===o){if(a.hasAttribute("selected")){r=i;break}i++}!(a=a.nextSibling)&&n&&(a=n.nextSibling,n=null)}e.selectedIndex=r}}};function m(){}function h(e){if(e)return e.getAttribute&&e.getAttribute("id")||e.id}var g=(e=function(e){return function(t,n,o){var a,l,s=o||{};if("string"==typeof n)if("#document"===t.nodeName||"HTML"===t.nodeName||"BODY"===t.nodeName){var c=n;(n=a.createElement("html")).innerHTML=c}else n=u(n);else 11===n.nodeType&&(n=n.firstElementChild);var c=s.getNodeKey||h,d=s.onBeforeNodeAdded||m,f=s.onNodeAdded||m,g=s.onBeforeElUpdated||m,v=s.onElUpdated||m,b=s.onBeforeNodeDiscarded||m,y=s.onNodeDiscarded||m,w=s.onBeforeElChildrenUpdated||m,k=!1!==s.childrenOnly,E=Object.create(null),C=[];function x(e){C.push(e)}function S(e,t){if(1===e.nodeType)for(var n=e.firstChild;n;){var o=void 0;t&&(o=c(n))?x(o):(y(n),n.firstChild&&S(n,t)),n=n.nextSibling}}function A(e,t,n){!1!==b(e)&&(t&&t.removeChild(e),y(e),S(e,n))}function N(e){f(e);for(var t=e.firstChild;t;){var n=t.nextSibling,o=c(t);if(o){var r=E[o];r&&d(t,r)?(t.parentNode.replaceChild(r,t),T(r,t)):N(t)}else N(t);t=n}}function T(t,n,o){var r,a=c(n);if(a&&delete E[a],!o){if(!1===g(t,n))return;if(t.actualize&&(t=t.actualize(t.ownerDocument||a)),e(t,n),v(t),!1===w(t,n))return}"TEXTAREA"!==t.nodeName?function(e,t,n,o,r){var a,l,s,u,f,m=t.firstChild,h=e.firstChild;e:for(;m;){for(u=m.nextSibling,a=c(m);h;){if(s=h.nextSibling,m.isSameNode&&m.isSameNode(h)){m=u,h=s;continue e}var g=c(h),v=l,b=m.nodeType,y=void 0;if(b===h.nodeType&&(1===b?(a?a!==g&&((f=E[a])?s===f?y=!1:(e.insertBefore(f,h),g?x(g):A(h,e,!0),h=f):y=!1):g&&(y=!1),(y=!1!==y&&d(h,m))&&T(h,m)):3!==b&&8!==b||(y=!0,h.nodeValue!==m.nodeValue&&(h.nodeValue=m.nodeValue))),y){m=u,h=s;continue e}g?x(g):A(h,e,!0),h=s}if(a&&(f=E[a])&&d(f,m))e.appendChild(f),T(f,m);else{var w=r(m);!1!==w&&(w&&(m=w),m.actualize&&(m=m.actualize(e.ownerDocument||i)),e.appendChild(m),N(m))}m=u,h=s}!function(e,t,n){for(;t;){var o=t.nextSibling;(n=c(t))?x(n):A(t,e,!0),t=o}}(e,h,0);var k=p[e.nodeName];k&&k(e,o)}(t,n,0,0,d):p.TEXTAREA(t,n)}(l=t).nodeType;return function(e){for(var t=e.firstChild;t;){var n=c(t);n&&(E[n]=t),t=t.nextSibling}}(l),function(e,t,n){1===t.nodeType&&(n=n||a,e===a?e=i:"string"==typeof e&&(e=n.createElement(e)),T(e,t,k));return e}(t,n,s.document||a)}}.call(t={},function(e,t){var n,o,r,i,a=t.attributes;if(11!==t.nodeType&&11!==e.nodeType){for(var l=a.length-1;l>=0;l--)o=(n=a[l]).name,r=n.namespaceURI,i=n.value,r?(o=n.localName||o,e.getAttributeNS(r,o)!==i&&("xmlns"===n.prefix&&(o=n.name),e.setAttributeNS(r,o,i))):e.getAttribute(o)!==i&&e.setAttribute(o,i);for(var s=e.attributes,c=s.length-1;c>=0;c--)o=(n=s[c]).name,(r=n.namespaceURI)?(o=n.localName||o,t.hasAttributeNS(r,o)||e.removeAttributeNS(r,o)):t.hasAttribute(o)||e.removeAttribute(o)}}));return g})();

/**
 * Obsidian LiveComponents - Client-side reactive component system
 *
 * Manages server-side reactive components with automatic DOM updates,
 * real-time validation, and seamless state synchronization.
 *
 * @class ObsidianComponents
 * @version 1.1.0 — Now powered by morphdom for surgical DOM patching.
 *
 * Features:
 * - live:click - Click event handling with server actions
 * - live:model - Two-way data binding with debouncing
 * - live:submit - Form submission with validation
 * - live:poll - Automatic polling/refresh
 * - live:init - Component initialization actions
 * - live:loading - Loading state indicators
 * - live:confirm - Confirmation dialogs
 * - Automatic validation error display
 */
class ObsidianComponents {
    /**
     * Creates an instance of ObsidianComponents.
     * Initializes component registry and discovers components in DOM.
     */
    constructor() {
        /** @type {Map<string, {element: Element, loading: boolean, pollInterval: number|null}>} */
        this.components = new Map();

        /** @type {string|null} CSRF token for secure requests */
        this.csrfToken = this.getCsrfToken();

        this.init();
    }

    /**
     * Initializes the LiveComponents system.
     * Discovers components and attaches global event listeners.
     */
    init() {
        this.discoverComponents();
        this.mountLazyComponents();
        this.attachEventListeners();
        console.log('Obsidian LiveComponents initialized:', this.components.size, 'components found');
    }

    /**
     * Discovers all LiveComponents in the DOM.
     * Components are identified by the [live:id] attribute.
     * Registers each component and attaches its event listeners.
     */
    discoverComponents() {
        document.querySelectorAll('[live\\:id]').forEach(el => {
            const componentId = el.getAttribute('live:id');
            if (!this.components.has(componentId)) {
                this.components.set(componentId, {
                    element: el,
                    loading: false,
                    pollInterval: null
                });
                this.attachModelBindings(el, componentId);
                this.attachPolling(el, componentId);
                this.attachInit(el, componentId);
                this.attachSubmit(el, componentId);
            }
        });
    }

    /**
     * Mounts all [live:lazy] placeholders after page load.
     * Fetches rendered HTML from the server and replaces the placeholder.
     * Props are read from the [live:props] attribute as a JSON object.
     *
     * Usage:
     *   <div live:lazy="PlayerSearch"></div>
     *   <div live:lazy="UserCard" live:props='{"userId": 42}'></div>
     */
    mountLazyComponents() {
        document.querySelectorAll('[live\\:lazy]').forEach(placeholder => {
            const componentName = placeholder.getAttribute('live:lazy');
            const propsAttr = placeholder.getAttribute('live:props');

            let url = `/obsidian/components/mount?component=${encodeURIComponent(componentName)}`;
            if (propsAttr) {
                url += `&props=${encodeURIComponent(propsAttr)}`;
            }

            fetch(url)
                .then(res => res.json())
                .then(data => {
                    if (!data.success || !data.html) {
                        console.error(`[LiveComponents] Failed to lazy-mount '${componentName}':`, data.error);
                        return;
                    }

                    const temp = document.createElement('div');
                    temp.innerHTML = data.html;
                    const newElement = temp.firstElementChild;

                    if (!newElement) return;

                    placeholder.parentNode.replaceChild(newElement, placeholder);

                    // Register and wire the newly mounted component
                    const componentId = newElement.getAttribute('live:id');
                    if (componentId) {
                        this.components.set(componentId, {
                            element: newElement,
                            loading: false,
                            pollInterval: null
                        });
                        this.attachModelBindings(newElement, componentId);
                        this.attachPolling(newElement, componentId);
                        this.attachInit(newElement, componentId);
                        this.attachSubmit(newElement, componentId);
                    }
                })
                .catch(err => {
                    console.error(`[LiveComponents] Lazy mount error for '${componentName}':`, err);
                });
        });
    }

    /**
     * Attaches global event listeners for click events.
     * Handles [live:click] and [live:confirm] attributes.
     */
    attachEventListeners() {
        document.addEventListener('click', (e) => {
            const target = e.target.closest('[live\\:click]');
            if (!target) return;

            e.preventDefault();

            // Check for confirmation
            const confirmMessage = target.getAttribute('live:confirm');
            if (confirmMessage) {
                if (!confirm(confirmMessage)) {
                    return; // User cancelled
                }
            }

            const action = target.getAttribute('live:click');
            const component = target.closest('[live\\:id]');

            if (component) {
                const componentId = component.getAttribute('live:id');
                this.call(componentId, action);
            }
        });
    }

    /**
     * Attaches two-way data binding to inputs with [live:model].
     *
     * Supports modifiers:
     * - live:debounce="500" - Custom debounce time (default: 300ms)
     * - live:blur - Update only on blur
     * - live:enter - Update only on Enter key
     *
     * @param {Element} element - Component root element
     * @param {string} componentId - Component identifier
     */
    attachModelBindings(element, componentId) {
        const modelInputs = element.querySelectorAll('[live\\:model]');

        modelInputs.forEach(input => {
            // Skip inputs that already have bindings attached
            if (input._liveModelBound) return;
            input._liveModelBound = true;

            const fieldName = input.getAttribute('live:model');
            const debounceTime = parseInt(input.getAttribute('live:debounce')) || 300;
            const updateOnBlur = input.hasAttribute('live:blur');
            const updateOnEnter = input.hasAttribute('live:enter');

            if (updateOnBlur) {
                input.addEventListener('blur', () => {
                    this.updateModel(componentId, fieldName, input.value);
                });
            } else if (updateOnEnter) {
                input.addEventListener('keydown', (e) => {
                    if (e.key === 'Enter') {
                        e.preventDefault();
                        this.updateModel(componentId, fieldName, input.value);
                    }
                });
            } else {
                const debouncedUpdate = this.debounce((value) => {
                    this.updateModel(componentId, fieldName, value);
                }, debounceTime);

                input.addEventListener('input', (e) => {
                    debouncedUpdate(e.target.value);
                });
            }
        });
    }

    /**
     * Attaches form submission handlers with [live:submit].
     * Prevents default form submission and calls server action.
     * Clears validation errors before submission.
     *
     * @param {Element} element - Component root element
     * @param {string} componentId - Component identifier
     */
    attachSubmit(element, componentId) {
        const forms = element.querySelectorAll('[live\\:submit]');

        forms.forEach(form => {
            // Skip forms that already have bindings attached
            if (form._liveSubmitBound) return;
            form._liveSubmitBound = true;

            const action = form.getAttribute('live:submit');

            form.addEventListener('submit', (e) => {
                e.preventDefault();
                this.clearValidationErrors(element);
                this.call(componentId, action);
            });
        });
    }

    /**
     * Updates a model field value on the server.
     *
     * @param {string} componentId - Component identifier
     * @param {string} fieldName - Field name to update
     * @param {*} value - New field value
     */
    updateModel(componentId, fieldName, value) {
        this.call(componentId, '', { field: fieldName, value: value });
    }

    /**
     * Creates a debounced function that delays execution.
     *
     * @param {Function} func - Function to debounce
     * @param {number} wait - Delay in milliseconds
     * @returns {Function} Debounced function
     */
    debounce(func, wait) {
        let timeout;
        return function executedFunction(...args) {
            const later = () => {
                clearTimeout(timeout);
                func(...args);
            };
            clearTimeout(timeout);
            timeout = setTimeout(later, wait);
        };
    }

    /**
     * Attaches automatic polling/refresh to components with [live:poll].
     *
     * Formats:
     * - live:poll="5000" - Poll every 5000ms
     * - live:poll="5s" - Poll every 5 seconds
     * - live:poll="2m" - Poll every 2 minutes
     * - live:poll.5s="refreshData" - Poll and call specific action
     *
     * @param {Element} element - Component root element
     * @param {string} componentId - Component identifier
     */
    attachPolling(element, componentId) {
        const pollAttr = element.getAttribute('live:poll');
        if (!pollAttr) return;

        let interval = parseInt(pollAttr);
        if (pollAttr.endsWith('s')) {
            interval = parseInt(pollAttr) * 1000;
        } else if (pollAttr.endsWith('m')) {
            interval = parseInt(pollAttr) * 60000;
        }

        let action = null;
        for (let attr of element.attributes) {
            if (attr.name.startsWith('live:poll.') && !attr.name.includes('live:poll.class')) {
                action = attr.value;
                break;
            }
        }

        const component = this.components.get(componentId);
        if (component) {
            if (component.pollInterval) {
                clearInterval(component.pollInterval);
            }

            component.pollInterval = setInterval(() => {
                if (!document.contains(element)) {
                    clearInterval(component.pollInterval);
                    return;
                }

                if (action) {
                    this.call(componentId, action);
                } else {
                    this.call(componentId, '__refresh');
                }
            }, interval);
        }
    }

    /**
     * Calls initialization action when component mounts.
     *
     * @param {Element} element - Component root element
     * @param {string} componentId - Component identifier
     */
    attachInit(element, componentId) {
        const initAction = element.getAttribute('live:init');
        if (initAction) {
            setTimeout(() => {
                this.call(componentId, initAction);
            }, 100);
        }
    }

    /**
     * Calls a server action on the component.
     * Manages loading state, state synchronization, and validation errors.
     *
     * @param {string} componentId - Component identifier
     * @param {string} action - Action method name
     * @param {Object} customParams - Optional custom parameters
     * @returns {Promise<void>}
     */
    async call(componentId, action, customParams = {}) {
        const component = this.components.get(componentId);
        if (!component || component.loading) return;

        try {
            component.loading = true;
            this.showLoading(component.element);
            this.clearValidationErrors(component.element);

            const state = this.captureState(component.element);
            const parsed = this.parseAction(action);

            const finalParams = customParams.field ?
                [customParams.value] :
                parsed.params;

            const response = await fetch('/obsidian/components', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-CSRF-TOKEN': this.csrfToken
                },
                body: JSON.stringify({
                    componentId: componentId,
                    action: customParams.field ? `updateField_${customParams.field}` : parsed.name,
                    state: state,
                    params: finalParams
                })
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();

            if (data.success) {
                if (data.redirect) {
                    window.location.href = data.redirect;
                    return;
                }

                this.updateComponent(componentId, data.html);

                if (data.event) {
                    const event = new CustomEvent(data.event, {
                        bubbles: true,
                        detail: data.eventPayload ?? null
                    });
                    document.dispatchEvent(event);
                }

                if (data.state && data.state.errors) {
                    this.displayValidationErrors(component.element, data.state.errors);
                }
            } else {
                if (data.html) {
                    this.updateComponent(componentId, data.html);
                }
                console.error('Component error:', data.error);
                this.showError(component.element, data.error);
            }
        } catch (error) {
            console.error('LiveComponent error:', error);
            this.showError(component.element, error.message);
        } finally {
            component.loading = false;
            this.hideLoading(component.element);
        }
    }

    /**
     * Captures current state from component inputs.
     *
     * @param {Element} element - Component root element
     * @returns {Object} State object with field values
     */
    captureState(element) {
        const state = {};
        const inputs = element.querySelectorAll('input, textarea, select');
        inputs.forEach(input => {
            const key = input.getAttribute('name') || input.getAttribute('live:model');
            if (!key) return;

            if (input.type === 'checkbox') {
                state[key] = input.checked;
            } else if (input.type === 'radio') {
                if (input.checked) {
                    state[key] = input.value;
                }
            } else {
                state[key] = input.value;
            }
        });

        return state;
    }

    /**
     * Updates component DOM using morphdom for surgical patching.
     * Only mutates the nodes that actually changed — preserves focus,
     * cursor position, scroll state, CSS animations, and event listeners
     * on unchanged nodes automatically.
     *
     * @param {string} componentId - Component identifier
     * @param {string} html - New HTML content from server
     */
    updateComponent(componentId, html) {
        const component = this.components.get(componentId);
        if (!component) return;

        if (!document.contains(component.element)) {
            console.warn('Component element no longer in DOM:', componentId);
            this.components.delete(componentId);
            return;
        }

        // morphdom patches the existing element in-place
        const updatedElement = morphdom(component.element, html, {
            /**
             * Called before an existing element is updated.
             * Skips update on the currently focused input to preserve
             * user typing (value, cursor, selection).
             */
            onBeforeElUpdated(fromEl, toEl) {
                // Don't touch the actively focused input
                if (fromEl === document.activeElement
                    && (fromEl.tagName === 'INPUT' || fromEl.tagName === 'TEXTAREA' || fromEl.tagName === 'SELECT')) {
                    return false;
                }
                return true;
            },

            /**
             * Called when a new element is added to the DOM.
             * Used to attach live:model and live:submit bindings
             * on freshly created nodes.
             */
            onNodeAdded: (node) => {
                if (node.nodeType !== 1) return node;

                // Attach bindings on new nodes that have live: attributes
                if (node.hasAttribute && node.hasAttribute('live:model') && !node._liveModelBound) {
                    this.attachModelBindings(node.parentElement || component.element, componentId);
                }
                if (node.hasAttribute && node.hasAttribute('live:submit') && !node._liveSubmitBound) {
                    this.attachSubmit(node.parentElement || component.element, componentId);
                }

                return node;
            }
        });

        // Update the reference — morphdom may return the same or a new root
        this.components.set(componentId, {
            element: updatedElement,
            loading: false,
            pollInterval: component.pollInterval
        });
    }

    /**
     * Displays validation errors from server response.
     *
     * @param {Element} element - Component root element
     * @param {Object} errors - Validation errors map (field -> message)
     */
    displayValidationErrors(element, errors) {
        if (!errors || typeof errors !== 'object') return;

        Object.entries(errors).forEach(([field, message]) => {
            const input = element.querySelector(`[name="${field}"], [live\\:model="${field}"]`);

            if (input) {
                input.classList.add('is-invalid', 'border-red-500');

                let errorElement = input.parentElement.querySelector('.error-message, .validation-error');

                if (!errorElement) {
                    errorElement = document.createElement('span');
                    errorElement.className = 'error-message validation-error text-red-500 text-sm mt-1';
                    errorElement.setAttribute('data-validation-error', field);
                    input.parentElement.insertBefore(errorElement, input.nextSibling);
                }

                errorElement.textContent = message;
                errorElement.style.display = 'block';
            }
        });
    }

    /**
     * Clears all validation errors from component.
     *
     * @param {Element} element - Component root element
     */
    clearValidationErrors(element) {
        element.querySelectorAll('.is-invalid, .border-red-500').forEach(input => {
            input.classList.remove('is-invalid', 'border-red-500');
        });

        element.querySelectorAll('[data-validation-error]').forEach(error => {
            error.remove();
        });
    }

    /**
     * Shows loading indicators in component.
     *
     * @param {Element} element - Component root element
     */
    showLoading(element) {
        const loadingIndicators = element.querySelectorAll('[live\\:loading]');
        loadingIndicators.forEach(indicator => {
            const classList = indicator.getAttribute('live:loading.class');
            const addClasses = indicator.getAttribute('live:loading.add');
            const removeClasses = indicator.getAttribute('live:loading.remove');

            if (classList) {
                classList.split(' ').forEach(cls => indicator.classList.add(cls));
            } else if (addClasses || removeClasses) {
                if (addClasses) {
                    addClasses.split(' ').forEach(cls => indicator.classList.add(cls));
                }
                if (removeClasses) {
                    removeClasses.split(' ').forEach(cls => indicator.classList.remove(cls));
                }
            } else {
                indicator.style.display = '';
            }
        });

        const buttons = element.querySelectorAll('button[live\\:click], [live\\:click]');
        buttons.forEach(btn => {
            btn.disabled = true;
            btn.style.opacity = '0.6';
            btn.style.cursor = 'not-allowed';
        });
    }

    /**
     * Hides loading indicators in component.
     *
     * @param {Element} element - Component root element
     */
    hideLoading(element) {
        const loadingIndicators = element.querySelectorAll('[live\\:loading]');
        loadingIndicators.forEach(indicator => {
            const classList = indicator.getAttribute('live:loading.class');
            const addClasses = indicator.getAttribute('live:loading.add');
            const removeClasses = indicator.getAttribute('live:loading.remove');

            if (classList) {
                classList.split(' ').forEach(cls => indicator.classList.remove(cls));
            } else if (addClasses || removeClasses) {
                if (addClasses) {
                    addClasses.split(' ').forEach(cls => indicator.classList.remove(cls));
                }
                if (removeClasses) {
                    removeClasses.split(' ').forEach(cls => indicator.classList.add(cls));
                }
            } else {
                indicator.style.display = 'none';
            }
        });

        const buttons = element.querySelectorAll('button[live\\:click], [live\\:click]');
        buttons.forEach(btn => {
            btn.disabled = false;
            btn.style.opacity = '';
            btn.style.cursor = '';
        });
    }

    /**
     * Shows error message in component.
     *
     * @param {Element} element - Component root element
     * @param {string} message - Error message to display
     */
    showError(element, message) {
        const errorDiv = document.createElement('div');
        errorDiv.style.cssText = 'background: #ef4444; color: white; padding: 1rem; margin: 1rem 0; border-radius: 0.5rem;';
        errorDiv.textContent = 'Error: ' + message;
        element.insertBefore(errorDiv, element.firstChild);
        setTimeout(() => errorDiv.remove(), 5000);
    }

    /**
     * Retrieves CSRF token from meta tag or cookies.
     *
     * @returns {string|null} CSRF token or null if not found
     */
    getCsrfToken() {
        const meta = document.querySelector('meta[name="csrf-token"]');
        if (meta && meta.content) return meta.content;

        const cookies = document.cookie.split(';');
        for (let cookie of cookies) {
            const [name, value] = cookie.trim().split('=');
            if (name === 'CSRF-TOKEN' || name === '_csrf') {
                return decodeURIComponent(value);
            }
        }
        return null;
    }

    /**
     * Parses action string with method call syntax.
     *
     * @param {string} actionString - Action string to parse
     * @returns {{name: string, params: Array}} Parsed action
     */
    parseAction(actionString) {
        const match = actionString.match(/^(\w+)\((.*)\)$/);

        if (!match) {
            return { name: actionString, params: [] };
        }

        const name = match[1];
        const paramsString = match[2];

        if (!paramsString.trim()) {
            return { name, params: [] };
        }

        const params = [];
        let current = '';
        let inString = false;
        let stringChar = null;

        for (let i = 0; i < paramsString.length; i++) {
            const char = paramsString[i];

            if ((char === '"' || char === "'") && paramsString[i-1] !== '\\') {
                if (!inString) {
                    inString = true;
                    stringChar = char;
                } else if (char === stringChar) {
                    inString = false;
                    stringChar = null;
                }
                continue;
            }

            if (char === ',' && !inString) {
                params.push(this.parseValue(current.trim()));
                current = '';
            } else {
                current += char;
            }
        }

        if (current.trim()) {
            params.push(this.parseValue(current.trim()));
        }

        return { name, params };
    }

    /**
     * Parses a parameter value to appropriate JavaScript type.
     *
     * @param {string} value - Value string to parse
     * @returns {*} Parsed value
     */
    parseValue(value) {
        if (value === 'true') return true;
        if (value === 'false') return false;
        if (value === 'null') return null;
        if (!isNaN(value) && value !== '') return Number(value);
        return value;
    }
}

/**
 * Initializes ObsidianComponents when DOM is ready.
 */
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        window.ObsidianComponents = new ObsidianComponents();
    });
} else {
    window.ObsidianComponents = new ObsidianComponents();
}