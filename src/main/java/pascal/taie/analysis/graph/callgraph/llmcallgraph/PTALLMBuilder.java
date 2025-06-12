/*
 * Tai-e: A Static Analysis Framework for Java
 *
 * Copyright (C) 2022 Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2022 Yue Li <yueli@nju.edu.cn>
 *
 * This file is part of Tai-e.
 *
 * Tai-e is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * Tai-e is distributed in the hope that it will be useful,but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Tai-e. If not, see <https://www.gnu.org/licenses/>.
 */

package pascal.taie.analysis.graph.callgraph.llmcallgraph;

import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.Logger;
import com.google.gson.Gson;
import pascal.taie.World;
import pascal.taie.analysis.graph.callgraph.CGBuilder;
import pascal.taie.analysis.graph.callgraph.CallGraph;
import pascal.taie.analysis.graph.callgraph.CallGraphs;
import pascal.taie.analysis.graph.callgraph.CallKind;
import pascal.taie.analysis.graph.callgraph.DefaultCallGraph;
import pascal.taie.analysis.graph.callgraph.Edge;
import pascal.taie.analysis.graph.cfg.CFG;
import pascal.taie.analysis.graph.cfg.CFGBuilder;
import pascal.taie.analysis.pta.PointerAnalysis;
import pascal.taie.analysis.pta.PointerAnalysisResultImpl;
import pascal.taie.analysis.pta.core.heap.Descriptor;
import pascal.taie.analysis.pta.core.heap.MockObj;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.analysis.pta.toolkit.PointerAnalysisResultExImpl;
import pascal.taie.config.ConfigException;
import pascal.taie.ir.IRPrinter;
import pascal.taie.ir.exp.InvokeDynamic;
import pascal.taie.ir.exp.InvokeInstanceExp;
import pascal.taie.ir.exp.MethodHandle;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.exp.InvokeExp;
import pascal.taie.ir.proginfo.MemberRef;
import pascal.taie.ir.proginfo.MethodRef;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.ir.stmt.Throw;
import pascal.taie.language.classes.ClassHierarchy;
import pascal.taie.language.classes.ClassNames;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.classes.MethodNames;
import pascal.taie.language.classes.Signatures;
import pascal.taie.language.classes.Subsignature;
import pascal.taie.language.type.ClassType;
import pascal.taie.language.type.PrimitiveType;
import pascal.taie.language.type.Type;
import pascal.taie.util.AnalysisException;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.TwoKeyMap;

import javax.annotation.Nullable;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static org.apache.logging.log4j.LogManager.getLogger;
import static pascal.taie.language.classes.ClassNames.CALL_SITE;
import static pascal.taie.language.classes.ClassNames.METHOD_HANDLE;

/**
 * Builds call graph based on pointer analysis results.
 * This builder assumes that pointer analysis has finished,
 * but it does not merely return the (context-insensitive) call graph.
 * Instead, it conducts LLM base CFA and obtain a new call graph
 * which excludes calls on unreachable branches
 */
public class PTALLMBuilder implements CGBuilder<Invoke, JMethod> {

    private static final Logger logger = getLogger(PTALLMBuilder.class);
    private PrintStream out;

    private ClassHierarchy hierarchy;

    /**
     * SubSignatures of methods in java.lang.Object.
     */
    private Set<Subsignature> objectMethods;

    /**
     * Cache resolve results for interface/virtual invocations.
     */
    private TwoKeyMap<JClass, MemberRef, Set<JMethod>> resolveTable;

    /**
     * Store full pta result for CSCallGraph Construction
     * result.getBase() returns a PointerAnalysisResult instance
     */
    private PointerAnalysisResultExImpl exPtaResult;

    /**
     * ptaResult.getPointsToSet(v) check object of a variable
     */
    private PointerAnalysisResultImpl ptaResult;

    private final Integer LLMQueryLimit = 500;

    private List<JMethod> LLMQueryMethods = new ArrayList<>();

    private final boolean ignoreObjectMethods = false;

    private final boolean useGMM;

