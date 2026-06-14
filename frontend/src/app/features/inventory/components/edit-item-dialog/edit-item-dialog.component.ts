import { Component, Inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';

/** Data passed into the dialog when opened. */
export interface EditItemDialogData {
  item: string;
  currentLocation: string;
}

/** Result passed back to the parent on save; undefined if cancelled. */
export interface EditItemDialogResult {
  item: string;
  location: string;
}

/**
 * Modal for moving an item to a new location.
 * Data flows in via DIALOG_DATA; result flows out via dialogRef.close(result).
 */
@Component({
  selector: 'app-edit-item-dialog',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './edit-item-dialog.component.html',
  styleUrl: './edit-item-dialog.component.css'
})
export class EditItemDialogComponent {
  // Plain string — no need for a signal since no computed values depend on it.
  newLocation: string;
  saveTouched = signal<boolean>(false);

  constructor(
    public dialogRef: DialogRef<EditItemDialogResult>,
    @Inject(DIALOG_DATA) public data: EditItemDialogData
  ) {
    // Set inside constructor — DIALOG_DATA is not available at field-initializer time.
    this.newLocation = data.currentLocation;
  }

  save(): void {
    this.saveTouched.set(true);
    const location = this.newLocation.trim();
    if (!location) return;
    this.dialogRef.close({ item: this.data.item, location });
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
