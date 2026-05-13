import { Building2, FileText, Home, Plus, Search } from "lucide-react";
import { FormEvent, useMemo, useState } from "react";
import {
  propertyProvinceText,
  type PropertyResponse,
  type PropertySummaryResponse,
  type PropertyUnitResponse,
  type UnitStatus
} from "../../api/client";
import { DataTable, type DataTableColumn } from "../../components/ui/DataTable";
import { KpiCard } from "../../components/ui/KpiCard";
import { PageContainer } from "../../components/ui/PageContainer";
import { StatusBadge } from "../../components/ui/StatusBadge";
import { Timeline } from "../../components/ui/Timeline";

type PropertyTab = "overview" | "units" | "documents" | "activity";

export function PropertiesPage({
  properties,
  selectedProperty,
  onCreateProperty,
  onCreateUnit,
  onRefresh,
  onSearch,
  onSelectProperty
}: {
  properties: PropertySummaryResponse[];
  selectedProperty: PropertyResponse | null;
  onCreateProperty: (request: CreatePropertyFormValue) => Promise<void>;
  onCreateUnit: (propertyId: string, request: CreateUnitFormValue) => Promise<void>;
  onRefresh: () => Promise<void>;
  onSearch: (query: string) => Promise<void>;
  onSelectProperty: (propertyId: string) => Promise<void>;
}) {
  const [query, setQuery] = useState("");
  const [activeTab, setActiveTab] = useState<PropertyTab>("overview");
  const [isCreatingProperty, setIsCreatingProperty] = useState(false);
  const [isCreatingUnit, setIsCreatingUnit] = useState(false);
  const [propertyForm, setPropertyForm] = useState({
    addressLine1: "",
    addressLine2: "",
    city: "",
    province: "",
    country: "Canada",
    notes: ""
  });
  const [postalCode, setPostalCode] = useState("");
  const postalCodeIsValid = CANADIAN_POSTAL_CODE_PATTERN.test(postalCode);
  const showPostalCodeError = postalCode.length > 0 && !postalCodeIsValid;
  const propertyFormIsComplete =
    propertyForm.addressLine1.trim() !== "" &&
    propertyForm.city.trim() !== "" &&
    propertyForm.province.trim() !== "" &&
    propertyForm.country.trim() !== "" &&
    propertyForm.notes.trim() !== "" &&
    postalCodeIsValid;
  const propertyColumns = useMemo(() => propertyTableColumns(onSelectProperty), [onSelectProperty]);

  async function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await onSearch(query);
  }

  async function submitProperty(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const formattedPostalCode = formatCanadianPostalCode(String(form.get("postalCode") ?? ""));
    if (!CANADIAN_POSTAL_CODE_PATTERN.test(formattedPostalCode)) {
      event.currentTarget.reportValidity();
      return;
    }
    await onCreateProperty({
      name: derivedPropertyName(form),
      addressLine1: propertyForm.addressLine1.trim(),
      addressLine2: optionalString(propertyForm.addressLine2),
      city: propertyForm.city.trim(),
      province: propertyForm.province.trim(),
      postalCode: formattedPostalCode,
      country: propertyForm.country.trim(),
      notes: propertyForm.notes.trim()
    });
    event.currentTarget.reset();
    setPropertyForm(emptyPropertyForm());
    setPostalCode("");
    setIsCreatingProperty(false);
  }

  async function submitUnit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedProperty) {
      return;
    }
    const form = new FormData(event.currentTarget);
    await onCreateUnit(selectedProperty.id, {
      unitLabel: String(form.get("unitLabel") ?? ""),
      status: String(form.get("status") ?? "VACANT") as UnitStatus,
      bedrooms: optionalNumber(form.get("bedrooms")),
      bathrooms: optionalNumber(form.get("bathrooms")),
      squareFeet: optionalNumber(form.get("squareFeet")),
      currentTenantNames: optionalString(form.get("currentTenantNames")),
      currentRentCents: currencyToCents(form.get("currentRent"))
    });
    event.currentTarget.reset();
    setIsCreatingUnit(false);
  }

  return (
    <PageContainer
      eyebrow="Properties"
      title="Property management"
      description="Track buildings, units, occupancy, and property-level operational activity."
    >
      <section className="property-toolbar" id="properties">
        <form className="search-control" onSubmit={submitSearch}>
          <Search aria-hidden="true" size={16} />
          <input
            aria-label="Search properties"
            placeholder="Search name, address, city, province, postal code"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
          <button className="secondary-action compact" type="submit">Search</button>
        </form>
        <button className="secondary-action compact" type="button" onClick={onRefresh}>Refresh</button>
      </section>

      <section className="properties-layout">
        <article className="workspace-panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Portfolio</p>
              <h2>Property list</h2>
            </div>
            <button
              className="primary-action compact-action"
              type="button"
              onClick={() => {
                setIsCreatingProperty((current) => !current);
                setPropertyForm(emptyPropertyForm());
                setPostalCode("");
              }}
            >
              <Plus aria-hidden="true" size={16} />
              <span>Property</span>
            </button>
          </div>
          {isCreatingProperty ? (
            <form className="property-form-grid property-list-create-form" onSubmit={submitProperty}>
              <Field
                label="Address line 1"
                name="addressLine1"
                value={propertyForm.addressLine1}
                onChange={(value) => setPropertyForm((current) => ({ ...current, addressLine1: value }))}
                required
              />
              <Field
                label="Address line 2"
                name="addressLine2"
                value={propertyForm.addressLine2}
                onChange={(value) => setPropertyForm((current) => ({ ...current, addressLine2: value }))}
              />
              <Field
                label="City"
                name="city"
                value={propertyForm.city}
                onChange={(value) => setPropertyForm((current) => ({ ...current, city: value }))}
                required
              />
              <Field
                label="Province"
                name="province"
                value={propertyForm.province}
                onChange={(value) => setPropertyForm((current) => ({ ...current, province: value }))}
                required
              />
              <PostalCodeField value={postalCode} showError={showPostalCodeError} onChange={setPostalCode} />
              <Field
                label="Country"
                name="country"
                value={propertyForm.country}
                onChange={(value) => setPropertyForm((current) => ({ ...current, country: value }))}
                required
              />
              <label className="field property-form-notes">
                <span>Notes</span>
                <textarea
                  name="notes"
                  rows={3}
                  value={propertyForm.notes}
                  onChange={(event) => setPropertyForm((current) => ({ ...current, notes: event.target.value }))}
                  required
                />
              </label>
              <button className="primary-action property-form-submit" type="submit" disabled={!propertyFormIsComplete}>
                Create property
              </button>
            </form>
          ) : null}
          <DataTable columns={propertyColumns} rows={properties} getRowKey={(row) => row.id} emptyMessage="No properties found." />
        </article>

        <article className="workspace-panel property-detail-panel">
          {selectedProperty ? (
            <PropertyDetail
              activeTab={activeTab}
              isCreatingUnit={isCreatingUnit}
              property={selectedProperty}
              onSetActiveTab={setActiveTab}
              onSubmitUnit={submitUnit}
              onToggleCreateUnit={() => setIsCreatingUnit((current) => !current)}
            />
          ) : (
            <div className="empty-state compact">
              <Building2 aria-hidden="true" size={28} />
              <h2>Select a property</h2>
              <p>Property metrics, units, and activity will appear here.</p>
            </div>
          )}
        </article>
      </section>
    </PageContainer>
  );
}

