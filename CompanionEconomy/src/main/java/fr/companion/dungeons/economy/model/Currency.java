package fr.companion.dungeons.economy.model;

/** Validated administrator-defined currency. */
public record Currency(String id, String name, String symbol, int scale, boolean paymentsEnabled) {}
