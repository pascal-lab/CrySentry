package pascal.taie.analysis.pta.plugin.cryptomisuse.rulejudge;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pascal.taie.analysis.pta.PointerAnalysisResult;
import pascal.taie.analysis.pta.plugin.cryptomisuse.*;
import pascal.taie.analysis.pta.plugin.cryptomisuse.issue.Issue;
import pascal.taie.analysis.pta.plugin.cryptomisuse.issue.IssueList;
import pascal.taie.analysis.pta.plugin.cryptomisuse.issue.PatternMatchIssue;
import pascal.taie.analysis.pta.plugin.cryptomisuse.rule.PatternMatchRule;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.ir.stmt.Stmt;

public class PatternMatchRuleJudge implements RuleJudge {

    PatternMatchRule patternMatchRule;

    CryptoObjManager manager;

    Logger logger = LogManager.getLogger(PatternMatchRuleJudge.class);

    public PatternMatchRuleJudge(PatternMatchRule patternMatchRule, CryptoObjManager manager) {
        this.patternMatchRule = patternMatchRule;
        this.manager = manager;
    }

    public Issue judge(PointerAnalysisResult result, Invoke callSite) {
        Var var = IndexUtils.getVar(callSite, patternMatchRule.index());
        IssueList issueList = new IssueList();
        AtomicReference<Issue> issue = new AtomicReference<>();

        if (CryptoAPIMisuseAnalysis.getAppClasses().contains(
                callSite.getContainer().getDeclaringClass())) {
            result.getPointsToSet(var).stream()
                    .filter(manager::isCryptoObj)
                    .forEach(cryptoObj -> {
                        if (cryptoObj.getAllocation() instanceof CryptoObjInformation coi &&
                                coi.constantValue() instanceof String value) {
                            logger.debug("coi constant value is " + value);
                            boolean matches = Pattern.matches(patternMatchRule.pattern(), value);
                            if (matches) {
                                issue.set(report(coi, var, callSite));
                                issueList.addIssue(issue.get());
                            }
                        }
                    });

            logger.debug("the result of " + callSite + " is " + !issueList.getIssues().isEmpty());
        }

        return issueList.getIssues().isEmpty() ? null : issueList;
    }

    public Issue report(CryptoObjInformation coi, Var var, Invoke callSite) {
        if(patternMatchRule.pattern().contains("http")) {
            PatternMatchIssue issue = new PatternMatchIssue("Pattern Match",
                    "This HTTP link is vulnerable to interception; use HTTPS to secure the transmission of information.",
                    coi.allocation().toString(),
                    coi.sourceMethod().toString(),
                    callSite, var.getName(),
                    coi.constantValue().toString(), patternMatchRule.method().toString());
            return issue;
        }
        else{
            PatternMatchIssue issue = new PatternMatchIssue("Pattern Match",
                    "The algorithm used in this API call, is insecure and may lead to data leakage due to vulnerability to attacks.",
                    coi.allocation().toString(),
                    coi.sourceMethod().toString(),
                    callSite, var.getName(),
                    coi.constantValue().toString(), patternMatchRule.method().toString());
            return issue;
        }
    }
}
