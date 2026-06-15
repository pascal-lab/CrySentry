package pascal.taie.analysis.pta.plugin.cryptomisuse.rulejudge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pascal.taie.analysis.pta.PointerAnalysisResult;
import pascal.taie.analysis.pta.plugin.cryptomisuse.CryptoObjInformation;
import pascal.taie.analysis.pta.plugin.cryptomisuse.issue.CoOccurrenceRuleIssue;
import pascal.taie.analysis.pta.plugin.cryptomisuse.issue.Issue;
import pascal.taie.analysis.pta.plugin.cryptomisuse.rule.CoOccurrenceRule;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.language.classes.JMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CoOccurrenceRuleJudge implements RuleJudge {

    CoOccurrenceRule coOccurrenceRule;

    IntraprocedualDefUse intraprocedualDefUse;

    Logger logger = LogManager.getLogger(CoOccurrenceRuleJudge.class);

    public CoOccurrenceRuleJudge(CoOccurrenceRule coOccurrenceRule) {
        this.coOccurrenceRule = coOccurrenceRule;
        this.intraprocedualDefUse = new IntraprocedualDefUse("defuse-config.yml");
    }

    public Issue judge(PointerAnalysisResult result, Invoke callSite) {
        String index = coOccurrenceRule.index();
        List<String> indexes = parseString(index);

        Set<String> needExist = indexes.stream()
                .filter(str -> !str.startsWith("!"))
                .collect(Collectors.toSet());

        Set<String> shouldNotExist = indexes.stream()
                .filter(str -> str.startsWith("!"))
                .map(str -> str.substring(1))
                .collect(Collectors.toSet());

        JMethod method = callSite.getContainer();
        intraprocedualDefUse.analyze(method.getIR());
        Set<Stmt> influencedStmts = intraprocedualDefUse.getInfluencingStmtsForward(method, callSite);

        boolean need = needExist.stream().allMatch(str -> judgeExist(influencedStmts, str, result));
        boolean shouldNot = shouldNotExist.stream().noneMatch(str -> judgeExist(influencedStmts, str, result));

        if (need && shouldNot) {
            return report(null, null, callSite);
        }
        return null;
    }

    public Issue report(CryptoObjInformation coi, Var var, Invoke callSite) {
        String index = coOccurrenceRule.index();
        JMethod method = coOccurrenceRule.getMethod();
        String description = "Should use all the invocation of " +
                parseString(index).stream()
                        .map(str -> str.startsWith("!") ? str.substring(1) : str)
                        .collect(Collectors.joining(" and ")) +
                " when calling method " + method;

        CoOccurrenceRuleIssue issue = new CoOccurrenceRuleIssue(
                "Co Occurrence",
                description,
                method.toString(),
                method.getSubsignature().toString()
        );
        return issue;
    }

    private List<String> parseString(String input) {
        // 去除外层的方括号和单引号
        String trimmedInput = input.substring(1, input.length() - 1).replace("'", "");

        // 以逗号为分隔符进行拆分
        List<String> result = new ArrayList<>();
        int level = 0;
        StringBuilder sb = new StringBuilder();
        for (char c : trimmedInput.toCharArray()) {
            if (c == ',' && level == 0) {
                result.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                if (c == '<') level++;
                else if (c == '>') level--;
                sb.append(c);
            }
        }
        result.add(sb.toString().trim());
        return result;
    }

    private boolean judgeExist(Set<Stmt> stmts, String criterion, PointerAnalysisResult result) {
        boolean res = stmts.stream()
                .filter(Invoke.class::isInstance)
                .map(Invoke.class::cast)
                .anyMatch(invoke -> criterion.startsWith("<")
                        ? result.getCallGraph()
                        .getCalleesOf(invoke)
                        .stream()
                        .anyMatch(jMethod1 -> jMethod1.getSignature().contains(criterion))
                        : invoke.getInvokeExp().toString().contains(criterion));
        return res;
    }
}
