import { Component, signal, computed, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgSwitch, NgSwitchCase, NgSwitchDefault } from '@angular/common';
import { forkJoin } from 'rxjs';
import { ItemService } from '../../services/item.service';
import { ApiResponse, ItemDto, PagedItemsResponse } from '../../models/item.model';

@Component({
  selector: 'app-item-list',
  standalone: true,
  imports: [FormsModule, NgSwitch, NgSwitchCase, NgSwitchDefault],
  templateUrl: './item-list.component.html',
  styleUrl: './item-list.component.css'
})
export class ItemListComponent {
  private readonly itemService = inject(ItemService);

  items   = signal<ItemDto[]>([]);
  loading = signal<boolean>(false);
  error   = signal<string | null>(null);
  toast   = signal<string | null>(null);
  private toastTimer: ReturnType<typeof setTimeout> | null = null;

  // Pagination
  currentPage = signal<number>(0);
  totalItems  = signal<number>(0);
  totalPages  = signal<number>(0);
  pageSize    = signal<number>(20);
  readonly pageSizeOptions = [20, 50, 100];

  hasNextPage = computed(() => this.currentPage() < this.totalPages() - 1);
  hasPrevPage = computed(() => this.currentPage() > 0);

  showingFrom = computed(() =>
    this.totalItems() === 0 ? 0 : this.currentPage() * this.pageSize() + 1
  );
  showingTo = computed(() =>
    Math.min((this.currentPage() + 1) * this.pageSize(), this.totalItems())
  );

  visiblePages = computed<(number | '...')[]>(() => {
    const total   = this.totalPages();
    const current = this.currentPage();
    if (total <= 9) return Array.from({ length: total }, (_, i) => i);
    const start = Math.max(1, current - 2);
    const end   = Math.min(total - 2, current + 2);
    const pages: (number | '...')[] = [0];
    if (start > 1) pages.push('...');
    for (let i = start; i <= end; i++) pages.push(i);
    if (end < total - 2) pages.push('...');
    pages.push(total - 1);
    return pages;
  });

  // Search
  searchLocation    = '';
  hasSearched       = signal<boolean>(false);
  searchFormTouched = signal<boolean>(false);
  sortField         = signal<'item' | 'location' | 'createdAt' | 'updatedAt' | null>(null);
  sortDirection     = signal<'asc' | 'desc'>('asc');

  sortedItems = computed<ItemDto[]>(() => {
    const field = this.sortField();
    const dir   = this.sortDirection();
    if (!field) return this.items();
    return [...this.items()].sort((a, b) => {
      const va = a[field] ?? '';
      const vb = b[field] ?? '';
      const cmp = va.localeCompare(vb, undefined, { numeric: true, sensitivity: 'base' });
      return dir === 'asc' ? cmp : -cmp;
    });
  });

  sortIcon(field: 'item' | 'location' | 'createdAt' | 'updatedAt'): 'asc' | 'desc' | null {
    return this.sortField() === field ? this.sortDirection() : null;
  }

  toggleSort(field: 'item' | 'location' | 'createdAt' | 'updatedAt'): void {
    if (this.sortField() === field) {
      this.sortDirection.update(d => d === 'asc' ? 'desc' : 'asc');
    } else {
      this.sortField.set(field);
      this.sortDirection.set('asc');
    }
  }

  // Inline add
  addingInline     = signal<boolean>(false);
  inlineAddTouched = signal<boolean>(false);
  newItemName      = '';
  newItemLocation  = '';

  // Inline edit — editingItem stored as Signal<ItemDto | null> per requirement
  editingItem        = signal<ItemDto | null>(null);
  inlineEditLocation = '';
  inlineEditTouched  = signal<boolean>(false);

  // Bulk selection (keyed by item name)
  selectedItems = signal<Set<string>>(new Set());
  bulkLocation  = '';
  bulkTouched   = signal<boolean>(false);
  bulkLoading   = signal<boolean>(false);

  selectedCount = computed(() => this.selectedItems().size);

  allSelected = computed(() => {
    const items = this.sortedItems();
    return items.length > 0 && items.every(i => this.selectedItems().has(i.item));
  });

  someSelected = computed(() => {
    const items = this.sortedItems();
    const sel   = this.selectedItems();
    return items.some(i => sel.has(i.item)) && !this.allSelected();
  });

  isSelected(item: ItemDto): boolean { return this.selectedItems().has(item.item); }

  formatDate(iso: string): string {
    return new Date(iso).toLocaleString(undefined, {
      year: 'numeric', month: 'short', day: 'numeric',
      hour: '2-digit', minute: '2-digit'
    });
  }

  // --- Search ---

  onSearch(): void {
    this.searchFormTouched.set(true);
    if (!this.searchLocation.trim()) return;
    this.hasSearched.set(true);
    this.error.set(null);
    this.currentPage.set(0);
    this.sortField.set(null);
    this.cancelEdit();
    this.clearSelection();
    this.loadItems();
  }

