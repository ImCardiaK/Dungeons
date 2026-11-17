package fr.companion.dungeons.permission.model;

import java.util.concurrent.CompletionStage;

/** Immediate domain outcome and completion of its queued persistence. */
public record MutationResult(MutationStatus status, CompletionStage<Void> persistence) {}
