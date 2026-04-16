import { Routes } from '@angular/router';
import { UploadComponent } from './components/upload/upload.component';
import { DashboardComponent } from './components/dashboard/dashboard.component';
import { GanttChartComponent } from './components/gantt-chart/gantt-chart.component';

export const routes: Routes = [
  { path: '', redirectTo: '/upload', pathMatch: 'full' },
  { path: 'upload', component: UploadComponent },
  { path: 'dashboard', component: DashboardComponent },
  { path: 'gantt', component: GanttChartComponent },
  { path: '**', redirectTo: '/upload' }
];
