package pascal.taie.analysis.pta.cryptomisuse.ApacheTest1;

import org.junit.jupiter.api.Test;
import pascal.taie.analysis.Tests;
import pascal.taie.analysis.pta.cryptomisuse.Benchmark;

public class ActivemqTest {
    @Test
    public void activemq() {
        Tests.testPTAInLibraryProgramOfCrypto(Benchmark.ACTIVEMQ, true);
    }
}
