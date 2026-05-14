export function StatusBadge({ value, tone }: { value?: string | null; tone?: "neutral" | "good" | "warning" | "danger" }) {
  const displayValue = value || "UNKNOWN";
  const normalizedTone = tone ?? toneForValue(displayValue);
  return <span className={`status-badge ${normalizedTone}`}>{labelFor(displayValue)}</span>;
}

function toneForValue(value: string) {
  const normalized = value.toLowerCase();
  if (/(delivered|active|complete|strong|good|resolved)/.test(normalized)) {
    return "good";
  }
  if (/(due|pending|review|soon|medium)/.test(normalized)) {
    return "warning";
  }
  if (/(overdue|failed|expired|disputed|weak|cancelled)/.test(normalized)) {
    return "danger";
  }
  return "neutral";
}

function labelFor(value: string) {
  return value
    .replace(/-/g, "_")
    .toLowerCase()
    .split("_")
    .map((part) => part[0].toUpperCase() + part.slice(1))
    .join(" ");
}
