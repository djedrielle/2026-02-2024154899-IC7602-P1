"use client";

import { useEffect, useState } from "react";
import styles from "./page.module.css";

const recordTypes = ["single", "multi", "weight", "round-trip", "geo"];
const apiBaseUrl = process.env.NEXT_PUBLIC_DNS_API_URL?.replace(/\/$/, "");

function emptyTarget(type) {
  if (type === "weight") return { ip: "", weight: "" };
  if (type === "geo") return { country: "", ip: "" };
  return { ip: "" };
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
    targets: [emptyTarget("single")],
    healthCheck: emptyHealthCheck(),
  };
}

function recordFromApi(record) {
  return {
    domain: record.name,
    type: record.type,
    ttl: record.ttl,
    targets: record.ips.map((target) =>
      record.type === "geo"
        ? { ...target, country: target.country_code }
        : { ...target },
    ),
    healthCheck: emptyHealthCheck(),
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
  return record.targets.map((target) => target.ip).join(", ");
}

export default function Home() {
  const [section, setSection] = useState("records");
  const [records, setRecords] = useState([]);
  const [recordsLoading, setRecordsLoading] = useState(true);
  const [recordsError, setRecordsError] = useState("");
  const [countryRecords, setCountryRecords] = useState([]);
  const [recordPanel, setRecordPanel] = useState(null);
  const [countryPanel, setCountryPanel] = useState(null);
  const [recordForm, setRecordForm] = useState(emptyRecord);
  const [countryForm, setCountryForm] = useState({ ip: "", country: "" });

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
    setRecordPanel({ mode: "create" });
  }

  function openEditRecord(index) {
    const record = records[index];
    setRecordForm({
      ...record,
      targets: record.targets.map((target) => ({ ...target })),
      healthCheck: { ...record.healthCheck },
    });
    setRecordPanel({ mode: "edit", index });
  }

  function saveRecord(event) {
    event.preventDefault();
    if (isEditingRecord) {
      setRecords((currentRecords) =>
        currentRecords.map((record, index) =>
          index === recordPanel.index ? recordForm : record,
        ),
      );
    } else {
      setRecords((currentRecords) => [...currentRecords, recordForm]);
    }
    setRecordPanel(null);
  }

  function deleteRecord(index) {
    setRecords((currentRecords) =>
      currentRecords.filter((_, recordIndex) => recordIndex !== index),
    );
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
    setCountryForm({ ip: "", country: "" });
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
                  País
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
                          <button onClick={() => deleteRecord(index)} type="button">Eliminar</button>
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
                <p>Agrega una dirección IP y su país asociado.</p>
              </div>
            ) : (
              <div className={styles.tableWrap}>
                <table>
                  <thead>
                    <tr>
                      <th>IP</th>
                      <th>País</th>
                      <th>Acciones</th>
                    </tr>
                  </thead>
                  <tbody>
                    {countryRecords.map((record, index) => (
                      <tr key={`${record.ip}-${index}`}>
                        <td className={styles.mono}>{record.ip}</td>
                        <td>{record.country}</td>
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
                  required
                  value={recordForm.domain}
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
            </div>
            <div className={styles.drawerFooter}>
              <button className={styles.secondaryButton} onClick={() => setRecordPanel(null)} type="button">Cancelar</button>
              <button className={styles.primaryButton} type="submit">
                {isEditingRecord ? "Guardar cambios" : "Guardar registro"}
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
                IP
                <input
                  onChange={(event) => setCountryForm((currentRecord) => ({ ...currentRecord, ip: event.target.value }))}
                  required
                  value={countryForm.ip}
                />
              </label>
              <label>
                País
                <input
                  onChange={(event) => setCountryForm((currentRecord) => ({ ...currentRecord, country: event.target.value }))}
                  required
                  value={countryForm.country}
                />
              </label>
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
