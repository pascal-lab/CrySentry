package pascal.taie.analysis.pta.cryptomisuse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pascal.taie.analysis.Tests;

public class ApacheTest {

    @Test
    public void activemq() {
        Tests.testPTAInLibraryProgramOfCrypto(Benchmark.ACTIVEMQ, true);
    }
    @Test
    public void deltaspike() {
        Tests.testPTAInLibraryProgramOfCrypto(Benchmark.DELTA, true);
    }

    @Test
    public void directory() {
        Tests.testPTAInLibraryProgramOfCrypto(Benchmark.DIRECTORY, true);
    }
    @Test
    public void incubator(){
        Tests.testPTAInLibraryProgramOfCrypto(Benchmark.INCU, true);
    }
    @Test
    public void manifoldcf() {
        Tests.testPTAInLibraryProgramOfCrypto(Benchmark.MANIFOLD, true);
    }
    @Test
    public void meecrowave() {
        Tests.testPTAInLibraryProgramOfCrypto(Benchmark.MEECRO, true);
    }

    @Test
    public void spark() {
        Tests.testPTAInLibraryProgramOfCrypto(Benchmark.SPARK, true);
    }
    @Test
    public void tika() {
        Tests.testPTAInLibraryProgramOfCrypto(Benchmark.TIKA, true);
    }
    @Test
    public void tomee() {
        Tests.testPTAInLibraryProgramOfCrypto(Benchmark.TOMEE, true);
    }

    @Test
    public void wicket() {
        Tests.testPTAInLibraryProgramOfCrypto(Benchmark.WICKET, true);
    }
}
