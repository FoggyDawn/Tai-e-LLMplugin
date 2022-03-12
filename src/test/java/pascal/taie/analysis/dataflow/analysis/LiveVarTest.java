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

package pascal.taie.analysis.dataflow.analysis;

import org.junit.Test;
import pascal.taie.analysis.Tests;

public class LiveVarTest {

    void testLV(String inputClass) {
        Tests.test(inputClass, "src/test/resources/dataflow/livevar",
                LiveVariable.ID, "strongly:false");
    }

    @Test
    public void testAssign() {
        testLV("Assign");
    }

    @Test
    public void testBranch() {
        testLV("Branch");
    }

    @Test
    public void testBranchLoop() {
        testLV("BranchLoop");
    }

    @Test
    public void Array() {
        testLV("Array");
    }

    @Test
    public void Fibonacci() {
        testLV("Fibonacci");
    }

    @Test
    public void Reference() {
        testLV("Reference");
    }
}
