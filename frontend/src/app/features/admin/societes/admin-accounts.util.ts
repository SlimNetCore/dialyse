import {AdminAccount, UpdateAdminAccount} from '../../../core/api/societe-admin-api.service';

export type AdminFormModel = {
  centerId: string;
  centerIds: string[];
  username: string;
  fullName: string;
  email: string;
  password: string;
};

export const emptyAdminForm = (): AdminFormModel => ({
  centerId: '', centerIds: [], username: '', fullName: '', email: '', password: '',
});

/** Formulaire prérempli avec un administrateur existant (nom, e-mail, centres actuels). */
export function adminToForm(account: AdminAccount): AdminFormModel {
  return {
    ...emptyAdminForm(),
    username: account.username,
    fullName: account.fullName ?? '',
    email: account.email ?? '',
    centerIds: account.centres.map((c) => c.id),
  };
}

export function toUpdatePayload(model: AdminFormModel): UpdateAdminAccount {
  return {
    fullName: model.fullName.trim() || undefined,
    email: model.email.trim() || undefined,
    centerIds: [...model.centerIds],
  };
}

/** Noms des centres d'un administrateur, séparés par une virgule. */
export function centreNames(account: AdminAccount): string {
  return account.centres.map((c) => c.name).join(', ');
}
