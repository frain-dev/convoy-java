package com.getconvoy.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeliveryAttemptListTest {
    @Test
    void generatedArrayAliasPreservesTheListEqualityAndHashContract() {
        DeliveryAttemptList alias = new DeliveryAttemptList();
        List<DatastoreDeliveryAttempt> ordinary = new ArrayList<>();
        assertEquals(ordinary, alias);
        assertEquals(alias, ordinary);
        assertEquals(ordinary.hashCode(), alias.hashCode());

        DatastoreDeliveryAttempt attempt = new DatastoreDeliveryAttempt().uid("attempt-1");
        alias.add(attempt);
        ordinary.add(attempt);
        assertEquals(ordinary, alias);
        assertEquals(alias, ordinary);
        assertEquals(ordinary.hashCode(), alias.hashCode());
        assertTrue(new HashSet<>(List.of(ordinary)).contains(alias));
        assertTrue(new HashSet<>(List.of(alias)).contains(ordinary));
    }
}
