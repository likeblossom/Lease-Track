export const dashboardKpis = [
  { label: "Active leases", value: "148", trend: "12 renewals due this quarter", tone: "good" as const },
  { label: "Compliance deadlines", value: "23", trend: "5 due in the next 7 days", tone: "warning" as const },
  { label: "Open notices", value: "41", trend: "8 awaiting delivery proof", tone: "neutral" as const },
  { label: "Evidence gaps", value: "6", trend: "Needs manager review", tone: "danger" as const }
];

export const upcomingDeadlines = [
  {
    id: "deadline-1",
    matter: "Rent increase notice response window",
    property: "Maple Court - Unit 4B",
    owner: "Avery Manager",
    due: "May 17",
    status: "due_soon"
  },
  {
    id: "deadline-2",
    matter: "Lease renewal decision",
    property: "Riverside Lofts - Unit 1202",
    owner: "Nadia Patel",
    due: "May 21",
    status: "pending_review"
  },
  {
    id: "deadline-3",
    matter: "Delivery confirmation follow-up",
    property: "Northline House - Unit 8",
    owner: "System",
    due: "Overdue",
    status: "overdue"
  }
];

export const recentActivity = [
  {
    id: "activity-1",
    title: "Notice delivered",
    description: "Registered mail delivery was confirmed for Maple Court - Unit 4B.",
    timestamp: "Today, 9:42 AM",
    actor: "Tracking service"
  },
  {
    id: "activity-2",
    title: "Evidence uploaded",
    description: "Carrier receipt attached to notice package N-1048.",
    timestamp: "Yesterday, 4:18 PM",
    actor: "Avery Manager"
  },
  {
    id: "activity-3",
    title: "Lease created",
    description: "New lease record opened for Riverside Lofts - Unit 1202.",
    timestamp: "Yesterday, 11:03 AM",
    actor: "Nadia Patel"
  }
];

export const complianceAlerts = [
  { id: "alert-1", title: "Missing proof of delivery", detail: "3 notices have no evidence document attached.", status: "pending_review" },
  { id: "alert-2", title: "Deadline breached", detail: "Northline House requires immediate follow-up.", status: "overdue" },
  { id: "alert-3", title: "Renewal package ready", detail: "5 lease renewal files are complete.", status: "complete" }
];

export const evidenceSummary = [
  { label: "Strong evidence", value: 72 },
  { label: "Medium evidence", value: 18 },
  { label: "Weak evidence", value: 10 }
];
