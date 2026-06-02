package pascal.taie.analysis.pta.plugin.cryptomisuse.reachableplugin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pascal.taie.analysis.pta.core.heap.Descriptor;
import pascal.taie.analysis.pta.core.heap.HeapModel;
import pascal.taie.analysis.pta.core.solver.EntryPoint;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.core.solver.SpecifiedParamProvider;
import pascal.taie.analysis.pta.plugin.Plugin;
import pascal.taie.analysis.pta.plugin.cryptomisuse.CryptoAPIMisuseAnalysis;
import pascal.taie.analysis.pta.plugin.spring.AppFirstParamProvider;
import pascal.taie.language.classes.ClassHierarchy;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JMethod;

public class CryptoReachablePlugin implements Plugin {

    private static final Logger logger = LogManager.getLogger(CryptoReachablePlugin.class);

    private static final Descriptor CRYPTO_REACHABLE_DESC = () -> "CryptoReachableObj";

    private Solver solver;

    private ClassHierarchy classHierarchy;

    private HeapModel heapModel;

    @Override
    public void setSolver(Solver solver) {
        this.solver = solver;
        classHierarchy = solver.getHierarchy();
        heapModel = solver.getHeapModel();
    }

    public void onStart() {
        for (JClass jClass : CryptoAPIMisuseAnalysis.getAppClasses()) {
            for (JMethod method : jClass.getDeclaredMethods()) {
                if ((!method.isPrivate() || method.isStatic()) && !method.isAbstract()
                        && !method.isNative() && !method.isPrivate()) {
                    SpecifiedParamProvider.Builder builder = new SpecifiedParamProvider.Builder(method)
                            .setDelegate(new CryptoReachableParamProvider(method, classHierarchy, solver));
                    builder.addThisObj(heapModel.getMockObj(
                            CRYPTO_REACHABLE_DESC, jClass.getType(), jClass.getType()));
                    SpecifiedParamProvider paramProvider = builder.build();
                    logger.debug("""
                            [Crypto Reachable Analysis] Adding entry point
                            \tmethod: {}
                            \tparamProvider: {}
                            """, method, paramProvider);
                    solver.addEntryPoint(new EntryPoint(method, paramProvider));
                }
            }
        }
    }
}
