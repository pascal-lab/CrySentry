package pascal.taie.analysis.pta.plugin.cryptomisuse.issue;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import pascal.taie.ir.stmt.Invoke;

import java.lang.invoke.CallSite;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PatternMatchIssue implements Issue {
    public PatternMatchIssue(String judgeType, String message, String sourceStmt,
                             String sourceMethod, Invoke callSite, String var,
                             String constantValue, String calleeMethod) {
        this.judgeType = judgeType;
        this.message = message;
        this.sourceStmt = sourceStmt;
        this.sourceMethod = sourceMethod;
        this.callSite = callSite;
        this.var = var;
        this.constantValue = constantValue;
        this.calleeMethod = calleeMethod;
    }

    @JsonProperty("judgeType")
    private String judgeType;

    @JsonProperty("message")
    private String message;

    @JsonProperty("sourceStmt")
    private String sourceStmt;

    @JsonProperty("sourceMethod")
    private String sourceMethod;

    public Invoke getCallSite() {
        return callSite;
    }

    @JsonProperty("callSite")
    private Invoke callSite;

    @JsonProperty("var")
    private String var;

    @JsonProperty("constantValue")
    private String constantValue;
    @JsonProperty("calleeMethod")
    private String calleeMethod;

    public String getCalleeMethod(){
        return  calleeMethod;
    }
}
