"use client";

import { FormEvent, useState } from "react";
import type { Airport, SearchParams } from "@/lib/api";
import styles from "./SearchForm.module.css";

type SearchFormProps = {
  airports: Airport[];
  isLoading: boolean;
  onSearch: (params: SearchParams) => void;
};

const DATASET_DATE = "2024-03-15";

export default function SearchForm({ airports, isLoading, onSearch }: SearchFormProps) {
  const [origin, setOrigin] = useState("");
  const [destination, setDestination] = useState("");
  const [date, setDate] = useState(DATASET_DATE);
  const [validationError, setValidationError] = useState<string | null>(null);

  const airportCodes = new Set(airports.map((airport) => airport.code));

  function validate(): string | null {
    const originCode = origin.trim().toUpperCase();
    const destinationCode = destination.trim().toUpperCase();

    if (!originCode || !destinationCode) {
      return "Enter both an origin and a destination airport.";
    }
    if (airportCodes.size > 0) {
      if (!airportCodes.has(originCode)) {
        return `"${origin}" isn't a known airport code.`;
      }
      if (!airportCodes.has(destinationCode)) {
        return `"${destination}" isn't a known airport code.`;
      }
    }
    if (originCode === destinationCode) {
      return "Origin and destination must be different airports.";
    }
    if (!date) {
      return "Select a travel date.";
    }
    return null;
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const error = validate();
    setValidationError(error);
    if (error) {
      return;
    }
    onSearch({
      origin: origin.trim().toUpperCase(),
      destination: destination.trim().toUpperCase(),
      date,
    });
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit} noValidate>
      <div className={styles.field}>
        <label htmlFor="origin">From</label>
        <input
          id="origin"
          name="origin"
          type="text"
          placeholder="e.g. JFK"
          list="airport-codes"
          maxLength={3}
          autoComplete="off"
          value={origin}
          onChange={(event) => setOrigin(event.target.value)}
        />
      </div>

      <div className={styles.field}>
        <label htmlFor="destination">To</label>
        <input
          id="destination"
          name="destination"
          type="text"
          placeholder="e.g. LAX"
          list="airport-codes"
          maxLength={3}
          autoComplete="off"
          value={destination}
          onChange={(event) => setDestination(event.target.value)}
        />
      </div>

      <div className={styles.field}>
        <label htmlFor="date">Date</label>
        <input
          id="date"
          name="date"
          type="date"
          value={date}
          onChange={(event) => setDate(event.target.value)}
        />
      </div>

      <datalist id="airport-codes">
        {airports.map((airport) => (
          <option key={airport.code} value={airport.code}>
            {airport.city} ({airport.country})
          </option>
        ))}
      </datalist>

      <button type="submit" className={styles.submit} disabled={isLoading}>
        {isLoading ? "Searching…" : "Search flights"}
      </button>

      {validationError ? (
        <p className={styles.error} role="alert">
          {validationError}
        </p>
      ) : null}
    </form>
  );
}
