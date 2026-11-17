package fr.companionproject.core.storage;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Complete UTF-8 document replacement shared by repositories. */
public final class AtomicFiles
{
    private AtomicFiles() {}

    /** Writes a temporary sibling before replacing the destination. */
    public static void replace(final Path destination, final String contents) throws IOException {
        Files.createDirectories(destination.toAbsolutePath().getParent());
        final Path temporary = Files.createTempFile(destination.toAbsolutePath().getParent(), "snapshot-", ".tmp");
        try {
            Files.writeString(temporary, contents);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (final AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
