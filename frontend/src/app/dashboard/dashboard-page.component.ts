import { CommonModule, DatePipe, DOCUMENT, isPlatformBrowser } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  OnInit,
  PLATFORM_ID,
  ViewChild,
  computed,
  effect,
  inject,
  signal
} from '@angular/core';
import { firstValueFrom } from 'rxjs';

import {
  DashboardSummaryResponse,
  InterventionMapMarker,
  StatusTrendPoint,
  TechnicianLoadResponse
} from './dashboard.models';

type SummaryMetricKey = 'scheduledCount' | 'inProgressCount' | 'completedCount' | 'validatedCount';
import { NgChartsModule } from 'ng2-charts';
import { Chart, ChartConfiguration, ChartOptions, registerables } from 'chart.js';
import { ShellStateService } from '../layout/shell-state.service';
import { DashboardService } from './dashboard.service';

Chart.register(...registerables);

@Component({
  selector: 'app-dashboard-page',
  standalone: true,
  imports: [CommonModule, DatePipe, NgChartsModule],
  templateUrl: './dashboard-page.component.html',
  styleUrl: './dashboard-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [DatePipe]
})
export class DashboardPageComponent implements OnInit {
  private readonly dashboardService = inject(DashboardService);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly destroyRef = inject(DestroyRef);
  private readonly datePipe = inject(DatePipe);
  private readonly documentRef = inject(DOCUMENT);
  private readonly isBrowser = isPlatformBrowser(this.platformId);
  private readonly shellState = inject(ShellStateService);

  private mapContainerEl?: HTMLDivElement;

  private leaflet: typeof import('leaflet') | null = null;
  private mapInstance: import('leaflet').Map | null = null;
  private markerLayer: import('leaflet').LayerGroup | null = null;
  private defaultIcon: import('leaflet').Icon | null = null;
  private readonly defaultCenter: [number, number] = [46.603354, 1.888334];
  private readonly defaultZoom = 6;

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
  private readonly maxTrendPoints = 14;
  private readonly maxTechnicianBars = 8;
  private readonly statusOrder: ReadonlyArray<string> = [
    'SCHEDULED',
    'IN_PROGRESS',
    'COMPLETED',
    'VALIDATED'
  ];
  private readonly statusLabels: Record<string, string> = {
    SCHEDULED: 'Planifiées',
    IN_PROGRESS: 'En cours',
    COMPLETED: 'Terminées',
    VALIDATED: 'Validées'
  };
  private readonly statusColors: Record<string, string> = {
    SCHEDULED: '#94a3b8',
    IN_PROGRESS: '#2563eb',
    COMPLETED: '#0ea5e9',
    VALIDATED: '#22c55e'
  };
  private readonly statusFillColors: Record<string, string> = {
    SCHEDULED: 'rgba(148, 163, 184, 0.7)',
    IN_PROGRESS: 'rgba(37, 99, 235, 0.75)',
    COMPLETED: 'rgba(14, 165, 233, 0.75)',
    VALIDATED: 'rgba(34, 197, 94, 0.75)'
  };
  private readonly summaryKeyToStatus: Record<SummaryMetricKey, string> = {
    scheduledCount: 'SCHEDULED',
    inProgressCount: 'IN_PROGRESS',
    completedCount: 'COMPLETED',
    validatedCount: 'VALIDATED'
  };

  protected readonly totalSummaryCount = computed(() => {
    const summary = this.summary();
    if (!summary) {
      return 0;
    }
    return this.statusKpis.reduce((total, item) => total + summary[item.key], 0);
  });

  protected readonly averageCompletionMinutes = computed(() => {
    const summary = this.summary();
    if (!summary || summary.averageCompletionSeconds == null) {
      return null;
    }
    return summary.averageCompletionSeconds / 60;
  });

  protected readonly interventionsPerDayChart = computed<ChartConfiguration<'line'>['data']>(() => {
    const points = this.statusTrends();
    if (!points.length) {
      return { labels: [], datasets: [] };
    }

    const totalsByDate = new Map<string, number>();
    for (const point of points) {
      const key = point.date;
      totalsByDate.set(key, (totalsByDate.get(key) ?? 0) + point.count);
    }
    const sortedDates = Array.from(totalsByDate.keys()).sort();
    const recentDates = sortedDates.slice(-this.maxTrendPoints);
    const labels = recentDates.map(date => this.formatDateLabel(date));
    const totals = recentDates.map(date => totalsByDate.get(date) ?? 0);

    return {
      labels,
      datasets: [
        {
          label: 'Interventions par jour',
          data: totals,
          fill: 'origin',
          tension: 0.35,
          borderColor: this.statusColors['IN_PROGRESS'],
          backgroundColor: 'rgba(37, 99, 235, 0.18)',
          pointRadius: 3,
          pointBackgroundColor: '#1d4ed8',
          pointBorderWidth: 0
        }
      ]
    };
  });

