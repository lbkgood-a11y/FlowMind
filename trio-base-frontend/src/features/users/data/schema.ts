import { z } from "zod";

export const userSchema = z.object({
  id: z.string(),
  username: z.string(),
  email: z.string().nullable().optional(),
  phone: z.string().nullable().optional(),
  status: z.number(), // 1=active, 0=inactive
  roles: z.array(z.string()),
  createdAt: z.string().nullable().optional(),
});

export type User = z.infer<typeof userSchema>;

export type UserStatus = "active" | "inactive";
export type UserRole = string;
