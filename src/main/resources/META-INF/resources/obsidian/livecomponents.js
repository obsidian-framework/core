/**
 * Obsidian LiveComponents - Client-side reactive component system
 * 
 * Manages server-side reactive components with automatic DOM updates,
 * real-time validation, and seamless state synchronization.
 * 
 * @class ObsidianComponents
 * @version 1.0.0
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
     * - live:lazy - Update only on Enter key
     * 
     * @param {Element} element - Component root element
     * @param {string} componentId - Component identifier
     */
    attachModelBindings(element, componentId) {
        // Find all inputs with live:model
        const modelInputs = element.querySelectorAll('[live\\:model]');

        modelInputs.forEach(input => {
            const fieldName = input.getAttribute('live:model');
            const debounceTime = parseInt(input.getAttribute('live:debounce')) || 300;
            const updateOnBlur = input.hasAttribute('live:blur');
            const updateOnEnter = input.hasAttribute('live:lazy');

            if (updateOnBlur) {
                // Update only on blur
                input.addEventListener('blur', () => {
                    this.updateModel(componentId, fieldName, input.value);
                });
            } else if (updateOnEnter) {
                // Update only on Enter key
                input.addEventListener('keydown', (e) => {
                    if (e.key === 'Enter') {
                        e.preventDefault();
                        this.updateModel(componentId, fieldName, input.value);
                    }
                });
            } else {
                // Update on input with debounce (default)
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
     * Usage: <form live:submit="submitForm">
     * 
     * @param {Element} element - Component root element
     * @param {string} componentId - Component identifier
     */
    attachSubmit(element, componentId) {
        // Find forms with live:submit
        const forms = element.querySelectorAll('[live\\:submit]');
        
        forms.forEach(form => {
            const action = form.getAttribute('live:submit');
            
            form.addEventListener('submit', (e) => {
                e.preventDefault();
                
                // Clear previous validation errors
                this.clearValidationErrors(element);
                
                this.call(componentId, action);
            });
        });
    }

    /**
     * Updates a model field value on the server.
     * Called automatically by live:model bindings.
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
     * Used for live:model inputs to avoid excessive server calls.
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

        // Parse interval: "5000" or "5s" or "5m"
        let interval = parseInt(pollAttr);
        if (pollAttr.endsWith('s')) {
            interval = parseInt(pollAttr) * 1000;
        } else if (pollAttr.endsWith('m')) {
            interval = parseInt(pollAttr) * 60000;
        }

        // Get optional action name (live:poll.5s="refreshStats")
        let action = null;
        for (let attr of element.attributes) {
            if (attr.name.startsWith('live:poll.') && !attr.name.includes('live:poll.class')) {
                action = attr.value;
                break;
            }
        }

        const component = this.components.get(componentId);
        if (component) {
            // Clear existing interval if any
            if (component.pollInterval) {
                clearInterval(component.pollInterval);
            }

            // Set new polling interval
            component.pollInterval = setInterval(() => {
                if (!document.contains(element)) {
                    // Component removed from DOM, stop polling
                    clearInterval(component.pollInterval);
                    return;
                }

                // Call action or just refresh
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
     * Used with [live:init] attribute.
     * 
     * Usage: <div live:init="loadData">
     * 
     * @param {Element} element - Component root element
     * @param {string} componentId - Component identifier
     */
    attachInit(element, componentId) {
        const initAction = element.getAttribute('live:init');
        if (initAction) {
            // Call init action after a short delay to ensure component is mounted
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
     * @param {string} action - Action method name (e.g., "submit", "delete(42)")
     * @param {Object} customParams - Optional custom parameters
     * @param {string} customParams.field - Field name for model updates
     * @param {*} customParams.value - Field value for model updates
     * @returns {Promise<void>}
     */
    async call(componentId, action, customParams = {}) {
        const component = this.components.get(componentId);
        if (!component || component.loading) return;

        try {
            component.loading = true;
            this.showLoading(component.element);
            
            // Clear validation errors before call
            this.clearValidationErrors(component.element);

            const state = this.captureState(component.element);

            // Parse action name and parameters from string like "vote('Functional')"
            const parsed = this.parseAction(action);

            // Merge parsed params with custom params (for __updateModel)
            const finalParams = customParams.field ?
                [customParams.value] : // For __updateModel, pass value directly
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
                this.updateComponent(componentId, data.html);
                
                // Check if component has validation errors in new state
                if (data.state && data.state.errors) {
                    this.displayValidationErrors(component.element, data.state.errors);
                }
            } else {
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
     * Collects values from all inputs, textareas, and selects.
     * 
     * @param {Element} element - Component root element
     * @returns {Object} State object with field values
     */
    captureState(element) {
        const state = {};

        // Capture all input, textarea, select values
        const inputs = element.querySelectorAll('input, textarea, select');
        inputs.forEach(input => {
            const id = input.id;
            if (id) {
                // Use ID as key
                const key = id.replace(/^.*-/, ''); // Remove component ID prefix
                if (input.type === 'checkbox') {
                    state[key] = input.checked;
                } else if (input.type === 'radio') {
                    if (input.checked) {
                        state[input.name] = input.value;
                    }
                } else {
                    state[key] = input.value;
                }
            }
        });

        return state;
    }

    /**
     * Updates component DOM with new HTML from server.
     * Replaces element and re-attaches event listeners.
     * 
     * @param {string} componentId - Component identifier
     * @param {string} html - New HTML content
     */
    updateComponent(componentId, html) {
        const component = this.components.get(componentId);
        if (!component) return;

        // Check if element is still in the DOM
        if (!document.contains(component.element)) {
            console.warn('Component element no longer in DOM:', componentId);
            this.components.delete(componentId);
            return;
        }

        // Check if element has a parent
        if (!component.element.parentNode) {
            console.warn('Component element has no parent:', componentId);
            this.components.delete(componentId);
            return;
        }

        // Replace the element
        try {
            const parent = component.element.parentNode;
            const tempDiv = document.createElement('div');
            tempDiv.innerHTML = html;
            const newElement = tempDiv.firstElementChild;

            parent.replaceChild(newElement, component.element);

            // Update reference and re-discover
            this.components.set(componentId, {
                element: newElement,
                loading: false,
                pollInterval: component.pollInterval
            });

            // Re-attach event listeners for the new element
            this.attachModelBindings(newElement, componentId);
            this.attachSubmit(newElement, componentId);
        } catch (error) {
            console.error('Failed to update component:', componentId, error);
        }
    }

    /**
     * Displays validation errors from server response.
     * Adds error classes to inputs and shows error messages.
     * 
     * Error display:
     * - Adds 'is-invalid' and 'border-red-500' classes to inputs
     * - Creates error message spans with class 'error-message'
     * - Automatically clears on next action
     * 
     * @param {Element} element - Component root element
     * @param {Object} errors - Validation errors map (field -> message)
     */
    displayValidationErrors(element, errors) {
        // errors is a Map<String, String> from server
        if (!errors || typeof errors !== 'object') return;

        Object.entries(errors).forEach(([field, message]) => {
            // Find input by name or live:model attribute
            const input = element.querySelector(`[name="${field}"], [live\\:model="${field}"]`);
            
            if (input) {
                // Add error class to input
                input.classList.add('is-invalid', 'border-red-500');
                
                // Find or create error message element
                let errorElement = input.parentElement.querySelector('.error-message, .validation-error');
                
                if (!errorElement) {
                    errorElement = document.createElement('span');
                    errorElement.className = 'error-message validation-error text-red-500 text-sm mt-1';
                    errorElement.setAttribute('data-validation-error', field);
                    
                    // Insert after input
                    input.parentElement.insertBefore(errorElement, input.nextSibling);
                }
                
                errorElement.textContent = message;
                errorElement.style.display = 'block';
            }
        });
    }

    /**
     * Clears all validation errors from component.
     * Removes error classes and error message elements.
     * 
     * @param {Element} element - Component root element
     */
    clearValidationErrors(element) {
        // Remove error classes from inputs
        element.querySelectorAll('.is-invalid, .border-red-500').forEach(input => {
            input.classList.remove('is-invalid', 'border-red-500');
        });
        
        // Remove error messages
        element.querySelectorAll('[data-validation-error]').forEach(error => {
            error.remove();
        });
    }

    /**
     * Shows loading indicators in component.
     * 
     * Supports multiple indicator types:
     * - [live:loading] - Default: shows element
     * - [live:loading.class="spinner"] - Adds specific classes
     * - [live:loading.add="opacity-50"] - Adds classes during loading
     * - [live:loading.remove="hidden"] - Removes classes during loading
     * 
     * Also disables all buttons with [live:click] during loading.
     * 
     * @param {Element} element - Component root element
     */
    showLoading(element) {
        // Show loading indicators (display style)
        const loadingIndicators = element.querySelectorAll('[live\\:loading]');
        loadingIndicators.forEach(indicator => {
            // Check if it has .class modifier
            const classList = indicator.getAttribute('live:loading.class');
            const addClasses = indicator.getAttribute('live:loading.add');
            const removeClasses = indicator.getAttribute('live:loading.remove');

            if (classList) {
                // Add specific classes
                classList.split(' ').forEach(cls => indicator.classList.add(cls));
            } else if (addClasses || removeClasses) {
                if (addClasses) {
                    addClasses.split(' ').forEach(cls => indicator.classList.add(cls));
                }
                if (removeClasses) {
                    removeClasses.split(' ').forEach(cls => indicator.classList.remove(cls));
                }
            } else {
                // Default: show by changing display
                indicator.style.display = '';
            }
        });

        // Disable buttons
        const buttons = element.querySelectorAll('button[live\\:click], [live\\:click]');
        buttons.forEach(btn => {
            btn.disabled = true;
            btn.style.opacity = '0.6';
            btn.style.cursor = 'not-allowed';
        });
    }

    /**
     * Hides loading indicators in component.
     * Reverses changes made by showLoading().
     * Re-enables all buttons.
     * 
     * @param {Element} element - Component root element
     */
    hideLoading(element) {
        // Hide loading indicators
        const loadingIndicators = element.querySelectorAll('[live\\:loading]');
        loadingIndicators.forEach(indicator => {
            const classList = indicator.getAttribute('live:loading.class');
            const addClasses = indicator.getAttribute('live:loading.add');
            const removeClasses = indicator.getAttribute('live:loading.remove');

            if (classList) {
                // Remove specific classes
                classList.split(' ').forEach(cls => indicator.classList.remove(cls));
            } else if (addClasses || removeClasses) {
                if (addClasses) {
                    addClasses.split(' ').forEach(cls => indicator.classList.remove(cls));
                }
                if (removeClasses) {
                    removeClasses.split(' ').forEach(cls => indicator.classList.add(cls));
                }
            } else {
                // Default: hide by changing display
                indicator.style.display = 'none';
            }
        });

        // Re-enable buttons
        const buttons = element.querySelectorAll('button[live\\:click], [live\\:click]');
        buttons.forEach(btn => {
            btn.disabled = false;
            btn.style.opacity = '';
            btn.style.cursor = '';
        });
    }

    /**
     * Shows error message in component.
     * Creates a temporary error banner that auto-dismisses after 5 seconds.
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
     * Checks in order:
     * 1. <meta name="csrf-token"> tag
     * 2. CSRF-TOKEN cookie
     * 3. _csrf cookie
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
     * Supported formats:
     * - "increment" → { name: "increment", params: [] }
     * - "delete(42)" → { name: "delete", params: [42] }
     * - "vote('Functional')" → { name: "vote", params: ["Functional"] }
     * - "update('name', 'John')" → { name: "update", params: ["name", "John"] }
     * 
     * @param {string} actionString - Action string to parse
     * @returns {{name: string, params: Array}} Parsed action with name and parameters
     */
    parseAction(actionString) {
        // Parse "vote('Functional')" or "deleteTodo(42)" or "update('name', 'John')"
        const match = actionString.match(/^(\w+)\((.*)\)$/);

        if (!match) {
            // Simple action without params: "increment"
            return { name: actionString, params: [] };
        }

        const name = match[1];
        const paramsString = match[2];

        if (!paramsString.trim()) {
            // Empty params: "reset()"
            return { name, params: [] };
        }

        // Parse parameters
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
     * Type detection:
     * - 'true' → boolean true
     * - 'false' → boolean false
     * - 'null' → null
     * - Numeric string → Number
     * - Other → String (as-is)
     * 
     * @param {string} value - Value string to parse
     * @returns {*} Parsed value with appropriate type
     */
    parseValue(value) {
        // Parse different types: 'string', 42, true, false, null
        if (value === 'true') return true;
        if (value === 'false') return false;
        if (value === 'null') return null;
        if (!isNaN(value) && value !== '') return Number(value);
        return value; // String
    }
}

/**
 * Initializes ObsidianComponents when DOM is ready.
 * Creates global instance accessible via window.ObsidianComponents.
 */
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        window.ObsidianComponents = new ObsidianComponents();
    });
} else {
    window.ObsidianComponents = new ObsidianComponents();
}
