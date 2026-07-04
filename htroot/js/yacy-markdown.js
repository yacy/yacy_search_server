/*
 * yacy-markdown.js
 * Shared markdown rendering helpers, used by yacychat.html and LogReports_p.html.
 * Renders LLM-generated markdown to sanitized HTML: marked for parsing, a
 * sanitizer that strips active content (the source is model output, not trusted
 * input), and highlight.js for fenced code blocks. Style rules for the rendered
 * output live in env/markdown.css, scoped to the .markdown-body class.
 * Requires js/marked.umd.js; js/highlight.min.js is optional.
 */
(function (global) {
    "use strict";

    const markedOptions = {
        gfm: true,
        breaks: true,
        smartLists: true,
        mangle: false,
        headerIds: false
    };

    function escapeHTML(value) {
        const div = document.createElement('div');
        div.textContent = value || '';
        return div.innerHTML;
    }

    function sanitizeHTML(html) {
        if (!html) return '';
        const template = document.createElement('template');
        template.innerHTML = html;
        const blockedTags = new Set(['script', 'style', 'iframe', 'object', 'embed', 'link', 'meta']);
        const walker = document.createTreeWalker(template.content, NodeFilter.SHOW_ELEMENT, null);
        const toRemove = [];
        while (walker.nextNode()) {
            const el = walker.currentNode;
            if (!el || !el.tagName) continue;
            const tag = el.tagName.toLowerCase();
            if (blockedTags.has(tag)) {
                toRemove.push(el);
                continue;
            }
            for (const attr of Array.from(el.attributes)) {
                const name = attr.name.toLowerCase();
                const value = attr.value || '';
                if (name.startsWith('on')) {
                    el.removeAttribute(attr.name);
                    continue;
                }
                if ((name === 'href' || name === 'src') && /^\s*javascript:/i.test(value)) {
                    el.removeAttribute(attr.name);
                }
            }
        }
        toRemove.forEach(node => node.remove());
        return template.innerHTML;
    }

    /**
     * Render markdown source to sanitized HTML; falls back to escaped plain text
     * when marked is not available or parsing fails.
     * optionOverrides can adjust the shared marked options per call, e.g.
     * { breaks: false } for documents with hard-wrapped source lines where a
     * single newline must not become a visible line break.
     */
    function render(source, optionOverrides) {
        const text = typeof source === 'string' ? source : '';
        if (typeof marked === 'undefined') return escapeHTML(text);
        try {
            marked.setOptions(Object.assign({}, markedOptions, optionOverrides || {}));
            return sanitizeHTML(marked.parse(text));
        } catch (err) {
            console.warn('Markdown rendering failed', err);
            return escapeHTML(text);
        }
    }

    /** Apply highlight.js to all fenced code blocks below the given container. */
    function highlightAll(container) {
        if (!container || typeof hljs === 'undefined') return;
        hljs.configure({ ignoreUnescapedHTML: true });
        container.querySelectorAll('pre code').forEach(block => hljs.highlightElement(block));
    }

    global.YaCyMarkdown = { markedOptions, escapeHTML, sanitizeHTML, render, highlightAll };
})(window);
