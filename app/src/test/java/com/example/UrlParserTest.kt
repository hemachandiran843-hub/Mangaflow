package com.example

import com.example.engine.browser.SearchEngine
import com.example.engine.browser.UrlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlParserTest {

    @Test
    fun parse_emptyInput_returnsAboutBlank() {
        assertEquals("about:blank", UrlParser.parse(""))
        assertEquals("about:blank", UrlParser.parse("   "))
    }

    @Test
    fun parse_explicitSchemes_preservesScheme() {
        assertEquals("https://www.webtoons.com", UrlParser.parse("https://www.webtoons.com"))
        assertEquals("http://example.com/test", UrlParser.parse("http://example.com/test"))
        assertEquals("about:blank", UrlParser.parse("about:blank"))
    }

    @Test
    fun parse_validDomains_prependsHttps() {
        assertEquals("https://webtoons.com", UrlParser.parse("webtoons.com"))
        assertEquals("https://mangadex.org/title/123", UrlParser.parse("mangadex.org/title/123"))
        assertEquals("https://sub.domain.co.uk:8080/path", UrlParser.parse("sub.domain.co.uk:8080/path"))
        assertEquals("https://localhost:3000", UrlParser.parse("localhost:3000"))
    }

    @Test
    fun parse_searchQueriesWithSpaces_routesToSearchEngine() {
        val query = "solo leveling manhwa chapter 10"
        val ddgResult = UrlParser.parse(query, SearchEngine.DUCKDUCKGO)
        assertTrue(ddgResult.startsWith("https://duckduckgo.com/?q="))
        assertTrue(ddgResult.contains("solo+leveling") || ddgResult.contains("solo%20leveling") || ddgResult.contains("solo+leveling+manhwa"))

        val googleResult = UrlParser.parse(query, SearchEngine.GOOGLE)
        assertTrue(googleResult.startsWith("https://www.google.com/search?q="))

        val bingResult = UrlParser.parse(query, SearchEngine.BING)
        assertTrue(bingResult.startsWith("https://www.bing.com/search?q="))
    }

    @Test
    fun parse_singleWordNonDomain_routesToSearchEngine() {
        val singleWord = "manhwa"
        val result = UrlParser.parse(singleWord, SearchEngine.DUCKDUCKGO)
        assertTrue(result.startsWith("https://duckduckgo.com/?q=manhwa"))
    }

    @Test
    fun extractDisplayHost_cleansWww() {
        assertEquals("webtoons.com", UrlParser.extractDisplayHost("https://www.webtoons.com/en/"))
        assertEquals("mangadex.org", UrlParser.extractDisplayHost("https://mangadex.org/title/1"))
    }
}
