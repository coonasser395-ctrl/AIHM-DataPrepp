import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.sqrt

object DataPrep {

    data class Row(
        val userId: String,
        val date: String,
        val go2bed: String,
        val asleep: String,
        val wakeup: String,
        val wakeupNight: Double,
        var waso: Double,
        var sleepDuration: Double,
        var inBedDuration: Double,
        var sleepLatency: Double,
        val sleepEfficiency: Double,
        var valid: Boolean = true
    )

    @JvmStatic
    fun main(args: Array<String>) {
        val inputPath = args.getOrElse(0) { "data/sleep_diary.csv" }
        val outDir = args.getOrElse(1) { "output" }
        File(outDir).mkdirs()

        val report = StringBuilder()
        fun log(s: String) { println(s); report.appendLine(s) }

        log("=".repeat(60))
        log("AIHM - Data Preparation Report")
        log("Input : $inputPath")
        log("Output: $outDir/")
        log("=".repeat(60))

        val file = File(inputPath)
        require(file.exists()) { "Data file not found: $inputPath" }
        val lines = file.readLines().filter { it.isNotBlank() }
        require(lines.size > 1) { "File is empty or contains no data rows" }

        val sep = if (lines[0].contains("|")) "|" else ","
        val header = lines[0].split(sep).map { it.trim() }.filter { it.isNotEmpty() }
        log("\n[1] Loading data")
        log("  Separator: $sep")
        log("  Columns (${header.size}): ${header.joinToString()}")
        log("  Raw rows: ${lines.size - 1}")

        val rawRows = lines.drop(1).map { it.split(sep).map { c -> c.trim() } }

        log("\n[2] Exploratory Data Analysis (EDA)")

        log("  -- Missing values per column:")
        header.indices.forEach { col ->
            val c = rawRows.count { it.size <= col || it[col].isEmpty() || it[col].equals("nan", true) }
            log("     ${header[col]}: $c")
        }

        val dupCount = rawRows.size - rawRows.distinct().size
        log("  -- Duplicate rows: $dupCount")

        val users = rawRows.mapNotNull { it.getOrNull(0)?.takeIf { u -> u.isNotEmpty() } }.distinct()
        log("  -- Unique users: ${users.size}")
        val dates = rawRows.mapNotNull { it.getOrNull(1)?.takeIf { d -> d.isNotEmpty() } }.sorted()
        if (dates.isNotEmpty()) log("  -- Date range: ${dates.first()}  ->  ${dates.last()}")

        val numericCols = listOf("wakeup@night","waso","sleep_duration","in_bed_duration","sleep_latency","sleep_efficiency")
        log("  -- Descriptive statistics for numeric columns:")
        log("     ${"Column".padEnd(18)}${"min".padEnd(8)}${"Q1".padEnd(8)}${"median".padEnd(8)}${"mean".padEnd(8)}${"Q3".padEnd(8)}${"max".padEnd(8)}${"outliers".padEnd(8)}")
        val colStats = mutableMapOf<String, Stats>()
        for (colName in numericCols) {
            val idx = header.indexOf(colName)
            if (idx < 0) continue
            val vals = rawRows.mapNotNull { it.getOrNull(idx)?.toDoubleOrNull() }.sorted()
            if (vals.isEmpty()) continue
            val st = Stats(vals)
            colStats[colName] = st
            log("     ${colName.padEnd(18)}${fmt(st.min)}${fmt(st.q1)}${fmt(st.median)}${fmt(st.mean)}${fmt(st.q3)}${fmt(st.max)}${st.outliers.toString().padEnd(8)}")
        }

        log("\n[3] Cleaning")
        var cleaned = rawRows

        var before = cleaned.size
        cleaned = cleaned.distinct()
        log("  Drop duplicates: $before -> ${cleaned.size}  (removed ${before - cleaned.size})")

        before = cleaned.size
        cleaned = cleaned.filter { it.size >= header.size && it[0].isNotEmpty() && it[1].isNotEmpty() }
        log("  Drop rows with incomplete keys: $before -> ${cleaned.size}")

        val wasoIdx = header.indexOf("waso")
        val wasoMedian = colStats["waso"]?.median ?: 0.0
        var filledWaso = 0
        val rows = cleaned.map { cells ->
            val wasoVal = cells.getOrNull(wasoIdx)?.toDoubleOrNull()
            if (wasoVal == null) filledWaso++
            Row(
                userId = cells[0],
                date = cells[1],
                go2bed = cells.getOrElse(2) { "" },
                asleep = cells.getOrElse(3) { "" },
                wakeup = cells.getOrElse(4) { "" },
                wakeupNight = cells.getOrNull(5)?.toDoubleOrNull() ?: 0.0,
                waso = wasoVal ?: wasoMedian,
                sleepDuration = cells.getOrNull(7)?.toDoubleOrNull() ?: 0.0,
                inBedDuration = cells.getOrNull(8)?.toDoubleOrNull() ?: 0.0,
                sleepLatency = cells.getOrNull(9)?.toDoubleOrNull() ?: 0.0,
                sleepEfficiency = cells.getOrNull(10)?.toDoubleOrNull() ?: 0.0
            )
        }
        log("  Fill missing waso with median ($wasoMedian): $filledWaso rows")

        var invalid = 0
        rows.forEach { r ->
            if (r.sleepEfficiency <= 0 || r.sleepEfficiency > 1.0001 ||
                r.sleepDuration <= 0 || r.inBedDuration < r.sleepDuration) {
                r.valid = false; invalid++
            }
        }
        val validRows = rows.filter { it.valid }
        log("  Drop invalid rows (bad efficiency/duration): $invalid  ->  remaining ${validRows.size}")

        val capCols = listOf("waso", "sleep_duration", "sleep_latency")
        var capped = 0
        for (colName in capCols) {
            val st = colStats[colName] ?: continue
            val lower = st.q1 - 1.5 * st.iqr
            val upper = st.q3 + 1.5 * st.iqr
            validRows.forEach { r ->
                val v = when (colName) {
                    "waso" -> r.waso
                    "sleep_duration" -> r.sleepDuration
                    "sleep_latency" -> r.sleepLatency
                    else -> 0.0
                }
                if (v < lower || v > upper) {
                    capped++
                    when (colName) {
                        "waso" -> r.waso = v.coerceIn(lower, upper)
                        "sleep_duration" -> r.sleepDuration = v.coerceIn(lower, upper)
                        "sleep_latency" -> r.sleepLatency = v.coerceIn(lower, upper)
                    }
                }
            }
        }
        log("  Outliers detected and winsorized (1.5*IQR) in waso/sleep_duration/sleep_latency: $capped values")

        log("\n[4] Feature Engineering")
        val fmtOut = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        data class Feature(
            val userId: String, val date: String,
            val dayOfWeek: Int, val isWeekend: Int,
            val go2bedHour: Double, val asleepHour: Double, val wakeupHour: Double,
            val wakeupNight: Double, val waso: Double,
            val sleepDuration: Double, val inBedDuration: Double,
            val sleepLatency: Double, val sleepEfficiency: Double
        )
        val features = validRows.map { r ->
            val dow = try { LocalDate.parse(r.date, fmtOut).dayOfWeek.value } catch (e: Exception) { 0 }
            Feature(
                userId = r.userId, date = r.date,
                dayOfWeek = dow,
                isWeekend = if (dow in 6..7) 1 else 0,
                go2bedHour = parseHour(r.go2bed),
                asleepHour = parseHour(r.asleep),
                wakeupHour = parseHour(r.wakeup, afterMidnight = false),
                wakeupNight = r.wakeupNight, waso = r.waso,
                sleepDuration = r.sleepDuration, inBedDuration = r.inBedDuration,
                sleepLatency = r.sleepLatency, sleepEfficiency = r.sleepEfficiency
            )
        }
        log("  New features: dayOfWeek, isWeekend, go2bedHour, asleepHour, wakeupHour (HH:MM -> decimal hours)")
        log("  Final rows ready for modeling: ${features.size}")

        log("\n[5] Saving outputs to $outDir/")

        val cleanedFile = File(outDir, "cleaned_data.csv")
        cleanedFile.writeText(header.joinToString(",") + "\n")
        validRows.forEach { r ->
            cleanedFile.appendText(listOf(r.userId,r.date,r.go2bed,r.asleep,r.wakeup,
                r.wakeupNight,r.waso,r.sleepDuration,r.inBedDuration,r.sleepLatency,r.sleepEfficiency
            ).joinToString(",") { fmtAny(it) } + "\n")
        }
        log("  [OK] cleaned_data.csv  (${validRows.size} rows)")

        val featFile = File(outDir, "features_for_model.csv")
        featFile.writeText("userId,date,dayOfWeek,isWeekend,go2bedHour,asleepHour,wakeupHour,wakeupNight,waso,sleep_duration,in_bed_duration,sleep_latency,sleep_efficiency\n")
        features.forEach { f ->
            featFile.appendText(listOf(f.userId,f.date,f.dayOfWeek,f.isWeekend,f.go2bedHour,f.asleepHour,f.wakeupHour,
                f.wakeupNight,f.waso,f.sleepDuration,f.inBedDuration,f.sleepLatency,f.sleepEfficiency
            ).joinToString(",") { fmtAny(it) } + "\n")
        }
        log("  [OK] features_for_model.csv  (${features.size} rows x 13 cols)")

        File(outDir, "analysis_report.txt").writeText(report.toString())
        log("  [OK] analysis_report.txt")

        log("\n" + "=".repeat(60))
        log("Data preparation completed successfully. Outputs saved in: $outDir")
        log("Next week: use features_for_model.csv to train XGBoost / Random Forest / Isolation Forest")
        log("=".repeat(60))
    }

