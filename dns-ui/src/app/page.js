import styles from "./page.module.css";

const recordTypes = ["single", "multi", "weight", "round-trip", "geo"];

export default function Home() {
  return (
    <div className={styles.page}>
      <main className={styles.main}>
        <h1>Registros DNS</h1>
        <p>Tipos de registros disponibles</p>
        <ul className={styles.recordList}>
          {recordTypes.map((type) => (
            <li key={type}>{type}</li>
          ))}
        </ul>
      </main>
    </div>
  );
}
