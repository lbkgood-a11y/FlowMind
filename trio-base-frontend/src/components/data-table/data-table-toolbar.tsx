import { X } from "lucide-react";
import { type Table } from "@tanstack/react-table";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

interface FilterOption {
  label: string;
  value: string;
  icon?: React.ComponentType<{ className?: string }>;
}

interface FilterConfig {
  columnId: string;
  title: string;
  options: FilterOption[];
}

interface DataTableToolbarProps<TData> {
  table: Table<TData>;
  searchPlaceholder?: string;
  searchKey?: string;
  filters?: FilterConfig[];
  children?: React.ReactNode;
  resetLabel?: string;
}

export function DataTableToolbar<TData>({
  table,
  searchPlaceholder = "Filter...",
  searchKey,
  filters = [],
  children,
  resetLabel = "Reset",
}: DataTableToolbarProps<TData>) {
  const isFiltered =
    (searchKey && table.getColumn(searchKey)?.getFilterValue() !== undefined) ||
    filters.some(
      (f) => table.getColumn(f.columnId)?.getFilterValue() !== undefined,
    );

  return (
    <div className="flex flex-wrap items-center justify-between gap-2">
      <div className="flex flex-1 flex-wrap items-center gap-2">
        {searchKey && (
          <Input
            placeholder={searchPlaceholder}
            value={
              (table.getColumn(searchKey)?.getFilterValue() as string) ?? ""
            }
            onChange={(event) =>
              table.getColumn(searchKey)?.setFilterValue(event.target.value)
            }
            className="h-8 w-[150px] lg:w-[250px]"
          />
        )}
        {filters.map((filter) => {
          const column = table.getColumn(filter.columnId);
          if (!column) return null;
          const selectedValues = new Set(
            (column.getFilterValue() as string[]) ?? [],
          );

          return (
            <DropdownMenu key={filter.columnId}>
              <DropdownMenuTrigger
                render={
                  <Button variant="outline" size="sm" className="h-8">
                    {filter.title}
                    {selectedValues.size > 0 && (
                      <span className="ms-1 rounded-sm bg-primary px-1 py-0.5 text-[10px] text-primary-foreground">
                        {selectedValues.size}
                      </span>
                    )}
                  </Button>
                }
              />
              <DropdownMenuContent align="start" className="w-48">
                {filter.options.map((option) => {
                  const isSelected = selectedValues.has(option.value);
                  return (
                    <DropdownMenuCheckboxItem
                      key={option.value}
                      checked={isSelected}
                      onCheckedChange={() => {
                        if (isSelected) {
                          selectedValues.delete(option.value);
                        } else {
                          selectedValues.add(option.value);
                        }
                        column?.setFilterValue(
                          selectedValues.size > 0
                            ? Array.from(selectedValues)
                            : undefined,
                        );
                      }}
                    >
                      {option.icon && (
                        <option.icon className="mr-2 size-4 text-muted-foreground" />
                      )}
                      {option.label}
                    </DropdownMenuCheckboxItem>
                  );
                })}
              </DropdownMenuContent>
            </DropdownMenu>
          );
        })}
        {isFiltered && (
          <Button
            variant="ghost"
            size="sm"
            className="h-8 px-2 lg:px-3"
            onClick={() => {
              table.resetColumnFilters();
              table.resetGlobalFilter();
            }}
          >
{resetLabel}
            <X />
          </Button>
        )}
      </div>
      {children}
    </div>
  );
}