    /**
     * record for the second period, each contains method and its pre-conditions
     * @param constraints represent pre-conditions when entering method
     * @param method JMethod
     */
    private record Entry(List<List<String>> constraints, JMethod method) {}

    /**
     * record for argument range LLM queries
     * @param arg args of invoke
     * @param range constraints of an arg
     */
    private record ArgRange(Var arg, List<String> range){}

    /**
     * record for param range recording. A method maps to a list of ParamRanges
     * @param paramName param name
     * @param paramType param type
     * @param range constraints of a param
     */
    private record ParamRange(String paramName, Type paramType, List<String> range) {
        public ParamRange(ParamRange a, ParamRange b) {
            // merge two ParamRanges
            this(a.paramName, a.paramType, Objects.equals(a.paramName, b.paramName)
                    && Objects.equals(a.paramType, b.paramType) ? Stream
                    .concat(a.range.stream(), b.range.stream())
                    .distinct()
                    .toList() : List.of("error"));
            if (Objects.equals(this.range, List.of("error"))) {
                logger.error("unmatched param type and name");
                throw new AnalysisException("params unmatched: " +
                        a.paramType.getName() + " " + a.paramName + " and "
                        + b.paramType.getName() + " " + b.paramName);
            }
        }
    }

    private Map<JMethod, List<ParamRange>> methodParamRange;

    private static long branchNum = 0;

    public PTALLMBuilder(String chooseMethod) {
        switch (chooseMethod) {
            case "gmm" -> useGMM = true;
            case "sort" -> useGMM = false;
            default -> {
                useGMM = false;
            }
        }
    }

    @Override
    public CallGraph<Invoke, JMethod> build() {
        hierarchy = World.get().getClassHierarchy();
        JClass object = hierarchy.getJREClass(ClassNames.OBJECT);
        objectMethods = Objects.requireNonNull(object)
                .getDeclaredMethods()
                .stream()
                .map(JMethod::getSubsignature)
                .collect(Collectors.toUnmodifiableSet());
        resolveTable = Maps.newTwoKeyMap();
        /*
          following getResult returns a PointerAnalysisResultImpl instance
          prove it in this path:
          DefaultSolver.getResult() ->
          PointerAnalysis.runAnalysis() -> analyze() ->
          AnalysisManager.runProgramAnalysis() ->
          AbstractResultHolder.storeResult() -> getResult()
         */
        ptaResult = World.get().getResult(PointerAnalysis.ID);
        exPtaResult = new PointerAnalysisResultExImpl(ptaResult, true);
        // algorithm to identify high value JMethods
        LLMQueryMethods = chooseMethods();
        // first round of llm query
        methodParamRange = buildRange();
        // for debug
        return new DefaultCallGraph();
        // second round
//        return buildCallGraph(World.get().getMainMethod());
    }

