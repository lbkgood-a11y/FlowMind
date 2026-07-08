import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { type Table } from "@tanstack/react-table";
import {
  ChevronLeft,
  ChevronRight,
  ChevronsLeft,
  ChevronsRight,
} from "lucide-react";

interface DataTablePaginationProps<TData> {
  table: Table<TData>;
  className?: string;
  pageInfo?: string;
  noResults?: string;
}

export function DataTablePagination<TData>({
  table,
  className,
  pageInfo = "{from}-{to} of {total}",
  noResults = "No results",
}: DataTablePaginationProps<TData>) {
  const { pageIndex, pageSize } = table.getState().pagination;
  const total = table.getFilteredRowModel().rows.length;
  const pageCount = table.getPageCount();
  const from = pageIndex * pageSize + 1;
  const to = Math.min((pageIndex + 1) * pageSize, total);

  return (
    <div
      className={cn(
        "flex items-center justify-between px-2",
        className,
      )}
    >
      <div className="text-xs text-muted-foreground">
        {total === 0
          ? noResults
          : pageInfo
              .replace("{from}", String(from))
              .replace("{to}", String(to))
              .replace("{total}", String(total))}
      </div>
      <div className="flex items-center gap-2">
        <Button
          variant="outline"
          size="icon-xs"
          onClick={() => table.setPageIndex(0)}
          disabled={!table.getCanPreviousPage()}
        >
          <ChevronsLeft />
        </Button>
        <Button
          variant="outline"
          size="icon-xs"
          onClick={() => table.previousPage()}
          disabled={!table.getCanPreviousPage()}
        >
          <ChevronLeft />
        </Button>
        <span className="text-xs text-muted-foreground min-w-[4rem] text-center">
          {pageIndex + 1}/{pageCount || 1}
        </span>
        <Button
          variant="outline"
          size="icon-xs"
          onClick={() => table.nextPage()}
          disabled={!table.getCanNextPage()}
        >
          <ChevronRight />
        </Button>
        <Button
          variant="outline"
          size="icon-xs"
          onClick={() => table.setPageIndex(pageCount - 1)}
          disabled={!table.getCanNextPage()}
        >
          <ChevronsRight />
        </Button>
      </div>
    </div>
  );
}
