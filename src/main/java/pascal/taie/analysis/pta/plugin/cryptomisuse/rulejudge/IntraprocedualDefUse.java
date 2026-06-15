package pascal.taie.analysis.pta.plugin.cryptomisuse.rulejudge;

import pascal.taie.analysis.dataflow.analysis.ReachingDefinition;
import pascal.taie.analysis.dataflow.fact.DataflowResult;
import pascal.taie.analysis.dataflow.fact.SetFact;
import pascal.taie.analysis.defuse.DefUse;
import pascal.taie.analysis.defuse.DefUseAnalysis;
import pascal.taie.analysis.graph.cfg.CFG;
import pascal.taie.analysis.graph.cfg.CFGBuilder;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.config.ConfigManager;
import pascal.taie.ir.IR;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.language.classes.JMethod;
import pascal.taie.util.collection.SetQueue;
import pascal.taie.util.collection.Sets;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import static pascal.taie.config.AnalysisConfig.parseConfigs;

public class IntraprocedualDefUse {

    CFGBuilder cfgBuilder;
    ReachingDefinition reachingDefinition;
    DefUseAnalysis defUseAnalysis;

    public IntraprocedualDefUse(String fileName) {
        InputStream inputStream = IntraprocedualDefUse.class
                .getClassLoader()
                .getResourceAsStream(fileName);
        List<AnalysisConfig> analysisConfigs = parseConfigs(inputStream);
        ConfigManager manager = new ConfigManager(analysisConfigs);
        cfgBuilder = new CFGBuilder(manager.getConfig(CFGBuilder.ID));
        reachingDefinition = new ReachingDefinition(manager.getConfig(ReachingDefinition.ID));
        defUseAnalysis = new DefUseAnalysis(manager.getConfig(DefUseAnalysis.ID));
    }

    public void analyze(IR ir) {
        CFG<Stmt> cfg = cfgBuilder.analyze(ir);
        ir.storeResult(CFGBuilder.ID, cfg);
        DataflowResult<Stmt, SetFact<Stmt>> dataflowResult = reachingDefinition.analyze(ir);
        ir.storeResult(ReachingDefinition.ID, dataflowResult);
        DefUse defUse = defUseAnalysis.analyze(ir);
        ir.storeResult(DefUseAnalysis.ID, defUse);
    }

    public Set<Stmt> getInfluencingStmtsBackward(JMethod jMethod, Stmt stmt) {
        return getInfluencingStmts(jMethod, stmt, true);
    }

    public Set<Stmt> getInfluencingStmtsForward(JMethod jMethod, Stmt stmt) {
        return getInfluencingStmts(jMethod, stmt, false);
    }

    private Set<Stmt> getInfluencingStmts(JMethod jMethod, Stmt stmt, boolean backward) {
        IR ir = jMethod.getIR();
        DefUse defUse = (DefUse) ir.getResult(DefUseAnalysis.ID);
        Set<Stmt> visited = Sets.newHybridSet();
        Queue<Stmt> workList = new SetQueue<>();
        workList.add(stmt);

        while (!workList.isEmpty()) {
            Stmt visitStmt = workList.poll();
            if (!visited.contains(visitStmt)) {
                if (backward) {
                    visitStmt.getUses().forEach(rValue -> {
                        if (rValue instanceof Var useVar) {
                            Set<Stmt> stmts = defUse.getDefs(visitStmt, useVar);
                            if (stmts != null) {
                                workList.addAll(stmts);
                            }
                        }
                    });
                } else if (visitStmt.getDef().isPresent()) {
                    Set<Stmt> stmts = defUse.getUses(visitStmt);
                    if (stmts != null) {
                        workList.addAll(stmts);
                    }
                }
                visited.add(visitStmt);
            }
        }

        return visited;
    }
}
