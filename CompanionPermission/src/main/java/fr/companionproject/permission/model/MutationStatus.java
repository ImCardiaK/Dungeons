package fr.companionproject.permission.model;

/** Outcome of a requested permission change. */
public enum MutationStatus
{
    CHANGED, UNCHANGED, NOT_FOUND, ALREADY_EXISTS, PROTECTED, CYCLE, INVALID
}
