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

package pascal.taie.analysis.pta.core.cs;

import pascal.taie.analysis.graph.callgraph.AbstractCallGraph;
import pascal.taie.analysis.graph.callgraph.Edge;
import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.CSCallSite;
import pascal.taie.analysis.pta.core.cs.element.CSManager;
import pascal.taie.analysis.pta.core.cs.element.CSMethod;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.language.classes.JMethod;
import pascal.taie.util.collection.ArraySet;
import pascal.taie.util.collection.IndexableSet;
import pascal.taie.util.collection.IndexerBitSet;
import pascal.taie.util.collection.Sets;
import pascal.taie.util.collection.Views;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Represents context-sensitive call graph.
 */
public class CSCallGraph extends AbstractCallGraph<CSCallSite, CSMethod> {

    private final CSManager csManager;

    public CSCallGraph(CSManager csManager) {
        this.csManager = csManager;
        this.reachableMethods = new IndexerBitSet<>(csManager.getMethodIndexer(), false);
    }

    /**
     * Adds an entry method to this call graph.
     */
    public void addEntryMethod(CSMethod entryMethod) {
        entryMethods.add(entryMethod);
    }

    /**
     * Adds a reachable method to this call graph.
     *
     * @return true if this call graph changed as a result of the call,
     * otherwise false.
     */
    public boolean addReachableMethod(CSMethod csMethod) {
        if (reachableMethods.add(csMethod)) {
            callSitesIn(csMethod).forEach(csCallSite ->
                    csCallSite.setContainer(csMethod));
            return true;
        } else {
            return false;
        }
    }

    /**
     * Adds a new call graph edge to this call graph.
     *
     * @param edge the call edge to be added
     * @return true if the call graph changed as a result of the call,
     * otherwise false.
     */
    public boolean addEdge(Edge<CSCallSite, CSMethod> edge) {
        if (edge.getCallSite().addEdge(edge)) {
            edge.getCallee().addEdge(edge);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Set<CSCallSite> getCallersOf(CSMethod callee) {
        return Views.toMappedSet(callee.getEdges(), Edge::getCallSite);
    }

    @Override
    public Set<CSMethod> getCalleesOf(CSCallSite csCallSite) {
        return Views.toMappedSet(csCallSite.getEdges(), Edge::getCallee);
    }

    @Override
    public CSMethod getContainerOf(CSCallSite csCallSite) {
        return csCallSite.getContainer();
    }

    @Override
    public Set<CSCallSite> getCallSitesIn(CSMethod csMethod) {
        JMethod method = csMethod.getMethod();
        Context context = csMethod.getContext();
        ArrayList<CSCallSite> callSites = new ArrayList<>();
        for (Stmt s : method.getIR()) {
            if (s instanceof Invoke) {
                CSCallSite csCallSite = csManager.getCSCallSite(context, (Invoke) s);
                // each Invoke is iterated once, that we can ensure that
                // callSites contain no duplicate Invokes
                callSites.add(csCallSite);
            }
        }
        return Collections.unmodifiableSet(new ArraySet<>(callSites, true));
    }

    @Override
    public Stream<Edge<CSCallSite, CSMethod>> edgesOutOf(CSCallSite csCallSite) {
        return csCallSite.getEdges().stream();
    }

    @Override
    public Stream<Edge<CSCallSite, CSMethod>> edgesInTo(CSMethod csMethod) {
        return csMethod.getEdges().stream();
    }

    @Override
    public Stream<Edge<CSCallSite, CSMethod>> edges() {
        return reachableMethods.stream()
                .flatMap(this::callSitesIn)
                .flatMap(this::edgesOutOf);
    }

    @Override
    public boolean isRelevant(Stmt stmt) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<CSMethod> getResult(Stmt stmt) {
        throw new UnsupportedOperationException();
    }
}
