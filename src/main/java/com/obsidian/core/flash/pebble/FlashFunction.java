package com.obsidian.core.flash.pebble;

import com.obsidian.core.flash.FlashConfig;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pebble function for rendering flash notifications.
 * Generates HTML and JavaScript for displaying toast-style messages.
 */
public class FlashFunction implements Function
{
    /**
     * Builds JavaScript and CSS for flash notification system.
     *
     * @return Complete script and style tags
     */
    private static String buildFlashScript()
    {
        String customCSS = FlashConfig.getCustomCSS();
        String positionCSS = FlashConfig.getPositionCSS();
        int duration = FlashConfig.getDuration();

        return String.format("""
            <style id="flash-notification-styles">
            .flash-notification {
                position: fixed;
                %s
                padding: 1rem 1.5rem;
                border-radius: 0.5rem;
                box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
                z-index: 9999;
                font-weight: 600;
                display: flex;
                align-items: center;
                gap: 0.75rem;
                color: white;
                font-family: system-ui, -apple-system, sans-serif;
                max-width: 400px;
            }
            .flash-notification svg {
                width: 1.25rem;
                height: 1.25rem;
                flex-shrink: 0;
            }
            .flash-success {
                background: linear-gradient(to right, #ea580c, #dc2626);
                box-shadow: 0 25px 50px -12px rgba(234, 88, 12, 0.5);
            }
            .flash-error {
                background: linear-gradient(to right, #dc2626, #991b1b);
                box-shadow: 0 25px 50px -12px rgba(220, 38, 38, 0.5);
            }
            .flash-info {
                background: linear-gradient(to right, #2563eb, #1e40af);
                box-shadow: 0 25px 50px -12px rgba(37, 99, 235, 0.5);
            }
            .flash-warning {
                background: linear-gradient(to right, #ca8a04, #ea580c);
                box-shadow: 0 25px 50px -12px rgba(202, 138, 4, 0.5);
            }
            @keyframes flash-slide-in {
                from { transform: translateX(400px); opacity: 0; }
                to { transform: translateX(0); opacity: 1; }
            }
            @keyframes flash-slide-out {
                from { transform: translateX(0); opacity: 1; }
                to { transform: translateX(400px); opacity: 0; }
            }
            .flash-animate-in { animation: flash-slide-in 0.3s ease-out; }
            .flash-animate-out { animation: flash-slide-out 0.3s ease-out; }
            %s
            </style>
            <script>
            (function() {
                'use strict';
                
                var FLASH_DURATION = %d;
                
                function showNotification(message, type) {
                    const notification = document.createElement('div');
                    let icon = '';
                    
                    if (type === 'success') {
                        icon = '<svg fill="currentColor" viewBox="0 0 20 20"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"/></svg>';
                    } else if (type === 'error') {
                        icon = '<svg fill="currentColor" viewBox="0 0 20 20"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clip-rule="evenodd"/></svg>';
                    } else if (type === 'info') {
                        icon = '<svg fill="currentColor" viewBox="0 0 20 20"><path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clip-rule="evenodd"/></svg>';
                    } else if (type === 'warning') {
                        icon = '<svg fill="currentColor" viewBox="0 0 20 20"><path fill-rule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clip-rule="evenodd"/></svg>';
                    }
                    
                    notification.className = 'flash-notification flash-' + type + ' flash-animate-in';
                    notification.innerHTML = icon + '<span>' + message + '</span>';
                    
                    document.body.appendChild(notification);
                    
                    setTimeout(function() {
                        notification.classList.remove('flash-animate-in');
                        notification.classList.add('flash-animate-out');
                        setTimeout(function() {
                            if (notification.parentNode) {
                                document.body.removeChild(notification);
                            }
                        }, 300);
                    }, FLASH_DURATION);
                }
                
                if (document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', initFlashes);
                } else {
                    initFlashes();
                }
                
                function initFlashes() {
                    const flashes = [
                        {el: document.querySelector('[data-flash-success]'), type: 'success'},
                        {el: document.querySelector('[data-flash-error]'), type: 'error'},
                        {el: document.querySelector('[data-flash-info]'), type: 'info'},
                        {el: document.querySelector('[data-flash-warning]'), type: 'warning'}
                    ];
                    
                    flashes.forEach(function(flash) {
                        if (flash.el) {
                            const message = flash.el.dataset['flash' + flash.type.charAt(0).toUpperCase() + flash.type.slice(1)];
                            if (message && message.trim()) {
                                showNotification(message, flash.type);
                            }
                        }
                    });
                }
                
                window.showNotification = showNotification;
            })();
            </script>
            """, positionCSS, customCSS, duration);
    }

    /**
     * Executes flash function in template context.
     * Renders hidden data elements and injects notification script.
     *
     * @param args Function arguments (unused)
     * @param self Template instance
     * @param context Evaluation context containing flash messages
     * @param lineNumber Line number in template
     * @return HTML string with notifications
     */
    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber)
    {
        @SuppressWarnings("unchecked")
        Map<String, String> flashes = (Map<String, String>) context.getVariable("flashes");

        if (flashes == null || flashes.isEmpty()) {
            return "";
        }

        StringBuilder html = new StringBuilder();

        if (flashes.containsKey("success")) {
            html.append(String.format("<div data-flash-success=\"%s\" style=\"display:none;\"></div>%n", escapeHtml(flashes.get("success"))));
        }

        if (flashes.containsKey("error")) {
            html.append(String.format("<div data-flash-error=\"%s\" style=\"display:none;\"></div>%n", escapeHtml(flashes.get("error"))));
        }

        if (flashes.containsKey("info")) {
            html.append(String.format("<div data-flash-info=\"%s\" style=\"display:none;\"></div>%n", escapeHtml(flashes.get("info"))));
        }

        if (flashes.containsKey("warning")) {
            html.append(String.format("<div data-flash-warning=\"%s\" style=\"display:none;\"></div>%n", escapeHtml(flashes.get("warning"))));
        }

        html.append(buildFlashScript());
        return html.toString();
    }

    /**
     * Returns empty list (no arguments required).
     *
     * @return Empty argument list
     */
    @Override
    public List<String> getArgumentNames()
    {
        return new ArrayList<>();
    }

    /**
     * Escapes HTML special characters to prevent XSS.
     *
     * @param text Text to escape
     * @return Escaped text
     */
    private String escapeHtml(String text)
    {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}