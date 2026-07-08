"use client";

import { z } from "zod";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { toast } from "sonner";
import { useI18n } from "@/lib/i18n";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { PasswordInput } from "@/components/ui/password-input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { roleLabels } from "../data/data";
import { type User } from "../data/schema";

const formSchema = z
  .object({
    username: z.string().min(1, "Username is required."),
    email: z.string().optional(),
    phone: z.string().optional(),
    password: z.string().optional(),
    confirmPassword: z.string().optional(),
    role: z.string().min(1, "Role is required."),
    isEdit: z.boolean(),
  })
  .refine(
    (data) => {
      if (data.isEdit && !data.password) return true;
      return (data.password?.length ?? 0) > 0;
    },
    {
      message: "Password is required.",
      path: ["password"],
    },
  )
  .refine(
    ({ isEdit, password }) => {
      if (isEdit && !password) return true;
      return (password?.length ?? 0) >= 8;
    },
    {
      message: "Password must be at least 8 characters.",
      path: ["password"],
    },
  )
  .refine(
    ({ isEdit, password, confirmPassword }) => {
      if (isEdit && !password) return true;
      return password === confirmPassword;
    },
    {
      message: "Passwords don't match.",
      path: ["confirmPassword"],
    },
  );

type UserForm = z.infer<typeof formSchema>;

type UsersActionDialogProps = {
  currentRow?: User;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess?: () => void;
};

export function UsersActionDialog({
  currentRow,
  open,
  onOpenChange,
  onSuccess,
}: UsersActionDialogProps) {
  const isEdit = !!currentRow;
  const { messages } = useI18n();
  const m = messages.pages.users;
  const form = useForm<UserForm>({
    resolver: zodResolver(formSchema),
    defaultValues: isEdit
      ? {
          username: currentRow.username,
          email: currentRow.email || "",
          phone: currentRow.phone || "",
          password: "",
          confirmPassword: "",
          role: currentRow.roles?.[0] || "USER",
          isEdit,
        }
      : {
          username: "",
          email: "",
          phone: "",
          password: "",
          confirmPassword: "",
          role: "USER",
          isEdit,
        },
  });

  const onSubmit = async (values: UserForm) => {
    try {
      if (isEdit && currentRow) {
        toast.success(m.userUpdated);
      } else {
        const params = new URLSearchParams({
          username: values.username,
          password: values.password || "",
        });
        if (values.email) params.append("email", values.email);
        if (values.phone) params.append("phone", values.phone);
        const res = await fetch(
          `/api/v1/auth/register?${params.toString()}`,
          { method: "POST" },
        );
        if (!res.ok) {
          const data = await res.json();
          throw new Error(data.message || "Failed to create user");
        }
        toast.success(m.userCreated);
      }
      form.reset();
      onOpenChange(false);
      onSuccess?.();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Operation failed");
    }
  };

  return (
    <Dialog
      open={open}
      onOpenChange={(state) => {
        form.reset();
        onOpenChange(state);
      }}
    >
      <DialogContent className="sm:max-w-lg">
        <DialogHeader className="text-start">
          <DialogTitle>
            {isEdit ? m.editUser : m.addNewUser}
          </DialogTitle>
          <DialogDescription>
            {isEdit ? m.editUserDesc : m.createUserDesc}
          </DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form
            id="user-form"
            onSubmit={form.handleSubmit(onSubmit)}
            className="space-y-4"
          >
            <FormField
              control={form.control}
              name="username"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{m.formLabels.username}</FormLabel>
                  <FormControl>
                    <Input placeholder="Username" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="email"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{m.formLabels.email}</FormLabel>
                  <FormControl>
                    <Input
                      placeholder={m.placeholders.email}
                      type="email"
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="phone"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{m.formLabels.phone}</FormLabel>
                  <FormControl>
                    <Input placeholder={m.placeholders.phone} type="tel" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="role"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{m.formLabels.role}</FormLabel>
                  <FormControl>
                    <Select
                      value={field.value}
                      onValueChange={field.onChange}
                    >
                      <SelectTrigger>
                        <SelectValue placeholder="Select a role" />
                      </SelectTrigger>
                      <SelectContent>
                        {Object.entries(roleLabels).map(([value, label]) => (
                          <SelectItem key={value} value={value}>
                            {label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="password"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    {isEdit ? m.formLabels.newPassword : m.formLabels.password}
                  </FormLabel>
                  <FormControl>
                    <PasswordInput
                      placeholder={
                        isEdit ? m.placeholders.passwordKeep : m.placeholders.password
                      }
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="confirmPassword"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{m.formLabels.confirmPassword}</FormLabel>
                  <FormControl>
                    <PasswordInput
                      placeholder={m.placeholders.confirmPassword}
                      {...field}
                      disabled={!form.watch("password")}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </form>
        </Form>
        <DialogFooter>
          <Button type="submit" form="user-form">
            {isEdit ? m.saveChanges : m.createUserLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