    /**
     * provide several methods to find out valuable methods to query with llm
     * it will get all methods from World
     * @return List<JMethod> list of valuable methods
     */
    private List<JMethod> chooseMethods() {

        // struct for assessing method value
        class MethodValue {
            private final JMethod method;

            // Metrics
            private final long callSiteNumber;
            private long primitiveArgNumber = 0;
            private long otherArgNumber = 0;
            private double subMethodIsAppNumber = 0;
            private double subMethodCallSiteNumber = 0;
            private double subMethodBranchNumber = 0;
            private double subMethodLineNumber = 0;

            /**
             * calculate all metrics about value of a method
             * @param method method to be assessed
             */
            public MethodValue(JMethod method) {
                this.method = method;
                CFG<Stmt> irs = method.getIR().getResult(CFGBuilder.ID);
                // statistic of branchNum
                if (method.isApplication()) {
                    branchNum += irs.getNodes().stream().filter(stmt -> irs.getSuccsOf(stmt).stream().filter(stmt1 ->
                            !(stmt instanceof Invoke) && !(stmt instanceof Throw) || !irs.isExit(stmt1)).count() > 1)
                            .count();
                }

                // compute call site number in methods called by this method
                this.callSiteNumber = method.getIR().invokes(true).count();
                // the method never call, return
                if (callSiteNumber == 0) {
                    return;
                }
                for (Invoke callee : method.getIR().invokes(true).toList()) {
                    this.primitiveArgNumber += callee.getInvokeExp().getArgs().stream()
                            .filter(arg -> arg.getType() instanceof PrimitiveType).count();
                    this.otherArgNumber += callee.getInvokeExp().getArgs().stream()
                            .filter(arg -> !(arg.getType() instanceof PrimitiveType)).count();

                    Set<JMethod> targetMethods = resolveCalleesOf(callee);
                    // invoke cannot be resolved, cannot statistic method content related metrics
                    if (targetMethods.isEmpty()) {
                        continue;
                    }
                    long sumInvoke = 0;
                    long sumBranch = 0;
                    long sumApp = 0;
                    long sumLine = 0;
                    long count = targetMethods.size();
                    for (JMethod targetMethod : targetMethods) {
                        if (targetMethod.isApplication()) {
                            sumApp++;
                        }

                        sumLine += targetMethod.getIR().stmts().count();

                        long countInvoke = targetMethod.getIR().invokes(true).count();
                        sumInvoke += countInvoke;

                        CFG<Stmt> cfg = targetMethod.getIR().getResult(CFGBuilder.ID);
                        long cnt = 0;
                        for (Stmt stmt : cfg) {
                            // consider stmt throw exceptions and not caught
                            // the cfg do not contain implicit exceptions
                            // if effective branch less than 1, do not add cnt
                            long nbr = cfg.getSuccsOf(stmt)
                                    .stream()
                                    .filter(stmt1 ->
                                            !(stmt instanceof Invoke)
                                                    && !(stmt instanceof Throw)
                                                    || !cfg.isExit(stmt1))
                                    .count();
                            if (nbr <= 1) {
                                continue;
                            }
                            cnt += nbr - 1;
                        }
                        sumBranch += cnt;
                    }
                    this.subMethodCallSiteNumber += (double) sumInvoke / count;
                    this.subMethodBranchNumber += (double) sumBranch / count;
                    this.subMethodIsAppNumber += (double) sumApp / count;
                    this.subMethodLineNumber += (double) sumLine / count;
                }
            }

            public List<Number> getMetrics() {
                return List.of(callSiteNumber, primitiveArgNumber, otherArgNumber,
                        subMethodIsAppNumber, subMethodLineNumber, subMethodCallSiteNumber,
                        subMethodBranchNumber);
            }

            public JMethod getMethod() {
                return method;
            }

            public double getScore(){
                if (primitiveArgNumber + otherArgNumber == 0)  {
                    return 0;
                } else {
                    return subMethodIsAppNumber
                            * primitiveArgNumber/(primitiveArgNumber + otherArgNumber)
                            * subMethodCallSiteNumber * subMethodBranchNumber;
                }
            }
        }

        List<MethodValue> valueList = World.get()
                .getClassHierarchy()
                .allClasses()
                .map(JClass::getDeclaredMethods)
                .flatMap(Collection::stream)
                .filter(m -> !m.isAbstract())
                .map(MethodValue::new).toList();

        logger.info("there are {} branches in app", branchNum);

        logger.info("package method invoke app method: {} times", valueList.stream()
                .filter(mv -> !mv.getMethod().isApplication()
                && mv.subMethodIsAppNumber > 0).count());

        List<JMethod> result = new ArrayList<>();

        if (!useGMM) {
            // method 1: sorting
            // score: subMethodIsAppNumber
            // * primitiveArgNumber/(primitiveArgNumber + otherArgNumber)
            // * subMethodCallSiteNumber * subMethodBranchNumber
            List<MethodValue> toSort = new ArrayList<>(valueList);
            toSort.sort((mv1,mv2) -> Double.compare(mv2.getScore(), mv1.getScore()));
            result = toSort.stream().limit(LLMQueryLimit)
                    .filter(mv -> mv.getScore() > 0).map(MethodValue::getMethod).toList();

            try {
                out = new PrintStream("llmagent/io/selectedmethods.txt");
            } catch (FileNotFoundException e) {
                throw new RuntimeException("fail to open result file ", e);
            }
            for (MethodValue mv : toSort.subList(0, Math.min(LLMQueryLimit, result.size()))) {
                out.printf(" %s\n   ", mv.getMethod().getRef().toString());
                out.print("Center: ");
                mv.getMetrics().forEach(number -> out.print(number + " "));
                out.println();
            }
            out.close();

        } else {
            //method 2: k-means or gmm (important)
            PythonBridge bridge = new PythonBridge();
            // filtering meaningless function calls, generate data
            Map<String, List<Number>> data = valueList.stream()
                    .filter(mv -> mv.callSiteNumber > 0 && mv.subMethodCallSiteNumber > 0
                            && mv.primitiveArgNumber + mv.otherArgNumber > 0
                            && mv.subMethodIsAppNumber > 1 && mv.subMethodBranchNumber > 0)
                    .collect(Collectors.toMap(mv ->
                            mv.getMethod().getRef().toString(), MethodValue::getMetrics));

            logger.info("there are {} methods", data.size());

            // Note: setPrettyPrinting() can be removed
            Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
            String json = gson.toJson(data);

            try (FileWriter writer = new FileWriter("llmagent/io/methodvalue.json")) {
                writer.write(json);
            } catch (FileNotFoundException e) {      // 文件路径无效
                logger.error("文件未找到：" + "llmagent/io/methodvalue.json");
            } catch (SecurityException e) {           // 权限不足
                logger.error("安全限制：{}", e.getMessage());
            } catch (IOException e) {                 // 通用IO异常
                logger.error("写入失败：{}", String.valueOf(e.getCause()));
            }

            Map<String, JMethod> refmap = valueList.stream()
                    .filter(mv -> mv.callSiteNumber > 0 && mv.subMethodCallSiteNumber > 0
                            && mv.primitiveArgNumber + mv.otherArgNumber > 0
                            && mv.subMethodIsAppNumber > 1 && mv.subMethodBranchNumber > 0)
                    .collect(Collectors.toMap(mv ->
                            mv.getMethod().getRef().toString(), MethodValue::getMethod));

            try {
                result = bridge.runScript("llmagent/io/methodvalue.json")
                        .stream().map(refmap::get).toList();
            } catch (IOException | InterruptedException e) {
                logger.error("python script error", e);
            }
        }

        // token testing, also tips on out stream to file and ir printer
        try {
            out = new PrintStream("llmagent/io/ir.txt");
        } catch (FileNotFoundException e) {
            throw new RuntimeException("fail to open ir file ", e);
        }
        for (JMethod method : result) {
            out.printf("------------ %s --------------%n", method.getRef().toString());
            method.getIR().stmts().forEach(stmt -> out.println(IRPrinter.toString(stmt)));
        }
        out.close();

        return result;
    }

