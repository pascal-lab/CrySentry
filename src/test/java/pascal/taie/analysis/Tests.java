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

package pascal.taie.analysis;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pascal.taie.Main;
import pascal.taie.World;
import pascal.taie.analysis.graph.cfg.CFGBuilder;
import pascal.taie.analysis.misc.IRDumper;
import pascal.taie.analysis.misc.ResultProcessor;
import pascal.taie.analysis.pta.PointerAnalysis;
import pascal.taie.analysis.pta.plugin.cryptomisuse.CryptoAPIMisuseAnalysis;
import pascal.taie.analysis.pta.plugin.cryptomisuse.resource.ResourceRetrieverModel;
import pascal.taie.analysis.pta.plugin.spring.MicroserviceHolder;
import pascal.taie.analysis.pta.cryptomisuse.Benchmark;
import pascal.taie.util.AppClassInferringUtils;
import pascal.taie.util.DirectoryTraverser;
import pascal.taie.util.ZipUtils;
import pascal.taie.util.collection.Sets;
import pascal.taie.util.collection.Tuple;
import pascal.taie.analysis.pta.plugin.assertion.AssertionChecker;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Static utility methods for testing.
 */
public final class Tests {

    private static final Logger logger = LogManager.getLogger(Tests.class);

    private Tests() {
    }

    /**
     * Whether generate expected results or not.
     */
    private static final boolean GENERATE_EXPECTED_RESULTS = false;

    /**
     * Whether dump IR or not.
     */
    private static final boolean DUMP_IR = false;

    /**
     * Whether dump control-flow graphs or not.
     */
    private static final boolean DUMP_CFG = false;

    /**
     * Starts an analysis for a specific test case.
     * Requires a main method in the given class.
     *
     * @param mainClass the main class to be analyzed
     * @param classPath where the main class is located
     * @param id        ID of the analysis to be executed
     * @param opts      options for the analysis
     */
    public static void testMain(String mainClass, String classPath,
                                String id, String... opts) {
        test(mainClass, true, classPath, id, opts);
    }

    /**
     * Starts an analysis for a specific test case.
     * Do not require a main method in the given class.
     *
     * @param inputClass the input class to be analyzed
     * @param classPath  where the input class is located
     * @param id         ID of the analysis to be executed
     * @param opts       options for the analysis
     */
    public static void testInput(String inputClass, String classPath,
                                 String id, String... opts) {
        test(inputClass, false, classPath, id, opts);
    }

    /**
     * Starts an analysis for a specific test case.
     *
     * @param clz         the class to be analyzed
     * @param isMainClass if the class contains main method
     * @param classPath   where the main class is located
     * @param id          ID of the analysis to be executed
     * @param opts        options for the analysis
     */
    private static void test(String clz, boolean isMainClass,
                             String classPath, String id, String... opts) {
        List<String> args = new ArrayList<>();
        args.add("-pp");
        Collections.addAll(args, "-cp", classPath);
        Collections.addAll(args, isMainClass ? "-m" : "--input-classes", clz);
        if (DUMP_IR) {
            // dump IR
            Collections.addAll(args, "-a", IRDumper.ID);
        }
        if (DUMP_CFG) {
            // dump control-flow graphs
            Collections.addAll(args, "-a",
                    String.format("%s=dump:true", CFGBuilder.ID));
        }
        // set up the analysis
        if (opts.length > 0 && !opts[0].equals("-a")) {
            // if the opts is not empty, and the opts[0] is not "-a",
            // then this option is given to analysis *id*.
            Collections.addAll(args, "-a", id + "=" + opts[0]);
            args.addAll(Arrays.asList(opts).subList(1, opts.length));
        } else {
            Collections.addAll(args, "-a", id);
            Collections.addAll(args, opts);
        }
        // set up result processor
        String action = GENERATE_EXPECTED_RESULTS ? "dump" : "compare";
        String file = getExpectedFile(classPath, clz, id);
        String processArg = String.format("%s=analyses:[%s];action:%s;action-file:%s",
                ResultProcessor.ID, id, action, file);
        Collections.addAll(args, "-a", processArg);
        Main.main(args.toArray(new String[0]));
        if (action.equals("compare")) {
            Set<String> mismatches = World.get().getResult(ResultProcessor.ID);
            assertTrue(mismatches.isEmpty(),
                    "Mismatches of analysis \"" + id + "\":\n" +
                            String.join("\n", mismatches));
        }
    }


