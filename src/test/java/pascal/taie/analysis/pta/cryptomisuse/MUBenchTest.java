package pascal.taie.analysis.pta.cryptomisuse;

import org.junit.jupiter.api.Test;
import pascal.taie.analysis.Tests;
import pascal.taie.util.AppClassInferringUtils;
import pascal.taie.util.DirectoryTraverser;
import pascal.taie.util.ZipUtils;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.Sets;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;


public class MUBenchTest {

    @Test
    public void aliyun() {
        Tests.testPTAInLibraryProgramOfCrypto(Benchmark.ALIYUN, true);
    }

//    @Test
//    public void biglybtCore() {
//        Tests.testPTAInLibraryProgramOfCrypto(Benchmark.BICORE, true);
//    }

    @Test
    public void bt() {
        Tests.testPTAInLibraryProgramOfCrypto(Benchmark.BT, true);
    }

    @Test
    public void dubbo() {
        Tests.testPTAInLibraryProgramOfCrypto(Benchmark.DUBBO, true);
    }

    @Test
    public void fastBoot() {
        Tests.testPTAInLibraryProgramOfCrypto(Benchmark.FASTBOOT, true);
    }

    @Test
    public void gameServer() {
        Tests.testPTAInLibraryProgramOfCrypto(Benchmark.GAMESERVER, true);
    }

    @Test
    public void telegram() {
        Tests.testPTAInLibraryProgramOfCrypto(Benchmark.TELEGRAM, true);
    }


    @Test
    public void haBridge() {
        Tests.testPTAInLibraryProgramOfCrypto(Benchmark.HABRIDGE, true);
    }

    @Test
    public void hsweb() {
        Tests.testPTAInLibraryProgramOfCrypto(Benchmark.HSWEB, true);
    }

    @Test
    public void ijpay() {
        Tests.testPTAInLibraryProgramOfCrypto(Benchmark.IJPAY, true);
    }

    @Test
    public void instagram4j() {
        Tests.testPTAInLibraryProgramOfCrypto(Benchmark.INSTAGRAM, true);
    }

    @Test
    public void j360() {
        Tests.testPTAInLibraryProgramOfCrypto(Benchmark.J360, true);
    }

    @Test
    public void springQuick() {
        Tests.testPTAInLibraryProgramOfCrypto(Benchmark.SPRINGQUICK, true);
    }

    @Test
    public void springStudent() {
        Tests.testPTAInLibraryProgramOfCrypto(Benchmark.SPRINGSTUDENT, true);
    }

    @Test
    public void nettyGameServer() {
        Tests.testPTAInLibraryProgramOfCrypto(Benchmark.NETTYGAMESERVER, true);
    }

    @Test
    public void mpush() {
        Tests.testPTAInLibraryProgramOfCrypto(Benchmark.MPUSH, true);
    }

    @Test
    public void myBlog() {
        Tests.testPTAInLibraryProgramOfCrypto(Benchmark.MYBLOG, true);
    }

    @Test
    public void saturnApi() {
        Tests.testPTAInLibraryProgramOfCrypto(Benchmark.SATURNAPI, true);
    }

    @Test
    public void saturnCore() {
        Tests.testPTAInLibraryProgramOfCrypto(Benchmark.SATURNCORE, true);
    }

    @Test
    public void protools() {
        Tests.testPTAInLibraryProgramOfCrypto(Benchmark.PROTOOLS, true);
    }

    @Test
    public void smart() {
        Tests.testPTAInLibraryProgramOfCrypto(Benchmark.SMART, true);
    }

    @Test
    public void zheng() {
        Tests.testPTAInLibraryProgramOfCrypto(Benchmark.ZHENG, true);
    }

    @Test
    public void rigorityj() {
        Tests.testPTAInLibraryProgramOfCrypto(Benchmark.RIGORITYJ, true);
        System.out.printf("");
    }

    @Test
    public void cryptoTest() {
        Tests.testPTAInLibraryProgramOfCrypto(Benchmark.CRYPTOTEST, true);
    }

//    @Test
//    public void gruul() {
//        Tests.testPTABySpringBootArchives(Benchmark.GRUUL, true);
//    }


    /**
     * uncompress Spring archives and construct app-info.yml
     */
    //@Test
    public void uncompressSpringArchives() throws IOException {
        Path uncompressDir = Path.of("uncompressed-microservice-benchmarks").toAbsolutePath();
        StringBuilder sb = new StringBuilder();
        for (Benchmark benchmark : Benchmark.values()) {
            Collection<String> appJarPaths = Sets.newSet();
            Collection<String> libJarPaths;
            Map<String, String> libName2LibJarPath = Maps.newMap();
            // uncompress jar and war archives
            File benchmarkDir = new File(benchmark.dir);
            for (File archive : Objects.requireNonNull(benchmarkDir.listFiles())) {
                String archiveName = archive.getName();
                if (archiveName.endsWith(".jar") || archiveName.endsWith(".war")) {
                    // uncompress archive
                    Path targetDir = uncompressDir.resolve(benchmarkDir.getName())
                            .resolve(archiveName.substring(0, archiveName.lastIndexOf(".")));
                    targetDir.toFile().mkdirs();
                    ZipUtils.uncompressZipFile(archive.getAbsolutePath(), targetDir.toFile().getAbsolutePath());
                    // construct class path
                    Path inf = targetDir.resolve("BOOT-INF");
                    if (!inf.toFile().exists()) {
                        inf = targetDir.resolve("WEB-INF");
                    }
                    // lib class path
                    Path lib = inf.resolve("lib");
                    for (File file : Objects.requireNonNull(lib.toFile().listFiles())) {
                        if (file.getName().endsWith(".jar")) {
                            libName2LibJarPath.put(file.getName(), file.getAbsolutePath());
                        }
                    }
                    // compress app classes to jar
                    Path classes = inf.resolve("classes");
                    Path jarFile = classes.resolveSibling(classes.getFileName() + ".jar");
                    ZipUtils.compressDirectory(classes, jarFile, false);
                    removeDirectory(classes);
                    appJarPaths.add(jarFile.toFile().getAbsolutePath());
                }
            }
            libJarPaths = new HashSet<>(libName2LibJarPath.values());
            // infer the actual app jar paths
            List<String> appClasses = appJarPaths.stream()
                    .map(DirectoryTraverser::listClassesInJar)
                    .flatMap(Collection::stream)
                    .toList();
            Collection<String> inferredAppJarPaths = AppClassInferringUtils.inferAppJarPaths(
                    appClasses, libJarPaths);
            appJarPaths.addAll(inferredAppJarPaths);
            libJarPaths.removeAll(inferredAppJarPaths);
            // change to relative path
            appJarPaths = appJarPaths.stream()
                    .map(Path::of)
                    .map(uncompressDir::relativize)
                    .map(Path::toString)
                    .sorted()
                    .toList();
            libJarPaths = libJarPaths.stream()
                    .map(Path::of)
                    .map(uncompressDir::relativize)
                    .map(Path::toString)
                    .sorted()
                    .toList();
            // output app-info
            sb.append(benchmarkDir.getName()).append(":\n");
            sb.append("  apps:\n");
            for (String appClassPath : appJarPaths) {
                sb.append("    - \"").append(appClassPath.replace('\\', '/')).append("\"\n");
            }
            sb.append("  libs:\n");
            for (String value : libJarPaths) {
                sb.append("    - \"").append(value.replace('\\', '/')).append("\"\n");
            }
            sb.append("\n");
        }
        Files.writeString(uncompressDir.resolve("app-info.yml"), sb.toString());
    }

    public static void removeDirectory(Path dir) throws IOException {
        Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }
}
