export interface BatchExecution {
  orderId: string;
  stepSeq: number;
  processName: string;
  lineId: string;
  batchNumber: number;
  batchQuantity: number;
  plannedStart: string;
  plannedEnd: string;
  durationMinutes: number;
  isDelayed: boolean;
  delayReason: string | null;
  color: string;
  leftPct?: number;
  widthPct?: number;
}

export interface StepExecution {
  stepSeq: number;
  processName: string;
  lineId: string;
  plannedStart: string;
  plannedEnd: string;
  durationMinutes: number;
  isDelayed: boolean;
  delayReason: string | null;
  color: string;
  orderId?: string;
  leftPct?: number;
  widthPct?: number;
  batches?: BatchExecution[];
}

export interface Order {
  orderId: string;
  panelType: string;
  wattage: number;
  quantity: number;
  orderReceived: string;
  dueDate: string;
  priority: number;
  customSetup: boolean;
  estimatedCompletion: string;
  status: string;
  totalLeadTimeMinutes: number;
  steps: StepExecution[];
}

export interface ScheduleSummary {
  totalOrders: number;
  totalPanels: number;
  avgLeadTimeHours: number;
  allCompleteBy: string;
  linesUtilized: number;
}

export interface ScheduleResult {
  orders: Order[];
  generatedAt: string;
  aiInsights: string;
  summary: ScheduleSummary;
}

export interface DashboardData {
  ordersByStatus: { [key: string]: number };
  lineUtilisation: { [key: string]: number };
  delaySummary: { [key: string]: number };
  recentOrders: Order[];
  summary: ScheduleSummary;
}
