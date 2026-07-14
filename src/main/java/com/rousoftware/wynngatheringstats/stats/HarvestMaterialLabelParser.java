package com.rousoftware.wynngatheringstats.stats;

import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HarvestMaterialLabelParser {
    private static final String STAR_GLYPHS = "✫★";
    private static final Pattern HARVEST_LINE_PATTERN = Pattern.compile(
            "^(?:\\[[^\\]]+]\\s*)?\\+(?<amount>\\d+)\\s+(?<material>.+?)(?:\\s+\\[(?<stars>["
                    + STAR_GLYPHS
                    + "]{3})\\])?\\s*$");

    private HarvestMaterialLabelParser() {}

    public static OptionalInt parseAmount(String unformattedLabel) {
        java.util.Optional<Matcher> harvestLine = parseHarvestLine(unformattedLabel);
        if (harvestLine.isEmpty()) {
            return OptionalInt.empty();
        }

        int amount = parsePositiveAmount(harvestLine.orElseThrow());
        return amount > 0 ? OptionalInt.of(amount) : OptionalInt.empty();
    }

    public static java.util.Optional<String> parseMaterialName(String unformattedLabel) {
        return parseHarvestLine(unformattedLabel)
                .map(matcher -> matcher.group("material").trim())
                .filter(name -> !name.isEmpty());
    }

    public static HarvestLabelParseResult analyze(String unformattedLabel, String formattedLabel) {
        return new HarvestLabelParseResult(
                parseAmount(unformattedLabel), parseMaterialName(unformattedLabel), parseTier(formattedLabel));
    }

    public static OptionalInt parseTier(String formattedLabel) {
        if (formattedLabel == null) {
            return OptionalInt.empty();
        }

        int starsStart = formattedLabel.lastIndexOf('[');
        if (starsStart < 0) {
            return OptionalInt.empty();
        }
        int starsEnd = formattedLabel.indexOf(']', starsStart);
        if (starsEnd < 0) {
            return OptionalInt.empty();
        }

        boolean active = false;
        int tier = 0;
        String stars = formattedLabel.substring(starsStart, starsEnd);
        for (int index = 0; index < stars.length(); index++) {
            char character = stars.charAt(index);
            if (character == '§' && index + 1 < stars.length()) {
                char color = Character.toLowerCase(stars.charAt(++index));
                if (color == 'e') {
                    active = true;
                } else if (color == '8') {
                    active = false;
                }
            } else if (isStar(character) && active) {
                tier++;
            }
        }

        return tier >= 1 && tier <= 3 ? OptionalInt.of(tier) : OptionalInt.empty();
    }

    private static java.util.Optional<Matcher> parseHarvestLine(String unformattedLabel) {
        if (unformattedLabel == null) {
            return java.util.Optional.empty();
        }

        String[] lines = unformattedLabel.split("\\R");
        for (int index = lines.length - 1; index >= 0; index--) {
            Matcher matcher = HARVEST_LINE_PATTERN.matcher(lines[index]);
            if (matcher.matches() && !matcher.group("material").contains(" XP")) {
                return java.util.Optional.of(matcher);
            }
        }
        return java.util.Optional.empty();
    }

    private static int parsePositiveAmount(Matcher matcher) {
        try {
            return Integer.parseInt(matcher.group("amount"));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static boolean isStar(char character) {
        return STAR_GLYPHS.indexOf(character) >= 0;
    }
}