  protected readonly interventionsPerDayOptions: ChartOptions<'line'> = {
    responsive: true,
    maintainAspectRatio: false,
    scales: {
      y: {
        beginAtZero: true,
        ticks: {
          precision: 0
        },
        grid: {
          color: 'rgba(148, 163, 184, 0.2)'
        }
      },
      x: {
        grid: {
          display: false
        }
      }
    },
    plugins: {
      legend: {
        display: false
      },
      tooltip: {
        mode: 'index',
        intersect: false
      }
    }
  };

  protected readonly statusStackedChart = computed<ChartConfiguration<'bar'>['data']>(() => {
    const points = this.statusTrends();
    if (!points.length) {
      return { labels: [], datasets: [] };
    }

    const buckets = new Map<string, Record<string, number>>();
    for (const point of points) {
      const dateKey = point.date;
      const statusKey = (point.status ?? '').toUpperCase();
      if (!this.statusOrder.includes(statusKey)) {
        continue;
      }
      if (!buckets.has(dateKey)) {
        buckets.set(dateKey, this.createStatusBucket());
      }
      buckets.get(dateKey)![statusKey] = point.count;
    }

    const sortedDates = Array.from(buckets.keys()).sort();
    const recentDates = sortedDates.slice(-this.maxTrendPoints);
    const labels = recentDates.map(date => this.formatDateLabel(date));

    const datasets = this.statusOrder
        .map(status => ({
          label: this.statusLabels[status],
          data: recentDates.map(date => buckets.get(date)?.[status] ?? 0),
          backgroundColor: this.statusFillColors[status],
          borderWidth: 0,
          stack: 'status'
        }))
        .filter(dataset => dataset.data.some(value => value > 0));

    return {
      labels,
      datasets
    };
  });

  protected readonly statusStackedOptions: ChartOptions<'bar'> = {
    responsive: true,
    maintainAspectRatio: false,
    scales: {
      x: {
        stacked: true,
        grid: {
          display: false
        }
      },
      y: {
        stacked: true,
        beginAtZero: true,
        ticks: {
          precision: 0
        },
        grid: {
          color: 'rgba(148, 163, 184, 0.2)'
        }
      }
    },
    plugins: {
      legend: {
        position: 'top',
        labels: {
          usePointStyle: true,
          boxWidth: 10
        }
      }
    }
  };

  protected readonly technicianLoadChart = computed<ChartConfiguration<'bar'>['data']>(() => {
    const loads = this.technicianLoad();
    if (!loads.length) {
      return { labels: [], datasets: [] };
    }

    const topTechnicians = [...loads]
        .sort((a, b) => b.openCount - a.openCount)
        .slice(0, this.maxTechnicianBars);
    const labels = topTechnicians.map(row => row.technicianName ?? row.technicianEmail);

    return {
      labels,
      datasets: [
        {
          label: 'Interventions ouvertes',
          data: topTechnicians.map(row => row.openCount),
          backgroundColor: this.statusColors['IN_PROGRESS'],
          stack: 'load'
        },
        {
          label: 'Terminées aujourd\'hui',
          data: topTechnicians.map(row => row.completedToday),
          backgroundColor: this.statusColors['VALIDATED'],
          stack: 'load'
        }
      ]
    };
  });

  protected readonly technicianLoadOptions: ChartOptions<'bar'> = {
    responsive: true,
    maintainAspectRatio: false,
    indexAxis: 'y',
    scales: {
      x: {
        stacked: true,
        beginAtZero: true,
        ticks: {
          precision: 0
        },
        grid: {
          color: 'rgba(148, 163, 184, 0.2)'
        }
      },
      y: {
        stacked: true,
        grid: {
          display: false
        }
      }
    },
    plugins: {
      legend: {
        position: 'top',
        labels: {
          usePointStyle: true,
          boxWidth: 10
        }
      }
    }
  };

  protected readonly statusDistributionChart = computed<ChartConfiguration<'doughnut'>['data']>(() => {
    const summary = this.summary();
    if (!summary) {
      return { labels: [], datasets: [] };
    }

    const labels = this.statusKpis.map(item => item.label);
    const data = this.statusKpis.map(item => summary[item.key]);
    const backgroundColor = this.statusKpis.map(
        item => this.statusColors[this.summaryKeyToStatus[item.key]]
    );

    return {
      labels,
      datasets: [
        {
          data,
          backgroundColor,
          borderWidth: 0
        }
      ]
    };
  });

  protected readonly statusDistributionOptions: ChartOptions<'doughnut'> = {
    responsive: true,
    maintainAspectRatio: false,
    cutout: '68%',
    plugins: {
      legend: {
        position: 'bottom',
        labels: {
          usePointStyle: true,
          boxWidth: 10
        }
      }
    }
  };

