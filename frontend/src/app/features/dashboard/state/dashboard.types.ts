export type DashboardStats = {
  patientCount: number;
  pecCree: number;
  pecValidee: number;
  pecExpiring: number;
  attestationTotal: number;
  attestationExpiring: number;
};

export const EMPTY_DASHBOARD_STATS: DashboardStats = {
  patientCount: 0,
  pecCree: 0,
  pecValidee: 0,
  pecExpiring: 0,
  attestationTotal: 0,
  attestationExpiring: 0
};