function PropertyDetail({
  activeTab,
  isCreatingUnit,
  property,
  onSetActiveTab,
  onSubmitUnit,
  onToggleCreateUnit
}: {
  activeTab: PropertyTab;
  isCreatingUnit: boolean;
  property: PropertyResponse;
  onSetActiveTab: (tab: PropertyTab) => void;
  onSubmitUnit: (event: FormEvent<HTMLFormElement>) => Promise<void>;
  onToggleCreateUnit: () => void;
}) {
  const activityItems = [
    {
      id: `property-created-${property.id}`,
      title: "Property created",
      description: `${property.name} was added to the portfolio.`,
      timestamp: formatDate(property.createdAt),
      actor: "Workspace"
    },
    ...property.units.slice(0, 5).map((unit) => ({
      id: `unit-${unit.id}`,
      title: "Unit registered",
      description: `Unit ${unit.unitLabel} is tracked as ${labelFor(unit.status)}.`,
      timestamp: formatDate(unit.createdAt),
      actor: "Property module"
    }))
  ];

  return (
    <>
      <div className="panel-heading">
        <div>
          <p className="eyebrow">Property record</p>
          <h2>{property.name}</h2>
          <p className="muted">{formatPropertyAddress(property)}</p>
        </div>
        <StatusBadge value={`${property.occupancyRate}%_occupied`} tone={property.occupancyRate >= 90 ? "good" : "neutral"} />
      </div>

      <div className="tabs" role="tablist" aria-label="Property detail sections">
        {(["overview", "units", "documents", "activity"] as PropertyTab[]).map((tab) => (
          <button
            className={activeTab === tab ? "active" : ""}
            key={tab}
            type="button"
            onClick={() => onSetActiveTab(tab)}
          >
            {labelFor(tab)}
          </button>
        ))}
      </div>

      {activeTab === "overview" ? (
        <section className="property-overview-grid">
          <KpiCard icon={Home} label="Units" value={String(property.unitCount)} trend={`${property.occupiedUnitCount} occupied`} />
          <KpiCard icon={Building2} label="Occupancy" value={`${property.occupancyRate}%`} trend={`${property.vacantUnitCount} vacant`} tone={property.occupancyRate >= 90 ? "good" : "neutral"} />
          <KpiCard icon={FileText} label="Active notices" value={String(property.activeNoticeCount)} trend="Linked notice workflow" tone={property.activeNoticeCount > 0 ? "warning" : "good"} />
          <div className="summary-block">
            <h3>Alerts</h3>
            {property.vacantUnitCount > 0 ? <p>{property.vacantUnitCount} vacant unit(s) require leasing follow-up.</p> : <p>No property alerts.</p>}
          </div>
        </section>
      ) : null}

      {activeTab === "units" ? (
        <section className="property-units-section">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Unit management</p>
              <h2>Units</h2>
            </div>
            <button className="secondary-action compact" type="button" onClick={onToggleCreateUnit}>
              <Plus aria-hidden="true" size={16} />
              <span>Unit</span>
            </button>
          </div>
          {isCreatingUnit ? <UnitForm onSubmit={onSubmitUnit} /> : null}
          <DataTable columns={unitTableColumns} rows={property.units} getRowKey={(row) => row.id} emptyMessage="No units found." />
        </section>
      ) : null}

      {activeTab === "documents" ? (
        <div className="summary-block">
          <h3>Documents</h3>
          <p>Property-level document storage will connect to the evidence vault storage abstraction in Phase 6.</p>
        </div>
      ) : null}

      {activeTab === "activity" ? <Timeline items={activityItems} /> : null}
    </>
  );
}

