export function StatusBadge({ value, tone }: { value: string; tone?: "neutral" | "good" | "warning" | "danger" }) {
  const normalizedTone = tone ?? toneForValue(value);
  return <span className={`status-badge ${normalizedTone}`}>{labelFor(value)}</span>;
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
