package pascal.taie.analysis.pta.cryptomisuse;

import static pascal.taie.analysis.pta.cryptomisuse.BenchmarkConfigs.APACHEAPI_BENCHMARKS_DIR;
import static pascal.taie.analysis.pta.cryptomisuse.BenchmarkConfigs.CRYPTO_BENCHMARKS_DIR;

public enum Benchmark {
    ACTIVEMQ("activemq", APACHEAPI_BENCHMARKS_DIR + "/activemq"),

    DELTA("deltaspike",APACHEAPI_BENCHMARKS_DIR + "/deltaspike"),

    DIRECTORY("directory-server",APACHEAPI_BENCHMARKS_DIR + "/directory-server"),

    INCU("incubator-taverna-workbench",APACHEAPI_BENCHMARKS_DIR + "/incubator-taverna-workbench"),

    MANIFOLD("manifoldcf",APACHEAPI_BENCHMARKS_DIR + "/manifoldcf"),

    MEECRO("meecrowave",APACHEAPI_BENCHMARKS_DIR + "/meecrowave"),

    SPARK("spark", APACHEAPI_BENCHMARKS_DIR + "/spark"),

    TIKA("tika",APACHEAPI_BENCHMARKS_DIR + "/tika"),

    TOMEE("tomee",APACHEAPI_BENCHMARKS_DIR + "/tomee"),

    WICKET("wicket", APACHEAPI_BENCHMARKS_DIR + "/wicket"),

    ALIYUN("aliyun-oss-java-sdk", CRYPTO_BENCHMARKS_DIR + "/aliyun-oss-java-sdk"),

    FASTBOOT("fast-boot-weixin", CRYPTO_BENCHMARKS_DIR + "/fast-boot-weixin"),

    SPRINGQUICK("spring-boot-quick", CRYPTO_BENCHMARKS_DIR + "/spring-boot-quick"),

    BICORE("biglybt-core", CRYPTO_BENCHMARKS_DIR + "/biglybt-core"),

    BT("bt", CRYPTO_BENCHMARKS_DIR + "/bt"),

    DUBBO("dubbo3", CRYPTO_BENCHMARKS_DIR + "/dubbo3"),

    MYBLOG("my-blog", CRYPTO_BENCHMARKS_DIR + "/my-blog"),

    HABRIDGE("ha-bridge", CRYPTO_BENCHMARKS_DIR + "/ha-bridge"),

    TELEGRAM("telegram-server", CRYPTO_BENCHMARKS_DIR + "/telegram-server"),

    IJPAY("ijpay", CRYPTO_BENCHMARKS_DIR + "/ijpay"),

    INSTAGRAM("instagram4j", CRYPTO_BENCHMARKS_DIR + "/instagram4j"),

    GAMESERVER("game-server", CRYPTO_BENCHMARKS_DIR + "/game-server"),

    MPUSH("mpush", CRYPTO_BENCHMARKS_DIR + "/mpush"),

    HSWEB("hsweb-framework", CRYPTO_BENCHMARKS_DIR + "/hsweb-framework"),

    PROTOOLS("protools", CRYPTO_BENCHMARKS_DIR + "/protools"),

    J360("j360-dubbo-app-all", CRYPTO_BENCHMARKS_DIR + "/j360-dubbo-app-all"),

    SPRINGSTUDENT("spring-boot-student", CRYPTO_BENCHMARKS_DIR + "/spring-boot-student"),

    SATURNAPI("saturn-console-api", CRYPTO_BENCHMARKS_DIR + "/saturn-console-api"),

    SATURNCORE("saturn-console-core", CRYPTO_BENCHMARKS_DIR + "/saturn-console-core"),

    SMART("smart", CRYPTO_BENCHMARKS_DIR + "/smart"),

    SYMMETRIC("symmetric-ds", CRYPTO_BENCHMARKS_DIR + "/symmetric-ds"),

    NETTYGAMESERVER("nettygameserver", CRYPTO_BENCHMARKS_DIR + "/nettygameserver"),

    ZHENG("zheng", CRYPTO_BENCHMARKS_DIR + "/zheng"),

    RIGORITYJ("rigorityj", CRYPTO_BENCHMARKS_DIR + "/rigorityj-samples"),

    OWASP("OWASP", CRYPTO_BENCHMARKS_DIR + "/OWASP"),

    CRYPTOTEST("crypto-test", CRYPTO_BENCHMARKS_DIR + "/crypto-test");

    public final String name;

    public final String dir;

    Benchmark(String name, String dir) {
        this.name = name;
        this.dir = dir;
    }
}
