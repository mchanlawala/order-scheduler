import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDividerModule } from '@angular/material/divider';
import { MatExpansionModule } from '@angular/material/expansion';
import { ScheduleService } from '../../services/schedule.service';

@Component({
  selector: 'app-upload',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatDividerModule,
    MatExpansionModule,
    MatSnackBarModule
  ],
  templateUrl: './upload.component.html',
  styleUrls: ['./upload.component.css']
})
export class UploadComponent {
  selectedFile: File | null = null;
  isDragOver = false;
  isLoading = false;
  loadingMessage = '';

  constructor(
    private scheduleService: ScheduleService,
    private snackBar: MatSnackBar,
    private router: Router
  ) {}

  onDragOver(e: DragEvent) {
    e.preventDefault();
    this.isDragOver = true;
  }

  onDragLeave() {
    this.isDragOver = false;
  }

  onDrop(e: DragEvent) {
    e.preventDefault();
    this.isDragOver = false;
    const files = e.dataTransfer?.files;
    if (files && files.length > 0) this.setFile(files[0]);
  }

  onFileSelect(e: Event) {
    const input = e.target as HTMLInputElement;
    if (input.files && input.files.length > 0) this.setFile(input.files[0]);
  }

  setFile(file: File) {
    if (!file.name.endsWith('.xlsx') && !file.name.endsWith('.xls')) {
      this.snackBar.open('Please select an Excel file (.xlsx or .xls)', 'Close', { duration: 4000 });
      return;
    }
    this.selectedFile = file;
  }

  upload() {
    if (!this.selectedFile) return;
    this.isLoading = true;
    this.loadingMessage = 'Parsing Excel file...';

    setTimeout(() => { this.loadingMessage = 'Running scheduling engine...'; }, 800);
    setTimeout(() => { this.loadingMessage = 'Running AI analysis...'; }, 2000);

    this.scheduleService.uploadExcel(this.selectedFile).subscribe({
      next: (result) => {
        this.isLoading = false;
        this.snackBar.open(
          `✅ Scheduled ${result.orders?.length ?? 0} orders successfully!`,
          'Close',
          { duration: 4000, panelClass: 'snack-success' }
        );
        this.router.navigate(['/gantt']);
      },
      error: (err) => {
        this.isLoading = false;
        this.snackBar.open(`❌ Error: ${err.message}`, 'Close', {
          duration: 6000, panelClass: 'snack-error'
        });
      }
    });
  }

  runDemo() {
    this.isLoading = true;
    this.loadingMessage = 'Loading demo data and scheduling...';
    this.scheduleService.runDemo().subscribe({
      next: (result) => {
        this.isLoading = false;
        this.snackBar.open(
          `✅ Demo: Scheduled ${result.orders?.length ?? 0} orders!`,
          'Close',
          { duration: 4000, panelClass: 'snack-success' }
        );
        this.router.navigate(['/gantt']);
      },
      error: (err) => {
        this.isLoading = false;
        this.snackBar.open(`❌ Error: ${err.message}`, 'Close', { duration: 6000 });
      }
    });
  }

  clearFile() {
    this.selectedFile = null;
  }

  formatBytes(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / 1048576).toFixed(1) + ' MB';
  }
}
