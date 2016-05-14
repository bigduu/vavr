/*     / \____  _    _  ____   ______  / \ ____  __    _______
 *    /  /    \/ \  / \/    \ /  /\__\/  //    \/  \  //  /\__\   JΛVΛSLΛNG
 *  _/  /  /\  \  \/  /  /\  \\__\\  \  //  /\  \ /\\/ \ /__\ \   Copyright 2014-2016 Javaslang, http://javaslang.io
 * /___/\_/  \_/\____/\_/  \_/\__\/__/\__\_/  \_//  \__/\_____/   Licensed under the Apache License, Version 2.0
 */
package javaslang.benchmark;

import javaslang.Tuple2;
import javaslang.collection.Array;
import javaslang.collection.CharSeq;
import javaslang.collection.List;
import javaslang.collection.Map;
import javaslang.collection.TreeMap;
import javaslang.control.Option;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.results.BenchmarkResult;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.RunResult;

import java.text.DecimalFormat;
import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Collectors;

public class BenchmarkPerformanceReporter {
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,##0.000");
    private static final DecimalFormat PERFORMANCE_FORMAT = new DecimalFormat("#0.00");
    private static final DecimalFormat PCT_FORMAT = new DecimalFormat("0.00%");
    private final Collection<RunResult> runResults;
    private final String targetImplementation;

    public static BenchmarkPerformanceReporter of(Collection<RunResult> runResults) {
        return of(runResults, "slang");
    }

    public static BenchmarkPerformanceReporter of(Collection<RunResult> runResults, String targetImplementation) {
        return new BenchmarkPerformanceReporter(runResults, targetImplementation);
    }

    /**
     * This class prints performance reports about the execution of individual tests, comparing their performance
     * against other implementations as required.
     * <br>
     * A typical JMH test is configured as follows:
     * <pre>
     *    \@Benchmark
     *    \@Group("groupName")
     *    public Object testName_Slang() {
     *       ....
     *    }
     * </pre>
     * <br>
     * The method name is broken into two parts separated by an underscore:
     * <ul>
     *     <li>testName - the test name</li>
     *     <li>Implementation - the type of implementation</li>
     * </ul>
     * This class relies on this naming convention to identify different implementations of the same operation.
     *
     * @param runResults
     */
    private BenchmarkPerformanceReporter(Collection<RunResult> runResults, String targetImplementation) {
        this.runResults = runResults;
        this.targetImplementation = targetImplementation;
    }

    /**
     * Prints the detail performance report for each individual test.
     * <br>
     * For each test it prints out:
     * <ul>
     *     <li>Group</li>
     *     <li>Test Name</li>
     *     <li>Implementation - tests can have different implementations, e.g. Scala, Java, JavaSlang</li>
     *     <li>Parameters</li>
     *     <li>Score</li>
     *     <li>Error - 99% confidence interval expressed in % of the Score</li>
     *     <li>Unit - units for the Score</li>
     *     <li>Alternative implementations - compares performance of this test against alternative implementations</li>
     * </ul>
     *
     */
    public void printDetailedPerformanceReport() {
        final List<TestExecution> results = mapToTestExecutions(runResults);
        if (results.isEmpty()) {
            return;
        }
        printDetailedPerformanceExecutionReport(results);
    }

    /**
     * Prints the performance ratio report for each test, and compares the performance against different implementations
     * of the same operation.
     * <br>
     * For each test it prints out:
     * <ul>
     *     <li>Group</li>
     *     <li>Test Name</li>
     *     <li>Ratio - A/B means implementation A is compared against base implementation B</li>
     *     <li>Results - How many times faster implementation A is compared with B</li>
     * </ul>
     */
    public void printPerformanceRatiosReport() {
        final List<TestExecution> results = mapToTestExecutions(runResults);
        if (results.isEmpty()) {
            return;
        }
        printPerformanceRatioReport(results);
    }

    private List<TestExecution> mapToTestExecutions(Collection<RunResult> runResults) {
        List<TestExecution> executions = List.empty();
        for (RunResult runResult : runResults) {
            executions = executions.push(TestExecution.of(runResult.getAggregatedResult()));
        }
        return executions;
    }

