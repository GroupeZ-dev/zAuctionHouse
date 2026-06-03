package fr.maxlego08.zauctionhouse.api.filter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchTextMatcherTest {

    @Test
    void containsIgnoreCaseMatchesSmallCaps() {
        assertTrue(SearchTextMatcher.matches("sᴀɴᴅᴀʟʏ ᴘᴏsʟᴀɴᴄᴀ", SearchFilterType.CONTAINS_IGNORE_CASE, "sandaly"));
    }

    @Test
    void equalsIgnoreCaseMatchesSmallCaps() {
        assertTrue(SearchTextMatcher.matches("sᴀɴᴅᴀʟʏ", SearchFilterType.EQUALS_IGNORE_CASE, "sandaly"));
    }

    @Test
    void caseSensitiveContainsKeepsExistingBehavior() {
        assertFalse(SearchTextMatcher.matches("Diamond Sword", SearchFilterType.CONTAINS, "diamond"));
    }

    @Test
    void exactMatchKeepsExistingBehavior() {
        assertFalse(SearchTextMatcher.matches("sᴀɴᴅᴀʟʏ", SearchFilterType.EQUALS, "sandaly"));
    }
}