function UnitForm({ onSubmit }: { onSubmit: (event: FormEvent<HTMLFormElement>) => Promise<void> }) {
  return (
    <form className="unit-form-grid" onSubmit={onSubmit}>
      <Field label="Unit" name="unitLabel" required />
      <label className="field">
        <span>Status</span>
        <select name="status" defaultValue="VACANT">
          {UNIT_STATUSES.map((status) => (
            <option key={status} value={status}>{labelFor(status)}</option>
          ))}
        </select>
      </label>
      <Field label="Bedrooms" name="bedrooms" type="number" />
      <Field label="Bathrooms" name="bathrooms" type="number" step="0.5" />
      <Field label="Sq ft" name="squareFeet" type="number" />
      <Field label="Current rent" name="currentRent" type="number" step="0.01" />
      <Field label="Assigned tenants" name="currentTenantNames" />
      <button className="primary-action" type="submit">Create unit</button>
    </form>
  );
}

function Field({
  label,
  name,
  onChange,
  placeholder,
  required,
  step,
  type = "text",
  value
}: {
  label: string;
  name: string;
  onChange?: (value: string) => void;
  placeholder?: string;
  required?: boolean;
  step?: string;
  type?: string;
  value?: string;
}) {
  return (
    <label className="field">
      <span>{label}</span>
      <input
        name={name}
        placeholder={placeholder}
        required={required}
        step={step}
        type={type}
        value={value}
        onChange={onChange ? (event) => onChange(event.target.value) : undefined}
      />
    </label>
  );
}

function PostalCodeField({
  showError,
  value,
  onChange
}: {
  showError: boolean;
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <label className="field">
      <span>Postal code</span>
      <input
        autoComplete="postal-code"
        inputMode="text"
        maxLength={7}
        name="postalCode"
        pattern={CANADIAN_POSTAL_CODE_PATTERN.source}
        placeholder="A1A 1A1"
        required
        title="Use Canadian postal code format: A1A 1A1"
        value={value}
        onChange={(event) => {
          event.target.setCustomValidity("");
          onChange(formatCanadianPostalCode(event.target.value));
        }}
        onInvalid={(event) => {
          event.currentTarget.setCustomValidity("Use Canadian postal code format: A1A 1A1");
        }}
      />
      {showError ? <small className="field-error">Use format A1A 1A1.</small> : null}
    </label>
  );
}