    private Map<JMethod, List<ParamRange>> buildRange() {
        logger.info("resolving param range method by method...");

        Map<JMethod, List<ParamRange>> paramRanges = new HashMap<>();

        for (JMethod method : LLMQueryMethods) {

            // now choose ask answers in one go for one method
            final Map<Invoke, List<ArgRange>> answers = llmQuery(method);

            // update answers
            answers.forEach((invoke, argRanges) -> {
                // !!!!IMPORTANT!!!! this function contains analysis in pta
                // and it might be wrong
                Set<JMethod> callees = resolveCalleesOf(invoke);
                if (!callees.isEmpty()) {
                    // only analyse application methods
                    // seems only-app way may got wrong. canceled filter
                    callees.forEach(callee -> {
                        if (callee.getParamCount() == 0) {
                            return;
                        }
                        // make sure invoke params matches with method params.
                        if (!fieldsEqual(callee.getParamTypes(),
                                invoke.getInvokeExp().getArgs())) {
                            // logMismatch(invoke, callee);
                            return;
                        }
                        // now: invoke args -> callee params
                        // ranges: the param ranges of this callee
                        // argRange -> paramRange. Done
                        List<Type> types = callee.getParamTypes();
                        List<ParamRange> ranges = IntStream.range(0, callee.getParamCount())
                                .mapToObj(i -> new ParamRange(callee.getParamName(i),
                                        types.get(i), argRanges.get(i).range))
                                .collect(Collectors.toList());
                        // add ranges to the final result paramRanges
                        paramRanges.compute(callee, (k, currentRanges) -> {
                            if (currentRanges == null) {
                                // return new param ranges
                                return ranges;
                            } else {
                                return appendRange(callee, currentRanges, ranges);
                            }
                        });
                    });
                }
//                } else {
//                    logger.error("Failed to resolve {}, Invoke {} cannot find callee.",
//                            invoke.getInvokeExp().getMethodRef(), invoke.toString());
//                }
            });
        }
        return paramRanges;
    }


