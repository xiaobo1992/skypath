/** All times from the API are local to the airport they belong to (no UTC conversion here). */
export function formatTime(localDateTime: string): string {
  const time = localDateTime.split("T")[1] ?? "";
  const [hourStr, minuteStr] = time.split(":");
  let hour = Number(hourStr);
  const period = hour >= 12 ? "PM" : "AM";
  hour = hour % 12 || 12;
  return `${hour}:${minuteStr} ${period}`;
}

export function formatDate(localDateTime: string): string {
  const datePart = localDateTime.split("T")[0] ?? "";
  const [year, month, day] = datePart.split("-").map(Number);
  const months = [
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
  ];
  return `${months[month - 1]} ${day}, ${year}`;
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
