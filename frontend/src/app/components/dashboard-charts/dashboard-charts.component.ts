import { Component, input, ChangeDetectionStrategy, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { IonCard, IonCardHeader, IonCardTitle, IonCardContent } from '@ionic/angular/standalone';
import { NgApexchartsModule } from 'ng-apexcharts';
import type {
  ApexChart, ApexDataLabels, ApexStroke, ApexTooltip,
  ApexLegend, ApexPlotOptions, ApexNonAxisChartSeries, ApexResponsive,
  ApexAxisChartSeries, ApexXAxis, ApexYAxis, ApexGrid, ApexMarkers,
} from 'ng-apexcharts';
import { ChartData, DonutData, DashboardSectionStates } from '../../models/dashboard.models';
import { INSTITUTIONAL_BLUE } from '../../shared/ui-colors';

@Component({
  selector: 'app-dashboard-charts',
  standalone: true,
  imports: [CommonModule, RouterLink, NgApexchartsModule, IonCard, IonCardHeader, IonCardTitle, IonCardContent],
  templateUrl: './dashboard-charts.component.html',
  styleUrls: ['./dashboard-charts.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardChartsComponent {
  readonly tareasPorEstado = input.required<DonutData>();
  readonly inventarioPorEstado = input<DonutData>({ items: [] });
  readonly reunionesPorEstado = input<DonutData>({ items: [] });
  readonly showInventario = input(true);
  readonly chartData = input<ChartData>({ categories: [], series: [] });
  readonly states = input<Partial<DashboardSectionStates>>({});

  protected readonly totalTareas = computed(() => this.tareasPorEstado().items.reduce((sum, item) => sum + item.value, 0));
  protected readonly totalInventario = computed(() => this.inventarioPorEstado().items.reduce((sum, item) => sum + item.value, 0));
  protected readonly totalReuniones = computed(() => this.reunionesPorEstado().items.reduce((sum, item) => sum + item.value, 0));

  protected readonly donutChart: ApexChart = {
    type: 'donut',
    height: 280,
    toolbar: { show: false },
    fontFamily: 'Inter, system-ui, sans-serif',
  };

  protected readonly donutDataLabels: ApexDataLabels = { enabled: false };
  protected readonly donutLegend: ApexLegend = {
    position: 'bottom',
    fontSize: '12px',
    labels: { colors: '#475569' },
    markers: { size: 6 },
    itemMargin: { horizontal: 12 },
  };
  protected readonly donutPlotOptions: ApexPlotOptions = {
    pie: {
      donut: {
        size: '65%',
        labels: {
          show: true,
          total: {
            show: true,
            label: 'Total',
            fontSize: '13px',
            fontWeight: 600,
            color: '#64748b',
          },
        },
      },
    },
  };
  protected readonly donutTooltip: ApexTooltip = {
    theme: 'light',
    y: { formatter: (val: number) => `${val} registros` },
  };
  protected readonly donutResponsive: ApexResponsive[] = [
    { breakpoint: 480, options: { chart: { height: 220 }, legend: { position: 'bottom' } } },
  ];
  protected readonly donutStroke: ApexStroke = { lineCap: 'round' };

  protected readonly donutTareasColors = computed(() =>
    this.tareasPorEstado().items.map(i => i.color)
  );

  protected readonly donutTareasSeries = computed<ApexNonAxisChartSeries>(
    () => this.tareasPorEstado().items.map(i => i.value)
  );
  protected readonly donutTareasLabels = computed<string[]>(
    () => this.tareasPorEstado().items.map(i => i.label)
  );

  protected readonly donutInventarioSeries = computed<ApexNonAxisChartSeries>(
    () => this.inventarioPorEstado().items.map(i => i.value)
  );
  protected readonly donutInventarioLabels = computed<string[]>(
    () => this.inventarioPorEstado().items.map(i => i.label)
  );
  protected readonly donutInventarioColors = computed<string[]>(
    () => this.inventarioPorEstado().items.map(i => i.color)
  );

  protected readonly donutReunionesSeries = computed<ApexNonAxisChartSeries>(
    () => this.reunionesPorEstado().items.map(i => i.value)
  );
  protected readonly donutReunionesLabels = computed<string[]>(
    () => this.reunionesPorEstado().items.map(i => i.label)
  );
  protected readonly donutReunionesColors = computed<string[]>(
    () => this.reunionesPorEstado().items.map(i => i.color)
  );

  protected readonly activitySeries = computed<ApexAxisChartSeries>(() => this.chartData().series);
  protected readonly activityXaxis = computed<ApexXAxis>(() => ({ categories: this.chartData().categories, axisBorder: { show: false }, axisTicks: { show: false }, labels: { style: { colors: '#8090a5', fontSize: '11px' } } }));
  protected readonly activityChart: ApexChart = { type: 'area', height: 270, toolbar: { show: false }, zoom: { enabled: false }, fontFamily: 'Inter, system-ui, sans-serif' };
  protected readonly activityStroke: ApexStroke = { curve: 'smooth', width: 3 };
  protected readonly activityGrid: ApexGrid = { borderColor: '#edf2f7', strokeDashArray: 4, xaxis: { lines: { show: false } } };
  protected readonly activityYaxis: ApexYAxis = { min: 0, forceNiceScale: true, labels: { style: { colors: '#8090a5', fontSize: '11px' } } };
  protected readonly activityMarkers: ApexMarkers = { size: 4, strokeWidth: 0, hover: { size: 6 } };
  protected readonly activityColors = [INSTITUTIONAL_BLUE];
  protected readonly activityTooltip: ApexTooltip = { theme: 'light', y: { formatter: (val: number) => `${val} operaciones` } };
}
