package com.example.engine.browser

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Plain-text article content extracted from the active document. */
data class ReaderArticle(
    val title: String,
    val paragraphs: List<String>
) {
    val characterCount: Int get() = paragraphs.sumOf(String::length)
}

sealed interface ReaderExtractionResult {
    data class Success(val article: ReaderArticle) : ReaderExtractionResult
    data class Failure(val reason: String) : ReaderExtractionResult
}

/**
 * Read-only DOM extraction for Reader Mode. The fixed script only reads visible text and returns
 * JSON; it never replaces page content, calls page-provided functions, or exposes a native bridge.
 */
object ReaderModeExtractor {
    fun getReaderModeScript(): String = """
        (function() {
          try {
            function clean(value) {
              return (value || '').replace(new RegExp(String.fromCharCode(160), 'g'), ' ').replace(/[\t ]+/g, ' ').trim();
            }
            function visible(element) {
              if (!element) return false;
              var style = window.getComputedStyle(element);
              return style && style.display !== 'none' && style.visibility !== 'hidden' && style.opacity !== '0';
            }
            function excluded(element) {
              return !!(element.closest('nav,header,footer,aside,form,button,[role="navigation"],[aria-hidden="true"]') ||
                element.closest('script,style,noscript,svg,template'));
            }
            var selectors = [
              'article', 'main', '[role="main"]', '[itemprop="articleBody"]',
              '.article-body', '.article-content', '.post-content', '.entry-content',
              '.reading-content', '.chapter-content', '.content'
            ];
            var candidates = [];
            selectors.forEach(function(selector) {
              document.querySelectorAll(selector).forEach(function(element) {
                if (visible(element) && !candidates.includes(element)) candidates.push(element);
              });
            });
            if (document.body && visible(document.body)) candidates.push(document.body);
            var best = null;
            var bestScore = 0;
            candidates.forEach(function(element) {
              var text = clean(element.innerText || element.textContent || '');
              if (text.length < 100) return;
              var paragraphs = element.querySelectorAll('p').length;
              var links = clean(Array.from(element.querySelectorAll('a')).map(function(a) { return a.innerText || ''; }).join(' ')).length;
              var linkDensity = text.length ? Math.min(1, links / text.length) : 1;
              var score = Math.min(text.length, 20000) * (1 - linkDensity * 0.7) + paragraphs * 40;
              if (score > bestScore) { best = element; bestScore = score; }
            });
            if (!best) return JSON.stringify({ok:false, reason:'No readable article text was found on this page.'});

            var blocks = [];
            var seen = Object.create(null);
            var nodes = best.querySelectorAll('h1,h2,h3,h4,p,blockquote,pre,li');
            nodes.forEach(function(element) {
              if (!visible(element) || excluded(element)) return;
              var text = clean(element.innerText || element.textContent || '');
              if (text.length < 2 || seen[text]) return;
              seen[text] = true;
              blocks.push(text);
            });
            if (blocks.length < 2) {
              blocks = clean(best.innerText || best.textContent || '').split(/\n+/).map(clean).filter(function(text) { return text.length > 1; });
            }
            var limitedBlocks = [];
            var remaining = 300000;
            blocks.forEach(function(text) {
              if (remaining <= 0 || limitedBlocks.length >= 600) return;
              var part = text.slice(0, Math.min(8000, remaining));
              if (part.length > 1) { limitedBlocks.push(part); remaining -= part.length; }
            });
            blocks = limitedBlocks;
            var total = blocks.reduce(function(sum, item) { return sum + item.length; }, 0);
            if (total < 160) return JSON.stringify({ok:false, reason:'Not enough readable text was found to create Reader Mode.'});
            return JSON.stringify({
              ok:true,
              title:clean(document.title || ''),
              paragraphs:blocks,
              characters:total
            });
          } catch (error) {
            return JSON.stringify({ok:false, reason:'The page could not be safely simplified.'});
          }
        })();
    """.trimIndent()

    fun parseJavascriptResult(rawResult: String?): ReaderExtractionResult {
        if (rawResult.isNullOrBlank() || rawResult == "null") {
            return ReaderExtractionResult.Failure("Reader Mode could not read this page.")
        }
        if (rawResult.length > MAX_RAW_RESULT_CHARACTERS) {
            return ReaderExtractionResult.Failure("This page is too large to simplify safely.")
        }
        return try {
            val decoded = JSONTokener(rawResult).nextValue()
            val payload = when (decoded) {
                is JSONObject -> decoded
                is String -> JSONObject(decoded)
                else -> JSONObject(rawResult)
            }
            if (!payload.optBoolean("ok", false)) {
                return ReaderExtractionResult.Failure(
                    payload.optString("reason").ifBlank { "No readable text was found on this page." }
                )
            }
            val paragraphsJson = payload.optJSONArray("paragraphs") ?: JSONArray()
            val paragraphs = buildList {
                var remainingCharacters = MAX_TOTAL_CHARACTERS
                for (index in 0 until minOf(paragraphsJson.length(), MAX_PARAGRAPHS)) {
                    if (remainingCharacters <= 0) break
                    val text = paragraphsJson.optString(index).trim()
                    if (text.isNotEmpty()) {
                        val limitedText = text.take(minOf(MAX_PARAGRAPH_CHARACTERS, remainingCharacters))
                        add(limitedText)
                        remainingCharacters -= limitedText.length
                    }
                }
            }
            if (paragraphs.isEmpty()) {
                ReaderExtractionResult.Failure("No readable text was found on this page.")
            } else {
                ReaderExtractionResult.Success(
                    ReaderArticle(
                        title = payload.optString("title").take(MAX_TITLE_CHARACTERS),
                        paragraphs = paragraphs
                    )
                )
            }
        } catch (_: Exception) {
            ReaderExtractionResult.Failure("The page returned text that Reader Mode could not parse.")
        }
    }

    private const val MAX_PARAGRAPHS = 600
    private const val MAX_PARAGRAPH_CHARACTERS = 8_000
    private const val MAX_TOTAL_CHARACTERS = 300_000
    private const val MAX_RAW_RESULT_CHARACTERS = 2_000_000
    private const val MAX_TITLE_CHARACTERS = 300
}
