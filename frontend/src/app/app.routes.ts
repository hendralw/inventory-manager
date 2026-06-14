import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadChildren: () =>
      import('./features/inventory/inventory.routes').then(m => m.INVENTORY_ROUTES)
  }
];