    private void printDetailedPerformanceExecutionReport(List<TestExecution> results) {
        final Map<String, List<TestExecution>> resultsByKey = results.groupBy(TestExecution::getTestNameParamKey);
        final int paramKeySize = Math.max(results.map(r -> r.getParamKey().length()).max().get(), 10);
        final int groupSize = Math.max(results.map(r -> r.getTarget().length()).max().get(), 10);
        final int nameSize = Math.max(results.map(r -> r.getOperation().length()).max().get(), 10);
        final int implSize = Math.max(results.map(r -> r.getImplementation().length()).max().get(), 10);
        final int scoreSize = Math.max(results.map(r -> r.getScoreFormatted().length()).max().get(), 15);
        final int errorSize = Math.max(results.map(r -> r.getScoreErrorPct().length()).max().get(), 10);
        final int unitSize = Math.max(results.map(r -> r.getUnit().length()).max().get(), 7);

        final List<String> alternativeImplementations = results.map(TestExecution::getImplementation).distinct().sorted();
        final int alternativeImplSize = Math.max(alternativeImplementations.map(String::length).max().get(), 10);
        final String alternativeImplHeader = alternativeImplementations.map(type -> padRight(type, alternativeImplSize)).mkString(" ");

        final String header = String.format("%s  %s  %s  %s  %s  ±%s %s %s",
                padLeft("Target", groupSize),
                padLeft("Operation", nameSize),
                padLeft("Impl", implSize),
                padRight("Params", paramKeySize),
                padRight("Score", scoreSize),
                padRight("Error", errorSize),
                padRight("Unit", unitSize),
                alternativeImplHeader
        );

        System.out.println("\n\n\n");
        System.out.println("Detailed Performance Execution Report");
        System.out.println(CharSeq.of("=").repeat(header.length()));
        System.out.println("  (Error: ±99% confidence interval, expressed as % of Score)");
        if (!alternativeImplementations.isEmpty()) {
            System.out.println(String.format("  (%s: read as current row implementation is x times faster than alternative implementation)", alternativeImplementations.mkString(", ")));
        }
        System.out.println();
        System.out.println(header);
        for (TestExecution result : results) {
            System.out.println(String.format("%s  %s  %s  %s  %s  ±%s %s %s",
                    padLeft(result.getTarget(), groupSize),
                    padLeft(result.getOperation(), nameSize),
                    padLeft(result.getImplementation(), implSize),
                    padRight(result.getParamKey(), paramKeySize),
                    padRight(result.getScoreFormatted(), scoreSize),
                    padRight(result.getScoreErrorPct(), errorSize),
                    padRight(result.getUnit(), unitSize),
                    calculatePerformanceStr(result, alternativeImplementations, resultsByKey, alternativeImplSize)
                    ));
        }
        System.out.println("\n");
    }

    private String calculatePerformanceStr(TestExecution result, List<String> alternativeImplementations, Map<String, List<TestExecution>> resultsByKey, int alternativeImplSize) {
        final String aggregateKey = result.getTestNameParamKey();
        final List<TestExecution> alternativeResults = resultsByKey.get(aggregateKey).getOrElse(List::empty);
        List<Option<TestExecution>> map = alternativeImplementations.map(alternativeType -> alternativeResults.find(r -> alternativeType.equals(r.getImplementation())));
        map.removeAt(0);
        return alternativeImplementations.map(alternativeType -> alternativeResults.find(r -> alternativeType.equals(r.getImplementation())))
                .map(alternativeExecution -> alternativeExecution.isDefined() ? alternativeExecution.get().getScore() : 0.0)
                .map(alternativeScore -> alternativeScore == 0 ? 1.0 : (result.getScore() / alternativeScore))
                .map(performanceImprovement -> padRight(performanceImprovement == 1.0 ? "" : PERFORMANCE_FORMAT.format(performanceImprovement) + "x", alternativeImplSize))
                .mkString(" ");
    }

    private void printPerformanceRatioReport(List<TestExecution> results) {
        final TreeMap<String, List<TestExecution>> resultsByKey = TreeMap.ofEntries(results.groupBy(TestExecution::getTestNameKey));
        final int groupSize = Math.max(results.map(r -> r.getTarget().length()).max().get(), 10);
        final int nameSize = Math.max(results.map(r -> r.getOperation().length()).max().get(), 10);

        final List<String> paramKeys = results.map(TestExecution::getParamKey).distinct().sorted();
        final int paramKeySize = Math.max(results.map(r -> r.getParamKey().length()).max().get(), 10);
        final String paramKeyHeader = paramKeys.map(type -> padRight(type, paramKeySize)).mkString(" ");

        final List<String> alternativeImplementations = results.map(TestExecution::getImplementation).distinct().sorted();
        final int alternativeImplSize = Math.max(alternativeImplementations.map(String::length).max().get(), 10);
        final int ratioSize = Math.max(alternativeImplSize * 2 + 1, 10);

        final String header = String.format("%s  %s  %s  %s ",
                padLeft("Target", groupSize),
                padLeft("Operation", nameSize),
                padRight("Ratio", ratioSize),
                paramKeyHeader
        );
        System.out.println("\n\n");
        System.out.println("Performance Ratios");
        System.out.println(CharSeq.of("=").repeat(header.length()));
        if (alternativeImplementations.size() < 2) {
            System.out.println("(nothing to report, you need at least two different implementation)");
            return;
        }

        for (String baseImpl : alternativeImplementations) {
            System.out.println(String.format("\nRatios alternative_impl/%s", baseImpl));
            System.out.println(header);
            for (Tuple2<String, List<TestExecution>> execution : resultsByKey) {
                printRatioForBaseType(baseImpl, execution._2, alternativeImplementations, paramKeys, groupSize, nameSize, ratioSize, paramKeySize);
            }
        }
        System.out.println("\n");
    }

