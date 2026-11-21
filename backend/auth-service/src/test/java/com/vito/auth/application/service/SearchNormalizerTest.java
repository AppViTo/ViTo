package com.vito.auth.application.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** T021 (Ola 2a, research R3): normalización de nombres y del texto de búsqueda. */
class SearchNormalizerTest {

    @Test
    void displayNameWordsLoseAccentsAndCase() {
        assertEquals(List.of("jose", "perez"), SearchNormalizer.terms("José Pérez"));
    }

    @Test
    void repeatedAndEdgeSpacesAreDropped() {
        assertEquals(List.of("ana", "maria", "lopez"), SearchNormalizer.terms("  Ana   María\tLópez  "));
    }

    @Test
    void repeatedWordsAreKeptOnce() {
        assertEquals(List.of("ana", "ana.b"), SearchNormalizer.terms("Ana ana ANA.b"));
    }

    @Test
    void missingDisplayNameHasNoTerms() {
        assertEquals(List.of(), SearchNormalizer.terms(null));
        assertEquals(List.of(), SearchNormalizer.terms("   "));
    }

    @Test
    void usernameIsLowercaseWithoutAccents() {
        assertEquals("caro.mz", SearchNormalizer.username("Caro.MZ"));
        assertEquals("nunez_1", SearchNormalizer.username("Núñez_1"));
        assertNull(SearchNormalizer.username(null));
    }

    @Test
    void queryDropsOneLeadingAtSign() {
        assertEquals("caro.mz", SearchNormalizer.query("@Caro.MZ"));
        assertEquals("@caro", SearchNormalizer.query("@@caro"));
        assertEquals("caro", SearchNormalizer.query("  @caro  "));
    }

    @Test
    void queryLosesAccentsAndCollapsesSpaces() {
        assertEquals("jose perez", SearchNormalizer.query("  JOSÉ   Pérez "));
    }

    /** Se conserva tal cual: el adaptador lo escapa antes de usarlo como prefijo. */
    @Test
    void specialCharactersAndEmojisStayLiteral() {
        assertEquals(".*", SearchNormalizer.query(".*"));
        assertEquals("\"o'neil\"", SearchNormalizer.query("\"O'Neil\""));
        assertEquals("ana 🎉", SearchNormalizer.query("Ana 🎉"));
    }

    @Test
    void nullQueryIsEmpty() {
        assertEquals("", SearchNormalizer.query(null));
    }

    @Test
    void lengthCountsCharactersNotUtf16Units() {
        assertEquals(2, SearchNormalizer.length("🎉🎉"));
        assertEquals(2, SearchNormalizer.length("jo"));
    }
}
