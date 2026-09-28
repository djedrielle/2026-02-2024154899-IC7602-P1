"use client";

import { useEffect, useState } from "react";
import styles from "./page.module.css";

const recordTypes = ["single", "multi", "weight", "round-trip", "geo"];
const apiBaseUrl = process.env.NEXT_PUBLIC_DNS_API_URL?.replace(/\/$/, "");

function emptyTarget(type) {
  if (type === "weight") return { ip: "", weight: "", healthy: true };
  if (type === "round-trip") {
    return { ip: "", latitude: "", longitude: "", healthy: true };
  }
  if (type === "geo") return { country: "", ip: "", healthy: true };
  return { ip: "", healthy: true };
}

function emptyHealthCheck() {
  return {
    type: "TCP",
    timeout: "",
    retries: "",
    interval: "",
    path: "",
    expectedCodes: "",
  };
}

function emptyRecord() {
  return {
    domain: "",
    type: "single",
    ttl: "",
    targets: [emptyTarget("single")],
    healthCheck: emptyHealthCheck(),
  };
}

function emptyCountryRecord() {
  return {
    start_ip: "",
    end_ip: "",
    country_code: "",
    country_name: "",
    city: "",
    latitude: "",
    longitude: "",
  };
}

function recordFromApi(record) {
  return {
    domain: record.name,
    type: record.type,
    ttl: record.ttl,
    targets: record.ips.map((target) => {
      if (record.type === "geo") {
        const { country_code, ...targetData } = target;
        return { ...targetData, country: country_code };
      }
      return { ...target };
    }),
    healthCheck: emptyHealthCheck(),
  };
}

function recordToApi(record) {
  return {
    name: record.domain,
    type: record.type,
    ttl: Number(record.ttl),
    ips: record.targets.map((target) => {
      const { country, ...targetData } = target;

      if (record.type === "weight") {
        return { ...targetData, weight: Number(targetData.weight) };
      }
      if (record.type === "round-trip") {
        return {
          ...targetData,
          latitude: Number(targetData.latitude),
          longitude: Number(targetData.longitude),
        };
      }
      if (record.type === "geo") {
        return { ...targetData, country_code: country };
      }
      return targetData;
    }),
  };
}

function describeTargets(record) {
  if (record.type === "weight") {
    return record.targets
      .map((target) => `${target.ip} (${target.weight})`)
      .join(", ");
  }
  if (record.type === "geo") {
    return record.targets
      .map((target) => `${target.country}: ${target.ip}`)
      .join(", ");
  }
  if (record.type === "round-trip") {
    return record.targets
      .map((target) => `${target.ip} (${target.latitude}, ${target.longitude})`)
      .join(", ");
  }
  return record.targets.map((target) => target.ip).join(", ");
}

