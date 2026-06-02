package pascal.taie.analysis.pta.plugin.cryptomisuse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pascal.taie.World;
import pascal.taie.analysis.graph.callgraph.Edge;
import pascal.taie.analysis.pta.PointerAnalysisResult;
import pascal.taie.analysis.pta.core.cs.CSCallGraph;
import pascal.taie.analysis.pta.core.cs.context.Context;
import pascal.taie.analysis.pta.core.cs.element.*;
import pascal.taie.analysis.pta.core.cs.selector.ContextSelector;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.plugin.Plugin;
import pascal.taie.analysis.pta.plugin.cryptomisuse.compositerule.CompositeRule;
import pascal.taie.analysis.pta.plugin.cryptomisuse.compositerule.FromSource;
import pascal.taie.analysis.pta.plugin.cryptomisuse.compositerule.ToSource;
import pascal.taie.analysis.pta.plugin.cryptomisuse.issue.*;
import pascal.taie.analysis.pta.plugin.cryptomisuse.resource.ResourceRetrieverModel;
import pascal.taie.analysis.pta.plugin.cryptomisuse.rule.*;
import pascal.taie.analysis.pta.plugin.cryptomisuse.rulejudge.*;
import pascal.taie.analysis.pta.pts.PointsToSet;
import pascal.taie.ir.exp.*;
import pascal.taie.ir.stmt.*;
import pascal.taie.language.classes.ClassHierarchy;
import pascal.taie.language.classes.ClassMember;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.Type;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.MultiMap;
import pascal.taie.util.collection.Pair;
import pascal.taie.util.collection.Sets;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CryptoAPIMisuseAnalysis implements Plugin {

    private static final Logger logger = LogManager.getLogger(CryptoAPIMisuseAnalysis.class);

    private static Set<String> appClassesInString = Sets.newSet();

    public static File outputFile() {
        return outputFile;
    }

    private static File outputFile = new File("CryptoAnalysis.json");


    private static Set<JClass> appClasses = Sets.newSet();

    /**
     * Rule Property
     */
    private final MultiMap<JMethod, CryptoObjPropagate> propagates = Maps.newMultiMap();

    private final MultiMap<JMethod, CryptoSource> sources = Maps.newMultiMap();

    private final MultiMap<Var, Pair<Var, Type>> cryptoVarPropagates = Maps.newMultiMap();

    private final Map<Rule, RuleJudge> ruleToJudge = Maps.newMap();

    private int focusMinMin = Integer.MAX_VALUE;

    private int focusMinMax = Integer.MIN_VALUE;

    /**
     * Composite Rule Property
     */
    private final MultiMap<JMethod, FromSource> compositeFromSources = Maps.newMultiMap();

    private final MultiMap<JMethod, CryptoObjPropagate> compositePropagates = Maps.newMultiMap();

    private final MultiMap<Var, Pair<Var, Type>> compositeVarPropagates = Maps.newMultiMap();

    private final MultiMap<FromSource, CompositeRule> fromSourceToRule = Maps.newMultiMap();

    private final MultiMap<Var, CompositeRule> fromVarToRule = Maps.newMultiMap();

    private final Map<ToSource, CompositeRule> toSourceToRule = Maps.newMap();

    private final MultiMap<JMethod, ToSource> compositeToSources = Maps.newMultiMap();

    private final Set<CompositeRule> compositeRules = Sets.newSet();

    private final MultiMap<Var, Pair<Stmt, ToSource>> compositeVarJudge = Maps.newMultiMap();

    /**
     * Array Involved Property
     */
    private final MultiMap<Var, Var> elementToBase = Maps.newMultiMap();

    private final String PREDICTABLE_DESC = "PredictableSourceObj";

    private ResourceRetrieverModel resourceRetrieverModel;

    /**
     * Environment Property
     */
    private CryptoObjManager manager;

    private Solver solver;

    private CSManager csManager;

    private ContextSelector contextSelector;

    private Context emptyContext;

    private CryptoAPIMisuseConfig config;

    private int stringCount = 0;

    private List<String> constTypes = Arrays.asList("char[]", "java.lang.String", "java.lang.String[]",
            "char", "byte", "byte[]", "int", "long", "short", "int[]", "long[]",
            "short[]", "java.lang.StringBuilder", "java.lang.StringBuffer");

    public static void addAppClass(Set<String> appClass) {
        appClassesInString.addAll(appClass);
    }

    public static Set<String> getAppClassString() {
        return appClassesInString;
    }

    public static Set<JClass> getAppClasses() {
        return appClasses;
    }

    @Override
    public void setSolver(Solver solver) {
        manager = new CryptoObjManager(solver.getHeapModel());
        config = CryptoAPIMisuseConfig.readConfig(
                solver.getOptions().getString("crypto-config"),
                solver.getHierarchy(),
                solver.getTypeSystem());
        if (solver.getOptions().getString("crypto-output") != null) {
            outputFile = new File(solver.getOptions().getString("crypto-output"));
        }
        csManager = solver.getCSManager();
        contextSelector = solver.getContextSelector();
        emptyContext = contextSelector.getEmptyContext();
        this.solver = solver;
        config.sources().forEach(s -> sources.put(s.method(), s));
        config.propagates().forEach(p -> propagates.put(p.method(), p));
        config.patternMatchRules().forEach(pa -> {
            ruleToJudge.put(pa, new PatternMatchRuleJudge(pa, manager));
        });
        config.predictableSourceRules().forEach(pr ->
                ruleToJudge.put(pr, new PredictableSourceRuleJudge(pr, manager)));
        config.numberSizeRules().forEach(n ->
                ruleToJudge.put(n, new NumberSizeRuleJudge(n, manager)));
        config.forbiddenMethodRules().forEach(f ->
                ruleToJudge.put(f, new ForbiddenMethodRuleJudge(f, manager)));
        config.influencingFactorRules().forEach(i ->
                ruleToJudge.put(i, new InfluencingFactorRuleJudge(i, manager, World.get().getClassHierarchy())));
        config.coOccurrenceRules().forEach(i ->
                ruleToJudge.put(i, new CoOccurrenceRuleJudge(i)));
        config.compositeRules().forEach(cr -> {
            compositeRules.add(cr);
            fromSourceToRule.put(cr.getFromSource(), cr);
            compositeFromSources.put(cr.getFromSource().method(), cr.getFromSource());
            cr.getToSources().forEach(toSource -> {
                toSourceToRule.put(toSource, cr);
                compositeToSources.put(toSource.method(), toSource);
            });
            cr.getTransfers().forEach(propagates ->
                    compositePropagates.put(propagates.method(), propagates));
        });
        calculateNumberRange();
        resourceRetrieverModel = new ResourceRetrieverModel(solver, solver.getHeapModel(), manager);
    }

    @Override
    public void onStart() {
        ClassHierarchy hierarchy = solver.getHierarchy();
        appClasses.addAll(appClassesInString.stream()
                .map(hierarchy::getClass)
                .collect(Collectors.toSet()));

        JClass objectClass = hierarchy.getClass("java.lang.Object");
        Set<JClass> focusedClass = Sets.newSet();

        focusedClass.addAll(sources.keySet().stream()
                .map(ClassMember::getDeclaringClass)
                .collect(Collectors.toSet()));
        focusedClass.addAll(ruleToJudge.keySet().stream()
                .map(rule -> rule.getMethod().getDeclaringClass())
                .collect(Collectors.toSet()));
        focusedClass.addAll(appClasses);

        Set<String> focusedPackages = Set.of("java.util");

        hierarchy.getAllSubclassesOf(objectClass).forEach(jClass -> {
            String className = jClass.getName();
            boolean isFocusedPackage = focusedPackages.stream().anyMatch(className::contains);
            if (!isFocusedPackage && !focusedClass.contains(jClass)) {
                jClass.getDeclaredMethods().forEach(solver::addIgnoredMethod);
            }
        });
    }

    @Override
    public void onNewStmt(Stmt stmt, JMethod container) {
        if (stmt instanceof Invoke invoke) {
            if (!invoke.isDynamic()) {
                resourceRetrieverModel.onNewStmt(stmt, container);
            }
        }
    }

    @Override
    public void onNewCSMethod(CSMethod csMethod) {
        JMethod jMethod = csMethod.getMethod();
        Context ctx = csMethod.getContext();
        if (jMethod.getDeclaringClass().isApplication()) {
            if (jMethod.isPublic()) {
                jMethod.getIR().getParams().forEach(var -> {
                    if (constTypes.contains(var.getType().getName())) {
                        Obj predictableObj = manager.makePredictableCryptoObj(var.getType());
                        solver.addVarPointsTo(csMethod.getContext(), var, emptyContext,
                                predictableObj);
                    }
                });
            }

            jMethod.getIR().getStmts().forEach(stmt -> {
                if (stmt instanceof AssignLiteral assignStmt) {
                    Var lhs = assignStmt.getLValue();
                    if (assignStmt.getRValue() instanceof StringLiteral stringLiteral) {
                        if (isPatternMatch(stringLiteral.getString())) {
                            CryptoObjInformation coi =
                                    new CryptoObjInformation(stmt, jMethod, stringLiteral.getString());
                            Obj cryptoObj = manager.makeCryptoObj(coi, stringLiteral.getType());
                            solver.addVarPointsTo(csMethod.getContext(), lhs, emptyContext,
                                    cryptoObj);
                        } else {
                            Obj predictableObj = manager.makePredictableCryptoObj(lhs.getType());
                            solver.addVarPointsTo(csMethod.getContext(), lhs, emptyContext,
                                    predictableObj);
                        }
                    }
//
                    if (assignStmt.getRValue() instanceof IntLiteral intLiteral) {
                        if (intLiteral.getNumber() < focusMinMin) {
                            Obj cryptoObj = manager.makeNumberCryptoObj();
                            solver.addVarPointsTo(csMethod.getContext(), lhs, emptyContext,
                                    cryptoObj);
                        } else if (intLiteral.getNumber() < focusMinMax &&
                                intLiteral.getNumber() >= focusMinMin) {
                            CryptoObjInformation coi =
                                    new CryptoObjInformation(stmt, jMethod, intLiteral.getValue());
                            Obj cryptoObj = manager.makeCryptoObj(coi, intLiteral.getType());
                            solver.addVarPointsTo(csMethod.getContext(), lhs, emptyContext,
                                    cryptoObj);
                        }
                    }
                }

                if (stmt instanceof StoreArray storeArray) {
                    Var base = storeArray.getLValue().getBase();
                    Var rhs = storeArray.getRValue();
                    elementToBase.put(rhs, base);
                    if (rhs.isConst()) {
                        CryptoObjInformation coi =
                                new CryptoObjInformation(stmt, jMethod, PREDICTABLE_DESC);
                        Obj cryptoObj = manager.makeCryptoObj(coi, base.getType());
                        solver.addVarPointsTo(ctx, base, emptyContext, cryptoObj);
                        logger.debug("the store array stmt " + storeArray + "is unsafe with type " + base.getType());
                    }
                }
            });
        }
    }

    @Override
    public void onNewPointsToSet(CSVar csVar, PointsToSet pts) {
        try {
            resourceRetrieverModel.onNewPointsToSet(csVar, pts);
        } catch (Exception ex) {
            logger.info("Retriever model has an invocation exception");
        }
        Var var = csVar.getVar();
        cryptoVarPropagates.get(var).forEach(p -> {
            Var to = p.first();
            Type type = p.second();
            propagateCryptoObj(pts, csVar.getContext(), to, type, false);
        });

        compositeVarPropagates.get(var).forEach(p -> {
            Var to = p.first();
            Type type = p.second();
            propagateCryptoObj(pts, csVar.getContext(), to, type, true);
        });

        compositeVarJudge.get(var).forEach(p -> {
            Stmt callSite = p.first();
            ToSource toSource = p.second();
            addJudgeStmtFromPts(var, pts, callSite, toSource);
        });

        elementToBase.get(var).forEach(base -> {
            pts.objects()
                    .map(CSObj::getObject)
                    .filter(manager::isCryptoObj)
                    .map(manager::getAllocationOfCOI)
                    .map(source -> manager.makeCryptoObj(source, base.getType()))
                    .map(cryptoObj -> csManager.getCSObj(emptyContext, cryptoObj))
                    .forEach(csObj -> solver.addVarPointsTo(csVar.getContext(), base, csObj));
        });
    }

    @Override
    public void onNewCallEdge(Edge<CSCallSite, CSMethod> edge) {
        Invoke callSite = edge.getCallSite().getCallSite();
        JMethod callee = edge.getCallee().getMethod();

        if (sources.containsKey(callee)) {
            sources.get(callee).forEach(source -> {
                Var var = IndexUtils.getVar(callSite, source.index());
                CryptoObjInformation coi =
                        new CryptoObjInformation(callSite, callSite.getContainer(), PREDICTABLE_DESC);
                Type type = source.type();
                Obj cryptoObj = manager.makeCryptoObj(coi, type);
                solver.addVarPointsTo(edge.getCallSite().getContext(), var,
                        edge.getCallSite().getContext(), cryptoObj);
            });
        }

        if (compositeFromSources.containsKey(callee)) {
            compositeFromSources.get(callee).forEach(compositeSource -> {
                Var var = IndexUtils.getVar(callSite, compositeSource.index());
                if (var != null) {
                    fromSourceToRule.get(compositeSource).forEach(compositeRule -> {
                        if (!fromVarToRule.containsKey(var) && compositeRule != null) {
                            CompositeRule cloneCompositeRule = compositeRule.clone();
                            cloneCompositeRule.setFromVar(var);
                            fromVarToRule.put(var, cloneCompositeRule);
                            Type type = compositeSource.type();
                            Obj compositeObj = manager.makeCompositeCryptoObj(cloneCompositeRule, type);
                            solver.addVarPointsTo(edge.getCallSite().getContext(), var,
                                    edge.getCallSite().getContext(), compositeObj);
                        }
                    });
                }
            });
        }

        if (compositeToSources.containsKey(callee)) {
            compositeToSources.get(callee).forEach(toSource -> {
                Var var = IndexUtils.getVar(callSite, toSource.index());
                if (var != null) {
                    Context ctx = edge.getCallSite().getContext();
                    CSVar csVar = csManager.getCSVar(ctx, var);
                    compositeVarJudge.put(var, new Pair<>(callSite, toSource));
                    addJudgeStmtFromPts(var, solver.getPointsToSetOf(csVar), callSite, toSource);
                }
            });
        }

        propagateOnCallEdge(edge, callSite, callee, propagates,
                cryptoVarPropagates, false);
        propagateOnCallEdge(edge, callSite, callee, compositePropagates,
                compositeVarPropagates, true);
    }


    /**
     * When adding pts to the pointer set of var, if pts contains compositeObj and
     * var is the 'toVar' in compositeRule, then add the statement containing 'toVar'
     * to the judgeStmt.
     */
    private void addJudgeStmtFromPts(Var var, PointsToSet pts, Stmt callSite, ToSource toSource) {
        pts.objects()
                .map(CSObj::getObject)
                .filter(manager::isCompositeCryptoObj)
                .map(manager::getAllocationOfRule)
                .forEach(compositeRule -> {
                    if (compositeRule.getToSources().contains(toSource)) {
                        compositeRule.getToVarToStmtAndToSource().put(var, new Pair<>(callSite, toSource));
                        logger.info("add judge stmt: " + callSite + " of to var: " + var);
                    }
                });
    }

    private void propagateOnCallEdge(Edge<CSCallSite, CSMethod> edge,
                                     Invoke callSite,
                                     JMethod callee,
                                     MultiMap<JMethod, CryptoObjPropagate> cryptoPropagates,
                                     MultiMap<Var, Pair<Var, Type>> cryptoVarPropagates,
                                     boolean isComposite) {
        cryptoPropagates.get(callee).forEach(propagate -> {
            Var from = getVar(callSite, propagate.from());
            Var to = getVar(callSite, propagate.to());
            // when transfer to result variable, and the call site
            // does not have result variable, then "to" is null.
            if (to != null) {
                Type type = propagate.type();
                cryptoVarPropagates.put(from, new Pair<>(to, type));
                Context ctx = edge.getCallSite().getContext();
                CSVar csFrom = csManager.getCSVar(ctx, from);
                propagateCryptoObj(solver.getPointsToSetOf(csFrom), ctx, to, type, isComposite);
            }
        });
    }

    private static Var getVar(Invoke callSite, int index) {
        InvokeExp invokeExp = callSite.getInvokeExp();
        return switch (index) {
            case CryptoObjPropagate.BASE -> ((InvokeInstanceExp) invokeExp).getBase();
            case CryptoObjPropagate.RESULT -> callSite.getResult();
            default -> invokeExp.getArg(index);
        };
    }

    /**
     * Transform the cryptoObj from pts into new type and add it to the pts of toVar.
     */
    private void propagateCryptoObj(PointsToSet pts, Context ctx, Var to, Type type, boolean isComposite) {
        PointsToSet newCryptoObjs = solver.makePointsToSet();

        pts.objects()
                .map(CSObj::getObject)
                .forEach(obj -> {
                    if (isComposite && manager.isCompositeCryptoObj(obj)) {
                        CompositeRule allocation = manager.getAllocationOfRule(obj);
                        Obj cryptoObj = manager.makeCompositeCryptoObj(allocation, type);
                        newCryptoObjs.addObject(csManager.getCSObj(emptyContext, cryptoObj));
                    } else {
                        if (manager.isCryptoObj(obj)) {
                            CryptoObjInformation allocation = manager.getAllocationOfCOI(obj);
                            Obj cryptoObj = manager.makeCryptoObj(allocation, type);
                            newCryptoObjs.addObject(csManager.getCSObj(emptyContext, cryptoObj));
                        }
                        if (manager.isPredictableCryptoObj(obj)) {
                            Obj newObj = manager.makePredictableCryptoObj(type);
                            newCryptoObjs.addObject(csManager.getCSObj(emptyContext, newObj));
                        }
                    }
                });

        if (!newCryptoObjs.isEmpty()) {
            solver.addVarPointsTo(ctx, to, newCryptoObjs);
        }
    }

    private void addCompositeIssue(List<Issue> issueList, PointerAnalysisResult result) {
        fromVarToRule.forEach((var, compositeRule) -> {
            if (compositeRule.getToVarToStmtAndToSource().size() >= compositeRule.getToSources().size()) {
                CompositeRuleIssue compositeRuleIssue = new CompositeRuleIssue();
                CompositeRuleJudge judge = new CompositeRuleJudge(compositeRule, manager);
                compositeRule.getToVarToStmtAndToSource().forEach((toVar, pair) -> {
                    Stmt stmt = pair.first();
                    compositeRuleIssue.addIssue(judge.judge(result, (Invoke) stmt));
                });
                if (!compositeRuleIssue.getIssues().isEmpty() && compositeRuleIssue.getPredicate() == 1) {
                    issueList.add(compositeRuleIssue);
                }
            }
        });
    }

    private void addSimpleIssue(List<Issue> issueList, PointerAnalysisResult result) {
        ruleToJudge.forEach((rule, ruleJudge) -> {
            Stream<Issue> issues;

            if (rule instanceof InfluencingFactorRule) {
                issues = Stream.ofNullable(ruleJudge.judge(result, null));
            } else {
                issues = result.getCallGraph()
                        .getCallersOf(rule.getMethod())
                        .stream()
                        .map(callSite -> ruleJudge.judge(result, callSite))
                        .filter(Objects::nonNull);
            }

            issues.forEach(issueList::add);
        });
    }

    private boolean isPatternMatch(String str) {
        return Stream.concat(
                config.patternMatchRules().stream(),
                config.compositeRules().stream()
                        .flatMap(compositeRule -> compositeRule.getToSources().stream())
                        .filter(toSource -> toSource.rule() instanceof PatternMatchRule)
                        .map(toSource -> (PatternMatchRule) toSource.rule())
        ).anyMatch(patternMatchRule -> Pattern.matches(patternMatchRule.pattern(), str));
    }

    private void calculateNumberRange() {
        Stream.concat(
                        config.numberSizeRules().stream(),
                        config.compositeRules().stream()
                                .flatMap(compositeRule -> compositeRule.getToSources().stream())
                                .filter(toSource -> toSource.rule() instanceof NumberSizeRule)
                                .map(toSource -> (NumberSizeRule) toSource.rule())
                )
                .forEach(numberSize -> {
                    if (focusMinMin > numberSize.min()) {
                        focusMinMin = numberSize.min();
                    }
                    if (focusMinMax < numberSize.min()) {
                        focusMinMax = numberSize.min();
                    }
                });
    }

    @Override
    public void onFinish() {
        ClassHierarchy classHierarchy = World.get().getClassHierarchy();
        PointerAnalysisResult result = solver.getResult();

        logger.info("Dumping crypto misuse reports to {}", CryptoAPIMisuseAnalysis.outputFile());

        List<Issue> issueList = new ArrayList<>();
        addSimpleIssue(issueList, result);
        addCompositeIssue(issueList, result);

        List<Issue> consistIssueList = issueList.stream()
                .flatMap(issue -> issue instanceof IssueList listTypeIssue ?
                        listTypeIssue.getIssues().stream() : Stream.of(issue))
                .collect(Collectors.toList());

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(CryptoAPIMisuseAnalysis.outputFile(), consistIssueList);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        result.storeResult(getClass().getName(), consistIssueList);
    }
}
