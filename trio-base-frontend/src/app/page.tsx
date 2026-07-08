"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Bar, BarChart, ResponsiveContainer, XAxis, YAxis } from "recharts";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { PageHeader } from "@/components/ui/PageHeader";
import { AppPage } from "@/components/layout/app-page";
import { useI18n } from "@/lib/i18n";
import {
  FileText,
  Users,
  Shield,
  Menu,
  Activity,
} from "lucide-react";

export default function Dashboard() {
  const router = useRouter();
  const { messages } = useI18n();
  const [authenticated, setAuthenticated] = useState(false);

  const chartData = messages.dashboard.months.map((name, index) => ({
    name,
    total: [1800, 2200, 2900, 2400, 3200, 3800, 4100, 3600, 4300, 3900, 4700, 5100][index],
  }));

  useEffect(() => {
    const token = localStorage.getItem("accessToken");
    if (!token) {
      router.replace("/login");
    } else {
      setAuthenticated(true);
    }
  }, [router]);

  if (!authenticated) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <p className="text-muted-foreground">{messages.common.loading}</p>
      </div>
    );
  }

  return (
    <AppPage>
      <PageHeader
        breadcrumb={messages.dashboard.breadcrumb}
        title={messages.dashboard.title}
        subtitle={messages.dashboard.subtitle}
        actions={(
          <Link href="/forms/new">
            <Button size="sm">{messages.dashboard.newForm}</Button>
          </Link>
        )}
      />

        <Tabs defaultValue="overview" className="space-y-4">
          <div className="w-full overflow-x-auto pb-2">
            <TabsList>
              <TabsTrigger value="overview">{messages.dashboard.tabs.overview}</TabsTrigger>
              <TabsTrigger value="analytics" disabled>
                {messages.dashboard.tabs.analytics}
              </TabsTrigger>
              <TabsTrigger value="reports" disabled>
                {messages.dashboard.tabs.reports}
              </TabsTrigger>
            </TabsList>
          </div>

          <TabsContent value="overview" className="space-y-4">
            {/* 4 Stat Cards */}
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <Card>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium">
                    {messages.dashboard.stats.forms}
                  </CardTitle>
                  <FileText className="h-4 w-4 text-muted-foreground" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">—</div>
                  <p className="text-xs text-muted-foreground">
                    {messages.dashboard.stats.formsHint}
                  </p>
                </CardContent>
              </Card>
              <Card>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium">
                    {messages.dashboard.stats.users}
                  </CardTitle>
                  <Users className="h-4 w-4 text-muted-foreground" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">—</div>
                  <p className="text-xs text-muted-foreground">
                    {messages.dashboard.stats.usersHint}
                  </p>
                </CardContent>
              </Card>
              <Card>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium">
                    {messages.dashboard.stats.roles}
                  </CardTitle>
                  <Shield className="h-4 w-4 text-muted-foreground" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">—</div>
                  <p className="text-xs text-muted-foreground">
                    {messages.dashboard.stats.rolesHint}
                  </p>
                </CardContent>
              </Card>
              <Card>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium">
                    {messages.dashboard.stats.menus}
                  </CardTitle>
                  <Menu className="h-4 w-4 text-muted-foreground" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">—</div>
                  <p className="text-xs text-muted-foreground">
                    {messages.dashboard.stats.menusHint}
                  </p>
                </CardContent>
              </Card>
            </div>

            {/* Chart + Recent Activity */}
            <div className="grid grid-cols-1 gap-4 lg:grid-cols-7">
              <Card className="col-span-1 lg:col-span-4">
                <CardHeader>
                  <CardTitle>{messages.dashboard.overview}</CardTitle>
                </CardHeader>
                <CardContent className="pl-2">
                  <ResponsiveContainer width="100%" height={350}>
                    <BarChart data={chartData}>
                      <XAxis
                        dataKey="name"
                        stroke="#888888"
                        fontSize={12}
                        tickLine={false}
                        axisLine={false}
                      />
                      <YAxis
                        stroke="#888888"
                        fontSize={12}
                        tickLine={false}
                        axisLine={false}
                        tickFormatter={(value: number) => `${value}`}
                      />
                      <Bar
                        dataKey="total"
                        fill="currentColor"
                        radius={[4, 4, 0, 0]}
                        className="fill-primary"
                      />
                    </BarChart>
                  </ResponsiveContainer>
                </CardContent>
              </Card>
              <Card className="col-span-1 lg:col-span-3">
                <CardHeader>
                  <CardTitle>{messages.dashboard.recentActivity}</CardTitle>
                  <CardDescription>
                    {messages.dashboard.recentActivityDescription}
                  </CardDescription>
                </CardHeader>
                <CardContent>
                  <div className="space-y-4">
                    {messages.dashboard.recentActivityItems.map((item, i) => (
                      <div
                        key={i}
                        className="flex items-center gap-4 border-b pb-3 last:border-0 last:pb-0"
                      >
                        <div className="flex size-9 items-center justify-center rounded-full bg-muted">
                          <Activity className="size-4 text-muted-foreground" />
                        </div>
                        <div className="min-w-0 flex-1">
                          <p className="text-sm font-medium truncate">
                            {item.action}
                          </p>
                          <p className="text-xs text-muted-foreground truncate">
                            {item.name}
                          </p>
                        </div>
                        <div className="text-xs text-muted-foreground shrink-0">
                          {item.time}
                        </div>
                      </div>
                    ))}
                  </div>
                </CardContent>
              </Card>
            </div>
          </TabsContent>
        </Tabs>
    </AppPage>
  );
}