const UNIT_STATUSES: UnitStatus[] = ["OCCUPIED", "VACANT", "NOTICE_PENDING", "MAINTENANCE", "OFF_MARKET"];

const unitTableColumns: DataTableColumn<PropertyUnitResponse>[] = [
  { key: "unit", header: "Unit", render: (row) => <strong>{row.unitLabel}</strong> },
  { key: "status", header: "Status", render: (row) => <StatusBadge value={row.status} /> },
  { key: "tenant", header: "Assigned tenants", render: (row) => row.currentTenantNames ?? "Unassigned" },
  { key: "rent", header: "Rent", render: (row) => formatCents(row.currentRentCents), align: "right" }
];

function propertyTableColumns(onSelectProperty: (propertyId: string) => Promise<void>): DataTableColumn<PropertySummaryResponse>[] {
  return [
    {
      key: "name",
      header: "Property name",
      render: (row) => (
        <button className="table-link" type="button" onClick={() => onSelectProperty(row.id)}>
          {row.name}
        </button>
      )
    },
    { key: "address", header: "Address", render: (row) => row.address },
    { key: "units", header: "Units", render: (row) => row.unitCount, align: "right" },
    { key: "occupancy", header: "Occupancy", render: (row) => `${row.occupancyRate}%`, align: "right" },
    { key: "notices", header: "Active notices", render: (row) => row.activeNoticeCount, align: "right" }
  ];
}

export interface CreatePropertyFormValue {
  name: string;
  addressLine1: string;
  addressLine2?: string | null;
  city: string;
  province: string;
  postalCode: string;
  country?: string | null;
  notes?: string | null;
}

export interface CreateUnitFormValue {
  unitLabel: string;
  status: UnitStatus;
  bedrooms?: number | null;
  bathrooms?: number | null;
  squareFeet?: number | null;
  currentTenantNames?: string | null;
  currentRentCents?: number | null;
}

function derivedPropertyName(form: FormData): string {
  const line1 = String(form.get("addressLine1") ?? "").trim();
  if (line1) {
    return line1;
  }
  const city = String(form.get("city") ?? "").trim();
  const province = String(form.get("province") ?? "").trim();
  const postal = String(form.get("postalCode") ?? "").trim();
  const fallback = [city, province, postal].filter(Boolean).join(", ");
  return fallback || "Property";
}

function optionalString(value: FormDataEntryValue | null) {
  if (typeof value !== "string") {
    return null;
  }
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
}

const CANADIAN_POSTAL_CODE_PATTERN = /^[A-Z]\d[A-Z] \d[A-Z]\d$/;

function emptyPropertyForm() {
  return {
    addressLine1: "",
    addressLine2: "",
    city: "",
    province: "",
    country: "Canada",
    notes: ""
  };
}

function formatCanadianPostalCode(value: string) {
  const compact = value
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, "")
    .slice(0, 6);
  if (compact.length <= 3) {
    return compact;
  }
  return `${compact.slice(0, 3)} ${compact.slice(3)}`;
}

function optionalNumber(value: FormDataEntryValue | null) {
  const text = optionalString(value);
  return text ? Number(text) : null;
}

function currencyToCents(value: FormDataEntryValue | null) {
  const amount = optionalNumber(value);
  return amount === null ? null : Math.round(amount * 100);
}

function formatCents(value?: number | null) {
  if (!value) {
    return "-";
  }
  return new Intl.NumberFormat(undefined, { style: "currency", currency: "CAD" }).format(value / 100);
}

function formatPropertyAddress(property: PropertyResponse) {
  const province = propertyProvinceText(property);
  return [property.addressLine1, property.addressLine2, property.city, province || undefined, property.postalCode]
    .filter(Boolean)
    .join(", ");
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric"
  }).format(new Date(value));
}

function labelFor(value: string) {
  return value
    .toLowerCase()
    .split("_")
    .map((part) => part[0].toUpperCase() + part.slice(1))
    .join(" ");
}