    private void printRatioForBaseType(String baseType, List<TestExecution> testExecutions, List<String> alternativeImplementations, List<String> paramKeys,
                                       int groupSize, int nameSize, int ratioSize, int paramKeySize) {
        List<TestExecution> baseImplExecutions = testExecutions.filter(e -> e.getImplementation().equals(baseType));
        if (baseImplExecutions.isEmpty()) {
            return;
        }
        TestExecution baseTypeExecution = baseImplExecutions.head();

        for (String alternativeImpl : alternativeImplementations) {
            if (alternativeImpl.equals(baseType)) {
                continue;
            }
            List<TestExecution> alternativeExecutions = testExecutions.filter(e -> e.getImplementation().equals(alternativeImpl));
            if (alternativeExecutions.isEmpty()) {
                continue;
            }
            System.out.println(String.format("%s  %s  %s  %s ",
                    padLeft(baseTypeExecution.getTarget(), groupSize),
                    padLeft(baseTypeExecution.getOperation(), nameSize),
                    padRight(String.format("%s/%s", alternativeImpl, baseType), ratioSize),
                    calculateRatios(baseImplExecutions, alternativeExecutions, paramKeys, paramKeySize)
                    ));
        }
    }

    private String calculateRatios(List<TestExecution> baseImplExecutions, List<TestExecution> alternativeExecutions, List<String> paramKeys, int paramKeySize) {
        Array<String> ratioStrs = Array.empty();
        for (String paramKey : paramKeys) {
            Option<TestExecution> alternativeExecution = alternativeExecutions.find(e -> e.getParamKey().equals(paramKey));
            Option<TestExecution> baseExecution = baseImplExecutions.find(e -> e.getParamKey().equals(paramKey));
            String paramRatio = alternativeExecution.isEmpty() || baseExecution.isEmpty() || baseExecution.get().getScore() == 0.0
                    ? ""
                    : PERFORMANCE_FORMAT.format(alternativeExecution.get().getScore() / baseExecution.get().getScore()) + "x";
            ratioStrs = ratioStrs.append(padRight(paramRatio, paramKeySize));
        }
        return ratioStrs.mkString(" ");
    }

    private String padLeft(String str, int size) {
        return str + CharSeq.repeat(' ', size - str.length());
    }

    private String padRight(String str, int size) {
        return CharSeq.repeat(' ', size - str.length()) + str;
    }

    static class TestExecution implements Comparable<TestExecution> {
        private final String paramKey;
        private final String fullName;
        private final String target;
        private final String operation;
        private final String implementation;
        private final double score;
        private final double scoreError;
        private final String unit;

        public static TestExecution of(BenchmarkResult benchmarkResult) {
            return new TestExecution(benchmarkResult, benchmarkResult.getPrimaryResult());
        }

        public TestExecution(BenchmarkResult benchmark, Result<?> result) {
            fullName = benchmark.getParams().getBenchmark();
            target = extractPart(fullName, 2);
            operation = extractPart(fullName, 1);
            implementation = extractPart(fullName, 0);

            paramKey = getParameterKey(benchmark);
            score = result.getScore();
            scoreError = result.getScoreError();
            unit = result.getScoreUnit();
        }

        private String getParameterKey(BenchmarkResult benchmarkResult) {
            BenchmarkParams params = benchmarkResult.getParams();
            return params.getParamsKeys().stream().map(params::getParam).collect(Collectors.joining(","));
        }

        public String getTestNameParamKey() {
            return target + ":" + operation + ":" + unit + ":" + paramKey;
        }

        public String getTestNameKey() {
            return target + ":" + operation + ":" + unit;
        }

        private String extractPart(String fullyQualifiedName, int indexFromLast) {
            final String[] parts = fullyQualifiedName.split("\\.");
            return parts.length > indexFromLast ? parts[parts.length - indexFromLast - 1] : "";
        }

        public String getParamKey() {
            return paramKey;
        }

        public String getTarget() {
            return target;
        }

        public String getOperation() {
            return operation;
        }

        public String getImplementation() {
            return implementation;
        }

        public double getScore() {
            return score;
        }

        public String getScoreFormatted() {
            return DECIMAL_FORMAT.format(score);
        }

        public String getScoreErrorPct() {
            return PCT_FORMAT.format(score == 0 ? 0 : scoreError / score);
        }

        public String getUnit() {
            return unit;
        }

        @Override
        public String toString() {
            return String.format("%s %s %s %s -> %s (± %s)", paramKey, target, operation, implementation, getScoreFormatted(), getScoreErrorPct());
        }

        Comparator<TestExecution> comparator = Comparator
                .comparing(TestExecution::getUnit)
                .thenComparing(TestExecution::getTarget)
                .thenComparing(TestExecution::getParamKey)
                .thenComparing(TestExecution::getOperation)
                .thenComparing(TestExecution::getImplementation);
        @Override
        public int compareTo(TestExecution o) {
            return comparator.compare(this, o);
        }
    }
}