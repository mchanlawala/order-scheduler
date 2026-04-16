import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { BaseChartDirective } from 'ng2-charts';
import { ScheduleService } from '../../services/schedule.service';
import { DashboardData } from '../../models/order.model';
import { ChartConfiguration, ChartData } from 'chart.js';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MatTooltipModule,
    BaseChartDirective
  ],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit {
  isLoading = true;
  data: DashboardData | null = null;
  insights = '';
  insightsLoading = false;
  errorMsg = '';

  statusChartData: ChartData<'bar'> = { labels: [], datasets: [] };
  statusChartOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: false }, title: { display: false } },
    scales: { y: { beginAtZero: true, ticks: { stepSize: 1 } } }
  };

  utilChartData: ChartData<'bar'> = { labels: [], datasets: [] };
  utilChartOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    indexAxis: 'y' as const,
    plugins: { legend: { display: false } },
    scales: { x: { min: 0, max: 100, ticks: { callback: (val) => val + '%' } } }
  };

  delayChartData: ChartData<'doughnut'> = { labels: [], datasets: [] };
  delayChartOptions: ChartConfiguration<'doughnut'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { position: 'right' } }
  };

  orderColumns = ['orderId', 'panelType', 'quantity', 'priority', 'status', 'leadTime', 'completion'];

  constructor(private scheduleService: ScheduleService) {}

  ngOnInit() {
    this.loadDashboard();
  }

  loadDashboard() {
    this.isLoading = true;
    this.scheduleService.getDashboard().subscribe({
      next: (d) => {
        this.data = d;
        this.buildCharts(d);
        this.isLoading = false;
      },
      error: (err) => {
        this.errorMsg = err.message;
        this.isLoading = false;
      }
    });
  }

  loadInsights() {
    this.insightsLoading = true;
    this.scheduleService.getInsights().subscribe({
      next: (r) => { this.insights = r.insights; this.insightsLoading = false; },
      error: () => { this.insights = 'Failed to load insights.'; this.insightsLoading = false; }
    });
  }

  buildCharts(d: DashboardData) {
    const statusColors: Record<string, string> = {
      SCHEDULED: '#42A5F5', COMPLETED: '#66BB6A', DELAYED: '#EF5350', PENDING: '#FFA726'
    };
    const sLabels = Object.keys(d.ordersByStatus);
    this.statusChartData = {
      labels: sLabels,
      datasets: [{
        data: Object.values(d.ordersByStatus),
        backgroundColor: sLabels.map(l => statusColors[l] ?? '#90A4AE'),
        borderRadius: 4
      }]
    };

    const uLabels = Object.keys(d.lineUtilisation);
    const uValues = Object.values(d.lineUtilisation);
    this.utilChartData = {
      labels: uLabels,
      datasets: [{
        data: uValues,
        backgroundColor: uValues.map(v => v > 85 ? '#EF5350' : v > 65 ? '#FFA726' : '#66BB6A'),
        borderRadius: 4
      }]
    };

    const dLabels = Object.keys(d.delaySummary);
    if (dLabels.length > 0) {
      this.delayChartData = {
        labels: dLabels,
        datasets: [{
          data: Object.values(d.delaySummary),
          backgroundColor: ['#EF5350','#FFA726','#42A5F5','#AB47BC','#26A69A']
        }]
      };
    }
  }

  getLineUtilKeys(): string[] {
    return this.data ? Object.keys(this.data.lineUtilisation) : [];
  }

  getDelaySummaryKeys(): string[] {
    return this.data ? Object.keys(this.data.delaySummary) : [];
  }

  statusClass(status: string): string {
    const map: Record<string, string> = {
      SCHEDULED: 'chip-blue', COMPLETED: 'chip-green', DELAYED: 'chip-red', PENDING: 'chip-orange'
    };
    return map[status] ?? 'chip-grey';
  }

  fmtLeadTime(mins: number): string {
    if (!mins) return '—';
    const h = Math.floor(mins / 60);
    const m = mins % 60;
    return h > 0 ? `${h}h ${m > 0 ? m + 'm' : ''}`.trim() : `${m}m`;
  }

  fmtDate(d: string | undefined): string {
    if (!d) return '—';
    return new Date(d).toLocaleString('en-GB', {
      day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit'
    });
  }

  get avgUtil(): number {
    if (!this.data?.lineUtilisation) return 0;
    const vals = Object.values(this.data.lineUtilisation);
    return vals.length ? Math.round(vals.reduce((a, b) => a + b, 0) / vals.length) : 0;
  }
}
