# AIHM — Data Preparation

AIHM (AI Heart Rate Monitor) project — **data preparation phase only**:
Exploratory Data Analysis (EDA) + Cleaning + Feature Engineering.
Language: **Kotlin** — zero external dependencies (standard library only).

## What the program does

Input: `data/sleep_diary.csv` → Output (in `output/`):

| Output file | Description |
|---|---|
| `cleaned_data.csv` | Data after cleaning (dedup, missing-value fill, outlier winsorization) |
| `features_for_model.csv` | Features ready for next week's model (XGBoost / Random Forest / Isolation Forest) |
| `analysis_report.txt` | Full EDA + cleaning report |

Pipeline:
1. Load CSV with auto separator detection (`,` or `|`).
2. EDA: row/column counts, missing values, duplicates, unique users, date range, descriptive stats (min/Q1/median/mean/Q3/max/std) and IQR outlier counts per numeric column.
3. Cleaning: drop duplicates, drop incomplete key rows, fill missing `waso` with median, drop invalid rows (efficiency > 1 or negative duration), winsorize outliers (1.5×IQR).
4. Feature engineering: convert `go2bed/asleep/wakeup` from `HH:MM` text to decimal hours (with after-midnight handling), add `dayOfWeek` and `isWeekend`.

## Project structure

```
AIHM-DataPrep/
├── README.md
├── .gitignore
├── data/
│   └── sleep_diary.csv
├── src/
│   └── DataPrep.kt
├── run.sh
├── run.bat
└── output/            (created automatically when you run the program)
```

## Quick start (Kotlin toolchain only — no Java command used)

```bash
# 1. Install Kotlin (once) — provides both `kotlinc` and `kotlin`
curl -s https://get.sdkman.io | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install kotlin

# 2. Compile with kotlinc
kotlinc src/DataPrep.kt -include-runtime -d dataprep.jar

# 3. Run with kotlin
kotlin dataprep.jar
```

To run on a different data file:
```bash
kotlin dataprep.jar data/your_file.csv output
```

## Run on GitHub (Codespaces)

1. Create a GitHub account at https://github.com and create a new repository `AIHM-DataPrep`.
2. Upload all project files (drag & drop via "uploading an existing file").
3. Click **Code → Codespaces → Create codespace on main**.
4. In the terminal, run the three commands above (install Kotlin → compile with kotlinc → run with kotlin).
5. Results appear in the terminal and are saved in `output/`.

## Expected results on sleep_diary.csv

- Raw rows: **1372**, columns: **11**, unique users: **49**.
- Missing values: **6** (only in `waso`) — filled with median (0).
- Duplicate rows: **0**.
- Notable outliers: `waso` up to 360 min — winsorized.
- Final: **~1372** rows × 13 numeric columns in `features_for_model.csv`.

## Next week (out of scope for this week)
Use `features_for_model.csv` to train XGBoost / Random Forest (classification) and Isolation Forest (anomaly detection). No model training is included in this project by design.