    private fun parseHour(t: String, afterMidnight: Boolean = true): Double {
        if (t.isBlank()) return 0.0
        val parts = t.split(":").mapNotNull { it.toIntOrNull() }
        if (parts.size < 2) return 0.0
        var h = parts[0] + parts[1] / 60.0 + (parts.getOrNull(2) ?: 0) / 3600.0
        if (afterMidnight && h < 12) h += 24.0
        return Math.round(h * 1000.0) / 1000.0
    }

    private fun fmtAny(x: Any): String = when (x) {
        is Double -> if (x == x.toLong().toDouble()) x.toLong().toString() else "%.4f".format(x)
        else -> x.toString()
    }
    private fun fmt(d: Double): String = "%.3f".format(d).padEnd(8)

    class Stats(vals: List<Double>) {
        val n = vals.size
        val min = vals.first()
        val max = vals.last()
        val mean = vals.sum() / n
        val median = percentile(vals, 50)
        val q1 = percentile(vals, 25)
        val q3 = percentile(vals, 75)
        val iqr = q3 - q1
        val std = sqrt(vals.sumOf { (it - mean) * (it - mean) } / n)
        val outliers = vals.count { it < q1 - 1.5 * iqr || it > q3 + 1.5 * iqr }
        private fun percentile(sorted: List<Double>, p: Int): Double {
            val idx = (p / 100.0 * (sorted.size - 1)).toInt()
            return sorted[idx.coerceIn(0, sorted.size - 1)]
        }
    }
}