    private void logAndThrow(JMethod callee) throws AnalysisException {
        logger.debug("unmatched param number");
        throw new AnalysisException("paramRanges unmatched." +
                callee.getDeclaringClass().getName() + "."
                + callee.getName());
    }

    private void logMismatch(Invoke invoke, JMethod callee) throws AnalysisException {
        logger.debug("unmatched method, {} mismatch to {}.{}, params unmatched.",
                invoke, callee.getDeclaringClass().getName(), callee.getName());

    }

    private List<ParamRange> appendRange(
            JMethod callee, List<ParamRange> currentRanges, List<ParamRange> ranges) {
        if (currentRanges.size() != ranges.size()) {
            logAndThrow(callee);
        }
        // return merged param ranges
        return IntStream
                .range(0, ranges.size())
                .mapToObj(i -> new ParamRange(ranges.get(i),
                        currentRanges.get(i)))
                .toList();
    }

    private static boolean fieldsEqual(List<Type> listA, List<Var> listB) {
        List<Type> listAFromB = listB.stream()
                .map(Var::getType)
                .toList();
        return listA.equals(listAFromB);
    }

    private boolean isIgnored(JMethod method) {
        return !method.isApplication();
    }

    private Map<Invoke, List<ArgRange>> llmQuery(JMethod method) {
        // TODO: implement LLMQuery use autogen

        record IndexRange(String argName, List<String> ranges){}

        Map<Integer, List<IndexRange>> queries = new HashMap<>();
        Map<Invoke, List<ArgRange>> answers = new HashMap<>();

        //construct llm queries for valuable to-query method in LLMQueryMethods
        method.getIR().invokes(true).forEach(invoke -> {
            // params of invoke
            if (invoke.getInvokeExp().getArgCount() == 0) {
                return;
            }
            List<IndexRange> params = new ArrayList<>();
            invoke.getInvokeExp().getArgs().forEach(arg -> {
                IndexRange indRange = new IndexRange(arg.getName(), new ArrayList<>());
                params.add(indRange);
            });
            queries.put(invoke.getIndex(), params);
        });

        PythonBridge bridge = new PythonBridge();

        // dump queries to json and pass to python
        Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
        String json = gson.toJson(queries);
//        logger.info(json);

        try (FileWriter writer = new FileWriter("llmagent/io/invoke-range.json")) {
            writer.write(json);
        } catch (FileNotFoundException e) {      // 文件路径无效
            logger.error("文件未找到：" + "llmagent/io/invoke-range.json");
        } catch (SecurityException e) {           // 权限不足
            logger.error("安全限制：{}", e.getMessage());
        } catch (IOException e) {                 // 通用IO异常
            logger.error("写入失败：{}", String.valueOf(e.getCause()));
        }

        StringBuilder ir = new StringBuilder();
        ir.append(method.getRef().toString()).append("\n");
        method.getIR().stmts().forEach(stmt -> ir.append(IRPrinter.toString(stmt)).append('\n'));

        String irString = ir.toString();



        return answers;
    }

