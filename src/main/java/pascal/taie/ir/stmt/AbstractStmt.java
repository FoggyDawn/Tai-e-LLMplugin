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

package pascal.taie.ir.stmt;

import pascal.taie.ir.exp.LValue;
import pascal.taie.ir.exp.RValue;

import java.util.List;
import java.util.Optional;

abstract class AbstractStmt implements Stmt {

    protected int index = -1;

    protected int lineNumber = -1;

    @Override
    public int getIndex() {
        return index;
    }

    @Override
    public void setIndex(int index) {
        if (this.index != -1) {
            throw new IllegalStateException("index already set");
        }
        this.index = index;
    }

    @Override
    public int getLineNumber() {
        return lineNumber;
    }

    @Override
    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    // Following three methods provide default behaviors for the three
    // implemented APIs (declared in Stmt). The subclasses of this class
    // should override these APIs iff their behaviors are different from
    // the default ones.

    @Override
    public Optional<LValue> getDef() {
        return Optional.empty();
    }

    @Override
    public List<RValue> getUses() {
        return List.of();
    }

    @Override
    public boolean canFallThrough() {
        return true;
    }
}
