export function PageContainer({
  children,
  eyebrow,
  title,
  description
}: {
  children: React.ReactNode;
  eyebrow?: string;
  title: string;
  description?: string;
}) {
  return (
    <section className="page-container">
      <div className="page-heading">
        {eyebrow ? <p className="eyebrow">{eyebrow}</p> : null}
        <h2>{title}</h2>
        {description ? <p>{description}</p> : null}
      </div>
      {children}
    </section>
  );
}