    private CallGraph<Invoke, JMethod> buildCallGraph(JMethod entry) {
        logger.info("Building Call Graph with LLM agent...");

//        Map<JMethod, List<ParamRange>> paramRanges = new HashMap<>();
//        // call graph only for recording and util functions
//        DefaultCallGraph callGraph = new DefaultCallGraph();
//        callGraph.addEntryMethod(entry);
//        Queue<JMethod> workList = new ArrayDeque<>();
//        workList.add(entry);
//        while (!workList.isEmpty()) {
//            JMethod method = workList.poll();
//            if (callGraph.addReachableMethod(method)) {
//                //construct llm queries for valuable to-query method in LLMQueryMethods
//                final Map<Invoke, List<ArgRange>> queries = new HashMap<>();
//                callGraph.callSitesIn(method).forEach(invoke -> {
//                    // params of invoke
//                    List<ArgRange> params = new ArrayList<>();
//                    invoke.getInvokeExp().getArgs().forEach(arg -> {
//                        ArgRange argRange = new ArgRange(arg, new ArrayList<>());
//                        params.add(argRange);
//                    });
//                    queries.put(invoke, params);
//                });
//                // now choose ask answers in one go
//
//                // change to get and deal with result in inter const prop?
//                final Map<Invoke, List<ArgRange>> answers = llmQuery(queries);
//
//                // update answers
//                answers.forEach((invoke, argRanges) -> {
//                    // !!!!IMPORTANT!!!! this function contains analysis in pta
//                    // and it might be wrong
//                    Set<JMethod> callees = resolveCalleesOf(invoke);
//                    if (!callees.isEmpty()) {
//                        // only analyse application methods
//                        // seems only-app way may got wrong. commented filter
//                        callees // .stream()
//                                // .filter(callee -> !isIgnored(callee))
//                                .forEach(callee -> {
//                                    if (!callGraph.contains(callee)) {
//                                        workList.add(callee);
//                                    }
//                                    // make sure invoke params matches with method params.
//                                    if (!fieldsEqual(callee.getParamTypes(),
//                                            invoke.getInvokeExp().getArgs())) {
//                                        logAndThrow(invoke, callee);
//                                    }
//                                    // now: invoke args -> callee params
//                                    // ranges: the param ranges of this callee
//                                    // argRange -> paramRange. Done
//                                    List<Type> types = callee.getParamTypes();
//                                    List<ParamRange> ranges = IntStream.range(0, callee.getParamCount())
//                                            .mapToObj(i -> new ParamRange(callee.getParamName(i),
//                                                    types.get(i), argRanges.get(i).range))
//                                            .collect(Collectors.toList());
//                                    // add ranges to the final result paramRanges
//                                    paramRanges.compute(callee, (k, currentRanges) -> {
//                                        if (currentRanges == null) {
//                                            // return new param ranges
//                                            return ranges;
//                                        } else {
//                                            return appendRange(callee, currentRanges, ranges);
//                                        }
//                                    });
//                                    callGraph.addEdge(new Edge<>(
//                                            CallGraphs.getCallKind(invoke), invoke, callee));
//                                });
//                    } else {
//                        logger.error("Failed to resolve {}, Invoke {} cannot find callee.",
//                                invoke.getInvokeExp().getMethodRef(), invoke.toString());
//                    }
//                });
//            }
//        }
//        return paramRanges;
        return new DefaultCallGraph();
    }

