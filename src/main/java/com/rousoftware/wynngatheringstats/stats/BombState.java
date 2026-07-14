package com.rousoftware.wynngatheringstats.stats;

public record BombState(boolean professionXpActive, boolean professionSpeedActive) {
    public static final BombState NONE = new BombState(false, false);
}
