import {ChangeDetectionStrategy, Component, inject, OnInit, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {RouterLink} from '@angular/router';
import {MatButtonModule} from '@angular/material/button';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import {MatSnackBar} from '@angular/material/snack-bar';
import {TranslateModule} from '@ngx-translate/core';
import {TranslateService} from '@ngx-translate/core';
import {AppShellStore} from '../../core/state/app-shell.store';
import {BackendApiService, SeanceCalendarResponse} from '../../core/api/backend-api.service';

@Component({
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    TranslateModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './seance-calendar-center.component.html',
  styleUrl: './seance-calendar-center.component.css',
})
export class SeanceCalendarCenterComponent implements OnInit {
  protected readonly month = signal(this.currentMonthIso());
  protected readonly loading = signal(false);
  protected readonly holidays = signal<SeanceCalendarResponse['holidays']>([]);
  protected readonly closures = signal<SeanceCalendarResponse['closures']>([]);
  protected readonly newHolidayDate = signal(this.todayIsoDate());
  protected readonly newHolidayLabel = signal('');
  protected readonly newClosureDate = signal(this.todayIsoDate());
  protected readonly newClosureReason = signal('');

  private readonly api = inject(BackendApiService);
  private readonly appShell = inject(AppShellStore);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);

  ngOnInit(): void {
    this.load();
  }

  protected onMonthInput(event: Event): void {
    this.month.set((event.target as HTMLInputElement | null)?.value ?? this.currentMonthIso());
  }

  protected onHolidayDateInput(event: Event): void {
    this.newHolidayDate.set((event.target as HTMLInputElement | null)?.value ?? this.todayIsoDate());
  }

  protected onHolidayLabelInput(event: Event): void {
    this.newHolidayLabel.set((event.target as HTMLInputElement | null)?.value ?? '');
  }

  protected onClosureDateInput(event: Event): void {
    this.newClosureDate.set((event.target as HTMLInputElement | null)?.value ?? this.todayIsoDate());
  }

  protected onClosureReasonInput(event: Event): void {
    this.newClosureReason.set((event.target as HTMLInputElement | null)?.value ?? '');
  }

  protected load(): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId) return;
    const [yearText, monthText] = this.month().split('-');
    const year = Number(yearText);
    const month = Number(monthText);
    if (!Number.isInteger(year) || !Number.isInteger(month)) return;

    this.loading.set(true);
    this.api.getSeanceCalendar(centerId, year, month).subscribe({
      next: (calendar) => {
        this.loading.set(false);
        this.holidays.set(calendar.holidays ?? []);
        this.closures.set(calendar.closures ?? []);
      },
      error: () => {
        this.loading.set(false);
        this.holidays.set([]);
        this.closures.set([]);
        this.snackBar.open(
          this.translate.instant('SEANCES.CALENDAR_LOAD_ERROR'),
          this.translate.instant('COMMON.OK'),
          {duration: 3000},
        );
      }
    });
  }

  protected addHoliday(): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.newHolidayDate()) return;
    this.api.addSeanceHoliday(centerId, this.newHolidayDate(), this.newHolidayLabel()).subscribe({
      next: () => {
        this.newHolidayLabel.set('');
        this.load();
      },
      error: () => this.snackBar.open(
        this.translate.instant('SEANCES.HOLIDAY_ADD_ERROR'),
        this.translate.instant('COMMON.OK'),
        {duration: 3000},
      )
    });
  }

  protected deleteHoliday(id: string): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId) return;
    this.api.deleteSeanceHoliday(centerId, id).subscribe({
      next: () => this.load(),
      error: () => this.snackBar.open(
        this.translate.instant('SEANCES.HOLIDAY_DELETE_ERROR'),
        this.translate.instant('COMMON.OK'),
        {duration: 3000},
      )
    });
  }

  protected addClosure(): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId || !this.newClosureDate()) return;
    this.api.addSeanceClosure(centerId, this.newClosureDate(), this.newClosureReason()).subscribe({
      next: () => {
        this.newClosureReason.set('');
        this.load();
      },
      error: () => this.snackBar.open(
        this.translate.instant('SEANCES.CLOSURE_ADD_ERROR'),
        this.translate.instant('COMMON.OK'),
        {duration: 3000},
      )
    });
  }

  protected deleteClosure(id: string): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId) return;
    this.api.deleteSeanceClosure(centerId, id).subscribe({
      next: () => this.load(),
      error: () => this.snackBar.open(
        this.translate.instant('SEANCES.CLOSURE_DELETE_ERROR'),
        this.translate.instant('COMMON.OK'),
        {duration: 3000},
      )
    });
  }

  protected exportDashboard(format: 'csv' | 'pdf' | 'xlsx'): void {
    const centerId = this.appShell.currentCenterId();
    if (!centerId) return;
    const [yearText, monthText] = this.month().split('-');
    const year = Number(yearText);
    const month = Number(monthText);
    this.api.exportSeanceDashboard(centerId, year, month, format).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `seances-dashboard-${year}-${String(month).padStart(2, '0')}.${format}`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: () => this.snackBar.open(
        this.translate.instant('COMMON.EXPORT_ERROR'),
        this.translate.instant('COMMON.OK'),
        {duration: 3000},
      )
    });
  }

  private todayIsoDate(): string {
    return new Date().toISOString().slice(0, 10);
  }

  private currentMonthIso(): string {
    return this.todayIsoDate().slice(0, 7);
  }
}