    /**
     * Resolves callees of a call site via pta results.
     */
    private Set<JMethod> resolveCalleesOf(Invoke callSite) {
        CallKind kind = CallGraphs.getCallKind(callSite);
        return switch (kind) {
            case INTERFACE, VIRTUAL -> {  // invoke interface may involve calling lambda function
                MethodRef methodRef = callSite.getMethodRef();
                if (ignoreObjectMethods && isObjectMethod(methodRef)) {
                    yield Set.of();
                }
                JClass cls = methodRef.getDeclaringClass();
                Set<JMethod> callees = resolveTable.get(cls, methodRef);
                if (callees == null) {
                    Var base = ((InvokeInstanceExp) callSite.getInvokeExp()).getBase();
                    //resolve callee via pta result
                    callees = ptaResult.getPointsToSet(base).stream()
                            .map(recvObj -> {
                                JMethod callee = CallGraphs.resolveCallee(
                                        recvObj.getType(), callSite);
                                return callee == null ? handleLambda(callSite) : callee;
                            })
                            .filter(Objects::nonNull)
                            .collect(Collectors.toUnmodifiableSet());
                    resolveTable.put(cls, methodRef, callees);
                }
                yield callees;
            }
            case STATIC, SPECIAL -> {
                JMethod callee = CallGraphs.resolveCallee(null, callSite);
                if (callee != null) {
                    yield Set.of(callee);
                } else {
                    yield Set.of();
                }
            }
            case DYNAMIC -> {
                Set<JMethod> callees = new HashSet<>();
                if (isBSMInvoke(callSite)) {
                    // lambda functions will be handled by further code
                    callees.addAll(handleBSM(callSite));
//                    logger.debug("find BSM call");
                } else { // deal with lambda
                    if (handleLambda(callSite) != null) {
                        callees.add(handleLambda(callSite));
                    }
                }
                yield callees.stream().filter(Objects::nonNull)
                        .collect(Collectors.toUnmodifiableSet());
            }
            default -> throw new AnalysisException(
                    "Failed to resolve call site: " + callSite);
        };
    }

    private Set<JMethod> handleBSM(Invoke callSite) {
        Set<JMethod> callees = new HashSet<>();
        InvokeDynamic indyBSM = (InvokeDynamic) callSite.getInvokeExp();
        JMethod bsm = indyBSM.getBootstrapMethodRef().resolve();
        callees.add(bsm);
        bsm.getIR()
                .invokes(true)
                .map(Invoke::getInvokeExp)
                .map(this::getMhVar)
                .filter(Objects::nonNull)
                .forEach(mhVar -> ptaResult.getPointsToSet(mhVar).forEach(recvObj -> {
                    MethodHandle mh = recvObj.getAllocation()
                            instanceof MethodHandle methodHandle ?
                            methodHandle : null;
                    if (mh != null) {
                        MethodRef ref = mh.getMethodRef();
                        switch (mh.getKind()) {
                            case REF_invokeVirtual -> {
                                // for virtual invocation, record base variable and
                                // add invokedynamic call edge
                                Var base = callSite.getInvokeExp().getArg(0);
                                Set<Obj> recvObjs = ptaResult.getPointsToSet(
                                        base);
                                recvObjs.forEach(recv -> {
                                    JMethod callee =
                                            hierarchy.dispatch(recv.getType(), ref);
                                    if (callee != null) {
                                        callees.add(callee);
                                    }
                                });
                            }
                            case REF_invokeStatic ->
                                // for static invocation, just add invokedynamic call edge
                                    callees.add(ref.resolve());
                        }
                    }
                }));
        return callees;
    }

    Descriptor LAMBDA_DESC = () -> "LambdaObj";
    Descriptor LAMBDA_NEW_DESC = () -> "LambdaConstructedObj";

