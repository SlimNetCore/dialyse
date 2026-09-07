import {ChangeDetectionStrategy, Component, effect, inject, OnInit, signal} from '@angular/core';
import {MatCardModule} from '@angular/material/card';
import {MatIconModule} from '@angular/material/icon';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {TranslateModule} from '@ngx-translate/core';
import {compatForm} from '@angular/forms/signals/compat';
import {FormField, FormRoot, required} from '@angular/forms/signals';
import {WebSocketService} from '../../core/ws/websocket.service';
import {AuthStore} from '../../core/state/auth.store';
import {DashboardStore} from './state/dashboard.store';

@Component({
  selector: 'app-center-dashboard',
  standalone: true,
  imports: [
    MatCardModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    TranslateModule,
    FormRoot,
    FormField,
  ],
  templateUrl: './center-dashboard.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './center-dashboard.component.css',
})
export class CenterDashboardComponent implements OnInit {
  private readonly dashboardStore = inject(DashboardStore);
  private readonly ws = inject(WebSocketService);
  private readonly auth = inject(AuthStore);

  readonly loading = this.dashboardStore.loading;
  readonly stats = this.dashboardStore.stats;
  readonly displayName = () => this.auth.fullName() || this.auth.username() || '';
  readonly dashboardFormState = signal({
    expirationDays: this.dashboardStore.expirationDays(),
    selectedMonth: this.dashboardStore.selectedMonth() as string | null
  });
  readonly dashboardForm = compatForm(this.dashboardFormState, (form) => {
    required(form.expirationDays);
  });

  constructor() {
    // Auto-refresh dashboard metrics on relevant WebSocket events.
    effect(() => {
      const evt = this.ws.lastEvent();
      this.dashboardStore.applyWsEvent(evt);
    });

    effect(() => {
      this.dashboardStore.setExpirationDays(this.dashboardFormState().expirationDays);
    });

    // Propagate month filter changes to the store
    effect(() => {
      const month = this.dashboardFormState().selectedMonth ?? null;
      this.dashboardStore.setSelectedMonth(month);
    });
  }

  ngOnInit(): void {
    this.dashboardStore.loadInitial();
  }
}
