"use client";

import { useEffect, useState } from "react";
import SearchForm from "@/components/SearchForm";
import ItineraryList from "@/components/ItineraryList";
import LoadingBar from "@/components/LoadingBar";
import { ApiError, fetchAirports, searchItineraries } from "@/lib/api";
import type { Airport, Itinerary, SearchParams } from "@/lib/api";
import styles from "./page.module.css";

export default function Home() {
  const [airports, setAirports] = useState<Airport[]>([]);
  const [airportsError, setAirportsError] = useState<string | null>(null);

  const [results, setResults] = useState<Itinerary[] | null>(null);
  const [isSearching, setIsSearching] = useState(false);
  const [searchError, setSearchError] = useState<string | null>(null);
  const [lastSearch, setLastSearch] = useState<SearchParams | null>(null);

  useEffect(() => {
    fetchAirports()
      .then(setAirports)
      .catch((error: unknown) => {
        setAirportsError(
          error instanceof ApiError ? error.message : "Couldn't load the airport list."
        );
      });
  }, []);

  async function handleSearch(params: SearchParams) {
    setIsSearching(true);
    setSearchError(null);
    setLastSearch(params);
    try {
      const itineraries = await searchItineraries(params);
      setResults(itineraries);
    } catch (error) {
      setResults(null);
      setSearchError(
        error instanceof ApiError ? error.message : "Something went wrong while searching. Please try again."
      );
    } finally {
      setIsSearching(false);
    }
  }

  return (
    <div className={styles.page}>
      <main className={styles.main}>
        <header className={styles.header}>
          <h1>SkyPath</h1>
          <p>Search direct and connecting flights across our network.</p>
        </header>

        {airportsError ? <p className={styles.banner}>{airportsError}</p> : null}

        <SearchForm airports={airports} isLoading={isSearching} onSearch={handleSearch} />

        <section className={styles.results} aria-live="polite">
          {isSearching ? (
            <div className={styles.loading}>
              <LoadingBar />
              <p className={styles.status}>Searching for flights…</p>
            </div>
          ) : null}

          {!isSearching && searchError ? (
            <p className={`${styles.status} ${styles.errorStatus}`}>{searchError}</p>
          ) : null}

          {!isSearching && !searchError && results !== null && results.length === 0 ? (
            <p className={styles.status}>
              No flights found from {lastSearch?.origin} to {lastSearch?.destination} on {lastSearch?.date}.
            </p>
          ) : null}

          {!isSearching && !searchError && results && results.length > 0 ? (
            <ItineraryList itineraries={results} />
          ) : null}
        </section>
      </main>
    </div>
  );
}
