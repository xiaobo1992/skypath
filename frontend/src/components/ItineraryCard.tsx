import type { Airport, Itinerary } from "@/lib/api";
import { formatDateTime, formatDuration, formatPrice } from "@/lib/format";
import styles from "./ItineraryCard.module.css";

type ItineraryCardProps = {
  itinerary: Itinerary;
  airportsByCode: Map<string, Airport>;
};

const stopLabel = (stops: number) => {
  if (stops === 0) return "Direct";
  if (stops === 1) return "1 stop";
  return `${stops} stops`;
};

export default function ItineraryCard({ itinerary, airportsByCode }: ItineraryCardProps) {
  const stops = itinerary.segments.length - 1;
  const layoverMinutes = itinerary.layoverMinutes ?? [];
  const cityOf = (code: string) => airportsByCode.get(code)?.city;

  return (
    <li className={styles.card}>
      <div className={styles.summary}>
        <span className={styles.stops}>{stopLabel(stops)}</span>
        <span className={styles.duration}>{formatDuration(itinerary.totalDurationMinutes)}</span>
        <span className={styles.price}>{formatPrice(itinerary.totalPrice)}</span>
      </div>

      <ol className={styles.segments}>
        {itinerary.segments.map((segment, index) => (
          <li key={segment.flightNumber} className={styles.segment}>
            <div className={styles.segmentRoute}>
              <span className={styles.time}>{formatDateTime(segment.departureTime)}</span>
              <span className={styles.airport}>
                {segment.origin}
                {cityOf(segment.origin) ? (
                  <span className={styles.city}> ({cityOf(segment.origin)})</span>
                ) : null}
              </span>
              <span className={styles.arrow}>&rarr;</span>
              <span className={styles.airport}>
                {segment.destination}
                {cityOf(segment.destination) ? (
                  <span className={styles.city}> ({cityOf(segment.destination)})</span>
                ) : null}
              </span>
              <span className={styles.time}>{formatDateTime(segment.arrivalTime)}</span>
            </div>
            <div className={styles.segmentMeta}>
              {segment.flightNumber} &middot; {segment.airline}
              {segment.aircraft ? ` · ${segment.aircraft}` : ""}
            </div>

            {index < layoverMinutes.length ? (
              <div className={styles.layover}>
                Layover in {segment.destination}
                {cityOf(segment.destination) ? ` (${cityOf(segment.destination)})` : ""}:{" "}
                {formatDuration(layoverMinutes[index])}
              </div>
            ) : null}
          </li>
        ))}
      </ol>
    </li>
  );
}