  loadItems(): void {
    this.loading.set(true);
    this.itemService
      .getItemsByLocation(this.searchLocation, this.currentPage(), this.pageSize())
      .subscribe({
        next: (res: ApiResponse<PagedItemsResponse>) => {
          this.items.set(res.data.items);
          this.totalItems.set(res.data.total);
          this.totalPages.set(res.data.totalPages);
          this.loading.set(false);
        },
        error: () => {
          this.error.set('Failed to load items. Please check the location and try again.');
          this.loading.set(false);
        }
      });
  }

  onPageSizeChange(size: number): void {
    this.pageSize.set(size);
    this.currentPage.set(0);
    this.clearSelection();
    if (this.hasSearched()) this.loadItems();
  }

  nextPage(): void {
    if (this.hasNextPage()) { this.currentPage.update(p => p + 1); this.clearSelection(); this.loadItems(); }
  }

  prevPage(): void {
    if (this.hasPrevPage()) { this.currentPage.update(p => p - 1); this.clearSelection(); this.loadItems(); }
  }

  goToPage(page: number): void {
    if (page !== this.currentPage()) { this.currentPage.set(page); this.clearSelection(); this.loadItems(); }
  }

  // --- Inline add ---

  startAdd(): void {
    this.cancelEdit();
    this.clearSelection();
    this.newItemName = '';
    this.newItemLocation = '';
    this.inlineAddTouched.set(false);
    this.addingInline.set(true);
  }

  cancelAdd(): void {
    this.addingInline.set(false);
    this.inlineAddTouched.set(false);
    this.newItemName = '';
    this.newItemLocation = '';
  }

  saveAdd(): void {
    this.inlineAddTouched.set(true);
    if (!this.newItemName.trim() || !this.newItemLocation.trim()) return;
    this.error.set(null);
    this.itemService
      .upsertItem({ item: this.newItemName.trim(), location: this.newItemLocation.trim() })
      .subscribe({
        next: (res) => {
          this.showToast(res.message ?? 'Item saved.');
          this.cancelAdd();
          if (this.hasSearched()) this.loadItems();
        },
        error: () => this.error.set('Failed to save item. Please try again.')
      });
  }

  // --- Inline edit ---

  startEdit(dto: ItemDto): void {
    this.cancelAdd();
    this.editingItem.set(dto);           // stored in Signal<ItemDto> per requirement
    this.inlineEditLocation = dto.location;
    this.inlineEditTouched.set(false);
  }

  cancelEdit(): void {
    this.editingItem.set(null);
    this.inlineEditLocation = '';
    this.inlineEditTouched.set(false);
  }

  saveInlineEdit(): void {
    this.inlineEditTouched.set(true);
    const location = this.inlineEditLocation.trim();
    const dto      = this.editingItem();
    if (!location || !dto) return;
    this.error.set(null);
    this.itemService.upsertItem({ item: dto.item, location }).subscribe({
      next: (res) => {
        this.showToast(res.message ?? 'Item updated.');
        this.cancelEdit();
        this.loadItems();
      },
      error: () => this.error.set('Failed to update item. Please try again.')
    });
  }

  // --- Bulk ---

  toggleSelect(dto: ItemDto): void {
    this.selectedItems.update(set => {
      const next = new Set(set);
      next.has(dto.item) ? next.delete(dto.item) : next.add(dto.item);
      return next;
    });
  }

  toggleSelectAll(): void {
    this.selectedItems.set(
      this.allSelected() ? new Set() : new Set(this.sortedItems().map(i => i.item))
    );
  }

  clearSelection(): void {
    this.selectedItems.set(new Set());
    this.bulkLocation = '';
    this.bulkTouched.set(false);
  }

  saveBulkUpdate(): void {
    this.bulkTouched.set(true);
    const location = this.bulkLocation.trim();
    const items    = Array.from(this.selectedItems());
    if (!location || items.length === 0) return;

    this.bulkLoading.set(true);
    this.error.set(null);

    forkJoin(items.map(item => this.itemService.upsertItem({ item, location }))).subscribe({
      next: () => {
        this.showToast(`Moved ${items.length} item${items.length > 1 ? 's' : ''} to "${location}".`);
        this.clearSelection();
        this.loadItems();
        this.bulkLoading.set(false);
      },
      error: () => {
        this.error.set('Bulk update failed. Some items may not have been moved.');
        this.bulkLoading.set(false);
      }
    });
  }

  dismissError(): void { this.error.set(null); }

  private showToast(message: string): void {
    if (this.toastTimer) clearTimeout(this.toastTimer);
    this.toast.set(message);
    this.toastTimer = setTimeout(() => this.toast.set(null), 3000);
  }
}
