import { CommonModule, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import {
  DashboardSummaryResponse,
  InterventionMapMarker,
  StatusTrendPoint,
  TechnicianLoadResponse
} from './dashboard.models';

type SummaryMetricKey = 'scheduledCount' | 'inProgressCount' | 'completedCount' | 'validatedCount';
import { DashboardService } from './dashboard.service';

@Component({
  selector: 'app-dashboard-page',
  standalone: true,
  imports: [CommonModule, DatePipe],
  templateUrl: './dashboard-page.component.html',
  styleUrl: './dashboard-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DashboardPageComponent implements OnInit {
  private readonly dashboardService = inject(DashboardService);

  protected readonly summary = signal<DashboardSummaryResponse | null>(null);
  protected readonly statusTrends = signal<StatusTrendPoint[]>([]);
  protected readonly technicianLoad = signal<TechnicianLoadResponse[]>([]);
  protected readonly mapMarkers = signal<InterventionMapMarker[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly statusKpis: ReadonlyArray<{ key: SummaryMetricKey; label: string }> = [
    { key: 'scheduledCount', label: 'Planifiées' },
    { key: 'inProgressCount', label: 'En cours' },
    { key: 'completedCount', label: 'Terminées' },
    { key: 'validatedCount', label: 'Validées' }
  ];

  async ngOnInit(): Promise<void> {
    await this.refresh();
  }

  async refresh(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      await Promise.all([
        this.loadSummary(),
        this.loadTrends(),
        this.loadTechnicianLoad(),
        this.loadMapMarkers()
      ]);
    } catch (error) {
      this.error.set(this.describeError(error));
    } finally {
      this.loading.set(false);
    }
  }

  protected summaryValue(summary: DashboardSummaryResponse, key: SummaryMetricKey): number {
    return summary[key];
  }

  private async loadSummary(): Promise<void> {
    const response = await firstValueFrom(this.dashboardService.getSummary());
    this.summary.set(response);
  }

  private async loadTrends(): Promise<void> {
    const response = await firstValueFrom(this.dashboardService.getStatusTrends());
    this.statusTrends.set(response);
  }

  private async loadTechnicianLoad(): Promise<void> {
    const response = await firstValueFrom(this.dashboardService.getTechnicianLoad());
    this.technicianLoad.set(response);
  }

  private async loadMapMarkers(): Promise<void> {
    const response = await firstValueFrom(this.dashboardService.getMapMarkers());
    this.mapMarkers.set(response);
  }

  private describeError(error: unknown): string {
    if (error instanceof Error) {
      return error.message;
    }
    return 'Une erreur est survenue lors du chargement du tableau de bord.';
  }
}