    @Nullable
    private JMethod handleLambda(Invoke callSite) {
        AtomicReference<JMethod> callee = new AtomicReference<>();
        Var base = ((InvokeInstanceExp) callSite.getInvokeExp()).getBase();
        Set<Obj> objsOfLambda = ptaResult.getPointsToSet(base);
        for (Obj obj : objsOfLambda) {
            // for each obj, try to find a callee and add into callees
            if (obj instanceof MockObj mockObj &&
                    mockObj.getDescriptor().equals(LAMBDA_DESC)) {
                Invoke indyInvoke = (Invoke) mockObj.getAllocation();
                InvokeDynamic indyLambda = (InvokeDynamic) indyInvoke.getInvokeExp();
                if (indyLambda.getMethodName()
                        .equals(callSite.getMethodRef().getName())) {
                    // lines of original LambdaAnalysis
                    MethodHandle mh = (MethodHandle) indyLambda.getBootstrapArgs().get(1);
                    final MethodRef targetRef = mh.getMethodRef();
                    switch (mh.getKind()) {
                        case REF_newInvokeSpecial -> { // targetRef is constructor
                                        /*
                                         Create mock object (if absent) which represents
                                         the newly-allocated object. Note that here we use the
                                         *invokedynamic* to represent the *allocation site*,
                                         instead of the actual invocation site of the constructor.
                                        */
                            ClassType type = targetRef.getDeclaringClass().getType();
                            Obj newObj = new MockObj(LAMBDA_NEW_DESC,
                                    indyInvoke, type, indyInvoke.getContainer(),
                                    true);
                            // add call edge to constructor
                            // recvObj is not null, meaning that callee is instance method
                            callee.set(hierarchy.dispatch(newObj.getType(),
                                    targetRef));
                        }
                        case REF_invokeInterface, REF_invokeVirtual,
                             REF_invokeSpecial -> { // targetRef is instance method
                            Var recvVar = getLambdaRecvVar(callSite, indyLambda);
                            ptaResult.getPointsToSet(recvVar).forEach(recvObj -> {
                                if (recvObj != null) {
                                    // recvObj is not null, meaning that
                                    // callee is instance method
                                    callee.set(hierarchy.dispatch(recvObj.getType(),
                                            targetRef));
                                } else { // otherwise, callee is static method
                                    callee.set(targetRef.resolveNullable());
                                }
                            });
                        }
                        case REF_invokeStatic -> // targetRef is static method
                                callee.set(targetRef.resolveNullable());
                        default -> {
                            logger.error("{} is not supported by Lambda handling",
                                    mh.getKind());
                            throw new AnalysisException(mh.getKind() + " is not supported");
                        }
                    }
                }
            }
        }
        return callee.get();
    }

    private Var getMhVar(InvokeExp ie) {
        MethodRef ref = ie.getMethodRef();
        ClassType declType = ref.getDeclaringClass().getType();
        if (World.get().getTypeSystem()
                .isSubtype(requireNonNull(hierarchy.getJREClass(CALL_SITE))
                        .getType(), declType)) {
            // new [Constant|Mutable|Volatile]CallSite(target);
            if (ref.getName().equals(MethodNames.INIT) ||
                    // callSite.setTarget(target);
                    ref.getName().equals("setTarget")) {
                Var tgt = ie.getArg(0);
                if (tgt.getType().equals(requireNonNull(
                        hierarchy.getJREClass(METHOD_HANDLE)).getType())) {
                    return tgt;
                }
            }
        }
        return null;
    }

    private static Var getLambdaRecvVar(Invoke callSite, InvokeDynamic indyLambda) {
        List<Var> capturedArgs = indyLambda.getArgs();
        List<Var> actualArgs = callSite.getInvokeExp().getArgs();
        // if captured arguments are not empty, then the first one
        // must be the receiver object for targetRef
        // otherwise, the first actual argument is the receiver
        return !capturedArgs.isEmpty() ? capturedArgs.get(0)
                : actualArgs.get(0);
    }

    private boolean isBSMInvoke(Invoke invoke) {
        boolean isLambda;
        boolean isJava9;
        // originally from Lambda and Java9StringConcat class
        JMethod bsm = ((InvokeDynamic) invoke.getInvokeExp())
                .getBootstrapMethodRef()
                .resolveNullable();
        if (bsm != null) {
            String bsmSig = bsm.getSignature();
            isLambda = bsmSig.equals(Signatures.LAMBDA_METAFACTORY) ||
                    bsmSig.equals(Signatures.LAMBDA_ALTMETAFACTORY);
            isJava9 = bsmSig.equals(Signatures.STRING_CONCAT_FACTORY_MAKE);
        } else {
            isLambda = false;
            isJava9 = false;
        }
        return !isLambda && !isJava9;
    }

    private boolean isObjectMethod(MethodRef methodRef) {
        return objectMethods.contains(methodRef.getSubsignature());
    }

}
