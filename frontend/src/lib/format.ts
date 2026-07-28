/** All times from the API are local to the airport they belong to (no UTC conversion here). */
export function formatTime(localDateTime: string): string {
  const time = localDateTime.split("T")[1] ?? "";
  const [hourStr, minuteStr] = time.split(":");
  let hour = Number(hourStr);
  const period = hour >= 12 ? "PM" : "AM";
  hour = hour % 12 || 12;
  return `${hour}:${minuteStr} ${period}`;
}

const MONTHS = [
  "Jan", "Feb", "Mar", "Apr", "May", "Jun",
  "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
];

export function formatDate(localDateTime: string): string {
  const datePart = localDateTime.split("T")[0] ?? "";
  const [year, month, day] = datePart.split("-").map(Number);
  return `${MONTHS[month - 1]} ${day}`;
}

// Segment times are each in their own airport's local time, so a connection or an
// overnight/date-line-crossing flight can land on a different calendar date than it
// departed (see instructions.md's SYD->LAX case) - always show the date alongside the time.
export function formatDateTime(localDateTime: string): string {
  return `${formatDate(localDateTime)}, ${formatTime(localDateTime)}`;
}

export function formatDuration(totalMinutes: number): string {
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  if (hours === 0) {
    return `${minutes}m`;
  }
  return `${hours}h ${minutes}m`;
}

export function formatPrice(amount: number): string {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
  }).format(amount);
}