    public static void testPTA(boolean processResult, String dir, String main, String... opts) {
        String id = PointerAnalysis.ID;
        List<String> args = new ArrayList<>();
        args.add("-pp");
        String classPath = "src/test/resources/pta/" + dir;
        Collections.addAll(args, "-cp", classPath);
        Collections.addAll(args, "-m", main);
        if (DUMP_IR) {
            // dump IR
            Collections.addAll(args, "-a", IRDumper.ID);
        }
        List<String> ptaArgs = new ArrayList<>();
        ptaArgs.add("implicit-entries:false");
        ptaArgs.add("crypto-output:" + "crypto-output/cryptobench/" + main + ".json");
        String expectedFile = getExpectedFile(classPath, main, id);
//        if (processResult) {
//            ptaArgs.add(GENERATE_EXPECTED_RESULTS ? "dump:true"
//                    : "expected-file:" + expectedFile);
//        }
        boolean specifyOnlyApp = false;
        for (String opt : opts) {
            ptaArgs.add(opt);
            if (opt.contains("only-app")) {
                specifyOnlyApp = true;
            }
        }
        Set<String> appClasses = Sets.newSet();
        appClasses.add(main);
        CryptoAPIMisuseAnalysis.addAppClass(appClasses);
//        if (!specifyOnlyApp) {
//            // if given options do not specify only-app, then set it true
//            ptaArgs.add("only-app:true");
//        }
        Collections.addAll(args, "-a", id + "=" + String.join(";", ptaArgs));
        Main.main(args.toArray(new String[0]));
        // move expected file
        if (processResult && GENERATE_EXPECTED_RESULTS) {
            try {
                Path from = new File(World.get().getOptions().getOutputDir(),
                        pascal.taie.analysis.pta.plugin.ResultProcessor.RESULTS_FILE).toPath();
                Files.move(from, Paths.get(expectedFile), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                logger.error("Failed to copy expected file", e);
            }
        }
    }


    public static void testPTABySpringBootArchives(Benchmark benchmark, boolean withDependency) {
        MicroserviceHolder.clear();

        if (!Path.of(benchmark.dir).toFile().isDirectory()) {
            logger.error("Directory not exists: {}", benchmark.dir);
            return;
        }

        List<String> cryptoArchives;
        try {
            cryptoArchives = Files.list(Path.of(benchmark.dir))
                    .filter(path -> path.toString().endsWith(".jar")
                            || path.toString().endsWith(".war"))
                    .map(Path::toAbsolutePath)
                    .map(Path::toString)
                    .toList();
            logger.info("{} Microservices detected: {}", cryptoArchives.size(), cryptoArchives);
        } catch (IOException e) {
            logger.error("", e);
            throw new RuntimeException(e);
        }

        var tuples = initializeSpringBootArchives(cryptoArchives);
        // iterator tuples indexly
        for (int i = 0; i < tuples.size(); i++) {
            var tuple = tuples.get(i);
            List<String> appClasses = AppClassInferringUtils.getAllAppClasses(tuple.second(), tuple.third());
            new MicroserviceHolder(cryptoArchives.get(i), tuple.first(), tuple.third(), appClasses);
        }

        List<String> dependencyPaths = new ArrayList<>();

        // de-duplication the dependency jar and add it into dependencyPaths
        tuples.stream()
                .map(Tuple::third)
                .flatMap(Collection::stream)
                .map(File::new)
                .collect(Collectors.toMap(File::getName, Function.identity(), (o1, o2) -> o1))
                .values()
                .stream()
                .map(File::getAbsolutePath)
                // fix Soot issue:
                // Trying to create interface invoke expression for non-interface type: org.bouncycastle.asn1.ASN1Encodable
                .filter(o -> !o.contains("bcprov-jdk"))
                // fix Soot issue:
                // This operation requires resolving level HIERARCHY but net.sf.cglib.proxy.MethodInterceptor is at resolving level DANGLING
                // If you are extending Soot, try to add the following call before calling soot.Main.main(..):
                // Scene.v().addBasicClass(net.sf.cglib.proxy.MethodInterceptor,HIERARCHY);
                .filter(o -> !o.contains("seata-all"))
                //// fix Soot issue:
                //// Failed to apply jb to <org.elasticsearch.search.aggregations.metrics.AbstractHyperLogLog: void <clinit>()>
                //.filter(o -> !o.contains("elasticsearch-7.10.1.jar"))
                .forEach(dependencyPaths::add);

        Collection<String> appPathsInDependency = AppClassInferringUtils.inferAppJarPaths(
                tuples.stream().map(Tuple::second).flatMap(Collection::stream).toList(),
                dependencyPaths);
        dependencyPaths.removeAll(appPathsInDependency);

        String appClassPath = Stream.concat(tuples.stream().map(Tuple::first), appPathsInDependency.stream())
                .collect(Collectors.joining(File.pathSeparator));

        boolean onlyApp = false;
        String cs = "ci";

        List<String> args = new ArrayList<>();
        Collections.addAll(args, "-java", "8");
        Collections.addAll(args, "-ap");
        Collections.addAll(args, "--pre-build-ir");
        Collections.addAll(args, "--output-dir", "output/" + benchmark.name);
        if (withDependency) {
            Collections.addAll(args, "-cp", String.join(File.pathSeparator, dependencyPaths));
        }
        Collections.addAll(args, "-acp", appClassPath);
        // Collections.addAll(args, "--input-classes", String.join(",", appClasses));
        Collections.addAll(args,
                // "-a", "ir-dumper",
                "-a", """
                        pta=
                        implicit-entries:false;
                        dump:false;
                        only-app:%s;
                        only-app-reflection:false;
                        handle-invokedynamic:true;
                        merge-string-constants:true;
                        reflection:null;
                        cs:%s;
                        plugins:[pascal.taie.analysis.pta.plugin.spring.SpringAnalysis,
                                 pascal.taie.analysis.pta.plugin.Profiler];
                        """.formatted(onlyApp, cs),
                "-a", """
                        cg=
                        algorithm:pta;
                        dump-methods:true;
                        dump-call-edges:true;
                        """
        );

        Main.main(args.toArray(new String[0]));
        MicroserviceHolder.reportAllStatistics();
    }

    public static void testPTABySpringBootArchivesOfCrypto(Benchmark benchmark, boolean withDependency) {
        MicroserviceHolder.clear();

        if (!Path.of(benchmark.dir).toFile().isDirectory()) {
            logger.error("Directory not exists: {}", benchmark.dir);
            return;
        }

        List<String> cryptoArchives1;
        List<String> cryptoArchives2;
        List<String> cryptoArchives = new ArrayList<>();
        try {
            cryptoArchives1 = Files.list(Path.of(benchmark.dir))
                    .filter(path -> path.toString().endsWith(".jar")
                            || path.toString().endsWith(".war"))
                    .map(Path::toAbsolutePath)
                    .map(Path::toString)
                    .toList();
//            cryptoArchives2 = Files.list(Path.of(benchmark.dir + "/dependencies"))
//                    .filter(path -> path.toString().endsWith(".jar")
//                            || path.toString().endsWith(".war"))
//                    .map(Path::toAbsolutePath)
//                    .map(Path::toString)
//                    .toList();
            cryptoArchives.addAll(cryptoArchives1);
//            cryptoArchives.addAll(cryptoArchives2);
//            cryptoArchives.forEach(s -> {
//                System.out.println(s);
//            });
            logger.info("{} Microservices detected: {}", cryptoArchives.size(), cryptoArchives);
        } catch (IOException e) {
            logger.error("", e);
            throw new RuntimeException(e);
        }

        var tuple = initializeSpringBootArchivesOfCrypto(cryptoArchives);
        // iterator tuples indexly
        List<String> appClasses = DirectoryTraverser.listClasses(tuple.first());
        new MicroserviceHolder(cryptoArchives.get(0), tuple.first(), tuple.third(), appClasses);

        List<String> dependencyPaths = new ArrayList<>();

        // de-duplication the dependency jar and add it into dependencyPaths
        List<Tuple<String, List<String>, List<String>>> tuples = new ArrayList<>();
        tuples.add(tuple);
        tuples.stream()
                .map(Tuple::third)
                .flatMap(Collection::stream)
                .map(File::new)
                .collect(Collectors.toMap(File::getName, Function.identity(), (o1, o2) -> o1))
                .values()
                .stream()
                .map(File::getAbsolutePath)
                // fix Soot issue:
                // Trying to create interface invoke expression for non-interface type: org.bouncycastle.asn1.ASN1Encodable
                .filter(o -> !o.contains("bcprov-jdk"))
                // fix Soot issue:
                // This operation requires resolving level HIERARCHY but net.sf.cglib.proxy.MethodInterceptor is at resolving level DANGLING
                // If you are extending Soot, try to add the following call before calling soot.Main.main(..):
                // Scene.v().addBasicClass(net.sf.cglib.proxy.MethodInterceptor,HIERARCHY);
                .filter(o -> !o.contains("seata-all"))
                //// fix Soot issue:
                //// Failed to apply jb to <org.elasticsearch.search.aggregations.metrics.AbstractHyperLogLog: void <clinit>()>
                //.filter(o -> !o.contains("elasticsearch-7.10.1.jar"))
                .forEach(dependencyPaths::add);

        Collection<String> appPathsInDependency = AppClassInferringUtils.inferAppJarPaths(
                tuples.stream().map(Tuple::second).flatMap(Collection::stream).toList(),
                dependencyPaths);
        dependencyPaths.removeAll(appPathsInDependency);

        String appClassPath = Stream.concat(tuples.stream().map(Tuple::first), appPathsInDependency.stream())
                .collect(Collectors.joining(File.pathSeparator));

        boolean onlyApp = false;
        String cs = "ci";

        List<String> args = new ArrayList<>();
        Collections.addAll(args, "-java", "8");
        Collections.addAll(args, "-ap");
        Collections.addAll(args, "--pre-build-ir");
        Collections.addAll(args, "--output-dir", "output/" + benchmark.name);
        if (withDependency) {
            Collections.addAll(args, "-cp", String.join(File.pathSeparator, dependencyPaths));
        }
        Collections.addAll(args, "-acp", appClassPath);
        // Collections.addAll(args, "--input-classes", String.join(",", appClasses));
        Collections.addAll(args,
                // "-a", "ir-dumper",
                "-a", """
                        pta=
                        implicit-entries:false;
                        dump:false;
                        only-app:%s;
                        handle-invokedynamic:true;
                        merge-string-builders:true;
                        reflection:null;
                        cs:%s;
                        crypto-output:%s
                        propagate-types:[reference,byte,char];
                        crypto-config:src/test/resources/pta/cryptomisuse/crypto-config.yml
                        plugins:[pascal.taie.analysis.pta.plugin.spring.SpringAnalysis,
                                 pascal.taie.analysis.pta.plugin.Profiler];
                        """.formatted(onlyApp, cs, "crypto-output/" + benchmark.name + ".json"),
                "-a", """
                        cg=
                        algorithm:pta;
                        dump-methods:true;
                        dump-call-edges:true;
                        """
        );

        Main.main(args.toArray(new String[0]));
        MicroserviceHolder.reportAllStatistics();
    }

    public static void testPTAInLibraryProgramOfCrypto(Benchmark benchmark, boolean withDependency) {
        if (!Path.of(benchmark.dir).toFile().isDirectory()) {
            logger.error("Directory not exists: {}", benchmark.dir);
            return;
        }

        List<String> cryptoArchives1;
//        List<String> cryptoArchives2;
        List<String> cryptoArchives = new ArrayList<>();
        try {
            cryptoArchives1 = Files.list(Path.of(benchmark.dir))
                    .filter(path -> path.toString().endsWith(".jar")
                            || path.toString().endsWith(".war"))
                    .map(Path::toAbsolutePath)
                    .map(Path::toString)
                    .toList();
//            cryptoArchives2 = Files.list(Path.of(benchmark.dir + "/dependencies"))
//                    .filter(path -> path.toString().endsWith(".jar")
//                            || path.toString().endsWith(".war"))
//                    .map(Path::toAbsolutePath)
//                    .map(Path::toString)
//                    .toList();
            cryptoArchives.addAll(cryptoArchives1);
//            cryptoArchives.addAll(cryptoArchives2);
            logger.info("{} jar detected: {}", cryptoArchives.size(), cryptoArchives);
        } catch (IOException e) {
            logger.error("", e);
            throw new RuntimeException(e);
        }

        var tuple = initializeSpringBootArchivesOfCrypto(cryptoArchives);
        // iterator tuples indexly
        List<String> dependencyPaths = new ArrayList<>();

        // de-duplication the dependency jar and add it into dependencyPaths
        List<Tuple<String, List<String>, List<String>>> tuples = new ArrayList<>();
        tuples.add(tuple);

        Collection<String> appPathsInDependency = AppClassInferringUtils.inferAppJarPaths(
                tuples.stream().map(Tuple::second).flatMap(Collection::stream).toList(),
                dependencyPaths);
        dependencyPaths.removeAll(appPathsInDependency);

        String appClassPath = Stream.concat(tuples.stream().map(Tuple::first), appPathsInDependency.stream())
                .collect(Collectors.joining(File.pathSeparator));
//        List<String> classes = DirectoryTraverser.listClasses(appClassPath);
//        CryptoAPIMisuseAnalysis.addAppClass(Sets.newSet(classes));
        boolean onlyApp = false;
        String cs = "1-call";

        List<String> args = new ArrayList<>();
        Collections.addAll(args, "-java", "8");
        Collections.addAll(args, "-ap");
        //Collections.addAll(args, "--pre-build-ir");
        Collections.addAll(args, "--output-dir", "output/" + benchmark.name);
        if (withDependency) {
            Collections.addAll(args, "-cp", String.join(File.pathSeparator, dependencyPaths));
        }
        Collections.addAll(args, "-acp", appClassPath);
        Collections.addAll(args, "--world-builder", "pascal.taie.frontend.newfrontend.AsmWorldBuilder");
        // Collections.addAll(args, "--input-classes", String.join(",", appClasses));
        Collections.addAll(args,
                // "-a", "ir-dumper",
                "-a", """
                        pta=
                        implicit-entries:false;
                        dump:false;
                        only-app:%s;
                        handle-invokedynamic:true;
                        merge-string-builders:true;
                        cs:%s;
                        advanced:hashmap;
                        crypto-output:%s;
                        propagate-types:[reference,byte,char,int];
                        crypto-config:src/test/resources/pta/cryptomisuse/crypto-config.yml;
                        plugins:[pascal.taie.analysis.pta.plugin.cryptomisuse.reachableplugin.CryptoReachablePlugin,
                                 pascal.taie.analysis.pta.plugin.Profiler];
                        """.formatted(onlyApp, cs, "crypto-output/" + benchmark.name + ".json"),
                "-a", """
                        cg=
                        algorithm:pta;
                        dump-methods:true;
                        dump-call-edges:true;
                        """
        );
        Main.main(args.toArray(new String[0]));
    }

    public static void testPTAInOWASPProgramOfCrypto(Benchmark benchmark, boolean withDependency) {
        if (!Path.of(benchmark.dir).toFile().isDirectory()) {
            logger.error("Directory not exists: {}", benchmark.dir);
            return;
        }

        List<String> cryptoArchives1;
        List<String> cryptoArchives2;
        List<String> cryptoArchives = new ArrayList<>();
        try {
            cryptoArchives1 = Files.list(Path.of(benchmark.dir))
                    .filter(path -> path.toString().endsWith(".jar")
                            || path.toString().endsWith(".war"))
                    .map(Path::toAbsolutePath)
                    .map(Path::toString)
                    .toList();
            cryptoArchives2 = Files.list(Path.of(benchmark.dir + "/dependencies"))
                    .filter(path -> path.toString().endsWith(".jar")
                            || path.toString().endsWith(".war"))
                    .map(Path::toAbsolutePath)
                    .map(Path::toString)
                    .toList();
            cryptoArchives.addAll(cryptoArchives1);
            cryptoArchives.addAll(cryptoArchives2);
            logger.info("{} jar detected: {}", cryptoArchives.size(), cryptoArchives);
        } catch (IOException e) {
            logger.error("", e);
            throw new RuntimeException(e);
        }

        var tuple = initializeSpringBootArchivesOfCrypto(cryptoArchives);
        // iterator tuples indexly
        List<String> dependencyPaths = new ArrayList<>();

        // de-duplication the dependency jar and add it into dependencyPaths
        List<Tuple<String, List<String>, List<String>>> tuples = new ArrayList<>();
        tuples.add(tuple);
        tuples.stream()
                .map(Tuple::third)
                .flatMap(Collection::stream)
                .map(File::new)
                .collect(Collectors.toMap(File::getName, Function.identity(), (o1, o2) -> o1))
                .values()
                .stream()
                .map(File::getAbsolutePath)
                .forEach(dependencyPaths::add);

        Collection<String> appPathsInDependency = AppClassInferringUtils.inferAppJarPaths(
                tuples.stream().map(Tuple::second).flatMap(Collection::stream).toList(),
                dependencyPaths);
        dependencyPaths.removeAll(appPathsInDependency);

        String appClassPath = Stream.concat(tuples.stream().map(Tuple::first), appPathsInDependency.stream())
                .collect(Collectors.joining(File.pathSeparator));

        boolean onlyApp = false;
        String cs = "1-call";

        List<String> args = new ArrayList<>();
        Collections.addAll(args, "-java", "8");
        Collections.addAll(args, "-ap");
        Collections.addAll(args, "--output-dir", "output/" + benchmark.name);
        if (withDependency) {
            Collections.addAll(args, "-cp", String.join(File.pathSeparator, dependencyPaths));
        }
        Collections.addAll(args, "-acp", appClassPath);
        Collections.addAll(args, "--world-builder", "pascal.taie.frontend.newfrontend.AsmWorldBuilder");
        // Collections.addAll(args, "--input-classes", String.join(",", appClasses));
        Collections.addAll(args,
                // "-a", "ir-dumper",
                "-a", """
                        pta=
                        implicit-entries:false;
                        dump:false;
                        only-app:%s;
                        handle-invokedynamic:true;
                        merge-string-builders:true;
                        cs:%s;
                        crypto-output:%s;
                        propagate-types:[reference,byte,char,int];
                        crypto-config:src/test/resources/pta/cryptomisuse/crypto-config.yml;
                        reflection-log:src/test/resources/pta/cryptomisuse/reflection-OWASP.log;
                        plugins:[pascal.taie.analysis.pta.plugin.owasp.OWASPBenchmarkAnalysis,
                                 pascal.taie.analysis.pta.plugin.Profiler];
                        """.formatted(onlyApp, cs, "crypto-output/apachebench/" + benchmark.name + ".json"),
                "-a", """
                        cg=
                        algorithm:pta;
                        dump-methods:true;
                        dump-call-edges:true;
                        """
        );

        Main.main(args.toArray(new String[0]));
    }

    /**
     * @param dir  the directory containing the test case
     * @param main main class of the test case
     * @param id   analysis ID
     * @return the expected file for given test case and analysis.
     */
    private static String getExpectedFile(String dir, String main, String id) {
        String fileName = String.format("%s-%s-expected.txt", main, id);
        return Path.of(dir, fileName).toString();
    }

    public static void testPTA(String dir, String main, String... opts) {
        testPTA(true, dir, main, opts);
    }

    private static String getPTAArgs(
            boolean processResult, String expectedFile, String... opts) {
        List<String> ptaArgs = new ArrayList<>(List.of(
                "implicit-entries:false",
                "only-app:true",
                "distinguish-string-constants:all"));
        if (processResult) {
            ptaArgs.add(GENERATE_EXPECTED_RESULTS
                    ? "dump:true"
                    : "expected-file:" + expectedFile);
        }
        List<String> plugins = new ArrayList<>();
        plugins.add(AssertionChecker.class.getName());
        for (String opt : opts) {
            if (opt.startsWith("plugins")) {
                // "plugins:[...]"
                String pluginStr = opt.substring(opt.indexOf('[') + 1, opt.indexOf(']'));
                plugins.addAll(Arrays.asList(pluginStr.split(",")));
            } else {
                ptaArgs.add(opt);
            }
        }
        ptaArgs.add("plugins:[" + String.join(",", plugins) + "]");
        return String.join(";", ptaArgs);
    }

    private static Tuple<String, List<String>, List<String>>
    initializeSpringBootArchivesOfCrypto(List<String> archivePaths) {
        Tuple<String, List<String>, List<String>> result;

        List<Path> tempDirectories = new ArrayList<>(archivePaths.size());
        String classPath = "";
        List<String> classes = new ArrayList<>();
        List<String> dependencyJarPaths = new ArrayList<>();
        for (String archivePath : archivePaths) {
            System.out.println("archivePath:" + archivePath);
            try {
                // uncompress archive file at temp directory
                // get classpath, classes
                if (archivePath.contains("original-classes.jar") || archivePath.contains("test.jar")) {
                    Path tempDirectory = Files.createTempDirectory(
                            Path.of(archivePath).toFile().getName()
                                    .replace(".jar", "-")
                                    .replace(".war", "-")
                    );
                    ZipUtils.uncompressZipFile(archivePath, tempDirectory.toString());
                    tempDirectories.add(tempDirectory);
                    classPath = tempDirectory.toString();
                    System.out.println(classPath);
                    classes = DirectoryTraverser.listClasses(classPath);
                    CryptoAPIMisuseAnalysis.addAppClass(Sets.newSet(classes));
                    ResourceRetrieverModel.setClasspath(tempDirectory);
                } else {
                    dependencyJarPaths.add(archivePath);
                }
            } catch (IOException e) {
                logger.error("", e);
                throw new RuntimeException(e);
            }
        }
        // delete temp directories
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
                tempDirectories.stream().map(Path::toFile).forEach(FileUtils::deleteQuietly)));
        result = new Tuple<>(classPath, classes, dependencyJarPaths);
        return result;
    }

    private static List<Tuple<String, List<String>, List<String>>>
    initializeSpringBootArchives(List<String> archivePaths) {
        List<Tuple<String, List<String>, List<String>>> result = new ArrayList<>();

        List<Path> tempDirectories = new ArrayList<>(archivePaths.size());
        for (String archivePath : archivePaths) {
            try {
                // uncompress archive file at temp directory
                Path tempDirectory = Files.createTempDirectory(
                        Path.of(archivePath).toFile().getName()
                                .replace(".jar", "-")
                                .replace(".war", "-")
                );
                ZipUtils.uncompressZipFile(archivePath, tempDirectory.toString());
                tempDirectories.add(tempDirectory);
                // get classpath, classes, and dependencyJarPaths
                Path inf = tempDirectory.resolve("BOOT-INF");
                if (!inf.toFile().exists()) {
                    inf = tempDirectory.resolve("WEB-INF");
                }
                String classPath = inf.resolve("classes").toString();
                List<String> classes = DirectoryTraverser.listClasses(classPath);
                Path dependencyDir = inf.resolve("lib");
                List<String> dependencyJarPaths = Files.list(dependencyDir).map(Path::toString).toList();
                result.add(new Tuple<>(classPath, classes, dependencyJarPaths));
            } catch (IOException e) {
                logger.error("", e);
                throw new RuntimeException(e);
            }
        }
        // delete temp directories
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
                tempDirectories.stream().map(Path::toFile).forEach(FileUtils::deleteQuietly)));

        return result;
    }
}
