package com.example.engine.translation

import org.json.JSONObject

/**
 * Non-invasive client-side DOM translation helpers.
 * Never executes remote untrusted scripts and does not expose privileged native APIs.
 */
object WebpageTranslationHelper {

    /**
     * Script to extract prominent text snippets from the webpage DOM for translation.
     */
    fun generateExtractTextNodesScript(): String {
        return """
            (function() {
                var texts = [];
                var walk = document.createTreeWalker(
                    document.body,
                    NodeFilter.SHOW_TEXT,
                    {
                        acceptNode: function(node) {
                            if (!node.parentElement) return NodeFilter.FILTER_REJECT;
                            var tag = node.parentElement.tagName.toLowerCase();
                            if (tag === 'script' || tag === 'style' || tag === 'noscript' || tag === 'textarea' || tag === 'code' || tag === 'pre') {
                                return NodeFilter.FILTER_REJECT;
                            }
                            var trimmed = node.nodeValue.trim();
                            if (trimmed.length > 2 && trimmed.length < 500) {
                                return NodeFilter.FILTER_ACCEPT;
                            }
                            return NodeFilter.FILTER_SKIP;
                        }
                    },
                    false
                );
                var node;
                while (node = walk.nextNode()) {
                    var val = node.nodeValue.trim();
                    if (texts.indexOf(val) === -1) {
                        texts.push(val);
                        if (texts.length >= 100) break; // Limit batch size for memory & responsiveness
                    }
                }
                return JSON.stringify(texts);
            })();
        """.trimIndent()
    }

    /**
     * Injects translated strings in-place while saving the original in data-mf-original.
     */
    fun generateApplyTranslationScript(translationMap: Map<String, String>): String {
        val jsonDict = JSONObject(translationMap).toString()
        return """
            (function() {
                var dict = $jsonDict;
                var walk = document.createTreeWalker(
                    document.body,
                    NodeFilter.SHOW_TEXT,
                    null,
                    false
                );
                var node;
                while (node = walk.nextNode()) {
                    var val = node.nodeValue.trim();
                    if (val && dict[val]) {
                        var parent = node.parentElement;
                        if (parent && !parent.getAttribute('data-mf-original')) {
                            parent.setAttribute('data-mf-original', node.nodeValue);
                        }
                        node.nodeValue = dict[val];
                    }
                }
            })();
        """.trimIndent()
    }

    /**
     * Restores the page to its original state by swapping back data-mf-original text.
     */
    fun generateRestoreScript(): String {
        return """
            (function() {
                var elements = document.querySelectorAll('[data-mf-original]');
                for (var i = 0; i < elements.length; i++) {
                    var orig = elements[i].getAttribute('data-mf-original');
                    if (orig) {
                        elements[i].textContent = orig;
                        elements[i].removeAttribute('data-mf-original');
                    }
                }
            })();
        """.trimIndent()
    }

    /**
     * Extracts selected text highlighted by the user.
     */
    fun generateGetSelectedTextScript(): String {
        return """
            (function() {
                var sel = window.getSelection();
                return sel ? sel.toString().trim() : '';
            })();
        """.trimIndent()
    }
}
