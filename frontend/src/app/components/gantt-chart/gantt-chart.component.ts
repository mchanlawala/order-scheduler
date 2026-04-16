import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { ScheduleService } from '../../services/schedule.service';
import { Order, ScheduleResult } from '../../models/order.model';

interface GanttBar {
  stepSeq: number;
  processName: string;
  lineId: string;
  start: Date;
  end: Date;
  durationMin: number;
  isDelayed: boolean;
  delayReason: string | null;
  color: string;
  leftPct: number;
  widthPct: number;
}

interface GanttOrder {
  order: Order;
  bars: GanttBar[];
  totalHours: number;
}

@Component({
  selector: 'app-gantt-chart',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatFormFieldModule,
    MatSelectModule       // includes MatOption
  ],
  templateUrl: './gantt-chart.component.html',
  styleUrls: ['./gantt-chart.component.css']
})
export class GanttChartComponent implements OnInit {
  isLoading = true;
  errorMsg = '';

  scheduleResult: ScheduleResult | null = null;
  allOrders: Order[] = [];
  selectedOrderId = '';

  // Single-order Gantt
  singleGantt: GanttBar[] = [];
  singleMinDate: Date = new Date();
  singleMaxDate: Date = new Date();
  singleTimeMarkers: { label: string; pct: number }[] = [];

  // All-orders Gantt
  allGantts: GanttOrder[] = [];
  allMinDate: Date = new Date();
  allMaxDate: Date = new Date();
  allTimeMarkers: { label: string; pct: number }[] = [];

  // Tooltip
  tooltip: {
    visible: boolean;
    x: number;
    y: number;
    name: string;
    line: string;
    start: string;
    end: string;
    duration: string;
    delayed: boolean;
    delayReason: string;
  } = { visible: false, x: 0, y: 0, name: '', line: '', start: '', end: '', duration: '', delayed: false, delayReason: '' };

  // Filter
  filterStatus = 'ALL';
  viewMode: 'single' | 'all' = 'all';

  constructor(private scheduleService: ScheduleService) {}

  ngOnInit() {
    this.loadSchedule();
  }

  loadSchedule() {
    this.isLoading = true;
    this.scheduleService.getResult().subscribe({
      next: (result) => {
        if (result && (result as any).message) {
          // No schedule yet
          this.isLoading = false;
          this.errorMsg = 'No schedule found. Please upload an Excel file or run the Demo first.';
          return;
        }
        this.scheduleResult = result;
        this.allOrders = result.orders ?? [];
        if (this.allOrders.length > 0) {
          this.selectedOrderId = this.allOrders[0].orderId;
        }
        this.buildAllGantts();
        this.buildSingleGantt();
        this.isLoading = false;
      },
      error: (err) => {
        this.errorMsg = err.message;
        this.isLoading = false;
      }
    });
  }

  get filteredOrders(): Order[] {
    if (this.filterStatus === 'ALL') return this.allOrders;
    return this.allOrders.filter(o => o.status === this.filterStatus);
  }

  onOrderSelect(orderId: string) {
    this.selectedOrderId = orderId;
    this.buildSingleGantt();
    this.viewMode = 'single';
  }

  // ---- Build single-order gantt ----
  buildSingleGantt() {
    const order = this.allOrders.find(o => o.orderId === this.selectedOrderId);
    if (!order?.steps?.length) { this.singleGantt = []; return; }

    const steps = order.steps;
    const minT = new Date(steps[0].plannedStart).getTime();
    const maxT = new Date(steps[steps.length - 1].plannedEnd).getTime();
    const span = maxT - minT || 1;

    this.singleMinDate = new Date(minT);
    this.singleMaxDate = new Date(maxT);
    this.singleTimeMarkers = this.buildMarkers(minT, maxT, 7);

    this.singleGantt = steps.map(s => {
      const start = new Date(s.plannedStart).getTime();
      const end = new Date(s.plannedEnd).getTime();
      return {
        stepSeq: s.stepSeq,
        processName: s.processName,
        lineId: s.lineId,
        start: new Date(s.plannedStart),
        end: new Date(s.plannedEnd),
        durationMin: s.durationMinutes,
        isDelayed: s.isDelayed,
        delayReason: s.delayReason,
        color: s.color || '#378ADD',
        leftPct: ((start - minT) / span) * 100,
        widthPct: Math.max(0.5, ((end - start) / span) * 100)
      };
    });
  }

