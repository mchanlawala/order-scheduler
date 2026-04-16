import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ScheduleResult, Order, StepExecution, DashboardData } from '../models/order.model';

@Injectable({ providedIn: 'root' })
export class ScheduleService {
  private readonly api = '/api/schedule';

  constructor(private http: HttpClient) {}

  uploadExcel(file: File): Observable<ScheduleResult> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ScheduleResult>(`${this.api}/upload`, form).pipe(catchError(this.handleError));
  }

  runDemo(): Observable<ScheduleResult> {
    return this.http.post<ScheduleResult>(`${this.api}/demo`, {}).pipe(catchError(this.handleError));
  }

  getAllOrders(): Observable<Order[]> {
    return this.http.get<Order[]>(`${this.api}/orders`).pipe(catchError(this.handleError));
  }

  getResult(): Observable<ScheduleResult> {
    return this.http.get<ScheduleResult>(`${this.api}/result`).pipe(catchError(this.handleError));
  }

  getGanttData(orderId: string): Observable<StepExecution[]> {
    return this.http.get<StepExecution[]>(`${this.api}/gantt/${orderId}`).pipe(catchError(this.handleError));
  }

  getDashboard(): Observable<DashboardData> {
    return this.http.get<DashboardData>(`${this.api}/dashboard`).pipe(catchError(this.handleError));
  }

  getInsights(): Observable<{ insights: string }> {
    return this.http.get<{ insights: string }>(`${this.api}/insights`).pipe(catchError(this.handleError));
  }

  private handleError(err: HttpErrorResponse) {
    const msg = err.error?.error || err.message || 'Unknown error';
    return throwError(() => new Error(msg));
  }
}
