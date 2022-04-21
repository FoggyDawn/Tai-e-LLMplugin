/*
 * Tai-e: A Static Analysis Framework for Java
 *
 * Copyright (C) 2020-- Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2020-- Yue Li <yueli@nju.edu.cn>
 * All rights reserved.
 *
 * Tai-e is only for educational and academic purposes,
 * and any form of commercial use is disallowed.
 * Distribution of Tai-e is disallowed without the approval.
 */

package pascal.taie.util.collection;

import java.util.HashMap;
import java.util.Map;

/**
 * Hybrid map that uses hash map for large map.
 */
public final class HybridHashMap<K, V> extends AbstractHybridMap<K, V> {

    /**
     * Constructs a new empty hybrid map.
     */
    public HybridHashMap() {
    }

    /**
     * Constructs a new hybrid map from the given map.
     */
    public HybridHashMap(Map<K, V> m) {
        super(m);
    }

    @Override
    protected Map<K, V> newLargeMap(int initialCapacity) {
        return new HashMap<>(initialCapacity);
    }
}
