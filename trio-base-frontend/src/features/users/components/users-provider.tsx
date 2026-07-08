"use client";

import React, { useState } from "react";
import type { User } from "../data/schema";

export type UsersDialogType = "invite" | "add" | "edit" | "delete" | "view" | "enable" | "disable" | null;

type UsersContextType = {
  open: UsersDialogType;
  setOpen: (str: UsersDialogType) => void;
  currentRow: User | null;
  setCurrentRow: React.Dispatch<React.SetStateAction<User | null>>;
};

const UsersContext = React.createContext<UsersContextType | null>(null);

export function UsersProvider({ children }: { children: React.ReactNode }) {
  const [open, setOpen] = useState<UsersDialogType>(null);
  const [currentRow, setCurrentRow] = useState<User | null>(null);

  return (
    <UsersContext value={{ open, setOpen, currentRow, setCurrentRow }}>
      {children}
    </UsersContext>
  );
}

export function useUsers() {
  const usersContext = React.useContext(UsersContext);
  if (!usersContext) {
    throw new Error("useUsers has to be used within <UsersContext>");
  }
  return usersContext;
}
