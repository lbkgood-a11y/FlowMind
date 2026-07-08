import { Shield, UserCheck, Users, type LucideIcon } from "lucide-react";
import type { UserStatus } from "./schema";

export const callTypes = new Map<UserStatus, string>([
  [
    "active",
    "bg-teal-100/30 text-teal-900 dark:text-teal-200 border-teal-200",
  ],
  ["inactive", "bg-neutral-300/40 border-neutral-300"],
]);

export const statusOptions = [
  { label: "Active", value: "active" },
  { label: "Inactive", value: "inactive" },
] as const;

// Map our backend role codes to display labels
export const roleLabels: Record<string, string> = {
  ADMIN: "Admin",
  USER: "User",
  SUPERADMIN: "Superadmin",
  MANAGER: "Manager",
  FINANCE: "Finance",
  CASHIER: "Cashier",
};

// Roles for filter dropdown
export const roleFilterOptions = Object.entries(roleLabels).map(
  ([value, label]) => ({ label, value }),
);

// Role icons (for display)
export const roleIcons: Record<string, LucideIcon> = {
  SUPERADMIN: Shield,
  ADMIN: UserCheck,
  MANAGER: Users,
  USER: Users,
};
