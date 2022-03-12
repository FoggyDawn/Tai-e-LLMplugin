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

package pascal.taie.analysis.dataflow.analysis.constprop;

import org.junit.Assert;
import org.junit.Test;

public class MeetValueTest {

    private final Value i1 = Value.makeConstant(1);
    private final Value i0 = Value.makeConstant(0);
    private final Value NAC = Value.getNAC();
    private final Value undef = Value.getUndef();
    private final ConstantPropagation.Analysis cp =
            new ConstantPropagation.Analysis(null, true);

    @Test
    public void testMeet() {
        Assert.assertEquals(cp.meetValue(undef, undef), undef);
        Assert.assertEquals(cp.meetValue(undef, i0), i0);
        Assert.assertEquals(cp.meetValue(undef, NAC), NAC);
        Assert.assertEquals(cp.meetValue(NAC, NAC), NAC);
        Assert.assertEquals(cp.meetValue(NAC, i0), NAC);
        Assert.assertEquals(cp.meetValue(NAC, undef), NAC);
        Assert.assertEquals(cp.meetValue(i0, i0), i0);
        Assert.assertEquals(cp.meetValue(i0, i1), NAC);
        Assert.assertEquals(cp.meetValue(i0, undef), i0);
        Assert.assertEquals(cp.meetValue(i0, NAC), NAC);
    }
}