  constructor() {
    effect(() => {
      if (this.shellState.collapsed()) {
        setTimeout(() => this.mapInstance?.invalidateSize(), 320);
      } else {
        setTimeout(() => this.mapInstance?.invalidateSize(), 150);
      }
    });

    this.destroyRef.onDestroy(() => {
      this.mapInstance?.remove();
      this.mapInstance = null;
      this.markerLayer = null;
    });
  }

  async ngOnInit(): Promise<void> {
    await this.refresh();
  }

  @ViewChild('mapContainer', { static: false })
  set mapContainer(ref: ElementRef<HTMLDivElement> | undefined) {
    if (!this.isBrowser) {
      return;
    }
    if (ref) {
      this.mapContainerEl = ref.nativeElement;
      this.ensureMapInitialized();
    }
  }

  async refresh(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      await Promise.all([this.loadSummary(), this.loadTrends(), this.loadTechnicianLoad()]);
      await this.ensureMapInitialized();
      await this.loadMapMarkers();
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
    this.updateMapMarkers(response);
  }

  private async ensureMapInitialized(): Promise<void> {
    if (!this.isBrowser || this.mapInstance || !this.mapContainerEl) {
      return;
    }

    const leafletModule = await import('leaflet');
    const L = leafletModule.default ?? leafletModule;
    this.leaflet = L;

    const iconRetinaUrl = this.resolveAssetUrl('assets/leaflet/marker-icon-2x.png');
    const iconUrl = this.resolveAssetUrl('assets/leaflet/marker-icon.png');
    const shadowUrl = this.resolveAssetUrl('assets/leaflet/marker-shadow.png');

    this.defaultIcon = L.icon({
      iconRetinaUrl,
      iconUrl,
      shadowUrl,
      iconSize: [25, 41],
      iconAnchor: [12, 41],
      popupAnchor: [1, -34],
      shadowSize: [41, 41]
    });

    this.mapInstance = L.map(this.mapContainerEl, {
      center: this.defaultCenter,
      zoom: this.defaultZoom,
      zoomControl: true,
      attributionControl: false
    });

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; OpenStreetMap contributors'
    }).addTo(this.mapInstance);

    this.markerLayer = L.layerGroup().addTo(this.mapInstance);
    this.updateMapMarkers(this.mapMarkers());
    setTimeout(() => this.mapInstance?.invalidateSize(), 0);
  }

  private updateMapMarkers(markers: InterventionMapMarker[]): void {
    if (!this.mapInstance || !this.markerLayer || !this.leaflet) {
      return;
    }

    const L = this.leaflet;
    const layerGroup = this.markerLayer;
    const icon = this.defaultIcon ?? undefined;
    layerGroup.clearLayers();

    if (markers.length === 0) {
      this.mapInstance.setView(this.defaultCenter, this.defaultZoom);
      this.mapInstance.invalidateSize();
      return;
    }

    const bounds = L.latLngBounds([]);
    markers.forEach(markerData => {
      const latLng = L.latLng(markerData.latitude, markerData.longitude);
      const popupContent = this.buildPopup(markerData);
      L.marker(latLng, { icon }).bindPopup(popupContent).addTo(layerGroup);
      bounds.extend(latLng);
    });

    this.mapInstance.fitBounds(bounds, { padding: [24, 24], maxZoom: 16 });
    this.mapInstance.invalidateSize();
  }

  private buildPopup(marker: InterventionMapMarker): string {
    const updatedAt = this.datePipe.transform(marker.updatedAt, 'medium') ?? 'N/A';
    const plannedAt = marker.plannedAt ? this.datePipe.transform(marker.plannedAt, 'medium') : null;
    const plannedLine = plannedAt ? `<br>Planifiée : ${plannedAt}` : '';
    return `<strong>${marker.status}</strong><br>Dernière mise à jour : ${updatedAt}${plannedLine}`;
  }

  private resolveAssetUrl(path: string): string {
    if (!this.isBrowser) {
      return path;
    }
    const baseHref = this.documentRef?.baseURI ?? '/';
    return new URL(path, baseHref).toString();
  }

  private describeError(error: unknown): string {
    if (error instanceof Error) {
      return error.message;
    }
    return 'Une erreur est survenue lors du chargement du tableau de bord.';
  }

  private createStatusBucket(): Record<string, number> {
    return {
      SCHEDULED: 0,
      IN_PROGRESS: 0,
      COMPLETED: 0,
      VALIDATED: 0
    };
  }

  private formatDateLabel(dateStr: string): string {
    return this.datePipe.transform(dateStr, 'd MMM', undefined, 'fr-FR') ?? dateStr;
  }
}