export default function Home() {
  const [section, setSection] = useState("records");
  const [records, setRecords] = useState([]);
  const [recordsLoading, setRecordsLoading] = useState(true);
  const [recordsError, setRecordsError] = useState("");
  const [recordSaving, setRecordSaving] = useState(false);
  const [recordSaveError, setRecordSaveError] = useState("");
  const [recordActionError, setRecordActionError] = useState("");
  const [deletingRecord, setDeletingRecord] = useState("");
  const [countryRecords, setCountryRecords] = useState([]);
  const [recordPanel, setRecordPanel] = useState(null);
  const [countryPanel, setCountryPanel] = useState(null);
  const [recordForm, setRecordForm] = useState(emptyRecord);
  const [countryForm, setCountryForm] = useState(emptyCountryRecord);

  const isEditingRecord = recordPanel?.mode === "edit";
  const isEditingCountry = countryPanel?.mode === "edit";

  useEffect(() => {
    let isCurrent = true;

    async function loadRecords() {
      if (!apiBaseUrl) {
        if (isCurrent) {
          setRecordsError("Configura NEXT_PUBLIC_DNS_API_URL para cargar los registros.");
          setRecordsLoading(false);
        }
        return;
      }

      try {
        const response = await fetch(`${apiBaseUrl}/api/records`);
        if (!response.ok) {
          throw new Error("No se pudieron obtener los registros.");
        }

        const data = await response.json();
        if (isCurrent) {
          setRecords(data.map(recordFromApi));
        }
      } catch {
        if (isCurrent) {
          setRecordsError("No se pudieron cargar los registros DNS.");
        }
      } finally {
        if (isCurrent) {
          setRecordsLoading(false);
        }
      }
    }

    loadRecords();

    return () => {
      isCurrent = false;
    };
  }, []);

  function openNewRecord() {
    setRecordForm(emptyRecord());
    setRecordSaveError("");
    setRecordActionError("");
    setRecordPanel({ mode: "create" });
  }

  function openEditRecord(index) {
    const record = records[index];
    setRecordForm({
      ...record,
      targets: record.targets.map((target) => ({ ...target })),
      healthCheck: { ...record.healthCheck },
    });
    setRecordSaveError("");
    setRecordActionError("");
    setRecordPanel({ mode: "edit", index, currentName: record.domain });
  }

  async function saveRecord(event) {
    event.preventDefault();
    if (!apiBaseUrl) {
      setRecordSaveError("Configura NEXT_PUBLIC_DNS_API_URL para guardar el registro.");
      return;
    }

    setRecordSaving(true);
    setRecordSaveError("");
    try {
      const path = isEditingRecord
        ? `/api/records/${encodeURIComponent(recordPanel.currentName)}`
        : "/api/records";
      const response = await fetch(`${apiBaseUrl}${path}`, {
        method: isEditingRecord ? "PUT" : "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(recordToApi(recordForm)),
      });
      if (!response.ok) {
        throw new Error("No se pudo crear el registro.");
      }

      const savedRecord = await response.json();
      setRecords((currentRecords) =>
        isEditingRecord
          ? currentRecords.map((record, index) =>
            index === recordPanel.index ? recordFromApi(savedRecord) : record,
          )
          : [...currentRecords, recordFromApi(savedRecord)],
      );
      setRecordPanel(null);
    } catch {
      setRecordSaveError("No se pudo guardar el registro DNS.");
    } finally {
      setRecordSaving(false);
    }
  }

  async function deleteRecord(index) {
    const record = records[index];
    if (!apiBaseUrl) {
      setRecordActionError("Configura NEXT_PUBLIC_DNS_API_URL para eliminar el registro.");
      return;
    }

    setDeletingRecord(record.domain);
    setRecordActionError("");
    try {
      const response = await fetch(
        `${apiBaseUrl}/api/records/${encodeURIComponent(record.domain)}`,
        { method: "DELETE" },
      );
      if (!response.ok) {
        throw new Error("No se pudo eliminar el registro.");
      }

      setRecords((currentRecords) =>
        currentRecords.filter((_, recordIndex) => recordIndex !== index),
      );
    } catch {
      setRecordActionError("No se pudo eliminar el registro DNS.");
    } finally {
      setDeletingRecord("");
    }
  }

  function changeRecordType(type) {
    setRecordForm((currentRecord) => ({
      ...currentRecord,
      type,
      targets: [emptyTarget(type)],
    }));
  }

  function updateTarget(index, field, value) {
    setRecordForm((currentRecord) => ({
      ...currentRecord,
      targets: currentRecord.targets.map((target, targetIndex) =>
        targetIndex === index ? { ...target, [field]: value } : target,
      ),
    }));
  }

  function addTarget() {
    setRecordForm((currentRecord) => ({
      ...currentRecord,
      targets: [...currentRecord.targets, emptyTarget(currentRecord.type)],
    }));
  }

  function removeTarget(index) {
    setRecordForm((currentRecord) => ({
      ...currentRecord,
      targets: currentRecord.targets.filter(
        (_, targetIndex) => targetIndex !== index,
      ),
    }));
  }

  function updateHealthCheck(field, value) {
    setRecordForm((currentRecord) => ({
      ...currentRecord,
      healthCheck: { ...currentRecord.healthCheck, [field]: value },
    }));
  }

  function openNewCountryRecord() {
    setCountryForm(emptyCountryRecord());
    setCountryPanel({ mode: "create" });
  }

  function openEditCountryRecord(index) {
    setCountryForm({ ...countryRecords[index] });
    setCountryPanel({ mode: "edit", index });
  }

  function saveCountryRecord(event) {
    event.preventDefault();
    if (isEditingCountry) {
      setCountryRecords((currentRecords) =>
        currentRecords.map((record, index) =>
          index === countryPanel.index ? countryForm : record,
        ),
      );
    } else {
      setCountryRecords((currentRecords) => [...currentRecords, countryForm]);
    }
    setCountryPanel(null);
  }

  function deleteCountryRecord(index) {
    setCountryRecords((currentRecords) =>
      currentRecords.filter((_, recordIndex) => recordIndex !== index),
    );
  }

  function renderTargetFields() {
    const { type, targets } = recordForm;
    const canAddTarget = type !== "single";

    return (
      <section className={styles.formSection}>
        <div className={styles.sectionHeading}>
          <div>
            <h3>Configuración del registro</h3>
            <p>
              {type === "geo"
                ? "Asigna un país a cada dirección IP."
                : "Agrega las direcciones IP necesarias para este tipo de registro."}
            </p>
          </div>
          {canAddTarget && (
            <button className={styles.textButton} onClick={addTarget} type="button">
              + Agregar {type === "geo" ? "asignación" : "IP"}
            </button>
          )}
        </div>

        <div className={styles.targetList}>
          {targets.map((target, index) => (
            <div className={styles.targetRow} key={index}>
              {type === "geo" && (
                <label>
                  Código de país
                  <input
                    onChange={(event) => updateTarget(index, "country", event.target.value)}
                    required
                    value={target.country}
                  />
                </label>
              )}
              <label>
                Dirección IP
                <input
                  onChange={(event) => updateTarget(index, "ip", event.target.value)}
                  required
                  value={target.ip}
                />
              </label>
              {type === "round-trip" && (
                <>
                  <label>
                    Latitud
                    <input
                      onChange={(event) => updateTarget(index, "latitude", event.target.value)}
                      required
                      step="any"
                      type="number"
                      value={target.latitude}
                    />
                  </label>
                  <label>
                    Longitud
                    <input
                      onChange={(event) => updateTarget(index, "longitude", event.target.value)}
                      required
                      step="any"
                      type="number"
                      value={target.longitude}
                    />
                  </label>
                </>
              )}
              {type === "weight" && (
                <label>
                  Peso
                  <input
                    min="0"
                    onChange={(event) => updateTarget(index, "weight", event.target.value)}
                    required
                    type="number"
                    value={target.weight}
                  />
                </label>
              )}
              {targets.length > 1 && (
                <button
                  aria-label="Eliminar fila"
                  className={styles.removeButton}
                  onClick={() => removeTarget(index)}
                  type="button"
                >
                  Eliminar
                </button>
              )}
            </div>
          ))}
        </div>
      </section>
    );
  }

  function renderHealthCheckFields() {
    const { healthCheck } = recordForm;
    const isHttp = healthCheck.type === "HTTP";

    return (
      <section className={styles.formSection}>
        <div className={styles.sectionHeading}>
          <div>
            <h3>Health check asociado</h3>
            <p>Define la prueba que utilizará el Health Checker.</p>
          </div>
        </div>
        <div className={styles.protocolButtons}>
          {["TCP", "HTTP"].map((type) => (
            <button
              className={healthCheck.type === type ? styles.selectedType : ""}
              key={type}
              onClick={() => updateHealthCheck("type", type)}
              type="button"
            >
              {type}
            </button>
          ))}
        </div>
        {isHttp && (
          <label>
            Path
            <input
              onChange={(event) => updateHealthCheck("path", event.target.value)}
              required
              value={healthCheck.path}
            />
          </label>
        )}
        <div className={styles.healthGrid}>
          <label>
            Timeout
            <input
              min="0"
              onChange={(event) => updateHealthCheck("timeout", event.target.value)}
              required
              type="number"
              value={healthCheck.timeout}
            />
          </label>
          <label>
            Retries
            <input
              min="0"
              onChange={(event) => updateHealthCheck("retries", event.target.value)}
              required
              type="number"
              value={healthCheck.retries}
            />
          </label>
          <label>
            Intervalo entre pruebas
            <input
              min="0"
              onChange={(event) => updateHealthCheck("interval", event.target.value)}
              required
              type="number"
              value={healthCheck.interval}
            />
          </label>
        </div>
        {isHttp && (
          <label>
            Códigos HTTP esperados
            <input
              onChange={(event) => updateHealthCheck("expectedCodes", event.target.value)}
              required
              value={healthCheck.expectedCodes}
            />
          </label>
        )}
      </section>
    );
  }

  return (
    <div className={styles.appShell}>
      <aside className={styles.sidebar}>
        <div>
          <div className={styles.brand}>DNS UI</div>
          <nav aria-label="Navegación principal" className={styles.navigation}>
            <button
              className={section === "records" ? styles.activeNav : ""}
              onClick={() => setSection("records")}
              type="button"
            >
              Registros DNS
            </button>
            <button
              className={section === "country" ? styles.activeNav : ""}
              onClick={() => setSection("country")}
              type="button"
            >
              IP to Country
            </button>
          </nav>
        </div>
      </aside>

      <main className={styles.main}>
        {section === "records" ? (
          <section className={styles.contentCard}>
            <div className={styles.pageHeader}>
              <div>
                <h1>Registros DNS</h1>
                <p>Administra los registros DNS y sus health checks.</p>
              </div>
              <button className={styles.primaryButton} onClick={openNewRecord} type="button">
                + Crear registro
              </button>
            </div>
            {recordActionError && <p role="alert">{recordActionError}</p>}
            {recordsLoading ? (
              <div className={styles.emptyState}>
                <h2>Cargando registros DNS</h2>
              </div>
            ) : recordsError ? (
              <div className={styles.emptyState}>
                <h2>No se pudieron cargar los registros DNS</h2>
                <p>{recordsError}</p>
              </div>
            ) : records.length === 0 ? (
              <div className={styles.emptyState}>
                <h2>No hay registros DNS</h2>
                <p>Crea un registro para comenzar.</p>
              </div>
            ) : (
              <div className={styles.tableWrap}>
                <table>
                  <thead>
                    <tr>
                      <th>Nombre del dominio</th>
                      <th>Tipo</th>
                      <th>TTL</th>
                      <th>Configuración</th>
                      <th>Acciones</th>
                    </tr>
                  </thead>
                  <tbody>
                    {records.map((record, index) => (
                      <tr key={`${record.domain}-${index}`}>
                        <td className={styles.mono}>{record.domain}</td>
                        <td><span className={styles.badge}>{record.type}</span></td>
                        <td>{record.ttl}</td>
                        <td className={styles.mono}>{describeTargets(record)}</td>
                        <td className={styles.actions}>
                          <button onClick={() => openEditRecord(index)} type="button">Editar</button>
                          <button
                            disabled={deletingRecord === record.domain}
                            onClick={() => deleteRecord(index)}
                            type="button"
                          >
                            {deletingRecord === record.domain ? "Eliminando..." : "Eliminar"}
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        ) : (
          <section className={styles.contentCard}>
            <div className={styles.pageHeader}>
              <div>
                <h1>IP to Country</h1>
                <p>Administra los registros de la base de datos IP to Country.</p>
              </div>
              <button className={styles.primaryButton} onClick={openNewCountryRecord} type="button">
                + Agregar registro
              </button>
            </div>
            {countryRecords.length === 0 ? (
              <div className={styles.emptyState}>
                <h2>No hay registros IP to Country</h2>
                <p>Agrega un rango de IP y su ubicación asociada.</p>
              </div>
            ) : (
              <div className={styles.tableWrap}>
                <table>
                  <thead>
                    <tr>
                      <th>Rango IP</th>
                      <th>País</th>
                      <th>Ciudad</th>
                      <th>Coordenadas</th>
                      <th>Acciones</th>
                    </tr>
                  </thead>
                  <tbody>
                    {countryRecords.map((record, index) => (
                      <tr key={`${record.start_ip}-${record.end_ip}-${index}`}>
                        <td className={styles.mono}>{record.start_ip} — {record.end_ip}</td>
                        <td>{record.country_code}{record.country_name ? ` · ${record.country_name}` : ""}</td>
                        <td>{record.city || "—"}</td>
                        <td>{record.latitude !== "" && record.longitude !== "" ? `${record.latitude}, ${record.longitude}` : "—"}</td>
                        <td className={styles.actions}>
                          <button onClick={() => openEditCountryRecord(index)} type="button">Editar</button>
                          <button onClick={() => deleteCountryRecord(index)} type="button">Eliminar</button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        )}
      </main>

      {recordPanel && (
        <div className={styles.overlay}>
          <form className={styles.drawer} onSubmit={saveRecord}>
            <div className={styles.drawerHeader}>
              <h2>{isEditingRecord ? "Editar registro DNS" : "Crear registro DNS"}</h2>
              <button aria-label="Cerrar" className={styles.closeButton} onClick={() => setRecordPanel(null)} type="button">×</button>
            </div>
            <div className={styles.drawerBody}>
              <label>
                Nombre del dominio
                <input
                  onChange={(event) => setRecordForm((currentRecord) => ({ ...currentRecord, domain: event.target.value }))}
                  readOnly={isEditingRecord}
                  required
                  value={recordForm.domain}
                />
              </label>
              <label>
                TTL (segundos)
                <input
                  min="0"
                  onChange={(event) => setRecordForm((currentRecord) => ({ ...currentRecord, ttl: event.target.value }))}
                  required
                  type="number"
                  value={recordForm.ttl}
                />
              </label>
              <fieldset className={styles.typeSelector}>
                <legend>Tipo de registro</legend>
                <div>
                  {recordTypes.map((type) => (
                    <button
                      className={recordForm.type === type ? styles.selectedType : ""}
                      key={type}
                      onClick={() => changeRecordType(type)}
                      type="button"
                    >
                      {type}
                    </button>
                  ))}
                </div>
              </fieldset>
              {renderTargetFields()}
              {renderHealthCheckFields()}
              {recordSaveError && <p role="alert">{recordSaveError}</p>}
            </div>
            <div className={styles.drawerFooter}>
              <button className={styles.secondaryButton} onClick={() => setRecordPanel(null)} type="button">Cancelar</button>
              <button className={styles.primaryButton} disabled={recordSaving} type="submit">
                {recordSaving ? "Guardando..." : isEditingRecord ? "Guardar cambios" : "Guardar registro"}
              </button>
            </div>
          </form>
        </div>
      )}

      {countryPanel && (
        <div className={styles.overlay}>
          <form className={styles.drawer} onSubmit={saveCountryRecord}>
            <div className={styles.drawerHeader}>
              <h2>{isEditingCountry ? "Editar registro IP to Country" : "Agregar registro IP to Country"}</h2>
              <button aria-label="Cerrar" className={styles.closeButton} onClick={() => setCountryPanel(null)} type="button">×</button>
            </div>
            <div className={styles.drawerBody}>
              <label>
                IP inicial
                <input
                  onChange={(event) => setCountryForm((currentRecord) => ({ ...currentRecord, start_ip: event.target.value }))}
                  required
                  value={countryForm.start_ip}
                />
              </label>
              <label>
                IP final
                <input
                  onChange={(event) => setCountryForm((currentRecord) => ({ ...currentRecord, end_ip: event.target.value }))}
                  required
                  value={countryForm.end_ip}
                />
              </label>
              <div className={styles.healthGrid}>
                <label>
                  Código de país
                  <input
                    maxLength="2"
                    minLength="2"
                    onChange={(event) => setCountryForm((currentRecord) => ({ ...currentRecord, country_code: event.target.value }))}
                    required
                    value={countryForm.country_code}
                  />
                </label>
                <label>
                  Nombre de país
                  <input
                    onChange={(event) => setCountryForm((currentRecord) => ({ ...currentRecord, country_name: event.target.value }))}
                    value={countryForm.country_name}
                  />
                </label>
                <label>
                  Ciudad
                  <input
                    onChange={(event) => setCountryForm((currentRecord) => ({ ...currentRecord, city: event.target.value }))}
                    value={countryForm.city}
                  />
                </label>
              </div>
              <div className={styles.healthGrid}>
                <label>
                  Latitud
                  <input
                    onChange={(event) => setCountryForm((currentRecord) => ({ ...currentRecord, latitude: event.target.value }))}
                    step="any"
                    type="number"
                    value={countryForm.latitude}
                  />
                </label>
                <label>
                  Longitud
                  <input
                    onChange={(event) => setCountryForm((currentRecord) => ({ ...currentRecord, longitude: event.target.value }))}
                    step="any"
                    type="number"
                    value={countryForm.longitude}
                  />
                </label>
              </div>
            </div>
            <div className={styles.drawerFooter}>
              <button className={styles.secondaryButton} onClick={() => setCountryPanel(null)} type="button">Cancelar</button>
              <button className={styles.primaryButton} type="submit">
                {isEditingCountry ? "Guardar cambios" : "Guardar registro"}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
