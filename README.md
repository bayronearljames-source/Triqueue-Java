# TrikeQueue

TrikeQueue is a Java Swing application for managing terminal drivers, queues, trips, fares, boundary payments, and daily earnings.

## Source layout

- `src/triqueue/model` — drivers, vehicles, trips, and fare details
- `src/triqueue/core` — application state, dispatcher, and queue workflow
- `src/triqueue/storage` — file persistence and data-directory handling
- `src/triqueue/ui` — Swing window, panels, and UI helpers
- `src/triqueue/Main.java` — console entry point

## Requirements

- Java JDK 17 or later
- Windows for the included batch launchers

## Run with your own records

From the project folder, run:

```powershell
.\run.bat
```

This uses the `data` folder.

## Run with fictional sample records

From the project folder, run:

```powershell
.\run-sample.bat
```

This uses `sample-data` and leaves the `data` folder untouched. Changes made while using the sample launcher are saved in `sample-data`.
