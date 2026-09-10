package com.cargoplus.service;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Gera a animação do prefixo DEV e sempre converte o gradient para cores legadas RGB. */
public final class PrefixAnimationService {
    private static final Pattern GRADIENT = Pattern.compile("<gradient:(#[0-9a-fA-F]{6}):(#[0-9a-fA-F]{6})>(.*?)</gradient>", Pattern.DOTALL);
    private static final String DEV_TEXT = "ᴅᴇᴠ";

    private final boolean enabled;
    private final long intervalMillis;
    private final long letterWhiteMillis;
    private final long letterNormalMillis;
    private final long totalWhiteMillis;
    private final long totalNormalMillis;
    private final int totalBlinks;

    public PrefixAnimationService(FileConfiguration config) {
        var section = config.getConfigurationSection("animacoes.dev");
        if (section == null) {
            enabled = false;
            intervalMillis = 10_000L;
            letterWhiteMillis = 300L;
            letterNormalMillis = 150L;
            totalWhiteMillis = 250L;
            totalNormalMillis = 250L;
            totalBlinks = 3;
            return;
        }

        enabled = section.getBoolean("ativado", true);
        intervalMillis = bounded(section.getLong("intervalo-segundos", 10L) * 1000L, 1_000L, 86_400_000L);
        letterWhiteMillis = bounded(section.getLong("duracao-branco-letra-ms", 300L), 50L, 5_000L);
        letterNormalMillis = bounded(section.getLong("duracao-normal-letra-ms", 150L), 50L, 5_000L);
        totalWhiteMillis = bounded(section.getLong("duracao-branco-total-ms", 250L), 50L, 5_000L);
        totalNormalMillis = bounded(section.getLong("duracao-normal-total-ms", 250L), 50L, 5_000L);
        totalBlinks = Math.max(1, Math.min(10, section.getInt("piscadas-totais", 3)));
    }

    public String animate(String prefix, String group) {
        if (prefix == null || prefix.isBlank()) return "";

        Matcher matcher = GRADIENT.matcher(prefix);
        if (!matcher.find()) return prefix;

        String startHex = matcher.group(1);
        String endHex = matcher.group(2);
        String content = matcher.group(3);

        // Only DEV is animated. Other cargos still have their gradient converted
        // to a Bukkit-compatible legacy RGB prefix instead of leaking <gradient>.
        if (!enabled || group == null || !group.equalsIgnoreCase("dev") || !content.contains(DEV_TEXT)) {
            return buildPrefix(matcher, startHex, endHex, content, -1, false);
        }

        long letterPhase = 3L * (letterWhiteMillis + letterNormalMillis);
        long blinkPhase = 2L * totalBlinks * (totalWhiteMillis + totalNormalMillis);
        long animationDuration = letterPhase + blinkPhase;
        long cycleDuration = intervalMillis + animationDuration;
        long phase = Math.floorMod(System.currentTimeMillis(), cycleDuration);

        // During the idle interval, return the normal gradient — already converted.
        if (phase < intervalMillis) {
            return buildPrefix(matcher, startHex, endHex, content, -1, false);
        }

        long animationPhase = phase - intervalMillis;
        int whiteCharacter = -1;
        long cursor = animationPhase;

        for (int index = 0; index < 3; index++) {
            long slot = letterWhiteMillis + letterNormalMillis;
            if (cursor < slot) {
                whiteCharacter = cursor < letterWhiteMillis ? index : -1;
                return buildPrefix(matcher, startHex, endHex, content, whiteCharacter, false);
            }
            cursor -= slot;
        }

        long blinkSlot = totalWhiteMillis + totalNormalMillis;
        long blinkIndex = cursor / blinkSlot;
        long blinkOffset = cursor % blinkSlot;
        if (blinkIndex < totalBlinks) {
            boolean wholeWhite = blinkOffset < totalWhiteMillis;
            return buildPrefix(matcher, startHex, endHex, content, -1, wholeWhite);
        }

        return buildPrefix(matcher, startHex, endHex, content, -1, false);
    }

    private String buildPrefix(Matcher matcher, String startHex, String endHex, String content, int whiteCharacter, boolean wholeWhite) {
        int start = Integer.parseInt(startHex.substring(1), 16);
        int end = Integer.parseInt(endHex.substring(1), 16);
        int visibleCharacters = content.codePointCount(0, content.length());
        StringBuilder rendered = new StringBuilder("&l");
        int index = 0;

        for (int offset = 0; offset < content.length();) {
            int codePoint = content.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            if (character.equals("\n") || character.equals("\r")) {
                rendered.append(character);
            } else {
                boolean white = wholeWhite || isDevLetter(content, offset, character, whiteCharacter);
                int rgb = white ? 0xFFFFFF : interpolate(start, end, visibleCharacters <= 1 ? 0.0 : (double) index / (visibleCharacters - 1));
                rendered.append(toLegacyHex(rgb)).append(character);
                index++;
            }
            offset += Character.charCount(codePoint);
        }

        String replacement = rendered.toString();
        return matcher.replaceFirst(Matcher.quoteReplacement(replacement));
    }

    private boolean isDevLetter(String content, int offset, String character, int whiteCharacter) {
        if (whiteCharacter < 0) return false;
        int devIndex = 0;
        for (int currentOffset = 0; currentOffset < content.length();) {
            int codePoint = content.codePointAt(currentOffset);
            String current = new String(Character.toChars(codePoint));
            if (current.equals("ᴅ") || current.equals("ᴇ") || current.equals("ᴠ")) {
                if (currentOffset == offset) return devIndex == whiteCharacter;
                devIndex++;
            }
            currentOffset += Character.charCount(codePoint);
        }
        return false;
    }

    private static int interpolate(int start, int end, double progress) {
        int sr = (start >> 16) & 0xFF;
        int sg = (start >> 8) & 0xFF;
        int sb = start & 0xFF;
        int er = (end >> 16) & 0xFF;
        int eg = (end >> 8) & 0xFF;
        int eb = end & 0xFF;
        int r = (int) Math.round(sr + (er - sr) * progress);
        int g = (int) Math.round(sg + (eg - sg) * progress);
        int b = (int) Math.round(sb + (eb - sb) * progress);
        return (r << 16) | (g << 8) | b;
    }

    private static String toLegacyHex(int rgb) {
        String hex = String.format("%06X", rgb);
        StringBuilder builder = new StringBuilder("&x");
        for (int i = 0; i < hex.length(); i++) builder.append('&').append(hex.charAt(i));
        return builder.toString();
    }

    private static long bounded(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
}
