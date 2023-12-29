package dev.heliosares.sync;

import dev.heliosares.sync.net.IDProvider;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class TestIDProvider {
    private final Set<Long> usedIDs = new HashSet<>();

    @Test
    public void testIDProvider() {
        System.out.println("min=" + IDProvider.MIN_CONNECTION_ID + ", max=" + IDProvider.MAX_CONNECTION_ID);

        for (short s = IDProvider.MIN_CONNECTION_ID; s <= IDProvider.MAX_CONNECTION_ID; s++) {
            IDProvider provider = new IDProvider(s);
            for (int i = 0; i < 32; i++) {
                IDProvider.ID id = provider.getNextID();
                assertEquals(id, IDProvider.parse(id.combined()));
                newID(id);
            }
        }
        System.out.println("Tested " + usedIDs.size() + " ids.");
    }

    private void newID(IDProvider.ID id) {
        if (usedIDs.add(id.combined())) return;
        throw new AssertionError("Duplicate ID: " + id);
    }
}
