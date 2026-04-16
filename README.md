# Solar Panel Manufacturing Order Scheduler

A full-stack manufacturing order scheduling system with:
- **Spring Boot** backend (Java 21)
- **LangChain4j** AI agent for scheduling insights
- **Angular 17** frontend with Material UI
- **Gantt chart** with per-order and all-orders views
- **Excel upload** for order data input

---

## Project Structure

```
order-scheduler/
├── backend/                   ← Spring Boot (Maven)
│   ├── pom.xml
│   └── src/main/java/com/scheduler/
│       ├── OrderSchedulerApplication.java
│       ├── agent/SchedulingAgent.java
│       ├── config/WebConfig.java
│       ├── controller/ScheduleController.java
│       ├── domain/             ← Order, StepExecution, Line, etc.
│       └── service/
│           ├── ExcelParserService.java
│           └── SchedulingEngine.java
├── frontend/                  ← Angular 17
│   ├── src/app/
│   │   ├── components/
│   │   │   ├── dashboard/
│   │   │   ├── gantt-chart/
│   │   │   └── upload/
│   │   ├── models/order.model.ts
│   │   └── services/schedule.service.ts
│   └── proxy.conf.json
└── sample_input_data.xlsx     ← Sample Excel with all 6 sheets
```

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 21+ |
| Maven | 3.9+ |
| Node.js | 18+ |
| Angular CLI | 17+ (`npm install -g @angular/cli`) |

---

## Quick Start

### 1. Start the Backend

```bash
cd order-scheduler/backend

# Optional: set your OpenAI key for real AI insights
# Without it, the system uses rule-based analysis (still fully functional)
export OPENAI_API_KEY=sk-your-key-here

mvn clean install -DskipTests
mvn spring-boot:run
```

Backend runs on **http://localhost:8080**

### 2. Start the Frontend

```bash
cd order-scheduler/frontend
npm install
npm start
```

Frontend runs on **http://localhost:4200**

---

## Usage

### Option A: Demo Mode (no file needed)
1. Go to **http://localhost:4200**
2. Click **"Run Demo Schedule"** on the Upload page
3. View the Gantt chart and Dashboard

### Option B: Upload Excel File
1. Open the included `sample_input_data.xlsx` to see the format
2. Go to the Upload page and drag/drop your `.xlsx` file
3. Click **"Upload & Schedule"**
4. Navigate to **Gantt Chart** or **Dashboard**

---

## API Endpoints

| Method | URL | Description |
|--------|-----|-------------|
| POST | `/api/schedule/upload` | Upload Excel file & schedule |
| POST | `/api/schedule/demo` | Run with built-in demo data |
| GET | `/api/schedule/result` | Get full schedule result |
| GET | `/api/schedule/orders` | Get all scheduled orders |
| GET | `/api/schedule/gantt/{orderId}` | Get steps for one order |
| GET | `/api/schedule/dashboard` | Get dashboard aggregates |
| GET | `/api/schedule/insights` | Get AI insights text |

---

## Excel Schema

### Orders (Required)
| Column | Type | Example |
|--------|------|---------|
| OrderID | Text | ORD-001 |
| PanelType | Text | Mono 400W |
| Wattage | Number | 400 |
| Quantity | Number | 500 |
| OrderReceived | DateTime | 2025-01-06 08:00 |
| DueDate | DateTime | 2025-01-20 17:00 |
| Priority | Number (1=highest) | 1 |
| CustomSetup | Boolean | false |

### ProcessSteps (Optional — defaults to 14 solar panel steps)
StepSeq, ProcessName, LineID, SetupTimeMin, ProcMinPerUnit, BatchSize, PrecedingStep, ReconfigPenaltyMin

### Lines (Optional — defaults to 9 production lines)
LineID, ParallelCapacity, ShiftHoursPerDay, DefaultStart (HH:mm), DefaultEnd (HH:mm)

### Shifts (Optional)
LineID, Date (YYYY-MM-DD), StartTime, EndTime

### Breaks (Optional)
LineID, BreakStart (HH:mm), BreakEnd (HH:mm), DailyOccurrence

### DowntimeEvents (Optional)
EventID, LineID, StartDateTime, EndDateTime, Reason, Type (Planned/Unplanned)

---

## Scheduling Algorithm

The engine uses a **priority-based sequential scheduler**:

1. Orders sorted by `Priority` ASC (1 = most urgent), then `OrderReceived`
2. For each order, each process step is scheduled sequentially
3. Step start = `max(previous step end, line next available slot)`
4. Working time respects:
   - Shift start/end times per line
   - Daily breaks (skipped, work continues after)
   - Shift overrides for specific dates
   - Downtime events (delay added to step end)
   - Custom setup penalty (added to setup time)
5. Line capacity tracked via a priority queue (one slot per parallel capacity unit)

---

## Default Production Lines

| Line | Shift | Capacity | Role |
|------|-------|----------|------|
| PLAN-LINE | 08:00–20:00 | 2 | Order planning |
| CELL-LINE | 06:00–22:00 | 2 | Silicon → Cell |
| QC-LINE | 08:00–20:00 | 3 | Quality checks |
| ASSY-LINE | 07:00–21:00 | 4 | Cell assembly |
| LAMI-LINE | 06:00–22:00 | 2 | Lamination |
| FRAME-LINE | 07:00–21:00 | 3 | Framing |
| FINAL-LINE | 08:00–20:00 | 2 | Junction box |
| TEST-LINE | 08:00–20:00 | 3 | Testing |
| PACK-LINE | 07:00–21:00 | 4 | Packaging |

---

## AI Integration (Optional)

Set `OPENAI_API_KEY` environment variable to enable 4-step AI agent:
1. **Order Analysis** — risk scoring, urgency assessment
2. **Capacity Check** — bottleneck identification
3. **Delay Prediction** — at-risk order flagging
4. **Optimisation** — actionable recommendations

Without the key, rule-based insights are provided automatically.

---

## Troubleshooting

**`Orders sheet not found`** — ensure your Excel has a sheet named exactly `Orders`

**Dates not parsed** — use format `YYYY-MM-DD HH:mm` or let Excel format dates natively

**CORS error** — ensure backend is on port 8080 and frontend proxy is configured

**`ng: command not found`** — run `npm install -g @angular/cli@17`
