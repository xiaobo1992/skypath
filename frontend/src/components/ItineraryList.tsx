import type { Itinerary } from "@/lib/api";
import ItineraryCard from "./ItineraryCard";
import styles from "./ItineraryList.module.css";

type ItineraryListProps = {
  itineraries: Itinerary[];
};

export default function ItineraryList({ itineraries }: ItineraryListProps) {
  return (
    <>
      <p className={styles.note}>
        All times shown are local to each airport, not to your current time zone.
      </p>
      <ul className={styles.list}>
        {itineraries.map((itinerary, index) => (
          // Itineraries aren't individually identified by the API; the flight-number
          // sequence plus index is stable enough as a list key for this read-only list.
          <ItineraryCard key={`${itinerary.segments.map((s) => s.flightNumber).join("-")}-${index}`} itinerary={itinerary} />
        ))}
      </ul>
    </>
  );
}
