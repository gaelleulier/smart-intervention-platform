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
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

import {
  DashboardSummaryResponse,
  InterventionMapMarker,
  StatusTrendPoint,
  TechnicianLoadResponse,
  AiInsightResponse,
  ForecastResponse,
  SmartAssignmentRequestPayload,
  SmartAssignmentResponsePayload,
  SmartAssignmentCandidate
} from './dashboard.models';

type SummaryMetricKey = 'scheduledCount' | 'inProgressCount' | 'completedCount' | 'validatedCount';
import { NgChartsModule } from 'ng2-charts';
import { Chart, ChartConfiguration, ChartOptions, registerables } from 'chart.js';
import { ShellStateService } from '../layout/shell-state.service';
import { DashboardService } from './dashboard.service';
import { AuthService } from '../auth/auth.service';

Chart.register(...registerables);

@Component({
  selector: 'app-dashboard-page',
  standalone: true,
  imports: [CommonModule, DatePipe, NgChartsModule, ReactiveFormsModule],
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
  private readonly fb = inject(FormBuilder);
  private readonly isBrowser = isPlatformBrowser(this.platformId);
  private readonly shellState = inject(ShellStateService);
  private readonly auth = inject(AuthService);

  private mapContainerEl?: HTMLDivElement;
  private smartMapContainerEl?: HTMLDivElement;

  private leaflet: typeof import('leaflet') | null = null;
  private mapInstance: import('leaflet').Map | null = null;
  private markerLayer: import('leaflet').LayerGroup | null = null;
  private defaultIcon: import('leaflet').Icon | null = null;
  private smartAssignmentMapInstance: import('leaflet').Map | null = null;
  private smartAssignmentMarkerLayer: import('leaflet').LayerGroup | null = null;
  private smartAssignmentLocationMarker: import('leaflet').Marker | null = null;
  private readonly defaultCenter: [number, number] = [46.603354, 1.888334];
  private readonly defaultZoom = 6;

  protected readonly summary = signal<DashboardSummaryResponse | null>(null);
  protected readonly statusTrends = signal<StatusTrendPoint[]>([]);
  protected readonly technicianLoad = signal<TechnicianLoadResponse[]>([]);
  protected readonly mapMarkers = signal<InterventionMapMarker[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly aiInsights = signal<AiInsightResponse | null>(null);
  protected readonly aiInsightsError = signal<string | null>(null);
  protected readonly forecast = signal<ForecastResponse | null>(null);
  protected readonly forecastError = signal<string | null>(null);
  protected readonly smartAssignmentResult = signal<SmartAssignmentResponsePayload | null>(null);
  protected readonly smartAssignmentLoading = signal(false);
  protected readonly smartAssignmentError = signal<string | null>(null);
  protected readonly smartAssignmentState = signal<'idle' | 'thinking' | 'result'>('idle');
  protected readonly smartAssignmentMapOpen = signal(false);
  protected readonly smartAssignmentLocation = signal<{ lat: number; lng: number } | null>(null);
  protected readonly smartAssignmentToast = signal<string | null>(null);
  protected readonly smartAssignmentReadOnly = computed(() => {
    const role = this.auth.role();
    if (!role) {
      return false;
    }
    const normalized = role.trim().toUpperCase();
    return normalized === 'TECH' || normalized === 'TECHNICIAN';
  });
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

  protected readonly busy = computed(() => this.loading());

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

  protected readonly forecastChartData = computed<ChartConfiguration<'line'>['data']>(() => {
    const response = this.forecast();
    if (!response || !response.points.length) {
      return { labels: [], datasets: [] };
    }
    const labels = response.points.map(point => this.formatDateLabel(point.date));
    const data = response.points.map(point => point.predictedCount);
    return {
      labels,
      datasets: [
        {
          label: 'Prévision (7 jours)',
          data,
          borderColor: '#8b5cf6',
          backgroundColor: 'rgba(139, 92, 246, 0.18)',
          tension: 0.3,
          fill: 'origin'
        }
      ]
    };
  });

  protected readonly forecastChartOptions: ChartOptions<'line'> = {
    responsive: true,
    maintainAspectRatio: false,
    scales: {
      y: {
        beginAtZero: true,
        ticks: { precision: 0 },
        grid: { color: 'rgba(148, 163, 184, 0.2)' }
      },
      x: { grid: { display: false } }
    },
    plugins: {
      legend: { display: false },
      tooltip: { intersect: false }
    }
  };

  protected readonly smartAssignmentForm = this.fb.group({
    title: ['', [Validators.maxLength(160)]],
    latitude: [null as number | null, [Validators.min(-90), Validators.max(90)]],
    longitude: [null as number | null, [Validators.min(-180), Validators.max(180)]]
  });
  protected readonly primaryInsightHighlight = computed(() => this.aiInsights()?.highlights?.[0] ?? null);
  protected readonly secondaryInsightHighlights = computed(() => this.aiInsights()?.highlights?.slice(1) ?? []);
  protected readonly smartAssignmentLocationLabel = computed(() => {
    const loc = this.smartAssignmentLocation();
    if (!loc) {
      return '';
    }
    return `${loc.lat.toFixed(3)}°, ${loc.lng.toFixed(3)}°`;
  });
  protected readonly smartAssignmentHasLocation = computed(() => this.smartAssignmentLocation() !== null);

  private smartAssignmentToastTimer: ReturnType<typeof setTimeout> | null = null;

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
      this.smartAssignmentMapInstance?.remove();
      this.smartAssignmentMapInstance = null;
      this.smartAssignmentMarkerLayer = null;
      this.smartAssignmentLocationMarker = null;
    });

    effect(() => {
      const loc = this.smartAssignmentLocation();
      if (loc) {
        this.smartAssignmentForm.patchValue({ latitude: loc.lat, longitude: loc.lng }, { emitEvent: false });
      } else {
        this.smartAssignmentForm.patchValue({ latitude: null, longitude: null }, { emitEvent: false });
      }
      this.refreshSmartAssignmentMapMarkers(this.smartAssignmentResult(), loc);
    });

    effect(() => {
      const result = this.smartAssignmentResult();
      this.refreshSmartAssignmentMapMarkers(result, this.smartAssignmentLocation());
    });

    effect(() => {
      if (this.smartAssignmentMapOpen()) {
        void this.ensureSmartAssignmentMapInitialized();
      }
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

  @ViewChild('smartMapContainer', { static: false })
  set smartMapContainer(ref: ElementRef<HTMLDivElement> | undefined) {
    if (!this.isBrowser) {
      return;
    }
    this.smartMapContainerEl = ref?.nativeElement;
    if (this.smartAssignmentMapOpen()) {
      void this.ensureSmartAssignmentMapInitialized();
    }
  }

  async refresh(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      await Promise.all([
        this.loadSummary(),
        this.loadTrends(),
        this.loadTechnicianLoad(),
        this.loadAiInsights(),
        this.loadForecast()
      ]);
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

  protected trendLabel(direction: string | null | undefined): string {
    switch ((direction ?? '').toUpperCase()) {
      case 'UP':
        return 'En hausse';
      case 'DOWN':
        return 'En baisse';
      default:
        return 'Stable';
    }
  }

  protected trendClass(direction: string | null | undefined): string {
    switch ((direction ?? '').toUpperCase()) {
      case 'UP':
        return 'trend--up';
      case 'DOWN':
        return 'trend--down';
      default:
        return 'trend--flat';
    }
  }

  protected trendIcon(direction: string | null | undefined): string {
    switch ((direction ?? '').toUpperCase()) {
      case 'UP':
        return '▲';
      case 'DOWN':
        return '▼';
      default:
        return '◆';
    }
  }

  protected async onSmartAssignmentSubmit(): Promise<void> {
    if (this.smartAssignmentReadOnly()) {
      return;
    }
    if (this.smartAssignmentLoading()) {
      return;
    }
    const location = this.smartAssignmentLocation();
    if (!location) {
      this.smartAssignmentError.set('Sélectionnez un emplacement via « Choisir sur la carte ».');
      this.smartAssignmentState.set('idle');
      return;
    }
    const generatedTitle = this.composeSmartAssignmentTitle();
    this.smartAssignmentForm.patchValue(
      { title: generatedTitle, latitude: location.lat, longitude: location.lng },
      { emitEvent: false }
    );
    const values = this.smartAssignmentForm.getRawValue();
    const payload: SmartAssignmentRequestPayload = {
      title: generatedTitle,
      latitude: values.latitude ?? undefined,
      longitude: values.longitude ?? undefined
    };
    this.smartAssignmentLoading.set(true);
    this.smartAssignmentState.set('thinking');
    this.smartAssignmentError.set(null);
    this.smartAssignmentResult.set(null);
    const startedAt = Date.now();
    const minDelay = 1200;
    try {
      const responsePromise = firstValueFrom(this.dashboardService.recommendTechnician(payload));
      const response = await responsePromise;
      const elapsed = Date.now() - startedAt;
      if (elapsed < minDelay) {
        await new Promise(resolve => setTimeout(resolve, minDelay - elapsed));
      }
      this.smartAssignmentResult.set(response);
      this.smartAssignmentState.set('result');
    } catch (error) {
      this.smartAssignmentError.set(this.describeError(error));
      this.smartAssignmentState.set('idle');
      this.smartAssignmentResult.set(null);
    } finally {
      this.smartAssignmentLoading.set(false);
    }
  }

  protected resetSmartAssignment(): void {
    if (this.smartAssignmentReadOnly()) {
      return;
    }
    this.smartAssignmentForm.reset({
      title: '',
      latitude: null,
      longitude: null
    });
    this.smartAssignmentResult.set(null);
    this.smartAssignmentError.set(null);
    this.smartAssignmentState.set('idle');
    this.smartAssignmentLocation.set(null);
    this.clearSmartAssignmentToast();
  }

  protected smartAssignmentScore(): number {
    const result = this.smartAssignmentResult();
    if (!result) {
      return 0;
    }
    return Math.round(result.recommended.overallScore * 100);
  }

  protected smartAssignmentRingColor(): string {
    const score = this.smartAssignmentScore();
    if (score >= 75) {
      return '#16a34a';
    }
    if (score >= 45) {
      return '#f59e0b';
    }
    return '#dc2626';
  }

  protected workloadLabel(openAssignments: number): string {
    if (openAssignments <= 1) {
      return 'faible';
    }
    if (openAssignments <= 3) {
      return 'modérée';
    }
    return 'élevée';
  }

  protected skillLabel(matches: number): string {
    if (matches >= 5) {
      return 'élevée';
    }
    if (matches >= 2) {
      return 'moyenne';
    }
    return 'à confirmer';
  }

  protected isSmartAssignmentThinking(): boolean {
    return this.smartAssignmentState() === 'thinking';
  }

  protected applyRecommendation(candidate?: SmartAssignmentCandidate): void {
    if (this.smartAssignmentReadOnly()) {
      return;
    }
    const current = this.smartAssignmentResult();
    if (!current) {
      return;
    }
    const selected = candidate ?? current.recommended;
    const updatedAlternatives = current.alternatives
      .filter(alt => alt.technicianId !== selected.technicianId)
      .concat(
        candidate && candidate.technicianId !== current.recommended.technicianId
          ? [current.recommended]
          : []
      );
    this.smartAssignmentResult.set({
      ...current,
      recommended: selected,
      alternatives: updatedAlternatives
    });
    this.smartAssignmentState.set('result');
    this.pushSmartAssignmentToast(`Technicien ${selected.fullName} assigné avec succès.`);
    this.closeSmartAssignmentMap();
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

  protected openSmartAssignmentMap(): void {
    if (this.smartAssignmentReadOnly()) {
      return;
    }
    this.smartAssignmentMapOpen.set(true);
  }

  protected closeSmartAssignmentMap(): void {
    this.smartAssignmentMapOpen.set(false);
  }

  private async ensureSmartAssignmentMapInitialized(): Promise<void> {
    if (!this.isBrowser || !this.smartAssignmentMapOpen() || !this.smartMapContainerEl) {
      return;
    }
    const L = await this.loadLeaflet();
    if (!this.smartAssignmentMapInstance) {
      if (!this.defaultIcon) {
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
      }

      this.smartAssignmentMapInstance = L.map(this.smartMapContainerEl, {
        center: this.smartAssignmentLocation() ?? { lat: this.defaultCenter[0], lng: this.defaultCenter[1] },
        zoom: this.defaultZoom,
        zoomControl: true,
        attributionControl: false
      });

      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '&copy; OpenStreetMap contributors'
      }).addTo(this.smartAssignmentMapInstance);

      this.smartAssignmentMarkerLayer = L.layerGroup().addTo(this.smartAssignmentMapInstance);
      this.smartAssignmentMapInstance.on('click', event => {
        this.onSmartAssignmentMapClick(event.latlng);
      });
    }

    const location = this.smartAssignmentLocation();
    if (location) {
      this.smartAssignmentMapInstance.setView(location, 12);
    } else {
      this.smartAssignmentMapInstance.setView({ lat: this.defaultCenter[0], lng: this.defaultCenter[1] }, this.defaultZoom);
    }
    this.refreshSmartAssignmentMapMarkers(this.smartAssignmentResult(), this.smartAssignmentLocation());
    setTimeout(() => this.smartAssignmentMapInstance?.invalidateSize(), 80);
  }

  private onSmartAssignmentMapClick(latlng: { lat: number; lng: number }): void {
    if (this.smartAssignmentReadOnly()) {
      return;
    }
    this.smartAssignmentLocation.set(latlng);
    this.smartAssignmentState.set('idle');
    this.smartAssignmentResult.set(null);
    this.smartAssignmentError.set(null);
  }

  private async loadAiInsights(): Promise<void> {
    try {
      const response = await firstValueFrom(this.dashboardService.getAiInsights());
      this.aiInsights.set(response);
      this.aiInsightsError.set(null);
    } catch (error) {
      this.aiInsights.set(null);
      this.aiInsightsError.set(this.describeError(error));
    }
  }

  private async loadForecast(): Promise<void> {
    try {
      const response = await firstValueFrom(this.dashboardService.getForecast());
      this.forecast.set(response);
      this.forecastError.set(null);
    } catch (error) {
      this.forecast.set(null);
      this.forecastError.set(this.describeError(error));
    }
  }

  private async ensureMapInitialized(): Promise<void> {
    if (!this.isBrowser || this.mapInstance || !this.mapContainerEl) {
      return;
    }

    const L = await this.loadLeaflet();

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

  private async loadLeaflet(): Promise<typeof import('leaflet')> {
    if (this.leaflet) {
      return this.leaflet;
    }
    const module = await import('leaflet');
    this.leaflet = module.default ?? module;
    return this.leaflet;
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

  private refreshSmartAssignmentMapMarkers(
          result: SmartAssignmentResponsePayload | null,
          location: { lat: number; lng: number } | null): void {
    if (!this.smartAssignmentMapInstance || !this.smartAssignmentMarkerLayer) {
      return;
    }
    const layer = this.smartAssignmentMarkerLayer;
    layer.clearLayers();

    const L = this.leaflet;
    if (!L) {
      return;
    }

    if (location) {
      if (!this.smartAssignmentLocationMarker) {
        this.smartAssignmentLocationMarker = L.marker(location, {
          icon: this.defaultIcon ?? undefined
        });
      } else {
        this.smartAssignmentLocationMarker.setLatLng(location);
      }
      this.smartAssignmentLocationMarker.addTo(layer).bindPopup('Lieu de l\'intervention');
    } else if (this.smartAssignmentLocationMarker) {
      this.smartAssignmentLocationMarker.remove();
      this.smartAssignmentLocationMarker = null;
    }

    if (result && location) {
      const candidates = [result.recommended, ...result.alternatives];
      const baseAngle = 45;
      const step = candidates.length > 0 ? 360 / candidates.length : 0;
      candidates.forEach((candidate, index) => {
        const angle = baseAngle + step * index;
        const dist = Math.max(candidate.distanceKm ?? 0.4, 0.3);
        const coord = this.computeOffsetCoordinate(location, dist, angle);
        const isRecommended = index === 0;
        L.circleMarker(coord, {
          radius: isRecommended ? 11 : 9,
          color: isRecommended ? '#2563eb' : '#7c3aed',
          weight: 3,
          opacity: 0.9,
          fillOpacity: 0.4,
          fillColor: isRecommended ? 'rgba(37,99,235,0.25)' : 'rgba(124,58,237,0.25)'
        })
            .addTo(layer)
            .bindPopup(`<strong>${candidate.fullName}</strong><br/>Score: ${(candidate.overallScore * 100).toFixed(1)}%`);
      });
    } else if (location) {
      const technicians = this.technicianLoad().slice(0, 3);
      technicians.forEach((tech, index) => {
        const angle = 60 + index * 150;
        const dist = 0.6 + index * 0.25;
        const coord = this.computeOffsetCoordinate(location, dist, angle);
        L.circleMarker(coord, {
          radius: 8,
          color: '#38bdf8',
          weight: 2,
          opacity: 0.85,
          fillOpacity: 0.3,
          fillColor: 'rgba(14,165,233,0.25)'
        })
                .addTo(layer)
                .bindPopup(`<strong>${tech.technicianName ?? tech.technicianEmail}</strong><br/>Charge: ${tech.openCount}`);
      });
    }

    if (location && this.smartAssignmentMapInstance) {
      this.smartAssignmentMapInstance.panTo(location, { animate: true });
    }
  }

  private computeOffsetCoordinate(base: { lat: number; lng: number }, distanceKm: number, angleDeg: number) {
    const earthRadiusKm = 6371;
    const angularDistance = distanceKm / earthRadiusKm;
    const bearing = (angleDeg * Math.PI) / 180;
    const lat1 = (base.lat * Math.PI) / 180;
    const lon1 = (base.lng * Math.PI) / 180;

    const sinLat1 = Math.sin(lat1);
    const cosLat1 = Math.cos(lat1);
    const sinAngular = Math.sin(angularDistance);
    const cosAngular = Math.cos(angularDistance);

    const lat2 = Math.asin(sinLat1 * cosAngular + cosLat1 * sinAngular * Math.cos(bearing));
    const lon2 =
            lon1 + Math.atan2(
                    Math.sin(bearing) * sinAngular * cosLat1,
                    cosAngular - sinLat1 * Math.sin(lat2));

    return {
      lat: (lat2 * 180) / Math.PI,
      lng: ((lon2 * 180) / Math.PI + 540) % 360 - 180
    };
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
    if (error instanceof HttpErrorResponse) {
      const detail = (error.error as { detail?: string })?.detail ?? error.message;
      return detail || 'Requête échouée.';
    }
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

  private composeSmartAssignmentTitle(): string {
    const label = this.smartAssignmentLocationLabel();
    if (label) {
      return `Intervention ${label}`;
    }
    return 'Intervention assistée par IA';
  }

  private pushSmartAssignmentToast(message: string, duration = 3200): void {
    if (!this.isBrowser) {
      return;
    }
    this.smartAssignmentToast.set(message);
    if (this.smartAssignmentToastTimer) {
      clearTimeout(this.smartAssignmentToastTimer);
    }
    this.smartAssignmentToastTimer = setTimeout(() => {
      this.smartAssignmentToast.set(null);
      this.smartAssignmentToastTimer = null;
    }, duration);
  }

  private clearSmartAssignmentToast(): void {
    if (this.smartAssignmentToastTimer) {
      clearTimeout(this.smartAssignmentToastTimer);
      this.smartAssignmentToastTimer = null;
    }
    this.smartAssignmentToast.set(null);
  }

  private formatDateLabel(dateStr: string): string {
    return this.datePipe.transform(dateStr, 'd MMM', undefined, 'fr-FR') ?? dateStr;
  }
}