  // ---- Build all-orders gantt ----
  buildAllGantts() {
    if (!this.allOrders.length) return;

    const allStarts = this.allOrders
      .filter(o => o.steps?.length)
      .map(o => new Date(o.steps[0].plannedStart).getTime());
    const allEnds = this.allOrders
      .filter(o => o.estimatedCompletion)
      .map(o => new Date(o.estimatedCompletion).getTime());

    const minT = Math.min(...allStarts);
    const maxT = Math.max(...allEnds);
    const span = maxT - minT || 1;

    this.allMinDate = new Date(minT);
    this.allMaxDate = new Date(maxT);
    this.allTimeMarkers = this.buildMarkers(minT, maxT, 6);

    const orderColors = ['#1976d2','#388e3c','#f57c00','#7b1fa2','#00796b','#c62828','#0288d1','#558b2f'];

    this.allGantts = this.allOrders.map((order, oi) => {
      const steps = order.steps ?? [];
      const bars: GanttBar[] = steps.map(s => {
        const start = new Date(s.plannedStart).getTime();
        const end = new Date(s.plannedEnd).getTime();
        return {
          stepSeq: s.stepSeq,
          processName: s.processName,
          lineId: s.lineId,
          start: new Date(s.plannedStart),
          end: new Date(s.plannedEnd),
          durationMin: s.durationMinutes,
          isDelayed: s.isDelayed,
          delayReason: s.delayReason,
          color: s.color || orderColors[oi % orderColors.length],
          leftPct: ((start - minT) / span) * 100,
          widthPct: Math.max(0.3, ((end - start) / span) * 100)
        };
      });
      return {
        order,
        bars,
        totalHours: order.totalLeadTimeMinutes ? Math.round(order.totalLeadTimeMinutes / 60) : 0
      };
    });
  }

  buildMarkers(minT: number, maxT: number, count: number): { label: string; pct: number }[] {
    const markers = [];
    for (let i = 0; i <= count; i++) {
      const t = new Date(minT + ((maxT - minT) * i / count));
      markers.push({ label: this.fmtMarker(t), pct: (i / count) * 100 });
    }
    return markers;
  }

  showTooltip(event: MouseEvent, bar: GanttBar) {
    this.tooltip = {
      visible: true,
      x: event.clientX + 14,
      y: event.clientY - 10,
      name: bar.processName,
      line: bar.lineId,
      start: this.fmtDateTime(bar.start),
      end: this.fmtDateTime(bar.end),
      duration: this.fmtDuration(bar.durationMin),
      delayed: bar.isDelayed,
      delayReason: bar.delayReason ?? ''
    };
  }

  hideTooltip() {
    this.tooltip.visible = false;
  }

  fmtDateTime(d: Date): string {
    return d.toLocaleString('en-GB', {
      day: '2-digit', month: 'short', year: 'numeric',
      hour: '2-digit', minute: '2-digit'
    });
  }

  fmtMarker(d: Date): string {
    return d.toLocaleString('en-GB', {
      day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit'
    });
  }

  fmtDuration(min: number): string {
    if (!min) return '—';
    const h = Math.floor(min / 60);
    const m = min % 60;
    return h > 0 ? `${h}h ${m > 0 ? m + 'm' : ''}`.trim() : `${m}m`;
  }

  fmtDate(s: string): string {
    if (!s) return '—';
    return new Date(s).toLocaleString('en-GB', {
      day: '2-digit', month: 'short', year: 'numeric',
      hour: '2-digit', minute: '2-digit'
    });
  }

  statusClass(status: string): string {
    const m: Record<string, string> = {
      SCHEDULED: 'chip-blue', COMPLETED: 'chip-green', DELAYED: 'chip-red', PENDING: 'chip-orange'
    };
    return m[status] ?? 'chip-grey';
  }

  priorityLabel(p: number): string {
    return p === 1 ? 'URGENT' : p === 2 ? 'HIGH' : p === 3 ? 'MEDIUM' : 'LOW';
  }

  priorityClass(p: number): string {
    return p <= 1 ? 'chip-red' : p === 2 ? 'chip-orange' : p === 3 ? 'chip-blue' : 'chip-grey';
  }

  get selectedOrder(): Order | undefined {
    return this.allOrders.find(o => o.orderId === this.selectedOrderId);
  }

  get uniqueStatuses(): string[] {
    const s = new Set(this.allOrders.map(o => o.status));
    return ['ALL', ...Array.from(s)];
  }
}
