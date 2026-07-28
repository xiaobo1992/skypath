import styles from "./LoadingBar.module.css";

export default function LoadingBar() {
  return (
    <div className={styles.track} role="progressbar" aria-label="Searching for flights">
      <div className={styles.bar} />
    </div>
  );
}
