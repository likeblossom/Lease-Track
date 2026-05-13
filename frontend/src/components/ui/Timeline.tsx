import { CircleDot } from "lucide-react";

export interface TimelineItem {
  id: string;
  title: string;
  description: string;
  timestamp: string;
  actor?: string;
}

export function Timeline({ items, emptyMessage = "No activity yet." }: { items: TimelineItem[]; emptyMessage?: string }) {
  if (items.length === 0) {
    return <p className="muted">{emptyMessage}</p>;
  }

  return (
    <ol className="timeline">
      {items.map((item) => (
        <li key={item.id}>
          <CircleDot aria-hidden="true" size={15} />
          <div>
            <strong>{item.title}</strong>
            <p>{item.description}</p>
            <span>
              {item.actor ? `${item.actor} · ` : ""}
              {item.timestamp}
            </span>
          </div>
        </li>
      ))}
    </ol>
  );
}
