/**
 *  LogReportService
 *  Copyright 2026 by contributors to the YaCy project
 *  First released 26.06.2026 at https://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.ai;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import net.yacy.ai.LLM.LLMModel;
import net.yacy.ai.LLM.LLMUsage;
import net.yacy.cora.util.ConcurrentLog;
import net.yacy.cora.util.LogRedaction;
import net.yacy.kelondro.logging.GuiHandler;
import net.yacy.search.Switchboard;

public class LogReportService {

    public static final String CONFIG_REPORT_DIR = "ai.logreport.dir";
    public static final String CONFIG_ENABLED = "ai.logreport.enabled";
    public static final String CONFIG_MAX_BUCKET_LINES = "ai.logreport.max_bucket_lines";
    public static final String CONFIG_INITIAL_DELAY_MINUTES = "ai.logreport.initial_delay_minutes";
    public static final String CONFIG_PERIOD_MINUTES = "ai.logreport.period_minutes";
    public static final String CONFIG_DAILY_COMPRESSION_ENABLED = "ai.logreport.daily_compression.enabled";
    public static final String CONFIG_FEED_MAX_ENTRIES = "ai.logreport.feed.max_entries";
    public static final String DEFAULT_REPORT_DIR = "DATA/REPORTS/log";
    public static final int DEFAULT_MAX_BUCKET_LINES = 100000;
    public static final int DEFAULT_FEED_MAX_ENTRIES = 100;
    public static final long DEFAULT_INITIAL_DELAY_MINUTES = 5L;
    public static final long DEFAULT_PERIOD_MINUTES = 60L;

    private static final ConcurrentLog log = new ConcurrentLog("LOGREPORT");
    private static final DateTimeFormatter LOG_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private static final DateTimeFormatter HOURLY_REPORT_FORMATTER = DateTimeFormatter.ofPattern("'report-'yyyy-MM-dd-HH'.md'");
    private static final DateTimeFormatter DAILY_REPORT_FORMATTER = DateTimeFormatter.ofPattern("'report-'yyyy-MM-dd'.md'");
    private static final ZoneId REPORT_ZONE = ZoneId.systemDefault();
    private static final String SYSTEM_PROMPT =
            "You evaluate YaCy runtime logs for operator-facing self-enhancement reports. " +
            "Do not suggest automatic code changes. Identify operational evidence and practical development opportunities.";
    private static final int NOISE_EXAMPLE_LIMIT = 3;

    private final Switchboard sb;

    public static class ReportEntry implements Comparable<ReportEntry> {
        public final String filename;
        public final String title;
        public final ZonedDateTime published;
        public final String content;
        public final boolean daily;

        public ReportEntry(final String filename, final String title, final ZonedDateTime published, final String content, final boolean daily) {
            this.filename = filename;
            this.title = title;
            this.published = published;
            this.content = content;
            this.daily = daily;
        }

        @Override
        public int compareTo(final ReportEntry other) {
            final int dateCompare = other.published.compareTo(this.published);
            if (dateCompare != 0) return dateCompare;
            return other.filename.compareTo(this.filename);
        }
    }

    private static class NoiseBucket {
        int count;
        final List<String> examples = new ArrayList<>(NOISE_EXAMPLE_LIMIT);
    }

    private static class NoiseSummary {
        final int totalLines;
        final Map<String, NoiseBucket> buckets = new LinkedHashMap<>();

        NoiseSummary(final int totalLines) {
            this.totalLines = totalLines;
        }

        int classifiedLines() {
            int count = 0;
            for (final NoiseBucket bucket : this.buckets.values()) count += bucket.count;
            return count;
        }
    }

    public LogReportService(final Switchboard sb) {
        this.sb = sb;
    }

    public static boolean hasConfiguredLogReportModel() {
        return LLM.llmFromUsageQuiet(LLMUsage.logreport) != null;
    }

    /*
     * Manual "run report now" job, triggered from /LogReports_p.html. The report
     * computation is a long-running LLM call which would exceed the browser/servlet
     * timeout when executed synchronously, therefore the servlet only starts this
     * job and returns; the front-end polls the page and reads the state below until
     * the job is finished. The computation itself is the same
     * generateCurrentHourReportOverwrite() that the scheduler environment uses.
     */

    private static final AtomicBoolean MANUAL_JOB_RUNNING = new AtomicBoolean(false);
    /** partially generated report text, filled live from the model output stream */
    private static final StringBuilder MANUAL_JOB_PARTIAL = new StringBuilder();
    private static volatile long manualJobStart = 0L;
    /** outcome of the last finished job, using the runReportResult template cases of
     *  LogReports_p.html: 0=none, 1=success, 2=no log lines, 3=no model, 4=failure */
    private static volatile int manualJobOutcome = 0;
    private static volatile String manualJobMessage = "";
    private static volatile long manualJobDurationSeconds = 0L;

    /** Snapshot of a finished manual report job for one-time display. */
    public static final class ManualJobResult {
        public final int outcome;
        public final String message; // report filename on success, error message on failure
        public final long durationSeconds;
        private ManualJobResult(final int outcome, final String message, final long durationSeconds) {
            this.outcome = outcome;
            this.message = message;
            this.durationSeconds = durationSeconds;
        }
    }

    /**
     * Start the manual report computation in a background thread.
     * @return true if a new job was started, false if one is already running
     */
    public boolean startManualReportJob() {
        if (!MANUAL_JOB_RUNNING.compareAndSet(false, true)) return false;
        manualJobStart = System.currentTimeMillis();
        manualJobOutcome = 0;
        synchronized (MANUAL_JOB_PARTIAL) {
            MANUAL_JOB_PARTIAL.setLength(0);
        }
        final Thread worker = new Thread(() -> {
            final long start = System.currentTimeMillis();
            try {
                final File reportFile = generateCurrentHourReportOverwrite(delta -> {
                    synchronized (MANUAL_JOB_PARTIAL) {
                        MANUAL_JOB_PARTIAL.append(delta);
                    }
                });
                if (reportFile == null) {
                    manualJobMessage = "";
                    manualJobOutcome = hasConfiguredLogReportModel() ? 2 : 3;
                } else {
                    manualJobMessage = reportFile.getName();
                    manualJobOutcome = 1;
                }
            } catch (final Exception e) {
                manualJobMessage = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                manualJobOutcome = 4;
            } finally {
                manualJobDurationSeconds = Math.max(0L, (System.currentTimeMillis() - start) / 1000L);
                MANUAL_JOB_RUNNING.set(false);
            }
        }, "LogReportService.manualReport");
        worker.setDaemon(true);
        worker.start();
        return true;
    }

    public static boolean isManualReportJobRunning() {
        return MANUAL_JOB_RUNNING.get();
    }

    public static long manualReportJobElapsedSeconds() {
        return MANUAL_JOB_RUNNING.get() ? Math.max(0L, (System.currentTimeMillis() - manualJobStart) / 1000L) : 0L;
    }

    /** @return the report text generated so far by the running manual job */
    public static String manualReportJobPartialReport() {
        synchronized (MANUAL_JOB_PARTIAL) {
            return MANUAL_JOB_PARTIAL.toString();
        }
    }

    /**
     * Fetch and clear the outcome of the last finished manual report job, so the
     * result message is displayed exactly once on the next page render.
     * @return the result snapshot, or null when no job finished since the last call
     */
    public static ManualJobResult consumeManualReportJobResult() {
        if (MANUAL_JOB_RUNNING.get() || manualJobOutcome == 0) return null;
        final ManualJobResult result = new ManualJobResult(manualJobOutcome, manualJobMessage, manualJobDurationSeconds);
        manualJobOutcome = 0;
        return result;
    }

    public static ScheduledExecutorService startScheduler(final Switchboard sb) {
        if (sb == null) return null;
        if (!sb.getConfigBool(CONFIG_ENABLED, true)) {
            log.info("Log report scheduler is disabled by " + CONFIG_ENABLED + ".");
            return null;
        }
        // Note: the scheduler is started even when no logreport model is configured
        // yet. The model assignment can happen at any time on /LLMSelection_p.html
        // and must not require a restart; the tick below skips (with a single log
        // line) as long as the model is missing and picks up work automatically once
        // it is configured. Do not add an early return here for a missing model -
        // that would silently disable report generation until the next restart.

        final LogReportService service = new LogReportService(sb);
        final long initialDelay = Math.max(0L, sb.getConfigLong(CONFIG_INITIAL_DELAY_MINUTES, DEFAULT_INITIAL_DELAY_MINUTES));
        final long period = Math.max(1L, sb.getConfigLong(CONFIG_PERIOD_MINUTES, DEFAULT_PERIOD_MINUTES));
        final AtomicBoolean missingModelLogged = new AtomicBoolean(false);
        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            final Thread thread = new Thread(r, "LogReportService.scheduler");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(() -> {
            final String runId = newRunId();
            final long tickStart = System.currentTimeMillis();
            try {
                if (!hasConfiguredLogReportModel()) {
                    if (missingModelLogged.compareAndSet(false, true)) {
                        log.info("runId=" + runId + " event=scheduler-tick phase=skip reason=no-logreport-model");
                    }
                    return;
                }
                missingModelLogged.set(false);
                log.info("runId=" + runId + " event=scheduler-tick phase=start periodMinutes=" + period);
                final List<File> hourlyReports = service.generateHourlyReports(runId, "scheduler");
                final List<File> dailyReports = service.generateDailyReports(runId, "scheduler");
                log.info("runId=" + runId + " event=scheduler-tick phase=end result=success hourlyWritten=" + hourlyReports.size() + " dailyWritten=" + dailyReports.size() + " durationMs=" + elapsed(tickStart));
            } catch (final Throwable e) {
                log.warn("runId=" + runId + " event=scheduler-tick phase=end result=failure errorClass=" + e.getClass().getName() + " reason=" + LogRedaction.redactMessage(e) + " durationMs=" + elapsed(tickStart));
            }
        }, initialDelay, period, TimeUnit.MINUTES);
        log.info("event=scheduler-start result=success initialDelayMinutes=" + initialDelay + " periodMinutes=" + period);
        return scheduler;
    }

    public static void stopScheduler(final ScheduledExecutorService scheduler) {
        if (scheduler == null) return;
        scheduler.shutdownNow();
        log.info("Log report scheduler stopped.");
    }

    public File getReportDirectory() {
        return this.sb.getDataPath(CONFIG_REPORT_DIR, DEFAULT_REPORT_DIR);
    }

    public List<ReportEntry> discoverReports(final int maxEntries) {
        final File reportDirectory = getReportDirectory();
        if (!reportDirectory.isDirectory()) {
            log.info("No log reports discovered because report directory does not exist: " + reportDirectory.getAbsolutePath());
            return Collections.emptyList();
        }
        final File[] files = reportDirectory.listFiles();
        if (files == null || files.length == 0) {
            log.info("No log reports discovered in " + reportDirectory.getAbsolutePath() + ".");
            return Collections.emptyList();
        }

        final List<ReportEntry> reports = new ArrayList<>();
        int candidates = 0;
        for (final File file : files) {
            if (!file.isFile()) continue;
            candidates++;
            final ReportEntry report = reportEntry(file);
            if (report != null) reports.add(report);
        }
        Collections.sort(reports);
        log.info("Discovered " + reports.size() + " log report(s) from " + candidates + " file(s) in " + reportDirectory.getAbsolutePath() + ".");
        if (maxEntries >= 0 && reports.size() > maxEntries) {
            return new ArrayList<>(reports.subList(0, maxEntries));
        }
        return reports;
    }

    public List<File> generateHourlyReports() {
        return generateHourlyReports(newRunId(), "direct");
    }

    private List<File> generateHourlyReports(final String runId, final String trigger) {
        final long start = System.currentTimeMillis();
        log.info("runId=" + runId + " event=hourly-reports phase=start trigger=" + trigger);
        final LLMModel model = LLM.llmFromUsage(LLMUsage.logreport, runId, "hourly-reports");
        if (model == null) {
            log.info("runId=" + runId + " event=hourly-reports phase=skip reason=no-logreport-model durationMs=" + elapsed(start));
            return Collections.emptyList();
        }

        final List<String> logLines = readRuntimeLogLines();
        final Map<LocalDateTime, List<String>> buckets = completedHourlyBuckets(logLines);
        log.info("runId=" + runId + " event=hourly-reports phase=bucket-scan runtimeLines=" + logLines.size() + " buckets=" + buckets.size());
        if (buckets.isEmpty()) {
            log.info("runId=" + runId + " event=hourly-reports phase=skip reason=no-completed-buckets durationMs=" + elapsed(start));
            return Collections.emptyList();
        }

        final File reportDirectory = getReportDirectory();
        if (!reportDirectory.exists() && !reportDirectory.mkdirs()) {
            log.warn("runId=" + runId + " event=hourly-reports phase=prepare-directory result=failure path=" + reportDirectory.getAbsolutePath());
            return Collections.emptyList();
        }

        final List<File> writtenReports = new ArrayList<>();
        int skippedExisting = 0;
        int failedReports = 0;
        for (final Map.Entry<LocalDateTime, List<String>> bucket : buckets.entrySet()) {
            final File reportFile = new File(reportDirectory, hourlyReportFilename(bucket.getKey()));
            if (reportFile.exists()) {
                skippedExisting++;
                log.info("runId=" + runId + " event=hourly-report phase=skip bucket=" + bucket.getKey() + " reason=report-exists file=" + reportFile.getAbsolutePath());
                continue;
            }
            try {
                final NoiseSummary noiseSummary = classifyNoise(bucket.getValue());
                // output cap follows the model's configured max_tokens (Production Models Matrix)
                final int maxTokens = Math.max(1, model.llm.max_tokens);
                final String prompt = hourlyPrompt(bucket.getKey(), bucket.getValue(), noiseSummary, promptPayloadCharBudget(model, maxTokens));
                log.info("runId=" + runId + " event=hourly-report phase=classify-noise bucket=" + bucket.getKey() + " inputLines=" + bucket.getValue().size() + " noiseLines=" + noiseSummary.classifiedLines() + " noiseCategories=" + noiseSummary.buckets.size());
                log.info("runId=" + runId + " event=hourly-report phase=model-call bucket=" + bucket.getKey() + " model=" + LogRedaction.redact(model.model) + " backend=" + LogRedaction.redact(model.llm.hoststub) + " inputLines=" + bucket.getValue().size() + " promptChars=" + prompt.length() + " maxTokens=" + maxTokens);
                final long modelStart = System.currentTimeMillis();
                final String report = model.llm.chatStream(model.model, SYSTEM_PROMPT, prompt, maxTokens, null);
                final long modelDuration = elapsed(modelStart);
                log.info("runId=" + runId + " event=hourly-report phase=model-return bucket=" + bucket.getKey() + " durationMs=" + modelDuration + " outputChars=" + (report == null ? 0 : report.length()));
                if (report == null || report.trim().isEmpty()) {
                    throw new IOException("model returned an empty report");
                }
                writeReport(reportFile, reportDocument(bucket.getKey(), bucket.getValue().size(), report));
                writtenReports.add(reportFile);
                log.info("runId=" + runId + " event=hourly-report phase=write result=success bucket=" + bucket.getKey() + " inputLines=" + bucket.getValue().size() + " outputChars=" + report.length() + " file=" + reportFile.getAbsolutePath());
            } catch (final IOException e) {
                failedReports++;
                log.warn("runId=" + runId + " event=hourly-report phase=write result=failure bucket=" + bucket.getKey() + " file=" + LogRedaction.redact(reportFile.getAbsolutePath()) + " errorClass=" + e.getClass().getName() + " reason=" + LogRedaction.redactMessage(e));
            }
        }
        log.info("runId=" + runId + " event=hourly-reports phase=end result=success buckets=" + buckets.size() + " written=" + writtenReports.size() + " skippedExisting=" + skippedExisting + " failed=" + failedReports + " durationMs=" + elapsed(start));
        return writtenReports;
    }

    public File generateCurrentHourReportOverwrite() throws IOException {
        return generateCurrentHourReportOverwrite(null);
    }

    /**
     * Generate the current-hour report, streaming the model output into the given
     * consumer while the generation is running (used for the live view of the
     * manual "run report now" job).
     */
    public File generateCurrentHourReportOverwrite(final java.util.function.Consumer<String> onDelta) throws IOException {
        final String runId = newRunId();
        final long start = System.currentTimeMillis();
        log.info("runId=" + runId + " event=current-hour-report phase=start mode=manual");
        final LLMModel model = LLM.llmFromUsage(LLMUsage.logreport, runId, "current-hour-report");
        if (model == null) {
            log.info("runId=" + runId + " event=current-hour-report phase=skip reason=no-logreport-model durationMs=" + elapsed(start));
            return null;
        }

        final LocalDateTime currentHour = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        final List<String> logLines = readRuntimeLogLines();
        final List<String> bucketLines = hourlyBucket(logLines, currentHour);
        log.info("runId=" + runId + " event=current-hour-report phase=bucket-scan bucket=" + currentHour + " runtimeLines=" + logLines.size() + " inputLines=" + bucketLines.size());
        if (bucketLines.isEmpty()) {
            log.info("runId=" + runId + " event=current-hour-report phase=skip bucket=" + currentHour + " reason=no-matching-log-lines durationMs=" + elapsed(start));
            return null;
        }

        final File reportDirectory = getReportDirectory();
        if (!reportDirectory.exists() && !reportDirectory.mkdirs()) {
            log.warn("runId=" + runId + " event=current-hour-report phase=prepare-directory result=failure path=" + reportDirectory.getAbsolutePath());
            throw new IOException("cannot create log report directory " + reportDirectory.getAbsolutePath());
        }

        final File reportFile = new File(reportDirectory, hourlyReportFilename(currentHour));
        final NoiseSummary noiseSummary = classifyNoise(bucketLines);
        // output cap follows the model's configured max_tokens (Production Models Matrix)
        final int maxTokens = Math.max(1, model.llm.max_tokens);
        final String prompt = hourlyPrompt(currentHour, bucketLines, noiseSummary, promptPayloadCharBudget(model, maxTokens));
        log.info("runId=" + runId + " event=current-hour-report phase=classify-noise bucket=" + currentHour + " inputLines=" + bucketLines.size() + " noiseLines=" + noiseSummary.classifiedLines() + " noiseCategories=" + noiseSummary.buckets.size());
        log.info("runId=" + runId + " event=current-hour-report phase=model-call bucket=" + currentHour + " model=" + LogRedaction.redact(model.model) + " backend=" + LogRedaction.redact(model.llm.hoststub) + " inputLines=" + bucketLines.size() + " promptChars=" + prompt.length() + " maxTokens=" + maxTokens);
        final long modelStart = System.currentTimeMillis();
        final String report = model.llm.chatStream(model.model, SYSTEM_PROMPT, prompt, maxTokens, onDelta);
        log.info("runId=" + runId + " event=current-hour-report phase=model-return bucket=" + currentHour + " durationMs=" + elapsed(modelStart) + " outputChars=" + (report == null ? 0 : report.length()));
        if (report == null || report.trim().isEmpty()) {
            log.warn("runId=" + runId + " event=current-hour-report phase=model-return result=failure bucket=" + currentHour + " reason=empty-report");
            throw new IOException("model returned an empty report");
        }
        if (reportFile.exists()) {
            log.info("runId=" + runId + " event=current-hour-report phase=write action=overwrite file=" + reportFile.getAbsolutePath());
        }
        writeReport(reportFile, reportDocument(currentHour, bucketLines.size(), report), true);
        log.info("runId=" + runId + " event=current-hour-report phase=end result=success bucket=" + currentHour + " inputLines=" + bucketLines.size() + " outputChars=" + report.length() + " file=" + reportFile.getAbsolutePath() + " durationMs=" + elapsed(start));
        return reportFile;
    }

    public List<File> generateDailyReports() {
        return generateDailyReports(newRunId(), "direct");
    }

    private List<File> generateDailyReports(final String runId, final String trigger) {
        final long start = System.currentTimeMillis();
        log.info("runId=" + runId + " event=daily-reports phase=start trigger=" + trigger);
        if (!this.sb.getConfigBool(CONFIG_DAILY_COMPRESSION_ENABLED, true)) {
            log.info("runId=" + runId + " event=daily-reports phase=skip reason=daily-compression-disabled durationMs=" + elapsed(start));
            return Collections.emptyList();
        }
        final LLMModel model = LLM.llmFromUsage(LLMUsage.logreport, runId, "daily-reports");
        if (model == null) {
            log.info("runId=" + runId + " event=daily-reports phase=skip reason=no-logreport-model durationMs=" + elapsed(start));
            return Collections.emptyList();
        }

        final File reportDirectory = getReportDirectory();
        if (!reportDirectory.isDirectory()) {
            log.info("runId=" + runId + " event=daily-reports phase=skip reason=report-directory-missing path=" + reportDirectory.getAbsolutePath() + " durationMs=" + elapsed(start));
            return Collections.emptyList();
        }

        final Map<LocalDate, List<File>> pastDays = pastDayHourlyReportSets(reportDirectory);
        log.info("runId=" + runId + " event=daily-reports phase=scan pastDays=" + pastDays.size() + " path=" + reportDirectory.getAbsolutePath());
        if (pastDays.isEmpty()) {
            log.info("runId=" + runId + " event=daily-reports phase=skip reason=no-past-day-hourly-reports durationMs=" + elapsed(start));
            return Collections.emptyList();
        }

        final List<File> writtenReports = new ArrayList<>();
        int skippedExisting = 0;
        int failedReports = 0;
        for (final Map.Entry<LocalDate, List<File>> day : pastDays.entrySet()) {
            final File dailyReportFile = new File(reportDirectory, dailyReportFilename(day.getKey()));
            if (dailyReportFile.exists()) {
                // the day was already compressed earlier; only clean up leftover hourly
                // reports (e.g. written by a manual "run report now" after the compression)
                skippedExisting++;
                deleteHourlyReports(day.getValue());
                log.info("runId=" + runId + " event=daily-report phase=skip day=" + day.getKey() + " reason=report-exists deletedLeftoverHourly=" + day.getValue().size() + " file=" + dailyReportFile.getAbsolutePath());
                continue;
            }
            try {
                // output cap follows the model's configured max_tokens (Production Models Matrix)
                final int maxTokens = Math.max(1, model.llm.max_tokens);
                final String prompt = dailyPrompt(day.getKey(), day.getValue(), promptPayloadCharBudget(model, maxTokens));
                log.info("runId=" + runId + " event=daily-report phase=model-call day=" + day.getKey() + " model=" + LogRedaction.redact(model.model) + " backend=" + LogRedaction.redact(model.llm.hoststub) + " sourceReports=" + day.getValue().size() + " promptChars=" + prompt.length() + " maxTokens=" + maxTokens);
                final long modelStart = System.currentTimeMillis();
                final String report = model.llm.chatStream(model.model, SYSTEM_PROMPT, prompt, maxTokens, null);
                log.info("runId=" + runId + " event=daily-report phase=model-return day=" + day.getKey() + " durationMs=" + elapsed(modelStart) + " outputChars=" + (report == null ? 0 : report.length()));
                if (report == null || report.trim().isEmpty()) {
                    throw new IOException("model returned an empty daily report");
                }
                writeReport(dailyReportFile, dailyReportDocument(day.getKey(), day.getValue().size(), report));
                writtenReports.add(dailyReportFile);
                log.info("runId=" + runId + " event=daily-report phase=write result=success day=" + day.getKey() + " sourceReports=" + day.getValue().size() + " outputChars=" + report.length() + " file=" + dailyReportFile.getAbsolutePath());
                // the hourly reports are now bundled into the daily report and must be deleted
                deleteHourlyReports(day.getValue());
                log.info("runId=" + runId + " event=daily-report phase=delete-hourly day=" + day.getKey() + " sourceReports=" + day.getValue().size());
            } catch (final IOException e) {
                failedReports++;
                log.warn("runId=" + runId + " event=daily-report phase=write result=failure day=" + day.getKey() + " file=" + LogRedaction.redact(dailyReportFile.getAbsolutePath()) + " errorClass=" + e.getClass().getName() + " reason=" + LogRedaction.redactMessage(e));
            }
        }
        log.info("runId=" + runId + " event=daily-reports phase=end result=success pastDays=" + pastDays.size() + " written=" + writtenReports.size() + " skippedExisting=" + skippedExisting + " failed=" + failedReports + " durationMs=" + elapsed(start));
        return writtenReports;
    }

    public Map<LocalDateTime, List<String>> completedHourlyBuckets(final List<String> logLines) {
        final int maxBucketLines = this.sb.getConfigInt(CONFIG_MAX_BUCKET_LINES, DEFAULT_MAX_BUCKET_LINES);
        final LocalDateTime currentHour = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        final Map<LocalDateTime, List<String>> buckets = new TreeMap<>();

        for (final String line : logLines) {
            final LocalDateTime timestamp = parseLogTimestamp(line);
            if (timestamp == null) continue;
            final LocalDateTime bucket = timestamp.truncatedTo(ChronoUnit.HOURS);
            if (!bucket.isBefore(currentHour)) continue;
            List<String> bucketLines = buckets.get(bucket);
            if (bucketLines == null) {
                bucketLines = new ArrayList<>();
                buckets.put(bucket, bucketLines);
            }
            bucketLines.add(line);
            while (bucketLines.size() > maxBucketLines) {
                bucketLines.remove(0);
            }
        }
        return buckets;
    }

    private List<String> hourlyBucket(final List<String> logLines, final LocalDateTime selectedHour) {
        final int maxBucketLines = this.sb.getConfigInt(CONFIG_MAX_BUCKET_LINES, DEFAULT_MAX_BUCKET_LINES);
        final List<String> bucketLines = new ArrayList<>();
        for (final String line : logLines) {
            final LocalDateTime timestamp = parseLogTimestamp(line);
            if (timestamp == null || !selectedHour.equals(timestamp.truncatedTo(ChronoUnit.HOURS))) continue;
            bucketLines.add(line);
            while (bucketLines.size() > maxBucketLines) {
                bucketLines.remove(0);
            }
        }
        return bucketLines;
    }

    public List<String> readRuntimeLogLines() {
        final GuiHandler handler = findGuiHandler();
        if (handler == null) return Collections.emptyList();
        final String[] lines = handler.getLogLines(false, handler.getSize());
        final List<String> result = new ArrayList<>(lines.length);
        for (final String line : lines) {
            if (line == null) continue;
            final String trimmed = line.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }

    public static String hourlyReportFilename(final LocalDateTime bucket) {
        return HOURLY_REPORT_FORMATTER.format(bucket);
    }

    public static String dailyReportFilename(final LocalDate day) {
        return DAILY_REPORT_FORMATTER.format(day);
    }

    static LocalDateTime parseLogTimestamp(final String line) {
        if (line == null || line.length() < 21) return null;
        try {
            return LocalDateTime.parse(line.substring(2, 21), LOG_TIMESTAMP_FORMATTER);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    private static GuiHandler findGuiHandler() {
        final Logger rootLogger = Logger.getLogger("");
        final Handler[] handlers = rootLogger.getHandlers();
        for (final Handler handler : handlers) {
            if (handler instanceof GuiHandler) return (GuiHandler) handler;
        }
        return null;
    }

    private static String newRunId() {
        return UUID.randomUUID().toString();
    }

    private static long elapsed(final long start) {
        return System.currentTimeMillis() - start;
    }

    private static String hourlyPrompt(final LocalDateTime bucket, final List<String> lines, final NoiseSummary noiseSummary, final int maxPayloadChars) {
        final StringBuilder prompt = new StringBuilder(1024 + lines.size() * 120);
        prompt.append("Create a YaCy self-enhancement log report for hour ")
                .append(bucket)
                .append(".\n\n")
                .append("Noise classification:\n")
                .append(noiseSummaryText(noiseSummary))
                .append("\nGuidance: repeated peer availability, remote search miss, and logreport self-observation noise should be summarized as operational background unless it correlates with user-visible failure, long latency, or a repeated code exception.\n\n")
                .append("Use these fixed sections:\n")
                .append("1. Summary / key takeaway\n")
                .append("2. Usage types\n")
                .append("3. Challenges\n")
                .append("4. Errors and risks\n")
                .append("5. Possible improvements\n")
                .append("6. Concrete actions\n\n")
                .append("Each concrete action must identify the log trigger and expected effect. ")
                .append("Do not include secrets, credentials, or raw user content beyond what already appears in the log metadata.\n\n")
                .append("Log lines (noise lines are omitted here, they are already summarized in the noise classification above):\n");
        // Only non-noise lines go into the prompt: the classified noise lines are
        // already represented by the category counts and examples above. Sending
        // them again as raw text would blow the prompt up to hundreds of kilobytes,
        // and the model-side prompt processing time grows with every token - this
        // was the reason why a report generation took many minutes.
        final StringBuilder linesText = new StringBuilder(lines.size() * 120);
        int omittedNoise = 0;
        for (final String line : lines) {
            if (noiseCategory(line) != null) {
                omittedNoise++;
                continue;
            }
            linesText.append(line).append('\n');
        }
        if (omittedNoise > 0) {
            prompt.append("(").append(omittedNoise).append(" noise lines omitted)\n");
        }
        appendWithPromptBudget(prompt, linesText, maxPayloadChars);
        return prompt.toString();
    }

    /** rough characters-per-token ratio used to budget the prompt against the model's token window */
    private static final int CHARS_PER_TOKEN = 4;
    /** never shrink the prompt payload below this many tokens, even for tiny model windows */
    private static final int MIN_PROMPT_PAYLOAD_TOKENS = 512;

    /**
     * Character budget for the variable part of a report prompt (log lines or hourly
     * reports), derived from the service's context window (num_ctx). A local model must
     * ingest the entire prompt before it emits a single output token, and the prompt and
     * the generated report share one context window: prompt + output has to fit into
     * num_ctx. The payload is therefore sized to (num_ctx - output reserve) tokens,
     * converted to characters.
     * <p>
     * A fixed budget (previously 64k chars ≈ 16k tokens) overflows small windows: with a
     * default 4k-token window the ~16k-token hourly prompt filled the whole window, left no
     * room to generate, and the report stopped after one or two tokens. When the output cap
     * already fills the window the payload falls back to a quarter of the window so a report
     * is still produced; if the output then hits its cap it is truncated and logged
     * (finish_reason=length) instead of the prompt silently overflowing. Raise the service's
     * num_ctx (on /LLMSelection_p.html, matching the backend's OLLAMA_CONTEXT_LENGTH) to give
     * the prompt more room.
     */
    private static int promptPayloadCharBudget(final LLMModel model, final int maxTokens) {
        final int contextTokens = Math.max(1, model.llm.num_ctx);
        final int promptTokens = Math.max(
                Math.max(MIN_PROMPT_PAYLOAD_TOKENS, contextTokens / 4),
                contextTokens - maxTokens);
        return promptTokens * CHARS_PER_TOKEN;
    }

    /**
     * Append the payload to the prompt, truncated to maxPayloadChars. When truncating,
     * the most recent part (the tail) is kept because the newest log lines are the most
     * relevant ones for the report.
     */
    private static void appendWithPromptBudget(final StringBuilder prompt, final StringBuilder payload, final int maxPayloadChars) {
        if (payload.length() <= maxPayloadChars) {
            prompt.append(payload);
            return;
        }
        int cut = payload.length() - maxPayloadChars;
        final int lineStart = payload.indexOf("\n", cut);
        if (lineStart >= 0) cut = lineStart + 1; // do not start with a partial line
        prompt.append("(older content truncated to fit the prompt budget)\n")
                .append(payload, cut, payload.length());
    }

    private static NoiseSummary classifyNoise(final List<String> lines) {
        final NoiseSummary summary = new NoiseSummary(lines == null ? 0 : lines.size());
        if (lines == null || lines.isEmpty()) return summary;
        for (final String line : lines) {
            final String category = noiseCategory(line);
            if (category == null) continue;
            NoiseBucket bucket = summary.buckets.get(category);
            if (bucket == null) {
                bucket = new NoiseBucket();
                summary.buckets.put(category, bucket);
            }
            bucket.count++;
            if (bucket.examples.size() < NOISE_EXAMPLE_LIMIT) {
                bucket.examples.add(LogRedaction.redact(line));
            }
        }
        return summary;
    }

    private static String noiseCategory(final String line) {
        if (line == null || line.isEmpty()) return null;
        final String normalized = line.toLowerCase();
        if (normalized.contains("network") && normalized.contains("publish: disconnected")) {
            return "peer-publish-disconnected";
        }
        if (normalized.contains("remote search - no answer from remote peer")) {
            return "remote-search-no-answer";
        }
        if (normalized.contains("remote search - interrupted search to remote peer")) {
            return "remote-search-interrupted";
        }
        if (normalized.contains("transfer to peer") && normalized.contains("failed")) {
            return "peer-transfer-failed";
        }
        if (normalized.contains("found not enough") && normalized.contains("peers for distribution")) {
            return "dht-not-enough-peers";
        }
        if (normalized.contains("logreport") && (normalized.contains("discovered ") || normalized.contains("no log reports discovered"))) {
            return "logreport-self-observation";
        }
        if (normalized.contains("event=model-routing phase=select") || normalized.contains("event=model-routing phase=miss")) {
            return "llm-routing-observation";
        }
        return null;
    }

    private static String noiseSummaryText(final NoiseSummary summary) {
        if (summary == null || summary.buckets.isEmpty()) {
            return "- Classified noise lines: 0 / " + (summary == null ? 0 : summary.totalLines) + "\n";
        }
        final StringBuilder text = new StringBuilder(512);
        text.append("- Classified noise lines: ")
                .append(summary.classifiedLines())
                .append(" / ")
                .append(summary.totalLines)
                .append('\n');
        for (final Map.Entry<String, NoiseBucket> entry : summary.buckets.entrySet()) {
            text.append("- ")
                    .append(entry.getKey())
                    .append(": ")
                    .append(entry.getValue().count)
                    .append('\n');
            for (final String example : entry.getValue().examples) {
                text.append("  example: ").append(example).append('\n');
            }
        }
        return text.toString();
    }

    /**
     * Group all hourly reports of past days (strictly before today) by day, ordered
     * chronologically within each day. Every past day that still has hourly reports is
     * a compression candidate, no matter how many hours were actually reported - the
     * peer may have been offline for parts of the day. Only the current day is excluded
     * because its hourly reports are still accumulating.
     */
    private static Map<LocalDate, List<File>> pastDayHourlyReportSets(final File reportDirectory) {
        final File[] files = reportDirectory.listFiles();
        if (files == null || files.length == 0) return Collections.emptyMap();

        final LocalDate today = LocalDate.now();
        final Map<LocalDate, List<File>> candidates = new TreeMap<>();
        for (final File file : files) {
            if (!file.isFile()) continue;
            final LocalDateTime hour = parseHourlyReportFilename(file.getName());
            if (hour == null) continue;
            final LocalDate day = hour.toLocalDate();
            if (!day.isBefore(today)) continue;
            List<File> dayFiles = candidates.get(day);
            if (dayFiles == null) {
                dayFiles = new ArrayList<>();
                candidates.put(day, dayFiles);
            }
            dayFiles.add(file);
        }
        // the hourly filename pattern sorts chronologically
        for (final List<File> dayFiles : candidates.values()) {
            dayFiles.sort((a, b) -> a.getName().compareTo(b.getName()));
        }
        return candidates;
    }

    private static LocalDateTime parseHourlyReportFilename(final String filename) {
        if (filename == null || !filename.matches("report-\\d{4}-\\d{2}-\\d{2}-\\d{2}\\.md")) return null;
        try {
            return LocalDateTime.parse(filename, HOURLY_REPORT_FORMATTER);
        } catch (final RuntimeException e) {
            log.warn("Could not parse hourly log report filename " + filename + ": " + e.getMessage());
            return null;
        }
    }

    private static LocalDate parseDailyReportFilename(final String filename) {
        if (filename == null || !filename.matches("report-\\d{4}-\\d{2}-\\d{2}\\.md")) return null;
        try {
            return LocalDate.parse(filename, DAILY_REPORT_FORMATTER);
        } catch (final RuntimeException e) {
            log.warn("Could not parse daily log report filename " + filename + ": " + e.getMessage());
            return null;
        }
    }

    private static ReportEntry reportEntry(final File file) {
        final String filename = file.getName();
        try {
            final LocalDate daily = parseDailyReportFilename(filename);
            if (daily != null) {
                final ZonedDateTime published = daily.atTime(LocalTime.MAX).atZone(REPORT_ZONE);
                return new ReportEntry(filename, "YaCy daily log report " + daily, published, readFile(file), true);
            }
            final LocalDateTime hourly = parseHourlyReportFilename(filename);
            if (hourly != null) {
                final ZonedDateTime published = hourly.atZone(REPORT_ZONE);
                return new ReportEntry(filename, "YaCy hourly log report " + hourly, published, readFile(file), false);
            }
        } catch (final IOException e) {
            log.warn("Could not read log report " + file.getAbsolutePath() + ": " + e.getMessage());
        }
        log.info("Ignoring file in log report directory because it does not match a report filename: " + file.getAbsolutePath());
        return null;
    }

    private static String readFile(final File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static String dailyPrompt(final LocalDate day, final List<File> hourlyReports, final int maxPayloadChars) throws IOException {
        final StringBuilder prompt = new StringBuilder(4096);
        prompt.append("Create one consolidated YaCy self-enhancement report for ")
                .append(day)
                .append(" from the following hourly log reports.\n\n")
                .append("Use these fixed sections:\n")
                .append("1. Summary / key takeaway\n")
                .append("2. Usage types\n")
                .append("3. Challenges\n")
                .append("4. Errors and risks\n")
                .append("5. Possible improvements\n")
                .append("6. Concrete actions\n\n")
                .append("Find common patterns across the day. Each concrete action must identify the repeated log trigger and expected effect.\n\n");
        final StringBuilder reportsText = new StringBuilder(hourlyReports.size() * 4096);
        for (final File hourlyReport : hourlyReports) {
            reportsText.append("\n\n## ").append(hourlyReport.getName()).append("\n\n")
                    .append(new String(Files.readAllBytes(hourlyReport.toPath()), StandardCharsets.UTF_8));
        }
        appendWithPromptBudget(prompt, reportsText, maxPayloadChars);
        return prompt.toString();
    }

    private static String reportDocument(final LocalDateTime bucket, final int lineCount, final String report) {
        final StringBuilder document = new StringBuilder(report.length() + 256);
        document.append("# YaCy Log Report\n\n")
                .append("- Bucket: ").append(bucket).append('\n')
                .append("- Lines: ").append(lineCount).append('\n')
                .append("- Generated: ").append(LocalDateTime.now()).append("\n\n")
                .append(report.trim())
                .append('\n');
        return document.toString();
    }

    private static String dailyReportDocument(final LocalDate day, final int hourlyReportCount, final String report) {
        final StringBuilder document = new StringBuilder(report.length() + 256);
        document.append("# YaCy Daily Log Report\n\n")
                .append("- Day: ").append(day).append('\n')
                .append("- Hourly reports: ").append(hourlyReportCount).append('\n')
                .append("- Generated: ").append(LocalDateTime.now()).append("\n\n")
                .append(report.trim())
                .append('\n');
        return document.toString();
    }

    private static void deleteHourlyReports(final List<File> hourlyReports) {
        for (final File hourlyReport : hourlyReports) {
            if (!hourlyReport.delete()) {
                log.warn("Could not delete hourly log report after daily compression: " + hourlyReport.getAbsolutePath());
            }
        }
    }

    private static void writeReport(final File reportFile, final String report) throws IOException {
        writeReport(reportFile, report, false);
    }

    private static void writeReport(final File reportFile, final String report, final boolean replaceExisting) throws IOException {
        final File parent = reportFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("cannot create directory " + parent.getAbsolutePath());
        }
        final File tmpFile = File.createTempFile(reportFile.getName() + ".", ".tmp", parent);
        Files.write(tmpFile.toPath(), report.getBytes(StandardCharsets.UTF_8));
        try {
            if (replaceExisting) {
                Files.move(tmpFile.toPath(), reportFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(tmpFile.toPath(), reportFile.toPath(), StandardCopyOption.ATOMIC_MOVE);
            }
        } catch (final AtomicMoveNotSupportedException e) {
            if (replaceExisting) {
                Files.move(tmpFile.toPath(), reportFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(tmpFile.toPath(), reportFile.toPath());
            }
        }
    }
}
